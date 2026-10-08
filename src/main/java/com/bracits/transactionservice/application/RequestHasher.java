package com.bracits.transactionservice.application;

import com.bracits.transactionservice.application.command.SendMoneyCommand;
import com.bracits.transactionservice.domain.txn.RequestHash;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * {@code request_hash} = SHA-256 of the canonical request body (FR-03): every body field in a fixed order, separated
 * by a control character that cannot occur in valid input, with a distinct marker for absent optional fields.
 */
@Component
public final class RequestHasher {

  public RequestHash hash(SendMoneyCommand command) {
    String canonical = String.join(String.valueOf(ApplicationConstants.FIELD_SEPARATOR),
        command.senderMsisdn(),
        command.receiverMsisdn(),
        Long.toString(command.amount()),
        command.currency(),
        command.reference().orElse(ApplicationConstants.ABSENT_FIELD),
        command.quoteToken().orElse(ApplicationConstants.ABSENT_FIELD));
    return new RequestHash(sha256().digest(canonical.getBytes(StandardCharsets.UTF_8)));
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance(ApplicationConstants.HASH_ALGORITHM);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
