package com.hermes.api.dto.transaction;

/**
 * updated : opérations catégorisées ; skippedTransfers : virements internes laissés de côté.
 */
public record BulkCategoryResponse(int updated, int skippedTransfers) {
}
