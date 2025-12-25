# ZooKeeper Development Guide

This guide helps you build and run ZooKeeper with your code changes for development and debugging.

## Build Commands

### Full Clean Build with Dependencies
```bash
mvn clean package -DskipTests -s settings.xml
```

### Quick Compile (after making code changes)
```bash
mvn compile -DskipTests -s settings.xml
```

### Compile Only (fastest for small changes)
```bash
mvn compile -DskipTests -s settings.xml
```

## Running ZooKeeper

### Start ZooKeeper Server
```bash
./bin/zkServer.sh start
```

### Stop ZooKeeper Server
```bash
./bin/zkServer.sh stop
```

### Restart ZooKeeper Server
```bash
./bin/zkServer.sh restart
```

### Check ZooKeeper Status
```bash
./bin/zkServer.sh status
```

## Debugging and Logs

### View Live Logs
```bash
tail -f logs/zookeeper-*.out
```

### Check Last 20 Lines of Logs
```bash
tail -20 logs/zookeeper-*.out
```

### Search for Specific Log Messages
```bash
grep -i "your_search_term" logs/zookeeper-*.out
```

## Development Workflow

1. **Make your code changes**
2. **Build**: `mvn compile -DskipTests -s settings.xml`
3. **Restart**: `./bin/zkServer.sh restart`
4. **Check logs**: `tail -f logs/zookeeper-*.out`

## Important Notes

- Always use `-s settings.xml` with Maven commands to avoid settings file issues
- Use `mvn package` for the first build to ensure all dependencies are copied
- Use `mvn compile` for subsequent builds to save time
- Logs are written to `logs/zookeeper-*.out` files
- ZooKeeper runs on port 2181 by default

## Configuration

- Main config file: `conf/zoo.cfg`
- Data directory: `/tmp/zookeeper` (configurable in zoo.cfg)
- Log directory: `logs/`

## Testing Your Changes

Connect to ZooKeeper using the CLI client:
```bash
./bin/zkCli.sh
```

This will help you test your ZooKeeper instance and verify your changes are working.