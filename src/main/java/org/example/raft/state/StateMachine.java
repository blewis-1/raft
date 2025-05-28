package org.example.raft.state;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class StateMachine {
    private final Map<String, String> state;

    public StateMachine() {
        this.state = new ConcurrentHashMap<>();
    }

    public String applyCommand(String command) {
        String[] parts = command.split("=");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid command format. Expected key=value");
        }
        String key = parts[0].trim();
        String value = parts[1].trim();
        state.put(key, value);
        return value;
    }

    public String getValue(String key) {
        return state.get(key);
    }

    public Map<String, String> getState() {
        return new ConcurrentHashMap<>(state);
    }
}