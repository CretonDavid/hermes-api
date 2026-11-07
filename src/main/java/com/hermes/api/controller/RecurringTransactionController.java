package com.hermes.api.controller;

import com.hermes.api.dto.recurring.RecurringTransactionRequest;
import com.hermes.api.dto.reimbursement.ReimbursementLinkRequest;
import com.hermes.api.dto.reimbursement.ReimbursementTrackingResponse;
import com.hermes.api.dto.recurring.RecurringTransactionResponse;
import com.hermes.api.dto.recurring.UpcomingOccurrenceResponse;
import com.hermes.api.service.RecurringTransactionService;
import com.hermes.api.service.ReimbursementService;
import com.hermes.api.utils.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/recurring-transactions")
public class RecurringTransactionController {

    private final RecurringTransactionService recurringTransactionService;
    private final ReimbursementService reimbursementService;
    private final CurrentUser currentUser;

    @GetMapping
    public ResponseEntity<List<RecurringTransactionResponse>> getAll() {
        return ResponseEntity.ok(recurringTransactionService.findAll());
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<UpcomingOccurrenceResponse>> upcoming(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(recurringTransactionService.upcoming(days));
    }

    // Suivi des dépenses récurrentes remboursées par un tiers
    @GetMapping("/reimbursements")
    public ResponseEntity<List<ReimbursementTrackingResponse>> reimbursements() {
        return ResponseEntity.ok(reimbursementService.tracking());
    }

    @PostMapping("/{id}/reimbursements")
    public ResponseEntity<Void> linkReimbursement(@PathVariable Long id, @Valid @RequestBody ReimbursementLinkRequest request) {
        reimbursementService.link(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/reimbursements/{transactionId}")
    public ResponseEntity<Void> unlinkReimbursement(@PathVariable Long id, @PathVariable Long transactionId) {
        reimbursementService.unlink(id, transactionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecurringTransactionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(recurringTransactionService.getById(id));
    }

    // Les occurrences déjà échues sont générées immédiatement
    @PostMapping
    public ResponseEntity<RecurringTransactionResponse> create(@Valid @RequestBody RecurringTransactionRequest request) {
        RecurringTransactionResponse created = recurringTransactionService.create(request);
        recurringTransactionService.generateDueForUser(currentUser.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(recurringTransactionService.getById(created.id()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecurringTransactionResponse> update(@PathVariable Long id, @Valid @RequestBody RecurringTransactionRequest request) {
        recurringTransactionService.update(id, request);
        recurringTransactionService.generateDueForUser(currentUser.id());
        return ResponseEntity.ok(recurringTransactionService.getById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        recurringTransactionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
