package com.aqualink.user;

public record ResidentProfileResponse(String fullName, String phoneE164) {

    public static ResidentProfileResponse from(User user) {
        return new ResidentProfileResponse(user.getFullName(), user.getPhoneE164());
    }
}