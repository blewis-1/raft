package org.example.raft.messages;

import akka.actor.typed.ActorRef;

public sealed interface ClientMessage {
    record Command(String command, ActorRef<ClientMessage> replyTo) implements ClientMessage {
    }

    record Response(boolean success, String value) implements ClientMessage {
    }
}