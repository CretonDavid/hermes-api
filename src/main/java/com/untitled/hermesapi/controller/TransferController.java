package com.untitled.hermesapi.controller;

import com.untitled.hermesapi.dto.transaction.TransferRequest;
import com.untitled.hermesapi.dto.transaction.TransferResponse;
import com.untitled.hermesapi.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Les virements sont identifiés par l'id de l'une de leurs deux opérations.
 * Leur suppression passe par DELETE /transactions/{id}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransferResponse> getById(@PathVariable Long transactionId) {
        return ResponseEntity.ok(transferService.getById(transactionId));
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(@Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferService.create(request));
    }

    @PutMapping("/{transactionId}")
    public ResponseEntity<TransferResponse> update(@PathVariable Long transactionId, @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.ok(transferService.update(transactionId, request));
    }
}
