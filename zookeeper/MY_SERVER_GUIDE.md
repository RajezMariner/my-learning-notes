# Building Your Own Server Like ZooKeeper

This guide shows you how ZooKeeper implements its server architecture and provides a working example you can build and extend.

## 🏗️ ZooKeeper's Server Architecture

### Key Components (from the ZooKeeper source code)

#### 1. **Server Initialization** (`ZooKeeperServerMain.java:143`)
```java
// Create the core server
ZooKeeperServer zkServer = new ZooKeeperServer(...);

// Create connection factory (handles networking)
ServerCnxnFactory cnxnFactory = ServerCnxnFactory.createFactory();
cnxnFactory.configure(config.getClientPortAddress(), maxClientCnxns, backlog);
cnxnFactory.startup(zkServer);  // Start listening on port!
```

#### 2. **Threading Model** (`NIOServerCnxnFactory.java`)
ZooKeeper uses a sophisticated multi-threaded architecture:

- **1 Accept Thread**: Listens on port 2181, accepts new connections
- **1-N Selector Threads**: Each handles portion of connections using NIO selectors
- **0-M Worker Threads**: Perform socket I/O operations  
- **1 Expiration Thread**: Cleans up idle connections

**Default on 32-core machine**: 1 accept + 1 expiration + 4 selector + 64 worker threads

#### 3. **Request Processing Chain**
```
Client Request → Accept Thread → Selector Thread → Worker Thread → Request Processor Chain
```

## 🚀 Your Simple Server Implementation

I've created `MySimpleServer.java` that mimics ZooKeeper's core concepts:

### **Key Features:**
- **Single Accept Thread**: Listens on configurable port
- **NIO Selectors**: Non-blocking I/O like ZooKeeper  
- **Thread Pool**: Worker threads for request processing
- **Request/Response Protocol**: Simple command handling
- **Connection Management**: Track and manage client connections
- **In-Memory Data Store**: Basic key-value storage

### **Architecture Comparison:**

| Component | ZooKeeper | MySimpleServer |
|-----------|-----------|----------------|
| **Accept Thread** | ✅ NIOServerCnxnFactory | ✅ acceptLoop() |
| **NIO Selectors** | ✅ Multiple selector threads | ✅ Single selector |
| **Worker Threads** | ✅ Configurable thread pool | ✅ ExecutorService |
| **Connection Tracking** | ✅ ServerCnxn objects | ✅ ClientConnection |
| **Request Processing** | ✅ RequestProcessor chain | ✅ processRequest() |
| **Data Storage** | ✅ DataTree (binary) | ✅ ConcurrentHashMap |

## 🛠️ How to Build and Run

### **1. Compile the Server**
```bash
# Navigate to ZooKeeper directory
cd /Users/rajesh.s/toast/git-repos/zookeeper

# Compile the server
javac MySimpleServer.java

# Compile the test client
javac TestClient.java
```

### **2. Start Your Server**
```bash
# Start server on default port 8888
java MySimpleServer

# Or specify custom port
java MySimpleServer 9999
```

**Expected output:**
```
Starting server on port 8888...
Server started! Listening on port 8888
Try: telnet localhost 8888
Commands: GET /path, SET /path value, LIST, QUIT
Press Enter to stop the server...
```

### **3. Test with Multiple Methods**

#### **Method 1: Using telnet**
```bash
# In another terminal
telnet localhost 8888

# Try commands:
SET /app/config "database=localhost:5432"
GET /app/config  
LIST
QUIT
```

#### **Method 2: Using the Test Client**
```bash
java TestClient
```

#### **Method 3: Multiple Concurrent Clients**
```bash
# Open multiple terminals and run:
java TestClient
# This tests the multi-threading like ZooKeeper handles multiple clients
```

## 📊 Understanding the Code

### **Core Server Loop** (like ZooKeeper's accept loop)
```java
private void acceptLoop() {
    while (running) {
        int readyChannels = selector.select(1000);  // Like ZooKeeper's selector.select()
        
        for (SelectionKey key : selector.selectedKeys()) {
            if (key.isAcceptable()) {
                handleAccept(key);    // New connection
            } else if (key.isReadable()) {
                handleRead(key);      // Client sent data
            }
        }
    }
}
```

### **Request Processing** (like ZooKeeper's RequestProcessor)
```java
private void processRequest(SocketChannel clientChannel, String request) {
    // Parse command (GET, SET, LIST)
    // Execute operation on data store  
    // Send response back to client
    // This runs in worker thread pool (like ZooKeeper)
}
```

### **Connection Management** (like ZooKeeper's ServerCnxn)
```java
private final Map<SocketChannel, ClientConnection> connections = new ConcurrentHashMap<>();
// Tracks all active client connections, just like ZooKeeper
```

## 🔧 Extending Your Server

### **1. Add ZooKeeper-like Features:**

#### **Persistent Storage:**
```java
// Replace ConcurrentHashMap with file-based storage
private void saveToFile() {
    // Implement like ZooKeeper's transaction logs
}
```

#### **Watches/Notifications:**
```java
// Add client notification system
private void notifyWatchers(String path) {
    // Implement like ZooKeeper's watch mechanism
}
```

#### **Hierarchical Paths:**
```java
// Support /parent/child paths
private Map<String, TreeNode> dataTree = new ConcurrentHashMap<>();
```

### **2. Performance Improvements:**

#### **Multiple Selector Threads** (like ZooKeeper):
```java
// Create multiple selectors for load balancing
for (int i = 0; i < numSelectorThreads; i++) {
    new Thread(this::selectorLoop, "SelectorThread-" + i).start();
}
```

#### **Binary Protocol** (like ZooKeeper):
```java
// Replace text protocol with binary for performance
ByteBuffer request = ByteBuffer.allocate(1024);
// Implement like ZooKeeper's Jute protocol
```

## 🎯 Key Learning Points

### **1. Why NIO?**
- **Scalability**: Handle thousands of connections with few threads
- **Performance**: Non-blocking I/O prevents thread blocking
- **Resource Efficiency**: Less memory per connection

### **2. Why Thread Pools?**
- **Isolation**: Request processing doesn't block networking
- **Scalability**: Process multiple requests concurrently  
- **Resource Control**: Limit thread usage

### **3. Why Selectors?**
- **Multiplexing**: One thread handles many connections
- **Event-Driven**: Only process when data is ready
- **Efficiency**: No polling or thread-per-connection

## 🚀 Next Steps

### **1. Study ZooKeeper's Code:**
```bash
# Look at these key files:
./zookeeper-server/src/main/java/org/apache/zookeeper/server/
├── ZooKeeperServerMain.java      # Server startup (what you selected)
├── NIOServerCnxnFactory.java     # NIO networking  
├── ServerCnxnFactory.java        # Abstract connection factory
└── ZooKeeperServer.java          # Core server logic
```

### **2. Experiment with Your Server:**
- Add more commands (DELETE, EXISTS)
- Implement authentication
- Add performance monitoring
- Create a cluster of servers

### **3. Compare Performance:**
```bash
# Test with many concurrent connections
for i in {1..100}; do
    java TestClient &
done
```

## 🎉 Congratulations!

You now have:
- ✅ **Working server** that listens on a port like ZooKeeper
- ✅ **Multi-threaded architecture** similar to ZooKeeper  
- ✅ **NIO-based networking** for scalability
- ✅ **Request/response protocol** for client communication
- ✅ **Foundation to build** a distributed coordination service

This is exactly how enterprise systems like ZooKeeper, Kafka, and distributed databases implement their networking layer!