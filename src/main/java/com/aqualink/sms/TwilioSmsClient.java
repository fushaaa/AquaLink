package com.aqualink.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

@Service
public class TwilioSmsClient {

    private static final Logger logger = LoggerFactory.getLogger(TwilioSmsClient.class);

    private final RestClient restClient;
    private final String accountSid;
    private final String authToken;
    private final String fromNumber;

    public TwilioSmsClient(
            RestClient.Builder restClientBuilder,
            @Value("${aqualink.sms.twilio.account-sid:}") String accountSid,
            @Value("${aqualink.sms.twilio.auth-token:}") String authToken,
            @Value("${aqualink.sms.twilio.from-number:}") String fromNumber) {
        this.restClient = restClientBuilder.build();
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
    }

    public boolean isOutboundConfigured() {
        return !accountSid.isBlank() && !authToken.isBlank() && !fromNumber.isBlank();
    }

    public boolean verifySignature(
            String url,
            MultiValueMap<String, String> form,
            String suppliedSignature) {
        if (authToken.isBlank() || suppliedSignature == null) {
            return false;
        }

        StringBuilder signedData = new StringBuilder(url);
        Map<String, java.util.List<String>> sorted = new TreeMap<>(form);
        sorted.forEach((key, values) -> values.forEach(value ->
                signedData.append(key).append(value)));

        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            String expected = Base64.getEncoder().encodeToString(
                    mac.doFinal(signedData.toString().getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    suppliedSignature.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException ex) {
            logger.error("Unable to verify Twilio webhook signature", ex);
            return false;
        }
    }

    public void sendText(String to, String message) {
        if (!isOutboundConfigured()) {
            return;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", to);
        form.add("From", fromNumber);
        form.add("Body", message);

        try {
            restClient.post()
                    .uri("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json")
                    .headers(headers -> headers.setBasicAuth(accountSid, authToken))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            logger.warn("SMS delivery failed for recipient ending {}",
                    to.length() > 4 ? to.substring(to.length() - 4) : "unknown");
        }
    }
}