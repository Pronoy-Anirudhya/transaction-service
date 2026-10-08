package com.bracits.transactionservice.application.wallet.service.impl;

import com.bracits.transactionservice.application.calendar.service.BusinessCalendar;
import com.bracits.transactionservice.application.command.FundWalletCommand;
import com.bracits.transactionservice.application.command.RegisterWalletCommand;
import com.bracits.transactionservice.application.constant.ApplicationConstants;
import com.bracits.transactionservice.application.mapper.WalletMapper;
import com.bracits.transactionservice.application.result.BalanceResult;
import com.bracits.transactionservice.application.result.FundingResult;
import com.bracits.transactionservice.application.result.RegisterWalletResult;
import com.bracits.transactionservice.application.wallet.service.WalletSupportService;
import com.bracits.transactionservice.domain.txn.util.TxnIds;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import com.bracits.transactionservice.domain.wallet.util.MsisdnMasker;
import com.bracits.transactionservice.port.out.client.LedgerAccountsPort;
import com.bracits.transactionservice.port.out.client.LedgerHealthPort;
import com.bracits.transactionservice.port.out.client.LedgerQueryPort;
import com.bracits.transactionservice.port.out.exception.LedgerAccountNotFoundException;
import com.bracits.transactionservice.port.out.exception.LedgerUnavailableException;
import com.bracits.transactionservice.port.out.generator.TxnIdGenerator;
import com.bracits.transactionservice.port.out.repository.WalletRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link WalletSupportService}.
 */
@Service
public class WalletSupportServiceImpl implements WalletSupportService {

  private static final Logger LOG = LoggerFactory.getLogger(WalletSupportServiceImpl.class);

  private final WalletRepository wallets;
  private final LedgerAccountsPort ledgerAccounts;
  private final LedgerQueryPort ledgerQueries;
  private final TxnIdGenerator ids;
  private final WalletMapper walletMapper;
  private final BusinessCalendar calendar;
  private final LedgerHealthPort ledgerHealth;

  public WalletSupportServiceImpl(
      WalletRepository wallets,
      LedgerAccountsPort ledgerAccounts,
      LedgerQueryPort ledgerQueries,
      TxnIdGenerator ids,
      WalletMapper walletMapper,
      BusinessCalendar calendar,
      LedgerHealthPort ledgerHealth) {
    this.wallets = wallets;
    this.ledgerAccounts = ledgerAccounts;
    this.ledgerQueries = ledgerQueries;
    this.ids = ids;
    this.walletMapper = walletMapper;
    this.calendar = calendar;
    this.ledgerHealth = ledgerHealth;
  }

  /**
   * Inserts the wallet (PostgreSQL assigns nothing but the ID; the ledger account ID is
   * time-ordered, spec 6.4), then creates its ledger account. A replay with identical fields
   * re-ensures the ledger account.
   */
  @Override
  public RegisterWalletResult register(RegisterWalletCommand command) {
    if (!ledgerHealth.isAvailable()) {
      // The ledger account is created right after the insert: do not leave a wallet without one.
      throw new LedgerUnavailableException(ApplicationConstants.MSG_LEDGER_UNAVAILABLE);
    }

    LocalDate today = calendar.today();
    Optional<Wallet> inserted = wallets.insertIfAbsent(
        walletMapper.toNewWallet(command, ids.next()), today);
    if (inserted.isPresent()) {
      return ensureLedgerAccount(inserted.get(), true);
    }

    return wallets.findByMsisdn(command.msisdn())
        .filter(existing -> sameRegistration(existing, command))
        .map(existing -> ensureLedgerAccount(existing, false))
        .orElseGet(RegisterWalletResult.MsisdnExists::new);
  }

  @Override
  public FundingResult fund(FundWalletCommand command) {
    Optional<Wallet> wallet = wallets.findByMsisdn(command.msisdn());
    if (wallet.isEmpty()) {
      return new FundingResult.WalletNotFound();
    }

    UUID fundingId = TxnIds.fundingId(wallet.get().walletId(), command.idempotencyKey());
    try {
      ledgerAccounts.fund(fundingId, wallet.get().ledgerAccountId(), command.amount());
    } catch (LedgerAccountNotFoundException e) {
      return new FundingResult.WalletNotFound();
    }

    return new FundingResult.Funded(fundingId, command.msisdn(), command.amount());
  }

  @Override
  public BalanceResult balance(String msisdn) {
    Optional<Wallet> wallet = wallets.findByMsisdn(msisdn);
    if (wallet.isEmpty()) {
      return new BalanceResult.WalletNotFound();
    }

    try {
      return new BalanceResult.BalanceFound(ledgerQueries.balance(wallet.get().ledgerAccountId()));
    } catch (LedgerAccountNotFoundException e) {
      return new BalanceResult.WalletNotFound();
    }
  }

  private RegisterWalletResult ensureLedgerAccount(Wallet wallet, boolean created) {
    ledgerAccounts.createAccount(walletMapper.toLedgerAccount(wallet));
    LOG.info(ApplicationConstants.LOG_WALLET_REGISTERED, MsisdnMasker.mask(wallet.msisdn()),
        created);
    return new RegisterWalletResult.Registered(wallet, created);
  }

  private static boolean sameRegistration(Wallet existing, RegisterWalletCommand command) {
    return existing.isCustomer()
        && existing.holderName().equals(command.holderName())
        && existing.kycTier() == command.kycTier();
  }
}
