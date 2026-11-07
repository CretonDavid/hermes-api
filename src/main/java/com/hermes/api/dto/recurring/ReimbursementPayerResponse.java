package com.hermes.api.dto.recurring;

import com.hermes.api.model.enums.ReimbursementPeriod;

import java.math.BigDecimal;

public record ReimbursementPayerResponse(Long id, String name, BigDecimal share, ReimbursementPeriod period) {
}
