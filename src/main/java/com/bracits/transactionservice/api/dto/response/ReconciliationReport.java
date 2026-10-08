package com.bracits.transactionservice.api.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/v1/admin/reconciliation?from=&to=} (FR-08). {@code truncated} = the window held
 * more rows than the per-call cap; call again with a narrower window.
 */
public record ReconciliationReport(
    Instant from,
    Instant to,
    int checked,
    boolean truncated,
    List<ReconciliationMismatch> mismatches) {

}
