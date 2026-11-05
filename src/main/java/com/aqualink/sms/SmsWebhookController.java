package com.aqualink.sms;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/sms")
@RequiredArgsConstructor
public class SmsWebhookController {

    private final TwilioSmsClient smsClient;
    private final SmsAssistantService assistant;

    @Value("${aqualink.sms.twilio.webhook-url:}")
    private String configuredWebhookUrl;

    @PostMapping(value = "/inbound", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public String receiveMessage(
            @RequestParam MultiValueMap<String, String> form,
            HttpServletRequest request) {
        String authToken = request.getHeader("X-Twilio-Signature");
        String webhookUrl = configuredWebhookUrl.isBlank()
                ? request.getRequestURL().toString() + querySuffix(request)
                : configuredWebhookUrl;

        if (!smsClient.verifySignature(webhookUrl, form, authToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid SMS provider signature");
        }

        String from = form.getFirst("From");
        String body = form.getFirst("Body");
        String messageSid = form.getFirst("MessageSid");
        String reply = assistant.handleMessage(from, body, messageSid);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Message>"
                + escapeXml(reply) + "</Message></Response>";
    }

    private String querySuffix(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? "" : "?" + query;
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}