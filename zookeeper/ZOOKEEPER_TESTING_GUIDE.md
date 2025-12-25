# ZooKeeper Testing Guide

This document provides comprehensive testing commands and scenarios for ZooKeeper to help you understand its functionality and test your code changes.

## Prerequisites

1. Ensure ZooKeeper is running:
   ```bash
   ./bin/zkServer.sh status
   ```

2. If not running, start it:
   ```bash
   ./bin/zkServer.sh start
   ```

## Understanding ZooKeeper's Binary Data Storage

### Why Binary Format?

ZooKeeper uses binary format for data storage to achieve high-performance distributed coordination. Here's why this is critical:

#### Performance Requirements
- **Thousands of operations per second**
- **Sub-millisecond response times**  
- **Minimal CPU overhead**
- **Fast recovery after crashes**

#### Text vs Binary Performance Comparison

**Text Format Issues:**
```bash
# JSON example: {"operation":"setData","path":"/myapp","data":"config","zxid":123456789}
```
- **Parsing overhead**: JSON/XML parsing is CPU-intensive (~100-500 microseconds per operation)
- **String conversion**: Converting numbers ↔ strings repeatedly  
- **Size overhead**: JSON uses ~3x more space than binary
- **Memory waste**: String objects have ~40 bytes overhead in Java

**Binary Format Advantages:**
```bash
# Binary: [MAGIC][VERSION][CHECKSUM][ZXID:8bytes][TIMESTAMP:8bytes][PATH_LENGTH:4bytes][PATH][DATA]
```
- **Direct memory access**: No parsing needed (~10-50 microseconds per operation)
- **Fixed-size fields**: Instant field access
- **Compact storage**: ~70% smaller than text
- **Native data types**: Integers stay as integers

#### Real-World Impact
- **10,000 ops/second**: Binary = 0.5s overhead, Text = 5s overhead (10x faster)
- **1M znodes recovery**: Binary = 3-6 seconds, Text = 30-60 seconds (10x faster startup)
- **Memory usage**: Binary uses 70% less memory than text format

### Where ZooKeeper Stores Your Data

When you run `set /myapp "updated configuration"`, the data is stored in:

```bash
# Main data directory (configured in zoo.cfg)
/tmp/zookeeper/version-2/

# Two types of files:
├── log.1, log.2, log.3...     # Transaction logs (all write operations)
└── snapshot.0, snapshot.1...  # Point-in-time data tree snapshots
```

### Reading Binary Data (Don't use `cat`!)

The files are in binary format, so use ZooKeeper's built-in tools:

```bash
# Read transaction logs properly
./bin/zkTxnLogToolkit.sh /tmp/zookeeper/version-2/log.3

# Read snapshot files properly
./bin/zkSnapShotToolkit.sh /tmp/zookeeper/version-2/snapshot.2

# Compare snapshots
./bin/zkSnapshotComparer.sh /tmp/zookeeper/version-2/snapshot.1 /tmp/zookeeper/version-2/snapshot.2

# Check data directory location
grep dataDir conf/zoo.cfg
```

### Binary Format Benefits for ZooKeeper

1. **Transaction Log Performance**: Enables 10,000+ writes/second
2. **Snapshot Recovery**: 10x faster startup after crashes  
3. **Memory Efficiency**: Supports millions of znodes efficiently
4. **Network Protocol**: Fast client-server communication
5. **Cross-Platform**: Consistent binary format across systems

This binary approach is why ZooKeeper can handle enterprise-scale distributed coordination while maintaining low latency.

## 1. Basic CLI Commands

### Start Interactive CLI
```bash
./bin/zkCli.sh
```

### Basic CRUD Operations
```bash
# List root directory
ls /

# Create a znode with data
create /myapp "application configuration data"

# Read znode data and metadata
get /myapp

# Update znode data
set /myapp "updated configuration"

# Get detailed statistics
stat /myapp

# List with statistics
ls -s /

# Delete znode
delete /myapp

# Exit CLI
quit
```

### Znode Types and Properties
```bash
# Persistent znode (default - survives client disconnect)
create /persistent "persistent data"

# Sequential znode (auto-appends sequence number)
create -s /sequential "seq_"

# Ephemeral znode (deleted when session ends)
create -e /ephemeral "session data"

# Ephemeral sequential znode
create -e -s /ephemeral-seq "temp_"

# Check what was created
ls /
```

## 2. Advanced CLI Operations

### Hierarchical Structure
```bash
# Create directory structure
create /applications ""
create /applications/web "nginx config"
create /applications/database "postgresql config"
create /applications/cache "redis config"

# Create nested structure
create /services ""
create /services/frontend ""
create /services/backend ""
create /services/database ""

# List hierarchical structure
ls /
ls /applications
ls /services
```

### Watches and Notifications
```bash
# Terminal 1: Set up a watch
get -w /applications/web

# Terminal 2: Modify the watched znode
set /applications/web "updated nginx config"

# Terminal 1 will show the watch notification
```

### Access Control Lists (ACL)
```bash
# Create znode with specific permissions
create /secure "secret data" world:anyone:r

# Create with digest authentication
create /admin "admin data" digest:user:password:rw

# Check ACL
getAcl /secure
```

## 3. One-Line Commands (Non-Interactive)

### Single Operations
```bash
# Create
echo "create /test 'test data'" | ./bin/zkCli.sh -server localhost:2181

# Read
echo "get /test" | ./bin/zkCli.sh -server localhost:2181

# Update
echo "set /test 'updated data'" | ./bin/zkCli.sh -server localhost:2181

# Delete
echo "delete /test" | ./bin/zkCli.sh -server localhost:2181

# List
echo "ls /" | ./bin/zkCli.sh -server localhost:2181
```

### Batch Operations
```bash
# Multiple commands in one script
./bin/zkCli.sh << EOF
create /batch_test ""
create /batch_test/item1 "data1"
create /batch_test/item2 "data2" 
create /batch_test/item3 "data3"
ls /batch_test
delete /batch_test/item1
delete /batch_test/item2
delete /batch_test/item3
delete /batch_test
quit
EOF
```

## 4. REST API Testing

### Server Information
```bash
# Server status
curl -s http://localhost:8080/commands/stat | jq '.'

# Configuration
curl -s http://localhost:8080/commands/conf | jq '.'

# Environment
curl -s http://localhost:8080/commands/envi | jq '.'

# Monitor metrics
curl -s http://localhost:8080/commands/mntr

# Server health check
curl -s http://localhost:8080/commands/ruok
```

### Connection Information
```bash
# Active connections
curl -s http://localhost:8080/commands/cons | jq '.'

# Connection statistics
curl -s http://localhost:8080/commands/dump | jq '.'
```

## 5. Real-World Scenarios

### Scenario 1: Configuration Management
```bash
./bin/zkCli.sh << EOF
# Create configuration hierarchy
create /config ""
create /config/app ""
create /config/app/database "host=localhost,port=5432,db=myapp"
create /config/app/cache "redis://localhost:6379/0"
create /config/app/logging "level=INFO,file=/var/log/app.log"
create /config/app/features "feature1=true,feature2=false"

# Read all configuration
ls /config/app
get /config/app/database
get /config/app/cache
get /config/app/logging

# Update configuration
set /config/app/logging "level=DEBUG,file=/var/log/app.log"
quit
EOF
```

### Scenario 2: Service Discovery
```bash
./bin/zkCli.sh << EOF
# Create service registry
create /services ""
create /services/web-server ""
create /services/api-server ""
create /services/database ""

# Register service instances (ephemeral nodes)
create -e -s /services/web-server/instance- "192.168.1.10:8080,healthy"
create -e -s /services/web-server/instance- "192.168.1.11:8080,healthy"
create -e -s /services/api-server/instance- "192.168.1.20:3000,healthy"

# List all services
ls /services
ls /services/web-server
ls /services/api-server

# Service health check simulation
set /services/web-server/instance-0000000001 "192.168.1.11:8080,unhealthy"
quit
EOF
```

### Scenario 3: Distributed Coordination
```bash
./bin/zkCli.sh << EOF
# Create coordination nodes
create /coordination ""
create /coordination/locks ""
create /coordination/barriers ""
create /coordination/queues ""

# Simulate distributed lock
create /coordination/locks/resource1 "locked_by_client1"
get /coordination/locks/resource1

# Simulate barrier
create /coordination/barriers/task1 ""
create -s /coordination/barriers/task1/ready- "client1"
create -s /coordination/barriers/task1/ready- "client2"
ls /coordination/barriers/task1

# Simulate queue
create /coordination/queues/tasks ""
create -s /coordination/queues/tasks/task- "process_user_data"
create -s /coordination/queues/tasks/task- "send_email"
create -s /coordination/queues/tasks/task- "update_cache"
ls /coordination/queues/tasks
quit
EOF
```

## 6. Performance Testing

### Load Testing Script
```bash
# Create load_test.sh
cat > load_test.sh << 'EOF'
#!/bin/bash
echo "Starting ZooKeeper load test..."

# Create test directory
echo "create /loadtest \"\"" | ./bin/zkCli.sh -server localhost:2181 > /dev/null 2>&1

# Create many nodes
for i in {1..100}; do
    echo "create /loadtest/node$i \"data$i\"" | ./bin/zkCli.sh -server localhost:2181 > /dev/null 2>&1
    if [ $((i % 10)) -eq 0 ]; then
        echo "Created $i nodes"
    fi
done

# Read operations
echo "Testing read operations..."
for i in {1..50}; do
    echo "get /loadtest/node$i" | ./bin/zkCli.sh -server localhost:2181 > /dev/null 2>&1
done

# Update operations  
echo "Testing update operations..."
for i in {1..30}; do
    echo "set /loadtest/node$i \"updated_data$i\"" | ./bin/zkCli.sh -server localhost:2181 > /dev/null 2>&1
done

echo "Load test completed!"
EOF

chmod +x load_test.sh
./load_test.sh
```

### Concurrent Access Test
```bash
# Create concurrent_test.sh
cat > concurrent_test.sh << 'EOF'
#!/bin/bash

# Function to create nodes in background
create_nodes() {
    local prefix=$1
    for i in {1..20}; do
        echo "create /concurrent/${prefix}_$i \"${prefix}_data_$i\"" | ./bin/zkCli.sh -server localhost:2181 > /dev/null 2>&1
    done
}

# Setup test environment
echo "create /concurrent \"\"" | ./bin/zkCli.sh -server localhost:2181 > /dev/null 2>&1

# Run concurrent operations
echo "Starting concurrent operations..."
create_nodes "client1" &
create_nodes "client2" &
create_nodes "client3" &

wait

# Check results
echo "Checking results..."
echo "ls /concurrent" | ./bin/zkCli.sh -server localhost:2181

echo "Concurrent test completed!"
EOF

chmod +x concurrent_test.sh
./concurrent_test.sh
```

## 7. Monitoring and Debugging

### Watch Server Logs
```bash
# Real-time log monitoring
tail -f logs/zookeeper-*.out

# Filter for specific events
tail -f logs/zookeeper-*.out | grep -E "(WARN|ERROR|Session)"

# Monitor your custom log messages
tail -f logs/zookeeper-*.out | grep -E "(distributed|standalone|quorum)"
```

### Performance Monitoring
```bash
# Monitor server statistics
watch -n 5 'curl -s http://localhost:8080/commands/stat | jq ".server_stats"'

# Monitor connections
watch -n 5 'curl -s http://localhost:8080/commands/cons | jq ". | length"'

# Check memory usage
curl -s http://localhost:8080/commands/mntr | grep -E "zk_approximate_data_size|zk_open_file_descriptor_count"
```

## 8. Cleanup Commands

### Clean Up Test Data
```bash
./bin/zkCli.sh << EOF
# Remove test structures
deleteall /config
deleteall /services  
deleteall /coordination
deleteall /loadtest
deleteall /concurrent
deleteall /applications
quit
EOF
```

### Reset ZooKeeper Data
```bash
# Stop ZooKeeper
./bin/zkServer.sh stop

# Clear data directory
rm -rf /tmp/zookeeper/*

# Start fresh
./bin/zkServer.sh start
```

## 9. Error Testing

### Test Error Scenarios
```bash
./bin/zkCli.sh << EOF
# Try to create duplicate nodes
create /duplicate "data1"
create /duplicate "data2"

# Try to delete non-existent nodes
delete /nonexistent

# Try to get non-existent nodes
get /missing

# Try invalid operations
set /nonexistent "data"
quit
EOF
```

## 10. Integration Testing

### Test with Different Data Types
```bash
./bin/zkCli.sh << EOF
# JSON data
create /json_config '{"host":"localhost","port":8080,"ssl":true}'

# XML data  
create /xml_config '<config><host>localhost</host><port>8080</port></config>'

# Binary-like data
create /binary_config "\\x00\\x01\\x02\\x03"

# Large data
create /large_data "$(printf 'A%.0s' {1..1000})"

# Read back
get /json_config
get /xml_config  
get /large_data
quit
EOF
```

## Usage Instructions

1. **Copy this file** to your local machine
2. **Make scripts executable**: `chmod +x *.sh`
3. **Run tests incrementally** - start with basic CLI commands
4. **Monitor logs** in separate terminal: `tail -f logs/zookeeper-*.out`
5. **Check your debug output** while running tests
6. **Clean up** after testing: use cleanup commands

## Tips for Testing Your Code Changes

1. **Restart ZooKeeper** after code changes: `./bin/zkServer.sh restart`
2. **Monitor startup logs** to see your debug messages
3. **Test different scenarios** to trigger various code paths
4. **Use REST API** for automated testing
5. **Create custom test scripts** based on your specific use cases

This guide covers comprehensive testing scenarios. Start with basic commands and gradually move to advanced scenarios based on your testing needs.