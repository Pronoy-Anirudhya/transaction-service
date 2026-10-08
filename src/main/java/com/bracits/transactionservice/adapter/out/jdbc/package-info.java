/**
 * PostgreSQL adapters of the persistence ports: explicit SQL over Spring {@code JdbcClient}, one statement per method
 * (P5), no ORM. SQL text, column and parameter names live in {@code sql}; all row and parameter conversions in
 * {@code mapper}.
 *
 * <p>The repositories are {@code @Component}, not {@code @Repository}: Spring Boot's persistence-exception translation
 * would CGLIB-proxy every {@code @Repository} bean, which fails for these {@code final} classes and would hide the
 * concrete types the Caffeine decorators inject. {@code JdbcClient} already throws Spring's
 * {@code DataAccessException} hierarchy, so the proxy would add nothing.
 */
package com.bracits.transactionservice.adapter.out.jdbc;
