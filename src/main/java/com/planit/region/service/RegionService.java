package com.planit.region.service;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.region.dto.RegionListResponse;
import com.planit.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionService {

    private final RegionRepository regionRepository;

    public RegionListResponse getRegions() {
        List<RegionListResponse.RegionItem> regions = regionRepository
                .findAllByOrderByIdAsc()
                .stream()
                .map(region -> new RegionListResponse.RegionItem(
                        region.getId().toString(),
                        region.getCode(),
                        region.getName(),
                        region.getLatitude(),
                        region.getLongitude()
                ))
                .toList();

        if (regions.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.REGION_CATALOG_UNAVAILABLE
            );
        }

        return new RegionListResponse(regions);
    }
}
