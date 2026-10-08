package com.bracits.transactionservice.application.fakes;

import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.TxnStatus;
import com.bracits.transactionservice.domain.txn.NewSendMoneyTxn;
import com.bracits.transactionservice.domain.txn.SendMoneyTxn;
import com.bracits.transactionservice.port.out.TxnRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

/**
 * {@code send_money_txn} in memory with the port's semantics: unique (sender, clientRef), compare-and-set on the status
 * for every finalising update, claim with lease. Failures can be injected per operation; calls are recorded.
 */
public final class InMemoryTxnRepository implements TxnRepository {

  /** Operations that can be made to fail once with {@link #failNext}. */
  public enum Op {
    INSERT, FIND_BY_SENDER, FIND_BY_ID, MARK_COMPLETED, MARK_FAILED, SCHEDULE_RECHECK, CLAIM_IN_DOUBT,
    FIND_BY_RANGE, MARK_FAILED_AS_COMPLETED
  }

  public record Recheck(UUID txnId, Duration delay) {
  }

  public record Release(UUID txnId, FailureCode code) {
  }

  public record Flip(UUID txnId, OptionalLong ledgerTimestamp) {
  }

  public record Claim(int limit, Duration minAge, Duration lease) {
  }

  public record Range(UUID fromInclusive, UUID toExclusive, int limit) {
  }

  /** Unsigned ordering of the 128 bits, as PostgreSQL compares {@code uuid}. */
  public static final Comparator<UUID> UUID_ORDER = (a, b) -> {
    int high = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
    return high != 0 ? high : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
  };

  private final Clock clock;
  private final Map<UUID, SendMoneyTxn> rows = new LinkedHashMap<>();
  private final Map<Op, Deque<RuntimeException>> failures = new EnumMap<>(Op.class);
  private final List<NewSendMoneyTxn> insertAttempts = new CopyOnWriteArrayList<>();
  private final List<Recheck> rechecks = new CopyOnWriteArrayList<>();
  private final List<Release> releases = new CopyOnWriteArrayList<>();
  private final List<Flip> flips = new CopyOnWriteArrayList<>();
  private final List<Claim> claims = new CopyOnWriteArrayList<>();
  private final List<Range> ranges = new CopyOnWriteArrayList<>();

  public InMemoryTxnRepository(Clock clock) {
    this.clock = clock;
  }

  /** The next call of {@code op} throws {@code failure} (queued; one failure per call). */
  public synchronized void failNext(Op op, RuntimeException failure) {
    failures.computeIfAbsent(op, k -> new ArrayDeque<>()).add(failure);
  }

  private void maybeFail(Op op) {
    Deque<RuntimeException> queue = failures.get(op);
    if (queue != null && !queue.isEmpty()) {
      throw queue.poll();
    }
  }

  // ---- seeding and inspection ----

  public synchronized void put(SendMoneyTxn row) {
    rows.put(row.txnId(), row);
  }

  public synchronized SendMoneyTxn get(UUID txnId) {
    return Optional.ofNullable(rows.get(txnId)).orElseThrow(() -> new AssertionError("no row " + txnId));
  }

  public synchronized List<SendMoneyTxn> all() {
    return List.copyOf(rows.values());
  }

  public synchronized int size() {
    return rows.size();
  }

  public synchronized Map<UUID, SendMoneyTxn> snapshot() {
    return new LinkedHashMap<>(rows);
  }

  public synchronized void restore(Map<UUID, SendMoneyTxn> snapshot) {
    rows.clear();
    rows.putAll(snapshot);
  }

  public List<NewSendMoneyTxn> insertAttempts() {
    return List.copyOf(insertAttempts);
  }

  public List<Recheck> rechecks() {
    return List.copyOf(rechecks);
  }

  public List<Release> releases() {
    return List.copyOf(releases);
  }

  public List<Flip> flips() {
    return List.copyOf(flips);
  }

  public List<Claim> claims() {
    return List.copyOf(claims);
  }

  public List<Range> ranges() {
    return List.copyOf(ranges);
  }

  // ---- port ----

  @Override
  public synchronized Optional<UUID> insertIfAbsent(NewSendMoneyTxn txn) {
    insertAttempts.add(txn);
    maybeFail(Op.INSERT);
    boolean duplicate = rows.values().stream().anyMatch(r ->
        r.senderWalletId() == txn.senderWalletId() && r.clientRef().equals(txn.clientRef()));
    if (duplicate) {
      return Optional.empty();
    }
    rows.put(txn.txnId(), TxnRowBuilder.inserted(txn, clock.instant()).build());
    return Optional.of(txn.txnId());
  }

  @Override
  public synchronized Optional<SendMoneyTxn> findBySenderAndClientRef(long senderWalletId, String clientRef) {
    maybeFail(Op.FIND_BY_SENDER);
    return rows.values().stream()
        .filter(r -> r.senderWalletId() == senderWalletId && r.clientRef().equals(clientRef))
        .findFirst();
  }

  @Override
  public synchronized Optional<SendMoneyTxn> findById(UUID txnId) {
    maybeFail(Op.FIND_BY_ID);
    return Optional.ofNullable(rows.get(txnId));
  }

  @Override
  public synchronized Optional<SendMoneyTxn> markCompleted(UUID txnId, long ledgerTimestamp) {
    maybeFail(Op.MARK_COMPLETED);
    return casFrom(txnId, TxnStatus.INITIATED, row -> TxnRowBuilder.from(row)
        .completed(ledgerTimestamp, clock.instant()).build());
  }

  @Override
  public synchronized Optional<SendMoneyTxn> markFailedAndReleaseLimits(UUID txnId, FailureCode code) {
    maybeFail(Op.MARK_FAILED);
    Optional<SendMoneyTxn> updated = casFrom(txnId, TxnStatus.INITIATED, row -> TxnRowBuilder.from(row)
        .failed(code, clock.instant()).build());
    updated.ifPresent(row -> releases.add(new Release(txnId, code)));
    return updated;
  }

  @Override
  public synchronized boolean scheduleRecheck(UUID txnId, Duration delay) {
    rechecks.add(new Recheck(txnId, delay));
    maybeFail(Op.SCHEDULE_RECHECK);
    return casFrom(txnId, TxnStatus.INITIATED, row -> TxnRowBuilder.from(row)
        .nextCheckAt(Optional.of(clock.instant().plus(delay)))
        .ledgerAttempts(row.ledgerAttempts() + 1)
        .build()).isPresent();
  }

  @Override
  public synchronized List<SendMoneyTxn> claimInDoubt(int limit, Duration minAge, Duration lease) {
    claims.add(new Claim(limit, minAge, lease));
    maybeFail(Op.CLAIM_IN_DOUBT);
    Instant now = clock.instant();
    List<SendMoneyTxn> due = rows.values().stream()
        .filter(r -> r.status() == TxnStatus.INITIATED)
        .filter(r -> r.nextCheckAt().map(at -> !at.isAfter(now)).orElse(false))
        .filter(r -> r.createdAt().isBefore(now.minus(minAge)))
        .sorted(Comparator.comparing(r -> r.nextCheckAt().orElseThrow()))
        .limit(limit)
        .toList();
    return due.stream().map(r -> {
      SendMoneyTxn leased = TxnRowBuilder.from(r).nextCheckAt(Optional.of(now.plus(lease))).build();
      rows.put(leased.txnId(), leased);
      return leased;
    }).toList();
  }

  @Override
  public synchronized List<SendMoneyTxn> claimUnpublished(int limit, Duration minAge, Duration lease) {
    Instant now = clock.instant();
    List<SendMoneyTxn> due = rows.values().stream()
        .filter(r -> r.status() != TxnStatus.INITIATED && r.eventPublishedAt().isEmpty())
        .filter(r -> r.completedAt().map(at -> at.isBefore(now.minus(minAge))).orElse(false))
        .filter(r -> r.nextCheckAt().map(at -> !at.isAfter(now)).orElse(true))
        .limit(limit)
        .toList();
    return due.stream().map(r -> {
      SendMoneyTxn leased = TxnRowBuilder.from(r).nextCheckAt(Optional.of(now.plus(lease))).build();
      rows.put(leased.txnId(), leased);
      return leased;
    }).toList();
  }

  @Override
  public synchronized int markEventsPublished(Collection<UUID> txnIds) {
    int updated = 0;
    for (UUID id : txnIds) {
      SendMoneyTxn row = rows.get(id);
      if (row != null && row.eventPublishedAt().isEmpty()) {
        rows.put(id, TxnRowBuilder.from(row).eventPublishedAt(Optional.of(clock.instant())).build());
        updated++;
      }
    }
    return updated;
  }

  @Override
  public synchronized List<SendMoneyTxn> findByTxnIdRange(UUID fromInclusive, UUID toExclusive, int limit) {
    ranges.add(new Range(fromInclusive, toExclusive, limit));
    maybeFail(Op.FIND_BY_RANGE);
    return rows.values().stream()
        .filter(r -> UUID_ORDER.compare(r.txnId(), fromInclusive) >= 0 && UUID_ORDER.compare(r.txnId(), toExclusive) < 0)
        .sorted(Comparator.comparing(SendMoneyTxn::txnId, UUID_ORDER))
        .limit(limit)
        .toList();
  }

  @Override
  public synchronized Optional<SendMoneyTxn> markFailedAsCompleted(UUID txnId, OptionalLong ledgerTimestamp) {
    flips.add(new Flip(txnId, ledgerTimestamp));
    maybeFail(Op.MARK_FAILED_AS_COMPLETED);
    return casFrom(txnId, TxnStatus.FAILED, row -> TxnRowBuilder.from(row)
        .status(TxnStatus.COMPLETED)
        .ledgerTimestamp(ledgerTimestamp)
        .failureCode(Optional.empty())
        .completedAt(Optional.of(clock.instant()))
        .eventPublishedAt(Optional.empty())
        .build());
  }

  @Override
  public synchronized long countInDoubt() {
    return rows.values().stream().filter(r -> r.status() == TxnStatus.INITIATED).count();
  }

  @Override
  public synchronized long countUnpublished() {
    return rows.values().stream()
        .filter(r -> r.status() != TxnStatus.INITIATED && r.eventPublishedAt().isEmpty())
        .count();
  }

  private Optional<SendMoneyTxn> casFrom(
      UUID txnId, TxnStatus expected, UnaryOperator<SendMoneyTxn> update) {
    SendMoneyTxn row = rows.get(txnId);
    if (row == null || row.status() != expected) {
      return Optional.empty();
    }
    SendMoneyTxn updated = update.apply(row);
    rows.put(txnId, updated);
    return Optional.of(updated);
  }
}
