package com.untitled.hermesapi.controller;

import com.untitled.hermesapi.dto.currency.CurrencyRequest;
import com.untitled.hermesapi.dto.currency.CurrencyResponse;
import com.untitled.hermesapi.service.CurrencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/currencies")
public class CurrencyController {

    private final CurrencyService currencyService;

    @GetMapping
    public ResponseEntity<List<CurrencyResponse>> getAll() {
        return ResponseEntity.ok(currencyService.findAll());
    }

    @PostMapping
    public ResponseEntity<CurrencyResponse> create(@Valid @RequestBody CurrencyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(currencyService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CurrencyResponse> update(@PathVariable Long id, @Valid @RequestBody CurrencyRequest request) {
        return ResponseEntity.ok(currencyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        currencyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
