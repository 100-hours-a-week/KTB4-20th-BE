package com.planit.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class TripRepositoryTest {

    @DisplayName("여행 멤버 변경용 조회에 비관적 쓰기 잠금을 적용한다")
    @Test
    void locksTripForMembershipChanges() throws NoSuchMethodException {
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
