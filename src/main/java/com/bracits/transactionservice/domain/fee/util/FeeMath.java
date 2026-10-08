package com.bracits.transactionservice.domain.fee.util;

import com.bracits.transactionservice.domain.constant.DomainConstants;
import com.bracits.transactionservice.domain.fee.constant.FeeMessages;
import com.bracits.transactionservice.domain.model.Pricing;

/**
 * Pure integer fee arithmetic (BR-04..BR-07). Values are poisha and basis points; every
 * multiplication is overflow-checked. Never floating point or BigDecimal.
 */
public final class FeeMath {

  private static final long BPS = DomainConstants.BPS_DENOMINATOR;

  private FeeMath() {
  }

  /**
   * {@code amount × bps / 10,000}, rounded half-up (PERCENT fee, BR-04).
   */
  public static long percentHalfUp(long amount, long bps) {
    requireNonNegative(amount);
    requireNonNegativeBps(bps);
    return divideHalfUp(Math.multiplyExact(amount, bps), BPS);
  }

  /**
   * VAT contained in a VAT-inclusive fee: {@code fee × vatBps / (10,000 + vatBps)}, rounded half-up
   * (BR-05).
   */
  public static long vatHalfUp(long fee, int vatBps) {
    requireNonNegative(fee);
    requireNonNegativeBps(vatBps);
    return divideHalfUp(Math.multiplyExact(fee, (long) vatBps), Math.addExact(BPS, vatBps));
  }

  /**
   * {@code netOfVat × commissionBps / 10,000}, rounded down (BR-06).
   */
  public static long commissionFloor(long netOfVat, int commissionBps) {
    requireNonNegative(netOfVat);
    requireCommissionBps(commissionBps);
    return Math.multiplyExact(netOfVat, (long) commissionBps) / BPS;
  }

  /**
   * Splits a fee into VAT, commission and fee income (BR-05..BR-07). The parts always add up to the
   * fee.
   */
  public static Pricing split(long fee, int vatBps, int commissionBps) {
    long vat = vatHalfUp(fee, vatBps);
    long netOfVat = Math.subtractExact(fee, vat);
    long commission = commissionFloor(netOfVat, commissionBps);
    long feeIncome = Math.subtractExact(netOfVat, commission);

    return new Pricing(fee, vat, commission, feeIncome);
  }

  /**
   * {@code numerator / denominator} rounded half-up, for a non-negative numerator and a positive
   * denominator.
   */
  private static long divideHalfUp(long numerator, long denominator) {
    long quotient = numerator / denominator;
    long remainder = numerator % denominator;
    // remainder < denominator (at most 10,000 + Integer.MAX_VALUE here), so doubling it cannot overflow.
    return remainder * 2 >= denominator ? quotient + 1 : quotient;
  }

  private static void requireNonNegative(long value) {
    if (value < 0) {
      throw new IllegalArgumentException(FeeMessages.NEGATIVE_FEE_INPUT.formatted(value));
    }
  }

  private static void requireNonNegativeBps(long bps) {
    if (bps < 0) {
      throw new IllegalArgumentException(FeeMessages.NEGATIVE_BPS.formatted(bps));
    }
  }

  private static void requireCommissionBps(int commissionBps) {
    requireNonNegativeBps(commissionBps);
    if (commissionBps > BPS) {
      throw new IllegalArgumentException(
          FeeMessages.COMMISSION_BPS_TOO_HIGH.formatted(BPS, commissionBps));
    }
  }
}
