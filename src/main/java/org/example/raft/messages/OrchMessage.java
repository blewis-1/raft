package org.example.raft.messages;

import akka.actor.typed.ActorRef;

public sealed interface OrchMessage {
    record Start(int numberOfProcesses) implements OrchMessage {
    }

    record Stop() implements OrchMessage {
    }

    record AddServer(ActorRef<RaftMessage> server) implements OrchMessage {
    }

    record ClientCommand(String command) implements OrchMessage {
    }
}