package com.bracits.transactionservice.application.fakes;

import com.bracits.transactionservice.application.command.SendMoneyCommand;
import com.bracits.transactionservice.config.BusinessProperties;
import com.bracits.transactionservice.config.QuoteProperties;
import com.bracits.transactionservice.config.ReconciliationProperties;
import com.bracits.transactionservice.config.RepairProperties;
import com.bracits.transactionservice.domain.Pricing;
import com.bracits.transactionservice.domain.ledger.SystemAccounts;
import com.bracits.transactionservice.domain.wallet.Wallet;
import com.bracits.transactionservice.domain.wallet.WalletStatus;
import com.bracits.transactionservice.domain.wallet.WalletType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/** Literal fixture values shared by the application-layer tests. */
public final class Fixtures {

  /** 20:30 UTC on 7 Oct is already 8 Oct in Asia/Dhaka (UTC+6): exercises the business-date zone. */
  public static final Instant NOW = Instant.parse("2026-10-07T20:30:00Z");
  public static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
  public static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 8);

  public static final String SENDER_MSISDN = "8801711000001";
  public static final String RECEIVER_MSISDN = "8801711000002";
  public static final String UNKNOWN_MSISDN = "8801711000099";
  public static final long SENDER_ID = 1L;
  public static final long RECEIVER_ID = 2L;
  public static final UUID SENDER_ACCOUNT = UUID.fromString("0192f5a4-0000-0000-0000-000000000100");
  public static final UUID RECEIVER_ACCOUNT = UUID.fromString("0192f5a4-0000-0000-0000-000000000200");
  public static final SystemAccounts SYSTEM_ACCOUNTS = new SystemAccounts(
      UUID.fromString("00000000-0000-0000-0000-00000000c800"),
      UUID.fromString("00000000-0000-0000-0000-00000000d200"),
      UUID.fromString("00000000-0000-0000-0000-00000000dc00"),
      UUID.fromString("00000000-0000-0000-0000-000000038400"));

  public static final String CURRENCY = "BDT";
  public static final String KEY = "idem-key-0001";
  /** 1,000.00 BDT: priced by the 5 BDT flat slab → fee 500 = VAT 65 + commission 87 + fee income 348. */
  public static final long AMOUNT = 100_000L;
  public static final Pricing STANDARD_PRICING = new Pricing(500L, 65L, 87L, 348L);
  /** 50.00 BDT: priced by the free slab. */
  public static final long FREE_AMOUNT = 5_000L;
  public static final long LEDGER_TS = 1_791_350_858_928_000_000L;
  public static final String SIGNING_KEY = "unit-test-quote-signing-key";

  private Fixtures() {
  }

  public static Wallet sender() {
    return new Wallet(SENDER_ID, SENDER_MSISDN, "Rahim Uddin", WalletType.CUSTOMER, WalletStatus.ACTIVE, 1,
        SENDER_ACCOUNT);
  }

  public static Wallet receiver() {
    return new Wallet(RECEIVER_ID, RECEIVER_MSISDN, "Karim Mia", WalletType.CUSTOMER, WalletStatus.ACTIVE, 1,
        RECEIVER_ACCOUNT);
  }

  public static Wallet withStatus(Wallet w, WalletStatus status) {
    return new Wallet(w.walletId(), w.msisdn(), w.holderName(), w.type(), status, w.kycTier(), w.ledgerAccountId());
  }

  public static Wallet withType(Wallet w, WalletType type) {
    return new Wallet(w.walletId(), w.msisdn(), w.holderName(), type, w.status(), w.kycTier(), w.ledgerAccountId());
  }

  public static Wallet withTier(Wallet w, int tier) {
    return new Wallet(w.walletId(), w.msisdn(), w.holderName(), w.type(), w.status(), tier, w.ledgerAccountId());
  }

  public static SendMoneyCommand command() {
    return command(KEY, AMOUNT);
  }

  public static SendMoneyCommand command(String key, long amount) {
    return command(key, SENDER_MSISDN, RECEIVER_MSISDN, amount);
  }

  public static SendMoneyCommand command(String key, String sender, String receiver, long amount) {
    return new SendMoneyCommand(key, sender, receiver, amount, CURRENCY, Optional.of("rent"), Optional.empty());
  }

  public static SendMoneyCommand withToken(SendMoneyCommand c, String token) {
    return new SendMoneyCommand(c.idempotencyKey(), c.senderMsisdn(), c.receiverMsisdn(), c.amount(), c.currency(),
        c.reference(), Optional.of(token));
  }

  public static SendMoneyCommand withReference(SendMoneyCommand c, Optional<String> reference) {
    return new SendMoneyCommand(c.idempotencyKey(), c.senderMsisdn(), c.receiverMsisdn(), c.amount(), c.currency(),
        reference, c.quoteToken());
  }

  public static BusinessProperties business() {
    return new BusinessProperties(DHAKA);
  }

  /** Spec 12 defaults: interval 1 s, batch 200, lease 30 s, min age 3 s, unknown recheck 2 s, 1 s → 60 s, alert 10. */
  public static RepairProperties repair() {
    return new RepairProperties(Duration.ofSeconds(1), 200, Duration.ofSeconds(30), Duration.ofSeconds(3),
        Duration.ofSeconds(2), Duration.ofSeconds(1), Duration.ofSeconds(60), 10, 4);
  }

  public static QuoteProperties quote() {
    return new QuoteProperties(Duration.ofMinutes(5), SIGNING_KEY);
  }

  public static ReconciliationProperties reconciliation(int maxRows) {
    return new ReconciliationProperties(maxRows, 4);
  }
}
