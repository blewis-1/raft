package org.example.raft.network;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import org.example.raft.messages.RaftMessage;
import org.example.raft.messages.OrchMessage;

public class Client extends AbstractBehavior<RaftMessage> {
    private final ActorRef<OrchMessage> orchestrator;

    public static Behavior<RaftMessage> create(ActorRef<OrchMessage> orchestrator) {
        return Behaviors.setup(context -> new Client(context, orchestrator));
    }

    private Client(ActorContext<RaftMessage> context, ActorRef<OrchMessage> orchestrator) {
        super(context);
        this.orchestrator = orchestrator;
    }

    @Override
    public Receive<RaftMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(RaftMessage.ClientCommand.class, this::handleCommand)
                .build();
    }

    private Behavior<RaftMessage> handleCommand(RaftMessage.ClientCommand msg) {
        getContext().getLog().info("Sending command: {}", msg.command());
        orchestrator.tell(new OrchMessage.ClientCommand(msg.command()));
        return this;
    }
}