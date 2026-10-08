package com.bracits.transactionservice.port.out;

import java.util.UUID;

/**
 * Factory for txnIds (spec 6.3): 48-bit Unix-millisecond timestamp, 72 random bits, 8 zero bits. Lock-free (P12).
 */
public interface TxnIdGenerator {

  UUID next();
}
