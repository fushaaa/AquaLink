package com.aqualink.user;

import com.aqualink.household.Household;
import com.aqualink.household.HouseholdRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResidentControllerTest {

    @Mock private UserRepository users;
    @Mock private HouseholdRepository households;

    private ResidentController controller;
    private UserDetails principal;
    private User user;

    @BeforeEach
    void setUp() {
        controller = new ResidentController(users, households);
        principal = mock(UserDetails.class);
        user = mock(User.class);
        when(principal.getUsername()).thenReturn("+15555550123");
    }

    @Test
    void returnsAuthenticatedResidentProfile() {
        when(user.getFullName()).thenReturn("Demo Resident");
        when(user.getPhoneE164()).thenReturn("+15555550123");
        when(users.findByPhoneE164("+15555550123")).thenReturn(Optional.of(user));

        ResidentProfileResponse response = controller.profile(principal);

        assertThat(response).isEqualTo(new ResidentProfileResponse("Demo Resident", "+15555550123"));
    }

    @Test
    void returnsOnlyHouseholdsLinkedToAuthenticatedUser() {
        when(user.getId()).thenReturn(40L);
        when(users.findByPhoneE164("+15555550123")).thenReturn(Optional.of(user));
        Household household = mock(Household.class);
        when(household.getPublicId()).thenReturn("household-1");
        when(household.getDisplayName()).thenReturn("Primary home");
        when(household.isVulnerable()).thenReturn(true);
        when(households.findAllByMemberUserId(40L)).thenReturn(List.of(household));

        assertThat(controller.households(principal))
                .containsExactly(new com.aqualink.household.HouseholdSummaryResponse(
                        "household-1", "Primary home", true));
        verify(households).findAllByMemberUserId(40L);
    }

    @Test
    void rejectsUnknownPrincipalBeforeHouseholdLookup() {
        when(users.findByPhoneE164("+15555550123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.households(principal))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");
        verify(households, never()).findAllByMemberUserId(40L);
    }
}