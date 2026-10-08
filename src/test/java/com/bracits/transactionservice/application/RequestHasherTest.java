package com.bracits.transactionservice.application;

import com.bracits.transactionservice.application.command.SendMoneyCommand;
import com.bracits.transactionservice.domain.txn.RequestHash;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHasherTest {

  private static final SendMoneyCommand BASE = new SendMoneyCommand(
      "key-1", "8801711000001", "8801711000002", 100_000L, "BDT", Optional.of("rent"), Optional.of("tok.sig"));

  private final RequestHasher hasher = new RequestHasher();

  private static SendMoneyCommand with(
      String sender, String receiver, long amount, String currency, Optional<String> reference, Optional<String> token) {
    return new SendMoneyCommand(BASE.idempotencyKey(), sender, receiver, amount, currency, reference, token);
  }

  @Test
  void isDeterministicSha256() {
    RequestHash first = hasher.hash(BASE);

    assertThat(hasher.hash(BASE)).isEqualTo(first);
    assertThat(new RequestHasher().hash(BASE)).isEqualTo(first);
    assertThat(first.value()).hasSize(32);
  }

  @Test
  void isTheSha256OfTheCanonicalBody() throws Exception {
    String canonical = String.join("\u001f", "8801711000001", "8801711000002", "100000", "BDT", "rent", "tok.sig");
    byte[] expected = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));

    assertThat(hasher.hash(BASE)).isEqualTo(new RequestHash(expected));
  }

  @Test
  void idempotencyKeyIsNotPartOfTheBody() {
    SendMoneyCommand otherKey = new SendMoneyCommand("key-2", BASE.senderMsisdn(), BASE.receiverMsisdn(),
        BASE.amount(), BASE.currency(), BASE.reference(), BASE.quoteToken());

    assertThat(hasher.hash(otherKey)).isEqualTo(hasher.hash(BASE));
  }

  static Stream<Arguments> changedBodies() {
    return Stream.of(
        Arguments.of("sender", with("8801711000009", "8801711000002", 100_000L, "BDT", Optional.of("rent"),
            Optional.of("tok.sig"))),
        Arguments.of("receiver", with("8801711000001", "8801711000009", 100_000L, "BDT", Optional.of("rent"),
            Optional.of("tok.sig"))),
        Arguments.of("amount", with("8801711000001", "8801711000002", 100_001L, "BDT", Optional.of("rent"),
            Optional.of("tok.sig"))),
        Arguments.of("currency", with("8801711000001", "8801711000002", 100_000L, "USD", Optional.of("rent"),
            Optional.of("tok.sig"))),
        Arguments.of("reference", with("8801711000001", "8801711000002", 100_000L, "BDT", Optional.of("Rent"),
            Optional.of("tok.sig"))),
        Arguments.of("no reference", with("8801711000001", "8801711000002", 100_000L, "BDT", Optional.empty(),
            Optional.of("tok.sig"))),
        Arguments.of("quote token", with("8801711000001", "8801711000002", 100_000L, "BDT", Optional.of("rent"),
            Optional.of("tok.sig2"))),
        Arguments.of("no quote token", with("8801711000001", "8801711000002", 100_000L, "BDT", Optional.of("rent"),
            Optional.empty())));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("changedBodies")
  void anyFieldChangeChangesTheHash(String field, SendMoneyCommand changed) {
    assertThat(hasher.hash(changed)).isNotEqualTo(hasher.hash(BASE));
    assertThat(hasher.hash(changed).matches(hasher.hash(BASE))).isFalse();
  }

  @Test
  void absentAndEmptyReferenceDiffer() {
    SendMoneyCommand absent = with("8801711000001", "8801711000002", 100_000L, "BDT", Optional.empty(),
        Optional.empty());
    SendMoneyCommand empty = with("8801711000001", "8801711000002", 100_000L, "BDT", Optional.of(""),
        Optional.empty());

    assertThat(hasher.hash(absent)).isNotEqualTo(hasher.hash(empty));
  }

  @Test
  void absentAndEmptyQuoteTokenDiffer() {
    SendMoneyCommand absent = with("8801711000001", "8801711000002", 100_000L, "BDT", Optional.empty(),
        Optional.empty());
    SendMoneyCommand empty = with("8801711000001", "8801711000002", 100_000L, "BDT", Optional.empty(),
        Optional.of(""));

    assertThat(hasher.hash(absent)).isNotEqualTo(hasher.hash(empty));
  }

  @Test
  void fieldBoundariesCannotBeShifted() {
    SendMoneyCommand a = with("88017110000011", "8801711000002", 100_000L, "BDT", Optional.empty(), Optional.empty());
    SendMoneyCommand b = with("8801711000001", "18801711000002", 100_000L, "BDT", Optional.empty(), Optional.empty());
    SendMoneyCommand c = with("8801711000001", "8801711000002", 100_000L, "BDT", Optional.of("x"), Optional.empty());
    SendMoneyCommand d = with("8801711000001", "8801711000002", 100_000L, "BDT", Optional.empty(), Optional.of("x"));

    assertThat(hasher.hash(a)).isNotEqualTo(hasher.hash(b));
    assertThat(hasher.hash(c)).isNotEqualTo(hasher.hash(d));
  }
}
