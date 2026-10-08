package com.bracits.transactionservice.application.sendmoney.service.impl;

import com.bracits.transactionservice.application.command.SendMoneyCommand;
import com.bracits.transactionservice.application.constant.ApplicationConstants;
import com.bracits.transactionservice.application.sendmoney.service.RequestHasher;
import com.bracits.transactionservice.domain.txn.model.RequestHash;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link RequestHasher}.
 */
@Component
public final class RequestHasherImpl implements RequestHasher {

  @Override
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
