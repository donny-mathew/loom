package com.loom.ai;

import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Model;
import com.loom.session.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuestioningService {

    private static final String SYSTEM_PROMPT = """
            You are a Socratic thinking partner helping the user explore and clarify a product or idea.
            Your role is to ask one focused, probing question at a time that pushes the user to examine
            assumptions, surface constraints, and identify what they actually believe.

            Rules:
            - Ask ONE question per response. Never list multiple questions.
            - Do not summarise or restate what the user said. Just ask the next question.
            - Prefer "why", "what would happen if", "how do you know", and "what are you trading off" framings.
            - Never give advice, opinions, or suggestions. Only ask questions.
            - When the conversation feels rich enough, you may say: "I think we have enough to extract findings."
            """;

    private final LoomAnthropicClient anthropicClient;

    public QuestioningService(LoomAnthropicClient anthropicClient) {
        this.anthropicClient = anthropicClient;
    }

    public String ask(List<ChatMessage> history) {
        MessageCreateParams params = buildParams(history);
        return anthropicClient.get().messages().create(params)
                .content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .findFirst()
                .orElse("");
    }

    public String askStreaming(List<ChatMessage> history, java.util.function.Consumer<String> onChunk) {
        MessageCreateParams params = buildParams(history);
        StringBuilder sb = new StringBuilder();
        try (var stream = anthropicClient.get().messages().createStreaming(params)) {
            stream.stream()
                    .flatMap(event -> event.contentBlockDelta().stream())
                    .flatMap(delta -> delta.delta().text().stream())
                    .forEach(text -> {
                        String chunk = text.text();
                        sb.append(chunk);
                        onChunk.accept(chunk);
                    });
        }
        return sb.toString();
    }

    private MessageCreateParams buildParams(List<ChatMessage> history) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(Model.CLAUDE_SONNET_4_6)
                .maxTokens(1024L)
                .system(SYSTEM_PROMPT);

        for (ChatMessage msg : history) {
            if ("user".equals(msg.role())) {
                builder.addUserMessage(msg.content());
            } else if ("assistant".equals(msg.role())) {
                builder.addAssistantMessage(msg.content());
            }
        }
        return builder.build();
    }
}
