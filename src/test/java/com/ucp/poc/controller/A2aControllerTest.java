package com.ucp.poc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucp.poc.dto.a2a.A2aMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class A2aControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testA2aHealthEndpoint() throws Exception {
        mockMvc.perform(get("/a2a/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("healthy"))
            .andExpect(jsonPath("$.protocol").value("A2A"));
    }

    @Test
    void testA2aCapabilities() throws Exception {
        mockMvc.perform(get("/a2a/capabilities"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.protocol").value("A2A"))
            .andExpect(jsonPath("$.operations").exists());
    }

    @Test
    void testA2aProductSearch() throws Exception {
        // Create A2A message
        A2aMessage request = A2aMessage.builder()
            .messageId(UUID.randomUUID().toString())
            .type(A2aMessage.MessageType.REQUEST)
            .from("test-agent")
            .to("ucp-demo-store")
            .operation("catalog.products.list")
            .payload(Map.of())
            .metadata(A2aMessage.MessageMetadata.builder()
                .timestamp(Instant.now())
                .protocolVersion("2026-04-08")
                .build())
            .build();

        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/a2a/message")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("RESPONSE"))
            .andExpect(jsonPath("$.correlationId").value(request.getMessageId()))
            .andExpect(jsonPath("$.operation").value("catalog.products.list"))
            .andExpect(jsonPath("$.payload").exists());
    }

    @Test
    void testA2aInvalidOperation() throws Exception {
        A2aMessage request = A2aMessage.builder()
            .messageId(UUID.randomUUID().toString())
            .type(A2aMessage.MessageType.REQUEST)
            .from("test-agent")
            .to("ucp-demo-store")
            .operation("invalid.operation")
            .payload(Map.of())
            .build();

        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/a2a/message")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value("OPERATION_NOT_FOUND"));
    }
}
