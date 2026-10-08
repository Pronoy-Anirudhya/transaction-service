package com.bracits.transactionservice.application;

import com.bracits.transactionservice.application.command.FundWalletCommand;
import com.bracits.transactionservice.application.command.RegisterWalletCommand;
import com.bracits.transactionservice.application.fakes.FakeLedgerAccountsPort;
import com.bracits.transactionservice.application.fakes.FakeLedgerAccountsPort.Funding;
import com.bracits.transactionservice.application.fakes.FakeLedgerQueryPort;
import com.bracits.transactionservice.application.fakes.Fixtures;
import com.bracits.transactionservice.application.fakes.InMemoryWalletRepository;
import com.bracits.transactionservice.application.fakes.MutableClock;
import com.bracits.transactionservice.application.fakes.SequentialTxnIds;
import com.bracits.transactionservice.application.mapper.WalletMapper;
import com.bracits.transactionservice.application.result.RegisterWalletResult;
import com.bracits.transactionservice.application.result.WalletLookupResult;
import com.bracits.transactionservice.domain.ledger.AccountBalance;
import com.bracits.transactionservice.domain.ledger.LedgerAccount;
import com.bracits.transactionservice.domain.ledger.LedgerAccountCode;
import com.bracits.transactionservice.domain.ledger.LedgerAccountFlag;
import com.bracits.transactionservice.domain.txn.TxnIds;
import com.bracits.transactionservice.domain.wallet.Wallet;
import com.bracits.transactionservice.domain.wallet.WalletStatus;
import com.bracits.transactionservice.domain.wallet.WalletType;
import com.bracits.transactionservice.port.out.LedgerUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static com.bracits.transactionservice.application.fakes.Fixtures.BUSINESS_DATE;
import static com.bracits.transactionservice.application.fakes.Fixtures.NOW;
import static com.bracits.transactionservice.application.fakes.Fixtures.RECEIVER_ACCOUNT;
import static com.bracits.transactionservice.application.fakes.Fixtures.SENDER_ACCOUNT;
import static com.bracits.transactionservice.application.fakes.Fixtures.SENDER_ID;
import static com.bracits.transactionservice.application.fakes.Fixtures.SENDER_MSISDN;
import static com.bracits.transactionservice.application.fakes.Fixtures.UNKNOWN_MSISDN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletSupportServiceTest {

  private static final String NEW_MSISDN = "8801811000042";

  private final MutableClock clock = new MutableClock(NOW);
  private final InMemoryWalletRepository wallets = new InMemoryWalletRepository();
  private final FakeLedgerAccountsPort accounts = new FakeLedgerAccountsPort();
  private final FakeLedgerQueryPort queries = new FakeLedgerQueryPort();
  private final SequentialTxnIds ids = new SequentialTxnIds(clock);
  private final WalletSupportService service = new WalletSupportService(
      wallets, accounts, queries, ids, new WalletMapper(), Fixtures.business(), clock);

  WalletSupportServiceTest() {
    wallets.put(Fixtures.sender());
  }

  private static Wallet registered(RegisterWalletResult result) {
    return ((RegisterWalletResult.Registered) result).wallet();
  }

  @Test
  void registeringANewWalletCreatesItsLedgerAccount() {
    RegisterWalletResult result = service.register(new RegisterWalletCommand(NEW_MSISDN, "Nasima Akter", 2));

    UUID accountId = ids.issued().getFirst();
    assertThat(result).isInstanceOfSatisfying(RegisterWalletResult.Registered.class, r -> {
      assertThat(r.created()).isTrue();
      assertThat(r.wallet().msisdn()).isEqualTo(NEW_MSISDN);
      assertThat(r.wallet().holderName()).isEqualTo("Nasima Akter");
      assertThat(r.wallet().kycTier()).isEqualTo(2);
      assertThat(r.wallet().type()).isEqualTo(WalletType.CUSTOMER);
      assertThat(r.wallet().status()).isEqualTo(WalletStatus.ACTIVE);
      assertThat(r.wallet().ledgerAccountId()).isEqualTo(accountId);
    });
    Wallet wallet = registered(result);
    assertThat(accounts.createCalls()).containsExactly(new LedgerAccount(accountId, LedgerAccountCode.CUSTOMER_WALLET,
        Set.of(LedgerAccountFlag.DEBITS_MUST_NOT_EXCEED_CREDITS), wallet.walletId()));
    assertThat(wallets.limitUsageDates()).containsEntry(wallet.walletId(), BUSINESS_DATE);
  }

  @Test
  void identicalReplayReEnsuresTheLedgerAccountAndReportsNotCreated() {
    RegisterWalletCommand command = new RegisterWalletCommand(NEW_MSISDN, "Nasima Akter", 1);
    Wallet first = registered(service.register(command));

    RegisterWalletResult replay = service.register(command);

    assertThat(replay).isEqualTo(new RegisterWalletResult.Registered(first, false));
    assertThat(accounts.createCalls()).hasSize(2).containsOnly(new WalletMapper().toLedgerAccount(first));
    assertThat(wallets.size()).isEqualTo(2);
  }

  @Test
  void differentHolderNameIsMsisdnExists() {
    service.register(new RegisterWalletCommand(NEW_MSISDN, "Nasima Akter", 1));

    assertThat(service.register(new RegisterWalletCommand(NEW_MSISDN, "Nasima Begum", 1)))
        .isEqualTo(new RegisterWalletResult.MsisdnExists());
    assertThat(accounts.createCalls()).hasSize(1);
  }

  @Test
  void differentTierIsMsisdnExists() {
    service.register(new RegisterWalletCommand(NEW_MSISDN, "Nasima Akter", 1));

    assertThat(service.register(new RegisterWalletCommand(NEW_MSISDN, "Nasima Akter", 2)))
        .isEqualTo(new RegisterWalletResult.MsisdnExists());
  }

  @Test
  void systemWalletWithTheSameMsisdnIsMsisdnExists() {
    wallets.put(new Wallet(77L, NEW_MSISDN, "Fee Income", WalletType.SYSTEM, WalletStatus.ACTIVE, 1,
        UUID.fromString("00000000-0000-0000-0000-00000000c800")));

    assertThat(service.register(new RegisterWalletCommand(NEW_MSISDN, "Fee Income", 1)))
        .isEqualTo(new RegisterWalletResult.MsisdnExists());
    assertThat(accounts.createCalls()).isEmpty();
  }

  @Test
  void fundingUsesADeterministicFundingId() {
    WalletLookupResult.Funding result = service.fund(new FundWalletCommand(SENDER_MSISDN, "fund-1", 1_000_000L));

    UUID expectedId = TxnIds.fundingId(SENDER_ID, "fund-1");
    assertThat(result).isEqualTo(new WalletLookupResult.Funded(expectedId, SENDER_MSISDN, 1_000_000L));
    assertThat(accounts.fundings()).containsExactly(new Funding(expectedId, SENDER_ACCOUNT, 1_000_000L));
  }

  @Test
  void retriedFundingReusesTheIdAndANewKeyGetsANewOne() {
    service.fund(new FundWalletCommand(SENDER_MSISDN, "fund-1", 500L));
    service.fund(new FundWalletCommand(SENDER_MSISDN, "fund-1", 500L));
    service.fund(new FundWalletCommand(SENDER_MSISDN, "fund-2", 500L));

    assertThat(accounts.fundings()).extracting(Funding::fundingId).containsExactly(
        TxnIds.fundingId(SENDER_ID, "fund-1"), TxnIds.fundingId(SENDER_ID, "fund-1"),
        TxnIds.fundingId(SENDER_ID, "fund-2"));
    assertThat(accounts.fundings().get(0).fundingId()).isNotEqualTo(accounts.fundings().get(2).fundingId());
  }

  @Test
  void fundingAnUnknownWalletIsNotFoundWithoutALedgerCall() {
    assertThat(service.fund(new FundWalletCommand(UNKNOWN_MSISDN, "fund-1", 500L)))
        .isEqualTo(new WalletLookupResult.WalletNotFound());
    assertThat(accounts.fundings()).isEmpty();
  }

  @Test
  void fundingAWalletTheLedgerDoesNotKnowIsNotFound() {
    accounts.unknownAccount(SENDER_ACCOUNT);

    assertThat(service.fund(new FundWalletCommand(SENDER_MSISDN, "fund-1", 500L)))
        .isEqualTo(new WalletLookupResult.WalletNotFound());
  }

  @Test
  void balanceIsReadThroughToTheLedger() {
    AccountBalance balance = new AccountBalance(100L, 1_000L, 0L, 50L, 900L);
    queries.balance(SENDER_ACCOUNT, balance);

    assertThat(service.balance(SENDER_MSISDN)).isEqualTo(new WalletLookupResult.BalanceFound(balance));
  }

  @Test
  void balanceOfAnUnknownWalletIsNotFound() {
    assertThat(service.balance(UNKNOWN_MSISDN)).isEqualTo(new WalletLookupResult.WalletNotFound());
  }

  @Test
  void balanceOfAWalletTheLedgerDoesNotKnowIsNotFound() {
    assertThat(service.balance(SENDER_MSISDN)).isEqualTo(new WalletLookupResult.WalletNotFound());
  }

  @Test
  void ledgerUnavailableOnBalancePropagates() {
    queries.failBalance(SENDER_ACCOUNT, new LedgerUnavailableException("503"));

    assertThatThrownBy(() -> service.balance(SENDER_MSISDN)).isInstanceOf(LedgerUnavailableException.class);
  }

  @Test
  void otherWalletsAccountsAreNotTouched() {
    queries.balance(RECEIVER_ACCOUNT, new AccountBalance(0L, 1L, 0L, 0L, 1L));

    assertThat(service.balance(SENDER_MSISDN)).isEqualTo(new WalletLookupResult.WalletNotFound());
  }
}
