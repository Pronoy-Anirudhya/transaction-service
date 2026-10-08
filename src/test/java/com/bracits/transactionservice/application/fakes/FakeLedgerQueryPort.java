package com.bracits.transactionservice.application.fakes;

import com.bracits.transactionservice.domain.ledger.enums.PostingLookupStatus;
import com.bracits.transactionservice.domain.ledger.model.AccountBalance;
import com.bracits.transactionservice.domain.ledger.model.PostingLookup;
import com.bracits.transactionservice.port.out.client.LedgerQueryPort;
import com.bracits.transactionservice.port.out.exception.LedgerAccountNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Posting look-ups and balances answered from maps; unknown postings are NOT_FOUND, unknown
 * accounts 404.
 */
public final class FakeLedgerQueryPort implements LedgerQueryPort {

  public record Lookup(UUID postingId, int legCount) {

  }

  private final Map<UUID, PostingLookup> postings = new ConcurrentHashMap<>();
  private final Map<UUID, RuntimeException> lookupFailures = new ConcurrentHashMap<>();
  private final Map<UUID, AccountBalance> balances = new ConcurrentHashMap<>();
  private final Map<UUID, RuntimeException> balanceFailures = new ConcurrentHashMap<>();
  private final List<Lookup> lookups = new CopyOnWriteArrayList<>();

  public void posted(UUID postingId, OptionalLong ledgerTimestamp) {
    postings.put(postingId, new PostingLookup(PostingLookupStatus.POSTED, ledgerTimestamp));
  }

  public void failLookup(UUID postingId, RuntimeException failure) {
    lookupFailures.put(postingId, failure);
  }

  public void balance(UUID accountId, AccountBalance balance) {
    balances.put(accountId, balance);
  }

  public void failBalance(UUID accountId, RuntimeException failure) {
    balanceFailures.put(accountId, failure);
  }

  @Override
  public PostingLookup lookupPosting(UUID postingId, int legCount) {
    lookups.add(new Lookup(postingId, legCount));

    RuntimeException failure = lookupFailures.get(postingId);
    if (failure != null) {
      throw failure;
    }

    return postings.getOrDefault(postingId,
        new PostingLookup(PostingLookupStatus.NOT_FOUND, OptionalLong.empty()));
  }

  @Override
  public AccountBalance balance(UUID accountId) {
    RuntimeException failure = balanceFailures.get(accountId);
    if (failure != null) {
      throw failure;
    }

    AccountBalance balance = balances.get(accountId);
    if (balance == null) {
      throw new LedgerAccountNotFoundException("no account " + accountId);
    }

    return balance;
  }

  public List<Lookup> lookups() {
    return List.copyOf(lookups);
  }
}
