package com.restaurant.system.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.restaurant.system.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void responseStatusException401KeepsStatus() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleResponseStatusException(
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid device token")
        );

        assertEquals(401, response.getStatusCode().value());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Invalid device token", response.getBody().getMessage());
    }

    @Test
    void responseStatusException403KeepsStatus() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleResponseStatusException(
            new ResponseStatusException(HttpStatus.FORBIDDEN, "Device cannot access this store")
        );

        assertEquals(403, response.getStatusCode().value());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Device cannot access this store", response.getBody().getMessage());
    }

    @Test
    void responseStatusException404KeepsStatus() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleResponseStatusException(
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found")
        );

        assertEquals(404, response.getStatusCode().value());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Device not found", response.getBody().getMessage());
    }

    @Test
    void responseStatusException409KeepsStatus() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleResponseStatusException(
            new ResponseStatusException(HttpStatus.CONFLICT, "Print job is not available to claim")
        );

        assertEquals(409, response.getStatusCode().value());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Print job is not available to claim", response.getBody().getMessage());
    }

    @Test
    void unknownRuntimeExceptionStillReturns500() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(
            new RuntimeException("boom")
        );

        assertEquals(500, response.getStatusCode().value());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Internal server error", response.getBody().getMessage());
    }
}
