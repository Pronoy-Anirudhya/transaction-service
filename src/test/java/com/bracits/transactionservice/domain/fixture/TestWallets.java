package com.bracits.transactionservice.domain.fixture;

import com.bracits.transactionservice.domain.wallet.enums.WalletStatus;
import com.bracits.transactionservice.domain.wallet.enums.WalletType;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import java.util.UUID;

/**
 * Wallet fixtures for domain tests.
 */
public final class TestWallets {

  public static final String SENDER_MSISDN = "8801711000001";
  public static final String RECEIVER_MSISDN = "8801711000002";
  public static final UUID SENDER_ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000101");
  public static final UUID RECEIVER_ACCOUNT = UUID.fromString(
      "00000000-0000-0000-0000-000000000102");

  private TestWallets() {
  }

  public static Wallet sender() {
    return wallet(1L, SENDER_MSISDN, SENDER_ACCOUNT, WalletType.CUSTOMER, WalletStatus.ACTIVE);
  }

  public static Wallet receiver() {
    return wallet(2L, RECEIVER_MSISDN, RECEIVER_ACCOUNT, WalletType.CUSTOMER, WalletStatus.ACTIVE);
  }

  public static Wallet wallet(long id, String msisdn, UUID account, WalletType type,
      WalletStatus status) {
    return new Wallet(id, msisdn, "Holder " + id, type, status, 1, account);
  }

  public static Wallet withStatus(Wallet w, WalletStatus status) {
    return new Wallet(w.walletId(), w.msisdn(), w.holderName(), w.type(), status, w.kycTier(),
        w.ledgerAccountId());
  }

  public static Wallet withType(Wallet w, WalletType type) {
    return new Wallet(w.walletId(), w.msisdn(), w.holderName(), type, w.status(), w.kycTier(),
        w.ledgerAccountId());
  }
}
