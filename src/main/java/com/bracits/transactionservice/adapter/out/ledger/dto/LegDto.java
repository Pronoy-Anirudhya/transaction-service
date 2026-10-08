package com.bracits.transactionservice.adapter.out.ledger.dto;

/** One leg of a posting: {@code {debit, credit, amount, code}}; account IDs are canonical UUID strings. */
public record LegDto(String debit, String credit, long amount, int code) {
}
