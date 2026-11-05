package com.aqualink.sms;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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

    @Test
    void rejectsMissingAuthTokenAndTamperedParameters() throws Exception {
        MultiValueMap<String, String> original = new LinkedMultiValueMap<>();
        original.add("Body", "HELP");
        String signature = signature(WEBHOOK_URL, original);

        assertFalse(client.verifySignature(WEBHOOK_URL, original, null));
        original.set("Body", "ORDER 500");
        assertFalse(client.verifySignature(WEBHOOK_URL, original, signature));
    }

    @Test
    void sendsAuthenticatedFormEncodedSmsWhenConfigured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.twilio.com/2010-04-01/Accounts/AC123/Messages.json"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                        "AC123:test-auth-token".getBytes(StandardCharsets.UTF_8))))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("To=%2B15551234567")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("From=%2B15551234567")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Body=Water+is+on+the+way")))
                .andRespond(withSuccess());
        TwilioSmsClient configured = new TwilioSmsClient(
                builder, "AC123", AUTH_TOKEN, "+15551234567");

        assertTrue(configured.isOutboundConfigured());
        configured.sendText("+15551234567", "Water is on the way");

        server.verify();
    }

    @Test
    void doesNotCallTwilioWhenOutboundCredentialsAreIncomplete() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TwilioSmsClient unconfigured = new TwilioSmsClient(
                builder, "", AUTH_TOKEN, "+15551234567");

        assertFalse(unconfigured.isOutboundConfigured());
        unconfigured.sendText("+15557654321", "Test");

        server.verify();
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
