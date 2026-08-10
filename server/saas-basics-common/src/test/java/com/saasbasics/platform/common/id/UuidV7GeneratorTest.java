package com.saasbasics.platform.common.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {

    @Test
    void generatesRfc9562VersionAndVariant() {
        UuidV7Generator generator = new UuidV7Generator(
                Clock.fixed(Instant.ofEpochMilli(1_700_000_000_123L), ZoneOffset.UTC),
                new SecureRandom()
        );

        UUID id = generator.generate();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(1_700_000_000_123L);
    }

    @Test
    void remainsMonotonicWithinTheSameMillisecond() {
        UuidV7Generator generator = new UuidV7Generator(
                Clock.fixed(Instant.ofEpochMilli(1_700_000_000_123L), ZoneOffset.UTC),
                new SecureRandom()
        );

        UUID first = generator.generate();
        UUID second = generator.generate();

        assertThat(second.getMostSignificantBits()).isGreaterThan(first.getMostSignificantBits());
    }
}
