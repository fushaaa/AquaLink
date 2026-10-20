package com.aqualink.order;

import com.aqualink.community.CommunityRepository;
import com.aqualink.household.Household;
import com.aqualink.household.HouseholdRepository;
import com.aqualink.provider.WaterProviderRepository;
import com.aqualink.user.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WaterPoolingService {

    private final WaterOrderRepository orders;
    private final HouseholdRepository households;
    private final CommunityRepository communities;
    private final WaterProviderRepository providers;
    private final UserRepository users;
    private final Clock clock;

    @Transactional
    public WaterOrder createRequest(
            Long requesterId,
            UUID householdPublicId,
            int quantityL,
            Instant windowStart,
            Instant windowEnd,
            String idempotencyKey,
            String notes) {

        if (quantityL <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        Instant now = clock.instant();
        if (!windowStart.isAfter(now) || !windowEnd.isAfter(windowStart)) {
            throw new IllegalArgumentException("Invalid delivery window");
        }

        Household household = households.findByPublicId(householdPublicId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Household not found"));

        // TODO authorization: verify requesterId belongs to this household via household_users
        // (e.g. households.isMember(household.getId(), requesterId)) before allowing the order.

        if (idempotencyKey != null) {
            var existing = orders.findByHouseholdIdAndIdempotencyKey(
                    household.getId(), idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Instant poolBy = windowStart.minus(Duration.ofHours(2));
        if (poolBy.isBefore(now)) {
            poolBy = now;
        }

        // TODO: derive requesterId from the authenticated principal, never from the request body.
        var requester = users.getReferenceById(requesterId);

        WaterOrder order = WaterOrder.individual(
                household, requester, quantityL,
                windowStart, windowEnd, poolBy,
                idempotencyKey, notes);

        return orders.save(order);
    }

    @Transactional
    public int poolAtServicePoint(Long servicePointId, int maxPoolLitres, int minMembers) {

        if (maxPoolLitres <= 0 || minMembers < 1) {
            throw new IllegalArgumentException("Invalid pooling configuration");
        }

        var firstPage = orders.findSharedPointCandidates(
                servicePointId,
                OrderType.INDIVIDUAL,
                OrderStatus.PENDING,
                Instant.now(clock),
                Instant.parse("9999-12-31T00:00:00Z"),
                PageRequest.of(0, 1));

        if (firstPage.isEmpty()) {
            return 0;
        }

        WaterOrder anchor = firstPage.getContent().get(0);

        var candidates = orders.findSharedPointCandidates(
                servicePointId,
                OrderType.INDIVIDUAL,
                OrderStatus.PENDING,
                anchor.getWindowStart(),
                anchor.getWindowEnd(),
                PageRequest.of(0, 100));

        // The anchor always joins, even if it alone exceeds the cap (pool of one).
        List<WaterOrder> selected = new ArrayList<>();
        selected.add(anchor);
        int totalLitres = anchor.getQuantityL();
        Instant ws = anchor.getWindowStart();
        Instant we = anchor.getWindowEnd();

        for (WaterOrder c : candidates.getContent()) {
            if (Objects.equals(c.getId(), anchor.getId())) continue;
            if (c.getQuantityL() > maxPoolLitres - totalLitres) continue;

            // Keep the shared delivery window non-empty as members are added.
            Instant nws = c.getWindowStart().isAfter(ws) ? c.getWindowStart() : ws;
            Instant nwe = c.getWindowEnd().isBefore(we) ? c.getWindowEnd() : we;
            if (!nwe.isAfter(nws)) continue;

            selected.add(c);
            totalLitres += c.getQuantityL();
            ws = nws;
            we = nwe;
        }

        Instant now = clock.instant();
        boolean deadlineReached = selected.stream()
                .anyMatch(o -> !o.getPoolBy().isAfter(now));

        if (selected.size() < minMembers && !deadlineReached) {
            return 0;
        }

        WaterOrder pool = WaterOrder.newPool(anchor);
        for (WaterOrder request : selected) {      // includes the anchor
            pool.addMember(request);
        }
        orders.save(pool);

        return selected.size();
    }
}