package com.bracits.transactionservice.domain.ledger.planner.impl;

import com.bracits.transactionservice.domain.constant.DomainConstants;
import com.bracits.transactionservice.domain.enums.Product;
import com.bracits.transactionservice.domain.ledger.enums.LegCode;
import com.bracits.transactionservice.domain.ledger.model.Leg;
import com.bracits.transactionservice.domain.ledger.model.PostingRequest;
import com.bracits.transactionservice.domain.ledger.model.SystemAccounts;
import com.bracits.transactionservice.domain.ledger.planner.LegPlanner;
import com.bracits.transactionservice.domain.model.Pricing;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link LegPlanner}.
 */
public final class LegPlannerImpl implements LegPlanner {

  private final SystemAccounts accounts;

  public LegPlannerImpl(SystemAccounts accounts) {
    this.accounts = accounts;
  }

  @Override
  public PostingRequest plan(UUID txnId, Wallet sender, Wallet receiver, long amount,
      Pricing pricing) {
    UUID from = sender.ledgerAccountId();
    List<Leg> legs = new ArrayList<>(DomainConstants.MAX_SEND_MONEY_LEGS);

    legs.add(new Leg(from, receiver.ledgerAccountId(), amount, LegCode.PRINCIPAL));
    addIfPositive(legs, from, accounts.feeIncome(), pricing.feeIncome(), LegCode.FEE);
    addIfPositive(legs, from, accounts.vatPayable(), pricing.vat(), LegCode.VAT);
    addIfPositive(legs, from, accounts.commissionPayable(), pricing.commission(),
        LegCode.COMMISSION);

    return new PostingRequest(txnId, Product.SEND_MONEY, sender.walletId(), legs);
  }

  private static void addIfPositive(List<Leg> legs, UUID debit, UUID credit, long amount,
      LegCode code) {
    if (amount > 0) {
      legs.add(new Leg(debit, credit, amount, code));
    }
  }

}
