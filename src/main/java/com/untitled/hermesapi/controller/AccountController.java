package com.untitled.hermesapi.controller;

import com.untitled.hermesapi.dto.account.*;
import com.untitled.hermesapi.service.AccountService;
import com.untitled.hermesapi.service.ReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;
    private final ReconciliationService reconciliationService;

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAll() {
        return ResponseEntity.ok(accountService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getById(id));
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody AccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccountResponse> update(@PathVariable Long id, @Valid @RequestBody AccountRequest request) {
        return ResponseEntity.ok(accountService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        accountService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- Rapprochement bancaire ----------

    @GetMapping("/{id}/reconciliations")
    public ResponseEntity<List<ReconciliationResponse>> getReconciliations(@PathVariable Long id) {
        return ResponseEntity.ok(reconciliationService.findAll(id));
    }

    @GetMapping("/{id}/reconciliation-status")
    public ResponseEntity<ReconciliationStatusResponse> getReconciliationStatus(@PathVariable Long id) {
        return ResponseEntity.ok(reconciliationService.status(id));
    }

    @PostMapping("/{id}/reconciliations")
    public ResponseEntity<ReconciliationResponse> reconcile(@PathVariable Long id, @Valid @RequestBody ReconciliationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reconciliationService.reconcile(id, request));
    }

    @DeleteMapping("/{id}/reconciliations/{reconciliationId}")
    public ResponseEntity<Void> undoReconciliation(@PathVariable Long id, @PathVariable Long reconciliationId) {
        reconciliationService.undoLast(id, reconciliationId);
        return ResponseEntity.noContent().build();
    }
}
