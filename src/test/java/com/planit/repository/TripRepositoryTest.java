package com.planit.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class TripRepositoryTest {

    @Test
    void locksTripForJoiningMember() throws NoSuchMethodException {
        Method method = TripRepository.class.getMethod(
                "findByIdForUpdate",
                Long.class
        );

        Lock lock = method.getAnnotation(Lock.class);
        assertThat(lock).isNotNull();
        assertThat(lock.value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
