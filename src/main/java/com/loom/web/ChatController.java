package com.loom.web;

import com.loom.ai.FindingExtractor;
import com.loom.ai.FindingProposal;
import com.loom.ai.QuestioningService;
import com.loom.savegate.SaveGateService;
import com.loom.session.ChatMessage;
import com.loom.session.SessionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final SessionService sessionService;
    private final QuestioningService questioningService;
    private final FindingExtractor findingExtractor;
    private final SaveGateService saveGateService;

    public ChatController(SessionService sessionService,
                          QuestioningService questioningService,
                          FindingExtractor findingExtractor,
                          SaveGateService saveGateService) {
        this.sessionService = sessionService;
        this.questioningService = questioningService;
        this.findingExtractor = findingExtractor;
        this.saveGateService = saveGateService;
    }

    @PostMapping("/session")
    public ResponseEntity<Map<String, String>> createSession() {
        String sessionId = sessionService.createSession();
        return ResponseEntity.ok(Map.of("sessionId", sessionId));
    }

    @PostMapping("/message")
    public ResponseEntity<StreamingResponseBody> message(
            @RequestParam String sessionId,
            @RequestBody Map<String, String> body) {

        String userText = body.get("message");
        sessionService.addMessage(sessionId, ChatMessage.user(userText));

        StreamingResponseBody stream = outputStream -> {
            StringBuilder reply = new StringBuilder();
            questioningService.askStreaming(
                    sessionService.getHistoryReadOnly(sessionId),
                    chunk -> {
                        try {
                            outputStream.write(chunk.getBytes());
                            outputStream.flush();
                            reply.append(chunk);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            sessionService.addMessage(sessionId, ChatMessage.assistant(reply.toString()));
        };

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(stream);
    }

    @PostMapping("/extract-findings")
    public ResponseEntity<List<FindingProposal>> extractFindings(@RequestParam String sessionId) {
        List<ChatMessage> history = sessionService.getHistoryReadOnly(sessionId);
        List<FindingProposal> proposals = findingExtractor.extract(history);
        return ResponseEntity.ok(proposals);
    }

    @PostMapping("/save")
    public ResponseEntity<Map<String, Integer>> save(
            @RequestParam String sessionId,
            @RequestParam String topic,
            @RequestBody List<FindingProposal> curated) {
        saveGateService.save(sessionId, topic, curated);
        return ResponseEntity.ok(Map.of("saved", curated.size()));
    }

    @DeleteMapping("/session")
    public ResponseEntity<Void> closeSession(@RequestParam String sessionId) {
        sessionService.closeSession(sessionId);
        return ResponseEntity.noContent().build();
    }
}
