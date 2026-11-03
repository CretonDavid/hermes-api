package com.untitled.hermesapi.service.importing;

import com.untitled.hermesapi.dto.importing.*;
import com.untitled.hermesapi.exception.BadRequestException;
import com.untitled.hermesapi.model.Account;
import com.untitled.hermesapi.model.Category;
import com.untitled.hermesapi.model.Transaction;
import com.untitled.hermesapi.model.enums.TransactionDirection;
import com.untitled.hermesapi.model.enums.TransactionStatus;
import com.untitled.hermesapi.repository.TransactionRepository;
import com.untitled.hermesapi.service.AccountService;
import com.untitled.hermesapi.service.CategoryService;
import com.untitled.hermesapi.utils.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Import de relevés bancaires en deux temps : aperçu (lecture du fichier, détection des doublons,
 * suggestion de catégories) puis confirmation des lignes retenues par l'utilisateur.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private static final int INSPECT_ROWS = 15;
    private static final int MAX_ROWS = 5000;
    private static final List<String> FALLBACK_DATE_FORMATS =
            List.of("dd/MM/yyyy", "dd/MM/yy", "yyyy-MM-dd", "dd-MM-yyyy", "dd.MM.yyyy", "yyyy/MM/dd", "MM/dd/yyyy");

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final CurrentUser currentUser;

    public CsvInspectResponse inspectCsv(MultipartFile file, String encoding) {
        Decoded decoded = decode(file, encoding);
        char separator = CsvReader.detectSeparator(decoded.content());
        List<List<String>> rows = CsvReader.parse(decoded.content(), separator);
        int columns = rows.stream().mapToInt(List::size).max().orElse(0);
        return new CsvInspectResponse(decoded.charset(), String.valueOf(separator), columns,
                rows.subList(0, Math.min(rows.size(), INSPECT_ROWS)));
    }

    @Transactional(readOnly = true)
    public ImportPreviewResponse previewOfx(MultipartFile file, Long accountId) {
        Account account = accountService.find(accountId);
        String content = decode(file, null).content();
        if (!OfxReader.looksLikeOfx(content)) {
            throw new BadRequestException("Ce fichier n'est pas au format OFX");
        }

        List<String> warnings = new ArrayList<>();
        List<ParsedRow> parsed = new ArrayList<>();
        for (OfxReader.OfxTransaction t : OfxReader.parse(content)) {
            if (t.amount().signum() == 0) {
                continue;
            }
            String label = firstNonBlank(t.name(), t.memo(), t.type(), "Opération");
            String notes = t.memo() != null && !t.memo().equalsIgnoreCase(t.name()) ? t.memo() : null;
            // DTUSER est la date de l'opération, DTPOSTED sa date de comptabilisation par la banque
            LocalDate operationDate = t.dateUser() != null ? t.dateUser() : t.datePosted();
            LocalDate valueDate = operationDate.equals(t.datePosted()) ? null : t.datePosted();
            parsed.add(new ParsedRow(operationDate, valueDate, truncate(label, 255), null, truncate(notes, 1000),
                    t.amount(), t.fitId() == null ? null : truncate("ofx:" + t.fitId(), 255)));
        }
        if (parsed.isEmpty()) {
            warnings.add("Aucune opération trouvée dans le fichier");
        }
        assignImportIds(parsed);
        return new ImportPreviewResponse("OFX", enrich(account, parsed), warnings);
    }

    @Transactional(readOnly = true)
    public ImportPreviewResponse previewCsv(MultipartFile file, Long accountId, CsvImportOptions options) {
        Account account = accountService.find(accountId);
        validate(options);

        Decoded decoded = decode(file, options.encoding());
        char separator = options.separator() == null || options.separator().isEmpty()
                ? CsvReader.detectSeparator(decoded.content())
                : ("\\t".equals(options.separator()) ? '\t' : options.separator().charAt(0));
        List<List<String>> rows = CsvReader.parse(decoded.content(), separator);

        int start = Math.max(options.skipLines() == null ? 0 : options.skipLines(), 0)
                + (Boolean.TRUE.equals(options.hasHeader()) ? 1 : 0);
        List<String> warnings = new ArrayList<>();
        List<ParsedRow> parsed = new ArrayList<>();
        DateTimeFormatter dateFormat = formatter(options.dateFormat());
        char decimal = options.decimalSeparator() == null || options.decimalSeparator().isEmpty()
                ? 0 : options.decimalSeparator().charAt(0);

        for (int i = start; i < rows.size() && parsed.size() < MAX_ROWS; i++) {
            List<String> row = rows.get(i);
            int line = i + 1;
            try {
                LocalDate date = parseDate(cell(row, options.dateColumn()), dateFormat);
                LocalDate valueDate = options.valueDateColumn() == null ? null
                        : parseDateOrNull(cell(row, options.valueDateColumn()), dateFormat);
                BigDecimal amount = amount(row, options, decimal);
                if (amount == null || amount.signum() == 0) {
                    continue;
                }
                String label = options.labelColumns().stream()
                        .map(index -> cell(row, index))
                        .filter(value -> !value.isBlank())
                        .reduce((a, b) -> a + " " + b)
                        .orElse("Opération");
                String payee = options.payeeColumn() == null ? null : blankToNull(cell(row, options.payeeColumn()));
                parsed.add(new ParsedRow(date, valueDate, truncate(label, 255), truncate(payee, 255), null, amount, null));
            } catch (IllegalArgumentException | DateTimeParseException exception) {
                warnings.add("Ligne " + line + " ignorée : " + exception.getMessage());
            }
        }
        if (parsed.isEmpty()) {
            warnings.add("Aucune opération n'a pu être lue : vérifiez le mapping des colonnes");
        }
        assignImportIds(parsed);
        return new ImportPreviewResponse("CSV", enrich(account, parsed), warnings.stream().limit(50).toList());
    }

    @Transactional
    public ImportResultResponse confirm(ImportConfirmRequest request) {
        Account account = accountService.find(request.accountId());
        if (account.isClosed()) {
            throw new BadRequestException("Le compte " + account.getName() + " est clôturé");
        }

        Set<String> existingImportIds = new HashSet<>(transactionRepository.findImportIds(account.getId()));
        Map<Long, Category> categories = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        int imported = 0;

        for (ImportRow row : request.rows()) {
            // Protection contre un double envoi : une ligne déjà importée n'est jamais recréée
            if (row.importId() != null && !existingImportIds.add(row.importId())) {
                continue;
            }
            Category category = row.categoryId() == null ? null
                    : categories.computeIfAbsent(row.categoryId(), categoryService::find);
            transactionRepository.save(Transaction.builder()
                    .account(account)
                    .direction(row.direction())
                    .amount(row.amount())
                    .label(row.label().trim())
                    .payee(blankToNull(row.payee()))
                    .notes(blankToNull(row.notes()))
                    .category(category)
                    .paymentMethod(row.paymentMethod() != null ? row.paymentMethod() : LabelNormalizer.guessPaymentMethod(row.label()))
                    .operationDate(row.operationDate())
                    .valueDate(row.valueDate())
                    // Les opérations importées proviennent du relevé : elles sont pointées d'office
                    .status(TransactionStatus.CLEARED)
                    .importId(row.importId())
                    .createdAt(now)
                    .build());
            imported++;
        }
        log.info("{} transactions imported into account {}", imported, account.getId());
        return new ImportResultResponse(imported);
    }

    /**
     * Ajoute aux lignes lues la détection des doublons et les suggestions de tiers et de catégorie.
     */
    private List<ImportRow> enrich(Account account, List<ParsedRow> parsed) {
        if (parsed.isEmpty()) {
            return List.of();
        }
        LocalDate min = parsed.stream().map(ParsedRow::date).min(LocalDate::compareTo).orElseThrow().minusDays(3);
        LocalDate max = parsed.stream().map(ParsedRow::date).max(LocalDate::compareTo).orElseThrow().plusDays(3);
        List<Transaction> existing = new ArrayList<>(transactionRepository.findByAccountBetween(account.getId(), min, max));
        Set<String> importIds = new HashSet<>(transactionRepository.findImportIds(account.getId()));
        Suggestions suggestions = loadSuggestions();

        List<ImportRow> result = new ArrayList<>();
        for (ParsedRow row : parsed) {
            TransactionDirection direction = row.amount().signum() < 0 ? TransactionDirection.DEBIT : TransactionDirection.CREDIT;
            BigDecimal amount = row.amount().abs();

            boolean duplicate = row.importId() != null && importIds.contains(row.importId());
            if (!duplicate) {
                // Opération déjà saisie à la main : même date et même montant signé
                Optional<Transaction> match = existing.stream()
                        .filter(t -> t.getOperationDate().equals(row.date()))
                        .filter(t -> t.signedAmount().compareTo(row.amount()) == 0)
                        .findFirst();
                if (match.isPresent()) {
                    duplicate = true;
                    existing.remove(match.get());
                }
            }

            Suggestion suggestion = suggestions.find(row.label(), row.payee());
            result.add(new ImportRow(
                    row.date(),
                    row.valueDate(),
                    row.label(),
                    row.payee() != null ? row.payee() : suggestion.payee(),
                    row.notes(),
                    amount,
                    direction,
                    suggestion.categoryId(),
                    LabelNormalizer.guessPaymentMethod(row.label()),
                    row.importId(),
                    duplicate));
        }
        return result;
    }

    private Suggestions loadSuggestions() {
        Map<String, Long> byKey = new HashMap<>();
        Map<String, Suggestion> byPayee = new LinkedHashMap<>();
        for (Transaction t : transactionRepository.findCategorizedByOwner(currentUser.id(), PageRequest.of(0, 5000))) {
            String key = LabelNormalizer.key(t.getLabel());
            if (!key.isEmpty()) {
                byKey.putIfAbsent(key, t.getCategory().getId());
            }
            if (t.getPayee() != null) {
                String payeeKey = LabelNormalizer.normalize(t.getPayee());
                if (payeeKey.length() >= 4) {
                    byPayee.putIfAbsent(payeeKey, new Suggestion(t.getPayee(), t.getCategory().getId()));
                }
            }
        }
        return new Suggestions(byKey, byPayee);
    }

    private record Suggestions(Map<String, Long> byKey, Map<String, Suggestion> byPayee) {

        Suggestion find(String label, String payee) {
            String normalizedLabel = " " + LabelNormalizer.normalize(label) + " ";
            Suggestion fromPayee = null;
            if (payee != null) {
                fromPayee = byPayee.get(LabelNormalizer.normalize(payee));
            }
            if (fromPayee == null) {
                for (Map.Entry<String, Suggestion> entry : byPayee.entrySet()) {
                    if (normalizedLabel.contains(" " + entry.getKey() + " ")) {
                        fromPayee = entry.getValue();
                        break;
                    }
                }
            }
            Long categoryId = byKey.get(LabelNormalizer.key(label));
            if (categoryId == null && fromPayee != null) {
                categoryId = fromPayee.categoryId();
            }
            return new Suggestion(fromPayee == null ? null : fromPayee.payee(), categoryId);
        }
    }

    private record Suggestion(String payee, Long categoryId) {
    }

    private record ParsedRow(LocalDate date, LocalDate valueDate, String label, String payee, String notes,
                             BigDecimal amount, String importId) {
    }

    private record Decoded(String content, String charset) {
    }

    // Empreinte stable des lignes sans identifiant bancaire, qui distingue les opérations identiques d'un même fichier
    private static void assignImportIds(List<ParsedRow> rows) {
        Map<String, Integer> occurrences = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            ParsedRow row = rows.get(i);
            if (row.importId() != null) {
                continue;
            }
            String base = row.date() + "|" + row.amount().stripTrailingZeros().toPlainString() + "|" + LabelNormalizer.normalize(row.label());
            int occurrence = occurrences.merge(base, 1, Integer::sum);
            rows.set(i, new ParsedRow(row.date(), row.valueDate(), row.label(), row.payee(), row.notes(), row.amount(),
                    "hash:" + sha1(base + "|" + occurrence)));
        }
    }

    private static void validate(CsvImportOptions options) {
        if (options.dateColumn() == null) {
            throw new BadRequestException("Indiquez la colonne de date");
        }
        if (options.labelColumns() == null || options.labelColumns().isEmpty()) {
            throw new BadRequestException("Indiquez au moins une colonne de libellé");
        }
        boolean single = options.amountColumn() != null;
        boolean split = options.debitColumn() != null || options.creditColumn() != null;
        if (single == split) {
            throw new BadRequestException("Indiquez soit une colonne montant, soit des colonnes débit/crédit");
        }
    }

    private static BigDecimal amount(List<String> row, CsvImportOptions options, char decimal) {
        BigDecimal amount;
        if (options.amountColumn() != null) {
            amount = parseAmount(cell(row, options.amountColumn()), decimal);
        } else {
            BigDecimal debit = options.debitColumn() == null ? null : parseAmount(cell(row, options.debitColumn()), decimal);
            BigDecimal credit = options.creditColumn() == null ? null : parseAmount(cell(row, options.creditColumn()), decimal);
            if (debit != null && debit.signum() != 0) {
                amount = debit.abs().negate();
            } else if (credit != null) {
                amount = credit.abs();
            } else {
                amount = null;
            }
        }
        if (amount != null && Boolean.TRUE.equals(options.negateAmounts())) {
            amount = amount.negate();
        }
        return amount;
    }

    /**
     * Lit un montant bancaire : « -1 234,56 », « 1.234,56 », « 1,234.56 », « 12,50 € », « 12.50- »...
     */
    static BigDecimal parseAmount(String raw, char decimal) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.replaceAll("[\\s\\u00A0\\u202F€$£]", "").replace("EUR", "");
        boolean negative = false;
        if (value.endsWith("-")) {
            negative = true;
            value = value.substring(0, value.length() - 1);
        }
        if (value.startsWith("(") && value.endsWith(")")) {
            negative = true;
            value = value.substring(1, value.length() - 1);
        }
        if (value.startsWith("+")) {
            value = value.substring(1);
        }

        char separator = decimal;
        if (separator == 0) {
            // Le dernier séparateur rencontré est le séparateur décimal
            int comma = value.lastIndexOf(',');
            int dot = value.lastIndexOf('.');
            separator = comma > dot ? ',' : '.';
        }
        char thousands = separator == ',' ? '.' : ',';
        value = value.replace(String.valueOf(thousands), "").replace(separator, '.');
        if (value.isEmpty() || "-".equals(value)) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(value);
            return negative ? amount.negate() : amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("montant illisible « " + raw + " »");
        }
    }

    private static DateTimeFormatter formatter(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return null;
        }
        try {
            return DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Format de date invalide : " + pattern);
        }
    }

    private static LocalDate parseDate(String raw, DateTimeFormatter formatter) {
        LocalDate date = parseDateOrNull(raw, formatter);
        if (date == null) {
            throw new IllegalArgumentException("date illisible « " + raw + " »");
        }
        return date;
    }

    private static LocalDate parseDateOrNull(String raw, DateTimeFormatter formatter) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > 10 && value.charAt(10) == ' ') {
            value = value.substring(0, 10);
        }
        if (formatter != null) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // on tente les formats courants
            }
        }
        for (String pattern : FALLBACK_DATE_FORMATS) {
            try {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
                // format suivant
            }
        }
        return null;
    }

    private static String cell(List<String> row, int index) {
        if (index < 0 || index >= row.size()) {
            return "";
        }
        return row.get(index);
    }

    /**
     * Décode le fichier : encodage imposé, sinon UTF-8 si le contenu est valide, sinon Windows-1252
     * (encodage habituel des exports bancaires français).
     */
    private static Decoded decode(MultipartFile file, String encoding) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Le fichier est vide");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new BadRequestException("Impossible de lire le fichier");
        }

        int offset = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        if (encoding != null && !encoding.isBlank()) {
            try {
                Charset charset = Charset.forName(encoding.trim());
                return new Decoded(new String(bytes, offset, bytes.length - offset, charset), charset.name());
            } catch (IllegalArgumentException exception) {
                throw new BadRequestException("Encodage inconnu : " + encoding);
            }
        }
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            String content = decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
            return new Decoded(content, StandardCharsets.UTF_8.name());
        } catch (CharacterCodingException exception) {
            Charset windows = Charset.forName("windows-1252");
            return new Decoded(new String(bytes, windows), windows.name());
        }
    }

    private static String sha1(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
