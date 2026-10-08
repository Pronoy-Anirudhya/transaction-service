package com.bracits.transactionservice.contract;

import com.bracits.transactionservice.api.controller.OpenApiController;
import com.bracits.transactionservice.api.controller.QuoteController;
import com.bracits.transactionservice.api.controller.ReconciliationController;
import com.bracits.transactionservice.api.controller.SendMoneyController;
import com.bracits.transactionservice.api.controller.WalletBalanceController;
import com.bracits.transactionservice.api.controller.WalletSupportController;
import com.bracits.transactionservice.api.mapper.impl.ProblemMapperImpl;
import com.bracits.transactionservice.api.mapper.impl.QuoteApiMapperImpl;
import com.bracits.transactionservice.api.mapper.impl.ReconciliationApiMapperImpl;
import com.bracits.transactionservice.api.mapper.impl.SendMoneyApiMapperImpl;
import com.bracits.transactionservice.api.mapper.impl.WalletApiMapperImpl;
import com.bracits.transactionservice.application.query.service.TxnQueryService;
import com.bracits.transactionservice.application.quote.service.QuoteService;
import com.bracits.transactionservice.application.reconciliation.service.ReconciliationService;
import com.bracits.transactionservice.application.sendmoney.service.SendMoneyService;
import com.bracits.transactionservice.application.wallet.service.WalletSupportService;
import com.bracits.transactionservice.config.properties.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Web slice with every controller, the Problem Details advice and the API-key filter; the use cases
 * are mocked. The {@code test} profile enables the support endpoints. Secrets referenced by
 * application.properties are supplied here.
 */
@WebMvcTest(controllers = {
    SendMoneyController.class,
    QuoteController.class,
    ReconciliationController.class,
    WalletSupportController.class,
    WalletBalanceController.class,
    OpenApiController.class})
@Import({ProblemMapperImpl.class, SendMoneyApiMapperImpl.class, QuoteApiMapperImpl.class,
    WalletApiMapperImpl.class,
    ReconciliationApiMapperImpl.class, ApiTestSupport.SecurityConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "API_KEY=" + ApiTestSupport.API_KEY,
    "QUOTE_SIGNING_KEY=slice-signing-key",
    "TXN_DB_PASSWORD=unused",
    "RABBIT_USER=unused",
    "RABBIT_PASSWORD=unused"})
abstract class ApiTestSupport {

  static final String API_KEY = "slice-api-key";

  @MockitoBean
  SendMoneyService sendMoneyService;

  @MockitoBean
  TxnQueryService txnQueryService;

  @MockitoBean
  QuoteService quoteService;

  @MockitoBean
  ReconciliationService reconciliationService;

  @MockitoBean
  WalletSupportService walletSupportService;

  /**
   * The slice does not scan {@code @ConfigurationProperties}; the API-key filter needs this one.
   */
  @TestConfiguration
  @EnableConfigurationProperties(SecurityProperties.class)
  static class SecurityConfig {

  }
}
