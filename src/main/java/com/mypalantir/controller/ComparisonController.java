package com.mypalantir.controller;

import com.mypalantir.service.DataComparisonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/comparison")
public class ComparisonController {

    @Autowired
    private DataComparisonService comparisonService;

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<DataComparisonService.ComparisonResult>> runComparison(
            @RequestBody DataComparisonService.ComparisonRequest request) {
        try {
            DataComparisonService.ComparisonResult result = comparisonService.runComparison(request);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(ApiResponse.error(500, e.getMessage()));
        }
    }
}
