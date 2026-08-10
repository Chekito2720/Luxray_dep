package com.luxray.analytics.dto;

import java.util.List;

public record KpiDto(
        String label,
        String value,
        double delta,
        String icon,
        String color
) { }
