package com.bracits.transactionservice.application.fakes;

import com.bracits.transactionservice.application.calendar.service.BusinessCalendar;
import com.bracits.transactionservice.application.calendar.service.impl.BusinessCalendarImpl;
import com.bracits.transactionservice.application.mapper.SendMoneyEventMapper;
import com.bracits.transactionservice.application.mapper.TxnMapper;
import com.bracits.transactionservice.application.mapper.impl.SendMoneyEventMapperImpl;
import com.bracits.transactionservice.application.mapper.impl.TxnMapperImpl;
import com.bracits.transactionservice.application.metrics.service.SendMoneyMetrics;
import com.bracits.transactionservice.application.metrics.service.impl.SendMoneyMetricsImpl;
import com.bracits.transactionservice.application.posting.service.LedgerPoster;
import com.bracits.transactionservice.application.posting.service.TxnFinaliser;
import com.bracits.transactionservice.application.posting.service.impl.LedgerPosterImpl;
import com.bracits.transactionservice.application.posting.service.impl.TxnFinaliserImpl;
import com.bracits.transactionservice.application.quote.service.QuoteService;
import com.bracits.transactionservice.application.quote.service.QuoteTokenCodec;
import com.bracits.transactionservice.application.quote.service.impl.QuoteServiceImpl;
import com.bracits.transactionservice.application.quote.service.impl.QuoteTokenCodecImpl;
import com.bracits.transactionservice.application.reconciliation.service.ReconciliationService;
import com.bracits.transactionservice.application.reconciliation.service.impl.ReconciliationServiceImpl;
import com.bracits.transactionservice.application.repair.service.impl.RepairWorkerImpl;
import com.bracits.transactionservice.application.sendmoney.service.SendMoneyPreparation;
import com.bracits.transactionservice.application.sendmoney.service.SendMoneyService;
import com.bracits.transactionservice.application.sendmoney.service.impl.IdempotentReplayImpl;
import com.bracits.transactionservice.application.sendmoney.service.impl.LimitReserverImpl;
import com.bracits.transactionservice.application.sendmoney.service.impl.QuoteVerifierImpl;
import com.bracits.transactionservice.application.sendmoney.service.impl.RequestHasherImpl;
import com.bracits.transactionservice.application.sendmoney.service.impl.SendMoneyPreparationImpl;
import com.bracits.transactionservice.application.sendmoney.service.impl.SendMoneyServiceImpl;
import com.bracits.transactionservice.domain.fee.calculator.impl.SlabFeeCalculator;
import com.bracits.transactionservice.domain.ledger.planner.LegPlanner;
import com.bracits.transactionservice.domain.ledger.planner.impl.LegPlannerImpl;
import com.bracits.transactionservice.domain.limit.policy.impl.LimitPolicyImpl;
import com.bracits.transactionservice.domain.rules.chain.impl.SendMoneyRuleChainImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * The application layer wired as in production, with the real domain objects (rule chain, slab fee
 * calculator, limit policy, leg planner, mappers, codec) and in-memory fakes for every port. Sender
 * and receiver are registered.
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
  public final SendMoneyMetrics metrics = new SendMoneyMetricsImpl(registry);
  public final LegPlanner legPlanner = new LegPlannerImpl(Fixtures.SYSTEM_ACCOUNTS);
  public final TxnMapper txnMapper = new TxnMapperImpl();
  public final SendMoneyEventMapper eventMapper = new SendMoneyEventMapperImpl();
  public final QuoteTokenCodec quoteTokens = new QuoteTokenCodecImpl(Fixtures.quote());
  public final SendMoneyPreparation preparation = new SendMoneyPreparationImpl(
      wallets,
      new LimitPolicyImpl(rules::findLimitRules),
      SendMoneyRuleChainImpl.standard(),
      new SlabFeeCalculator(rules::findActiveFeeRules));
  public final FakeLedgerHealth ledgerHealth = new FakeLedgerHealth();
  public final LedgerPoster poster = new LedgerPosterImpl(ledger);
  public final TxnFinaliser finaliser = new TxnFinaliserImpl(txns, events, eventMapper);
  public final BusinessCalendar calendar = new BusinessCalendarImpl(clock, Fixtures.business());
  public final SendMoneyService service = new SendMoneyServiceImpl(
      preparation, new RequestHasherImpl(), new QuoteVerifierImpl(quoteTokens, clock),
      new IdempotentReplayImpl(txns, txnMapper),
      ids, calendar, new LimitReserverImpl(txns, limits, transactions), legPlanner, poster,
      finaliser,
      txnMapper, metrics,
      Fixtures.repair(), ledgerHealth);
  public final QuoteService quotes = new QuoteServiceImpl(preparation, quoteTokens,
      Fixtures.quote(),
      clock);
  public final RepairWorkerImpl repairWorker = new RepairWorkerImpl(
      txns, wallets, legPlanner, poster, finaliser, metrics, Fixtures.repair(), ledgerHealth);

  public SendMoneyHarness() {
    wallets.put(Fixtures.sender());
    wallets.put(Fixtures.receiver());
  }

  public ReconciliationService reconciliation(FakeLedgerQueryPort ledgerQueries, int maxRows) {
    return new ReconciliationServiceImpl(txns, ledgerQueries, finaliser, metrics,
        Fixtures.reconciliation(maxRows), ledgerHealth);
  }

  /**
   * Count of {@code sendmoney.requests{outcome, code}}.
   */
  public double sendCount(String outcome, String code) {
    var counter = registry.find("sendmoney.requests").tags("outcome", outcome, "code", code)
        .counter();
    return counter == null ? 0.0 : counter.count();
  }

  /**
   * Value of a tag-less counter, 0 if it does not exist.
   */
  public double counter(String name) {
    var counter = registry.find(name).counter();
    return counter == null ? 0.0 : counter.count();
  }
}
