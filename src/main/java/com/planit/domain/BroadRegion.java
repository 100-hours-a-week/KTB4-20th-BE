package com.planit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "broad_regions")
public class BroadRegion {

    @Id
    private Long id;

    @Column(name = "broad_region_code", nullable = false, length = 50)
    private String code;

    @Column(name = "broad_region_name", nullable = false, length = 50)
    private String name;

    protected BroadRegion() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
