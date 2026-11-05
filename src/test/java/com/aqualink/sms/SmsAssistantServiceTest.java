package com.aqualink.sms;

import com.aqualink.household.Household;
import com.aqualink.household.HouseholdRepository;
import com.aqualink.order.OrderStatus;
import com.aqualink.order.WaterOrder;
import com.aqualink.order.WaterOrderRepository;
import com.aqualink.order.WaterPoolingService;
import com.aqualink.user.User;
import com.aqualink.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsAssistantServiceTest {

    private static final String PHONE = "+15555550123";
    private static final Long USER_ID = 40L;
    private static final UUID HOUSEHOLD_ID = UUID.fromString("a1000000-0000-4000-8000-000000000004");

    @Mock private UserRepository users;
    @Mock private HouseholdRepository households;
    @Mock private WaterOrderRepository orders;
    @Mock private WaterPoolingService poolingService;
    @Mock private AiSmsAssistant aiAssistant;

    private SmsAssistantService service;

    @BeforeEach
    void setUp() {
        service = new SmsAssistantService(users, households, orders, poolingService, aiAssistant);
    }

    @Test
    void emptyAndOversizedMessagesAreRejectedWithoutCallingServices() {
        assertThat(service.handleMessage(PHONE, "   ", "SM1"))
                .contains("didn't receive a message");
        assertThat(service.handleMessage(PHONE, "x".repeat(1601), "SM2"))
                .contains("too long");
        verifyNoInteractions(users, households, orders, poolingService, aiAssistant);
    }

    @Test
    void helpAndFreeformQuestionsAreForwardedToAi() {
        when(aiAssistant.answer("Help me use AquaLink to order water.")).thenReturn("Order by SMS.");
        when(aiAssistant.answer("where is my water")).thenReturn("Reply STATUS to check.");

        assertThat(service.handleMessage(PHONE, " help ", "SM1")).isEqualTo("Order by SMS.");
        assertThat(service.handleMessage(PHONE, "where is my water", "SM2"))
                .isEqualTo("Reply STATUS to check.");
        verifyNoInteractions(users, households, orders, poolingService);
    }

    @Test
    void malformedOrderAndMissingMessageSidDoNotReachAccountOrOrderServices() {
        assertThat(service.handleMessage(PHONE, "ORDER 500", "SM1"))
                .contains("To order:");
        assertThat(service.handleMessage(PHONE,
                "ORDER 500 2026-11-06T09:00:00Z 2026-11-06T11:00:00Z", null))
                .contains("retry protection");
        verifyNoInteractions(users, households, orders, poolingService, aiAssistant);
    }

    @Test
    void unknownSenderAndUnlinkedHouseholdReceiveActionableReplies() {
        when(users.findByPhoneE164(PHONE)).thenReturn(Optional.empty());
        assertThat(service.handleMessage(PHONE,
                "ORDER 500 2026-11-06T09:00:00Z 2026-11-06T11:00:00Z", "SM1"))
                .contains("not linked");

        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(users.findByPhoneE164(PHONE)).thenReturn(Optional.of(user));
        when(households.findPrimaryByMemberUserId(USER_ID)).thenReturn(Optional.empty());
        assertThat(service.handleMessage(PHONE,
                "ORDER 500 2026-11-06T09:00:00Z 2026-11-06T11:00:00Z", "SM2"))
                .contains("No household is linked");
        verifyNoInteractions(poolingService, orders, aiAssistant);
    }

    @Test
    void enforcesQuantityBoundsBeforeCreatingOrder() {
                stubOrderAccount(false);

        assertThat(service.handleMessage(PHONE,
                "ORDER 50001 2026-11-06T09:00:00Z 2026-11-06T11:00:00Z", "SM1"))
                .contains("between 1 and 50000");
        assertThat(service.handleMessage(PHONE,
                "ORDER 0 2026-11-06T09:00:00Z 2026-11-06T11:00:00Z", "SM2"))
                .contains("between 1 and 50000");
        verify(poolingService, never()).createSmsRequest(
                eq(USER_ID), eq(HOUSEHOLD_ID), eq(500), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void validOrderUsesSmsIdempotencyKeyAndPrimaryHousehold() {
                stubOrderAccount(true);
        WaterOrder order = mock(WaterOrder.class);
        when(order.getQuantityL()).thenReturn(2000);
        when(order.getStatus()).thenReturn(OrderStatus.PENDING);
        when(poolingService.createSmsRequest(
                USER_ID, HOUSEHOLD_ID, 2000,
                Instant.parse("2026-11-06T09:00:00Z"),
                Instant.parse("2026-11-06T11:00:00Z"), "sms-SM123", "Placed by SMS"))
                .thenReturn(order);

        String reply = service.handleMessage(PHONE,
                "order 2000 2026-11-06T09:00:00Z 2026-11-06T11:00:00Z", "SM123");

        assertThat(reply).contains("your 2000 L order is received", "PENDING");
        verify(poolingService).createSmsRequest(
                USER_ID, HOUSEHOLD_ID, 2000,
                Instant.parse("2026-11-06T09:00:00Z"),
                Instant.parse("2026-11-06T11:00:00Z"), "sms-SM123", "Placed by SMS");
    }

    @Test
    void invalidDateAndEndBeforeStartReturnGuidance() {
                stubOrderAccount(true);

        assertThat(service.handleMessage(PHONE,
                "ORDER 500 not-a-time 2026-11-06T11:00:00Z", "SM1"))
                .contains("couldn't place that order");
        verify(poolingService, never()).createSmsRequest(
                eq(USER_ID), eq(HOUSEHOLD_ID), eq(500), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());

        when(poolingService.createSmsRequest(
                USER_ID, HOUSEHOLD_ID, 500,
                Instant.parse("2026-11-06T11:00:00Z"),
                Instant.parse("2026-11-06T09:00:00Z"), "sms-SM2", "Placed by SMS"))
                .thenThrow(new IllegalArgumentException("Invalid delivery window"));
        assertThat(service.handleMessage(PHONE,
                "ORDER 500 2026-11-06T11:00:00Z 2026-11-06T09:00:00Z", "SM2"))
                .contains("couldn't place that order");
        verify(poolingService).createSmsRequest(
                USER_ID, HOUSEHOLD_ID, 500,
                Instant.parse("2026-11-06T11:00:00Z"),
                Instant.parse("2026-11-06T09:00:00Z"), "sms-SM2", "Placed by SMS");
    }

    @Test
    void statusReturnsLatestOrderOrAnEmptyState() {
        User user = mock(User.class);
        Household household = mock(Household.class);
        when(user.getId()).thenReturn(USER_ID);
        when(household.getId()).thenReturn(30L);
        when(users.findByPhoneE164(PHONE)).thenReturn(Optional.of(user));
        when(households.findPrimaryByMemberUserId(USER_ID)).thenReturn(Optional.of(household));
        WaterOrder order = mock(WaterOrder.class);
        when(order.getQuantityL()).thenReturn(750);
        when(order.getStatus()).thenReturn(OrderStatus.DELIVERED);
        when(order.getWindowStart()).thenReturn(Instant.parse("2026-11-06T09:00:00Z"));
        when(order.getWindowEnd()).thenReturn(Instant.parse("2026-11-06T11:00:00Z"));
        when(orders.findTopByHouseholdIdOrderByCreatedAtDesc(30L)).thenReturn(Optional.of(order));

        assertThat(service.handleMessage(PHONE, "STATUS", "SM1"))
                .contains("750 L", "DELIVERED", "2026-11-06T09:00:00Z");

        when(orders.findTopByHouseholdIdOrderByCreatedAtDesc(30L)).thenReturn(Optional.empty());
        assertThat(service.handleMessage(PHONE, "status", "SM2"))
                .isEqualTo("There are no water orders for your household yet.");
    }

        private void stubOrderAccount(boolean includeHouseholdPublicId) {
        User user = mock(User.class);
        Household household = mock(Household.class);
        when(user.getId()).thenReturn(USER_ID);
                if (includeHouseholdPublicId) {
                        when(household.getPublicId()).thenReturn(HOUSEHOLD_ID.toString());
                }
        when(users.findByPhoneE164(PHONE)).thenReturn(Optional.of(user));
        when(households.findPrimaryByMemberUserId(USER_ID)).thenReturn(Optional.of(household));
    }
}