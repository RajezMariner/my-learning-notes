# Networking Fundamentals: TCP, Ports, Channels & OS Internals

## Table of Contents
1. [Network Stack Overview](#network-stack-overview)
2. [TCP Connections](#tcp-connections)
3. [Ports](#ports)
4. [Sockets & Channels](#sockets--channels)
5. [OS Networking Internals](#os-networking-internals)
6. [Data Flow Through the Stack](#data-flow-through-the-stack)

---

## Network Stack Overview

The network stack in an OS follows the OSI model (simplified to TCP/IP model in practice):

```
+------------------------------------------------------------------+
|                      APPLICATION LAYER                           |
|              (HTTP, FTP, SSH, DNS, SMTP, etc.)                   |
|                                                                  |
|   Your application code lives here - uses sockets API            |
+------------------------------------------------------------------+
                              |
                              v
+------------------------------------------------------------------+
|                      TRANSPORT LAYER                             |
|                      (TCP / UDP)                                 |
|                                                                  |
|   - Segmentation/Reassembly                                      |
|   - Port multiplexing                                            |
|   - Flow control (TCP)                                           |
|   - Reliability (TCP)                                            |
+------------------------------------------------------------------+
                              |
                              v
+------------------------------------------------------------------+
|                      NETWORK LAYER                               |
|                         (IP)                                     |
|                                                                  |
|   - Routing                                                      |
|   - IP addressing                                                |
|   - Packet fragmentation                                         |
+------------------------------------------------------------------+
                              |
                              v
+------------------------------------------------------------------+
|                   DATA LINK LAYER                                |
|               (Ethernet, Wi-Fi, etc.)                            |
|                                                                  |
|   - MAC addressing                                               |
|   - Frame creation                                               |
|   - Error detection (CRC)                                        |
+------------------------------------------------------------------+
                              |
                              v
+------------------------------------------------------------------+
|                    PHYSICAL LAYER                                |
|            (Cables, Radio waves, Fiber)                          |
|                                                                  |
|   - Bit transmission                                             |
|   - Electrical/optical signals                                   |
+------------------------------------------------------------------+
```

---

## TCP Connections

### What is TCP?

**Transmission Control Protocol (TCP)** is a connection-oriented, reliable transport protocol that guarantees:
- **Ordered delivery** - packets arrive in sequence
- **Reliable delivery** - lost packets are retransmitted
- **Flow control** - prevents overwhelming the receiver
- **Congestion control** - prevents overwhelming the network

### TCP Connection Lifecycle

```
+--------------------------------------------------------------------------+
|                    TCP CONNECTION LIFECYCLE                              |
+--------------------------------------------------------------------------+

    CLIENT                                              SERVER
       |                                                   |
       |                   +-----------+                   |
       |                   |  LISTEN   |<------------------+ Server binds
       |                   | (Passive  |                   | to port and
       |                   |   Open)   |                   | waits
       |                   +-----------+                   |
       |                                                   |
  =====|===================================================|===============
       |              THREE-WAY HANDSHAKE                  |
  =====|===================================================|===============
       |                                                   |
       |  --------------- SYN (seq=x) ----------------->   |
       |              Client initiates                     |
       |                                                   |
       |  <------------- SYN-ACK (seq=y, ack=x+1) -------  |
       |              Server acknowledges                  |
       |                                                   |
       |  --------------- ACK (ack=y+1) ---------------->  |
       |              Connection established               |
       |                                                   |
  =====|===================================================|===============
       |              DATA TRANSFER                        |
  =====|===================================================|===============
       |                                                   |
       |  --------------- DATA + ACK ------------------>   |
       |  <-------------- DATA + ACK -------------------   |
       |              (bidirectional)                      |
       |                                                   |
  =====|===================================================|===============
       |              FOUR-WAY TERMINATION                 |
  =====|===================================================|===============
       |                                                   |
       |  --------------- FIN ------------------------->   |
       |              Client wants to close                |
       |                                                   |
       |  <-------------- ACK --------------------------   |
       |              Server acknowledges                  |
       |                                                   |
       |  <-------------- FIN --------------------------   |
       |              Server wants to close                |
       |                                                   |
       |  --------------- ACK ------------------------->   |
       |              Connection closed                    |
       |                                                   |
       v                                                   v
```

### TCP State Machine

```
                              +-----------+
                              |  CLOSED   |
                              +-----+-----+
                                    |
              +---------------------+---------------------+
              | (passive open)      |       (active open) |
              v                     |                     v
       +-----------+                |              +-----------+
       |  LISTEN   |                |              | SYN_SENT  |
       +-----+-----+                |              +-----+-----+
             | rcv SYN              |                    | rcv SYN+ACK
             | send SYN+ACK         |                    | send ACK
             v                      |                    v
       +-----------+                |              +-----------+
       | SYN_RCVD  |----------------+------------->| ESTABLISHED|
       +-----------+   rcv ACK      |              +-----+-----+
                                    |                    |
                                    |    +---------------+---------------+
                                    |    | close                         |
                                    |    | send FIN                      |
                                    |    v                               |
                                    | +-----------+                      |
                                    | | FIN_WAIT_1|                      |
                                    | +-----+-----+                      |
                                    |       | rcv ACK                    | rcv FIN
                                    |       v                            | send ACK
                                    | +-----------+                      v
                                    | | FIN_WAIT_2|              +-----------+
                                    | +-----+-----+              | CLOSE_WAIT|
                                    |       | rcv FIN            +-----+-----+
                                    |       | send ACK                 | close
                                    |       v                          | send FIN
                                    | +-----------+                    v
                                    | | TIME_WAIT |              +-----------+
                                    | +-----+-----+              | LAST_ACK  |
                                    |       | 2MSL timeout       +-----+-----+
                                    |       |                          | rcv ACK
                                    |       v                          |
                                    | +-----------+                    |
                                    +>|  CLOSED   |<-------------------+
                                      +-----------+
```

### TCP Header Structure

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          Source Port          |       Destination Port        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Sequence Number                        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    Acknowledgment Number                      |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| Data  |       |U|A|P|R|S|F|                                   |
|Offset | Rsrvd |R|C|S|S|Y|I|            Window                 |
|       |       |G|K|H|T|N|N|                                   |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|           Checksum            |         Urgent Pointer        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    Options (if any)                           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                             Data                              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

Flags:
  URG - Urgent pointer is valid
  ACK - Acknowledgment number is valid
  PSH - Push data immediately
  RST - Reset connection
  SYN - Synchronize sequence numbers
  FIN - Finish (close connection)
```

---

## Ports

### What is a Port?

A **port** is a 16-bit number (0-65535) that identifies a specific process/service on a host. The combination of **IP address + Port** uniquely identifies a network endpoint.

```
+------------------------------------------------------------------+
|                         PORT CONCEPT                             |
+------------------------------------------------------------------+

        IP Address = Street Address (identifies the building)
        Port       = Apartment Number (identifies specific unit)

        +---------------------------------------------+
        |            Server: 192.168.1.100            |
        |  +-------------------------------------+    |
        |  |  +-----+  +-----+  +-----+  +-----+ |    |
        |  |  | :22 |  | :80 |  |:443 |  |:3306| |    |
        |  |  | SSH |  |HTTP |  |HTTPS|  |MySQL| |    |
        |  |  +--+--+  +--+--+  +--+--+  +--+--+ |    |
        |  |     |        |        |        |    |    |
        |  +-----+--------+--------+--------+----+    |
        |        |        |        |        |         |
        +--------+--------+--------+--------+---------+
                 |        |        |        |
                 v        v        v        v
              Network Interface Card (NIC)
```

### Port Ranges

```
+------------------------------------------------------------------+
|                        PORT RANGES                               |
+------------------------------------------------------------------+
|                                                                  |
|  0 --------------- 1023 ---------------- 49151 ----------- 65535 |
|  |                  |                     |                   |  |
|  |  WELL-KNOWN      |     REGISTERED      |    DYNAMIC/       |  |
|  |  (System)        |     (User)          |    EPHEMERAL      |  |
|  |                  |                     |                   |  |
|  |  Requires root   |  Registered with    |  Assigned by OS   |  |
|  |  privileges      |  IANA               |  for client       |  |
|  |                  |                     |  connections      |  |
|  |  Examples:       |  Examples:          |                   |  |
|  |  22 - SSH        |  3306 - MySQL       |  Used when you    |  |
|  |  80 - HTTP       |  5432 - PostgreSQL  |  connect to a     |  |
|  |  443 - HTTPS     |  6379 - Redis       |  server           |  |
|  |  53 - DNS        |  8080 - HTTP Alt    |                   |  |
|  |                  |                     |                   |  |
+------------------------------------------------------------------+
```

### Port Multiplexing

```
+------------------------------------------------------------------+
|                    PORT MULTIPLEXING                             |
|         Multiple connections to same server port                 |
+------------------------------------------------------------------+

  Client A                                         Server
  192.168.1.10                                   10.0.0.1
  +------------+                              +------------+
  | Port 54321 |------------------------------|            |
  +------------+        Connection 1          |            |
                                              |            |
  Client B                                    |  Port 80   |
  192.168.1.20                                |  (HTTP)    |
  +------------+                              |            |
  | Port 54322 |------------------------------|            |
  +------------+        Connection 2          |            |
                                              |            |
  Client A (2nd conn)                         |            |
  192.168.1.10                                |            |
  +------------+                              |            |
  | Port 54323 |------------------------------|            |
  +------------+        Connection 3          +------------+


  Each connection is uniquely identified by:
  +-----------------------------------------------------------+
  |  (Source IP, Source Port, Dest IP, Dest Port, Protocol)   |
  |                                                           |
  |  Conn 1: (192.168.1.10, 54321, 10.0.0.1, 80, TCP)         |
  |  Conn 2: (192.168.1.20, 54322, 10.0.0.1, 80, TCP)         |
  |  Conn 3: (192.168.1.10, 54323, 10.0.0.1, 80, TCP)         |
  +-----------------------------------------------------------+
```

---

## Sockets & Channels

### What is a Socket?

A **socket** is an endpoint for communication. It's the programming interface (API) that applications use to send and receive data over the network.

```
+------------------------------------------------------------------+
|                      SOCKET CONCEPT                              |
+------------------------------------------------------------------+

    +------------------------------------------------------------+
    |                    APPLICATION                             |
    |                                                            |
    |    read()  write()  connect()  accept()  bind()            |
    +----------------------------+-------------------------------+
                                 |
                                 v
    +------------------------------------------------------------+
    |                      SOCKET                                |
    |    +--------------------------------------------------+    |
    |    |  File Descriptor (integer)                       |    |
    |    |  Protocol (TCP/UDP)                              |    |
    |    |  Local Address (IP:Port)                         |    |
    |    |  Remote Address (IP:Port) - if connected         |    |
    |    |  State (LISTEN, ESTABLISHED, etc.)               |    |
    |    |  Send Buffer                                     |    |
    |    |  Receive Buffer                                  |    |
    |    +--------------------------------------------------+    |
    +----------------------------+-------------------------------+
                                 |
                                 v
    +------------------------------------------------------------+
    |                 KERNEL NETWORK STACK                       |
    +------------------------------------------------------------+
```

### Socket Types

```
+------------------------------------------------------------------+
|                      SOCKET TYPES                                |
+------------------------------+-----------------------------------+
|       STREAM SOCKET          |        DATAGRAM SOCKET            |
|        (SOCK_STREAM)         |         (SOCK_DGRAM)              |
+------------------------------+-----------------------------------+
|  - Connection-oriented       |  - Connectionless                 |
|  - Uses TCP                  |  - Uses UDP                       |
|  - Reliable                  |  - Unreliable                     |
|  - Ordered delivery          |  - No ordering guarantee          |
|  - Byte stream               |  - Message boundaries             |
|                              |                                   |
|  Use for:                    |  Use for:                         |
|  - HTTP, SSH, Database       |  - DNS, Gaming, Streaming         |
|  - File transfer             |  - Real-time applications         |
+------------------------------+-----------------------------------+
```

### Server Socket Lifecycle

```
+------------------------------------------------------------------+
|                 SERVER SOCKET LIFECYCLE                          |
+------------------------------------------------------------------+

     +------------------------------------------------------+
     |                    socket()                          |
     |         Create a new socket file descriptor          |
     +---------------------------+--------------------------+
                                 |
                                 v
     +------------------------------------------------------+
     |                     bind()                           |
     |      Associate socket with IP address and port       |
     |         bind(socket, 0.0.0.0:8080)                   |
     +---------------------------+--------------------------+
                                 |
                                 v
     +------------------------------------------------------+
     |                    listen()                          |
     |     Mark socket as passive (ready to accept)         |
     |     Set backlog queue size for pending connections   |
     +---------------------------+--------------------------+
                                 |
                                 v
     +------------------------------------------------------+
     |                    accept()                          |<----+
     |     Block until client connects                      |     |
     |     Returns NEW socket for the connection            |     |
     +---------------------------+--------------------------+     |
                                 |                                |
                                 v                                |
     +------------------------------------------------------+     |
     |              read() / write()                        |     |
     |     Exchange data with the connected client          |     |
     +---------------------------+--------------------------+     |
                                 |                                |
                                 v                                |
     +------------------------------------------------------+     |
     |                    close()                           |     |
     |     Close the client connection socket               |-----+
     |     (Server socket continues listening)              |
     +------------------------------------------------------+
```

### Client Socket Lifecycle

```
+------------------------------------------------------------------+
|                 CLIENT SOCKET LIFECYCLE                          |
+------------------------------------------------------------------+

     +------------------------------------------------------+
     |                    socket()                          |
     |         Create a new socket file descriptor          |
     +---------------------------+--------------------------+
                                 |
                                 v
     +------------------------------------------------------+
     |                   connect()                          |
     |     Initiate connection to server                    |
     |     OS assigns ephemeral port automatically          |
     |     Performs TCP 3-way handshake                     |
     +---------------------------+--------------------------+
                                 |
                                 v
     +------------------------------------------------------+
     |              read() / write()                        |
     |     Exchange data with the server                    |
     +---------------------------+--------------------------+
                                 |
                                 v
     +------------------------------------------------------+
     |                    close()                           |
     |     Terminate the connection                         |
     |     Performs TCP 4-way termination                   |
     +------------------------------------------------------+
```

### Channels (NIO / Non-blocking I/O)

Channels are a higher-level abstraction over sockets, commonly used in Java NIO and similar frameworks:

```
+------------------------------------------------------------------+
|              CHANNELS vs TRADITIONAL SOCKETS                     |
+------------------------------------------------------------------+

  TRADITIONAL (Blocking I/O)          CHANNELS (Non-blocking I/O)
  --------------------------          -----------------------------

  +-------------+                     +-------------+
  |  Thread 1   |--> Socket 1         |   Thread    |--> Selector
  +-------------+                     +-------------+       |
  +-------------+                           +---------------+---------------+
  |  Thread 2   |--> Socket 2               |               |               |
  +-------------+                           v               v               v
  +-------------+                     +----------+   +----------+   +----------+
  |  Thread 3   |--> Socket 3         |Channel 1 |   |Channel 2 |   |Channel 3 |
  +-------------+                     +----------+   +----------+   +----------+

  - One thread per connection          - One thread handles many connections
  - Thread blocks on read/write        - Non-blocking operations
  - Simple but doesn't scale           - Complex but highly scalable
  - 10K connections = 10K threads      - 10K connections = few threads
```

### Selector Pattern (Event-driven I/O)

```
+------------------------------------------------------------------+
|                    SELECTOR PATTERN                              |
+------------------------------------------------------------------+

                        +-----------------+
                        |    Selector     |
                        |  (Event Loop)   |
                        +--------+--------+
                                 |
                    +------------+------------+
                    |            |            |
                    v            v            v
              +----------+ +----------+ +----------+
              |Channel A | |Channel B | |Channel C |
              |OP_READ   | |OP_WRITE  | |OP_ACCEPT |
              +----------+ +----------+ +----------+


  Event Loop:
  +-------------------------------------------------------------+
  |  while (running) {                                          |
  |      int ready = selector.select();  // Block until events  |
  |                                                             |
  |      for (SelectionKey key : selector.selectedKeys()) {     |
  |          if (key.isAcceptable()) {                          |
  |              // Accept new connection                       |
  |          }                                                  |
  |          if (key.isReadable()) {                            |
  |              // Read data from channel                      |
  |          }                                                  |
  |          if (key.isWritable()) {                            |
  |              // Write data to channel                       |
  |          }                                                  |
  |      }                                                      |
  |  }                                                          |
  +-------------------------------------------------------------+
```

---

## OS Networking Internals

### Kernel Network Stack Architecture

```
+------------------------------------------------------------------+
|                    USER SPACE                                    |
|  +----------------------------------------------------------+    |
|  |                    Application                           |    |
|  |               (uses socket API)                          |    |
|  +----------------------------------------------------------+    |
+------------------------------------------------------------------+
                              |
                    System Call Interface
                              | socket(), bind(), connect(),
                              | listen(), accept(), read(), write()
                              v
+------------------------------------------------------------------+
|                    KERNEL SPACE                                  |
|  +----------------------------------------------------------+    |
|  |              Socket Layer (BSD Sockets)                  |    |
|  |    +------------------------------------------------+    |    |
|  |    |  - Socket structures & file descriptors        |    |    |
|  |    |  - Protocol family dispatch                    |    |    |
|  |    |  - Socket options                              |    |    |
|  |    +------------------------------------------------+    |    |
|  +----------------------------------------------------------+    |
|                              |                                   |
|  +----------------------------------------------------------+    |
|  |              Transport Layer (TCP/UDP)                   |    |
|  |    +------------------------------------------------+    |    |
|  |    |  - Connection management (TCP)                 |    |    |
|  |    |  - Sequence numbers & acknowledgments          |    |    |
|  |    |  - Send/receive buffers                        |    |    |
|  |    |  - Congestion control                          |    |    |
|  |    |  - Port demultiplexing                         |    |    |
|  |    +------------------------------------------------+    |    |
|  +----------------------------------------------------------+    |
|                              |                                   |
|  +----------------------------------------------------------+    |
|  |                Network Layer (IP)                        |    |
|  |    +------------------------------------------------+    |    |
|  |    |  - Routing table lookup                        |    |    |
|  |    |  - IP fragmentation/reassembly                 |    |    |
|  |    |  - Netfilter hooks (firewall)                  |    |    |
|  |    +------------------------------------------------+    |    |
|  +----------------------------------------------------------+    |
|                              |                                   |
|  +----------------------------------------------------------+    |
|  |              Device Driver Layer                         |    |
|  |    +------------------------------------------------+    |    |
|  |    |  - Network interface queues                    |    |    |
|  |    |  - DMA management                              |    |    |
|  |    |  - Interrupt handling                          |    |    |
|  |    |  - Ring buffers                                |    |    |
|  |    +------------------------------------------------+    |    |
|  +----------------------------------------------------------+    |
+------------------------------------------------------------------+
                              |
                              v
+------------------------------------------------------------------+
|                    HARDWARE                                      |
|  +----------------------------------------------------------+    |
|  |              Network Interface Card (NIC)                |    |
|  +----------------------------------------------------------+    |
+------------------------------------------------------------------+
```

### File Descriptor & Socket Relationship

```
+------------------------------------------------------------------+
|               FILE DESCRIPTOR TABLE (Per Process)                |
+------------------------------------------------------------------+

  Process
  +-------------------------------------------------------------+
  |  fd 0  ------> stdin                                        |
  |  fd 1  ------> stdout                                       |
  |  fd 2  ------> stderr                                       |
  |  fd 3  ------> +----------------------------------------------+
  |                |  Socket Structure                            |
  |                |  +----------------------------------------+  |
  |                |  | Type: SOCK_STREAM                      |  |
  |                |  | Protocol: TCP                          |  |
  |                |  | Local:  192.168.1.10:54321             |  |
  |                |  | Remote: 10.0.0.1:80                    |  |
  |                |  | State: ESTABLISHED                     |  |
  |                |  | Send Buffer: [...]                     |  |
  |                |  | Recv Buffer: [...]                     |  |
  |                |  +----------------------------------------+  |
  |                +----------------------------------------------+
  |  fd 4  ------> Regular file (/home/user/data.txt)           |
  |  fd 5  ------> Another socket...                            |
  +-------------------------------------------------------------+

  Note: Sockets are treated as file descriptors in Unix
        Same read()/write() calls work for files and sockets
```

### Socket Buffers

```
+------------------------------------------------------------------+
|                    SOCKET BUFFERS                                |
+------------------------------------------------------------------+

                        Application
                            |
              +-------------+-------------+
              |                           |
         write()                       read()
              |                           |
              v                           |
  +-----------------------+   +-----------------------+
  |     SEND BUFFER       |   |    RECEIVE BUFFER     |
  |                       |   |                       |
  |  +-+-+-+-+-+-+-+-+    |   |    +-+-+-+-+-+-+-+-+  |
  |  |#|#|#|#| | | | |    |   |    |#|#|#|#|#| | | |  |
  |  +-+-+-+-+-+-+-+-+    |   |    +-+-+-+-+-+-+-+-+  |
  |   |         |         |   |     |           ^     |
  |   |         +- Space  |   |     |           |     |
  |   |            for    |   |     |     Data from   |
  |   +-- Data waiting    |   |     |     network     |
  |       to be sent      |   |     +-- Data waiting  |
  |                       |   |         for app       |
  +-----------+-----------+   +-----------------------+
              |                           ^
              v                           |
  +-------------------------------------------------------+
  |                 TCP/IP Stack                          |
  +-------------------------------------------------------+
              |                           ^
              v                           |
  +-------------------------------------------------------+
  |                 Network (NIC)                         |
  +-------------------------------------------------------+


  Buffer Behavior:
  ----------------
  - write() copies data to send buffer, returns immediately
    (unless buffer is full -> blocks or returns EAGAIN)

  - Kernel sends data from send buffer to network
    at its own pace (handles retransmissions)

  - Incoming data from network goes to receive buffer

  - read() copies data from receive buffer to application
    (blocks if buffer empty, or returns EAGAIN)
```

### Connection Tracking Table

```
+------------------------------------------------------------------+
|              KERNEL CONNECTION TRACKING TABLE                    |
+------------------------------------------------------------------+

  The kernel maintains a hash table for quick connection lookup:

  +----------------------------------------------------------------+
  |  Key: (Proto, SrcIP, SrcPort, DstIP, DstPort)                  |
  |  Value: Pointer to socket structure                            |
  +----------------------------------------------------------------+

  Hash Table:
  +--------+------------------------------------------------------+
  | Bucket | Entries                                              |
  +--------+------------------------------------------------------+
  |   0    | (TCP,10.0.0.1,80,192.168.1.5,54321) -> sock_A        |
  |        | (TCP,10.0.0.1,80,192.168.1.6,54322) -> sock_B        |
  +--------+------------------------------------------------------+
  |   1    | (empty)                                              |
  +--------+------------------------------------------------------+
  |   2    | (TCP,10.0.0.1,443,192.168.1.7,54323) -> sock_C       |
  +--------+------------------------------------------------------+
  |  ...   | ...                                                  |
  +--------+------------------------------------------------------+

  When packet arrives:
  1. Extract 5-tuple from packet headers
  2. Hash the 5-tuple -> bucket index
  3. Search bucket for matching entry
  4. Deliver packet to associated socket buffer
```

---

## Data Flow Through the Stack

### Sending Data (Outbound)

```
+------------------------------------------------------------------+
|                 OUTBOUND DATA FLOW                               |
+------------------------------------------------------------------+

  Application: write(socket_fd, "Hello", 5)
              |
              v
  +-------------------------------------------------------------+
  | 1. SOCKET LAYER                                             |
  |    - Validate socket state                                  |
  |    - Copy data from user space to kernel send buffer        |
  |    - Return to application (may block if buffer full)       |
  +-----------------------------+-------------------------------+
                                |
                                v
  +-------------------------------------------------------------+
  | 2. TCP LAYER                                                |
  |    - Segment data (MSS = ~1460 bytes)                       |
  |    - Add TCP header (src port, dst port, seq, ack, flags)   |
  |    - Calculate checksum                                     |
  |    - Add to retransmission queue                            |
  |    - Apply congestion control                               |
  +-----------------------------+-------------------------------+
                                |
  +--------------------------------------------------------------+
  | TCP Segment                                                  |
  | +----------------------------------------------------------+ |
  | | TCP Header (20+ bytes) | "Hello" (data)                  | |
  | +----------------------------------------------------------+ |
  +--------------------------------------------------------------+
                                |
                                v
  +-------------------------------------------------------------+
  | 3. IP LAYER                                                 |
  |    - Route lookup (which interface/gateway)                 |
  |    - Add IP header (src IP, dst IP, TTL, protocol)          |
  |    - Fragment if necessary (MTU check)                      |
  |    - Apply netfilter rules (firewall)                       |
  +-----------------------------+-------------------------------+
                                |
  +--------------------------------------------------------------+
  | IP Packet                                                    |
  | +---------------------------------------------------------+  |
  | | IP Hdr | TCP Header | "Hello"                           |  |
  | +---------------------------------------------------------+  |
  +--------------------------------------------------------------+
                                |
                                v
  +-------------------------------------------------------------+
  | 4. DATA LINK LAYER (Ethernet)                               |
  |    - ARP lookup (get MAC for next hop)                      |
  |    - Add Ethernet header (src MAC, dst MAC, type)           |
  |    - Add FCS (frame check sequence / CRC)                   |
  +-----------------------------+-------------------------------+
                                |
  +--------------------------------------------------------------+
  | Ethernet Frame                                               |
  | +---------------------------------------------------------+  |
  | | Eth Hdr | IP Hdr | TCP Hdr | "Hello" | FCS              |  |
  | | 14 B    | 20 B   | 20 B    | 5 B     | 4 B              |  |
  | +---------------------------------------------------------+  |
  +--------------------------------------------------------------+
                                |
                                v
  +-------------------------------------------------------------+
  | 5. DEVICE DRIVER / NIC                                      |
  |    - Queue frame in transmit ring buffer                    |
  |    - DMA transfer to NIC hardware                           |
  |    - NIC transmits on wire                                  |
  |    - Interrupt when complete (for cleanup)                  |
  +-------------------------------------------------------------+
                                |
                                v
                            [Network Wire]
```

### Receiving Data (Inbound)

```
+------------------------------------------------------------------+
|                 INBOUND DATA FLOW                                |
+------------------------------------------------------------------+

                            [Network Wire]
                                  |
                                  v
  +-------------------------------------------------------------+
  | 1. NIC / DEVICE DRIVER                                      |
  |    - NIC receives electrical signals                        |
  |    - DMA transfers frame to memory (ring buffer)            |
  |    - NIC raises interrupt (or NAPI polling)                 |
  |    - Driver creates sk_buff (socket buffer structure)       |
  +-----------------------------+-------------------------------+
                                |
                                v
  +-------------------------------------------------------------+
  | 2. DATA LINK LAYER                                          |
  |    - Validate FCS (drop if corrupt)                         |
  |    - Check destination MAC (is it for us?)                  |
  |    - Strip Ethernet header                                  |
  |    - Determine next protocol (IP, ARP, etc.)                |
  +-----------------------------+-------------------------------+
                                |
                                v
  +-------------------------------------------------------------+
  | 3. IP LAYER                                                 |
  |    - Validate IP header checksum                            |
  |    - Check destination IP (is it for us? forward?)          |
  |    - Apply netfilter rules (firewall, NAT)                  |
  |    - Reassemble fragments if needed                         |
  |    - Strip IP header                                        |
  |    - Determine transport protocol (TCP/UDP)                 |
  +-----------------------------+-------------------------------+
                                |
                                v
  +-------------------------------------------------------------+
  | 4. TCP LAYER                                                |
  |    - Validate TCP checksum                                  |
  |    - Lookup connection (5-tuple hash)                       |
  |    - Process based on TCP state machine                     |
  |    - Handle SYN/ACK/FIN flags                               |
  |    - Sequence number validation                             |
  |    - Send ACK back                                          |
  |    - Place data in socket receive buffer (in order)         |
  +-----------------------------+-------------------------------+
                                |
                                v
  +-------------------------------------------------------------+
  | 5. SOCKET LAYER                                             |
  |    - Wake up any blocked readers                            |
  |    - Data sits in receive buffer                            |
  +-----------------------------+-------------------------------+
                                |
                                v
              Application: read(socket_fd, buffer, size)
              - Copies data from kernel receive buffer to user buffer
              - Returns number of bytes read
```

---

## Summary Cheat Sheet

```
+------------------------------------------------------------------+
|                    QUICK REFERENCE                               |
+------------------------------------------------------------------+
|                                                                  |
|  TCP Connection = 5-tuple:                                       |
|    (Protocol, Src IP, Src Port, Dst IP, Dst Port)                |
|                                                                  |
|  Socket = Kernel data structure + File Descriptor                |
|           Endpoint for network communication                     |
|                                                                  |
|  Port = 16-bit number identifying a service/process              |
|         0-1023: Well-known (root required)                       |
|         1024-49151: Registered                                   |
|         49152-65535: Dynamic/Ephemeral                           |
|                                                                  |
|  Channel = Abstraction over socket for non-blocking I/O          |
|            Used with Selector for event-driven programming       |
|                                                                  |
|  Key OS Components:                                              |
|    - Socket Layer: API & socket structures                       |
|    - Transport Layer: TCP/UDP implementation                     |
|    - Network Layer: IP routing & addressing                      |
|    - Device Driver: NIC communication                            |
|                                                                  |
|  Key Kernel Structures:                                          |
|    - File Descriptor Table (per process)                         |
|    - Socket structures (protocol, state, buffers)                |
|    - Connection hash table (5-tuple -> socket)                   |
|    - Send/Receive buffers (per socket)                           |
|    - sk_buff (packet representation)                             |
|                                                                  |
+------------------------------------------------------------------+
```

---

## TCP Connection vs Socket vs HTTP: Key Differences

This is a common source of confusion. Let's clarify the relationship:

### The Hierarchy

```
+------------------------------------------------------------------+
|                    CONCEPTUAL HIERARCHY                          |
+------------------------------------------------------------------+

  +----------------------------------------------------------+
  |                      HTTP (Layer 7)                      |
  |   Application Protocol - defines message format/semantics |
  |   "GET /index.html HTTP/1.1"                             |
  +----------------------------------------------------------+
                              |
                        runs over
                              |
                              v
  +----------------------------------------------------------+
  |                 TCP CONNECTION (Layer 4)                 |
  |   Logical communication channel between two endpoints     |
  |   Identified by: (SrcIP, SrcPort, DstIP, DstPort)        |
  +----------------------------------------------------------+
                              |
                       accessed via
                              |
                              v
  +----------------------------------------------------------+
  |                    SOCKET (API/Handle)                   |
  |   Programming interface to access the connection          |
  |   A file descriptor (integer) in your process            |
  +----------------------------------------------------------+
```

### Socket vs Connection: The Key Insight

**A socket is NOT inside a connection. A socket is the HANDLE/API to access a connection.**

Think of it this way:

```
+------------------------------------------------------------------+
|                    ANALOGY: PHONE CALL                           |
+------------------------------------------------------------------+

  Phone Call = TCP Connection
      - The actual communication channel
      - Exists between two parties
      - Has state (ringing, connected, ended)

  Phone Handset = Socket
      - The device you use to access the call
      - You speak into it, listen from it
      - Multiple handsets can exist (extensions)
      - The handset is not "inside" the call

  Conversation Protocol = HTTP
      - The language/format you use
      - "Hello, how are you?" "I'm fine, thanks"
      - Rules for taking turns, ending conversation
```

### Detailed Breakdown

```
+------------------------------------------------------------------+
|                         SOCKET                                   |
+------------------------------------------------------------------+
|                                                                  |
|  WHAT IT IS:                                                     |
|  - A kernel data structure + file descriptor                     |
|  - Programming API endpoint                                      |
|  - Handle for your process to read/write network data            |
|                                                                  |
|  WHAT IT IS NOT:                                                 |
|  - NOT the connection itself                                     |
|  - NOT a physical thing                                          |
|                                                                  |
|  LIFECYCLE:                                                      |
|  - Created by socket() system call                               |
|  - Can exist WITHOUT a connection (before connect/accept)        |
|  - Can be bound to address without connection (UDP)              |
|  - Destroyed by close()                                          |
|                                                                  |
|  CONTAINS:                                                       |
|  - Local address (IP:Port)                                       |
|  - Remote address (IP:Port) - if connected                       |
|  - Protocol type (TCP/UDP)                                       |
|  - State (LISTEN, ESTABLISHED, etc.)                             |
|  - Send buffer (outgoing data queue)                             |
|  - Receive buffer (incoming data queue)                          |
|  - Socket options (timeouts, buffer sizes, etc.)                 |
|                                                                  |
+------------------------------------------------------------------+
```

```
+------------------------------------------------------------------+
|                     TCP CONNECTION                               |
+------------------------------------------------------------------+
|                                                                  |
|  WHAT IT IS:                                                     |
|  - Logical bidirectional communication channel                   |
|  - Stateful session between two endpoints                        |
|  - Guarantees reliable, ordered delivery                         |
|                                                                  |
|  IDENTIFIED BY (5-tuple):                                        |
|  - Protocol (TCP)                                                |
|  - Source IP                                                     |
|  - Source Port                                                   |
|  - Destination IP                                                |
|  - Destination Port                                              |
|                                                                  |
|  LIFECYCLE:                                                      |
|  - Established via 3-way handshake (SYN, SYN-ACK, ACK)           |
|  - Maintained with keep-alives and ACKs                          |
|  - Terminated via 4-way handshake (FIN, ACK, FIN, ACK)           |
|                                                                  |
|  STATE MACHINE:                                                  |
|  - LISTEN, SYN_SENT, SYN_RCVD, ESTABLISHED                       |
|  - FIN_WAIT_1, FIN_WAIT_2, CLOSE_WAIT, LAST_ACK                  |
|  - TIME_WAIT, CLOSED                                             |
|                                                                  |
|  PROVIDES:                                                       |
|  - Reliable delivery (retransmissions)                           |
|  - Ordered delivery (sequence numbers)                           |
|  - Flow control (window size)                                    |
|  - Congestion control                                            |
|                                                                  |
+------------------------------------------------------------------+
```

```
+------------------------------------------------------------------+
|                     HTTP PROTOCOL                                |
+------------------------------------------------------------------+
|                                                                  |
|  WHAT IT IS:                                                     |
|  - Application-layer protocol (Layer 7)                          |
|  - Defines message FORMAT and SEMANTICS                          |
|  - Request-Response model                                        |
|  - Stateless (each request independent)                          |
|                                                                  |
|  RUNS OVER:                                                      |
|  - TCP (HTTP/1.0, HTTP/1.1, HTTP/2)                              |
|  - QUIC/UDP (HTTP/3)                                             |
|                                                                  |
|  MESSAGE FORMAT:                                                 |
|  +----------------------------------------------------------+    |
|  | Request:                                                 |    |
|  |   GET /index.html HTTP/1.1                               |    |
|  |   Host: example.com                                      |    |
|  |   User-Agent: Mozilla/5.0                                |    |
|  |   Accept: text/html                                      |    |
|  |   <blank line>                                           |    |
|  |   [optional body]                                        |    |
|  +----------------------------------------------------------+    |
|  | Response:                                                |    |
|  |   HTTP/1.1 200 OK                                        |    |
|  |   Content-Type: text/html                                |    |
|  |   Content-Length: 1234                                   |    |
|  |   <blank line>                                           |    |
|  |   <html>...</html>                                       |    |
|  +----------------------------------------------------------+    |
|                                                                  |
|  KNOWS NOTHING ABOUT:                                            |
|  - TCP connections (abstracted away)                             |
|  - Sockets (programming detail)                                  |
|  - IP addresses (handled by lower layers)                        |
|                                                                  |
+------------------------------------------------------------------+
```

### How They Work Together

```
+------------------------------------------------------------------+
|              COMPLETE PICTURE: HTTP REQUEST FLOW                 |
+------------------------------------------------------------------+

  YOUR APPLICATION (e.g., curl, browser, Python requests)
       |
       | 1. Application wants to fetch http://example.com/page
       v
  +----------------------------------------------------------+
  |                    HTTP LAYER                            |
  |  Formats the request:                                    |
  |  "GET /page HTTP/1.1\r\nHost: example.com\r\n\r\n"       |
  +----------------------------------------------------------+
       |
       | 2. HTTP gives this string to the socket
       v
  +----------------------------------------------------------+
  |                    SOCKET (API)                          |
  |  socket_fd = socket(AF_INET, SOCK_STREAM, 0)             |
  |  connect(socket_fd, "93.184.216.34:80")                  |
  |  write(socket_fd, http_request_string)                   |
  |  read(socket_fd, response_buffer)                        |
  +----------------------------------------------------------+
       |
       | 3. Socket operations trigger TCP layer
       v
  +----------------------------------------------------------+
  |                 TCP CONNECTION                           |
  |  - 3-way handshake establishes connection                |
  |  - Segments the HTTP data                                |
  |  - Adds sequence numbers                                 |
  |  - Handles retransmissions                               |
  |  - Receives ACKs from server                             |
  +----------------------------------------------------------+
       |
       | 4. TCP hands off to IP layer
       v
  +----------------------------------------------------------+
  |                    IP LAYER                              |
  |  Routes packets to 93.184.216.34                         |
  +----------------------------------------------------------+
       |
       v
  [  Network  ] -----> [  Server  ]
```

### Multiple HTTP Requests Over One TCP Connection

```
+------------------------------------------------------------------+
|           HTTP/1.1 KEEP-ALIVE (Connection Reuse)                 |
+------------------------------------------------------------------+

  ONE TCP CONNECTION (socket fd=5)
  +--------------------------------------------------------------+
  |                                                              |
  |  Time -->                                                    |
  |                                                              |
  |  [TCP Handshake] --> [HTTP Req 1] --> [HTTP Resp 1]          |
  |                      [HTTP Req 2] --> [HTTP Resp 2]          |
  |                      [HTTP Req 3] --> [HTTP Resp 3]          |
  |                      ...                                     |
  |                      [HTTP Req N] --> [HTTP Resp N]          |
  |                                                              |
  |  [TCP Termination]                                           |
  |                                                              |
  +--------------------------------------------------------------+

  - Single socket, single TCP connection
  - Multiple HTTP request/response pairs
  - More efficient than connection-per-request
```

### One Socket, Multiple Connections? NO!

```
+------------------------------------------------------------------+
|           COMMON MISCONCEPTION CLARIFIED                         |
+------------------------------------------------------------------+

  WRONG: "One socket handles multiple connections"

      Socket ----+----> Connection 1
                 +----> Connection 2    <-- NOT how it works!
                 +----> Connection 3

  CORRECT: "One socket = One connection (for connected sockets)"

      Socket A --------> Connection 1
      Socket B --------> Connection 2
      Socket C --------> Connection 3


  SPECIAL CASE: Server LISTENING Socket

      Listening Socket (fd=3, port 80)
             |
             | accept() creates NEW socket for each connection
             |
             +----> accept() returns fd=4 --> Connection from Client A
             +----> accept() returns fd=5 --> Connection from Client B
             +----> accept() returns fd=6 --> Connection from Client C

      Listening socket does NOT have a connection
      It creates NEW sockets that have connections
```

### Summary Comparison Table

```
+------------------------------------------------------------------+
|                    COMPARISON TABLE                              |
+----------+-----------------+------------------+-------------------+
| Aspect   | Socket          | TCP Connection   | HTTP              |
+----------+-----------------+------------------+-------------------+
| Layer    | API (spans      | Transport (L4)   | Application (L7)  |
|          | L4-L7)          |                  |                   |
+----------+-----------------+------------------+-------------------+
| What     | Handle/API      | Communication    | Message format    |
|          | to access       | channel          | and semantics     |
|          | network         |                  |                   |
+----------+-----------------+------------------+-------------------+
| Created  | socket()        | 3-way handshake  | N/A (it's a       |
| by       | system call     | (SYN/SYN-ACK/    | protocol spec)    |
|          |                 | ACK)             |                   |
+----------+-----------------+------------------+-------------------+
| Exists   | In kernel +     | Between two      | In the data       |
| where    | file descriptor | network hosts    | being sent        |
|          | in process      |                  |                   |
+----------+-----------------+------------------+-------------------+
| Lifetime | Process-bound   | Until terminated | Per request       |
|          |                 | or timeout       | (stateless)       |
+----------+-----------------+------------------+-------------------+
| State    | Kernel struct   | TCP state        | Stateless         |
|          | with buffers    | machine          | (cookies/sessions |
|          |                 |                  | add state)        |
+----------+-----------------+------------------+-------------------+
| Without  | Can exist       | Can exist        | Cannot exist      |
| others   | without TCP     | without HTTP     | without TCP       |
|          | connection      | (e.g., SSH uses  | (needs transport) |
|          | (UDP socket)    | TCP)             |                   |
+----------+-----------------+------------------+-------------------+
```

### Code Example: Seeing All Three

```
+------------------------------------------------------------------+
|                    PYTHON EXAMPLE                                |
+------------------------------------------------------------------+

  import socket

  # 1. CREATE SOCKET (just an API handle, no connection yet)
  sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
  print(f"Socket created: {sock.fileno()}")  # e.g., 3

  # 2. ESTABLISH TCP CONNECTION (3-way handshake happens here)
  sock.connect(("example.com", 80))
  print("TCP connection established")

  # 3. SEND HTTP REQUEST (application protocol over the connection)
  http_request = b"GET / HTTP/1.1\r\nHost: example.com\r\n\r\n"
  sock.send(http_request)

  # 4. RECEIVE HTTP RESPONSE
  response = sock.recv(4096)
  print(response.decode())

  # 5. CLOSE (terminates TCP connection, releases socket)
  sock.close()

  # Timeline:
  # socket()  --> Socket exists, NO connection
  # connect() --> Socket + TCP Connection exist
  # send()    --> HTTP data flows over TCP via Socket
  # close()   --> Connection terminated, Socket released
```

---

## Related Topics
- Network programming in various languages (Java NIO, Python asyncio, Go net)
- Advanced topics: epoll, kqueue, io_uring, DPDK
