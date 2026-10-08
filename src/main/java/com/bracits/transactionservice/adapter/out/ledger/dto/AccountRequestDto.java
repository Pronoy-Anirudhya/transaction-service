package com.bracits.transactionservice.adapter.out.ledger.dto;

import java.util.List;

/** {@code POST /internal/v1/accounts} body: {@code {accountId, code, flags, userData64}}. */
public record AccountRequestDto(String accountId, int code, List<String> flags, long userData64) {

  public AccountRequestDto {
    flags = List.copyOf(flags);
  }
}
