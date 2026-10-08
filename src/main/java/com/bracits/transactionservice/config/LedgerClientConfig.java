package com.bracits.transactionservice.config;

import com.bracits.transactionservice.adapter.out.ledger.LedgerApiConstants;
import com.bracits.transactionservice.adapter.out.ledger.LedgerRetryPredicate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Ledger client wiring (spec 8.3, P9, P10): one shared HTTP/1.1 keep-alive {@link HttpClient} with explicit connect
 * and read timeouts, one ledger {@link RestClient} built from Boot's {@link RestClient.Builder} (so observation and
 * {@code traceparent} propagation apply), and the ledger {@link RetryTemplate}. {@link EnableResilientMethods}
 * switches on {@code @ConcurrencyLimit} / {@code @Retryable} for the whole application.
 */
@Configuration(proxyBeanMethods = false)
@EnableResilientMethods
public class LedgerClientConfig {

  /** Shared JDK client; closed by the container on shutdown (inferred {@code close()}). */
  @Bean(LedgerApiConstants.HTTP_CLIENT_BEAN)
  HttpClient ledgerHttpClient(LedgerProperties properties) {
    return HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(properties.connectTimeout())
        .build();
  }

  @Bean(LedgerApiConstants.REQUEST_FACTORY_BEAN)
  JdkClientHttpRequestFactory ledgerClientHttpRequestFactory(
      @Qualifier(LedgerApiConstants.HTTP_CLIENT_BEAN) HttpClient httpClient, LedgerProperties properties) {
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.readTimeout());
    return requestFactory;
  }

  @Bean(LedgerApiConstants.REST_CLIENT_BEAN)
  RestClient ledgerRestClient(
      RestClient.Builder restClientBuilder,
      @Qualifier(LedgerApiConstants.REQUEST_FACTORY_BEAN) ClientHttpRequestFactory requestFactory,
      LedgerProperties properties) {
    return restClientBuilder
        .baseUrl(properties.baseUrl())
        .requestFactory(requestFactory)
        .build();
  }

  /**
   * At most {@code maxRetries} retries, exponential backoff with jitter, only for 503 / I/O / timeout, and an overall
   * {@code totalBudget} (Spring checks it before each backoff; the client additionally applies decision B7).
   */
  @Bean(LedgerApiConstants.RETRY_TEMPLATE_BEAN)
  RetryTemplate ledgerRetryTemplate(LedgerProperties properties) {
    RetryPolicy retryPolicy = RetryPolicy.builder()
        .maxRetries(properties.maxRetries())
        .delay(properties.retry().initialDelay())
        .multiplier(properties.retry().multiplier())
        .jitter(properties.retry().jitter())
        .timeout(properties.totalBudget())
        .predicate(new LedgerRetryPredicate())
        .build();
    return new RetryTemplate(retryPolicy);
  }
}
