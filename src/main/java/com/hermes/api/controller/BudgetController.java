package com.hermes.api.controller;

import com.hermes.api.dto.budget.BudgetResponse;
import com.hermes.api.dto.budget.EnvelopeAmountRequest;
import com.hermes.api.dto.budget.EnvelopeRequest;
import com.hermes.api.service.BudgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@RestController
@RequiredArgsConstructor
@RequestMapping("/budget")
public class BudgetController {

    private final BudgetService budgetService;

    // Budget du mois (aaaa-mm), le mois en cours par défaut
    @GetMapping
    public ResponseEntity<BudgetResponse> month(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ResponseEntity.ok(budgetService.month(month == null ? YearMonth.now() : month));
    }

    @PostMapping("/envelopes")
    public ResponseEntity<Void> create(@Valid @RequestBody EnvelopeRequest request) {
        budgetService.create(request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/envelopes/{id}/amount")
    public ResponseEntity<Void> updateAmount(@PathVariable Long id, @Valid @RequestBody EnvelopeAmountRequest request) {
        budgetService.updateAmount(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/envelopes/{id}/amount")
    public ResponseEntity<Void> clearMonthAmount(
            @PathVariable Long id, @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        budgetService.clearMonthAmount(id, month);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/envelopes/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        budgetService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
