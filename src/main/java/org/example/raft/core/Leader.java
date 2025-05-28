package org.example.raft.core;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.TimerScheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.example.raft.state.LogEntry;
import org.example.raft.state.StateMachine;
import org.example.raft.messages.RaftMessage;
import org.example.raft.persistence.LogPersistence;
import org.example.raft.state.Command;

public class Leader extends Server {
    private static final String HEARTBEAT_KEY = "heartbeat";
    private static final int HEARTBEAT_INTERVAL = 150;
    private final TimerScheduler<RaftMessage> timers;
    private List<LogEntry> logEntries;
    private int commitIndex;
    private int lastApplied;
    private Map<ActorRef<RaftMessage>, Integer> nextIndex;
    private Map<ActorRef<RaftMessage>, Integer> matchIndex;
    private LogPersistence logPersistence;
    private Map<Integer, ActorRef<RaftMessage>> pendingCommands; // logIndex -> client

    public static Behavior<RaftMessage> create(
            ActorContext<RaftMessage> context,
            int currentTerm,
            ActorRef<RaftMessage> votedFor,
            List<LogEntry> logEntries,
            int commitIndex,
            int lastApplied,
            StateMachine stateMachine,
            List<ActorRef<RaftMessage>> peers,
            String logFileName) {
        return Behaviors.setup(ctx -> Behaviors.withTimers(timers -> new Leader(ctx, timers, currentTerm, votedFor,
                logEntries, commitIndex, lastApplied, stateMachine, peers, logFileName)));
    }

    private Leader(
            ActorContext<RaftMessage> context,
            TimerScheduler<RaftMessage> timers,
            int currentTerm,
            ActorRef<RaftMessage> votedFor,
            List<LogEntry> logEntries,
            int commitIndex,
            int lastApplied,
            StateMachine stateMachine,
            List<ActorRef<RaftMessage>> peers,
            String logFileName) {
        super(context, peers, logFileName);
        this.timers = timers;
        this.currentTerm = currentTerm;
        this.votedFor = votedFor;
        this.logEntries = new ArrayList<>(logEntries);
        this.commitIndex = commitIndex;
        this.lastApplied = lastApplied;
        this.stateMachine = stateMachine;
        this.logPersistence = new LogPersistence(logFileName);
        this.pendingCommands = new ConcurrentHashMap<>();

        initializeLeaderState();
        startHeartbeatTimer();
        getContext().getLog().info("Started as leader for term {}", currentTerm);
    }

    private void initializeLeaderState() {
        nextIndex = new HashMap<>();
        matchIndex = new HashMap<>();
        initializePeerIndices();
    }

    private void initializePeerIndices() {
        int lastLogIndex = logEntries.size() - 1;
        for (ActorRef<RaftMessage> peer : peers) {
            nextIndex.put(peer, lastLogIndex + 1);
            matchIndex.put(peer, -1);
        }
    }

    private void startHeartbeatTimer() {
        timers.startTimerAtFixedRate(
                HEARTBEAT_KEY,
                new RaftMessage.Heartbeat(),
                Duration.ofMillis(HEARTBEAT_INTERVAL));
    }

    @Override
    public Receive<RaftMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(RaftMessage.AppendEntriesResponse.class, this::handleAppendEntriesResponse)
                .onMessage(RaftMessage.RequestVote.class, this::handleRequestVote)
                .onMessage(RaftMessage.RequestVoteResponse.class, this::handleRequestVoteResponse)
                .onMessage(RaftMessage.ClientCommand.class, this::handleClientCommand)
                .onMessage(RaftMessage.AddFollower.class, this::handleAddFollower)
                .onMessage(RaftMessage.Heartbeat.class, this::handleHeartbeat)
                .onSignal(akka.actor.typed.Terminated.class, this::handleTerminated)
                .build();
    }

    private Behavior<RaftMessage> handleTerminated(akka.actor.typed.Terminated terminated) {
        String terminatedPath = terminated.getRef().path().toString();
        getContext().getLog().info("Actor terminated: {}", terminatedPath);

        peers.removeIf(peer -> peer.path().toString().equals(terminatedPath));
        nextIndex.entrySet().removeIf(entry -> entry.getKey().path().toString().equals(terminatedPath));
        matchIndex.entrySet().removeIf(entry -> entry.getKey().path().toString().equals(terminatedPath));

        int majority = (peers.size() + 1) / 2 + 1;

        if (peers.size() + 1 < majority) {
            getContext().getLog().info("Lost majority, stepping down");
            return Follower.create(peers, logFileName);
        }

        return this;
    }

    private void sendHeartbeats() {
        List<ActorRef<RaftMessage>> failedPeers = new ArrayList<>();
        for (ActorRef<RaftMessage> peer : peers) {
            try {
                int prevLogIndex = nextIndex.get(peer) - 1;
                int prevLogTerm = prevLogIndex >= 0 && prevLogIndex < logEntries.size()
                        ? logEntries.get(prevLogIndex).term()
                        : 0;
                List<LogEntry> entries = new ArrayList<>();
                if (nextIndex.get(peer) < logEntries.size()) {
                    entries = new ArrayList<>(logEntries.subList(nextIndex.get(peer), logEntries.size()));
                }
                peer.tell(new RaftMessage.AppendEntries(currentTerm, getContext().getSelf(), prevLogIndex, prevLogTerm,
                        entries, commitIndex));
            } catch (Exception e) {
                getContext().getLog().error("Failed to send heartbeat to peer {}: {}", peer.path().name(),
                        e.getMessage());
                failedPeers.add(peer);
            }
        }

        for (ActorRef<RaftMessage> peer : failedPeers) {
            peers.remove(peer);
            nextIndex.remove(peer);
            matchIndex.remove(peer);
            getContext().getLog().info("Removed failed peer: {}", peer.path().name());
        }

        // Check if we still have majority
        int majority = (peers.size() + 1) / 2 + 1;
        if (peers.size() + 1 < majority) {
            getContext().getLog().info("Lost majority after peer failures, stepping down");
            getContext().getSelf().tell(new RaftMessage.SetCurrentTerm(currentTerm + 1));
        }
    }

    private Behavior<RaftMessage> handleHeartbeat(RaftMessage.Heartbeat msg) {
        sendHeartbeats();
        return this;
    }

    private Behavior<RaftMessage> handleAppendEntriesResponse(RaftMessage.AppendEntriesResponse msg) {
        if (msg.term() > currentTerm) {
            getContext().getLog().info("Received higher term from follower: {} > {}", msg.term(), currentTerm);
            return Follower.create(peers, logFileName);
        }

        if (msg.success()) {
            matchIndex.put(msg.sender(), msg.matchIndex());
            nextIndex.put(msg.sender(), msg.matchIndex() + 1);
            updateCommitIndex();
        } else {
            nextIndex.put(msg.sender(), Math.max(0, nextIndex.get(msg.sender()) - 1));
            // Retry immediately with the updated nextIndex
            handleHeartbeat(new RaftMessage.Heartbeat());
        }
        return this;
    }

    private void updateCommitIndex() {
        for (int n = commitIndex + 1; n < logEntries.size(); n++) {
            if (logEntries.get(n).term() == currentTerm) {
                int count = 1;
                for (int matchIdx : matchIndex.values()) {
                    if (matchIdx >= n) {
                        count++;
                    }
                }
                if (count > (peers.size() + 1) / 2) {
                    commitIndex = n;
                }
            }
        }
        applyCommittedEntries();
    }

    private void applyCommittedEntries() {
        while (lastApplied < commitIndex) {
            lastApplied++;
            LogEntry entry = logEntries.get(lastApplied);
            String result = stateMachine.applyCommand(entry.command().value());
            logPersistence.saveLog(logEntries);

            ActorRef<RaftMessage> client = pendingCommands.remove(lastApplied);
            if (client != null) {
                client.tell(new RaftMessage.ClientResponse(true, result));
            }
        }
    }

    private Behavior<RaftMessage> handleClientCommand(RaftMessage.ClientCommand msg) {
        getContext().getLog().info("Received client command: {}", msg.command());

        LogEntry entry = new LogEntry(currentTerm, new Command(msg.command()));
        logEntries.add(entry);
        logPersistence.saveLog(logEntries);
        int logIndex = logEntries.size() - 1;

        pendingCommands.put(logIndex, msg.replyTo());

        handleHeartbeat(new RaftMessage.Heartbeat());
        return this;
    }

    private Behavior<RaftMessage> handleAddFollower(RaftMessage.AddFollower msg) {
        if (!peers.contains(msg.follower())) {
            peers.add(msg.follower());
            nextIndex.put(msg.follower(), logEntries.size());
            matchIndex.put(msg.follower(), -1);
            getContext().getLog().info("Added new follower: {}", msg.follower().path().name());
        }
        return this;
    }

    private Behavior<RaftMessage> handleRequestVote(RaftMessage.RequestVote msg) {
        if (msg.term() > currentTerm) {
            getContext().getLog().info("Received higher term from candidate: {} > {}", msg.term(), currentTerm);
            return Follower.create(peers, logFileName);
        }

        msg.candidateRef().tell(new RaftMessage.RequestVoteResponse(currentTerm, false));
        return this;
    }

    private Behavior<RaftMessage> handleRequestVoteResponse(RaftMessage.RequestVoteResponse msg) {

        return this;
    }
}