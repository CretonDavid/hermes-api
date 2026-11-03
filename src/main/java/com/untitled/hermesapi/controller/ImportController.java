package com.untitled.hermesapi.controller;

import com.untitled.hermesapi.dto.importing.*;
import com.untitled.hermesapi.service.importing.ImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/imports")
public class ImportController {

    private final ImportService importService;

    // Aperçu brut d'un CSV pour configurer le mapping des colonnes
    @PostMapping(value = "/csv/inspect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsvInspectResponse> inspectCsv(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String encoding) {
        return ResponseEntity.ok(importService.inspectCsv(file, encoding));
    }

    @PostMapping(value = "/csv/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportPreviewResponse> previewCsv(
            @RequestPart("file") MultipartFile file,
            @RequestParam Long accountId,
            @ModelAttribute CsvImportOptions options) {
        return ResponseEntity.ok(importService.previewCsv(file, accountId, options));
    }

    @PostMapping(value = "/ofx/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportPreviewResponse> previewOfx(
            @RequestPart("file") MultipartFile file,
            @RequestParam Long accountId) {
        return ResponseEntity.ok(importService.previewOfx(file, accountId));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ImportResultResponse> confirm(@Valid @RequestBody ImportConfirmRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(importService.confirm(request));
    }
}
