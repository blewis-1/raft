package org.example.raft;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.DirectoryStream;
import java.io.IOException;
import java.util.UUID;

import org.example.raft.core.Follower;
import org.example.raft.messages.RaftMessage;
import org.example.raft.state.Command;
import org.example.raft.state.LogEntry;

class FollowerTest {
    private static ActorTestKit testKit = ActorTestKit.create();
    private TestProbe<RaftMessage> probe;
    private ActorRef<RaftMessage> follower;
    private String logFileName;

    @AfterAll
    static void cleanup() {
        testKit.shutdownTestKit();
    }

    @BeforeEach
    void setup() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of("."), "follower-test*.log")) {
            for (Path entry : stream) {
                Files.deleteIfExists(entry);
            }
        }
        probe = testKit.createTestProbe();
        logFileName = "follower-test-" + UUID.randomUUID() + ".log";
        follower = testKit.spawn(Follower.create(List.of(probe.getRef()), logFileName));
    }

    @Test
    void testElectionTimeout() {
        RaftMessage.RequestVote requestVote = probe.expectMessageClass(RaftMessage.RequestVote.class,
                Duration.ofMillis(3000));

        assertEquals(1, requestVote.term(), "Term should be incremented");
        assertEquals(follower, requestVote.candidateRef(), "Should request vote from itself");
    }

    @Test
    void shouldRejectAppendEntriesWithLowerTerm() {
        follower.tell(new RaftMessage.SetCurrentTerm(2));
        follower.tell(new RaftMessage.AppendEntries(2, probe.getRef(), 0, 0, List.of(), 0));
        probe.expectMessageClass(RaftMessage.AppendEntriesResponse.class);
        follower.tell(new RaftMessage.AppendEntries(1, probe.getRef(), 0, 0, List.of(), 0));

        RaftMessage.AppendEntriesResponse response = probe.expectMessageClass(RaftMessage.AppendEntriesResponse.class);
        assertFalse(response.success());
        assertEquals(2, response.term());
    }

    @Test
    void shouldAcceptAppendEntriesWithHigherTerm() {
        follower.tell(new RaftMessage.SetCurrentTerm(1));
        follower.tell(new RaftMessage.AppendEntries(2, probe.getRef(), 0, 0, List.of(), 0));

        RaftMessage.AppendEntriesResponse response = probe.expectMessageClass(RaftMessage.AppendEntriesResponse.class);
        assertTrue(response.success());
        assertEquals(2, response.term());
    }

    @Test
    void shouldRejectAppendEntriesWhenLogInconsistent() {

        follower.tell(new RaftMessage.SetCurrentTerm(1));
        follower.tell(new RaftMessage.AppendEntries(1, probe.getRef(), -1, 0, List.of(
                new LogEntry(1, new Command("test-1=value1"))), 0));

        probe.expectMessageClass(RaftMessage.AppendEntriesResponse.class);

        follower.tell(new RaftMessage.AppendEntries(1, probe.getRef(), 0, 2, List.of(
                new LogEntry(1, new Command("test-2=value2"))), 0));

        RaftMessage.AppendEntriesResponse response = probe.expectMessageClass(RaftMessage.AppendEntriesResponse.class);
        assertFalse(response.success());
    }

    @Test
    void shouldApplyCommittedEntries() {
        follower.tell(new RaftMessage.SetCurrentTerm(1));

        LogEntry entry = new LogEntry(1, new Command("test-1=value1"));
        LogEntry entry1 = new LogEntry(1, new Command("test-2=value2"));

        follower.tell(new RaftMessage.AppendEntries(1, probe.getRef(), -1, 0, List.of(entry, entry1), 0));

        RaftMessage.AppendEntriesResponse response = probe.expectMessageClass(RaftMessage.AppendEntriesResponse.class);
        assertTrue(response.success());

        follower.tell(new RaftMessage.GetLog(probe.getRef()));
        RaftMessage.GetLogResponse logResponse = probe.expectMessageClass(RaftMessage.GetLogResponse.class);

        assertEquals(2, logResponse.log().size());
        assertEquals(entry, logResponse.log().get(0));
    }

    @Test
    void shouldApplyEntryToStateMachine() {
        follower.tell(new RaftMessage.SetCurrentTerm(1));

        LogEntry entry = new LogEntry(1, new Command("apply-test=value"));

        follower.tell(new RaftMessage.AppendEntries(1, probe.getRef(), -1, 0, List.of(entry), 0));

        RaftMessage.AppendEntriesResponse response = probe.expectMessageClass(RaftMessage.AppendEntriesResponse.class);
        assertTrue(response.success());

        follower.tell(new RaftMessage.GetLog(probe.getRef()));
        RaftMessage.GetLogResponse logResponse = probe.expectMessageClass(RaftMessage.GetLogResponse.class);
        assertEquals(1, logResponse.log().size());
        assertEquals(entry, logResponse.log().get(0));
    }

    @Test
    void shouldRecoverFromFailure() {
        // Create initial log entries
        LogEntry entry1 = new LogEntry(1, new Command("key1=value1"));
        LogEntry entry2 = new LogEntry(1, new Command("key2=value2"));

        follower.tell(new RaftMessage.SetCurrentTerm(1));
        follower.tell(new RaftMessage.AppendEntries(1, probe.getRef(), -1, 0, List.of(entry1, entry2), 0));
        RaftMessage.AppendEntriesResponse response = probe.expectMessageClass(RaftMessage.AppendEntriesResponse.class);
        assertTrue(response.success());

        // Commit the entries
        follower.tell(new RaftMessage.AppendEntries(1, probe.getRef(), 1, 1, List.of(), 1));
        response = probe.expectMessageClass(RaftMessage.AppendEntriesResponse.class);
        assertTrue(response.success());

        // Verify entries are in log
        follower.tell(new RaftMessage.GetLog(probe.getRef()));
        RaftMessage.GetLogResponse logResponse = probe.expectMessageClass(RaftMessage.GetLogResponse.class);
        assertEquals(2, logResponse.log().size());
        assertEquals(entry1, logResponse.log().get(0));
        assertEquals(entry2, logResponse.log().get(1));

        // Verify state machine has the values
        follower.tell(new RaftMessage.GetState("key1", probe.getRef()));
        RaftMessage.GetStateResponse stateResponse = probe.expectMessageClass(RaftMessage.GetStateResponse.class);
        assertEquals("value1", stateResponse.value());

        // Simulate follower failure by creating a new follower instance
        testKit.shutdownTestKit();
        testKit = ActorTestKit.create();
        probe = testKit.createTestProbe();
        follower = testKit.spawn(Follower.create(List.of(probe.getRef()), logFileName));

        follower.tell(new RaftMessage.SetCurrentTerm(1));

        follower.tell(new RaftMessage.AppendEntries(1, probe.getRef(), -1, 0, List.of(entry1, entry2), 2));
        probe.expectMessageClass(RaftMessage.AppendEntriesResponse.class);

        // Verify log is recovered
        follower.tell(new RaftMessage.GetLog(probe.getRef()));
        logResponse = probe.expectMessageClass(RaftMessage.GetLogResponse.class);
        assertEquals(2, logResponse.log().size());
        assertEquals(entry1, logResponse.log().get(0));
        assertEquals(entry2, logResponse.log().get(1));

        follower.tell(new RaftMessage.GetState("key1", probe.getRef()));
        stateResponse = probe.expectMessageClass(RaftMessage.GetStateResponse.class);
        assertEquals("value1", stateResponse.value());
    }
}
