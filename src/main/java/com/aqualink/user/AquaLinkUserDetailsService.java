package com.aqualink.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AquaLinkUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    @Override
    public UserDetails loadUserByUsername(String phoneE164) {
        User user = users.findByPhoneE164(phoneE164)
                .filter(account -> "ACTIVE".equals(account.getStatus()))
                .filter(account -> account.getPasswordHash() != null
                        && !account.getPasswordHash().isBlank())
                .orElseThrow(() -> new UsernameNotFoundException("Account is unavailable"));

        var authorities = users.findRoleCodesByUserId(user.getId()).stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        if (authorities.isEmpty()) {
            throw new UsernameNotFoundException("Account has no assigned role");
        }

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getPhoneE164())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .build();
    }
}