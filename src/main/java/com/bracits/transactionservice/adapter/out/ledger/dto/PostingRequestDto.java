package com.bracits.transactionservice.adapter.out.ledger.dto;

import java.util.List;

/**
 * {@code POST /internal/v1/postings} body: {@code {postingId, product, userData64, legs:[…]}}.
 */
public record PostingRequestDto(String postingId, int product, long userData64, List<LegDto> legs) {

  public PostingRequestDto {
    legs = List.copyOf(legs);
  }
}
