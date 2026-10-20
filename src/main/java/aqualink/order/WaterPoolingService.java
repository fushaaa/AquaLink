package com.aqualink.order;

import com.aqualink.household.Household;
import com.aqualink.household.HouseholdRepository;
import com.aqualink.provider.WaterProviderRepository;
import com.aqualink.community.CommunityRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WaterPoolingService {

    private final WaterOrderRepository orders;
    private final HouseholdRepository households;
    private final CommunityRepository communities;
    private final WaterProviderRepository providers;
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
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero");
        }

        Instant now = clock.instant();

        if (!windowStart.isAfter(now)
                || !windowEnd.isAfter(windowStart)) {
            throw new IllegalArgumentException(
                    "Invalid delivery window");
        }

        Household household = households.findByPublicId(
                householdPublicId.toString())
            .orElseThrow(() ->
                new IllegalArgumentException("Household not found"));

        // Add the household_users authorization check here before
        // allowing a requester to create orders for this household.

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

        // Implement a requester lookup from the authenticated principal.
        // Do not accept requesterId directly from an untrusted request.
        var requester = households.findUserReference(requesterId);

        WaterOrder order = WaterOrder.individual(
                household,
                requester,
                quantityL,
                windowStart,
                windowEnd,
                poolBy,
                idempotencyKey,
                notes
        );

        return orders.save(order);
    }

    @Transactional
    public int poolAtServicePoint(
            Long servicePointId,
            int maxPoolLitres,
            int minMembers) {

        if (maxPoolLitres <= 0 || minMembers < 1) {
            throw new IllegalArgumentException(
                    "Invalid pooling configuration");
        }

        // The first candidate provides the initial delivery window.
        var firstPage = orders.findSharedPointCandidates(
                servicePointId,
                OrderType.INDIVIDUAL,
                OrderStatus.PENDING,
                Instant.now(clock),
                Instant.parse("9999-12-31T00:00:00Z"),
                PageRequest.of(0, 1)
        );

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
                PageRequest.of(0, 100)
        );

        List<WaterOrder> selected = new ArrayList<>();
        int totalLitres = 0;

        for (WaterOrder candidate : candidates.getContent()) {
            if (selected.size() >= 100) {
                break;
            }

            if (candidate.getQuantityL() > maxPoolLitres - totalLitres) {
                continue;
            }

            selected.add(candidate);
            totalLitres += candidate.getQuantityL();
        }

        boolean deadlineReached = selected.stream()
                .anyMatch(o -> !o.getPoolBy().isAfter(clock.instant()));

        if (selected.size() < minMembers && !deadlineReached) {
            return 0;
        }

        if (selected.isEmpty()) {
            return 0;
        }

        WaterOrder pool = WaterOrder.newPool(selected.get(0));

        for (int i = 1; i < selected.size(); i++) {
            pool.addMember(selected.get(i));
        }

        // Important: the first selected request must also be added as a
        // member, rather than being treated as only the pool's anchor.
        // Use a dedicated helper when implementing the complete version.
        orders.save(pool);

        return selected.size();
    }
}