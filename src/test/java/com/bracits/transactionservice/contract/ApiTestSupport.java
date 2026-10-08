package com.bracits.transactionservice.contract;

import com.bracits.transactionservice.api.controller.OpenApiController;
import com.bracits.transactionservice.api.controller.QuoteController;
import com.bracits.transactionservice.api.controller.ReconciliationController;
import com.bracits.transactionservice.api.controller.SendMoneyController;
import com.bracits.transactionservice.api.controller.WalletBalanceController;
import com.bracits.transactionservice.api.controller.WalletSupportController;
import com.bracits.transactionservice.api.mapper.ProblemMapper;
import com.bracits.transactionservice.api.mapper.QuoteApiMapper;
import com.bracits.transactionservice.api.mapper.ReconciliationApiMapper;
import com.bracits.transactionservice.api.mapper.SendMoneyApiMapper;
import com.bracits.transactionservice.api.mapper.WalletApiMapper;
import com.bracits.transactionservice.application.QuoteService;
import com.bracits.transactionservice.application.ReconciliationService;
import com.bracits.transactionservice.application.SendMoneyService;
import com.bracits.transactionservice.application.TxnQueryService;
import com.bracits.transactionservice.application.WalletSupportService;
import com.bracits.transactionservice.config.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Web slice with every controller, the Problem Details advice and the API-key filter; the use cases are mocked. The
 * {@code test} profile enables the support endpoints. Secrets referenced by application.properties are supplied here.
 */
@WebMvcTest(controllers = {
    SendMoneyController.class,
    QuoteController.class,
    ReconciliationController.class,
    WalletSupportController.class,
    WalletBalanceController.class,
    OpenApiController.class})
@Import({ProblemMapper.class, SendMoneyApiMapper.class, QuoteApiMapper.class, WalletApiMapper.class,
    ReconciliationApiMapper.class, ApiTestSupport.SecurityConfig.class})
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

  /** The slice does not scan {@code @ConfigurationProperties}; the API-key filter needs this one. */
  @TestConfiguration
  @EnableConfigurationProperties(SecurityProperties.class)
  static class SecurityConfig {
  }
}
