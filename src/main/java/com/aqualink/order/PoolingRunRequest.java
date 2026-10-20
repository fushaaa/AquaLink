package com.aqualink.order;

import jakarta.validation.constraints.*;

public record PoolingRunRequest(
    @Min(1) @Max(50000) int maxPoolLitres,
    @Min(1) @Max(50) int minMembers,
    @Min(1) @Max(200) int maxPools
) {}