package com.bracits.transactionservice.application.command;

/**
 * Test-profile wallet registration input.
 */
public record RegisterWalletCommand(String msisdn, String holderName, int kycTier) {

}
