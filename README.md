# My Learning Notes

A collection of learning notes on distributed systems, networking, and Java NIO.

## Directory Structure

```
my-learning-notes/
|
+-- networking/          # OS & Network Fundamentals
|   +-- networking.md        # Sockets, ports, connections, file descriptors
|   +-- tcp-udp-protocols.md # TCP/UDP deep dive
|
+-- nio-java/            # Java NIO & Server Programming
|   +-- NIO_LEARNING_GUIDE.md    # Java NIO concepts
|   +-- MY_SERVER_GUIDE.md       # Building servers with NIO
|   +-- MySimpleServer.java      # Example NIO server
|   +-- TestClient.java          # Test client
|
+-- zookeeper/           # Apache ZooKeeper
    +-- ZOOKEEPER_NIO_INDEX.md        # Index & overview
    +-- ZOOKEEPER_LEARNING_PATH.md    # Learning roadmap
    +-- ZOOKEEPER_COORDINATION_GUIDE.md # Coordination patterns
    +-- ZOOKEEPER_FLOWCHARTS.md       # Visual diagrams
    +-- ZOOKEEPER_TESTING_GUIDE.md    # Testing guide
    +-- KAFKA_ZOOKEEPER.md            # Kafka + ZK integration
    +-- DEVELOPMENT_README.md         # Development setup
    +-- README_packaging.md           # Packaging info
```

## Topics Covered

### Networking Fundamentals
- OSI/TCP-IP network stack layers
- TCP connections (3-way handshake, state machine)
- UDP protocol characteristics
- Sockets, ports, and file descriptors
- OS networking internals (kernel buffers, syscalls)
- Web server socket models (FastAPI, Jersey, Netty)

### Java NIO
- Channels, Buffers, Selectors
- Non-blocking I/O patterns
- Building high-performance servers

### Apache ZooKeeper
- Distributed coordination primitives
- Leader election, locks, barriers
- ZAB protocol internals
- Kafka + ZooKeeper integration
- Testing strategies
