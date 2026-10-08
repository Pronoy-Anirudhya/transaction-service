package com.bracits.transactionservice.application.repair.service;


/**
 * FR-06 / spec 8.4: resolves in-doubt transactions from the ledger's answer. Claim-then-process:
 * one short auto-commit statement claims rows with {@code FOR UPDATE SKIP LOCKED} and a lease; the
 * identical posting is then re-sent outside any DB transaction and finalised with the same
 * compare-and-set statements as the request path. Never marks FAILED without a 422 from the ledger.
 * Runs in every instance.
 */
public interface RepairWorker {

  void run();
}
