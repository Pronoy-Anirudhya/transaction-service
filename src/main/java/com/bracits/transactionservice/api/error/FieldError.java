package com.bracits.transactionservice.api.error;

/** One item of the Problem Details {@code errors} member: the offending field (or parameter) and why. */
public record FieldError(String field, String message) {
}
