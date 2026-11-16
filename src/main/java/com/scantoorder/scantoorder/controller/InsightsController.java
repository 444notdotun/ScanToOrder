package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.config.CurrentTenant;
import com.scantoorder.scantoorder.dtos.respond.ApiResponse;
import com.scantoorder.scantoorder.dtos.respond.InsightsResponse;
import com.scantoorder.scantoorder.service.InsightsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/insights")
public class InsightsController {

    @Autowired
    private InsightsService insights;

    // Managers of restaurants only. Chefs, waiters and event planners are refused.
    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping
    public ResponseEntity<ApiResponse<InsightsResponse>> insights(@RequestParam(defaultValue = "today") String period) {
        return ResponseEntity.ok(new ApiResponse<>(insights.insights(CurrentTenant.id(), period)));
    }
}
