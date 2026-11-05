package com.aqualink.config;

import com.aqualink.household.HouseholdRepository;
import com.aqualink.order.WaterOrderController;
import com.aqualink.order.WaterOrderRepository;
import com.aqualink.order.WaterPoolingService;
import com.aqualink.user.AquaLinkUserDetailsService;
import com.aqualink.user.ResidentController;
import com.aqualink.user.User;
import com.aqualink.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ResidentController.class, WaterOrderController.class})
@Import(SecurityConfig.class)
@ImportAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
class SecurityHttpTest {

    private static final String PHONE = "+15555550123";
    private static final String PASSWORD = "test-password";

    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private AquaLinkUserDetailsService userDetailsService;
    @MockitoBean private UserRepository users;
    @MockitoBean private HouseholdRepository households;
    @MockitoBean private WaterOrderRepository orders;
    @MockitoBean private WaterPoolingService poolingService;

    @BeforeEach
    void setUp() {
        when(userDetailsService.loadUserByUsername(PHONE)).thenReturn(details("ROLE_RESIDENT"));
    }

    @Test
    void protectsResidentApiWhenNoCredentialsAreProvided() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());

        verify(users, never()).findByPhoneE164(PHONE);
    }

    @Test
    void authenticatesResidentAndReturnsProfile() throws Exception {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getFullName()).thenReturn("Resident Test");
        when(user.getPhoneE164()).thenReturn(PHONE);
        when(users.findByPhoneE164(PHONE)).thenReturn(Optional.of(user));

        mvc.perform(get("/api/v1/me").with(httpBasic(PHONE, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Resident Test"))
                .andExpect(jsonPath("$.phoneE164").value(PHONE));
    }

    @Test
    void residentRoleCannotRunManagerPoolingEndpoint() throws Exception {
        mvc.perform(post("/api/v1/service-points/20/pooling-runs")
                        .with(httpBasic(PHONE, PASSWORD))
                        .contentType("application/json")
                        .content("{\"maxPoolLitres\":1000,\"minMembers\":2,\"maxPools\":1}"))
                .andExpect(status().isForbidden());

        verify(poolingService, never()).poolAtServicePoint(20L, 1000, 2);
    }

    private UserDetails details(String authority) {
        return org.springframework.security.core.userdetails.User.withUsername(PHONE)
                .password(passwordEncoder.encode(PASSWORD))
                .authorities(authority)
                .build();
    }
}