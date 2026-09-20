package com.planit.auth.id;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.UUID;

@Component
public class UuidV7Generator {

    private final SecureRandom secureRandom =
            new SecureRandom();

    public UUID generate() {
        long timestamp = System.currentTimeMillis()
                & 0x0000FFFFFFFFFFFFL;

        byte[] randomBytes = new byte[10];
        secureRandom.nextBytes(randomBytes);

        long randomA = ((randomBytes[0] & 0xFFL) << 4)
                | ((randomBytes[1] & 0xF0L) >>> 4);

        long mostSignificantBits = (timestamp << 16)
                | 0x7000L
                | randomA;

        long randomB = 0L;
        for (int index = 2; index < randomBytes.length; index++) {
            randomB = (randomB << 8)
                    | (randomBytes[index] & 0xFFL);
        }

        long leastSignificantBits =
                (randomB & 0x3FFFFFFFFFFFFFFFL)
                        | 0x8000000000000000L;

        return new UUID(
                mostSignificantBits,
                leastSignificantBits
        );
    }
}
