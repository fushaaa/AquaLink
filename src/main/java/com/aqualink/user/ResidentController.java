package com.aqualink.user;

import com.aqualink.household.HouseholdRepository;
import com.aqualink.household.HouseholdSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ResidentController {

    private final UserRepository users;
    private final HouseholdRepository households;

    @GetMapping("/me")
    public ResidentProfileResponse profile(@AuthenticationPrincipal UserDetails principal) {
        return ResidentProfileResponse.from(requireUser(principal));
    }

    @GetMapping("/households")
    public List<HouseholdSummaryResponse> households(
            @AuthenticationPrincipal UserDetails principal) {
        User user = requireUser(principal);
        return households.findAllByMemberUserId(user.getId()).stream()
                .map(HouseholdSummaryResponse::from)
                .toList();
    }

    private User requireUser(UserDetails principal) {
        return users.findByPhoneE164(principal.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}