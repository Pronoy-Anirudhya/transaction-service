package com.bracits.transactionservice.adapter.out.jdbc.constant;

/**
 * Bean names of the JDBC repositories, so the Caffeine decorators can depend on the port interfaces
 * and still pick the JDBC delegate (the decorators themselves are {@code @Primary}).
 */
public final class JdbcConstants {

  public static final String JDBC_WALLET_REPOSITORY_BEAN = "jdbcWalletRepository";
  public static final String JDBC_RULE_REPOSITORY_BEAN = "jdbcRuleRepository";

  private JdbcConstants() {
  }
}
