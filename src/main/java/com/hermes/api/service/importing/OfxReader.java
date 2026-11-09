package com.hermes.api.service.importing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lecteur OFX tolérant : accepte l'OFX 1.x (SGML, balises non fermées) exporté par la plupart des
 * banques françaises comme l'OFX 2.x (XML). Le fichier est lu par expressions régulières, sans
 * parseur XML : aucune entité externe ne peut être résolue.
 */
public final class OfxReader {

    /**
     * Opération lue dans le fichier ; {@code amount} est null si le montant est illisible.
     */
    public record OfxTransaction(String fitId, String type, LocalDate datePosted, LocalDate dateUser,
                                 BigDecimal amount, String name, String memo, String checkNumber) {
    }

    private static final Pattern TRANSACTION_BLOCK =
            Pattern.compile("<STMTTRN>(.*?)(?:</STMTTRN>|(?=<STMTTRN>)|(?=</BANKTRANLIST>))",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final DateTimeFormatter OFX_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int DATE_LENGTH = 8;
    private static final int HEADER_LENGTH = 2000;
    private static final Map<String, Pattern> TAG_PATTERNS = new ConcurrentHashMap<>();

    private OfxReader() {
    }

    public static boolean looksLikeOfx(String content) {
        String head = content.substring(0, Math.min(content.length(), HEADER_LENGTH)).toUpperCase(Locale.ROOT);
        return head.contains("OFXHEADER") || head.contains("<OFX>");
    }

    public static List<OfxTransaction> parse(String content) {
        List<OfxTransaction> transactions = new ArrayList<>();
        Matcher matcher = TRANSACTION_BLOCK.matcher(content);
        while (matcher.find()) {
            String block = matcher.group(1);
            String amount = value(block, "TRNAMT");
            LocalDate posted = date(value(block, "DTPOSTED"));
            if (amount == null || posted == null) {
                continue;
            }
            transactions.add(new OfxTransaction(
                    value(block, "FITID"),
                    value(block, "TRNTYPE"),
                    posted,
                    date(value(block, "DTUSER")),
                    amount(amount),
                    value(block, "NAME"),
                    value(block, "MEMO"),
                    value(block, "CHECKNUM")));
        }
        return transactions;
    }

    // Valeur d'un élément, qu'il soit fermé (<NAME>x</NAME>) ou non (<NAME>x)
    private static String value(String block, String tag) {
        Pattern pattern = TAG_PATTERNS.computeIfAbsent(tag,
                t -> Pattern.compile("<" + t + ">([^<\\r\\n]*)", Pattern.CASE_INSENSITIVE));
        Matcher matcher = pattern.matcher(block);
        if (!matcher.find()) {
            return null;
        }
        String value = decode(matcher.group(1).trim());
        return value.isEmpty() ? null : value;
    }

    private static BigDecimal amount(String value) {
        try {
            return new BigDecimal(value.replace(",", ".").replace(" ", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate date(String value) {
        if (value == null || value.length() < DATE_LENGTH) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, DATE_LENGTH), OFX_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String decode(String value) {
        return value.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }
}
