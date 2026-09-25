package com.planit.region.dto;

import java.math.BigDecimal;
import java.util.List;

public record RegionListResponse(
        List<RegionItem> regions
) {
    public RegionListResponse {
        regions = List.copyOf(regions);
    }

    public record RegionItem(
            String regionId,
            String regionCode,
            String regionName,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }
}
