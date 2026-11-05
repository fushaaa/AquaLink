package com.aqualink.household;

public record HouseholdSummaryResponse(
        String publicId,
        String displayName,
        boolean vulnerable) {

    public static HouseholdSummaryResponse from(Household household) {
        return new HouseholdSummaryResponse(
                household.getPublicId(), household.getDisplayName(), household.isVulnerable());
    }
}