package com.bracits.transactionservice.application.fakes;

import com.bracits.transactionservice.domain.ledger.enums.AccountCreation;
import com.bracits.transactionservice.domain.ledger.model.LedgerAccount;
import com.bracits.transactionservice.port.out.client.LedgerAccountsPort;
import com.bracits.transactionservice.port.out.exception.LedgerAccountNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Idempotent account creation and funding; accounts marked unknown answer 404 on funding.
 */
public final class FakeLedgerAccountsPort implements LedgerAccountsPort {

  public record Funding(UUID fundingId, UUID accountId, long amount) {

  }

  private final List<LedgerAccount> createCalls = new CopyOnWriteArrayList<>();
  private final Set<UUID> existing = ConcurrentHashMap.newKeySet();
  private final Set<UUID> unknownAccounts = ConcurrentHashMap.newKeySet();
  private final List<Funding> fundings = new CopyOnWriteArrayList<>();

  public void unknownAccount(UUID accountId) {
    unknownAccounts.add(accountId);
  }

  @Override
  public AccountCreation createAccount(LedgerAccount account) {
    createCalls.add(account);
    return existing.add(account.accountId()) ? AccountCreation.CREATED
        : AccountCreation.ALREADY_EXISTS;
  }

  @Override
  public void fund(UUID fundingId, UUID accountId, long amount) {
    if (unknownAccounts.contains(accountId)) {
      throw new LedgerAccountNotFoundException("no account " + accountId);
    }
    fundings.add(new Funding(fundingId, accountId, amount));
  }

  public List<LedgerAccount> createCalls() {
    return List.copyOf(createCalls);
  }

  public List<Funding> fundings() {
    return List.copyOf(fundings);
  }
}
