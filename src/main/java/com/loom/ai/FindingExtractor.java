package com.loom.ai;

import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loom.session.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FindingExtractor {

    private static final String SYSTEM_PROMPT = """
            You are a knowledge extraction assistant. Given a conversation between a user and a
            Socratic thinking partner, extract the key findings as a JSON array.

            Each finding must have:
            - "type": one of insight | pattern | constraint | tension | question | artifact
            - "title": short noun phrase (max 8 words)
            - "body": 1–3 sentence summary of the finding

            Output ONLY a valid JSON array — no markdown fences, no commentary, no preamble.
            Example output:
            [
              {"type": "insight", "title": "Users prefer flat pricing", "body": "The user repeatedly returned to the idea that tiered pricing creates friction for small teams."},
              {"type": "tension", "title": "Simplicity vs. enterprise needs", "body": "The user wants a simple product but also wants to win enterprise deals, which require configurability."}
            ]
            """;

    private final LoomAnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;

    public FindingExtractor(LoomAnthropicClient anthropicClient, ObjectMapper objectMapper) {
        this.anthropicClient = anthropicClient;
        this.objectMapper = objectMapper;
    }

    public List<FindingProposal> extract(List<ChatMessage> history) {
        String conversationText = formatConversation(history);

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_SONNET_4_6)
                .maxTokens(2048L)
                .system(SYSTEM_PROMPT)
                .addUserMessage("Extract findings from this conversation:\n\n" + conversationText)
                .build();

        String json = anthropicClient.get().messages().create(params)
                .content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .findFirst()
                .orElse("[]");

        try {
            return objectMapper.readValue(json.strip(), new TypeReference<List<FindingProposal>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse findings JSON: " + json, e);
        }
    }

    private String formatConversation(List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : history) {
            sb.append(msg.role().toUpperCase()).append(": ").append(msg.content()).append("\n\n");
        }
        return sb.toString().strip();
    }
}
