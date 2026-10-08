package com.bracits.transactionservice.adapter.out.jdbc.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Typed, null-aware column reads shared by the row mappers. */
final class ResultSetValues {

  private ResultSetValues() {
  }

  static UUID uuid(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, UUID.class);
  }

  static LocalDate date(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, LocalDate.class);
  }

  /** {@code timestamptz NOT NULL} → {@link Instant}. */
  static Instant instant(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, OffsetDateTime.class).toInstant();
  }

  /** Nullable {@code timestamptz} → {@code Optional<Instant>}. */
  static Optional<Instant> optionalInstant(ResultSet rs, String column) throws SQLException {
    return Optional.ofNullable(rs.getObject(column, OffsetDateTime.class)).map(OffsetDateTime::toInstant);
  }

  /** Nullable {@code bigint} → {@link OptionalLong}. */
  static OptionalLong optionalLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? OptionalLong.empty() : OptionalLong.of(value);
  }

  /** Nullable text → {@code Optional<String>}. */
  static Optional<String> optionalString(ResultSet rs, String column) throws SQLException {
    return Optional.ofNullable(rs.getString(column));
  }
}
