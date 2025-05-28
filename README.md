# Raft Implementation

This is an implementation of the Raft consensus algorithm using Akka actors in Java. The implementation follows the Raft protocol as described in the paper "In Search of an Understandable Consensus Algorithm" by Diego Ongaro and John Ousterhout.

When you run the program:

1. You will be prompted to enter the number of processes (servers) in the cluster
2. The system will initialize with the specified number of servers
3. Client commands will be automatically generated and sent every 2 seconds
4. The commands include setting and removing key-value pairs (e.g., "user1=John", "user1=null")
5. Press Enter to exit the program

## Implementation Details

### Core Components

- **Leader**: Handles client requests, log replication, and heartbeat management
- **Follower**: Processes leader heartbeats and client requests
- **Candidate**: Manages election process and vote collection
- **State Machine**: Applies committed log entries
- **Log Persistence**: Handles log storage and retrieval

### Key Features

- Leader election with randomized timeouts
- Log replication with consistency checks
- Client command handling and forwarding
- Persistent log storage
- Graceful shutdown handling

## Known Issues

1. I am not stopping the servers when it is running to stimulate failures and recovery.
     I have a test for that.
2. Can not get a leader what the leader dies.


## External Sources Consulted

1. **Raft Paper and Resources**

   - "In Search of an Understandable Consensus Algorithm" by Diego Ongaro and John Ousterhout
   - Raft Visualization: https://raft.github.io/
   - Raft Implementation Guide: https://raft.github.io/raft.pdf

2. **Akka Documentation**

   - Akka Typed Documentation: https://doc.akka.io/docs/akka/current/typed/index.html
   - Akka Actor Lifecycle: https://doc.akka.io/docs/akka/current/typed/actor-lifecycle.html
   - Akka Testing: https://doc.akka.io/docs/akka/current/typed/testing.html

3. **Java Concurrency**
   - Java Concurrency in Practice by Brian Goetz
   - Java Memory Model documentation

## Building and Running

### Prerequisites

- Java 11 or higher
- Maven 3.6 or higher

### Build

```bash
mvn clean compile
```

### Run

```bash
mvn exec:java -Dexec.mainClass="org.example.raft.Main"
```

### Testing

```bash
mvn test
```
