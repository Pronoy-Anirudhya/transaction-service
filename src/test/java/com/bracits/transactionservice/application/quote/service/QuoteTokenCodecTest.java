package com.bracits.transactionservice.application.quote.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.transactionservice.application.quote.model.QuoteClaims;
import com.bracits.transactionservice.application.quote.model.QuoteTokenCheck;
import com.bracits.transactionservice.application.quote.service.impl.QuoteTokenCodecImpl;
import com.bracits.transactionservice.config.properties.QuoteProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuoteTokenCodecTest {

  private static final String KEY = "unit-test-quote-signing-key";
  private static final Instant EXPIRES = Instant.parse("2026-10-07T20:35:00.123Z");
  private static final QuoteClaims CLAIMS = new QuoteClaims("8801711000001", "8801711000002",
      100_000L, 500L, EXPIRES);

  private final QuoteTokenCodec codec = codec(KEY);

  private static QuoteTokenCodec codec(String key) {
    return new QuoteTokenCodecImpl(new QuoteProperties(Duration.ofMinutes(5), key));
  }

  private static String b64(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * A token signed with the right key over an arbitrary payload, as an attacker with the key could
   * not do.
   */
  private static String signedToken(String rawPayload) throws Exception {
    String encodedPayload = b64(rawPayload.getBytes(StandardCharsets.UTF_8));

    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return encodedPayload + "." + b64(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void roundTrip() {
    assertThat(codec.decode(codec.encode(CLAIMS))).isEqualTo(new QuoteTokenCheck.Valid(CLAIMS));
  }

  @Test
  void tokenIsTwoUnpaddedBase64UrlParts() {
    String token = codec.encode(CLAIMS);

    assertThat(token.split("\\.")).hasSize(2);
    assertThat(token).matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
  }

  @Test
  void encodingIsDeterministicAndVerifiableByAnyInstance() {
    assertThat(codec.encode(CLAIMS)).isEqualTo(codec(KEY).encode(CLAIMS));
    assertThat(codec(KEY).decode(codec.encode(CLAIMS))).isEqualTo(
        new QuoteTokenCheck.Valid(CLAIMS));
  }

  @Test
  void matchesTheGoldenTokenFormat() throws Exception {
    String expected = signedToken(
        "8801711000001|8801711000002|100000|500|" + EXPIRES.toEpochMilli());

    assertThat(codec.encode(CLAIMS)).isEqualTo(expected);
  }

  @Test
  void tamperedPayloadIsInvalid() {
    String genuine = codec.encode(CLAIMS);
    String cheaper = codec.encode(
        new QuoteClaims(CLAIMS.senderMsisdn(), CLAIMS.receiverMsisdn(), 100_000L, 0L,
            EXPIRES));
    String tampered = cheaper.split("\\.")[0] + "." + genuine.split("\\.")[1];

    assertThat(codec.decode(tampered)).isEqualTo(new QuoteTokenCheck.Invalid());
  }

  @Test
  void tamperedSignatureIsInvalid() {
    String[] parts = codec.encode(CLAIMS).split("\\.");
    char last = parts[1].charAt(parts[1].length() - 2);
    String flipped = parts[1].substring(0, parts[1].length() - 2) + (last == 'A' ? 'B' : 'A')
        + parts[1].charAt(parts[1].length() - 1);

    assertThat(codec.decode(parts[0] + "." + flipped)).isEqualTo(new QuoteTokenCheck.Invalid());
  }

  @Test
  void differentKeyIsInvalid() {
    assertThat(codec("another-key").decode(codec.encode(CLAIMS))).isEqualTo(
        new QuoteTokenCheck.Invalid());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", ".", "abc", "a.b.c", "abc.", ".abc", "a..b"})
  void wrongNumberOfPartsOrEmptyPartsIsInvalid(String token) {
    assertThat(codec.decode(token)).isEqualTo(new QuoteTokenCheck.Invalid());
  }

  @Test
  void extraPartIsInvalid() {
    assertThat(codec.decode(codec.encode(CLAIMS) + ".x")).isEqualTo(new QuoteTokenCheck.Invalid());
  }

  @ParameterizedTest
  @ValueSource(strings = {"!!!.@@@", "abc.%%%", "ä.ö", "a+b/c=.d+e/f="})
  void garbageBase64IsInvalid(String token) {
    assertThat(codec.decode(token)).isEqualTo(new QuoteTokenCheck.Invalid());
  }

  @Test
  void genuineSignatureOverGarbagePayloadIsInvalid() throws Exception {
    assertThat(codec.decode(signedToken("only|four|claims|here"))).isEqualTo(
        new QuoteTokenCheck.Invalid());
    assertThat(codec.decode(signedToken("a|b|not-a-number|500|1"))).isEqualTo(
        new QuoteTokenCheck.Invalid());
    assertThat(codec.decode(signedToken("a|b|1|2|3|4"))).isEqualTo(new QuoteTokenCheck.Invalid());
  }

  @Test
  void signatureOverANonBase64PayloadIsInvalid() throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

    String payload = "!!not base64!!";
    String token = payload + "." + b64(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));

    assertThat(codec.decode(token)).isEqualTo(new QuoteTokenCheck.Invalid());
  }

  @Test
  void claimsMatchOnlyTheSameRequestBeforeExpiry() {
    Instant before = EXPIRES.minusMillis(1);

    assertThat(CLAIMS.matches("8801711000001", "8801711000002", 100_000L, before)).isTrue();
    assertThat(CLAIMS.matches("8801711000001", "8801711000002", 100_000L, EXPIRES)).isFalse();
    assertThat(CLAIMS.matches("8801711000003", "8801711000002", 100_000L, before)).isFalse();
    assertThat(CLAIMS.matches("8801711000001", "8801711000003", 100_000L, before)).isFalse();
    assertThat(CLAIMS.matches("8801711000001", "8801711000002", 100_001L, before)).isFalse();
  }
}
