package com.bracits.transactionservice.domain.wallet.util;

import com.bracits.transactionservice.domain.constant.DomainConstants;

/**
 * Masks an MSISDN to its last digits for logs (NFR-09).
 */
public final class MsisdnMasker {

  private MsisdnMasker() {
  }

  public static String mask(String msisdn) {
    if (msisdn == null || msisdn.isEmpty()) {
      return String.valueOf(msisdn);
    }

    int visible = Math.min(DomainConstants.MSISDN_VISIBLE_DIGITS, msisdn.length());
    int hidden = msisdn.length() - visible;
    return String.valueOf(DomainConstants.MASK_CHAR).repeat(hidden) + msisdn.substring(hidden);
  }
}
