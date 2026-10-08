package com.bracits.transactionservice.config;

import com.bracits.transactionservice.port.out.TxnRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;

import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/** Gauges {@code txn_in_doubt} and {@code events_unpublished} (spec 12), read on scrape via the partial indexes. */
@Configuration
public class MetricsConfig {

  @Bean
  MeterBinder txnGauges(TxnRepository txns) {
    return registry -> {
      Gauge.builder(MetricConstants.TXN_IN_DOUBT, txns, safely(TxnRepository::countInDoubt)).register(registry);
      Gauge.builder(MetricConstants.EVENTS_UNPUBLISHED, txns, safely(TxnRepository::countUnpublished))
          .register(registry);
    };
  }

  private static ToDoubleFunction<TxnRepository> safely(ToLongFunction<TxnRepository> count) {
    return repository -> {
      try {
        return count.applyAsLong(repository);
      } catch (DataAccessException e) {
        return Double.NaN;
      }
    };
  }
}
