package org.example.raft.state;

import java.io.Serializable;

public record Command(String value) implements Serializable {
    public Command {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Command value cannot be null or empty");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
