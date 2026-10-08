package com.bracits.transactionservice.adapter.out.ledger.client;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bracits.transactionservice.domain.ledger.enums.PostingLookupStatus;
import com.bracits.transactionservice.domain.ledger.model.AccountBalance;
import com.bracits.transactionservice.domain.ledger.model.PostingLookup;
import com.bracits.transactionservice.port.out.client.LedgerQueryPort;
import com.bracits.transactionservice.port.out.exception.LedgerAccountNotFoundException;
import com.bracits.transactionservice.port.out.exception.LedgerUnavailableException;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/**
 * {@link LedgerQueryClient} ({@link LedgerQueryPort}) against an in-JVM WireMock ledger, wired by
 * the real {@code LedgerClientConfig}: posting lookup and account balance. Fixtures and wiring live
 * in {@link LedgerClientTestSupport}.
 */
class LedgerQueryClientTest extends LedgerClientTestSupport {

  // ---- Lookup and balance -----------------------------------------------------------------------------------------

  @Test
  void lookupPostedCarriesTimestamp() {
    stubLookup(json(200, LOOKUP_POSTED));
    run(ctx -> assertThat(ctx.getBean(LedgerQueryPort.class).lookupPosting(POSTING_ID, 4))
        .isEqualTo(new PostingLookup(PostingLookupStatus.POSTED, OptionalLong.of(LEDGER_TS))));
    ledger.verify(exactly(1), getRequestedFor(urlPathEqualTo(POSTINGS + "/" + POSTING_ID))
        .withQueryParam("legs", equalTo("4")));
  }

  @Test
  void lookupPostedWithoutTimestampIsEmpty() {
    stubLookup(json(200, "{\"postingId\":\"" + POSTING_ID + "\",\"status\":\"POSTED\"}"));
    run(ctx -> assertThat(ctx.getBean(LedgerQueryPort.class).lookupPosting(POSTING_ID, 4))
        .isEqualTo(new PostingLookup(PostingLookupStatus.POSTED, OptionalLong.empty())));
  }

  @Test
  void lookupNotFound() {
    stubLookup(json(200, LOOKUP_NOT_FOUND));
    run(ctx -> assertThat(ctx.getBean(LedgerQueryPort.class).lookupPosting(POSTING_ID, 4))
        .isEqualTo(new PostingLookup(PostingLookupStatus.NOT_FOUND, OptionalLong.empty())));
  }

  @Test
  void lookupRejectsLegCountOutsideContractRange() {
    run(ctx -> {
      LedgerQueryPort port = ctx.getBean(LedgerQueryPort.class);

      assertThatThrownBy(() -> port.lookupPosting(POSTING_ID, 0)).isInstanceOf(
          IllegalArgumentException.class);
      assertThatThrownBy(() -> port.lookupPosting(POSTING_ID, 9)).isInstanceOf(
          IllegalArgumentException.class);
    });

    assertThat(ledger.getAllServeEvents()).isEmpty();
  }

  @Test
  void lookupUnavailableAfterRetries() {
    stubLookup(unavailable(LEDGER_UNAVAILABLE));
    run(ctx -> assertThatThrownBy(
        () -> ctx.getBean(LedgerQueryPort.class).lookupPosting(POSTING_ID, 4))
        .isInstanceOf(LedgerUnavailableException.class));
    ledger.verify(exactly(3), getRequestedFor(urlPathEqualTo(POSTINGS + "/" + POSTING_ID)));
  }

  @Test
  void lookupServerErrorIsUnavailableWithoutRetry() {
    stubLookup(problem(500, INTERNAL_ERROR));
    run(ctx -> assertThatThrownBy(
        () -> ctx.getBean(LedgerQueryPort.class).lookupPosting(POSTING_ID, 4))
        .isInstanceOf(LedgerUnavailableException.class));
    ledger.verify(exactly(1), getRequestedFor(urlPathEqualTo(POSTINGS + "/" + POSTING_ID)));
  }

  @Test
  void balanceOfCustomerWallet() {
    ledger.stubFor(get(urlEqualTo(ACCOUNTS + "/" + SENDER + "/balance")).willReturn(
        json(200, BALANCE_CUSTOMER_WALLET)));
    run(ctx -> assertThat(ctx.getBean(LedgerQueryPort.class).balance(SENDER))
        .isEqualTo(new AccountBalance(100_500, 500_000, 0, 0, 399_500)));
  }

  @Test
  void balanceOfIssuanceIsNegative() {
    ledger.stubFor(get(urlEqualTo(ACCOUNTS + "/" + ISSUANCE + "/balance")).willReturn(
        json(200, BALANCE_ISSUANCE)));
    run(ctx -> assertThat(ctx.getBean(LedgerQueryPort.class).balance(ISSUANCE))
        .isEqualTo(new AccountBalance(500_000, 0, 0, 0, -500_000)));
  }

  @Test
  void balanceNotFound() {
    ledger.stubFor(get(urlEqualTo(ACCOUNTS + "/" + MISSING_ACCOUNT + "/balance"))
        .willReturn(problem(404, BALANCE_NOT_FOUND)));
    run(ctx -> assertThatThrownBy(() -> ctx.getBean(LedgerQueryPort.class).balance(MISSING_ACCOUNT))
        .isInstanceOf(LedgerAccountNotFoundException.class));
  }
}
