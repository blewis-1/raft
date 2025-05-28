package org.example.raft.core;

import java.util.List;
import java.util.Random;
import java.time.Duration;
import java.util.ArrayList;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.TimerScheduler;

import org.example.raft.messages.RaftMessage;
import org.example.raft.state.LogEntry;

public class Follower extends Server {

    private static final String ELECTION_TIMEOUT_KEY = "election-timeout";
    private static final Random random = new Random();
    private static final int MIN_TIMEOUT = 500;
    private static final int MAX_TIMEOUT = 1000;
    private static final int MAX_BACKOFF = 2000;
    private final TimerScheduler<RaftMessage> timers;

    private ActorRef<RaftMessage> leader;
    private int failedElections = 0;

    public static Behavior<RaftMessage> create(List<ActorRef<RaftMessage>> peers, String logFileName) {
        return Behaviors
                .setup(context -> Behaviors.withTimers(timers -> new Follower(context, timers, peers, logFileName)));
    }

    private Follower(ActorContext<RaftMessage> context, TimerScheduler<RaftMessage> timers,
            List<ActorRef<RaftMessage>> peers, String logFileName) {
        super(context, peers, logFileName);
        this.timers = timers;
        this.commitIndex = -1;
        this.lastApplied = -1;

        List<LogEntry> persisted = logPersistence.loadLog();
        if (persisted != null && !persisted.isEmpty()) {
            this.logEntries.addAll(persisted);
        }
        startElectionTimeout();
    }

    private void startElectionTimeout() {
        int baseTimeout = MIN_TIMEOUT + random.nextInt(MAX_TIMEOUT - MIN_TIMEOUT);
        int backoff = Math.min(MAX_BACKOFF, baseTimeout * (1 << failedElections));
        int timeout = baseTimeout + random.nextInt(backoff);
        timers.startSingleTimer(ELECTION_TIMEOUT_KEY, new RaftMessage.ElectionTimeout(), Duration.ofMillis(timeout));
        getContext().getLog().info("Election timeout set: {}ms (attempt {})", timeout, failedElections + 1);
    }

    private void resetElectionTimeout() {
        timers.cancel(ELECTION_TIMEOUT_KEY);
        failedElections = 0;
        startElectionTimeout();
    }

    @Override
    public Receive<RaftMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(RaftMessage.AppendEntries.class, this::handleAppendEntries)
                .onMessage(RaftMessage.SetCurrentTerm.class, this::setCurrentTerm)
                .onMessage(RaftMessage.GetLastApplied.class, this::getLastApplied)
                .onMessage(RaftMessage.GetLog.class, this::getLog)
                .onMessage(RaftMessage.GetState.class, this::getState)
                .onMessage(RaftMessage.AddFollower.class, this::handleAddFollower)
                .onMessage(RaftMessage.ElectionTimeout.class, this::handleElectionTimeout)
                .onMessage(RaftMessage.RequestVote.class, this::handleRequestVote)
                .onMessage(RaftMessage.ClientCommand.class, this::handleClientCommand)
                .build();
    }

    private Behavior<RaftMessage> handleClientCommand(RaftMessage.ClientCommand msg) {
        if (leader != null) {
            // Forward client command to leader
            leader.tell(msg);
            getContext().getLog().info("Forwarded client command to leader: {}", msg.command());
        } else {
            // No leader, reject command
            msg.replyTo().tell(new RaftMessage.ClientResponse(false, "No leader available"));
            getContext().getLog().info("Rejected client command (no leader): {}", msg.command());
        }
        return this;
    }

    private Behavior<RaftMessage> setCurrentTerm(RaftMessage.SetCurrentTerm msg) {
        currentTerm = msg.term();
        return this;
    }

    private Behavior<RaftMessage> getLastApplied(RaftMessage.GetLastApplied msg) {
        msg.replyTo().tell(new RaftMessage.LastAppliedResponse(lastApplied));
        return this;
    }

    private Behavior<RaftMessage> getLog(RaftMessage.GetLog msg) {
        msg.replyTo().tell(new RaftMessage.GetLogResponse(logEntries));
        return this;
    }

    private Behavior<RaftMessage> getState(RaftMessage.GetState msg) {
        String value = stateMachine.getValue(msg.key());
        msg.replyTo().tell(new RaftMessage.GetStateResponse(value));
        return this;
    }

    private Behavior<RaftMessage> handleAddFollower(RaftMessage.AddFollower msg) {
        getContext().getLog().info("Adding follower: {}", msg.follower().path().name());
        if (!peers.contains(msg.follower())) {
            peers.add(msg.follower());
            getContext().getLog().info("Peers: {}", peers.size());
        }
        return this;
    }

    private Behavior<RaftMessage> handleAppendEntries(RaftMessage.AppendEntries msg) {
        if (msg.term() < currentTerm) {
            msg.leaderRef().tell(new RaftMessage.AppendEntriesResponse(currentTerm, false, getContext().getSelf(), -1));
            return this;
        }

        if (msg.term() > currentTerm) {
            getContext().getLog().info("Accept: term={} > current={}", msg.term(), currentTerm);
            updateTerm(msg.term());
        }
        leader = msg.leaderRef();
        currentTerm = msg.term();

        resetElectionTimeout();

        if (!isLogConsistent(msg)) {
            getContext().getLog().info("Reject: log inconsistent");
            sendAppendEntriesResponse(msg.leaderRef(), currentTerm, false);
            return this;
        }

        getContext().getLog().info("Accept: log consistent");
        appendNewEntries(msg);
        updateCommitIndexAndApply(msg);
        sendAppendEntriesResponse(msg.leaderRef(), currentTerm, true);
        return this;
    }

    private void updateTerm(int newTerm) {
        currentTerm = newTerm;
    }

    private boolean isLogConsistent(RaftMessage.AppendEntries message) {
        if (message.prevLogIndex() == -1) {
            return true;
        }
        if (logEntries.isEmpty() && message.prevLogIndex() == 0) {
            return true;
        }
        if (message.prevLogIndex() >= logEntries.size()) {
            getContext().getLog().info("Log inconsistent: index={} > size={}", message.prevLogIndex(),
                    logEntries.size());
            return false;
        }
        LogEntry prevEntry = logEntries.get(message.prevLogIndex());
        boolean consistent = prevEntry.term() == message.prevLogTerm();
        getContext().getLog().info("Log check: index={} term={} entryTerm={} consistent={}",
                message.prevLogIndex(), message.prevLogTerm(), prevEntry.term(), consistent);
        return consistent;
    }

    private void appendNewEntries(RaftMessage.AppendEntries message) {
        if (!message.entries().isEmpty()) {
            synchronized (logEntries) {
                List<LogEntry> newEntries = new ArrayList<>();
                int endIndex = Math.min(message.prevLogIndex() + 1, logEntries.size());
                for (int i = 0; i < endIndex; i++) {
                    newEntries.add(logEntries.get(i));
                }
                newEntries.addAll(message.entries());
                logEntries = newEntries;
                logPersistence.saveLog(logEntries);
            }
        }
    }

    private void updateCommitIndexAndApply(RaftMessage.AppendEntries message) {
        if (message.leaderCommit() > commitIndex) {
            commitIndex = Math.min(message.leaderCommit(), logEntries.size() - 1);
            applyCommittedEntries();
        }
    }

    private void applyCommittedEntries() {
        while (lastApplied < commitIndex) {
            lastApplied++;
            LogEntry entry = logEntries.get(lastApplied);
            stateMachine.applyCommand(entry.command().value());
        }
    }

    private void sendAppendEntriesResponse(ActorRef<RaftMessage> leaderRef, int term, boolean success) {
        leaderRef.tell(new RaftMessage.AppendEntriesResponse(term, success, getContext().getSelf(), -1));
    }

    private Behavior<RaftMessage> handleElectionTimeout(RaftMessage.ElectionTimeout msg) {
        failedElections++;
        getContext().getLog().info("Timeout: becoming candidate term={} (attempt {})", currentTerm + 1,
                failedElections);
        timers.cancel(ELECTION_TIMEOUT_KEY);
        currentTerm++;
        votedFor = getContext().getSelf();

        for (ActorRef<RaftMessage> peer : peers) {
            peer.tell(new RaftMessage.RequestVote(currentTerm, getContext().getSelf()));
        }

        return Candidate.create(
                getContext(),
                currentTerm,
                votedFor,
                new ArrayList<>(logEntries),
                commitIndex,
                lastApplied,
                stateMachine,
                peers,
                logFileName);
    }

    private Behavior<RaftMessage> handleRequestVote(RaftMessage.RequestVote msg) {
        if (msg.term() < currentTerm) {
            msg.candidateRef().tell(new RaftMessage.RequestVoteResponse(currentTerm, false));
            return this;
        }

        if (msg.term() > currentTerm) {
            getContext().getLog().info("Update term: {} -> {}", currentTerm, msg.term());
            updateTerm(msg.term());
        }

        if (votedFor == null || votedFor.equals(msg.candidateRef())) {
            getContext().getLog().info("Grant vote: candidate={} term={}",
                    msg.candidateRef().path().name(),
                    msg.term());
            votedFor = msg.candidateRef();
            msg.candidateRef().tell(new RaftMessage.RequestVoteResponse(msg.term(), true));
            resetElectionTimeout();
        } else {
            getContext().getLog().info("Reject: voted for {} term={}", votedFor.path().name(), currentTerm);
            msg.candidateRef().tell(new RaftMessage.RequestVoteResponse(currentTerm, false));
        }

        return this;
    }
}
