package org.example.raft;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.DirectoryStream;
import java.io.IOException;
import java.util.UUID;

import org.example.raft.core.Follower;
import org.example.raft.messages.RaftMessage;

class VoteHandlingTest {
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
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of("."), "vote-test*.log")) {
            for (Path entry : stream) {
                Files.deleteIfExists(entry);
            }
        }
        probe = testKit.createTestProbe();
        logFileName = "vote-test-" + UUID.randomUUID() + ".log";
        follower = testKit.spawn(Follower.create(new ArrayList<>(), logFileName));
    }

    @Test
    void shouldGrantVoteToCandidateWithHigherTerm() {
        int term = 3;
        follower.tell(new RaftMessage.RequestVote(term, probe.getRef()));

        RaftMessage.RequestVoteResponse response = probe.expectMessageClass(RaftMessage.RequestVoteResponse.class);
        assertTrue(response.voteGranted(), "Follower should grant vote to candidate with higher term");
        assertEquals(term, response.term(), "Response should have the same term as the request");
    }

    @Test
    void shouldRejectVoteToCandidateWithLowerTerm() {

        follower.tell(new RaftMessage.SetCurrentTerm(2));
        follower.tell(new RaftMessage.RequestVote(1, probe.getRef()));

        RaftMessage.RequestVoteResponse response = probe.expectMessageClass(RaftMessage.RequestVoteResponse.class);
        assertFalse(response.voteGranted(), "Follower should reject vote to candidate with lower term");
        assertEquals(2, response.term(), "Response should have the follower's term");
    }

    @Test
    void shouldOnlyVoteOncePerTerm() {
        TestProbe<RaftMessage> probe2 = testKit.createTestProbe();

        follower.tell(new RaftMessage.RequestVote(1, probe.getRef()));

        RaftMessage.RequestVoteResponse response1 = probe.expectMessageClass(RaftMessage.RequestVoteResponse.class);
        assertTrue(response1.voteGranted(), "First vote in a term should be granted");

        follower.tell(new RaftMessage.RequestVote(1, probe2.getRef()));

        RaftMessage.RequestVoteResponse response2 = probe2.expectMessageClass(RaftMessage.RequestVoteResponse.class);
        assertFalse(response2.voteGranted(), "Second vote in the same term should be rejected");
    }
}