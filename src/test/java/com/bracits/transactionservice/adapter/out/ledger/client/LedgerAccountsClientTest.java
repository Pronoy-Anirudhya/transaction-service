package com.bracits.transactionservice.adapter.out.ledger.client;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bracits.transactionservice.domain.ledger.enums.AccountCreation;
import com.bracits.transactionservice.port.out.client.LedgerAccountsPort;
import com.bracits.transactionservice.port.out.exception.LedgerAccountNotFoundException;
import com.bracits.transactionservice.port.out.exception.LedgerConflictException;
import com.bracits.transactionservice.port.out.exception.LedgerUnavailableException;
import org.junit.jupiter.api.Test;

/**
 * {@link LedgerAccountsClient} ({@link LedgerAccountsPort}) against an in-JVM WireMock ledger,
 * wired by the real {@code LedgerClientConfig}: account creation and wallet funding. Fixtures and
 * wiring live in {@link LedgerClientTestSupport}.
 */
class LedgerAccountsClientTest extends LedgerClientTestSupport {

  // ---- Accounts and funding ---------------------------------------------------------------------------------------

  @Test
  void createAccountCreatedMatchesContractExample() {
    ledger.stubFor(post(urlEqualTo(ACCOUNTS)).willReturn(json(201, ACCOUNT_CREATED)));
    run(ctx -> assertThat(ctx.getBean(LedgerAccountsPort.class).createAccount(wallet()))
        .isEqualTo(AccountCreation.CREATED));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(ACCOUNTS))
        .withHeader("Content-Type", containing(JSON))
        .withRequestBody(equalToJson(CUSTOMER_WALLET_REQUEST, false, false)));
  }

  @Test
  void createAccountAlreadyExists() {
    ledger.stubFor(post(urlEqualTo(ACCOUNTS)).willReturn(json(200, ACCOUNT_EXISTS)));
    run(ctx -> assertThat(ctx.getBean(LedgerAccountsPort.class).createAccount(wallet()))
        .isEqualTo(AccountCreation.ALREADY_EXISTS));
  }

  @Test
  void createAccountConflict() {
    ledger.stubFor(post(urlEqualTo(ACCOUNTS)).willReturn(problem(409, ACCOUNT_CONFLICT)));
    run(ctx -> assertThatThrownBy(
        () -> ctx.getBean(LedgerAccountsPort.class).createAccount(wallet()))
        .isInstanceOf(LedgerConflictException.class));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(ACCOUNTS)));
  }

  @Test
  void fundPostedMatchesContractExample() {
    ledger.stubFor(post(urlEqualTo(FUNDINGS)).willReturn(json(200, FUNDING_POSTED)));
    run(ctx -> ctx.getBean(LedgerAccountsPort.class).fund(FUNDING_ID, SENDER, 500_000L));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(FUNDINGS))
        .withHeader("Content-Type", containing(JSON))
        .withRequestBody(equalToJson(FUND_WALLET_REQUEST, false, false)));
  }

  @Test
  void fundAccountNotFound() {
    ledger.stubFor(post(urlEqualTo(FUNDINGS)).willReturn(problem(422, ACCOUNT_NOT_FOUND_422)));
    run(ctx -> assertThatThrownBy(
        () -> ctx.getBean(LedgerAccountsPort.class).fund(FUNDING_ID, MISSING_ACCOUNT, 1L))
        .isInstanceOf(LedgerAccountNotFoundException.class));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(FUNDINGS)));
  }

  @Test
  void fundPreviouslyRejectedIsUnavailable() {
    ledger.stubFor(post(urlEqualTo(FUNDINGS)).willReturn(problem(422, PREVIOUSLY_REJECTED)));
    run(ctx -> assertThatThrownBy(
        () -> ctx.getBean(LedgerAccountsPort.class).fund(FUNDING_ID, SENDER, 1L))
        .isInstanceOf(LedgerUnavailableException.class));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(FUNDINGS)));
  }

  @Test
  void fundConflict() {
    ledger.stubFor(post(urlEqualTo(FUNDINGS)).willReturn(problem(409, POSTING_CONFLICT)));
    run(ctx -> assertThatThrownBy(
        () -> ctx.getBean(LedgerAccountsPort.class).fund(FUNDING_ID, SENDER, 1L))
        .isInstanceOf(LedgerConflictException.class));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(FUNDINGS)));
  }

  @Test
  void fundServerErrorIsUnavailableWithoutRetry() {
    ledger.stubFor(post(urlEqualTo(FUNDINGS)).willReturn(problem(500, POSTING_LEDGER_ERROR)));
    run(ctx -> assertThatThrownBy(
        () -> ctx.getBean(LedgerAccountsPort.class).fund(FUNDING_ID, SENDER, 1L))
        .isInstanceOf(LedgerUnavailableException.class));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(FUNDINGS)));
  }

  @Test
  void fundUnavailableAfterIdenticalRetries() {
    ledger.stubFor(post(urlEqualTo(FUNDINGS)).willReturn(unavailable(OVERLOADED)));

    run(ctx -> assertThatThrownBy(
        () -> ctx.getBean(LedgerAccountsPort.class).fund(FUNDING_ID, SENDER, 1L))
        .isInstanceOf(LedgerUnavailableException.class));

    ledger.verify(exactly(3), postRequestedFor(urlEqualTo(FUNDINGS)));
    assertAllBodiesIdentical(FUNDINGS, 3);
  }
}
