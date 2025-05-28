package org.example.raft.state;

import java.io.Serializable;

public record LogEntry(int term, Command command) implements Serializable {
}
