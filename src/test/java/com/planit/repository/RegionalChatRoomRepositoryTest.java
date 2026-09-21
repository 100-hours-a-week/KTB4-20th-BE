package com.planit.repository;

import com.planit.domain.RegionalChatRoom;
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
    private BroadRegionRepository broadRegionRepository;

    @Autowired
    private SubRegionRepository subRegionRepository;

    @Autowired
    private RegionalChatRoomRepository regionalChatRoomRepository;

    @Test
    void mapsEverySeededRegionTable() {
        assertThat(broadRegionRepository.count()).isEqualTo(17);
        assertThat(subRegionRepository.count()).isEqualTo(229);
        assertThat(regionalChatRoomRepository.count()).isEqualTo(229);
    }

    @Test
    void traversesRegionalChatRoomRelations() {
        RegionalChatRoom room = regionalChatRoomRepository.findByRegion_Id(1L)
                .orElseThrow();

        assertThat(room.getId()).isEqualTo(1L);
        assertThat(room.getRegion().getId()).isEqualTo(1L);
        assertThat(room.getRegion().getName()).isEqualTo("강남구");
        assertThat(room.getRegion().getBroadRegion().getName()).isEqualTo("서울특별시");
        assertThat(room.getName()).isEqualTo("서울특별시 강남구");
    }

    @Test
    void queriesRoomListFieldsForUserContext() {
        var rooms = regionalChatRoomRepository.findListEntries(
                -1L,
                LocalDate.of(2026, 9, 20)
        );

        assertThat(rooms).hasSize(229);
        assertThat(rooms).allSatisfy(room -> {
            assertThat(room.getRelatedToMyTrip()).isFalse();
            assertThat(room.getJoined()).isFalse();
            assertThat(room.getMemberCount()).isNotNegative();
            assertThat(room.getCanJoin()).isFalse();
        });
    }
}
