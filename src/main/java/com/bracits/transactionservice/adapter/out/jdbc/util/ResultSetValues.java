package com.bracits.transactionservice.adapter.out.jdbc.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Typed, null-aware column reads shared by the row mappers.
 */
public final class ResultSetValues {

  private ResultSetValues() {
  }

  public static UUID uuid(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, UUID.class);
  }

  public static LocalDate date(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, LocalDate.class);
  }

  /**
   * {@code timestamptz NOT NULL} → {@link Instant}.
   */
  public static Instant instant(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, OffsetDateTime.class).toInstant();
  }

  /**
   * Nullable {@code timestamptz} → {@code Optional<Instant>}.
   */
  public static Optional<Instant> optionalInstant(ResultSet rs, String column) throws SQLException {
    return Optional.ofNullable(rs.getObject(column, OffsetDateTime.class))
        .map(OffsetDateTime::toInstant);
  }

  /**
   * Nullable {@code bigint} → {@link OptionalLong}.
   */
  public static OptionalLong optionalLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? OptionalLong.empty() : OptionalLong.of(value);
  }

  /**
   * Nullable text → {@code Optional<String>}.
   */
  public static Optional<String> optionalString(ResultSet rs, String column) throws SQLException {
    return Optional.ofNullable(rs.getString(column));
  }
}
