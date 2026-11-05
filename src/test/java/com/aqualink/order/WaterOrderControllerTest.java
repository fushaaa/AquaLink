package com.aqualink.order;

import com.aqualink.user.User;
import com.aqualink.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaterOrderControllerTest {

    private static final Instant START = Instant.parse("2026-11-06T09:00:00Z");
    private static final Instant END = Instant.parse("2026-11-06T11:00:00Z");

    @Mock private WaterPoolingService poolingService;
    @Mock private WaterOrderRepository orders;
    @Mock private UserRepository users;

    private WaterOrderController controller;
    private UserDetails principal;
    private User user;

    @BeforeEach
    void setUp() {
        controller = new WaterOrderController(poolingService, orders, users);
        principal = mock(UserDetails.class);
        user = mock(User.class);
        when(principal.getUsername()).thenReturn("+15555550123");
    }

    @Test
    void createsOrderForAuthenticatedUserAndReturnsLocation() {
        stubUser();
        UUID householdId = UUID.fromString("a1000000-0000-4000-8000-000000000004");
        CreateWaterRequest request = new CreateWaterRequest(householdId, 1200, START, END, "Gate 2");
        WaterOrder order = order("order-123");
        when(poolingService.createRequest(40L, householdId, 1200, START, END,
                "request-1", "Gate 2")).thenReturn(order);

        var response = controller.createRequest(principal, "request-1", request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/v1/water-orders/order-123");
        assertThat(response.getBody()).extracting(WaterOrderResponse::publicId)
                .isEqualTo("order-123");
        verify(poolingService).createRequest(40L, householdId, 1200, START, END,
                "request-1", "Gate 2");
    }

    @Test
    void listsOnlyRepositoryReturnedMemberOrders() {
        stubUser();
        WaterOrder order = order("order-123");
        when(orders.findAllForMemberUserId(eq(40L), any())).thenReturn(new PageImpl<>(List.of(order)));

        List<WaterOrderResponse> response = controller.listOrders(principal);

        assertThat(response).singleElement()
                .extracting(WaterOrderResponse::publicId)
                .isEqualTo("order-123");
        verify(orders).findAllForMemberUserId(eq(40L), any());
    }

    @Test
    void returnsNotFoundForInaccessibleOrder() {
        stubUser();
        when(orders.findAccessibleByPublicId("private-order", 40L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getOrder(principal, "private-order"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    void mapsMissingAccountToUnauthorized() {
        when(users.findByPhoneE164("+15555550123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.listOrders(principal))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");
        verify(orders, never()).findAllForMemberUserId(any(), any());
    }

    @Test
    void preventsUnscopedManagerFromRunningPool() {
        stubUser();
        when(users.countServicePointManagerRoles(40L, 20L)).thenReturn(0L);

        assertThatThrownBy(() -> controller.runPooling(
                principal, 20L, new PoolingRunRequest(1000, 2, 1)))
                .isInstanceOf(AccessDeniedException.class);
        verify(poolingService, never()).poolAtServicePoint(any(), any(Integer.class), any(Integer.class));
    }

    @Test
    void delegatesPoolingToScopedManager() {
        stubUser();
        when(users.countServicePointManagerRoles(40L, 20L)).thenReturn(1L);
        when(poolingService.poolAtServicePoint(20L, 1000, 2)).thenReturn(2);

        int pooled = controller.runPooling(
                principal, 20L, new PoolingRunRequest(1000, 2, 1));

        assertThat(pooled).isEqualTo(2);
    }

    private WaterOrder order(String publicId) {
        WaterOrder order = mock(WaterOrder.class);
        when(order.getPublicId()).thenReturn(publicId);
        when(order.getOrderType()).thenReturn(OrderType.INDIVIDUAL);
        when(order.getStatus()).thenReturn(OrderStatus.PENDING);
        when(order.getQuantityL()).thenReturn(1200);
        when(order.getWindowStart()).thenReturn(START);
        when(order.getWindowEnd()).thenReturn(END);
        when(order.getCreatedAt()).thenReturn(START.minusSeconds(300));
        return order;
    }

    private void stubUser() {
        when(user.getId()).thenReturn(40L);
        when(users.findByPhoneE164("+15555550123")).thenReturn(Optional.of(user));
    }
}