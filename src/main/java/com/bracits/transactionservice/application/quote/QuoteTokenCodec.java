package com.bracits.transactionservice.application.quote;

import com.bracits.transactionservice.application.ApplicationConstants;
import com.bracits.transactionservice.config.QuoteProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * Signs and verifies quote tokens: {@code base64url(sender|receiver|amount|fee|expiresAtMillis)} + "." +
 * {@code base64url(HMAC-SHA256)}. Stateless: any instance can verify any token (P17).
 */
@Component
public final class QuoteTokenCodec {

  private static final int SENDER = 0;
  private static final int RECEIVER = 1;
  private static final int AMOUNT = 2;
  private static final int FEE = 3;
  private static final int EXPIRES_AT = 4;

  private final SecretKeySpec key;
  private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
  private final Base64.Decoder decoder = Base64.getUrlDecoder();

  public QuoteTokenCodec(QuoteProperties properties) {
    this.key = new SecretKeySpec(properties.signingKey().getBytes(StandardCharsets.UTF_8),
        ApplicationConstants.HMAC_ALGORITHM);
  }

  public String encode(QuoteClaims claims) {
    String payload = String.join(ApplicationConstants.CLAIM_SEPARATOR,
        claims.senderMsisdn(),
        claims.receiverMsisdn(),
        Long.toString(claims.amount()),
        Long.toString(claims.fee()),
        Long.toString(claims.expiresAt().toEpochMilli()));
    String encodedPayload = encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    return encodedPayload + ApplicationConstants.TOKEN_PART_SEPARATOR + encoder.encodeToString(sign(encodedPayload));
  }

  public QuoteTokenCheck decode(String token) {
    try {
      String[] parts = token.split(ApplicationConstants.TOKEN_PART_SEPARATOR_REGEX, -1);
      if (parts.length != ApplicationConstants.TOKEN_PARTS) {
        return new QuoteTokenCheck.Invalid();
      }
      if (!MessageDigest.isEqual(sign(parts[0]), decoder.decode(parts[1]))) {
        return new QuoteTokenCheck.Invalid();
      }
      String[] claims = new String(decoder.decode(parts[0]), StandardCharsets.UTF_8)
          .split(ApplicationConstants.CLAIM_SEPARATOR_REGEX, -1);
      if (claims.length != ApplicationConstants.CLAIM_COUNT) {
        return new QuoteTokenCheck.Invalid();
      }
      return new QuoteTokenCheck.Valid(new QuoteClaims(
          claims[SENDER],
          claims[RECEIVER],
          Long.parseLong(claims[AMOUNT]),
          Long.parseLong(claims[FEE]),
          Instant.ofEpochMilli(Long.parseLong(claims[EXPIRES_AT]))));
    } catch (IllegalArgumentException e) {
      return new QuoteTokenCheck.Invalid();
    }
  }

  private byte[] sign(String encodedPayload) {
    try {
      Mac mac = Mac.getInstance(ApplicationConstants.HMAC_ALGORITHM);
      mac.init(key);
      return mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
