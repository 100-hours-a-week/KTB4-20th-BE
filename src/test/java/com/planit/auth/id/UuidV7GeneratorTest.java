package com.planit.auth.id;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7GeneratorTest {

    private final UuidV7Generator uuidV7Generator =
            new UuidV7Generator();

    @DisplayName("UUID 버전 7을 생성한다")
    @Test
    void generatesUuidVersionSeven() {
        UUID uuid = uuidV7Generator.generate();

        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @DisplayName("호출할 때마다 서로 다른 UUID를 생성한다")
    @Test
    void generatesDifferentUuidEachTime() {
        UUID firstUuid = uuidV7Generator.generate();
        UUID secondUuid = uuidV7Generator.generate();

        assertThat(firstUuid).isNotEqualTo(secondUuid);
    }
}
