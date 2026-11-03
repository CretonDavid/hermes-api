package com.untitled.hermesapi.service;

import com.untitled.hermesapi.dto.PageResponse;
import com.untitled.hermesapi.dto.transaction.*;
import com.untitled.hermesapi.exception.BadRequestException;
import com.untitled.hermesapi.exception.ResourceNotFoundException;
import com.untitled.hermesapi.mapper.TransactionMapper;
import com.untitled.hermesapi.model.Account;
import com.untitled.hermesapi.model.Transaction;
import com.untitled.hermesapi.model.enums.TransactionStatus;
import com.untitled.hermesapi.repository.TransactionRepository;
import com.untitled.hermesapi.repository.TransactionSpecifications;
import com.untitled.hermesapi.utils.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final int MAX_PAGE_SIZE = 500;

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final TransactionMapper transactionMapper;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> search(TransactionFilter filter, int page, int size) {
        Set<Long> categoryIds = filter.categoryIds() == null || filter.categoryIds().isEmpty()
                ? Set.of()
                : categoryService.withDescendants(filter.categoryIds());

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("operationDate"), Sort.Order.desc("id")));

        return PageResponse.of(
                transactionRepository.findAll(TransactionSpecifications.matching(currentUser.id(), filter, categoryIds), pageable),
                transactionMapper::toResponse);
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
                throw new BadRequestException("Le compte, le montant et la date d'une opération rapprochée ne peuvent pas être modifiés");
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
        Transaction peer = transaction.getTransferPeer();

        if (transaction.getStatus() == TransactionStatus.RECONCILED
                || (peer != null && peer.getStatus() == TransactionStatus.RECONCILED)) {
            throw new BadRequestException("Une opération rapprochée ne peut pas être supprimée");
        }

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
        String q = query == null ? "" : query.trim();
        List<TransactionRepository.PayeeView> payees =
                transactionRepository.findPayees(userId, q, PageRequest.of(0, Math.clamp(limit, 1, 50)));

        // Dernière catégorie utilisée pour chaque tiers, pour pré-remplir le formulaire
        Map<String, Long> lastCategory = new HashMap<>();
        for (Transaction t : transactionRepository.findCategorizedByOwner(userId, PageRequest.of(0, 2000))) {
            if (t.getPayee() != null) {
                lastCategory.putIfAbsent(t.getPayee().toLowerCase(), t.getCategory().getId());
            }
        }
        return payees.stream()
                .map(p -> new PayeeSuggestion(p.getPayee(), p.getUsage(), lastCategory.get(p.getPayee().toLowerCase())))
                .toList();
    }

    public Transaction find(Long id) {
        return transactionRepository.findByIdAndOwner(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Opération introuvable"));
    }

    static void ensureOpen(Account account) {
        if (account.isClosed()) {
            throw new BadRequestException("Le compte " + account.getName() + " est clôturé");
        }
    }

    private void apply(Transaction transaction, TransactionRequest request) {
        transaction.setDirection(request.direction());
        transaction.setAmount(request.amount());
        transaction.setLabel(request.label().trim());
        transaction.setPayee(AccountService.blankToNull(request.payee()));
        transaction.setNotes(AccountService.blankToNull(request.notes()));
        transaction.setCategory(request.categoryId() == null ? null : categoryService.find(request.categoryId()));
        transaction.setPaymentMethod(request.paymentMethod());
        transaction.setOperationDate(request.operationDate());
        transaction.setValueDate(request.valueDate());
    }
}
