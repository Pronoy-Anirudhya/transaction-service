package com.bracits.transactionservice.application;

import com.bracits.transactionservice.application.command.QuoteCommand;
import com.bracits.transactionservice.application.fakes.SendMoneyHarness;
import com.bracits.transactionservice.application.quote.QuoteClaims;
import com.bracits.transactionservice.application.quote.QuoteTokenCheck;
import com.bracits.transactionservice.application.result.QuoteResult;
import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.Pricing;
import com.bracits.transactionservice.domain.wallet.WalletStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.bracits.transactionservice.application.fakes.Fixtures.AMOUNT;
import static com.bracits.transactionservice.application.fakes.Fixtures.CURRENCY;
import static com.bracits.transactionservice.application.fakes.Fixtures.FREE_AMOUNT;
import static com.bracits.transactionservice.application.fakes.Fixtures.NOW;
import static com.bracits.transactionservice.application.fakes.Fixtures.RECEIVER_MSISDN;
import static com.bracits.transactionservice.application.fakes.Fixtures.SENDER_MSISDN;
import static com.bracits.transactionservice.application.fakes.Fixtures.STANDARD_PRICING;
import static com.bracits.transactionservice.application.fakes.Fixtures.UNKNOWN_MSISDN;
import static com.bracits.transactionservice.application.fakes.Fixtures.sender;
import static com.bracits.transactionservice.application.fakes.Fixtures.withStatus;
import static org.assertj.core.api.Assertions.assertThat;

class QuoteServiceTest {

  private final SendMoneyHarness h = new SendMoneyHarness();

  private QuoteResult quote(String sender, String receiver, long amount) {
    return h.quotes.quote(new QuoteCommand(sender, receiver, amount, CURRENCY));
  }

  @Test
  void quotesTheMaskedReceiverThePricingAndASignedToken() {
    QuoteResult result = quote(SENDER_MSISDN, RECEIVER_MSISDN, AMOUNT);

    assertThat(result).isInstanceOfSatisfying(QuoteResult.Quoted.class, q -> {
      assertThat(q.receiverName()).isEqualTo("K***m M*a");
      assertThat(q.amount()).isEqualTo(AMOUNT);
      assertThat(q.pricing()).isEqualTo(STANDARD_PRICING);
      assertThat(q.totalDebit()).isEqualTo(100_500L);
      assertThat(q.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
      assertThat(h.quoteTokens.decode(q.quoteToken())).isEqualTo(new QuoteTokenCheck.Valid(
          new QuoteClaims(SENDER_MSISDN, RECEIVER_MSISDN, AMOUNT, 500L, NOW.plus(Duration.ofMinutes(5)))));
    });
  }

  @Test
  void freeSlabQuotesAZeroFee() {
    QuoteResult result = quote(SENDER_MSISDN, RECEIVER_MSISDN, FREE_AMOUNT);

    assertThat(result).isInstanceOfSatisfying(QuoteResult.Quoted.class, q -> {
      assertThat(q.pricing()).isEqualTo(Pricing.FREE);
      assertThat(q.totalDebit()).isEqualTo(FREE_AMOUNT);
    });
  }

  @Test
  void writesNothing() {
    quote(SENDER_MSISDN, RECEIVER_MSISDN, AMOUNT);

    assertThat(h.txns.insertAttempts()).isEmpty();
    assertThat(h.ids.issued()).isEmpty();
    assertThat(h.limits.calls()).isEmpty();
    assertThat(h.ledger.requests()).isEmpty();
  }

  @Test
  void ruleFailuresAreRejected() {
    assertThat(quote(SENDER_MSISDN, SENDER_MSISDN, AMOUNT)).isEqualTo(new QuoteResult.Rejected(FailureCode.SELF_TRANSFER));
    assertThat(quote(SENDER_MSISDN, UNKNOWN_MSISDN, AMOUNT))
        .isEqualTo(new QuoteResult.Rejected(FailureCode.WALLET_NOT_FOUND));
    assertThat(quote(SENDER_MSISDN, RECEIVER_MSISDN, 999L))
        .isEqualTo(new QuoteResult.Rejected(FailureCode.AMOUNT_OUT_OF_RANGE));
    h.wallets.put(withStatus(sender(), WalletStatus.FROZEN));
    assertThat(quote(SENDER_MSISDN, RECEIVER_MSISDN, AMOUNT))
        .isEqualTo(new QuoteResult.Rejected(FailureCode.WALLET_INACTIVE));
  }

  @Test
  void noMatchingFeeRuleIsAmountOutOfRange() {
    h.rules.setFeeRules(List.of());

    assertThat(quote(SENDER_MSISDN, RECEIVER_MSISDN, AMOUNT))
        .isEqualTo(new QuoteResult.Rejected(FailureCode.AMOUNT_OUT_OF_RANGE));
  }

  @Test
  void quoteDoesNotCheckLimitUsage() {
    h.limits.reject();

    assertThat(quote(SENDER_MSISDN, RECEIVER_MSISDN, AMOUNT)).isInstanceOf(QuoteResult.Quoted.class);
  }
}
