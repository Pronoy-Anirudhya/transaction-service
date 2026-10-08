package com.bracits.transactionservice.domain.ledger;

import com.bracits.transactionservice.domain.DomainConstants;
import com.bracits.transactionservice.domain.Pricing;
import com.bracits.transactionservice.domain.Product;
import com.bracits.transactionservice.domain.wallet.Wallet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the linked Send Money posting (spec 6.2): principal, fee income, VAT, commission, all debited from the
 * sender. Legs with a zero amount are omitted (BR-09), so leg indexes are positions in the posted batch.
 */
public final class LegPlanner {

  private final SystemAccounts accounts;

  public LegPlanner(SystemAccounts accounts) {
    this.accounts = accounts;
  }

  public PostingRequest plan(UUID txnId, Wallet sender, Wallet receiver, long amount, Pricing pricing) {
    UUID from = sender.ledgerAccountId();
    List<Leg> legs = new ArrayList<>(DomainConstants.MAX_SEND_MONEY_LEGS);
    legs.add(new Leg(from, receiver.ledgerAccountId(), amount, LegCode.PRINCIPAL));
    addIfPositive(legs, from, accounts.feeIncome(), pricing.feeIncome(), LegCode.FEE);
    addIfPositive(legs, from, accounts.vatPayable(), pricing.vat(), LegCode.VAT);
    addIfPositive(legs, from, accounts.commissionPayable(), pricing.commission(), LegCode.COMMISSION);
    return new PostingRequest(txnId, Product.SEND_MONEY, sender.walletId(), legs);
  }

  /** Number of legs {@link #plan} produces for this pricing (for {@code GET /postings/{id}?legs=n}). */
  public static int legCount(Pricing pricing) {
    return 1 + positive(pricing.feeIncome()) + positive(pricing.vat()) + positive(pricing.commission());
  }

  private static void addIfPositive(List<Leg> legs, UUID debit, UUID credit, long amount, LegCode code) {
    if (amount > 0) {
      legs.add(new Leg(debit, credit, amount, code));
    }
  }

  private static int positive(long amount) {
    return amount > 0 ? 1 : 0;
  }
}
