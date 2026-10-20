package com.aqualink.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class WaterOrderController {

    private final WaterPoolingService poolingService;

    @PostMapping("/water-orders")
    public ResponseEntity<WaterOrderResponse> createRequest(
            @RequestHeader("X-Requester-Id") Long requesterId,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody CreateWaterRequest request) {

        // Temporary integration point. Replace X-Requester-Id with
        // the authenticated user's identity before deployment.
        WaterOrder order = poolingService.createRequest(
                requesterId,
                request.householdId(),
                request.quantityL(),
                request.windowStart(),
                request.windowEnd(),
                idempotencyKey,
                request.notes()
        );

        WaterOrderResponse response = WaterOrderResponse.from(order);

        return ResponseEntity
                .created(URI.create(
                        "/api/v1/water-orders/" + order.getPublicId()))
                .body(response);
    }

    @PostMapping("/service-points/{servicePointId}/pooling-runs")
    @ResponseStatus(HttpStatus.OK)
    public int runPooling(
            @PathVariable Long servicePointId,
            @Valid @RequestBody PoolingRunRequest request) {

        // Temporary development endpoint.
        // Add authentication and manager/admin authorization
        // before exposing this endpoint outside your local environment.
        return poolingService.poolAtServicePoint(
                servicePointId,
                request.maxPoolLitres(),
                request.minMembers()
        );
    }
}