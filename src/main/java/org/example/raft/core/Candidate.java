package org.example.raft.core;

import java.util.ArrayList;
import java.util.List;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Receive;
import org.example.raft.state.LogEntry;
import org.example.raft.state.StateMachine;
import org.example.raft.messages.RaftMessage;
import akka.actor.typed.javadsl.Behaviors;

public class Candidate extends Server {
    private int votesReceived;

    public static Behavior<RaftMessage> create(
            ActorContext<RaftMessage> context,
            int currentTerm,
            ActorRef<RaftMessage> votedFor,
            ArrayList<LogEntry> logEntries,
            int commitIndex,
            int lastApplied,
            StateMachine stateMachine,
            List<ActorRef<RaftMessage>> peers,
            String logFileName) {
        return new Candidate(context, currentTerm, votedFor, logEntries, commitIndex, lastApplied, stateMachine,
                peers, logFileName);
    }

    private Candidate(
            ActorContext<RaftMessage> context,
            int currentTerm,
            ActorRef<RaftMessage> votedFor,
            ArrayList<LogEntry> logEntries,
            int commitIndex,
            int lastApplied,
            StateMachine stateMachine,
            List<ActorRef<RaftMessage>> peers,
            String logFileName) {
        super(context, peers, logFileName);
        this.currentTerm = currentTerm;
        this.votedFor = votedFor;
        this.logEntries = logEntries;
        this.commitIndex = commitIndex;
        this.lastApplied = lastApplied;
        this.stateMachine = stateMachine;
        this.votesReceived = 1;
    }

    @Override
    public Receive<RaftMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(RaftMessage.AppendEntries.class, this::appendEntries)
                .onMessage(RaftMessage.RequestVoteResponse.class, this::handleRequestVoteResponse)
                .onMessage(RaftMessage.RequestVote.class, this::handleRequestVote)
                .onMessage(RaftMessage.SetCurrentTerm.class, this::setCurrentTerm)
                .onMessage(RaftMessage.GetState.class, this::handleGetState)
                .build();
    }

    private Behavior<RaftMessage> setCurrentTerm(RaftMessage.SetCurrentTerm msg) {
        currentTerm = msg.term();
        return this;
    }

    protected Behavior<RaftMessage> handleGetState(RaftMessage.GetState msg) {
        String value = stateMachine.getValue(msg.key());
        msg.replyTo().tell(new RaftMessage.GetStateResponse(value));
        return this;
    }

    private Behavior<RaftMessage> handleRequestVote(RaftMessage.RequestVote message) {
        if (message.candidateRef().equals(getContext().getSelf())) {
            message.candidateRef().tell(new RaftMessage.RequestVoteResponse(currentTerm, true));
        }
        return this;
    }

    private Behavior<RaftMessage> appendEntries(RaftMessage.AppendEntries message) {
        if (message.term() >= currentTerm) {
            return Follower.create(peers, logFileName);
        }
        return this;
    }

    private Behavior<RaftMessage> handleRequestVoteResponse(RaftMessage.RequestVoteResponse message) {
        getContext().getLog().info("Vote response: term={} granted={}", message.term(), message.voteGranted());

        if (message.term() > currentTerm) {
            getContext().getLog().info("Stepping down: higher term {} received", message.term());
            setCurrentTerm(new RaftMessage.SetCurrentTerm(message.term()));
            return Follower.create(peers, logFileName);
        }

        if (message.term() == currentTerm && message.voteGranted()) {
            votesReceived++;
            getContext().getLog().info("Vote received: total={}", votesReceived);

            int majority = (peers.size() + 1) / 2 + 1;
            if (votesReceived >= majority) {
                getContext().getLog().info("Majority reached: {} votes, becoming leader", votesReceived);
                return transitionToLeader();
            }
        }

        return this;
    }

    private Behavior<RaftMessage> transitionToLeader() {
        return Behaviors.withTimers(timers -> Leader.create(
                getContext(),
                currentTerm,
                votedFor,
                new ArrayList<>(logEntries),
                commitIndex,
                lastApplied,
                stateMachine,
                peers,
                logFileName));
    }
}