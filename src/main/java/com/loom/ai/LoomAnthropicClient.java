package com.loom.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Thin wrapper that constructs the Anthropic SDK client from ANTHROPIC_API_KEY env var.
 */
@Component
public class LoomAnthropicClient {

    private AnthropicClient client;

    @PostConstruct
    public void init() {
        client = AnthropicOkHttpClient.fromEnv();
    }

    public AnthropicClient get() {
        return client;
    }
}
