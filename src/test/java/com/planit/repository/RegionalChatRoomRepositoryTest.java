package com.planit.repository;

import com.planit.domain.RegionalChatRoom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RegionalChatRoomRepositoryTest {

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private RegionalChatRoomRepository regionalChatRoomRepository;

    @DisplayName("초기 지역 수와 지역 채팅방 수가 일치한다")
    @Test
    void mapsEverySeededRegionTable() {
        assertThat(regionRepository.count()).isEqualTo(5);
        assertThat(regionalChatRoomRepository.count()).isEqualTo(5);
    }

    @DisplayName("지역 ID로 채팅방을 조회하면 연결된 지역 정보도 반환한다")
    @Test
    void traversesRegionalChatRoomRelations() {
        RegionalChatRoom room = regionalChatRoomRepository.findByRegion_Id(1L)
                .orElseThrow();

        assertThat(room.getId()).isEqualTo(1L);
        assertThat(room.getRegion().getId()).isEqualTo(1L);
        assertThat(room.getRegion().getName()).isEqualTo("서울");
        assertThat(room.getName()).isEqualTo("서울");
    }

    @DisplayName("참여 이력이 없는 사용자는 모든 지역 채팅방에서 미참여 상태로 표시된다")
    @Test
    void queriesRoomListFieldsForUserContext() {
        var rooms = regionalChatRoomRepository.findListEntries(
                -1L,
                LocalDate.of(2026, 9, 20)
        );

        assertThat(rooms).hasSize(5);
        assertThat(rooms).allSatisfy(room -> {
            assertThat(room.getRelatedToMyTrip()).isFalse();
            assertThat(room.getJoined()).isFalse();
            assertThat(room.getMemberCount()).isNotNegative();
            assertThat(room.getCanJoin()).isFalse();
        });
    }
}
