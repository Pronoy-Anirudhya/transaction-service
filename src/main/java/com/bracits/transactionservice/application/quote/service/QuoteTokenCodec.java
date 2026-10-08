package com.bracits.transactionservice.application.quote.service;

import com.bracits.transactionservice.application.quote.model.QuoteClaims;
import com.bracits.transactionservice.application.quote.model.QuoteTokenCheck;

/**
 * Signs and verifies quote tokens: {@code base64url(sender|receiver|amount|fee|expiresAtMillis)} +
 * "." + {@code base64url(HMAC-SHA256)}. Stateless: any instance can verify any token (P17).
 */
public interface QuoteTokenCodec {

  String encode(QuoteClaims claims);

  QuoteTokenCheck decode(String token);
}
