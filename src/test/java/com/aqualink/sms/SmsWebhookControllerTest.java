package com.aqualink.sms;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmsWebhookControllerTest {

    private TwilioSmsClient smsClient;
    private SmsAssistantService assistant;
    private SmsWebhookController controller;

    @BeforeEach
    void setUp() {
        smsClient = mock(TwilioSmsClient.class);
        assistant = mock(SmsAssistantService.class);
        controller = new SmsWebhookController(smsClient, assistant);
        ReflectionTestUtils.setField(controller, "configuredWebhookUrl", "");
    }

    @AfterEach
    void clearRequestState() {
        org.springframework.test.util.ReflectionTestUtils.setField(
                controller, "configuredWebhookUrl", "");
    }

    @Test
    void verifiesRequestUrlAndEscapesAssistantReplyInTwiml() {
        MultiValueMap<String, String> form = form();
        HttpServletRequest request = request("sid=1");
        when(smsClient.verifySignature(
                "https://sms.example.test/api/v1/sms/inbound?sid=1", form, "valid-signature"))
                .thenReturn(true);
        when(assistant.handleMessage("+15557654321", "HELP", "SM123"))
                .thenReturn("Tea & <water> \"safe\" 'today'");

        String response = controller.receiveMessage(form, request);

        assertThat(response).contains("<Response><Message>");
        assertThat(response).contains("Tea &amp; &lt;water&gt; &quot;safe&quot; &apos;today&apos;");
        verify(assistant).handleMessage("+15557654321", "HELP", "SM123");
    }

    @Test
    void usesConfiguredPublicUrlWhenPresent() {
        ReflectionTestUtils.setField(controller, "configuredWebhookUrl",
                "https://public.example.test/sms-hook");
        MultiValueMap<String, String> form = form();
        HttpServletRequest request = request(null);
        when(smsClient.verifySignature(
                "https://public.example.test/sms-hook", form, "valid-signature"))
                .thenReturn(true);
        when(assistant.handleMessage("+15557654321", "HELP", "SM123")).thenReturn("ok");

        controller.receiveMessage(form, request);

        verify(smsClient).verifySignature(
                "https://public.example.test/sms-hook", form, "valid-signature");
    }

    @Test
    void rejectsInvalidSignatureWithoutCallingAssistant() {
        MultiValueMap<String, String> form = form();
        when(smsClient.verifySignature(
                "https://sms.example.test/api/v1/sms/inbound", form, "invalid"))
                .thenReturn(false);

        assertThatThrownBy(() -> controller.receiveMessage(form, request(null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
        verify(assistant, never()).handleMessage("+15557654321", "HELP", "SM123");
    }

    private MultiValueMap<String, String> form() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", "+15557654321");
        form.add("Body", "HELP");
        form.add("MessageSid", "SM123");
        return form;
    }

    private MockHttpServletRequest request(String query) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/sms/inbound");
        request.setScheme("https");
        request.setServerName("sms.example.test");
        request.setServerPort(443);
        request.setRequestURI("/api/v1/sms/inbound");
        request.setQueryString(query);
        request.addHeader("X-Twilio-Signature", "valid-signature");
        return request;
    }
}