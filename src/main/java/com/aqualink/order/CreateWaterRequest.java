package com.aqualink.order;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record CreateWaterRequest(
    @NotNull java.util.UUID householdId,
    @Min(1) @Max(50000) int quantityL,
    @NotNull @Future Instant windowStart,
    @NotNull @Future Instant windowEnd,
    @Size(max = 255) String notes
) {}