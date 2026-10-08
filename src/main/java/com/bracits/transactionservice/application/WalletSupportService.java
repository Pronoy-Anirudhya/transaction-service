package com.bracits.transactionservice.application;

import com.bracits.transactionservice.application.command.FundWalletCommand;
import com.bracits.transactionservice.application.command.RegisterWalletCommand;
import com.bracits.transactionservice.application.mapper.WalletMapper;
import com.bracits.transactionservice.application.result.RegisterWalletResult;
import com.bracits.transactionservice.application.result.WalletLookupResult;
import com.bracits.transactionservice.config.BusinessProperties;
import com.bracits.transactionservice.domain.txn.TxnIds;
import com.bracits.transactionservice.domain.wallet.MsisdnMasker;
import com.bracits.transactionservice.domain.wallet.Wallet;
import com.bracits.transactionservice.port.out.LedgerAccountNotFoundException;
import com.bracits.transactionservice.port.out.LedgerAccountsPort;
import com.bracits.transactionservice.port.out.LedgerQueryPort;
import com.bracits.transactionservice.port.out.TxnIdGenerator;
import com.bracits.transactionservice.port.out.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * FR-09 support use cases: register a wallet, fund it from the issuance account, and read its balance through to the
 * ledger. All idempotent (decisions B11, B12).
 */
@Service
public class WalletSupportService {

  private static final Logger LOG = LoggerFactory.getLogger(WalletSupportService.class);

  private final WalletRepository wallets;
  private final LedgerAccountsPort ledgerAccounts;
  private final LedgerQueryPort ledgerQueries;
  private final TxnIdGenerator ids;
  private final WalletMapper walletMapper;
  private final BusinessProperties business;
  private final Clock clock;

  public WalletSupportService(
      WalletRepository wallets,
      LedgerAccountsPort ledgerAccounts,
      LedgerQueryPort ledgerQueries,
      TxnIdGenerator ids,
      WalletMapper walletMapper,
      BusinessProperties business,
      Clock clock) {
    this.wallets = wallets;
    this.ledgerAccounts = ledgerAccounts;
    this.ledgerQueries = ledgerQueries;
    this.ids = ids;
    this.walletMapper = walletMapper;
    this.business = business;
    this.clock = clock;
  }

  /**
   * Inserts the wallet (PostgreSQL assigns nothing but the ID; the ledger account ID is time-ordered, spec 6.4), then
   * creates its ledger account. A replay with identical fields re-ensures the ledger account.
   */
  public RegisterWalletResult register(RegisterWalletCommand command) {
    LocalDate today = LocalDate.now(clock.withZone(business.zone()));
    Optional<Wallet> inserted = wallets.insertIfAbsent(walletMapper.toNewWallet(command, ids.next()), today);
    if (inserted.isPresent()) {
      return ensureLedgerAccount(inserted.get(), true);
    }
    return wallets.findByMsisdn(command.msisdn())
        .filter(existing -> sameRegistration(existing, command))
        .map(existing -> ensureLedgerAccount(existing, false))
        .orElseGet(RegisterWalletResult.MsisdnExists::new);
  }

  public WalletLookupResult.Funding fund(FundWalletCommand command) {
    Optional<Wallet> wallet = wallets.findByMsisdn(command.msisdn());
    if (wallet.isEmpty()) {
      return new WalletLookupResult.WalletNotFound();
    }
    UUID fundingId = TxnIds.fundingId(wallet.get().walletId(), command.idempotencyKey());
    try {
      ledgerAccounts.fund(fundingId, wallet.get().ledgerAccountId(), command.amount());
    } catch (LedgerAccountNotFoundException e) {
      return new WalletLookupResult.WalletNotFound();
    }
    return new WalletLookupResult.Funded(fundingId, command.msisdn(), command.amount());
  }

  public WalletLookupResult.Balance balance(String msisdn) {
    Optional<Wallet> wallet = wallets.findByMsisdn(msisdn);
    if (wallet.isEmpty()) {
      return new WalletLookupResult.WalletNotFound();
    }
    try {
      return new WalletLookupResult.BalanceFound(ledgerQueries.balance(wallet.get().ledgerAccountId()));
    } catch (LedgerAccountNotFoundException e) {
      return new WalletLookupResult.WalletNotFound();
    }
  }

  private RegisterWalletResult ensureLedgerAccount(Wallet wallet, boolean created) {
    ledgerAccounts.createAccount(walletMapper.toLedgerAccount(wallet));
    LOG.info(ApplicationConstants.LOG_WALLET_REGISTERED, MsisdnMasker.mask(wallet.msisdn()), created);
    return new RegisterWalletResult.Registered(wallet, created);
  }

  private static boolean sameRegistration(Wallet existing, RegisterWalletCommand command) {
    return existing.isCustomer()
        && existing.holderName().equals(command.holderName())
        && existing.kycTier() == command.kycTier();
  }
}
