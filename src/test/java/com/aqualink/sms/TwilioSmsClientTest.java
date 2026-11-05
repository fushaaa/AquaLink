package com.aqualink.sms;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwilioSmsClientTest {

    private static final String AUTH_TOKEN = "test-auth-token";
    private static final String WEBHOOK_URL = "https://example.test/api/v1/sms/inbound";

    private final TwilioSmsClient client = new TwilioSmsClient(
            RestClient.builder(), "AC123", AUTH_TOKEN, "+15551234567");

    @Test
    void acceptsValidSignatureForFormParameters() throws Exception {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("Body", "HELP");
        form.add("From", "+15557654321");
        form.add("MessageSid", "SM123");

        assertTrue(client.verifySignature(WEBHOOK_URL, form, signature(WEBHOOK_URL, form)));
    }

    @Test
    void rejectsInvalidSignature() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("Body", "HELP");

        assertFalse(client.verifySignature(WEBHOOK_URL, form, "invalid"));
    }

    private String signature(String url, MultiValueMap<String, String> form) throws Exception {
        StringBuilder signedData = new StringBuilder(url);
        new TreeMap<>(form).forEach((key, values) -> values.forEach(value ->
                signedData.append(key).append(value)));

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(AUTH_TOKEN.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(
                mac.doFinal(signedData.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
