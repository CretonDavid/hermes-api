package com.hermes.api.controller;

import com.hermes.api.dto.account.AccountTypeRequest;
import com.hermes.api.dto.account.AccountTypeResponse;
import com.hermes.api.service.AccountTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/account-types")
public class AccountTypeController {

    private final AccountTypeService accountTypeService;

    @GetMapping
    public ResponseEntity<List<AccountTypeResponse>> getAll() {
        return ResponseEntity.ok(accountTypeService.findAll());
    }

    @PostMapping
    public ResponseEntity<AccountTypeResponse> create(@Valid @RequestBody AccountTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountTypeService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccountTypeResponse> update(@PathVariable Long id, @Valid @RequestBody AccountTypeRequest request) {
        return ResponseEntity.ok(accountTypeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        accountTypeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
