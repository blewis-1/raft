error id: file://<WORKSPACE>/src/test/java/org/example/raft/LeaderTest.java
file://<WORKSPACE>/src/test/java/org/example/raft/LeaderTest.java
### com.thoughtworks.qdox.parser.ParseException: syntax error @[48,4]

error in qdox parser
file content:
```java
offset: 1419
uri: file://<WORKSPACE>/src/test/java/org/example/raft/LeaderTest.java
text:
```scala
package org.example.raft;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeaderTest {
    private ActorTestKit testKit;
    private ActorRef<RaftMessage> leader;
    private TestProbe<RaftMessage> leaderProbe;
    private ActorRef<RaftMessage> follower;
    private TestProbe<RaftMessage> followerProbe;

    @BeforeEach
    void setUp() {
        testKit = ActorTestKit.create();
        leaderProbe = testKit.createTestProbe();
        followerProbe = testKit.createTestProbe();
        leader = testKit.spawn(Leader.create());
    }

    @AfterEach
    void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    void shouldSendHeartbeatWhenFollowerAdded() {
        leader.tell(new RaftMessage.SetCurrentTerm(1));

        // Add follower
        leader.tell(new RaftMessage.AddFollower(followerProbe.getRef()));

        // Expect heartbeat
        RaftMessage.AppendEntries heartbeat = followerProbe.expectMessageClass(RaftMessage.AppendEntries.class);
        assertEquals(1, heartbeat.term());
        assertTrue(heartbeat.entries().isEmpty());
        assertEquals(0, heartbeat.leaderCommit());
    }

   @@
```

```



#### Error stacktrace:

```
com.thoughtworks.qdox.parser.impl.Parser.yyerror(Parser.java:2025)
	com.thoughtworks.qdox.parser.impl.Parser.yyparse(Parser.java:2147)
	com.thoughtworks.qdox.parser.impl.Parser.parse(Parser.java:2006)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:232)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:190)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:94)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:89)
	com.thoughtworks.qdox.library.SortedClassLibraryBuilder.addSource(SortedClassLibraryBuilder.java:162)
	com.thoughtworks.qdox.JavaProjectBuilder.addSource(JavaProjectBuilder.java:174)
	scala.meta.internal.mtags.JavaMtags.indexRoot(JavaMtags.scala:49)
	scala.meta.internal.metals.SemanticdbDefinition$.foreachWithReturnMtags(SemanticdbDefinition.scala:99)
	scala.meta.internal.metals.Indexer.indexSourceFile(Indexer.scala:489)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3(Indexer.scala:587)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3$adapted(Indexer.scala:584)
	scala.collection.IterableOnceOps.foreach(IterableOnce.scala:619)
	scala.collection.IterableOnceOps.foreach$(IterableOnce.scala:617)
	scala.collection.AbstractIterator.foreach(Iterator.scala:1306)
	scala.meta.internal.metals.Indexer.reindexWorkspaceSources(Indexer.scala:584)
	scala.meta.internal.metals.MetalsLspService.$anonfun$onChange$2(MetalsLspService.scala:902)
	scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.scala:18)
	scala.concurrent.Future$.$anonfun$apply$1(Future.scala:687)
	scala.concurrent.impl.Promise$Transformation.run(Promise.scala:467)
	java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)
	java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)
	java.base/java.lang.Thread.run(Thread.java:1589)
```
#### Short summary: 

QDox parse error in file://<WORKSPACE>/src/test/java/org/example/raft/LeaderTest.java