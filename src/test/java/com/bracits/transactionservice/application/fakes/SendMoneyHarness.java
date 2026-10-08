package com.bracits.transactionservice.application.fakes;

import com.bracits.transactionservice.application.LedgerPoster;
import com.bracits.transactionservice.application.QuoteService;
import com.bracits.transactionservice.application.ReconciliationService;
import com.bracits.transactionservice.application.RepairWorker;
import com.bracits.transactionservice.application.RequestHasher;
import com.bracits.transactionservice.application.SendMoneyMetrics;
import com.bracits.transactionservice.application.SendMoneyPreparation;
import com.bracits.transactionservice.application.SendMoneyService;
import com.bracits.transactionservice.application.TxnFinaliser;
import com.bracits.transactionservice.application.mapper.SendMoneyEventMapper;
import com.bracits.transactionservice.application.mapper.TxnMapper;
import com.bracits.transactionservice.application.quote.QuoteTokenCodec;
import com.bracits.transactionservice.domain.fee.SlabFeeCalculator;
import com.bracits.transactionservice.domain.ledger.LegPlanner;
import com.bracits.transactionservice.domain.limit.LimitPolicy;
import com.bracits.transactionservice.domain.rules.SendMoneyRuleChain;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * The application layer wired as in production, with the real domain objects (rule chain, slab fee calculator,
 * limit policy, leg planner, mappers, codec) and in-memory fakes for every port. Sender and receiver are registered.
 */
public final class SendMoneyHarness {

  public final MutableClock clock = new MutableClock(Fixtures.NOW);
  public final InMemoryWalletRepository wallets = new InMemoryWalletRepository();
  public final InMemoryRuleRepository rules = new InMemoryRuleRepository();
  public final InMemoryTxnRepository txns = new InMemoryTxnRepository(clock);
  public final FakeLimitRepository limits = new FakeLimitRepository();
  public final FakeTransactionOperations transactions = new FakeTransactionOperations(txns, limits);
  public final ScriptedLedgerPort ledger = new ScriptedLedgerPort();
  public final RecordingEventPublisher events = new RecordingEventPublisher(txns::findById);
  public final SequentialTxnIds ids = new SequentialTxnIds(clock);
  public final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  public final SendMoneyMetrics metrics = new SendMoneyMetrics(registry);
  public final LegPlanner legPlanner = new LegPlanner(Fixtures.SYSTEM_ACCOUNTS);
  public final TxnMapper txnMapper = new TxnMapper();
  public final SendMoneyEventMapper eventMapper = new SendMoneyEventMapper();
  public final QuoteTokenCodec quoteTokens = new QuoteTokenCodec(Fixtures.quote());
  public final SendMoneyPreparation preparation = new SendMoneyPreparation(
      wallets,
      new LimitPolicy(rules::findLimitRules),
      SendMoneyRuleChain.standard(),
      new SlabFeeCalculator(rules::findActiveFeeRules));
  public final LedgerPoster poster = new LedgerPoster(ledger);
  public final TxnFinaliser finaliser = new TxnFinaliser(txns, events, eventMapper);
  public final SendMoneyService service = new SendMoneyService(
      preparation, new RequestHasher(), quoteTokens, ids, txns, limits, transactions, legPlanner, poster, finaliser,
      txnMapper, metrics, Fixtures.business(), Fixtures.repair(), clock);
  public final QuoteService quotes = new QuoteService(preparation, quoteTokens, Fixtures.quote(), clock);
  public final RepairWorker repairWorker = new RepairWorker(
      txns, wallets, legPlanner, poster, finaliser, metrics, Fixtures.repair());

  public SendMoneyHarness() {
    wallets.put(Fixtures.sender());
    wallets.put(Fixtures.receiver());
  }

  public ReconciliationService reconciliation(FakeLedgerQueryPort ledgerQueries, int maxRows) {
    return new ReconciliationService(txns, ledgerQueries, finaliser, metrics, Fixtures.reconciliation(maxRows));
  }

  /** Count of {@code sendmoney.requests{outcome, code}}. */
  public double sendCount(String outcome, String code) {
    var counter = registry.find("sendmoney.requests").tags("outcome", outcome, "code", code).counter();
    return counter == null ? 0.0 : counter.count();
  }

  /** Value of a tag-less counter, 0 if it does not exist. */
  public double counter(String name) {
    var counter = registry.find(name).counter();
    return counter == null ? 0.0 : counter.count();
  }
}
