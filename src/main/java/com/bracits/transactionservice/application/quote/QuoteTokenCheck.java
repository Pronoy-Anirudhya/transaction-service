package com.bracits.transactionservice.application.quote;

/** Result of decoding a quote token. */
public sealed interface QuoteTokenCheck {

  /** Signature valid; the claims can be compared with the request. */
  record Valid(QuoteClaims claims) implements QuoteTokenCheck {
  }

  /** Malformed or forged. */
  record Invalid() implements QuoteTokenCheck {
  }
}
