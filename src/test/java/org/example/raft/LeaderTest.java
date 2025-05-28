package org.example.raft;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.example.raft.core.Leader;
import org.example.raft.messages.RaftMessage;
import org.example.raft.state.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.DirectoryStream;

import static org.junit.jupiter.api.Assertions.*;
import akka.actor.typed.javadsl.Behaviors;
import org.example.raft.state.StateMachine;

class LeaderTest {
    private TestKitJunitResource testKit;
    private ActorRef<RaftMessage> leader;
    private List<TestProbe<RaftMessage>> followers;
    private static final String LOG_FILE = "leader-test.log";

    @BeforeEach
    void setUp() {
        testKit = new TestKitJunitResource();
        followers = new ArrayList<>();
        List<ActorRef<RaftMessage>> followerRefs = new ArrayList<>();

        // Create test probes for followers
        for (int i = 0; i < 3; i++) {
            TestProbe<RaftMessage> probe = testKit.createTestProbe();
            followers.add(probe);
            followerRefs.add(probe.getRef());
        }

        // Create leader with initial state
        leader = testKit.spawn(Behaviors.setup(context -> Leader.create(
                context,
                1, // initial term
                null, // no votedFor initially
                new ArrayList<>(), // empty log
                -1, // commitIndex
                -1, // lastApplied
                new StateMachine(), // new state machine
                followerRefs,
                LOG_FILE)));
    }

    @AfterEach
    void tearDown() {
        if (testKit != null) {
            testKit.after();
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of("."), "leader-test*.log")) {
            for (Path entry : stream) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException e) {
            // Optionally log or print the error
            e.printStackTrace();
        }
    }

    @Test
    void shouldSendHeartbeatsToAllFollowers() {
        // Wait for initial heartbeats
        for (TestProbe<RaftMessage> follower : followers) {
            RaftMessage.AppendEntries heartbeat = follower.expectMessageClass(RaftMessage.AppendEntries.class);
            assertEquals(1, heartbeat.term());
            assertTrue(heartbeat.entries().isEmpty());
        }
    }

    @Test
    void shouldHandleAppendEntryAndReplicateToFollowers() {
        // Send append entry request
        Command command = new Command("key=value");
        TestProbe<RaftMessage> clientProbe = testKit.createTestProbe();
        leader.tell(new RaftMessage.ClientCommand(command.value(), clientProbe.getRef()));

        // Verify followers receive the entry
        for (TestProbe<RaftMessage> follower : followers) {
            RaftMessage.AppendEntries appendEntries = follower.expectMessageClass(RaftMessage.AppendEntries.class);
            assertEquals(1, appendEntries.entries().size());
            assertEquals(command, appendEntries.entries().get(0).command());
        }
    }

    @Test
    void shouldStepDownOnHigherTerm() {
        // Send append entries response with higher term
        leader.tell(new RaftMessage.AppendEntriesResponse(2, true, followers.get(0).getRef(), -1));

        // Verify leader stepped down by checking if it responds to append entries
        Command command = new Command("key=value");
        leader.tell(new RaftMessage.AppendEntry(command));

        // Should not receive append entries as leader stepped down
        followers.get(0).expectNoMessage();
    }

    @Test
    void shouldHandleRequestVoteResponse() {
        // Send vote response
        leader.tell(new RaftMessage.RequestVoteResponse(1, true));

        // Verify leader continues sending heartbeats
        for (TestProbe<RaftMessage> follower : followers) {
            RaftMessage.AppendEntries heartbeat = follower.expectMessageClass(RaftMessage.AppendEntries.class);
            assertEquals(1, heartbeat.term());
        }
    }

    @Test
    void shouldAddNewFollower() {
        // Create new follower
        TestProbe<RaftMessage> newFollower = testKit.createTestProbe();
        leader.tell(new RaftMessage.AddFollower(newFollower.getRef()));

        // Verify new follower receives heartbeat
        RaftMessage.AppendEntries heartbeat = newFollower.expectMessageClass(RaftMessage.AppendEntries.class);
        assertEquals(1, heartbeat.term());
    }
}