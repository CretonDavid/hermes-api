package com.untitled.hermesapi.dto.importing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ImportConfirmRequest(
        @NotNull(message = "Le compte est obligatoire")
        Long accountId,
        @NotEmpty(message = "Aucune opération à importer")
        @Size(max = 5000, message = "5000 opérations maximum par import")
        List<@Valid ImportRow> rows
) {
}
