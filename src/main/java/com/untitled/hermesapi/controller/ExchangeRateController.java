package com.untitled.hermesapi.controller;

import com.untitled.hermesapi.dto.currency.ExchangeRateRequest;
import com.untitled.hermesapi.dto.currency.ExchangeRateResponse;
import com.untitled.hermesapi.dto.currency.ExchangeRateSyncResponse;
import com.untitled.hermesapi.service.ExchangeRateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/exchange-rates")
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping
    public ResponseEntity<List<ExchangeRateResponse>> getAll(
            @RequestParam(required = false) Long currencyId,
            @RequestParam(defaultValue = "false") boolean latest) {
        return ResponseEntity.ok(exchangeRateService.findAll(currencyId, latest));
    }

    @PostMapping
    public ResponseEntity<ExchangeRateResponse> create(@Valid @RequestBody ExchangeRateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(exchangeRateService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExchangeRateResponse> update(@PathVariable Long id, @Valid @RequestBody ExchangeRateRequest request) {
        return ResponseEntity.ok(exchangeRateService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        exchangeRateService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Import des taux BCE ; avec from, tout l'historique depuis cette date
    @PostMapping("/sync")
    public ResponseEntity<ExchangeRateSyncResponse> sync(@RequestParam(required = false) LocalDate from) {
        return ResponseEntity.ok(exchangeRateService.sync(from));
    }
}
