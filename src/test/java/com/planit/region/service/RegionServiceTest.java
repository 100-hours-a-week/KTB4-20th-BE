package com.planit.region.service;

import com.planit.domain.Region;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.region.dto.RegionListResponse;
import com.planit.repository.RegionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegionServiceTest {

    @Test
    void returnsRegionsInRepositoryOrder() {
        RegionRepository repository = mock(RegionRepository.class);
        Region seoul = region(1L, "REGION-SEOUL", "서울", "37.566500", "126.978000");
        Region gyeongju = region(2L, "REGION-GYEONGJU", "경주", "35.856200", "129.224700");
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(seoul, gyeongju));
        RegionService service = new RegionService(repository);

        RegionListResponse response = service.getRegions();

        assertThat(response.regions())
                .extracting(RegionListResponse.RegionItem::regionName)
                .containsExactly("서울", "경주");
        assertThat(response.regions().getFirst().regionId()).isEqualTo("1");
        assertThat(response.regions().getFirst().latitude())
                .isEqualByComparingTo("37.566500");
    }

    @Test
    void rejectsEmptyRegionCatalog() {
        RegionRepository repository = mock(RegionRepository.class);
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of());
        RegionService service = new RegionService(repository);

        assertThatThrownBy(service::getRegions)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REGION_CATALOG_UNAVAILABLE);
    }

    private Region region(
            Long id,
            String code,
            String name,
            String latitude,
            String longitude
    ) {
        Region region = mock(Region.class);
        when(region.getId()).thenReturn(id);
        when(region.getCode()).thenReturn(code);
        when(region.getName()).thenReturn(name);
        when(region.getLatitude()).thenReturn(new BigDecimal(latitude));
        when(region.getLongitude()).thenReturn(new BigDecimal(longitude));
        return region;
    }
}
