package com.aqualink.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AquaLinkUserDetailsServiceTest {

    @Mock
    private UserRepository users;

    private AquaLinkUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new AquaLinkUserDetailsService(users);
    }

    @Test
    void returnsActiveUserCredentialsAndMappedRoles() {
        User user = user("ACTIVE", "$2a$hash");
        when(users.findByPhoneE164("+15555550123")).thenReturn(Optional.of(user));
        when(users.findRoleCodesByUserId(40L)).thenReturn(List.of("RESIDENT", "COMMUNITY_MANAGER"));

        var details = service.loadUserByUsername("+15555550123");

        assertThat(details.getUsername()).isEqualTo("+15555550123");
        assertThat(details.getPassword()).isEqualTo("$2a$hash");
        assertThat(details.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_RESIDENT", "ROLE_COMMUNITY_MANAGER");
    }

    @Test
    void rejectsUnknownUser() {
        when(users.findByPhoneE164("+15550000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("+15550000000"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Account is unavailable");
    }

    @Test
    void rejectsInactiveAndPasswordlessAccountsBeforeRoleLookup() {
                User inactiveUser = mock(User.class);
                when(inactiveUser.getStatus()).thenReturn("SUSPENDED");
        when(users.findByPhoneE164("+15555550123"))
                .thenReturn(Optional.of(inactiveUser));
        assertThatThrownBy(() -> service.loadUserByUsername("+15555550123"))
                .isInstanceOf(UsernameNotFoundException.class);

        User passwordlessUser = mock(User.class);
        when(passwordlessUser.getStatus()).thenReturn("ACTIVE");
        when(passwordlessUser.getPasswordHash()).thenReturn(" ");
        when(users.findByPhoneE164("+15555550123"))
                .thenReturn(Optional.of(passwordlessUser));
        assertThatThrownBy(() -> service.loadUserByUsername("+15555550123"))
                .isInstanceOf(UsernameNotFoundException.class);

        verify(users, never()).findRoleCodesByUserId(40L);
    }

    @Test
    void rejectsAccountsWithoutRoles() {
                User user = mock(User.class);
                when(user.getId()).thenReturn(40L);
                when(user.getStatus()).thenReturn("ACTIVE");
                when(user.getPasswordHash()).thenReturn("hash");
        when(users.findByPhoneE164("+15555550123"))
                .thenReturn(Optional.of(user));
        when(users.findRoleCodesByUserId(40L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.loadUserByUsername("+15555550123"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Account has no assigned role");
    }

        private User user(String status, String passwordHash) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(40L);
        when(user.getPhoneE164()).thenReturn("+15555550123");
        when(user.getStatus()).thenReturn(status);
        when(user.getPasswordHash()).thenReturn(passwordHash);
        return user;
    }
}