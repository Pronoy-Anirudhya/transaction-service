package com.bracits.transactionservice.contract;

import com.bracits.transactionservice.application.result.QuoteResult;
import com.bracits.transactionservice.application.result.ReconciliationResult;
import com.bracits.transactionservice.application.result.RegisterWalletResult;
import com.bracits.transactionservice.application.result.SendMoneyResult;
import com.bracits.transactionservice.application.result.TxnSummary;
import com.bracits.transactionservice.application.result.WalletLookupResult;
import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.Pricing;
import com.bracits.transactionservice.domain.TxnStatus;
import com.bracits.transactionservice.domain.ledger.AccountBalance;
import com.bracits.transactionservice.domain.wallet.Wallet;
import com.bracits.transactionservice.domain.wallet.WalletStatus;
import com.bracits.transactionservice.domain.wallet.WalletType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.resilience.InvocationRejectedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP behaviour of every endpoint: status codes, bodies, RFC 9457 problems with {@code code}, API key. */
class ApiSliceTest extends ApiTestSupport {

  private static final UUID TXN_ID = UUID.fromString("0192f5a4-0000-7000-8000-000000000100");
  private static final Pricing PRICING = new Pricing(500, 65, 87, 348);
  private static final Instant COMPLETED_AT = Instant.parse("2026-10-07T09:14:03.211Z");
  private static final String BODY = """
      {"senderMsisdn":"01711000001","receiverMsisdn":"01811000002","amount":100000,"currency":"BDT"}""";

  @Autowired
  MockMvc mvc;

  @Test
  void missingApiKeyIs401() throws Exception {
    mvc.perform(post("/api/v1/send-money").header("Idempotency-Key", "k1")
            .contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void wrongApiKeyIs401() throws Exception {
    mvc.perform(get("/api/v1/send-money/{id}", TXN_ID).header("X-API-Key", "wrong"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void completedIs200() throws Exception {
    given(sendMoneyService.send(any())).willReturn(new SendMoneyResult.Completed(summary(TxnStatus.COMPLETED)));
    send("k1", BODY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.txnId").value(TXN_ID.toString()))
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.fee").value(500))
        .andExpect(jsonPath("$.vat").value(65))
        .andExpect(jsonPath("$.commission").value(87))
        .andExpect(jsonPath("$.totalDebit").value(100_500))
        .andExpect(jsonPath("$.completedAt").value("2026-10-07T09:14:03.211Z"));
  }

  @Test
  void processingIs202WithoutCompletedAt() throws Exception {
    given(sendMoneyService.send(any())).willReturn(new SendMoneyResult.Processing(summary(TxnStatus.INITIATED)));
    send("k1", BODY)
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("PROCESSING"))
        .andExpect(jsonPath("$.completedAt").doesNotExist());
  }

  @Test
  void ledgerRejectionIs422WithTxnId() throws Exception {
    given(sendMoneyService.send(any()))
        .willReturn(new SendMoneyResult.Rejected(FailureCode.INSUFFICIENT_FUNDS, Optional.of(TXN_ID)));
    send("k1", BODY)
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
        .andExpect(jsonPath("$.txnId").value(TXN_ID.toString()))
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.type").value("urn:problem-type:mfs:insufficient_funds"));
  }

  @Test
  void idempotencyConflictAndQuoteChangedAre409() throws Exception {
    given(sendMoneyService.send(any()))
        .willReturn(new SendMoneyResult.Rejected(FailureCode.IDEMPOTENCY_CONFLICT, Optional.empty()));
    send("k1", BODY).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

    given(sendMoneyService.send(any()))
        .willReturn(new SendMoneyResult.Rejected(FailureCode.QUOTE_CHANGED, Optional.empty()));
    send("k1", BODY).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUOTE_CHANGED"));
  }

  @Test
  void invalidQuoteTokenIs400() throws Exception {
    given(sendMoneyService.send(any())).willReturn(new SendMoneyResult.InvalidQuoteToken());
    send("k1", BODY).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUOTE_TOKEN"));
  }

  @Test
  void missingIdempotencyKeyIs400() throws Exception {
    mvc.perform(post("/api/v1/send-money").header("X-API-Key", API_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void tooLongIdempotencyKeyIs400() throws Exception {
    send("k".repeat(65), BODY).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void invalidBodyIs400WithFieldErrors() throws Exception {
    send("k1", """
        {"senderMsisdn":"123","receiverMsisdn":"01811000002","amount":-5,"currency":"USD"}""")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[*].field", hasItem("amount")))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
  }

  @Test
  void malformedJsonIs400() throws Exception {
    send("k1", "{not json").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void saturatedBulkheadIs503WithRetryAfter() throws Exception {
    given(sendMoneyService.send(any())).willThrow(new InvocationRejectedException("limit reached", sendMoneyService));
    send("k1", BODY)
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "1"))
        .andExpect(jsonPath("$.code").value("OVERLOADED"));
  }

  @Test
  void databaseDownIs503() throws Exception {
    given(sendMoneyService.send(any())).willThrow(new CannotGetJdbcConnectionException("down"));
    send("k1", BODY)
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "1"))
        .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
  }

  @Test
  void unexpectedErrorIs500() throws Exception {
    given(sendMoneyService.send(any())).willThrow(new IllegalStateException("boom"));
    send("k1", BODY).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
  }

  @Test
  void statusFoundAndNotFound() throws Exception {
    given(txnQueryService.find(TXN_ID)).willReturn(Optional.empty());
    authorized(get("/api/v1/send-money/{id}", TXN_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    authorized(get("/api/v1/send-money/{id}", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void quoteOkAndRejected() throws Exception {
    given(quoteService.quote(any())).willReturn(new QuoteResult.Quoted(
        "K***m M*a", 100_000, PRICING, "token", Instant.parse("2026-10-07T09:19:03Z")));
    authorized(post("/api/v1/send-money/quote").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.receiverName").value("K***m M*a"))
        .andExpect(jsonPath("$.totalDebit").value(100_500))
        .andExpect(jsonPath("$.quoteToken").value("token"));

    given(quoteService.quote(any())).willReturn(new QuoteResult.Rejected(FailureCode.SELF_TRANSFER));
    authorized(post("/api/v1/send-money/quote").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("SELF_TRANSFER"));
  }

  @Test
  void reconciliation() throws Exception {
    Instant from = Instant.parse("2026-10-07T00:00:00Z");
    Instant to = Instant.parse("2026-10-08T00:00:00Z");
    given(reconciliationService.reconcile(from, to)).willReturn(new ReconciliationResult(from, to, 3, false,
        List.of(new ReconciliationResult.Mismatch(TXN_ID, TxnStatus.FAILED,
            ReconciliationResult.LedgerView.POSTED, ReconciliationResult.Action.FLIPPED_TO_COMPLETED))));
    authorized(get("/api/v1/admin/reconciliation").param("from", from.toString()).param("to", to.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.checked").value(3))
        .andExpect(jsonPath("$.mismatches[0].action").value("FLIPPED_TO_COMPLETED"));
    authorized(get("/api/v1/admin/reconciliation").param("from", to.toString()).param("to", from.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    authorized(get("/api/v1/admin/reconciliation").param("from", from.toString()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void registerWallet() throws Exception {
    Wallet wallet = new Wallet(42, "01711000001", "Rahim Uddin", WalletType.CUSTOMER, WalletStatus.ACTIVE, 1,
        UUID.fromString("0192f5a4-0000-7000-8000-000000000200"));
    String body = """
        {"msisdn":"01711000001","holderName":"Rahim Uddin","kycTier":1}""";
    given(walletSupportService.register(any())).willReturn(new RegisterWalletResult.Registered(wallet, true));
    authorized(post("/api/v1/wallets").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.walletId").value(42));
    given(walletSupportService.register(any())).willReturn(new RegisterWalletResult.Registered(wallet, false));
    authorized(post("/api/v1/wallets").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk());
    given(walletSupportService.register(any())).willReturn(new RegisterWalletResult.MsisdnExists());
    authorized(post("/api/v1/wallets").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MSISDN_EXISTS"));
  }

  @Test
  void fundWallet() throws Exception {
    UUID fundingId = UUID.fromString("0192f5a4-0000-5000-8000-000000000000");
    given(walletSupportService.fund(any()))
        .willReturn(new WalletLookupResult.Funded(fundingId, "01711000001", 10_000));
    authorized(post("/api/v1/wallets/01711000001/fund").header("Idempotency-Key", "f1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10000}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fundingId").value(fundingId.toString()))
        .andExpect(jsonPath("$.status").value("POSTED"));
    given(walletSupportService.fund(any())).willReturn(new WalletLookupResult.WalletNotFound());
    authorized(post("/api/v1/wallets/01711000001/fund").header("Idempotency-Key", "f1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10000}"))
        .andExpect(status().isNotFound());
    authorized(post("/api/v1/wallets/01711000001/fund")
            .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10000}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void balance() throws Exception {
    given(walletSupportService.balance(anyString()))
        .willReturn(new WalletLookupResult.BalanceFound(new AccountBalance(100, 1_100, 0, 50, 950)));
    authorized(get("/api/v1/wallets/01711000001/balance"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.posted").value(1_000))
        .andExpect(jsonPath("$.pending").value(50))
        .andExpect(jsonPath("$.available").value(950));
    given(walletSupportService.balance(anyString())).willReturn(new WalletLookupResult.WalletNotFound());
    authorized(get("/api/v1/wallets/01711000001/balance")).andExpect(status().isNotFound());
    authorized(get("/api/v1/wallets/abc/balance"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void unknownPathKeepsProblemCode() throws Exception {
    authorized(get("/api/v1/nope"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void errorsListIsPresentForValidation() throws Exception {
    send("k1", """
        {"senderMsisdn":"123","receiverMsisdn":"01811000002","amount":1,"currency":"BDT"}""")
        .andExpect(jsonPath("$.errors[*].field", hasItem("senderMsisdn")));
  }

  private ResultActions send(String idempotencyKey, String body) throws Exception {
    return authorized(post("/api/v1/send-money").header("Idempotency-Key", idempotencyKey)
        .contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private ResultActions authorized(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.header("X-API-Key", API_KEY));
  }

  private static TxnSummary summary(TxnStatus status) {
    return new TxnSummary(TXN_ID, status, 100_000, PRICING, Optional.empty(),
        status == TxnStatus.COMPLETED ? Optional.of(COMPLETED_AT) : Optional.empty());
  }
}
