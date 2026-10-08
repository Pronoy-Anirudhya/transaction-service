package com.bracits.transactionservice.application.mapper;

import static com.bracits.transactionservice.application.fakes.Fixtures.SENDER_ACCOUNT;
import static com.bracits.transactionservice.application.fakes.Fixtures.SENDER_ID;
import static com.bracits.transactionservice.application.fakes.Fixtures.sender;
import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.transactionservice.application.command.RegisterWalletCommand;
import com.bracits.transactionservice.application.mapper.impl.WalletMapperImpl;
import com.bracits.transactionservice.domain.ledger.enums.LedgerAccountCode;
import com.bracits.transactionservice.domain.ledger.enums.LedgerAccountFlag;
import com.bracits.transactionservice.domain.ledger.model.LedgerAccount;
import com.bracits.transactionservice.domain.wallet.enums.WalletStatus;
import com.bracits.transactionservice.domain.wallet.enums.WalletType;
import com.bracits.transactionservice.domain.wallet.model.NewWallet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletMapperTest {

  private final WalletMapper mapper = new WalletMapperImpl();

  @Test
  void registeredWalletsAreActiveCustomerWallets() {
    UUID accountId = UUID.fromString("0192f5a4-0000-0000-0000-000000000300");

    NewWallet wallet = mapper.toNewWallet(
        new RegisterWalletCommand("8801811000042", "Nasima Akter", 2), accountId);

    assertThat(wallet).isEqualTo(new NewWallet("8801811000042", "Nasima Akter", WalletType.CUSTOMER,
        WalletStatus.ACTIVE, 2, accountId));
  }

  @Test
  void customerLedgerAccountIsCode100WithTheOverdraftGuardAndTheWalletId() {
    assertThat(mapper.toLedgerAccount(sender())).isEqualTo(new LedgerAccount(SENDER_ACCOUNT,
        LedgerAccountCode.CUSTOMER_WALLET, Set.of(LedgerAccountFlag.DEBITS_MUST_NOT_EXCEED_CREDITS),
        SENDER_ID));
    assertThat(LedgerAccountCode.CUSTOMER_WALLET.code()).isEqualTo(100);
  }
}
