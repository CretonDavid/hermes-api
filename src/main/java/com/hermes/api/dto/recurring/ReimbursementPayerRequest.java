package com.hermes.api.dto.recurring;

import com.hermes.api.model.enums.ReimbursementPeriod;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tiers qui rembourse une part de la dépense. id est renseigné pour un tiers existant, afin de
 * conserver les remboursements déjà rattachés.
 */
public record ReimbursementPayerRequest(
        Long id,
        @NotBlank(message = "Le nom du tiers est obligatoire")
        @Size(max = 100, message = "Le nom du tiers ne doit pas dépasser 100 caractères")
        String name,
        @NotNull(message = "La part remboursée est obligatoire")
        @DecimalMin(value = "0.01", message = "La part remboursée doit être comprise entre 0,01 et 100 %")
        @DecimalMax(value = "100", message = "La part remboursée doit être comprise entre 0,01 et 100 %")
        @Digits(integer = 3, fraction = 2, message = "La part remboursée doit avoir au plus 2 décimales")
        BigDecimal share,
        ReimbursementPeriod period,
        // Une date de la première période due, ramenée au début de cette période ; par défaut la période en cours
        LocalDate startDate
) {
}
