package com.luxray.analytics.controller;

import com.luxray.analytics.dto.DashboardResponse;
import com.luxray.analytics.service.DashboardService;
import com.luxray.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics")
@Tag(name = "Analytics", description = "Métricas y dashboard del usuario")
public class AnalyticsController {

    private final DashboardService service;

    public AnalyticsController(DashboardService service) { this.service = service; }

    @GetMapping("/dashboard")
    @Operation(summary = "KPIs, series temporales y top cursos del usuario")
    public ResponseEntity<ApiResponse<DashboardResponse>> dashboard(
            @RequestHeader("Authorization") String auth) {
        return ResponseEntity.ok(service.dashboard(auth));
    }
}
