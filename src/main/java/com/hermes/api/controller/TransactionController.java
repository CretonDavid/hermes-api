package com.hermes.api.controller;

import com.hermes.api.dto.PageResponse;
import com.hermes.api.dto.transaction.*;
import com.hermes.api.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public ResponseEntity<PageResponse<TransactionResponse>> search(
            @ModelAttribute TransactionFilter filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "date") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return ResponseEntity.ok(transactionService.search(filter, page, size, sort, direction));
    }

    // Cumul de toutes les opérations correspondant aux filtres, par devise
    @GetMapping("/totals")
    public ResponseEntity<List<TransactionTotals>> totals(@ModelAttribute TransactionFilter filter) {
        return ResponseEntity.ok(transactionService.totals(filter));
    }

    // Suppression groupée, tout ou rien ; un virement emporte son opération liée
    @PostMapping("/delete")
    public ResponseEntity<BulkDeleteResponse> deleteAll(@Valid @RequestBody BulkDeleteRequest request) {
        return ResponseEntity.ok(new BulkDeleteResponse(transactionService.deleteAll(request.transactionIds())));
    }

    @GetMapping("/payees")
    public ResponseEntity<List<PayeeSuggestion>> payees(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(transactionService.payees(q, limit));
    }

    // Libellés déjà utilisés, les plus fréquents d'abord
    @GetMapping("/labels")
    public ResponseEntity<List<LabelSuggestion>> labels(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(transactionService.labels(q, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getById(id));
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> update(@PathVariable Long id, @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionService.update(id, request));
    }

    // Pointage / dépointage
    @PatchMapping("/{id}/status")
    public ResponseEntity<TransactionResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody TransactionStatusRequest request) {
        return ResponseEntity.ok(transactionService.updateStatus(id, request));
    }

    // Supprime aussi l'opération liée s'il s'agit d'un virement
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        transactionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
