package com.bracits.transactionservice.adapter.out.jdbc.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.transactionservice.domain.enums.Product;
import com.bracits.transactionservice.domain.fee.enums.FeeType;
import com.bracits.transactionservice.domain.fee.model.FeeRule;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class SchemaAndRuleRepositoryTest extends PostgresTestSupport {

  @Test
  void seededLimitRuleIsReadableThroughTheMapper() {
    assertThat(rules.findLimitRules()).containsExactly(TIER1);
  }

  @Test
  void seededFeeSlabsAreReadableThroughTheMapper() {
    List<FeeRule> fees = rules.findActiveFeeRules();

    assertThat(fees).hasSize(2);

    FeeRule free = fees.get(0);
    FeeRule flat = fees.get(1);

    assertThat(free.product()).isEqualTo(Product.SEND_MONEY);
    assertThat(free.kycTier()).isEqualTo(1);
    assertThat(free.minAmount()).isEqualTo(100L);
    assertThat(free.maxAmount()).isEqualTo(10_000L);
    assertThat(free.feeType()).isEqualTo(FeeType.FLAT);
    assertThat(free.feeValue()).isZero();

    assertThat(flat.minAmount()).isEqualTo(10_001L);
    assertThat(flat.maxAmount()).isEqualTo(2_500_000L);
    assertThat(flat.feeValue()).isEqualTo(500L);

    assertThat(fees).allSatisfy(rule -> {
      assertThat(rule.feeMin()).isZero();
      assertThat(rule.feeMax()).isEqualTo(OptionalLong.empty());
      assertThat(rule.vatBps()).isEqualTo(1_500);
      assertThat(rule.commissionBps()).isEqualTo(2_000);
      assertThat(rule.active()).isTrue();
    });
  }

  @Test
  void inactiveFeeRulesAreNotReturned() {
    jdbc.sql("UPDATE fee_rule SET active = false WHERE fee_value = 0").update();

    try {
      assertThat(rules.findActiveFeeRules()).extracting(FeeRule::feeValue).containsExactly(500L);
    } finally {
      jdbc.sql("UPDATE fee_rule SET active = true").update();
    }
  }

  @Test
  void onlyTheSpecIndexesExist() {
    List<String> indexes = jdbc.sql("""
            SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
             ORDER BY indexname
            """)
        .query(String.class)
        .list();

    assertThat(indexes).containsExactlyInAnyOrder(
        "wallet_pkey", "wallet_msisdn_key", "wallet_ledger_account_id_key",
        "wallet_limit_usage_pkey", "limit_rule_pkey", "fee_rule_pkey",
        "send_money_txn_pkey", "send_money_txn_sender_wallet_id_client_ref_key",
        "smt_in_doubt", "smt_unpublished");
  }

  @Test
  void hotTablesHaveFillfactorAndAggressiveAutovacuum() {
    assertThat(reloptions("send_money_txn")).containsExactlyInAnyOrder(
        "fillfactor=80", "autovacuum_vacuum_scale_factor=0.02",
        "autovacuum_analyze_scale_factor=0.02");
    assertThat(reloptions("wallet_limit_usage")).containsExactlyInAnyOrder(
        "fillfactor=70", "autovacuum_vacuum_scale_factor=0.02",
        "autovacuum_analyze_scale_factor=0.02");
  }

  private static List<String> reloptions(String table) {
    return jdbc.sql("SELECT unnest(reloptions) FROM pg_class WHERE relname = :t").param("t", table)
        .query(String.class).list();
  }
}
