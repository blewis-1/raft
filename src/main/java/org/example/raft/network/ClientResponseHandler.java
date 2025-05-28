package org.example.raft.network;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import org.example.raft.messages.RaftMessage;
import org.example.raft.messages.OrchMessage;

public class ClientResponseHandler extends AbstractBehavior<RaftMessage> {
    private int responseCount = 0;
    private final int expectedResponses;

    public static Behavior<RaftMessage> create(ActorRef<OrchMessage> orchestrator) {
        return Behaviors.setup(context -> new ClientResponseHandler(context, orchestrator));
    }

    private ClientResponseHandler(ActorContext<RaftMessage> context, ActorRef<OrchMessage> orchestrator) {
        super(context);
        this.expectedResponses = 5;
    }

    @Override
    public Receive<RaftMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(RaftMessage.ClientResponse.class, this::handleResponse)
                .build();
    }

    private Behavior<RaftMessage> handleResponse(RaftMessage.ClientResponse response) {
        responseCount++;
        getContext().getLog().info("Response received: {} ({}/{})", response.success(), responseCount,
                expectedResponses);

        if (responseCount >= expectedResponses) {
            getContext().getLog().info("All responses received, stopping handler");
            return Behaviors.stopped();
        }
        return this;
    }
}