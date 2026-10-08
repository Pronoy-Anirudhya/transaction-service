package com.bracits.transactionservice.application.fakes;

import com.bracits.transactionservice.domain.Product;
import com.bracits.transactionservice.domain.fee.FeeRule;
import com.bracits.transactionservice.domain.fee.FeeType;
import com.bracits.transactionservice.domain.limit.LimitRule;
import com.bracits.transactionservice.port.out.RuleRepository;

import java.util.List;
import java.util.OptionalLong;

/** Fee and limit rules, seeded with the spec 3 placeholder data (all values poisha). */
public final class InMemoryRuleRepository implements RuleRepository {

  public static final int VAT_BPS = 1_500;
  public static final int COMMISSION_BPS = 2_000;

  /** Tier 1: 10–25,000 BDT per transaction; daily 50,000 BDT / 50; monthly 300,000 BDT / 200. */
  public static final LimitRule TIER_1_LIMIT =
      new LimitRule(Product.SEND_MONEY, 1, 1_000L, 2_500_000L, 5_000_000L, 50, 30_000_000L, 200);

  private volatile List<FeeRule> feeRules = standardFeeRules(500L);
  private volatile List<LimitRule> limitRules = List.of(TIER_1_LIMIT);

  /** Fee 0 for 1–100 BDT; {@code secondSlabFee} poisha flat for 100.01–25,000 BDT. */
  public static List<FeeRule> standardFeeRules(long secondSlabFee) {
    return List.of(
        new FeeRule(1L, Product.SEND_MONEY, 1, 100L, 10_000L, FeeType.FLAT, 0L, 0L, OptionalLong.empty(),
            VAT_BPS, COMMISSION_BPS, true),
        new FeeRule(2L, Product.SEND_MONEY, 1, 10_001L, 2_500_000L, FeeType.FLAT, secondSlabFee, 0L,
            OptionalLong.empty(), VAT_BPS, COMMISSION_BPS, true));
  }

  public void setFeeRules(List<FeeRule> rules) {
    feeRules = List.copyOf(rules);
  }

  public void setLimitRules(List<LimitRule> rules) {
    limitRules = List.copyOf(rules);
  }

  @Override
  public List<FeeRule> findActiveFeeRules() {
    return feeRules.stream().filter(FeeRule::active).toList();
  }

  @Override
  public List<LimitRule> findLimitRules() {
    return limitRules;
  }
}
