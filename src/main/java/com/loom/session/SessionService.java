package com.loom.session;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

    private final Map<String, List<ChatMessage>> sessions = new ConcurrentHashMap<>();

    public String createSession() {
        String id = UUID.randomUUID().toString();
        sessions.put(id, new ArrayList<>());
        return id;
    }

    public void addMessage(String sessionId, ChatMessage message) {
        List<ChatMessage> history = getHistory(sessionId);
        history.add(message);
    }

    public List<ChatMessage> getHistory(String sessionId) {
        List<ChatMessage> history = sessions.get(sessionId);
        if (history == null) {
            throw new IllegalArgumentException("Unknown session: " + sessionId);
        }
        return history;
    }

    public List<ChatMessage> getHistoryReadOnly(String sessionId) {
        return Collections.unmodifiableList(getHistory(sessionId));
    }

    public void closeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public boolean exists(String sessionId) {
        return sessions.containsKey(sessionId);
    }
}
