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

### What is a File Descriptor?

A **file descriptor (fd)** is simply an **integer** that acts as a handle/index to access kernel resources. It's NOT the resource itself - it's a reference to it.

```
+------------------------------------------------------------------+
|                    FILE DESCRIPTOR EXPLAINED                     |
+------------------------------------------------------------------+

  WHAT IT IS:
  - A small non-negative integer (0, 1, 2, 3, 4, ...)
  - An INDEX into the process's file descriptor table
  - A HANDLE to access kernel-managed resources

  WHAT IT IS NOT:
  - NOT the actual file or socket data
  - NOT a pointer to memory
  - NOT unique across processes (fd=5 in Process A != fd=5 in Process B)

  ANALOGY:
  - File Descriptor = Coat check ticket number
  - Kernel Resource = Your actual coat
  - You give the ticket (fd) to get/use your coat (resource)
```

### File Descriptor Table Architecture

```
+------------------------------------------------------------------+
|            HOW FILE DESCRIPTORS WORK IN THE KERNEL               |
+------------------------------------------------------------------+

  PROCESS A (PID 1234)
  +------------------------------------------------------------------+
  |                                                                  |
  |  Process Control Block (PCB)                                     |
  |  +------------------------------------------------------------+  |
  |  |  PID: 1234                                                 |  |
  |  |  State: RUNNING                                            |  |
  |  |  ...                                                       |  |
  |  |  File Descriptor Table Pointer  ------+                    |  |
  |  +------------------------------------------------------------+  |
  |                                          |                       |
  +------------------------------------------|-----------------------+
                                             |
                                             v
  +------------------------------------------------------------------+
  |              FILE DESCRIPTOR TABLE (per process)                 |
  +------------------------------------------------------------------+
  |                                                                  |
  |  Index   Flags        Pointer to Open File Description           |
  |  (fd)                                                            |
  |  +-----+----------+------------------------------------------+   |
  |  |  0  | O_RDONLY | -----> Open File Description (stdin)     |   |
  |  +-----+----------+------------------------------------------+   |
  |  |  1  | O_WRONLY | -----> Open File Description (stdout)    |   |
  |  +-----+----------+------------------------------------------+   |
  |  |  2  | O_WRONLY | -----> Open File Description (stderr)    |   |
  |  +-----+----------+------------------------------------------+   |
  |  |  3  | O_RDWR   | -----> Open File Description (socket)  --+---|---+
  |  +-----+----------+------------------------------------------+   |   |
  |  |  4  | O_RDONLY | -----> Open File Description (file)      |   |   |
  |  +-----+----------+------------------------------------------+   |   |
  |  | ... |   ...    |  ...                                     |   |   |
  |  +-----+----------+------------------------------------------+   |   |
  |                                                                  |   |
  +------------------------------------------------------------------+   |
                                                                         |
              +----------------------------------------------------------+
              |
              v
  +------------------------------------------------------------------+
  |              OPEN FILE DESCRIPTION (in kernel)                   |
  |              (also called "file object" or "file structure")     |
  +------------------------------------------------------------------+
  |                                                                  |
  |  +------------------------------------------------------------+  |
  |  |  File Offset (current position): 0                         |  |
  |  |  Access Mode: O_RDWR                                       |  |
  |  |  Status Flags: O_NONBLOCK                                  |  |
  |  |  Reference Count: 1                                        |  |
  |  |  Pointer to Inode/Socket -------+                          |  |
  |  +------------------------------------------------------------+  |
  |                                    |                             |
  +------------------------------------|-----------------------------|
                                       |
                                       v
  +------------------------------------------------------------------+
  |              SOCKET STRUCTURE (for network fd)                   |
  +------------------------------------------------------------------+
  |                                                                  |
  |  +------------------------------------------------------------+  |
  |  |  Type: SOCK_STREAM                                         |  |
  |  |  Protocol: IPPROTO_TCP                                     |  |
  |  |  State: ESTABLISHED                                        |  |
  |  |  Local Address: 192.168.1.10:54321                         |  |
  |  |  Remote Address: 10.0.0.1:80                               |  |
  |  |  Send Buffer: [kernel memory region]                       |  |
  |  |  Receive Buffer: [kernel memory region]                    |  |
  |  |  Socket Options: { SO_REUSEADDR, SO_KEEPALIVE, ... }       |  |
  |  |  Wait Queue: [list of waiting processes]                   |  |
  |  +------------------------------------------------------------+  |
  |                                                                  |
  +------------------------------------------------------------------+
```

### What's Inside Each Layer

```
+------------------------------------------------------------------+
|         DETAILED BREAKDOWN OF EACH COMPONENT                     |
+------------------------------------------------------------------+

1. FILE DESCRIPTOR (the integer itself)
   +--------------------------------------------------------------+
   |  Just a number: 0, 1, 2, 3, 4, 5, ...                        |
   |                                                              |
   |  - Allocated by kernel on open()/socket()/accept()           |
   |  - Always uses lowest available number                       |
   |  - Released on close()                                       |
   |  - Valid only within the process that owns it                |
   |                                                              |
   |  Default FDs:                                                |
   |    0 = stdin  (standard input)                               |
   |    1 = stdout (standard output)                              |
   |    2 = stderr (standard error)                               |
   |    3+ = your files, sockets, pipes, etc.                     |
   +--------------------------------------------------------------+

2. FILE DESCRIPTOR TABLE ENTRY (per-process)
   +--------------------------------------------------------------+
   |  struct fdtable_entry {                                      |
   |      int flags;           // FD_CLOEXEC, etc.                |
   |      struct file *file;   // pointer to open file description|
   |  }                                                           |
   |                                                              |
   |  Flags stored here:                                          |
   |    - FD_CLOEXEC: close fd on exec() call                     |
   +--------------------------------------------------------------+

3. OPEN FILE DESCRIPTION (shared, in kernel)
   +--------------------------------------------------------------+
   |  struct file {                                               |
   |      loff_t f_pos;        // current read/write offset       |
   |      unsigned int f_flags; // O_RDONLY, O_NONBLOCK, etc.     |
   |      fmode_t f_mode;      // read/write permissions          |
   |      atomic_t f_count;    // reference count                 |
   |      struct inode *f_inode;    // for files                  |
   |      struct socket *f_socket;  // for sockets                |
   |      const struct file_operations *f_op; // read/write funcs |
   |  }                                                           |
   |                                                              |
   |  This is SHARED when:                                        |
   |    - fork() duplicates fd table (parent & child share this)  |
   |    - dup()/dup2() creates alias to same description          |
   +--------------------------------------------------------------+

4. SOCKET STRUCTURE (for network file descriptors)
   +--------------------------------------------------------------+
   |  struct socket {                                             |
   |      socket_state state;      // SS_CONNECTED, etc.          |
   |      short type;              // SOCK_STREAM, SOCK_DGRAM     |
   |      struct sock *sk;         // protocol-specific socket    |
   |  }                                                           |
   |                                                              |
   |  struct sock {  // TCP-specific                              |
   |      // Connection identity                                  |
   |      __be32 saddr, daddr;     // source/dest IP              |
   |      __be16 sport, dport;     // source/dest port            |
   |                                                              |
   |      // TCP state machine                                    |
   |      int sk_state;            // TCP_ESTABLISHED, etc.       |
   |                                                              |
   |      // Buffers                                              |
   |      struct sk_buff_head sk_receive_queue;  // incoming data |
   |      struct sk_buff_head sk_write_queue;    // outgoing data |
   |                                                              |
   |      // TCP-specific fields                                  |
   |      u32 snd_una;             // oldest unacked seq          |
   |      u32 snd_nxt;             // next seq to send            |
   |      u32 rcv_nxt;             // next seq expected           |
   |      u32 snd_wnd;             // send window size            |
   |      u32 rcv_wnd;             // receive window size         |
   |                                                              |
   |      // Wait queues                                          |
   |      struct socket_wq *wq;    // processes waiting on socket |
   |  }                                                           |
   +--------------------------------------------------------------+
```

### File Descriptor Sharing Scenarios

```
+------------------------------------------------------------------+
|              FILE DESCRIPTOR SHARING                             |
+------------------------------------------------------------------+

SCENARIO 1: dup() / dup2()
-----------------------------
  fd=3 ----+
           |----> Open File Description ----> Socket
  fd=4 ----+      (shared offset, flags)

  Both fds point to SAME open file description
  close(3) doesn't close socket until close(4) too


SCENARIO 2: fork()
-----------------------------

  PARENT (before fork)
  fd=3 --------> Open File Description ----> Socket

  PARENT (after fork)          CHILD (after fork)
  fd=3 ----+                   fd=3 ----+
           |                            |
           +---> Open File Description -+----> Socket
                 (SHARED between parent & child)

  Either process can read/write
  Socket closed only when BOTH close their fd=3


SCENARIO 3: Independent open()/socket()
-----------------------------

  Process A                    Process B
  fd=3 ----> OFD A ----> Socket A
                                     fd=3 ----> OFD B ----> Socket B

  Same fd number, but COMPLETELY different resources
  fd is just an index within each process
```

### System Calls and File Descriptors

```
+------------------------------------------------------------------+
|           FILE DESCRIPTOR LIFECYCLE                              |
+------------------------------------------------------------------+

  CREATION:
  +--------------------------------------------------------------+
  |  int fd = socket(AF_INET, SOCK_STREAM, 0);                   |
  |                                                              |
  |  Kernel does:                                                |
  |  1. Find lowest unused fd in process's table (e.g., 3)       |
  |  2. Allocate Open File Description structure                 |
  |  3. Allocate Socket structure                                |
  |  4. Link: fd table[3] -> OFD -> Socket                       |
  |  5. Return 3 to user                                         |
  +--------------------------------------------------------------+

  OPERATIONS:
  +--------------------------------------------------------------+
  |  read(fd, buffer, size)                                      |
  |                                                              |
  |  Kernel does:                                                |
  |  1. Look up fd in process's fd table                         |
  |  2. Follow pointer to Open File Description                  |
  |  3. Follow pointer to Socket structure                       |
  |  4. Copy data from socket's receive buffer to user buffer    |
  |  5. Return bytes read                                        |
  +--------------------------------------------------------------+

  CLOSING:
  +--------------------------------------------------------------+
  |  close(fd)                                                   |
  |                                                              |
  |  Kernel does:                                                |
  |  1. Look up fd in process's fd table                         |
  |  2. Mark fd table entry as unused                            |
  |  3. Decrement OFD reference count                            |
  |  4. If refcount == 0:                                        |
  |     - For socket: initiate TCP FIN sequence                  |
  |     - Free socket buffers                                    |
  |     - Free OFD structure                                     |
  |  5. fd number can now be reused                              |
  +--------------------------------------------------------------+
```

### Limits on File Descriptors

```
+------------------------------------------------------------------+
|           FILE DESCRIPTOR LIMITS                                 |
+------------------------------------------------------------------+

  PER-PROCESS LIMIT (soft/hard):
  +--------------------------------------------------------------+
  |  $ ulimit -n        # show soft limit (often 1024)           |
  |  $ ulimit -Hn       # show hard limit (often 1048576)        |
  |                                                              |
  |  Can be increased in:                                        |
  |  - /etc/security/limits.conf                                 |
  |  - setrlimit() system call                                   |
  +--------------------------------------------------------------+

  SYSTEM-WIDE LIMIT:
  +--------------------------------------------------------------+
  |  $ cat /proc/sys/fs/file-max    # e.g., 9223372036854775807  |
  |  $ cat /proc/sys/fs/file-nr     # allocated, free, max       |
  +--------------------------------------------------------------+

  WHY LIMITS MATTER:
  +--------------------------------------------------------------+
  |  - Each TCP connection needs a file descriptor               |
  |  - Server with 10,000 connections = 10,000+ fds              |
  |  - "Too many open files" error = hit the limit               |
  |  - High-performance servers increase limits significantly    |
  +--------------------------------------------------------------+
```

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

  WRONG: "One connection has multiple sockets"

      Connection ----+----> Socket 1
                     +----> Socket 2    <-- Also NOT how it works!
                     +----> Socket 3

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

### Socket-Connection Relationship (Important!)

```
+------------------------------------------------------------------+
|           ONE CONNECTION = ONE SOCKET (per endpoint)             |
+------------------------------------------------------------------+

  A TCP connection has exactly TWO sockets: one on each end

  Client Side                              Server Side
  +----------+                             +----------+
  | Socket A |<===== TCP Connection ======>| Socket X |
  | (fd=5)   |                             | (fd=7)   |
  +----------+                             +----------+

  - The connection exists BETWEEN the two sockets
  - Each endpoint has exactly ONE socket for this connection
  - Total: 2 sockets (one on each side) for 1 connection


+------------------------------------------------------------------+
|           MULTIPLE CLIENTS = MULTIPLE SOCKETS                    |
+------------------------------------------------------------------+

  Server with 3 clients = 4 sockets on server (1 listening + 3 connected)

  Server Machine:
  +---------------------+
  | Listening Socket    |  fd=3, port 80
  | (NO connection)     |  Just waits for incoming connections
  +---------------------+
           |
           | accept() creates NEW socket for each client
           |
  +--------+---------+---------+
  |        |         |         |
  v        v         v         v
  fd=4     fd=5      fd=6
  |        |         |
  v        v         v
  Conn 1   Conn 2    Conn 3    <-- Each socket has ONE connection
  |        |         |
  v        v         v
  Client A Client B  Client C


  SOCKET COUNT:
  +--------------------------------------------------------------+
  | Server side: 4 sockets (1 listening + 3 connected)           |
  | Client side: 3 sockets (1 per client)                        |
  | Connections: 3 total                                         |
  | Ratio: 1 connected socket = 1 connection (always!)           |
  +--------------------------------------------------------------+


+------------------------------------------------------------------+
|           HTTP KEEP-ALIVE (Often Misunderstood)                  |
+------------------------------------------------------------------+

  People sometimes think multiple HTTP requests = multiple connections
  THIS IS WRONG!

  +----------+                             +----------+
  | Socket   |<==== 1 TCP Connection ====>| Socket   |
  | (fd=5)   |                             | (fd=7)   |
  +----------+                             +----------+
       |
       |  HTTP Request 1  ------>
       |  <------ HTTP Response 1
       |  HTTP Request 2  ------>
       |  <------ HTTP Response 2
       |  HTTP Request 3  ------>
       |  <------ HTTP Response 3
       |
  SAME socket, SAME connection, multiple HTTP requests
  (NOT multiple connections, NOT multiple sockets!)


+------------------------------------------------------------------+
|                    SUMMARY TABLE                                 |
+------------------------------------------------------------------+

  Scenario                          | Sockets      | Connections
  ----------------------------------|--------------|-------------
  1 client connects to server       | 2 (1 each    | 1
                                    |    side)     |
  ----------------------------------|--------------|-------------
  3 clients connect to server       | 4 on server  | 3
                                    | (1 listen +  |
                                    |  3 connected)|
                                    | 3 on clients |
  ----------------------------------|--------------|-------------
  HTTP keep-alive with 10 requests  | 2 (1 each    | 1
                                    |    side)     |
  ----------------------------------|--------------|-------------

  THE RULE: 1 connected socket = 1 connection (ALWAYS)
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

## Web Server Sockets: Practical Examples

When you start a web server, how many sockets are created? Let's explore with real examples.

### Server Startup: The Listening Socket

```
+------------------------------------------------------------------+
|           WHEN YOU START A WEB SERVER                            |
+------------------------------------------------------------------+

  $ uvicorn main:app --host 0.0.0.0 --port 8000

  What happens:

  1. Server process starts
  2. Creates ONE listening socket
  3. Binds it to 0.0.0.0:8000
  4. Calls listen() - now waiting for connections

  At this point:
  +--------------------------------------------------------------+
  | Sockets: 1 (the listening socket)                            |
  | Connections: 0 (no clients yet)                              |
  +--------------------------------------------------------------+

  Server Process
  +---------------------------+
  |  Listening Socket (fd=3)  |
  |  State: LISTEN            |
  |  Bound to: 0.0.0.0:8000   |
  |  NO connection            |
  +---------------------------+
```

### When Clients Connect

```
+------------------------------------------------------------------+
|           CLIENTS START CONNECTING                               |
+------------------------------------------------------------------+

  After 3 clients connect:

  Server Process
  +---------------------------+
  |  Listening Socket (fd=3)  |  <-- Still listening for MORE clients
  |  State: LISTEN            |
  |  Bound to: 0.0.0.0:8000   |
  +---------------------------+
           |
           | accept() was called 3 times
           |
  +--------+--------+--------+
  |        |        |        |
  v        v        v        v
  +------+ +------+ +------+
  | fd=4 | | fd=5 | | fd=6 |
  | ESTAB| | ESTAB| | ESTAB|
  +------+ +------+ +------+
     |        |        |
     v        v        v
  Client1  Client2  Client3

  Socket count:
  +--------------------------------------------------------------+
  | Listening socket: 1                                          |
  | Connected sockets: 3                                         |
  | TOTAL: 4 sockets                                             |
  | Connections: 3                                               |
  +--------------------------------------------------------------+
```

### FastAPI/Uvicorn Example

```
+------------------------------------------------------------------+
|           FASTAPI WITH UVICORN                                   |
+------------------------------------------------------------------+

  # main.py
  from fastapi import FastAPI
  app = FastAPI()

  @app.get("/")
  def read_root():
      return {"Hello": "World"}

  # Run with: uvicorn main:app --workers 4


  SINGLE WORKER (default):
  ========================

  $ uvicorn main:app --port 8000

  +--------------------------------------------------+
  |  Uvicorn Process (PID 1234)                      |
  |                                                  |
  |  +--------------------------------------------+  |
  |  | Listening Socket (fd=3)                   |  |
  |  | 0.0.0.0:8000                               |  |
  |  +--------------------------------------------+  |
  |                                                  |
  |  Event loop (asyncio) handles all connections   |
  |  using non-blocking I/O                         |
  |                                                  |
  |  When 100 clients connect:                      |
  |  - 1 listening socket                           |
  |  - 100 connected sockets                        |
  |  - All in ONE process                           |
  |  - Total: 101 sockets, 100 connections          |
  +--------------------------------------------------+


  MULTIPLE WORKERS:
  =================

  $ uvicorn main:app --port 8000 --workers 4

  Main Process (manages workers)
  +--------------------------------------------------+
  |                                                  |
  |  Creates listening socket, then forks workers   |
  |                                                  |
  +--------------------------------------------------+
           |
           | fork()
           |
  +--------+--------+--------+--------+
  |        |        |        |        |
  v        v        v        v        v

  Worker 1    Worker 2    Worker 3    Worker 4
  (PID 1001)  (PID 1002)  (PID 1003)  (PID 1004)
  +--------+  +--------+  +--------+  +--------+
  |fd=3    |  |fd=3    |  |fd=3    |  |fd=3    |
  |LISTEN  |  |LISTEN  |  |LISTEN  |  |LISTEN  |
  |:8000   |  |:8000   |  |:8000   |  |:8000   |
  +--------+  +--------+  +--------+  +--------+

  All 4 workers SHARE the same listening socket!
  (via fork() - they inherit the same fd)

  When connection comes in:
  - Kernel wakes up ONE worker (usually)
  - That worker calls accept()
  - Gets NEW socket for that connection
  - Other workers continue listening

  With 100 connections distributed across workers:
  +--------------------------------------------------------------+
  | Worker 1: 1 listening + ~25 connected = ~26 sockets          |
  | Worker 2: 1 listening + ~25 connected = ~26 sockets          |
  | Worker 3: 1 listening + ~25 connected = ~26 sockets          |
  | Worker 4: 1 listening + ~25 connected = ~26 sockets          |
  |                                                              |
  | Note: All 4 "listening sockets" are actually the SAME        |
  | underlying socket (shared via fork)                          |
  |                                                              |
  | Unique sockets: 1 listening + 100 connected = 101            |
  | Connections: 100                                             |
  +--------------------------------------------------------------+
```

### Gunicorn with Sync Workers

```
+------------------------------------------------------------------+
|           GUNICORN SYNC WORKERS (Thread-per-request)             |
+------------------------------------------------------------------+

  $ gunicorn --workers 4 --threads 2 main:app

  Worker 1 (PID 1001)
  +--------------------------------------------------+
  |  Listening Socket (fd=3) - shared                |
  |                                                  |
  |  Thread Pool (2 threads)                         |
  |  +------------------+  +------------------+      |
  |  | Thread 1         |  | Thread 2         |      |
  |  | Handles 1 conn   |  | Handles 1 conn   |      |
  |  | at a time        |  | at a time        |      |
  |  +------------------+  +------------------+      |
  |                                                  |
  |  Max concurrent connections per worker: 2       |
  +--------------------------------------------------+

  4 workers x 2 threads = 8 concurrent connections max

  With 8 active connections:
  +--------------------------------------------------------------+
  | Listening sockets: 1 (shared)                                |
  | Connected sockets: 8 (2 per worker)                          |
  | Total unique sockets: 9                                      |
  +--------------------------------------------------------------+
```

### Java Jersey/Tomcat Example

```
+------------------------------------------------------------------+
|           JERSEY ON TOMCAT (Thread Pool Model)                   |
+------------------------------------------------------------------+

  Tomcat uses a different model: Thread Pool with blocking I/O

  Tomcat Process (Single JVM)
  +----------------------------------------------------------+
  |                                                          |
  |  Acceptor Thread                                         |
  |  +----------------------------------------------------+  |
  |  | Listening Socket (ServerSocket)                    |  |
  |  | Bound to 0.0.0.0:8080                              |  |
  |  | Calls accept() in a loop                           |  |
  |  +----------------------------------------------------+  |
  |                                                          |
  |  When connection arrives:                                |
  |  1. accept() returns new Socket                          |
  |  2. Hand Socket to a worker thread from pool             |
  |  3. Continue accepting more connections                  |
  |                                                          |
  |  Thread Pool (default 200 threads)                       |
  |  +----------------------------------------------------+  |
  |  | Thread-1: Socket fd=101 -> Client A                |  |
  |  | Thread-2: Socket fd=102 -> Client B                |  |
  |  | Thread-3: Socket fd=103 -> Client C                |  |
  |  | ...                                                |  |
  |  | Thread-200: (idle, waiting for work)               |  |
  |  +----------------------------------------------------+  |
  |                                                          |
  +----------------------------------------------------------+

  With 150 concurrent connections:
  +--------------------------------------------------------------+
  | Listening socket: 1                                          |
  | Connected sockets: 150                                       |
  | Total sockets: 151                                           |
  | Active threads: 150 (one per connection)                     |
  | Idle threads: 50                                             |
  +--------------------------------------------------------------+

  BLOCKING vs NON-BLOCKING I/O:
  +--------------------------------------------------------------+
  | Tomcat (blocking):    1 thread per connection                |
  |                       200 threads = 200 max connections      |
  |                       Simple but doesn't scale well          |
  |                                                              |
  | Uvicorn (non-block):  1 thread handles thousands             |
  |                       Uses select/epoll/kqueue               |
  |                       Complex but scales very well           |
  +--------------------------------------------------------------+
```

### Netty/Spring WebFlux (NIO Model)

```
+------------------------------------------------------------------+
|           SPRING WEBFLUX / NETTY (Non-blocking I/O)              |
+------------------------------------------------------------------+

  Netty uses event-driven, non-blocking I/O (similar to Node.js)

  Netty Process (Single JVM)
  +----------------------------------------------------------+
  |                                                          |
  |  Boss Event Loop (1 thread typically)                    |
  |  +----------------------------------------------------+  |
  |  | Listening ServerSocketChannel                      |  |
  |  | Registered with Selector                           |  |
  |  | Handles: OP_ACCEPT events                          |  |
  |  +----------------------------------------------------+  |
  |           |                                              |
  |           | New connection -> hand to worker             |
  |           v                                              |
  |  Worker Event Loop Group (N threads, usually CPU cores)  |
  |  +----------------------------------------------------+  |
  |  | Worker 1: Selector watching 500 SocketChannels     |  |
  |  | Worker 2: Selector watching 500 SocketChannels     |  |
  |  | Worker 3: Selector watching 500 SocketChannels     |  |
  |  | Worker 4: Selector watching 500 SocketChannels     |  |
  |  +----------------------------------------------------+  |
  |                                                          |
  |  Each worker handles MANY connections (non-blocking)     |
  |                                                          |
  +----------------------------------------------------------+

  With 2000 concurrent connections on 4-core machine:
  +--------------------------------------------------------------+
  | Boss thread: 1 listening socket                              |
  | Worker threads: 4 (one per core)                             |
  | Sockets per worker: ~500                                     |
  | Total sockets: 1 + 2000 = 2001                               |
  | Total threads: 5 (1 boss + 4 workers)                        |
  |                                                              |
  | Compare to Tomcat:                                           |
  | Tomcat would need 2000 threads for 2000 connections!        |
  +--------------------------------------------------------------+
```

### Socket Scaling Summary

```
+------------------------------------------------------------------+
|           SERVER SOCKET SCALING COMPARISON                       |
+------------------------------------------------------------------+

  Server starts (no clients):
  +--------------------------------------------------------------+
  | Model              | Processes | Threads | Sockets            |
  +--------------------+-----------+---------+--------------------+
  | Uvicorn (1 worker) | 1         | 1       | 1 listening        |
  | Uvicorn (4 workers)| 4         | 4       | 1 shared listening |
  | Gunicorn sync      | 4         | 8       | 1 shared listening |
  | Tomcat             | 1         | ~10     | 1 listening        |
  | Netty              | 1         | 5       | 1 listening        |
  +--------------------------------------------------------------+


  With 1000 concurrent connections:
  +--------------------------------------------------------------+
  | Model              | Processes | Threads | Sockets            |
  +--------------------+-----------+---------+--------------------+
  | Uvicorn (1 worker) | 1         | 1       | 1001 (1+1000)      |
  | Uvicorn (4 workers)| 4         | 4       | 1001 (1+1000)      |
  | Gunicorn sync      | 4         | 8       | MAX 8 connections! |
  | Tomcat (200 pool)  | 1         | 200+    | MAX 200 or queue   |
  | Netty (4 workers)  | 1         | 5       | 1001 (1+1000)      |
  +--------------------------------------------------------------+


  FORMULA:
  +--------------------------------------------------------------+
  |  Sockets at any time = 1 (listening) + N (active connections)|
  |                                                              |
  |  For blocking I/O:   Max connections = Thread pool size     |
  |  For non-blocking:   Max connections = File descriptor limit |
  |                      (ulimit -n, often 1024-1M)              |
  +--------------------------------------------------------------+
```

### Viewing Sockets in Practice

```
+------------------------------------------------------------------+
|           HOW TO SEE YOUR SERVER'S SOCKETS                       |
+------------------------------------------------------------------+

  Linux commands to inspect sockets:

  # See all TCP sockets for a process
  $ ss -tlnp | grep 8000
  LISTEN  0  128  0.0.0.0:8000  *:*  users:(("uvicorn",pid=1234,fd=3))

  # See all connections to your server
  $ ss -tn | grep 8000
  ESTAB  0  0  192.168.1.10:8000  192.168.1.20:54321
  ESTAB  0  0  192.168.1.10:8000  192.168.1.21:54322
  ESTAB  0  0  192.168.1.10:8000  192.168.1.22:54323

  # Count connections
  $ ss -tn | grep 8000 | wc -l
  3

  # See file descriptors for a process
  $ ls -la /proc/1234/fd/
  lrwx------ 1 user user 64 Dec 25 10:00 0 -> /dev/pts/0
  lrwx------ 1 user user 64 Dec 25 10:00 1 -> /dev/pts/0
  lrwx------ 1 user user 64 Dec 25 10:00 2 -> /dev/pts/0
  lrwx------ 1 user user 64 Dec 25 10:00 3 -> socket:[12345]  <- listening
  lrwx------ 1 user user 64 Dec 25 10:00 4 -> socket:[12346]  <- connection 1
  lrwx------ 1 user user 64 Dec 25 10:00 5 -> socket:[12347]  <- connection 2

  # Using lsof
  $ lsof -i :8000
  COMMAND   PID USER   FD   TYPE DEVICE SIZE/OFF NODE NAME
  uvicorn 1234 user    3u  IPv4  12345      0t0  TCP *:8000 (LISTEN)
  uvicorn 1234 user    4u  IPv4  12346      0t0  TCP ...:8000->...:54321 (ESTABLISHED)
  uvicorn 1234 user    5u  IPv4  12347      0t0  TCP ...:8000->...:54322 (ESTABLISHED)


  Python check (inside your app):
  +--------------------------------------------------------------+
  | import os                                                    |
  | fd_count = len(os.listdir(f'/proc/{os.getpid()}/fd'))        |
  | print(f"Open file descriptors: {fd_count}")                  |
  +--------------------------------------------------------------+
```

---

## Related Topics
- [TCP and UDP Protocols](./tcp-udp-protocols.md) - Deep dive into transport protocols
- Network programming in various languages (Java NIO, Python asyncio, Go net)
- Advanced topics: epoll, kqueue, io_uring, DPDK
