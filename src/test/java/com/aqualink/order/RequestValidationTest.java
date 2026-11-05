package com.aqualink.order;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private static Validator validator;
    private static jakarta.validation.ValidatorFactory validatorFactory;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void acceptsValidFutureWaterOrderRequest() {
        CreateWaterRequest request = new CreateWaterRequest(
                UUID.randomUUID(), 1, Instant.parse("2030-01-01T09:00:00Z"),
                Instant.parse("2030-01-01T11:00:00Z"), "Front entrance");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void enforcesWaterOrderQuantityLimits() {
        assertThat(properties(new CreateWaterRequest(UUID.randomUUID(), 0,
                Instant.parse("2030-01-01T09:00:00Z"),
                Instant.parse("2030-01-01T11:00:00Z"), null)))
                .contains("quantityL");
        assertThat(properties(new CreateWaterRequest(UUID.randomUUID(), 50001,
                Instant.parse("2030-01-01T09:00:00Z"),
                Instant.parse("2030-01-01T11:00:00Z"), null)))
                .contains("quantityL");
    }

    @Test
    void requiresHouseholdAndFutureWindowTimestamps() {
        CreateWaterRequest request = new CreateWaterRequest(
                null, 100, null, Instant.parse("2020-01-01T11:00:00Z"), null);

        assertThat(properties(request))
                .contains("householdId", "windowStart", "windowEnd");
    }

    @Test
    void limitsOptionalOrderNotes() {
        CreateWaterRequest request = new CreateWaterRequest(
                UUID.randomUUID(), 100,
                Instant.parse("2030-01-01T09:00:00Z"),
                Instant.parse("2030-01-01T11:00:00Z"), "n".repeat(256));

        assertThat(properties(request)).contains("notes");
    }

    @Test
    void enforcesPoolingRunConfigurationRanges() {
        assertThat(validator.validate(new PoolingRunRequest(1, 1, 1))).isEmpty();
        assertThat(validator.validate(new PoolingRunRequest(50000, 50, 200))).isEmpty();
        assertThat(properties(new PoolingRunRequest(0, 0, 201)))
                .contains("maxPoolLitres", "minMembers", "maxPools");
    }

    private Set<String> properties(Object value) {
        return validator.validate(value).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}