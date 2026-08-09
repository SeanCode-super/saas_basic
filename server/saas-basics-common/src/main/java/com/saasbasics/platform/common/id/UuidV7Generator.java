package com.saasbasics.platform.common.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Generates time-ordered UUID version 7 identifiers defined by RFC 9562. */
public final class UuidV7Generator {

    private static final long TIMESTAMP_MASK = 0x0000FFFFFFFFFFFFL;
    private static final long RANDOM_B_MASK = 0x3FFFFFFFFFFFFFFFL;

    private final Clock clock;
    private final SecureRandom random;
    private long lastTimestamp = -1L;
    private int sequence;

    public UuidV7Generator() {
        this(Clock.systemUTC(), new SecureRandom());
    }

    UuidV7Generator(Clock clock, SecureRandom random) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public synchronized UUID generate() {
        long timestamp = Math.max(clock.millis(), lastTimestamp);
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & 0x0FFF;
            if (sequence == 0) {
                timestamp = lastTimestamp + 1;
            }
        } else {
            sequence = random.nextInt(0x1000);
        }
        lastTimestamp = timestamp;

        long mostSignificantBits = ((timestamp & TIMESTAMP_MASK) << 16)
                | 0x0000000000007000L
                | sequence;
        long leastSignificantBits = 0x8000000000000000L
                | (random.nextLong() & RANDOM_B_MASK);
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
