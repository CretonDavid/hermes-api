package com.hermes.api.dto.goal;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

// Le budget a servi (la moto est achetée) : son montant est libéré à partir de ce jour
public record GoalUseRequest(
        @NotNull(message = "La date est obligatoire")
        LocalDate date
) {
}
