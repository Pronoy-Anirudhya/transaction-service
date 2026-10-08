package com.bracits.transactionservice.domain.fee;

import com.bracits.transactionservice.domain.Pricing;
import com.bracits.transactionservice.domain.Product;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

/** BR-04..BR-07 over the full realistic input range, with {@link BigInteger} as the exact oracle (tests only). */
class FeeMathProperties {

  private static final BigInteger BPS = BigInteger.valueOf(10_000);
  private static final BigInteger TWO = BigInteger.TWO;
  private static final long MAX_AMOUNT = 1_000_000_000_000L;

  @Property
  void splitPartsAreExact(
      @ForAll @LongRange(min = 0, max = MAX_AMOUNT) long fee,
      @ForAll @IntRange(min = 0, max = 5_000) int vatBps,
      @ForAll @IntRange(min = 0, max = 10_000) int commissionBps) {
    Pricing pricing = FeeMath.split(fee, vatBps, commissionBps);

    assertSplit(pricing, fee, vatBps, commissionBps);
  }

  @Property
  void percentIsHalfUp(
      @ForAll @LongRange(min = 1, max = MAX_AMOUNT) long amount,
      @ForAll @IntRange(min = 0, max = 10_000) int bps) {
    BigInteger exact = halfUp(BigInteger.valueOf(amount).multiply(BigInteger.valueOf(bps)), BPS);

    assertThat(FeeMath.percentHalfUp(amount, bps)).isEqualTo(exact.longValueExact());
  }

  @Property
  void slabPricingIsClampedAndExact(
      @ForAll @LongRange(min = 1, max = MAX_AMOUNT) long amount,
      @ForAll FeeType feeType,
      @ForAll @LongRange(min = 0, max = 10_000) long feeValue,
      @ForAll @LongRange(min = 0, max = 100_000) long feeMin,
      @ForAll @LongRange(min = 0, max = 10_000_000) long feeMaxExtra,
      @ForAll boolean hasFeeMax,
      @ForAll @IntRange(min = 0, max = 5_000) int vatBps,
      @ForAll @IntRange(min = 0, max = 10_000) int commissionBps) {
    OptionalLong feeMax = hasFeeMax ? OptionalLong.of(feeMin + feeMaxExtra) : OptionalLong.empty();
    FeeRule rule = new FeeRule(1, Product.SEND_MONEY, 1, 1, MAX_AMOUNT, feeType, feeValue, feeMin, feeMax,
        vatBps, commissionBps, true);

    Optional<Pricing> priced = new SlabFeeCalculator(() -> List.of(rule)).price(Product.SEND_MONEY, 1, amount);

    BigInteger raw = switch (feeType) {
      case FLAT -> BigInteger.valueOf(feeValue);
      case PERCENT -> halfUp(BigInteger.valueOf(amount).multiply(BigInteger.valueOf(feeValue)), BPS);
    };
    BigInteger expectedFee = raw.max(BigInteger.valueOf(feeMin));
    if (feeMax.isPresent()) {
      expectedFee = expectedFee.min(BigInteger.valueOf(feeMax.getAsLong()));
    }
    assertThat(priced).isPresent();
    assertThat(priced.get().fee()).isEqualTo(expectedFee.longValueExact());
    assertSplit(priced.get(), expectedFee.longValueExact(), vatBps, commissionBps);
  }

  private static void assertSplit(Pricing pricing, long fee, int vatBps, int commissionBps) {
    BigInteger f = BigInteger.valueOf(fee);
    BigInteger vat = halfUp(f.multiply(BigInteger.valueOf(vatBps)), BPS.add(BigInteger.valueOf(vatBps)));
    BigInteger commission = f.subtract(vat).multiply(BigInteger.valueOf(commissionBps)).divide(BPS);

    assertThat(pricing.fee()).isEqualTo(fee);
    assertThat(pricing.vat()).isEqualTo(vat.longValueExact());
    assertThat(pricing.commission()).isEqualTo(commission.longValueExact());
    assertThat(pricing.vat()).isNotNegative();
    assertThat(pricing.commission()).isNotNegative();
    assertThat(pricing.feeIncome()).isNotNegative();
    assertThat(pricing.vat() + pricing.commission() + pricing.feeIncome()).isEqualTo(fee);
  }

  /** floor((2n + d) / 2d) = n / d rounded half-up, for n >= 0 and d > 0. */
  private static BigInteger halfUp(BigInteger numerator, BigInteger denominator) {
    return numerator.multiply(TWO).add(denominator).divide(denominator.multiply(TWO));
  }
}
