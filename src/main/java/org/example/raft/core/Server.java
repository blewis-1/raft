package org.example.raft.core;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Receive;
import org.example.raft.messages.RaftMessage;
import org.example.raft.state.LogEntry;
import org.example.raft.persistence.LogPersistence;
import org.example.raft.state.StateMachine;

import java.util.List;
import java.util.ArrayList;

public abstract class Server extends AbstractBehavior<RaftMessage> {
    protected final List<ActorRef<RaftMessage>> followers;
    protected final LogPersistence logPersistence;
    protected int currentTerm;
    protected int lastApplied;
    protected List<ActorRef<RaftMessage>> peers;
    protected ActorRef<RaftMessage> votedFor;
    protected String logFileName;
    protected List<LogEntry> logEntries;
    protected int commitIndex;
    protected StateMachine stateMachine;

    protected Server(ActorContext<RaftMessage> context, List<ActorRef<RaftMessage>> followers, String logFileName) {
        super(context);
        this.followers = followers;
        this.logPersistence = new LogPersistence(logFileName);
        this.currentTerm = 0;
        this.lastApplied = 0;
        this.peers = followers;
        this.logFileName = logFileName;
        this.logEntries = new ArrayList<>();
        this.commitIndex = 0;
        this.stateMachine = new StateMachine();
    }

    @Override
    public Receive<RaftMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(RaftMessage.GetLog.class, this::handleGetLog)
                .onMessage(RaftMessage.GetLastApplied.class, this::handleGetLastApplied)
                .onMessage(RaftMessage.GetState.class, this::handleGetState)
                .build();
    }

    protected Behavior<RaftMessage> handleGetLog(RaftMessage.GetLog msg) {
        msg.replyTo().tell(new RaftMessage.GetLogResponse(logPersistence.loadLog()));
        return this;
    }

    protected Behavior<RaftMessage> handleGetLastApplied(RaftMessage.GetLastApplied msg) {
        msg.replyTo().tell(new RaftMessage.LastAppliedResponse(lastApplied));
        return this;
    }

    protected Behavior<RaftMessage> handleGetState(RaftMessage.GetState msg) {
        msg.replyTo().tell(new RaftMessage.GetStateResponse(""));
        return this;
    }
}