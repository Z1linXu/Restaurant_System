package com.restaurant.system.integration.ubereats.controller;

import com.restaurant.system.integration.ubereats.service.UberEatsWebhookService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/integrations/uber-eats")
public class UberEatsWebhookController {
    private final UberEatsWebhookService service;

    public UberEatsWebhookController(UberEatsWebhookService service) {
        this.service = service;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(HttpServletRequest request) throws IOException {
        service.receive(
                request.getInputStream().readNBytes(262145),
                request.getHeader("X-Uber-Signature"),
                request.getHeader("X-Environment"));
        return ResponseEntity.ok().build();
    }
}
