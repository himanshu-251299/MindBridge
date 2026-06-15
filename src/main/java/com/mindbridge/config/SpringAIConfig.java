package com.mindbridge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAIConfig {

    /**
     * Configures the Spring AI ChatClient bean.
     *
     * Spring AI auto-configures Google Gemini via the
     * spring-ai-starter-model-google-genai starter using
     * the API key in application.yml.
     *
     * Both CheckInAgent and PatternAgent inject this bean.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                // Default system context applied to all agents
                // (Individual agents override this with their own system prompts)
                .defaultSystem("""
                You are an AI assistant for MindBridge, a workplace wellness platform.
                Always respond professionally, empathetically, and concisely.
                Protect employee privacy at all times.
                """)
                .build();
    }
}