package org.example.raft.network;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import org.example.raft.messages.RaftMessage;
import org.example.raft.messages.OrchMessage;

import java.util.ArrayList;
import java.util.List;

import org.example.raft.core.Follower;

public class Orchestrator extends AbstractBehavior<OrchMessage> {
    private final List<ActorRef<RaftMessage>> servers;

    public static Behavior<OrchMessage> create(int serverCount) {
        return Behaviors.setup(context -> new Orchestrator(context, serverCount));
    }

    private Orchestrator(ActorContext<OrchMessage> context, int serverCount) {
        super(context);
        this.servers = new ArrayList<>();
    }

    @Override
    public Receive<OrchMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(OrchMessage.Start.class, this::handleStart)
                .onMessage(OrchMessage.Stop.class, this::handleStop)
                .onMessage(OrchMessage.ClientCommand.class, this::handleClientCommand)
                .build();
    }

    private Behavior<OrchMessage> handleStart(OrchMessage.Start msg) {
        getContext().getLog().info("Start: {} servers", msg.numberOfProcesses());
        createServers(msg.numberOfProcesses());
        setupServerCommunication();
        return this;
    }

    private Behavior<OrchMessage> handleStop(OrchMessage.Stop msg) {
        getContext().getLog().info("Stop: cluster");
        for (ActorRef<RaftMessage> server : servers) {
            getContext().stop(server);
        }
        return Behaviors.stopped();
    }

    private Behavior<OrchMessage> handleClientCommand(OrchMessage.ClientCommand msg) {
        String handlerName = "response-handler-" + System.currentTimeMillis();
        ActorRef<RaftMessage> responseHandler = getContext().spawn(ClientResponseHandler.create(getContext().getSelf()),
                handlerName);

        for (ActorRef<RaftMessage> server : servers) {
            server.tell(new RaftMessage.ClientCommand(msg.command(), responseHandler));
        }
        return this;
    }

    private void createServers(int serverCount) {
        getContext().getLog().info("Create: {} followers", serverCount);
        for (int i = 0; i < serverCount; i++) {
            String logFileName = "server-" + i + ".log";
            ActorRef<RaftMessage> server = getContext().spawn(Follower.create(new ArrayList<>(), logFileName),
                    "server-" + i);
            servers.add(server);
            server.tell(new RaftMessage.SetCurrentTerm(0));
            getContext().getLog().info("Created: server-{} log={}", i, logFileName);
        }
        getContext().getLog().info("Created: {} followers", serverCount);
    }

    private void setupServerCommunication() {
        getContext().getLog().info("Setup: communication");
        for (ActorRef<RaftMessage> server : servers) {
            List<ActorRef<RaftMessage>> otherServers = new ArrayList<>(servers);
            otherServers.remove(server);
            getContext().getLog().info("Add: {} followers to {}", otherServers.size(), server.path().name());
            for (ActorRef<RaftMessage> follower : otherServers) {
                server.tell(new RaftMessage.AddFollower(follower));
            }
        }
        getContext().getLog().info("Setup: complete");
    }
}