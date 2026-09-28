package com.apargo.platform.contract.event;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Event id generation: UUIDv7 (RFC 9562). Time-ordered, so ids sort roughly
 * by creation time and index well; random enough that two producers never
 * collide.
 *
 * <p>An event id is generated once, when the event is built, and reused on
 * every retry: consumers de-duplicate on it.
 */
public final class EventIds {

    private static final SecureRandom RANDOM = new SecureRandom();

    private EventIds() {
    }

    public static String newEventId() {
        return newUuidV7().toString();
    }

    /**
     * 48-bit Unix epoch milliseconds, version 7, 12 random bits, the RFC 4122
     * variant, 62 random bits.
     */
    public static UUID newUuidV7() {
        long millis = System.currentTimeMillis();
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);

        long msb = (millis & 0xFFFF_FFFF_FFFFL) << 16;
        msb |= 0x7000L;                                              // version 7
        msb |= ((random[0] & 0x0FL) << 8) | (random[1] & 0xFFL);     // rand_a (12 bits)

        long lsb = 0;
        for (int i = 2; i < 10; i++) {
            lsb = (lsb << 8) | (random[i] & 0xFFL);
        }
        lsb = (lsb & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L; // variant 10

        return new UUID(msb, lsb);
    }

    /** True for a well-formed UUID string of version 7 and the RFC 4122 variant. */
    public static boolean isUuidV7(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.version() == 7 && uuid.variant() == 2 && uuid.toString().equals(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
