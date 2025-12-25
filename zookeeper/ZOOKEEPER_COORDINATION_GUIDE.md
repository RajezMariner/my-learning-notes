# ZooKeeper Coordination Internals Guide

This guide explains how Apache ZooKeeper implements distributed coordination, covering both the primitives it provides to applications and its internal coordination mechanisms.

## Table of Contents

1. [Overview](#overview)
2. [Core Concepts](#core-concepts)
3. [Coordination Primitives](#coordination-primitives)
4. [Internal Coordination](#internal-coordination)
5. [Code Architecture](#code-architecture)
6. [Implementation Deep Dive](#implementation-deep-dive)

## Overview

ZooKeeper provides distributed coordination through:
- **Hierarchical namespace** (like a filesystem)
- **Atomic operations** with sequential consistency
- **Watch notifications** for changes
- **Session-based ephemeral nodes**

### Key Design Principles

1. **Wait-free coordination** - No blocking primitives
2. **FIFO client ordering** - Requests processed in order
3. **Linearizable writes** - All writes globally ordered
4. **Local reads** - Fast read performance

## Core Concepts

### 1. ZNodes (Data Nodes)

```java
// Location: zookeeper-server/src/main/java/org/apache/zookeeper/server/DataNode.java
public class DataNode implements Record {
    byte[] data;              // Node data (max 1MB)
    Long acl;                 // Access control
    StatPersisted stat;       // Metadata
    Set<String> children;     // Child node names
}
```

**Types of ZNodes:**
- **Persistent** - Exists until explicitly deleted
- **Ephemeral** - Deleted when session ends
- **Sequential** - Appends monotonic counter
- **Container** - Auto-deleted when empty (3.6+)
- **TTL** - Time-to-live nodes (3.6+)

### 2. Sessions

```java
// Location: zookeeper-server/src/main/java/org/apache/zookeeper/server/SessionTracker.java
public interface SessionTracker {
    long createSession(int sessionTimeout);
    boolean touchSession(long sessionId, int sessionTimeout);
    void removeSession(long sessionId);
}
```

Sessions provide:
- Client identity
- Ephemeral node lifecycle
- Heartbeat mechanism

### 3. Watches

```java
// Location: zookeeper-server/src/main/java/org/apache/zookeeper/Watcher.java
public interface Watcher {
    void process(WatchedEvent event);
}
```

One-time triggers for:
- Node data changes
- Node creation/deletion
- Children changes

## Coordination Primitives

### 1. Configuration Management

**Use Case:** Distributed configuration updates

```java
// Write configuration
zk.create("/config/database", 
    "host=db1.example.com".getBytes(), 
    Ids.OPEN_ACL_UNSAFE, 
    CreateMode.PERSISTENT);

// Watch for changes
zk.getData("/config/database", new Watcher() {
    public void process(WatchedEvent e) {
        // Reload configuration
    }
}, null);
```

**How it works:**
1. Configuration stored in persistent znodes
2. Clients watch for changes
3. Updates trigger watch events
4. Clients reload configuration

### 2. Group Membership / Service Discovery

**Use Case:** Track live services

```java
// Register service (ephemeral node)
String path = zk.create("/services/api/member-", 
    "192.168.1.10:8080".getBytes(),
    Ids.OPEN_ACL_UNSAFE,
    CreateMode.EPHEMERAL_SEQUENTIAL);

// Discover services
List<String> services = zk.getChildren("/services/api", true);
```

**Implementation in code:**
- See: `zookeeper-server/src/main/java/org/apache/zookeeper/server/DataTree.java:698` (createNode)
- Ephemeral nodes tracked in `ephemerals` map by session
- Session termination triggers cleanup

### 3. Distributed Lock

**Use Case:** Mutual exclusion

```java
// Simple lock implementation
public class DistributedLock {
    private ZooKeeper zk;
    private String lockPath;
    private String myPath;
    
    public void lock() throws Exception {
        // Create sequential ephemeral node
        myPath = zk.create(lockPath + "/lock-", 
            null, 
            Ids.OPEN_ACL_UNSAFE,
            CreateMode.EPHEMERAL_SEQUENTIAL);
        
        while (true) {
            List<String> children = zk.getChildren(lockPath, false);
            Collections.sort(children);
            
            if (myPath.endsWith(children.get(0))) {
                // We have the lock!
                return;
            }
            
            // Watch the node before us
            String prevNode = getPreviousNode(children, myPath);
            Stat stat = zk.exists(lockPath + "/" + prevNode, true);
            
            if (stat != null) {
                // Wait for watch event
                synchronized (this) {
                    wait();
                }
            }
        }
    }
}
```

**Key code locations:**
- Sequential node creation: `PrepRequestProcessor.java:558`
- Ephemeral cleanup: `DataTree.java:1459` (killSession)

### 4. Leader Election

**Use Case:** Choose single coordinator

```java
// Each candidate creates a sequential ephemeral node
String electionPath = "/election/leader-";
String myNode = zk.create(electionPath, 
    serverInfo.getBytes(),
    Ids.OPEN_ACL_UNSAFE,
    CreateMode.EPHEMERAL_SEQUENTIAL);

// Lowest sequence number wins
List<String> candidates = zk.getChildren("/election", false);
Collections.sort(candidates);
String leader = candidates.get(0);

if (myNode.endsWith(leader)) {
    // I am the leader!
    performLeaderDuties();
} else {
    // Watch the leader
    zk.exists("/election/" + leader, true);
}
```

### 5. Barrier

**Use Case:** Synchronization point

```java
public class Barrier {
    private ZooKeeper zk;
    private String barrierPath;
    private int size;
    
    public void enter() throws Exception {
        zk.create(barrierPath + "/" + sessionId, 
            null,
            Ids.OPEN_ACL_UNSAFE,
            CreateMode.EPHEMERAL);
            
        while (true) {
            List<String> children = zk.getChildren(barrierPath, true);
            if (children.size() >= size) {
                return; // All participants ready
            }
            synchronized (this) {
                wait(); // Wait for watch event
            }
        }
    }
}
```

## Internal Coordination

### 1. ZAB Protocol (ZooKeeper Atomic Broadcast)

**Location:** `zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/`

ZooKeeper uses ZAB for:
- Leader election
- Atomic broadcast of updates
- Recovery

**Key Classes:**
- `FastLeaderElection.java` - Leader election implementation
- `Leader.java` - Leader behavior
- `Follower.java` - Follower behavior
- `QuorumPeer.java` - Main quorum participant

### 2. Leader Election Process

```java
// FastLeaderElection.java:1019
public Vote lookForLeader() throws InterruptedException {
    // Each server votes for itself initially
    updateProposal(getInitId(), getInitLastLoggedZxid(), getPeerEpoch());
    
    // Exchange votes with other servers
    while (running && !stop) {
        Notification n = recvqueue.poll(notTimeout, TimeUnit.MILLISECONDS);
        
        if (n == null) {
            // Send notifications to all peers
            sendNotifications();
        } else {
            // Process received vote
            switch (n.state) {
                case LOOKING:
                    // Compare votes and update if necessary
                    if (totalOrderPredicate(n.leader, n.zxid, n.peerEpoch,
                            proposedLeader, proposedZxid, proposedEpoch)) {
                        updateProposal(n.leader, n.zxid, n.peerEpoch);
                        sendNotifications();
                    }
                    break;
            }
        }
        
        // Check if we have a quorum
        if (termPredicate(votes, proposedLeader, proposedZxid, proposedEpoch)) {
            return new Vote(proposedLeader, proposedZxid, proposedEpoch);
        }
    }
}
```

**Election criteria (totalOrderPredicate):**
1. Higher epoch wins
2. If equal epoch, higher zxid (transaction id) wins
3. If equal zxid, higher server id wins

### 3. Request Processing Pipeline

**Standalone mode:**
```
Client Request 
    → NIOServerCnxn (network layer)
    → ZooKeeperServer.processPacket()
    → PrepRequestProcessor (validate, set headers)
    → SyncRequestProcessor (log to disk)
    → FinalRequestProcessor (apply to memory)
    → Response to client
```

**Quorum mode (write on follower):**
```
Client Request
    → FollowerRequestProcessor
    → CommitProcessor
    → Leader (via PROPOSAL)
    → Broadcast to all followers
    → Followers acknowledge
    → Leader commits (quorum achieved)
    → CommitProcessor continues
    → FinalRequestProcessor
```

### 4. Two-Phase Commit for Writes

**Phase 1: Proposal**
```java
// Leader.java:496
public void processRequest(Request request) {
    // Create proposal with unique zxid
    long zxid = zk.getZxid();
    proposal = new Proposal(request, zxid);
    
    // Log the proposal
    zk.getZKDatabase().log(request);
    
    // Send to all followers
    sendPacket(QuorumPacket.PROPOSAL, proposal);
}
```

**Phase 2: Commit**
```java
// When quorum of ACKs received
if (proposal.ackCount > half) {
    // Send COMMIT to all
    sendPacket(QuorumPacket.COMMIT, proposal);
    
    // Apply to local state
    zk.processTxn(proposal);
}
```

### 5. Session Management

**Key components:**
```java
// SessionTrackerImpl.java
public class SessionTrackerImpl implements SessionTracker {
    // Sessions by ID
    HashMap<Long, SessionImpl> sessionsById;
    
    // Sessions grouped by timeout for efficient expiration
    HashMap<Long, SessionSet> sessionSets;
    
    // Session expiration thread
    public void run() {
        while (running) {
            long waitTime = sessionSets.first().timeout;
            wait(waitTime);
            
            // Check for expired sessions
            SessionSet set = sessionSets.poll();
            for (SessionImpl s : set.sessions) {
                if (s.timeout <= currentTime) {
                    expireSession(s.sessionId);
                }
            }
        }
    }
}
```

## Code Architecture

### Key Packages

1. **`org.apache.zookeeper`** - Client API
   - `ZooKeeper.java` - Main client class
   - `Watcher.java` - Watch interface
   - `CreateMode.java` - Node types

2. **`org.apache.zookeeper.server`** - Server core
   - `ZooKeeperServer.java` - Standalone server
   - `DataTree.java` - In-memory data store
   - `SessionTracker.java` - Session management
   - Request processors chain

3. **`org.apache.zookeeper.server.quorum`** - Distributed consensus
   - `Leader.java` - Leader role
   - `Follower.java` - Follower role
   - `FastLeaderElection.java` - Election algorithm
   - `QuorumPeer.java` - Quorum participant

### Data Flow Example: Create Operation

1. **Client sends create request**
   ```java
   // ZooKeeper.java:1413
   public String create(String path, byte[] data, List<ACL> acl, CreateMode mode)
   ```

2. **Server receives in NIOServerCnxn**
   ```java
   // NIOServerCnxn.java:457
   void doIO(SelectionKey k) {
       readPayload();
       processPacket(packet);
   }
   ```

3. **PrepRequestProcessor validates**
   ```java
   // PrepRequestProcessor.java:315
   case OpCode.create:
       CreateRequest create = new CreateRequest();
       pRequest2Txn(request.type, zks.getNextZxid(), request, create);
   ```

4. **SyncRequestProcessor logs**
   ```java
   // SyncRequestProcessor.java:155
   zks.getZKDatabase().append(si);
   ```

5. **FinalRequestProcessor applies**
   ```java
   // FinalRequestProcessor.java:459
   ProcessTxnResult rc = zks.processTxn(request);
   ```

6. **DataTree creates node**
   ```java
   // DataTree.java:698
   public void createNode(String path, byte[] data, List<ACL> acl, 
                         long ephemeralOwner, int parentCVersion, 
                         long zxid, long time, Stat outputStat)
   ```

## Implementation Deep Dive

### 1. Watch Management

**Watch storage:**
```java
// WatchManager.java
private final Map<String, Set<Watcher>> watchTable = new HashMap<>();
private final Map<Watcher, Set<String>> watch2Paths = new HashMap<>();
```

**Triggering watches:**
```java
// DataTree.java:531 (setData operation)
public Stat setData(String path, byte data[], int version, long zxid, long time) {
    Stat s = node.stat;
    node.data = data;
    node.stat.setMtime(time);
    node.stat.setMzxid(zxid);
    node.stat.setVersion(version);
    
    // Trigger data watches
    dataWatches.triggerWatch(path, EventType.NodeDataChanged, zxid);
    return s;
}
```

### 2. Sequential Node Implementation

```java
// PrepRequestProcessor.java:578
if (createMode.isSequential()) {
    path = path + String.format(Locale.ENGLISH, "%010d", 
        parentCVersion); // 10-digit padded sequence
}
```

### 3. Ephemeral Node Tracking

```java
// DataTree.java
private final Map<Long, HashSet<String>> ephemerals = 
    new ConcurrentHashMap<>();

// On session close
public void killSession(long session, long zxid) {
    HashSet<String> paths = ephemerals.remove(session);
    if (paths != null) {
        for (String path : paths) {
            deleteNode(path, zxid);
        }
    }
}
```

### 4. Atomic Broadcast Implementation

```java
// Leader.java:734 (broadcast proposal)
public void propose(Proposal proposal) {
    QuorumPacket qp = buildProposalPacket(proposal);
    
    for (LearnerHandler f : learners) {
        f.queuePacket(qp);
    }
    
    // Track pending proposals
    outstandingProposals.add(proposal);
}
```

## Performance Optimizations

1. **Read from local replica** - No consensus needed
2. **Sequential consistency** - Client operations ordered
3. **Batching** - Multiple operations in single transaction
4. **Asynchronous logging** - Write-ahead log optimization
5. **Memory-based storage** - All data in RAM

## Testing Coordination

Run these examples to see coordination in action:

### 1. Leader Election Test
```java
// Create multiple clients simulating candidates
for (int i = 0; i < 5; i++) {
    ZooKeeper zk = new ZooKeeper("localhost:2181", 3000, null);
    String node = zk.create("/election/candidate-", 
        ("server-" + i).getBytes(),
        Ids.OPEN_ACL_UNSAFE,
        CreateMode.EPHEMERAL_SEQUENTIAL);
    System.out.println("Created: " + node);
}

// Check who is leader
List<String> children = zk.getChildren("/election", false);
Collections.sort(children);
System.out.println("Leader is: " + children.get(0));
```

### 2. Distributed Lock Test
```java
public class LockTest {
    public static void main(String[] args) throws Exception {
        // Run multiple instances to see mutual exclusion
        DistributedLock lock = new DistributedLock(zk, "/locks/resource1");
        
        lock.acquire();
        System.out.println("Got lock, doing work...");
        Thread.sleep(10000);
        lock.release();
    }
}
```

## Key Takeaways

1. **ZooKeeper provides primitives, not solutions** - Build coordination patterns on top
2. **Everything is ordered** - Sequential consistency simplifies reasoning
3. **Watches enable reactive programming** - No polling needed
4. **Sessions + ephemeral nodes = failure detection** - Automatic cleanup
5. **ZAB ensures consistency** - Total order broadcast for all updates

## Further Reading

- ZooKeeper paper: "ZooKeeper: Wait-free coordination for Internet-scale systems"
- ZAB paper: "A simple totally ordered broadcast protocol"
- Internal documentation: `zookeeper-docs/` folder
- Recipe implementations: `zookeeper-recipes/` folder