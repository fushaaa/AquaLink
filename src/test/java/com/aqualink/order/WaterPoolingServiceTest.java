package com.aqualink.order;

import com.aqualink.household.Household;
import com.aqualink.household.HouseholdRepository;
import com.aqualink.household.community.Community;
import com.aqualink.household.community.CommunityRepository;
import com.aqualink.provider.WaterProviderRepository;
import com.aqualink.servicepoint.ServicePoint;
import com.aqualink.sms.SmsNotificationService;
import com.aqualink.user.User;
import com.aqualink.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class WaterPoolingServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");
    private static final UUID HOUSEHOLD_ID = UUID.fromString("a1000000-0000-4000-8000-000000000004");

    @Mock private WaterOrderRepository orders;
    @Mock private HouseholdRepository households;
    @Mock private CommunityRepository communities;
    @Mock private WaterProviderRepository providers;
    @Mock private UserRepository users;
    @Mock private SmsNotificationService smsNotifications;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private WaterPoolingService service;
    private Household household;
    private User requester;

    @BeforeEach
    void setUp() {
        service = new WaterPoolingService(
                orders, households, communities, providers, users, clock, smsNotifications);
        household = mock(Household.class);
        requester = mock(User.class);
    }

    @Test
    void rejectsInvalidQuantityBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.createRequest(
                40L, HOUSEHOLD_ID, 0, NOW.plusSeconds(3600), NOW.plusSeconds(7200), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantity must be greater than zero");
        verifyNoInteractions(households, orders, users, smsNotifications);
    }

    @Test
    void rejectsInvalidDeliveryWindow() {
        assertThatThrownBy(() -> service.createRequest(
                40L, HOUSEHOLD_ID, 500, NOW, NOW.plusSeconds(3600), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid delivery window");
        verifyNoInteractions(households, orders, users, smsNotifications);
    }

    @Test
    void refusesToCreateAnOrderForANonMember() {
                when(household.getId()).thenReturn(30L);
        when(households.findByPublicId(HOUSEHOLD_ID.toString())).thenReturn(Optional.of(household));
        when(households.countMemberships(30L, 40L)).thenReturn(0L);

        assertThatThrownBy(() -> service.createRequest(
                40L, HOUSEHOLD_ID, 500, NOW.plusSeconds(3600), NOW.plusSeconds(7200), null, null))
                .isInstanceOf(AccessDeniedException.class);
        verify(orders, never()).save(any());
        verifyNoInteractions(smsNotifications);
    }

    @Test
    void returnsExistingOrderForRepeatedIdempotencyKey() {
                when(household.getId()).thenReturn(30L);
        WaterOrder existing = mock(WaterOrder.class);
        when(households.findByPublicId(HOUSEHOLD_ID.toString())).thenReturn(Optional.of(household));
        when(households.countMemberships(30L, 40L)).thenReturn(1L);
        when(orders.findByHouseholdIdAndIdempotencyKey(30L, "retry-1"))
                .thenReturn(Optional.of(existing));

        WaterOrder returned = service.createRequest(
                40L, HOUSEHOLD_ID, 500, NOW.plusSeconds(3600), NOW.plusSeconds(7200), "retry-1", null);

        assertThat(returned).isSameAs(existing);
        verify(orders, never()).save(any());
        verifyNoInteractions(smsNotifications);
    }

    @Test
    void createsMemberOrderAndNotifiesAfterCommit() {
                stubHousehold();
                when(requester.getPhoneE164()).thenReturn("+15555550123");
        Instant start = NOW.plusSeconds(6 * 3600);
        Instant end = start.plusSeconds(2 * 3600);
        when(households.findByPublicId(HOUSEHOLD_ID.toString())).thenReturn(Optional.of(household));
        when(households.countMemberships(30L, 40L)).thenReturn(1L);
        when(orders.findByHouseholdIdAndIdempotencyKey(30L, "order-1"))
                .thenReturn(Optional.empty());
        when(users.getReferenceById(40L)).thenReturn(requester);
        when(orders.save(any(WaterOrder.class))).thenAnswer(
                invocation -> invocation.getArgument(0, WaterOrder.class));

        WaterOrder created = service.createRequest(
                40L, HOUSEHOLD_ID, 1500, start, end, "order-1", "Leave by gate");

        assertThat(created.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(created.getQuantityL()).isEqualTo(1500);
        assertThat(created.getPoolBy()).isEqualTo(NOW.plusSeconds(4 * 3600));
        assertThat(created.getNotes()).isEqualTo("Leave by gate");
        verify(smsNotifications).orderCreatedAfterCommit("+15555550123", created);
    }

    @Test
    void smsOrderDoesNotSendDuplicateOutboundNotification() {
                stubHousehold();
        Instant start = NOW.plusSeconds(6 * 3600);
        when(households.findByPublicId(HOUSEHOLD_ID.toString())).thenReturn(Optional.of(household));
        when(households.countMemberships(30L, 40L)).thenReturn(1L);
        when(users.getReferenceById(40L)).thenReturn(requester);
        when(orders.save(any(WaterOrder.class))).thenAnswer(
                invocation -> invocation.getArgument(0, WaterOrder.class));

        service.createSmsRequest(
                40L, HOUSEHOLD_ID, 1500, start, start.plusSeconds(3600), "sms-1", "Placed by SMS");

        verify(smsNotifications, never()).orderCreatedAfterCommit(anyString(), any());
    }

    @Test
    void doesNotPoolBeforeMinimumMembersOrDeadline() {
        WaterOrder anchor = individual(500, NOW.plusSeconds(3600), NOW.plusSeconds(7200),
                NOW.plusSeconds(1));
        when(orders.findSharedPointCandidates(eq(20L), eq(OrderType.INDIVIDUAL),
                eq(OrderStatus.PENDING), any(Instant.class), any(Instant.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(anchor)), new PageImpl<>(List.of(anchor)));

        assertThat(service.poolAtServicePoint(20L, 1000, 2)).isZero();
        verify(orders, never()).save(any());
    }

    @Test
    void createsPoolWhenDeadlineIsReachedEvenIfMinimumIsNotMet() {
        WaterOrder anchor = individual(500, NOW.plusSeconds(3600), NOW.plusSeconds(7200), NOW);
        when(orders.findSharedPointCandidates(eq(20L), eq(OrderType.INDIVIDUAL),
                eq(OrderStatus.PENDING), any(Instant.class), any(Instant.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(anchor)), new PageImpl<>(List.of(anchor)));
        when(orders.save(any(WaterOrder.class))).thenAnswer(
                invocation -> invocation.getArgument(0, WaterOrder.class));

        assertThat(service.poolAtServicePoint(20L, 1000, 2)).isEqualTo(1);
        assertThat(anchor.getStatus()).isEqualTo(OrderStatus.POOLED);
        verify(orders).save(any(WaterOrder.class));
    }

    @Test
    void selectsOnlyCandidatesWithinCapacityAndOverlappingDeliveryWindow() {
        Instant anchorStart = NOW.plusSeconds(2 * 3600);
        Instant anchorEnd = NOW.plusSeconds(6 * 3600);
        WaterOrder anchor = withId(individual(500, anchorStart, anchorEnd, NOW.plusSeconds(1800)), 1L);
        WaterOrder fitting = withId(individual(300, anchorStart.plusSeconds(3600), anchorEnd,
                NOW.plusSeconds(1800)), 2L);
        WaterOrder overCapacity = withId(individual(300, anchorStart, anchorEnd,
                NOW.plusSeconds(1800)), 3L);
        WaterOrder noOverlap = withId(individual(100, anchorEnd.plusSeconds(3600),
                anchorEnd.plusSeconds(7200), NOW.plusSeconds(1800)), 4L);
        when(orders.findSharedPointCandidates(eq(20L), eq(OrderType.INDIVIDUAL),
                eq(OrderStatus.PENDING), any(Instant.class), any(Instant.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(anchor)),
                        new PageImpl<>(List.of(anchor, fitting, overCapacity, noOverlap)));
        when(orders.save(any(WaterOrder.class))).thenAnswer(
                invocation -> invocation.getArgument(0, WaterOrder.class));

        assertThat(service.poolAtServicePoint(20L, 1000, 2)).isEqualTo(2);

        ArgumentCaptor<WaterOrder> poolCaptor = ArgumentCaptor.forClass(WaterOrder.class);
        verify(orders).save(poolCaptor.capture());
        WaterOrder pool = poolCaptor.getValue();
        assertThat(pool.getQuantityL()).isEqualTo(800);
        assertThat(pool.getMembers()).hasSize(2);
        assertThat(pool.getWindowStart()).isEqualTo(anchorStart.plusSeconds(3600));
        assertThat(pool.getWindowEnd()).isEqualTo(anchorEnd);
        assertThat(overCapacity.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(noOverlap.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void rejectsInvalidPoolingConfiguration() {
        assertThatThrownBy(() -> service.poolAtServicePoint(20L, 0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid pooling configuration");
        assertThatThrownBy(() -> service.poolAtServicePoint(20L, 1000, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid pooling configuration");
    }

    private static Household household() {
        Household household = mock(Household.class);
        ServicePoint point = mock(ServicePoint.class);
        when(household.getCommunity()).thenReturn(mock(Community.class));
        when(household.getServicePoint()).thenReturn(point);
        when(point.getArrangement()).thenReturn(Arrangement.COMMUNAL_TAP);
        when(household.getLatitude()).thenReturn(new BigDecimal("-1.286389"));
        when(household.getLongitude()).thenReturn(new BigDecimal("36.817223"));
        when(household.isVulnerable()).thenReturn(false);
        return household;
    }

        private void stubHousehold() {
                ServicePoint point = mock(ServicePoint.class);
                when(household.getId()).thenReturn(30L);
                when(household.getCommunity()).thenReturn(mock(Community.class));
                when(household.getServicePoint()).thenReturn(point);
                when(point.getArrangement()).thenReturn(Arrangement.COMMUNAL_TAP);
                when(household.getLatitude()).thenReturn(new BigDecimal("-1.286389"));
                when(household.getLongitude()).thenReturn(new BigDecimal("36.817223"));
                when(household.isVulnerable()).thenReturn(false);
        }

    private static WaterOrder individual(int quantity, Instant start, Instant end, Instant poolBy) {
        Household household = household();
        return WaterOrder.individual(household, mock(User.class), quantity,
                start, end, poolBy, null, null);
    }

        private static WaterOrder withId(WaterOrder order, Long id) {
                WaterOrder identified = spy(order);
                doReturn(id).when(identified).getId();
                return identified;
        }
}