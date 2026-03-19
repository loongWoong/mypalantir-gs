package com.mypalantir.controller;

import com.mypalantir.config.Config;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/app")
public class AppInfoController {
    private final Config config;

    public AppInfoController(Config config) {
        this.config = config;
    }

    @GetMapping("/info")
    public ResponseEntity<ApiResponse<Map<String, String>>> info() {
        Map<String, String> data = new HashMap<>();
        data.put("appVersion", config.getAppVersion());
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}

