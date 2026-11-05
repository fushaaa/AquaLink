package com.aqualink.sms;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiSmsAssistantTest {

    @Test
    void missingApiKeyUsesBuiltInInstructionsWithoutCallingProvider() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiSmsAssistant assistant = new AiSmsAssistant(builder, "", "https://ai.test/v1", "test-model");

        String reply = assistant.answer("help");

        assertThat(reply).contains("To order:", "STATUS");
        server.verify();
    }

    @Test
    void sendsPromptAndReturnsProviderAnswer() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ai.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"Use ORDER 1000 <start> <end>."}}]}
                        """, MediaType.APPLICATION_JSON));
        AiSmsAssistant assistant = new AiSmsAssistant(
                builder, "test-key", "https://ai.test/v1/", "test-model");

        assertThat(assistant.answer("How do I order?"))
                .isEqualTo("Use ORDER 1000 <start> <end>.");
        server.verify();
    }

    @Test
    void limitsLongProviderReplyToOneThousandCharacters() {
        String content = "x".repeat(1100);
        String response = "{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}";
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ai.test/v1/chat/completions"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        AiSmsAssistant assistant = new AiSmsAssistant(
                builder, "test-key", "https://ai.test/v1", "test-model");

        assertThat(assistant.answer("question")).hasSize(1000);
        server.verify();
    }

    @Test
    void fallsBackWhenProviderReturnsAnErrorOrNoAnswer() {
        RestClient.Builder failingBuilder = RestClient.builder();
        MockRestServiceServer failingServer = MockRestServiceServer.bindTo(failingBuilder).build();
        failingServer.expect(requestTo("https://ai.test/v1/chat/completions"))
                .andRespond(withServerError());
        AiSmsAssistant failingAssistant = new AiSmsAssistant(
                failingBuilder, "test-key", "https://ai.test/v1", "test-model");
        assertThat(failingAssistant.answer("question")).contains("To order:");
        failingServer.verify();

        RestClient.Builder emptyBuilder = RestClient.builder();
        MockRestServiceServer emptyServer = MockRestServiceServer.bindTo(emptyBuilder).build();
        emptyServer.expect(requestTo("https://ai.test/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));
        AiSmsAssistant emptyAssistant = new AiSmsAssistant(
                emptyBuilder, "test-key", "https://ai.test/v1", "test-model");
        assertThat(emptyAssistant.answer("question")).contains("To order:");
        emptyServer.verify();
    }
}