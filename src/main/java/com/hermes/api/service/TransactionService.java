package com.hermes.api.service;

import com.hermes.api.dto.PageResponse;
import com.hermes.api.dto.transaction.*;
import com.hermes.api.exception.BadRequestException;
import com.hermes.api.exception.ResourceNotFoundException;
import com.hermes.api.mapper.TransactionMapper;
import com.hermes.api.model.Account;
import com.hermes.api.model.Category;
import com.hermes.api.model.Transaction;
import com.hermes.api.model.TransactionSplit;
import com.hermes.api.model.enums.TransactionDirection;
import com.hermes.api.model.enums.TransactionStatus;
import com.hermes.api.repository.LikePatterns;
import com.hermes.api.repository.TransactionRepository;
import com.hermes.api.repository.TransactionSpecifications;
import com.hermes.api.utils.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final int MAX_PAGE_SIZE = 500;
    private static final int MAX_PAYEES = 50;

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final GoalService goalService;
    private final TransactionMapper transactionMapper;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> search(TransactionFilter filter, int page, int size, String sort, String direction) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), sort(sort, direction));

        Set<Long> categoryIds = categoryIds(filter);
        boolean uncategorized = Boolean.TRUE.equals(filter.uncategorized());
        return PageResponse.of(
                transactionRepository.findAll(TransactionSpecifications.matching(currentUser.id(), filter, categoryIds), pageable),
                t -> transactionMapper.toResponse(t, matchedAmount(t, categoryIds, uncategorized)));
    }

    @Transactional(readOnly = true)
    public List<TransactionTotals> totals(TransactionFilter filter) {
        Set<Long> categoryIds = categoryIds(filter);
        return transactionRepository.totalsByCurrency(
                TransactionSpecifications.matching(currentUser.id(), filter, categoryIds),
                categoryIds, Boolean.TRUE.equals(filter.uncategorized()));
    }

    @Transactional
    public BulkCategoryResponse updateCategory(BulkCategoryRequest request) {
        Category category = request.categoryId() == null ? null : categoryService.find(request.categoryId());
        int updated = 0;
        int skippedTransfers = 0;
        for (Long id : new LinkedHashSet<>(request.transactionIds())) {
            Transaction transaction = find(id);
            if (transaction.isTransfer()) {
                skippedTransfers++;
            } else {
                // La nouvelle catégorie remplace une éventuelle ventilation
                transaction.getSplits().clear();
                transaction.setCategory(category);
                updated++;
            }
        }
        return new BulkCategoryResponse(updated, skippedTransfers);
    }

    private Set<Long> categoryIds(TransactionFilter filter) {
        return filter.categoryIds() == null || filter.categoryIds().isEmpty()
                ? Set.of()
                : categoryService.withDescendants(filter.categoryIds());
    }

    // Le montant est trié sans tenir compte de son signe ; l'identifiant garantit un ordre stable
    private static Sort sort(String sort, String direction) {
        Sort.Direction order = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort.Order primary = switch (sort) {
            case "label" -> new Sort.Order(order, "label").ignoreCase();
            case "account" -> new Sort.Order(order, "account.name").ignoreCase();
            case "amount" -> new Sort.Order(order, "amount");
            default -> new Sort.Order(order, "operationDate");
        };
        return Sort.by(primary, Sort.Order.desc("id"));
    }

    @Transactional(readOnly = true)
    public TransactionResponse getById(Long id) {
        return transactionMapper.toResponse(find(id));
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        Account account = accountService.find(request.accountId());
        ensureOpen(account);

        Transaction transaction = Transaction.builder()
                .account(account)
                .createdAt(LocalDateTime.now())
                .status(TransactionStatus.PENDING)
                .build();
        apply(transaction, request);
        if (request.status() == TransactionStatus.CLEARED) {
            transaction.setStatus(TransactionStatus.CLEARED);
        }

        transactionRepository.save(transaction);
        return transactionMapper.toResponse(transaction);
    }

    @Transactional
    public TransactionResponse update(Long id, TransactionRequest request) {
        Transaction transaction = find(id);
        if (transaction.isTransfer()) {
            throw new BadRequestException("Un virement se modifie depuis l'écran des virements");
        }

        if (transaction.getStatus() == TransactionStatus.RECONCILED) {
            boolean lockedFieldChanged = !transaction.getAccount().getId().equals(request.accountId())
                    || transaction.getDirection() != request.direction()
                    || transaction.getAmount().compareTo(request.amount()) != 0
                    || !transaction.getOperationDate().equals(request.operationDate());
            if (lockedFieldChanged) {
                throw new BadRequestException("Le compte bancaire, le montant et la date d'une opération rapprochée ne peuvent pas être modifiés");
            }
        } else if (request.status() != null && request.status() != TransactionStatus.RECONCILED) {
            transaction.setStatus(request.status());
        }

        if (!transaction.getAccount().getId().equals(request.accountId())) {
            Account account = accountService.find(request.accountId());
            ensureOpen(account);
            transaction.setAccount(account);
        }
        apply(transaction, request);
        if (transaction.getReimbursementPayer() != null && transaction.getDirection() != TransactionDirection.CREDIT) {
            // Une dépense ne peut pas être un remboursement reçu
            transaction.setReimbursementPayer(null);
            transaction.setReimbursementPeriodStart(null);
        }
        return transactionMapper.toResponse(transaction);
    }

    @Transactional
    public TransactionResponse updateStatus(Long id, TransactionStatusRequest request) {
        Transaction transaction = find(id);
        if (transaction.getStatus() == TransactionStatus.RECONCILED) {
            throw new BadRequestException("Cette opération est rapprochée : annulez le rapprochement pour la modifier");
        }
        if (request.status() == TransactionStatus.RECONCILED) {
            throw new BadRequestException("Utilisez le rapprochement bancaire pour rapprocher des opérations");
        }
        transaction.setStatus(request.status());
        return transactionMapper.toResponse(transaction);
    }

    @Transactional
    public void delete(Long id) {
        Transaction transaction = find(id);
        if (isReconciled(transaction)) {
            throw new BadRequestException("Une opération rapprochée ne peut pas être supprimée");
        }
        delete(transaction);
    }

    /**
     * Suppression groupée, tout ou rien : une seule opération rapprochée bloque l'ensemble.
     * Un virement emporte son opération liée, même si elle n'est pas sélectionnée.
     *
     * @return nombre d'opérations supprimées, opérations liées des virements comprises
     */
    @Transactional
    public int deleteAll(List<Long> ids) {
        List<Transaction> transactions = new LinkedHashSet<>(ids).stream().map(this::find).toList();
        long reconciled = transactions.stream().filter(TransactionService::isReconciled).count();
        if (reconciled > 0) {
            throw new BadRequestException(reconciled == 1
                    ? "Une opération rapprochée ne peut pas être supprimée : rien n'a été supprimé"
                    : reconciled + " opérations rapprochées ne peuvent pas être supprimées : rien n'a été supprimé");
        }
        Set<Long> deleted = new HashSet<>();
        for (Transaction transaction : transactions) {
            if (deleted.contains(transaction.getId())) {
                continue; // déjà supprimée avec l'autre jambe du virement
            }
            Transaction peer = transaction.getTransferPeer();
            if (peer != null) {
                deleted.add(peer.getId());
            }
            deleted.add(transaction.getId());
            delete(transaction);
        }
        return deleted.size();
    }

    private static boolean isReconciled(Transaction transaction) {
        Transaction peer = transaction.getTransferPeer();
        return transaction.getStatus() == TransactionStatus.RECONCILED
                || (peer != null && peer.getStatus() == TransactionStatus.RECONCILED);
    }

    private void delete(Transaction transaction) {
        Long id = transaction.getId();
        Transaction peer = transaction.getTransferPeer();
        if (peer != null) {
            transaction.setTransferPeer(null);
            peer.setTransferPeer(null);
            transactionRepository.flush();
            transactionRepository.delete(peer);
        }
        transactionRepository.delete(transaction);
        log.info("Transaction {} deleted", id);
    }

    @Transactional(readOnly = true)
    public List<PayeeSuggestion> payees(String query, int limit) {
        UUID userId = currentUser.id();
        String pattern = LikePatterns.containsIgnoreCase(query == null ? "" : query);
        List<TransactionRepository.PayeeView> payees =
                transactionRepository.findPayees(userId, pattern, PageRequest.of(0, Math.clamp(limit, 1, MAX_PAYEES)));
        if (payees.isEmpty()) {
            return List.of();
        }

        // Dernière catégorie utilisée pour chaque tiers, pour pré-remplir le formulaire
        Set<String> keys = payees.stream().map(p -> p.getPayee().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Map<String, Long> lastCategory = new HashMap<>();
        for (TransactionRepository.PayeeCategoryView row : transactionRepository.findCategoriesOfPayees(userId, keys)) {
            lastCategory.putIfAbsent(row.getPayee().toLowerCase(Locale.ROOT), row.getCategoryId());
        }
        return payees.stream()
                .map(p -> new PayeeSuggestion(p.getPayee(), p.getUsage(), lastCategory.get(p.getPayee().toLowerCase(Locale.ROOT))))
                .toList();
    }

    public Transaction find(Long id) {
        return transactionRepository.findByIdAndOwner(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Opération introuvable"));
    }

    static void ensureOpen(Account account) {
        if (account.isClosed()) {
            throw new BadRequestException("Le compte bancaire " + account.getName() + " est clôturé");
        }
    }

    private void apply(Transaction transaction, TransactionRequest request) {
        transaction.setDirection(request.direction());
        transaction.setAmount(request.amount());
        transaction.setLabel(request.label().trim());
        transaction.setPayee(AccountService.blankToNull(request.payee()));
        transaction.setNotes(AccountService.blankToNull(request.notes()));
        applyCategories(transaction, request);
        transaction.setPaymentMethod(request.paymentMethod());
        transaction.setOperationDate(request.operationDate());
        transaction.setValueDate(request.valueDate());
        goalService.applyAllocations(transaction, request.goalAllocations());
    }

    /**
     * Une catégorie, ou une ventilation d'au moins deux lignes dont la somme est égale au montant.
     * Une ventilation d'une seule ligne revient à une catégorie simple.
     */
    private void applyCategories(Transaction transaction, TransactionRequest request) {
        List<TransactionSplitRequest> splits = request.splits() == null ? List.of() : request.splits();
        transaction.getSplits().clear();
        if (splits.size() == 1) {
            transaction.setCategory(categoryService.find(splits.getFirst().categoryId()));
            return;
        }
        if (splits.isEmpty()) {
            transaction.setCategory(request.categoryId() == null ? null : categoryService.find(request.categoryId()));
            return;
        }

        BigDecimal sum = splits.stream().map(TransactionSplitRequest::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(request.amount()) != 0) {
            throw new BadRequestException("La somme de la ventilation (" + sum.toPlainString()
                    + ") doit être égale au montant de l'opération (" + request.amount().toPlainString() + ")");
        }
        transaction.setCategory(null);
        for (TransactionSplitRequest split : splits) {
            transaction.getSplits().add(TransactionSplit.builder()
                    .transaction(transaction)
                    .category(categoryService.find(split.categoryId()))
                    .label(AccountService.blankToNull(split.label()))
                    .amount(split.amount())
                    .build());
        }
    }

    /**
     * Part d'une opération ventilée qui correspond au filtre de catégories : c'est elle que la liste
     * affiche, pour que ses montants correspondent aux statistiques. Null sans filtre de catégorie.
     */
    private static BigDecimal matchedAmount(Transaction transaction, Set<Long> categoryIds, boolean uncategorized) {
        if (!transaction.isSplit() || (categoryIds.isEmpty() && !uncategorized)) {
            return null;
        }
        return transaction.getSplits().stream()
                .filter(s -> s.getCategory() == null ? uncategorized : categoryIds.contains(s.getCategory().getId()))
                .map(TransactionSplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
