package com.loom.web;

import com.loom.ai.FindingExtractor;
import com.loom.ai.FindingProposal;
import com.loom.ai.QuestioningService;
import com.loom.savegate.SaveGateService;
import com.loom.session.ChatMessage;
import com.loom.session.SessionService;
import com.loom.linking.CrossSessionLinker;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Thymeleaf-rendering controller for the chat UI.
 * Uses the non-streaming ask() path so HTMX gets a clean HTML fragment back.
 */
@Controller
@RequestMapping("/chat")
public class ChatPageController {

    private final SessionService sessionService;
    private final QuestioningService questioningService;
    private final FindingExtractor findingExtractor;
    private final SaveGateService saveGateService;
    private final CrossSessionLinker crossSessionLinker;

    public ChatPageController(SessionService sessionService,
                               QuestioningService questioningService,
                               FindingExtractor findingExtractor,
                               SaveGateService saveGateService,
                               CrossSessionLinker crossSessionLinker) {
        this.sessionService = sessionService;
        this.questioningService = questioningService;
        this.findingExtractor = findingExtractor;
        this.saveGateService = saveGateService;
        this.crossSessionLinker = crossSessionLinker;
    }

    @PostMapping("/session")
    public String newSession(@RequestParam String topic, Model model) {
        String sessionId = sessionService.createSession();
        model.addAttribute("sessionId", sessionId);
        model.addAttribute("topic", topic);
        model.addAttribute("messages", List.of());
        return "fragments/chat :: conversation";
    }

    @PostMapping("/message")
    public String sendMessage(@RequestParam String sessionId,
                              @RequestParam String message,
                              Model model) {
        sessionService.addMessage(sessionId, ChatMessage.user(message));
        String reply = questioningService.ask(sessionService.getHistoryReadOnly(sessionId));
        sessionService.addMessage(sessionId, ChatMessage.assistant(reply));

        model.addAttribute("userMessage", message);
        model.addAttribute("assistantMessage", reply);
        return "fragments/message :: pair";
    }

    @PostMapping("/extract")
    public String extractFindings(@RequestParam String sessionId, Model model) {
        List<FindingProposal> proposals = findingExtractor.extract(
                sessionService.getHistoryReadOnly(sessionId));
        model.addAttribute("proposals", proposals);
        model.addAttribute("sessionId", sessionId);
        return "fragments/findings :: panel";
    }

    @PostMapping("/save")
    public String save(@RequestParam String sessionId,
                       @RequestParam String topic,
                       @RequestParam(required = false, defaultValue = "") List<String> types,
                       @RequestParam(required = false, defaultValue = "") List<String> titles,
                       @RequestParam(required = false, defaultValue = "") List<String> bodies,
                       Model model) {
        List<FindingProposal> curated = buildProposals(types, titles, bodies);
        if (!curated.isEmpty()) {
            saveGateService.save(sessionId, topic, curated);
        }
        sessionService.closeSession(sessionId);
        crossSessionLinker.runForSession(sessionId);
        model.addAttribute("count", curated.size());
        model.addAttribute("topic", topic);
        return "fragments/findings :: saved";
    }

    private List<FindingProposal> buildProposals(List<String> types, List<String> titles, List<String> bodies) {
        if (types.isEmpty()) return List.of();
        int size = Math.min(types.size(), Math.min(titles.size(), bodies.size()));
        List<FindingProposal> list = new java.util.ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(new FindingProposal(types.get(i), titles.get(i), bodies.get(i)));
        }
        return list;
    }
}
