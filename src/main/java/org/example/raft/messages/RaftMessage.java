package org.example.raft.messages;

import java.util.List;
import akka.actor.typed.ActorRef;
import org.example.raft.state.LogEntry;
import org.example.raft.state.Command;

public interface RaftMessage {
        record Heartbeat() implements RaftMessage {
        }

        record ElectionTimeout() implements RaftMessage {
        }

        record RequestVote(int term, ActorRef<RaftMessage> candidateRef) implements RaftMessage {
        }

        record RequestVoteResponse(int term, boolean voteGranted) implements RaftMessage {
        }

        record AppendEntries(
                        int term,
                        ActorRef<RaftMessage> leaderRef,
                        int prevLogIndex,
                        int prevLogTerm,
                        List<LogEntry> entries,
                        int leaderCommit) implements RaftMessage {
        }

        record AppendEntriesResponse(
                        int term,
                        boolean success,
                        ActorRef<RaftMessage> sender,
                        int matchIndex) implements RaftMessage {
        }

        record ClientCommand(String command, ActorRef<RaftMessage> replyTo) implements RaftMessage {
        }

        record ClientResponse(boolean success, String result) implements RaftMessage {
        }

        record SetCurrentTerm(int term) implements RaftMessage {
        }

        record GetState(String key, ActorRef<RaftMessage> replyTo) implements RaftMessage {
        }

        record StateResponse(
                        int currentTerm,
                        ActorRef<RaftMessage> votedFor,
                        List<LogEntry> log,
                        int commitIndex,
                        int lastApplied) implements RaftMessage {
        }

        record AddFollower(ActorRef<RaftMessage> follower) implements RaftMessage {
        }

        record Start() implements RaftMessage {
        }

        record GetLog(ActorRef<RaftMessage> replyTo) implements RaftMessage {
        }

        record GetLogResponse(List<LogEntry> log) implements RaftMessage {
        }

        record GetLastApplied(ActorRef<RaftMessage> replyTo) implements RaftMessage {
        }

        record LastAppliedResponse(int lastApplied) implements RaftMessage {
        }

        record GetStateResponse(String value) implements RaftMessage {
        }

        record AppendEntry(Command command) implements RaftMessage {
        }
}