package com.bracits.transactionservice.domain.event;

import com.bracits.transactionservice.domain.DomainConstants;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** Factory for deterministic name-based UUIDs (RFC 9562 version 5, SHA-1). */
public final class EventIds {

  private static final String SHA_1 = "SHA-1";
  private static final int VERSION_5 = 0x50;
  private static final int VERSION_MASK = 0x0f;
  private static final int VARIANT_RFC = 0x80;
  private static final int VARIANT_MASK = 0x3f;
  private static final int UUID_BYTES = 16;

  private EventIds() {
  }

  /** {@code message_id} / {@code eventId} = UUIDv5(EVENT_ID_NAMESPACE, txnId + eventType). */
  public static UUID of(UUID txnId, EventType eventType) {
    return uuidV5(DomainConstants.EVENT_ID_NAMESPACE, txnId.toString() + eventType.eventName());
  }

  public static UUID uuidV5(UUID namespace, String name) {
    MessageDigest sha1;
    try {
      sha1 = MessageDigest.getInstance(SHA_1);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    sha1.update(ByteBuffer.allocate(UUID_BYTES)
        .putLong(namespace.getMostSignificantBits())
        .putLong(namespace.getLeastSignificantBits())
        .array());
    byte[] hash = sha1.digest(name.getBytes(StandardCharsets.UTF_8));
    hash[6] = (byte) ((hash[6] & VERSION_MASK) | VERSION_5);
    hash[8] = (byte) ((hash[8] & VARIANT_MASK) | VARIANT_RFC);
    ByteBuffer buffer = ByteBuffer.wrap(hash, 0, UUID_BYTES);
    return new UUID(buffer.getLong(), buffer.getLong());
  }
}
