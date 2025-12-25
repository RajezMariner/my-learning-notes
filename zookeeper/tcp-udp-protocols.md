# TCP and UDP Protocols: A Deep Dive

## Table of Contents
1. [Overview: TCP vs UDP](#overview-tcp-vs-udp)
2. [TCP Protocol](#tcp-protocol)
3. [UDP Protocol](#udp-protocol)
4. [When to Use Which](#when-to-use-which)
5. [Real-World Examples](#real-world-examples)

---

## Overview: TCP vs UDP

Both TCP and UDP are **Transport Layer (Layer 4)** protocols that sit on top of IP.

```
+------------------------------------------------------------------+
|                    TCP vs UDP AT A GLANCE                        |
+------------------------------------------------------------------+

                    TCP                         UDP
              (Transmission                (User Datagram
               Control Protocol)            Protocol)

  +-----------------------------+  +-----------------------------+
  |                             |  |                             |
  |    "Registered Mail"        |  |      "Postcard"             |
  |                             |  |                             |
  |  - Delivery confirmation    |  |  - Just send it             |
  |  - Guaranteed order         |  |  - No confirmation          |
  |  - Slower, more overhead    |  |  - Faster, lightweight      |
  |  - Connection required      |  |  - No connection needed     |
  |                             |  |                             |
  +-----------------------------+  +-----------------------------+
```

### Quick Comparison Table

```
+------------------------------------------------------------------+
|                    FEATURE COMPARISON                            |
+------------------+------------------------+----------------------+
| Feature          | TCP                    | UDP                  |
+------------------+------------------------+----------------------+
| Connection       | Connection-oriented    | Connectionless       |
|                  | (handshake required)   | (just send)          |
+------------------+------------------------+----------------------+
| Reliability      | Guaranteed delivery    | Best-effort only     |
|                  | (retransmissions)      | (may lose packets)   |
+------------------+------------------------+----------------------+
| Ordering         | Guaranteed order       | No ordering          |
|                  | (sequence numbers)     | (may arrive jumbled) |
+------------------+------------------------+----------------------+
| Flow Control     | Yes (window-based)     | No                   |
+------------------+------------------------+----------------------+
| Congestion       | Yes (slow start,       | No                   |
| Control          | congestion avoidance)  |                      |
+------------------+------------------------+----------------------+
| Header Size      | 20-60 bytes            | 8 bytes              |
+------------------+------------------------+----------------------+
| Speed            | Slower (overhead)      | Faster (minimal)     |
+------------------+------------------------+----------------------+
| Data Boundary    | Byte stream            | Message-based        |
|                  | (no boundaries)        | (preserves messages) |
+------------------+------------------------+----------------------+
| Broadcast/       | No                     | Yes                  |
| Multicast        |                        |                      |
+------------------+------------------------+----------------------+
```

---

## TCP Protocol

### What is TCP?

**Transmission Control Protocol (TCP)** provides reliable, ordered, and error-checked delivery of a stream of bytes between applications.

```
+------------------------------------------------------------------+
|                    TCP CHARACTERISTICS                           |
+------------------------------------------------------------------+
|                                                                  |
|  1. CONNECTION-ORIENTED                                          |
|     - Must establish connection before sending data              |
|     - 3-way handshake to connect                                 |
|     - 4-way handshake to disconnect                              |
|                                                                  |
|  2. RELIABLE DELIVERY                                            |
|     - Every byte is acknowledged                                 |
|     - Lost packets are retransmitted                             |
|     - Corrupted packets detected via checksum                    |
|                                                                  |
|  3. ORDERED DELIVERY                                             |
|     - Sequence numbers track byte position                       |
|     - Receiver reorders out-of-order packets                     |
|     - Application sees data in exact order sent                  |
|                                                                  |
|  4. FLOW CONTROL                                                 |
|     - Receiver advertises window size                            |
|     - Sender won't overwhelm slow receiver                       |
|                                                                  |
|  5. CONGESTION CONTROL                                           |
|     - Detects network congestion                                 |
|     - Reduces sending rate when network is busy                  |
|                                                                  |
+------------------------------------------------------------------+
```

### TCP Header Structure

```
+------------------------------------------------------------------+
|                    TCP HEADER (20-60 bytes)                      |
+------------------------------------------------------------------+

 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          Source Port          |       Destination Port        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Sequence Number                        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    Acknowledgment Number                      |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| Data  |Reserv|N|C|E|U|A|P|R|S|F|                              |
|Offset |      |S|W|C|R|C|S|S|Y|I|         Window Size          |
|       |      | |R|E|G|K|H|T|N|N|                              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|           Checksum            |         Urgent Pointer        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    Options (if Data Offset > 5)               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                             Data                              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### TCP Header Fields Explained

```
+------------------------------------------------------------------+
|                    TCP HEADER FIELDS                             |
+------------------------------------------------------------------+

  SOURCE PORT (16 bits)
  +--------------------------------------------------------------+
  |  - Sender's port number (0-65535)                            |
  |  - Ephemeral port for clients (49152-65535)                  |
  |  - Well-known port for servers (0-1023)                      |
  +--------------------------------------------------------------+

  DESTINATION PORT (16 bits)
  +--------------------------------------------------------------+
  |  - Receiver's port number                                    |
  |  - Identifies the application/service                        |
  +--------------------------------------------------------------+

  SEQUENCE NUMBER (32 bits)
  +--------------------------------------------------------------+
  |  - Position of first data byte in this segment               |
  |  - If SYN flag set: Initial Sequence Number (ISN)            |
  |  - Wraps around after 4GB (2^32 bytes)                       |
  |                                                              |
  |  Example:                                                    |
  |    SEQ=1000, Data="Hello" (5 bytes)                          |
  |    Next segment will have SEQ=1005                           |
  +--------------------------------------------------------------+

  ACKNOWLEDGMENT NUMBER (32 bits)
  +--------------------------------------------------------------+
  |  - Next sequence number the receiver expects                 |
  |  - Acknowledges all bytes up to this number - 1              |
  |  - Only valid if ACK flag is set                             |
  |                                                              |
  |  Example:                                                    |
  |    ACK=1005 means "I received bytes up to 1004,              |
  |                    now send me byte 1005"                    |
  +--------------------------------------------------------------+

  DATA OFFSET (4 bits)
  +--------------------------------------------------------------+
  |  - Number of 32-bit words in TCP header                      |
  |  - Minimum: 5 (20 bytes), Maximum: 15 (60 bytes)             |
  |  - Values > 5 indicate options are present                   |
  +--------------------------------------------------------------+

  FLAGS (9 bits)
  +--------------------------------------------------------------+
  |  NS  - ECN-nonce concealment protection                      |
  |  CWR - Congestion Window Reduced                             |
  |  ECE - ECN-Echo                                              |
  |  URG - Urgent pointer field is significant                   |
  |  ACK - Acknowledgment field is significant                   |
  |  PSH - Push function (deliver data immediately)              |
  |  RST - Reset the connection                                  |
  |  SYN - Synchronize sequence numbers (connection start)       |
  |  FIN - No more data from sender (connection end)             |
  +--------------------------------------------------------------+

  WINDOW SIZE (16 bits)
  +--------------------------------------------------------------+
  |  - Receiver's available buffer space                         |
  |  - Flow control: tells sender how much to send               |
  |  - Can be scaled with Window Scale option (up to 1GB)        |
  +--------------------------------------------------------------+

  CHECKSUM (16 bits)
  +--------------------------------------------------------------+
  |  - Error detection for header and data                       |
  |  - Includes pseudo-header (src IP, dst IP, protocol)         |
  |  - Mandatory in TCP                                          |
  +--------------------------------------------------------------+

  URGENT POINTER (16 bits)
  +--------------------------------------------------------------+
  |  - Offset from sequence number to urgent data                |
  |  - Only valid if URG flag is set                             |
  |  - Rarely used in practice                                   |
  +--------------------------------------------------------------+

  OPTIONS (0-40 bytes)
  +--------------------------------------------------------------+
  |  Common options:                                             |
  |  - MSS (Maximum Segment Size): largest segment acceptable    |
  |  - Window Scale: multiplier for window size                  |
  |  - SACK (Selective ACK): acknowledge non-contiguous data     |
  |  - Timestamps: for RTT measurement and PAWS                  |
  +--------------------------------------------------------------+
```

### TCP Connection Lifecycle

```
+------------------------------------------------------------------+
|                    TCP 3-WAY HANDSHAKE                           |
|                    (Connection Establishment)                    |
+------------------------------------------------------------------+

    CLIENT                                              SERVER
       |                                                   |
       |  State: CLOSED                    State: LISTEN   |
       |                                                   |
       |                                                   |
       |  ----------- SYN (seq=x) ------------------>      |
       |  State: SYN_SENT                                  |
       |                                                   |
       |  "I want to connect. My starting                  |
       |   sequence number is X."                          |
       |                                                   |
       |                                                   |
       |  <--------- SYN-ACK (seq=y, ack=x+1) ----------   |
       |                              State: SYN_RECEIVED  |
       |                                                   |
       |  "OK, I acknowledge your X. My starting           |
       |   sequence number is Y."                          |
       |                                                   |
       |                                                   |
       |  ----------- ACK (ack=y+1) ------------------>    |
       |  State: ESTABLISHED          State: ESTABLISHED   |
       |                                                   |
       |  "I acknowledge your Y.                           |
       |   Let's start talking!"                           |
       |                                                   |
       v                                                   v


  WHY 3 HANDSHAKES?
  +--------------------------------------------------------------+
  |  1. Prevents old duplicate connection requests               |
  |  2. Both sides agree on initial sequence numbers             |
  |  3. Both sides confirm they can send AND receive             |
  |                                                              |
  |  2-way would fail because:                                   |
  |  - Server wouldn't know if client received its SYN-ACK       |
  |  - Old SYN packets could create zombie connections           |
  +--------------------------------------------------------------+
```

### TCP Connection Termination

```
+------------------------------------------------------------------+
|                    TCP 4-WAY TERMINATION                         |
|                    (Connection Close)                            |
+------------------------------------------------------------------+

    CLIENT                                              SERVER
       |                                                   |
       |  State: ESTABLISHED          State: ESTABLISHED   |
       |                                                   |
       |                                                   |
       |  ----------- FIN (seq=u) ------------------>      |
       |  State: FIN_WAIT_1                                |
       |                                                   |
       |  "I'm done sending data."                         |
       |                                                   |
       |                                                   |
       |  <----------- ACK (ack=u+1) ------------------    |
       |  State: FIN_WAIT_2           State: CLOSE_WAIT    |
       |                                                   |
       |  "OK, I got your FIN."                            |
       |  (Server may still send data)                     |
       |                                                   |
       |                                                   |
       |  <----------- FIN (seq=v) --------------------    |
       |                              State: LAST_ACK      |
       |                                                   |
       |  "I'm also done sending."                         |
       |                                                   |
       |                                                   |
       |  ----------- ACK (ack=v+1) ------------------>    |
       |  State: TIME_WAIT            State: CLOSED        |
       |                                                   |
       |  "Got it. Goodbye!"                               |
       |                                                   |
       |                                                   |
       |  (wait 2*MSL)                                     |
       |  State: CLOSED                                    |
       |                                                   |
       v                                                   v


  WHY 4 HANDSHAKES (not 3)?
  +--------------------------------------------------------------+
  |  TCP is FULL-DUPLEX: each direction closes independently     |
  |                                                              |
  |  - Client's FIN: "I won't send more data"                    |
  |  - Server's ACK: "OK, I heard you"                           |
  |  - Server's FIN: "I'm also done" (may come later)            |
  |  - Client's ACK: "OK, goodbye"                               |
  |                                                              |
  |  Sometimes combined: Server sends FIN+ACK together (3-way)   |
  +--------------------------------------------------------------+


  WHY TIME_WAIT (2*MSL)?
  +--------------------------------------------------------------+
  |  MSL = Maximum Segment Lifetime (typically 30-120 seconds)   |
  |                                                              |
  |  Reasons:                                                    |
  |  1. Ensure final ACK reaches server                          |
  |     (if lost, server retransmits FIN)                        |
  |  2. Allow old duplicate packets to expire                    |
  |     (prevents confusion with new connection on same port)    |
  +--------------------------------------------------------------+
```

### TCP State Machine

```
+------------------------------------------------------------------+
|                    TCP STATE MACHINE                             |
+------------------------------------------------------------------+

                              +-------------+
                              |   CLOSED    |
                              +------+------+
                                     |
          +--------------------------+------------------------+
          |                          |                        |
          | passive open             | active open            |
          | (server)                 | (client)               |
          v                          |                        v
   +-------------+                   |                 +-------------+
   |   LISTEN    |                   |                 |  SYN_SENT   |
   +------+------+                   |                 +------+------+
          |                          |                        |
          | rcv SYN                  |                        | rcv SYN+ACK
          | send SYN+ACK             |                        | send ACK
          v                          |                        v
   +-------------+                   |                 +-------------+
   |  SYN_RCVD   |-------------------+---------------->| ESTABLISHED |
   +-------------+      rcv ACK                        +------+------+
                                                              |
                                          +-------------------+
                                          |
                    close                 |                   rcv FIN
                    send FIN              |                   send ACK
                          |               |                         |
                          v               |                         v
                   +-------------+        |                 +-------------+
                   | FIN_WAIT_1  |        |                 | CLOSE_WAIT  |
                   +------+------+        |                 +------+------+
                          |               |                        |
          +---------------+               |                        | close
          |               |               |                        | send FIN
          | rcv ACK       | rcv FIN+ACK   |                        v
          v               | send ACK      |                 +-------------+
   +-------------+        |               |                 |  LAST_ACK   |
   | FIN_WAIT_2  |        |               |                 +------+------+
   +------+------+        |               |                        |
          |               |               |                        | rcv ACK
          | rcv FIN       |               |                        v
          | send ACK      |               |                 +-------------+
          |               v               |                 |   CLOSED    |
          |        +-------------+        |                 +-------------+
          +------->|  TIME_WAIT  |        |
                   +------+------+        |
                          |               |
                          | 2MSL timeout  |
                          v               |
                   +-------------+        |
                   |   CLOSED    |<-------+
                   +-------------+


  STATE DESCRIPTIONS:
  +--------------------------------------------------------------+
  | CLOSED     - No connection                                   |
  | LISTEN     - Server waiting for connection requests          |
  | SYN_SENT   - Client sent SYN, waiting for SYN-ACK            |
  | SYN_RCVD   - Server received SYN, sent SYN-ACK, waiting ACK  |
  | ESTABLISHED- Connection open, data transfer possible         |
  | FIN_WAIT_1 - Sent FIN, waiting for ACK or FIN                |
  | FIN_WAIT_2 - Received ACK for our FIN, waiting for their FIN |
  | CLOSE_WAIT - Received FIN, waiting for app to close          |
  | LAST_ACK   - Sent FIN, waiting for final ACK                 |
  | TIME_WAIT  - Waiting for old packets to expire               |
  +--------------------------------------------------------------+
```

### TCP Reliability Mechanisms

```
+------------------------------------------------------------------+
|                    TCP RELIABILITY                               |
+------------------------------------------------------------------+

1. SEQUENCE NUMBERS & ACKNOWLEDGMENTS
+--------------------------------------------------------------+

  Sender                                          Receiver
     |                                                |
     |  SEQ=100, Data="Hello" (5 bytes)               |
     |  ------------------------------------------->  |
     |                                                |
     |                           ACK=105              |
     |  <-------------------------------------------  |
     |                                                |
     |  SEQ=105, Data="World" (5 bytes)               |
     |  ------------------------------------------->  |
     |                                                |
     |                           ACK=110              |
     |  <-------------------------------------------  |

  - Each byte has a sequence number
  - ACK = "I expect byte N next" (cumulative)


2. RETRANSMISSION
+--------------------------------------------------------------+

  Sender                                          Receiver
     |                                                |
     |  SEQ=100, Data="Hello"                         |
     |  --------------------X (LOST)                  |
     |                                                |
     |  (timeout - no ACK received)                   |
     |                                                |
     |  SEQ=100, Data="Hello" (retransmit)            |
     |  ------------------------------------------->  |
     |                                                |
     |                           ACK=105              |
     |  <-------------------------------------------  |

  Timeout calculation:
  - RTO (Retransmission Timeout) based on RTT
  - RTT measured using timestamps
  - RTO = SRTT + 4*RTTVAR (smoothed RTT + variance)


3. SELECTIVE ACKNOWLEDGMENT (SACK)
+--------------------------------------------------------------+

  Without SACK (cumulative ACK only):

  Sender sends: [1] [2] [3] [4] [5]
  Receiver gets: [1] [2] _ [4] [5]  (packet 3 lost)
  Receiver ACKs: ACK=3, ACK=3, ACK=3 (duplicate ACKs)
  Sender must retransmit: [3] [4] [5] (wasteful!)

  With SACK:

  Receiver ACKs: ACK=3, SACK=4-5
  "I'm missing 3, but I have 4 and 5"
  Sender retransmits only: [3]


4. DUPLICATE ACK & FAST RETRANSMIT
+--------------------------------------------------------------+

  Normal timeout: Wait RTO (could be seconds)

  Fast retransmit: After 3 duplicate ACKs, assume loss

  Sender                                          Receiver
     |  SEQ=1 ----------------------------------->  |
     |  SEQ=2 ----------X (LOST)                    |
     |  SEQ=3 ----------------------------------->  |  ACK=2
     |  SEQ=4 ----------------------------------->  |  ACK=2 (dup)
     |  SEQ=5 ----------------------------------->  |  ACK=2 (dup)
     |                                              |  ACK=2 (dup)
     |                                                |
     |  (3 dup ACKs = packet 2 lost, retransmit NOW)  |
     |  SEQ=2 ----------------------------------->  |
     |                                   ACK=6      |
     |  <-------------------------------------------  |
```

### TCP Flow Control

```
+------------------------------------------------------------------+
|                    TCP FLOW CONTROL                              |
+------------------------------------------------------------------+

  Flow control prevents sender from overwhelming receiver.
  Receiver advertises available buffer space (window).

  SLIDING WINDOW MECHANISM:
  +--------------------------------------------------------------+

    Sender's View:

    +---+---+---+---+---+---+---+---+---+---+---+---+
    | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |10 |11 |12 |
    +---+---+---+---+---+---+---+---+---+---+---+---+
    |<-ACKed->|<-- Sent, waiting -->|<-- Can send -->|<-Cannot->|
              |      for ACK        |   (in window)  |   send   |
              |                     |                |          |
              +---------------------+----------------+
                    Sliding Window


  WINDOW SIZE ADVERTISEMENT:
  +--------------------------------------------------------------+

    Sender                                          Receiver
       |                                                |
       |  Data (1000 bytes)                             |
       |  ------------------------------------------->  |
       |                                                |
       |                   ACK, Window=4000             |
       |  <-------------------------------------------  |
       |                                                |
       |  "I have 4000 bytes of buffer space"           |
       |                                                |
       |  Data (4000 bytes)                             |
       |  ------------------------------------------->  |
       |                                                |
       |                   ACK, Window=0                |
       |  <-------------------------------------------  |
       |                                                |
       |  "STOP! My buffer is full!"                    |
       |  (Sender must wait for window update)          |


  ZERO WINDOW PROBE:
  +--------------------------------------------------------------+

    When receiver advertises Window=0:
    - Sender periodically sends 1-byte probes
    - Receiver responds with current window size
    - Prevents deadlock if window update is lost
```

### TCP Congestion Control

```
+------------------------------------------------------------------+
|                    TCP CONGESTION CONTROL                        |
+------------------------------------------------------------------+

  Congestion control prevents sender from overwhelming the NETWORK.
  (Different from flow control which protects the RECEIVER)

  KEY VARIABLES:
  +--------------------------------------------------------------+
  |  cwnd  - Congestion Window (sender's limit)                  |
  |  ssthresh - Slow Start Threshold                             |
  |  rwnd  - Receiver Window (advertised by receiver)            |
  |                                                              |
  |  Actual window = min(cwnd, rwnd)                             |
  +--------------------------------------------------------------+


  1. SLOW START (Exponential Growth)
  +--------------------------------------------------------------+

       cwnd
         ^
         |                    ssthresh
         |                        |
    32   |                        |      ......
    16   |                        |   ...
     8   |                     ...|...
     4   |                  ...   |
     2   |               ...      |
     1   |____________...________ |_______________________> time
         |  Exponential     |  Congestion
         |  growth          |  Avoidance

    - Start with cwnd = 1 MSS (or 10 MSS in modern TCP)
    - Double cwnd every RTT (exponential)
    - Continue until cwnd >= ssthresh
    - Then switch to congestion avoidance


  2. CONGESTION AVOIDANCE (Linear Growth)
  +--------------------------------------------------------------+

    - Increase cwnd by 1 MSS per RTT (linear)
    - cwnd = cwnd + MSS * (MSS / cwnd)
    - Slow, careful increase to probe for bandwidth


  3. CONGESTION DETECTION & RESPONSE
  +--------------------------------------------------------------+

    TIMEOUT (severe congestion):
    +----------------------------------------------------------+
    |  ssthresh = cwnd / 2                                     |
    |  cwnd = 1 MSS                                            |
    |  Go back to Slow Start                                   |
    +----------------------------------------------------------+

    3 DUPLICATE ACKs (mild congestion - Fast Recovery):
    +----------------------------------------------------------+
    |  ssthresh = cwnd / 2                                     |
    |  cwnd = ssthresh + 3 MSS                                 |
    |  Continue in Congestion Avoidance                        |
    +----------------------------------------------------------+


  VISUAL: CONGESTION CONTROL STATES
  +--------------------------------------------------------------+

                         +-------------+
                         | Slow Start  |
                         +------+------+
                                |
              +-----------------+-----------------+
              |                                   |
              | cwnd >= ssthresh                  | timeout
              v                                   v
       +-------------+                     +-------------+
       | Congestion  |-------------------->| Slow Start  |
       | Avoidance   |    3 dup ACKs       +-------------+
       +------+------+         |
              |                v
              |         +-------------+
              +-------->|   Fast      |
                        |  Recovery   |
                        +-------------+


  MODERN ALGORITHMS:
  +--------------------------------------------------------------+
  |  - TCP Reno: Basic algorithm described above                 |
  |  - TCP NewReno: Better fast recovery                         |
  |  - TCP CUBIC: Default in Linux (better for high bandwidth)   |
  |  - TCP BBR: Google's algorithm (model-based, not loss-based) |
  +--------------------------------------------------------------+
```

---

## UDP Protocol

### What is UDP?

**User Datagram Protocol (UDP)** provides a simple, connectionless communication service with minimal protocol overhead.

```
+------------------------------------------------------------------+
|                    UDP CHARACTERISTICS                           |
+------------------------------------------------------------------+
|                                                                  |
|  1. CONNECTIONLESS                                               |
|     - No handshake required                                      |
|     - Just send the datagram                                     |
|     - Each datagram is independent                               |
|                                                                  |
|  2. UNRELIABLE                                                   |
|     - No guarantee of delivery                                   |
|     - No retransmission                                          |
|     - Packets may be lost, duplicated, or reordered              |
|                                                                  |
|  3. NO FLOW CONTROL                                              |
|     - Sender can overwhelm receiver                              |
|     - Application must handle this                               |
|                                                                  |
|  4. NO CONGESTION CONTROL                                        |
|     - Sender can overwhelm network                               |
|     - Application must handle this                               |
|                                                                  |
|  5. MESSAGE-ORIENTED                                             |
|     - Preserves message boundaries                               |
|     - Each send() = one datagram                                 |
|     - Each recv() = one datagram (if it arrives)                 |
|                                                                  |
|  6. LOW OVERHEAD                                                 |
|     - 8-byte header (vs TCP's 20-60 bytes)                       |
|     - No connection state to maintain                            |
|     - Faster for small messages                                  |
|                                                                  |
+------------------------------------------------------------------+
```

### UDP Header Structure

```
+------------------------------------------------------------------+
|                    UDP HEADER (8 bytes only!)                    |
+------------------------------------------------------------------+

 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          Source Port          |       Destination Port        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|            Length             |           Checksum            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                             Data                              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+


  Compare to TCP's 20+ byte header!

  UDP Header: 8 bytes
  +-------+-------+-------+-------+
  |SrcPort|DstPort|Length |Chksum |
  +-------+-------+-------+-------+
  | 2B    | 2B    | 2B    | 2B    |
```

### UDP Header Fields Explained

```
+------------------------------------------------------------------+
|                    UDP HEADER FIELDS                             |
+------------------------------------------------------------------+

  SOURCE PORT (16 bits)
  +--------------------------------------------------------------+
  |  - Sender's port number                                      |
  |  - Optional: can be 0 if reply not needed                    |
  +--------------------------------------------------------------+

  DESTINATION PORT (16 bits)
  +--------------------------------------------------------------+
  |  - Receiver's port number                                    |
  |  - Identifies the application                                |
  +--------------------------------------------------------------+

  LENGTH (16 bits)
  +--------------------------------------------------------------+
  |  - Length of header + data in bytes                          |
  |  - Minimum: 8 (header only)                                  |
  |  - Maximum: 65,535 (limited by IP packet size)               |
  |  - Practical max: ~65,507 (65,535 - 20 IP - 8 UDP)           |
  +--------------------------------------------------------------+

  CHECKSUM (16 bits)
  +--------------------------------------------------------------+
  |  - Error detection (optional in IPv4, mandatory in IPv6)     |
  |  - Covers header, data, and pseudo-header                    |
  |  - 0 means checksum not computed (IPv4 only)                 |
  +--------------------------------------------------------------+
```

### UDP Communication Flow

```
+------------------------------------------------------------------+
|                    UDP COMMUNICATION                             |
+------------------------------------------------------------------+

  NO HANDSHAKE - JUST SEND!

    CLIENT                                              SERVER
       |                                                   |
       |                                 bind() to port    |
       |                                                   |
       |  ----------- UDP Datagram 1 ----------------->    |
       |             (may or may not arrive)               |
       |                                                   |
       |  ----------- UDP Datagram 2 ----------------->    |
       |             (may arrive before 1!)                |
       |                                                   |
       |  <----------- UDP Response ------------------     |
       |             (optional, if server replies)         |
       |                                                   |

  KEY DIFFERENCES FROM TCP:
  +--------------------------------------------------------------+
  |  - No connection establishment (no 3-way handshake)          |
  |  - No connection termination (no FIN/ACK)                    |
  |  - No state on server (no per-client socket)                 |
  |  - Server uses ONE socket for ALL clients                    |
  +--------------------------------------------------------------+


  SERVER SOCKET MODEL:
  +--------------------------------------------------------------+

    TCP Server:
    +------------------+
    | Listening Socket |
    +--------+---------+
             |
      accept() creates new socket per client
             |
    +--------+--------+--------+
    |        |        |        |
    v        v        v        v
  Socket1  Socket2  Socket3  Socket4
  Client1  Client2  Client3  Client4


    UDP Server:
    +------------------+
    |  Single Socket   |<---- All clients use this ONE socket
    +--------+---------+
             |
    recvfrom() returns sender's address
    sendto() specifies destination address
             |
    +--------+--------+--------+
    |        |        |        |
  Client1  Client2  Client3  Client4
  (same socket handles all)
```

### UDP Use Cases

```
+------------------------------------------------------------------+
|                    WHEN TO USE UDP                               |
+------------------------------------------------------------------+

  GOOD FOR:
  +--------------------------------------------------------------+
  |  1. Real-time applications                                   |
  |     - Voice/Video calls (VoIP, Zoom, Skype)                  |
  |     - Live streaming                                         |
  |     - Online gaming                                          |
  |     - Late data is worse than lost data                      |
  |                                                              |
  |  2. Request-response protocols                               |
  |     - DNS (usually single packet each way)                   |
  |     - DHCP                                                   |
  |     - NTP (time sync)                                        |
  |                                                              |
  |  3. Broadcast/Multicast                                      |
  |     - Service discovery                                      |
  |     - Network announcements                                  |
  |     - TCP cannot do broadcast/multicast                      |
  |                                                              |
  |  4. High-frequency small messages                            |
  |     - IoT sensors                                            |
  |     - Game state updates                                     |
  |     - Telemetry                                              |
  |                                                              |
  |  5. When you need custom reliability                         |
  |     - QUIC (Google's protocol, HTTP/3)                       |
  |     - Custom game networking                                 |
  |     - Application knows best how to handle loss              |
  +--------------------------------------------------------------+

  BAD FOR:
  +--------------------------------------------------------------+
  |  1. File transfer (need reliability)                         |
  |  2. Email (need reliability)                                 |
  |  3. Web browsing (need reliability, ordering)                |
  |  4. Database queries (need reliability)                      |
  |  5. Any time you can't afford to lose data                   |
  +--------------------------------------------------------------+
```

---

## When to Use Which

```
+------------------------------------------------------------------+
|                    DECISION FLOWCHART                            |
+------------------------------------------------------------------+

  Start
    |
    v
  Do you need guaranteed delivery?
    |
    +-- YES --> Do you need ordering?
    |               |
    |               +-- YES --> Use TCP
    |               |
    |               +-- NO --> Consider TCP or
    |                          implement reliability over UDP
    |
    +-- NO --> Is latency critical?
                    |
                    +-- YES --> Use UDP
                    |
                    +-- NO --> Is it request-response?
                                    |
                                    +-- YES (small) --> UDP is fine
                                    |
                                    +-- NO --> Use TCP


  RULE OF THUMB:
  +--------------------------------------------------------------+
  |  "When in doubt, use TCP"                                    |
  |                                                              |
  |  TCP is correct by default. Only use UDP when you have       |
  |  a specific reason (latency, broadcast, custom reliability)  |
  +--------------------------------------------------------------+
```

### Detailed Comparison

```
+------------------------------------------------------------------+
|                    TCP vs UDP DETAILED                           |
+------------------------------------------------------------------+

  CONNECTION SETUP TIME:
  +--------------------------------------------------------------+
  |  TCP: 1.5 RTT before first data (SYN, SYN-ACK, ACK+Data)     |
  |  UDP: 0 RTT (just send)                                      |
  |                                                              |
  |  For a 100ms RTT:                                            |
  |    TCP: 150ms before first data byte                         |
  |    UDP: 0ms                                                  |
  +--------------------------------------------------------------+

  OVERHEAD PER PACKET:
  +--------------------------------------------------------------+
  |  TCP: 20-60 bytes header                                     |
  |  UDP: 8 bytes header                                         |
  |                                                              |
  |  For 100-byte payload:                                       |
  |    TCP: 20% overhead minimum                                 |
  |    UDP: 8% overhead                                          |
  +--------------------------------------------------------------+

  SMALL MESSAGE EFFICIENCY:
  +--------------------------------------------------------------+
  |  Sending 1 byte of data:                                     |
  |                                                              |
  |  TCP: 20 bytes header + 1 byte data = 21 bytes               |
  |       + ACK packet (20 bytes)                                |
  |       = 41 bytes total for 1 byte of data                    |
  |                                                              |
  |  UDP: 8 bytes header + 1 byte data = 9 bytes                 |
  |       = 9 bytes total for 1 byte of data                     |
  +--------------------------------------------------------------+

  HEAD-OF-LINE BLOCKING:
  +--------------------------------------------------------------+
  |  TCP: If packet 3 is lost, packets 4, 5, 6 wait              |
  |       (even if they arrived) until 3 is retransmitted        |
  |                                                              |
  |  UDP: No blocking. Each packet is independent.               |
  |       Lost packet 3? Just use 4, 5, 6.                       |
  +--------------------------------------------------------------+
```

---

## Real-World Examples

### DNS (UDP)

```
+------------------------------------------------------------------+
|                    DNS OVER UDP                                  |
+------------------------------------------------------------------+

  Client                                         DNS Server
     |                                                |
     |  UDP Query: "What is google.com's IP?"         |
     |  ------------------------------------------->  |
     |                                                |
     |  UDP Response: "142.250.185.78"                |
     |  <-------------------------------------------  |
     |                                                |

  Why UDP?
  - Single request, single response
  - Small packets (usually < 512 bytes)
  - Speed matters (every web page needs DNS)
  - Retry at application level if lost
  - Falls back to TCP for large responses (> 512 bytes)
```

### HTTP (TCP)

```
+------------------------------------------------------------------+
|                    HTTP OVER TCP                                 |
+------------------------------------------------------------------+

  Client                                         Web Server
     |                                                |
     |  === TCP 3-way handshake ===                   |
     |  SYN ----------------------------------------> |
     |  <------------------------------------- SYN-ACK|
     |  ACK ----------------------------------------> |
     |                                                |
     |  HTTP GET /index.html                          |
     |  ------------------------------------------->  |
     |                                                |
     |  HTTP Response (may be multiple packets)       |
     |  <-------------------------------------------  |
     |  <-------------------------------------------  |
     |  <-------------------------------------------  |
     |                                                |
     |  === TCP termination ===                       |
     |                                                |

  Why TCP?
  - Need complete, correct file (reliability)
  - HTML must be in order (ordering)
  - Multiple packets per response (connection reuse)
```

### Video Streaming (UDP vs TCP)

```
+------------------------------------------------------------------+
|                    VIDEO STREAMING                               |
+------------------------------------------------------------------+

  LIVE STREAMING (often UDP/RTP):
  +--------------------------------------------------------------+
  |  - Latency critical (< 1 second delay)                       |
  |  - Lost frame? Skip it, show next frame                      |
  |  - Waiting for retransmission = video stutter                |
  |  - Old frame is useless                                      |
  +--------------------------------------------------------------+

  VIDEO ON DEMAND (often TCP/HTTP):
  +--------------------------------------------------------------+
  |  - Buffer ahead (5-30 seconds)                               |
  |  - Can wait for retransmissions                              |
  |  - Quality matters (no missing frames)                       |
  |  - Netflix, YouTube use TCP (HTTP streaming)                 |
  +--------------------------------------------------------------+


  WHY YOUTUBE/NETFLIX USE TCP:
  +--------------------------------------------------------------+
  |  1. Can buffer ahead (no need for ultra-low latency)         |
  |  2. Works through all firewalls (HTTP on port 80/443)        |
  |  3. Adaptive bitrate handles congestion                      |
  |  4. Complete frames matter for quality                       |
  +--------------------------------------------------------------+
```

### Online Gaming

```
+------------------------------------------------------------------+
|                    ONLINE GAMING                                 |
+------------------------------------------------------------------+

  GAME STATE UPDATES (often UDP):
  +--------------------------------------------------------------+
  |  - Player position, bullet locations                         |
  |  - 20-60 updates per second                                  |
  |  - Old position update is worthless                          |
  |  - Skip lost packet, use latest                              |
  +--------------------------------------------------------------+

  CHAT MESSAGES (often TCP):
  +--------------------------------------------------------------+
  |  - Must be delivered                                         |
  |  - Order matters                                             |
  |  - Lower frequency                                           |
  +--------------------------------------------------------------+

  MANY GAMES USE BOTH:
  +--------------------------------------------------------------+
  |  UDP: Fast-changing game state                               |
  |  TCP: Chat, matchmaking, purchases, achievements             |
  +--------------------------------------------------------------+
```

---

## Summary

```
+------------------------------------------------------------------+
|                    QUICK REFERENCE                               |
+------------------------------------------------------------------+

  USE TCP WHEN:
  +--------------------------------------------------------------+
  |  - Data must arrive correctly (files, web, email, DB)        |
  |  - Order matters (streaming protocols, commands)             |
  |  - You don't want to implement reliability yourself          |
  |  - Connection state is acceptable overhead                   |
  +--------------------------------------------------------------+

  USE UDP WHEN:
  +--------------------------------------------------------------+
  |  - Speed > reliability (games, voice, video)                 |
  |  - Simple request-response (DNS, DHCP)                       |
  |  - Broadcast/multicast needed                                |
  |  - You'll implement custom reliability (QUIC)                |
  |  - Connection setup overhead unacceptable                    |
  +--------------------------------------------------------------+

  PROTOCOL NUMBERS:
  +--------------------------------------------------------------+
  |  TCP: IP Protocol 6                                          |
  |  UDP: IP Protocol 17                                         |
  +--------------------------------------------------------------+

  COMMON PORTS:
  +--------------------------------------------------------------+
  |  TCP: 80 (HTTP), 443 (HTTPS), 22 (SSH), 21 (FTP)             |
  |  UDP: 53 (DNS), 67/68 (DHCP), 123 (NTP), 161 (SNMP)          |
  |  Both: 53 (DNS), 443 (QUIC/HTTP3)                            |
  +--------------------------------------------------------------+
```

---

## Related Topics
- [Networking Fundamentals](./networking.md) - Sockets, ports, OS internals
- QUIC Protocol - Google's UDP-based reliable protocol (HTTP/3)
- SCTP - Stream Control Transmission Protocol (alternative to TCP)
- DCCP - Datagram Congestion Control Protocol
