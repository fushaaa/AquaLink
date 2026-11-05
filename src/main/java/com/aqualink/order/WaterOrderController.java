package com.aqualink.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import com.aqualink.user.User;
import com.aqualink.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class WaterOrderController {

    private final WaterPoolingService poolingService;
    private final WaterOrderRepository orders;
    private final UserRepository users;

    @PostMapping("/water-orders")
    public ResponseEntity<WaterOrderResponse> createRequest(
            @AuthenticationPrincipal UserDetails principal,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody CreateWaterRequest request) {

        User requester = requireUser(principal);
        WaterOrder order = poolingService.createRequest(
                requester.getId(),
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

    @GetMapping("/water-orders")
    public List<WaterOrderResponse> listOrders(
            @AuthenticationPrincipal UserDetails principal) {
        User requester = requireUser(principal);
        return orders.findAllForMemberUserId(requester.getId(), PageRequest.of(0, 50))
                .map(WaterOrderResponse::from)
                .toList();
    }

    @GetMapping("/water-orders/{publicId}")
    public WaterOrderResponse getOrder(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable String publicId) {
        User requester = requireUser(principal);
        WaterOrder order = orders.findAccessibleByPublicId(publicId, requester.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return WaterOrderResponse.from(order);
    }

    @PostMapping("/service-points/{servicePointId}/pooling-runs")
    @ResponseStatus(HttpStatus.OK)
        @PreAuthorize("hasAnyRole('COMMUNITY_MANAGER', 'ADMIN')")
    public int runPooling(
                        @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long servicePointId,
            @Valid @RequestBody PoolingRunRequest request) {

                User requester = requireUser(principal);
                if (!users.canManageServicePoint(requester.getId(), servicePointId)) {
                        throw new AccessDeniedException("User cannot manage this service point");
                }
        return poolingService.poolAtServicePoint(
                servicePointId,
                request.maxPoolLitres(),
                request.minMembers()
        );
    }

        private User requireUser(UserDetails principal) {
                return users.findByPhoneE164(principal.getUsername())
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        }
}