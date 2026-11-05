package com.aqualink.order;

import com.aqualink.household.Household;
import com.aqualink.household.community.Community;
import com.aqualink.provider.WaterProvider;
import com.aqualink.servicepoint.ServicePoint;
import com.aqualink.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WaterOrderDomainTest {

    private static final Instant START = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant END = Instant.parse("2026-11-10T13:00:00Z");

    @Test
    void individualOrderCopiesHouseholdAndRequesterDetails() {
        Household household = household(true);
        User requester = mock(User.class);

        WaterOrder order = individual(household, requester, 1200, START, END);

        assertThat(order.getOrderType()).isEqualTo(OrderType.INDIVIDUAL);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getQuantityL()).isEqualTo(1200);
        assertThat(order.getPriority()).isEqualTo((short) 1);
        assertThat(order.getCommunity()).isSameAs(household.getCommunity());
        assertThat(order.getServicePoint()).isSameAs(household.getServicePoint());
        assertThat(order.getRequestedBy()).isSameAs(requester);
    }

    @Test
    void individualOrderRejectsNonPositiveQuantityAndInvalidWindow() {
        Household household = household(false);
        User requester = mock(User.class);

        assertThatThrownBy(() -> individual(household, requester, 0, START, END))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantity must be positive");
        assertThatThrownBy(() -> individual(household, requester, 100, END, START))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Delivery window end must follow its start");
    }

    @Test
    void poolTracksMembersQuantityAndSharedWindow() {
        WaterOrder anchor = individual(household(false), mock(User.class), 500,
                START, END);
        WaterOrder neighbor = individual(household(false), mock(User.class), 700,
                START.plusSeconds(3600), END.minusSeconds(3600));
        WaterOrder pool = WaterOrder.newPool(anchor);

        pool.addMember(anchor);
        pool.addMember(neighbor);

        assertThat(pool.getOrderType()).isEqualTo(OrderType.POOLED);
        assertThat(pool.getStatus()).isEqualTo(OrderStatus.AWAITING_PROVIDER);
        assertThat(pool.getQuantityL()).isEqualTo(1200);
        assertThat(pool.getWindowStart()).isEqualTo(START.plusSeconds(3600));
        assertThat(pool.getWindowEnd()).isEqualTo(END.minusSeconds(3600));
        assertThat(pool.getMembers()).hasSize(2);
        assertThat(anchor.getStatus()).isEqualTo(OrderStatus.POOLED);
        assertThat(neighbor.getStatus()).isEqualTo(OrderStatus.POOLED);
    }

    @Test
    void poolRejectsNonOverlappingWindowsWithoutChangingTotals() {
        WaterOrder anchor = individual(household(false), mock(User.class), 500,
                START, START.plusSeconds(3600));
        WaterOrder outsideWindow = individual(household(false), mock(User.class), 700,
                END, END.plusSeconds(3600));
        WaterOrder pool = WaterOrder.newPool(anchor);
        pool.addMember(anchor);

        assertThatThrownBy(() -> pool.addMember(outsideWindow))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Delivery windows do not overlap");
        assertThat(pool.getQuantityL()).isEqualTo(500);
        assertThat(pool.getMembers()).hasSize(1);
        assertThat(outsideWindow.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void providerOfferRequiresCapacityAndAppliesRoundedMemberPrice() {
        WaterOrder request = individual(household(false), mock(User.class), 100,
                START, END);
        WaterOrder pool = WaterOrder.newPool(request);
        pool.addMember(request);
        WaterProvider provider = new WaterProvider();
        provider.setTripCapacityL(100);
        provider.setPricePerLitre(new BigDecimal("0.0125"));
        provider.setCurrency("KES");

        pool.offerTo(provider, END);

        assertThat(pool.getStatus()).isEqualTo(OrderStatus.PROVIDER_OFFERED);
        assertThat(pool.getProvider()).isSameAs(provider);
        assertThat(pool.getUnitPrice()).isEqualByComparingTo("0.0125");
        assertThat(pool.getMembers().get(0).getEstimatedCost()).isEqualByComparingTo("1.25");

        WaterOrder anotherRequest = individual(household(false), mock(User.class), 100,
                START, END);
        WaterOrder tooLarge = WaterOrder.newPool(anotherRequest);
        tooLarge.addMember(anotherRequest);
        provider.setTripCapacityL(99);
        assertThatThrownBy(() -> tooLarge.offerTo(provider, END))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provider capacity is below pooled demand");
        assertThat(tooLarge.getStatus()).isEqualTo(OrderStatus.AWAITING_PROVIDER);
    }

    private static WaterOrder individual(
            Household household,
            User requester,
            int quantity,
            Instant start,
            Instant end) {
        return WaterOrder.individual(household, requester, quantity, start, end,
                start.minusSeconds(7200), null, null);
    }

    private static Household household(boolean vulnerable) {
        Household household = mock(Household.class);
        ServicePoint servicePoint = mock(ServicePoint.class);
        when(household.getCommunity()).thenReturn(mock(Community.class));
        when(household.getServicePoint()).thenReturn(servicePoint);
        when(servicePoint.getArrangement()).thenReturn(Arrangement.COMMUNAL_TAP);
        when(household.getLatitude()).thenReturn(new BigDecimal("-1.286389"));
        when(household.getLongitude()).thenReturn(new BigDecimal("36.817223"));
        when(household.isVulnerable()).thenReturn(vulnerable);
        return household;
    }
}