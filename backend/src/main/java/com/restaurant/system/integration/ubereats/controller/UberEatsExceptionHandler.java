package com.restaurant.system.integration.ubereats.controller;

import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.integration.ubereats.client.UberEatsApiException;
import com.restaurant.system.integration.ubereats.service.UberEatsException;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackages = "com.restaurant.system.integration.ubereats.controller")
@Order(-10)
public class UberEatsExceptionHandler {
    @ExceptionHandler(UberEatsException.class)
    public ResponseEntity<ApiResponse<Void>> handle(UberEatsException ex) {
        return ResponseEntity.status(ex.status).body(ApiResponse.failure(ex.getMessage()));
    }

    @ExceptionHandler(UberEatsApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(UberEatsApiException ex) {
        return ResponseEntity.status(502).body(ApiResponse.failure(ex.getMessage()));
    }
}
