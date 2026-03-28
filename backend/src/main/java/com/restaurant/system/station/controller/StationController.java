package com.restaurant.system.station.controller;

import com.restaurant.system.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stations")
public class StationController {

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("station module ready");
    }
}
