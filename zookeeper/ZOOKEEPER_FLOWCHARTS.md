# ZooKeeper Coordination Flowcharts

This document contains detailed flowcharts for understanding ZooKeeper's coordination mechanisms.

## 1. Client Connection and Session Establishment

```mermaid
flowchart TD
    Start([Client Starts]) --> Connect[Create ZooKeeper Object]
    Connect --> DNS[Resolve Server Addresses]
    DNS --> Pick[Pick Random Server]
    Pick --> TCP[Establish TCP Connection]
    
    TCP -->|Success| Auth{Need Auth?}
    TCP -->|Failure| Retry[Try Next Server]
    Retry --> Pick
    
    Auth -->|Yes| SendAuth[Send Auth Info]
    Auth -->|No| SendConnect[Send Connect Request]
    SendAuth --> SendConnect
    
    SendConnect --> ServerProcess[Server Processes Request]
    ServerProcess --> CreateSession[Create/Restore Session]
    CreateSession --> AssignID[Assign Session ID]
    AssignID --> SetTimeout[Set Session Timeout]
    SetTimeout --> Response[Send Connect Response]
    Response --> Connected([Client Connected])
    
    Connected --> Heartbeat[Start Heartbeat Thread]
    Heartbeat --> SendPing[Send Ping]
    SendPing --> Wait[Wait timeout/3]
    Wait --> CheckAlive{Connection Alive?}
    CheckAlive -->|Yes| SendPing
    CheckAlive -->|No| Reconnect[Try Reconnect]
    Reconnect --> Pick
```

## 2. Write Operation Flow (Create Node Example)

```mermaid
flowchart TD
    Client([Client: zk.create]) --> Serialize[Serialize Create Request]
    Serialize --> Send[Send to Server]
    Send --> NIO[NIOServerCnxn Receives]
    
    NIO --> Deserialize[Deserialize Request]
    Deserialize --> Submit[Submit to Request Processor]
    
    Submit --> Prep[PrepRequestProcessor]
    Prep --> Validate{Valid Request?}
    Validate -->|No| Error[Send Error Response]
    Validate -->|Yes| Transform[Transform to Txn]
    
    Transform --> SetHeaders[Set Zxid & Headers]
    SetHeaders --> Sync[SyncRequestProcessor]
    
    Sync --> Log[Write to Transaction Log]
    Log --> Snapshot{Need Snapshot?}
    Snapshot -->|Yes| TakeSnap[Take Snapshot]
    Snapshot -->|No| Continue1[Continue]
    TakeSnap --> Continue1
    
    Continue1 --> Final[FinalRequestProcessor]
    Final --> Apply[Apply to DataTree]
    
    Apply --> CreateNode[Create DataNode]
    CreateNode --> UpdateParent[Update Parent's Children]
    UpdateParent --> CheckEphemeral{Ephemeral?}
    CheckEphemeral -->|Yes| TrackSession[Add to Session Tracking]
    CheckEphemeral -->|No| Continue2[Continue]
    TrackSession --> Continue2
    
    Continue2 --> TriggerWatch[Trigger Watches]
    TriggerWatch --> BuildResponse[Build Response]
    BuildResponse --> SendResponse[Send to Client]
    SendResponse --> End([Operation Complete])
```

## 3. Leader Election Process

```mermaid
flowchart TD
    Start([Server Starts]) --> Init[Initialize Election]
    Init --> SetLooking[State = LOOKING]
    SetLooking --> CreateVote[Vote for Self]
    
    CreateVote --> Broadcast[Broadcast Vote to All]
    Broadcast --> WaitVotes[Wait for Votes]
    
    WaitVotes --> ReceiveVote{Receive Vote?}
    ReceiveVote -->|Timeout| CheckQuorum1{Have Quorum?}
    ReceiveVote -->|Yes| CompareVote{Better Vote?}
    
    CompareVote -->|Yes| UpdateVote[Update My Vote]
    CompareVote -->|No| KeepVote[Keep Current Vote]
    UpdateVote --> Broadcast
    KeepVote --> AddSupport[Add to Vote Count]
    
    AddSupport --> CheckQuorum2{Have Quorum?}
    CheckQuorum2 -->|No| WaitVotes
    CheckQuorum2 -->|Yes| CheckLeader{Am I Leader?}
    
    CheckQuorum1 -->|No| WaitVotes
    CheckQuorum1 -->|Yes| CheckLeader
    
    CheckLeader -->|Yes| BecomeLeader[State = LEADING]
    CheckLeader -->|No| BecomeFollower[State = FOLLOWING]
    
    BecomeLeader --> StartLead[Start Leader Services]
    BecomeFollower --> ConnectLeader[Connect to Leader]
    
    StartLead --> WaitFollowers[Wait for Followers]
    WaitFollowers --> Sync[Sync with Followers]
    Sync --> Ready1([Leader Ready])
    
    ConnectLeader --> SyncWithLeader[Sync with Leader]
    SyncWithLeader --> Ready2([Follower Ready])
```

### Vote Comparison Logic

```mermaid
flowchart TD
    Compare([Compare Votes]) --> CheckEpoch{n.epoch > my.epoch?}
    CheckEpoch -->|Yes| SelectN[Select N's Vote]
    CheckEpoch -->|No| CheckEpoch2{n.epoch < my.epoch?}
    
    CheckEpoch2 -->|Yes| SelectMy[Keep My Vote]
    CheckEpoch2 -->|No| CheckZxid{n.zxid > my.zxid?}
    
    CheckZxid -->|Yes| SelectN
    CheckZxid -->|No| CheckZxid2{n.zxid < my.zxid?}
    
    CheckZxid2 -->|Yes| SelectMy
    CheckZxid2 -->|No| CheckSid{n.sid > my.sid?}
    
    CheckSid -->|Yes| SelectN
    CheckSid -->|No| SelectMy
    
    SelectN --> Return1([Return N's Vote])
    SelectMy --> Return2([Return My Vote])
```

## 4. Distributed Lock Algorithm

```mermaid
flowchart TD
    Start([Client Wants Lock]) --> Create[Create Sequential Ephemeral Node]
    Create --> GetChildren[Get All Children of Lock Path]
    GetChildren --> Sort[Sort Children by Sequence]
    
    Sort --> CheckLowest{Am I Lowest?}
    CheckLowest -->|Yes| AcquiredLock([Lock Acquired])
    CheckLowest -->|No| FindPrev[Find Previous Node]
    
    FindPrev --> WatchPrev[Set Watch on Previous]
    WatchPrev --> WaitWatch[Wait for Watch Event]
    
    WaitWatch --> WatchFired{Watch Fired?}
    WatchFired -->|Node Deleted| GetChildren
    WatchFired -->|Connection Lost| Reconnect[Reconnect]
    
    Reconnect --> CheckExists{My Node Exists?}
    CheckExists -->|No| Create
    CheckExists -->|Yes| GetChildren
    
    AcquiredLock --> DoWork[Perform Protected Work]
    DoWork --> Release[Delete My Node]
    Release --> NotifyNext[Trigger Next Waiter's Watch]
    NotifyNext --> End([Lock Released])
```

## 5. Two-Phase Commit (2PC) for Writes in Quorum Mode

```mermaid
flowchart TD
    Client([Client Write Request]) --> Follower[Follower Receives]
    Follower --> Forward[Forward to Leader]
    
    Forward --> Leader[Leader Receives]
    Leader --> Propose[Create Proposal with Zxid]
    
    Propose --> LogLeader[Leader Logs Proposal]
    LogLeader --> Broadcast[Broadcast PROPOSAL to All]
    
    Broadcast --> F1[Follower 1]
    Broadcast --> F2[Follower 2]  
    Broadcast --> F3[Follower 3]
    
    F1 --> LogF1[Log Proposal]
    F2 --> LogF2[Log Proposal]
    F3 --> LogF3[Log Proposal]
    
    LogF1 --> AckF1[Send ACK]
    LogF2 --> AckF2[Send ACK]
    LogF3 --> AckF3[Send ACK]
    
    AckF1 --> Collect[Leader Collects ACKs]
    AckF2 --> Collect
    AckF3 --> Collect
    
    Collect --> CheckQuorum{Quorum of ACKs?}
    CheckQuorum -->|No| Wait[Wait for More]
    CheckQuorum -->|Yes| Commit[Broadcast COMMIT]
    
    Wait --> Collect
    
    Commit --> ApplyLeader[Leader Applies Txn]
    Commit --> CF1[Follower 1 Commits]
    Commit --> CF2[Follower 2 Commits]
    Commit --> CF3[Follower 3 Commits]
    
    CF1 --> ApplyF1[Apply to DataTree]
    CF2 --> ApplyF2[Apply to DataTree]
    CF3 --> ApplyF3[Apply to DataTree]
    
    ApplyLeader --> Response[Send Response to Client]
    Response --> End([Write Complete])
```

## 6. Watch Mechanism Flow

```mermaid
flowchart TD
    Client([Client Sets Watch]) --> Request[getData/exists/getChildren with Watch]
    Request --> Server[Server Processes]
    
    Server --> AddWatch[Add Watch to WatchManager]
    AddWatch --> StoreC2P[Store Client->Path Mapping]
    StoreC2P --> StoreP2C[Store Path->Client Mapping]
    
    StoreP2C --> ReturnData[Return Current Data]
    ReturnData --> ClientWait([Client Has Data + Watch Set])
    
    ClientWait --> OtherOp[Another Client Modifies Node]
    OtherOp --> Modify[Apply Modification]
    
    Modify --> CheckWatches{Any Watches on Path?}
    CheckWatches -->|No| Done1([Operation Complete])
    CheckWatches -->|Yes| GetWatchers[Get All Watchers]
    
    GetWatchers --> CreateEvent[Create WatchedEvent]
    CreateEvent --> SendNotify[Send to Each Watcher]
    
    SendNotify --> RemoveWatch[Remove Watch Entry]
    RemoveWatch --> ClientRecv[Client Receives Event]
    
    ClientRecv --> ProcessEvent[Process in Watcher]
    ProcessEvent --> ReWatch{Re-watch?}
    ReWatch -->|Yes| Request
    ReWatch -->|No| Done2([Watch Complete])
```

## 7. Session Lifecycle

```mermaid
flowchart TD
    Create([Session Created]) --> Active[Session Active]
    Active --> Ping[Client Sends Ping]
    
    Ping --> UpdateTime[Update Last Heard Time]
    UpdateTime --> CheckExpiry{Check All Sessions}
    
    CheckExpiry --> NextBucket[Move to Next Time Bucket]
    NextBucket --> Wait[Wait Tick Time]
    Wait --> CheckExpiry
    
    Active --> NoComm[No Communication]
    NoComm --> Timeout{Timeout Exceeded?}
    
    Timeout -->|No| Active
    Timeout -->|Yes| MarkExpiring[Mark Session Expiring]
    
    MarkExpiring --> CloseSession[Close Session Request]
    CloseSession --> QueueClose[Queue Close Txn]
    
    QueueClose --> Process[Process Close Txn]
    Process --> RemoveEphem[Remove Ephemeral Nodes]
    
    RemoveEphem --> TriggerWatches[Trigger Delete Watches]
    TriggerWatches --> CleanMaps[Clean Session Maps]
    
    CleanMaps --> NotifyClient[Notify Client Disconnected]
    NotifyClient --> End([Session Closed])
    
    Active --> ClientClose[Client Explicitly Closes]
    ClientClose --> CloseSession
```

## 8. Barrier Implementation Flow

```mermaid
flowchart TD
    Start([N Clients Start]) --> Create[Each Creates Ephemeral Node]
    Create --> Count[Count Children Nodes]
    
    Count --> CheckN{Count >= N?}
    CheckN -->|Yes| Enter[All Enter Barrier]
    CheckN -->|No| SetWatch[Set Watch on Parent]
    
    SetWatch --> WaitNotify[Wait for Notification]
    WaitNotify --> WatchEvent{Watch Event}
    
    WatchEvent --> Count
    
    Enter --> DoWork[Perform Synchronized Work]
    DoWork --> Delete[Delete Own Node]
    
    Delete --> CheckEmpty{All Nodes Deleted?}
    CheckEmpty -->|No| WaitOthers[Wait for Others]
    CheckEmpty -->|Yes| Exit([Exit Barrier])
    
    WaitOthers --> WatchDelete[Watch for Deletions]
    WatchDelete --> CheckEmpty
```

## 9. Request Processing Pipeline

```mermaid
flowchart LR
    subgraph "Standalone Server"
        Req1[Request] --> Prep1[PrepRequestProcessor]
        Prep1 --> Sync1[SyncRequestProcessor]
        Sync1 --> Final1[FinalRequestProcessor]
        Final1 --> Resp1[Response]
    end
    
    subgraph "Leader"
        Req2[Request] --> Prep2[PrepRequestProcessor]
        Prep2 --> Proposal[ProposalRequestProcessor]
        Proposal --> Commit2[CommitProcessor]
        Commit2 --> ToBeApplied[ToBeAppliedProcessor]
        ToBeApplied --> Final2[FinalRequestProcessor]
        Final2 --> Resp2[Response]
    end
    
    subgraph "Follower"
        Req3[Request] --> FollowerRP[FollowerRequestProcessor]
        FollowerRP --> Commit3[CommitProcessor]
        Commit3 --> Final3[FinalRequestProcessor]
        Final3 --> Resp3[Response]
        
        FollowerRP -.->|Write Requests| SendLeader[Send to Leader]
    end
```

## 10. Quota Enforcement Flow

```mermaid
flowchart TD
    Create([Create Node Request]) --> Check[Check Parent Path]
    Check --> FindQuota{Find Quota Node}
    
    FindQuota -->|Not Found| NoQuota[No Quota Enforcement]
    FindQuota -->|Found| GetStats[Get Current Stats]
    
    GetStats --> CalcNew[Calculate New Stats]
    CalcNew --> CompareCount{Count Exceeded?}
    
    CompareCount -->|Yes| Reject1[Reject: Too Many Nodes]
    CompareCount -->|No| CompareBytes{Bytes Exceeded?}
    
    CompareBytes -->|Yes| Reject2[Reject: Too Much Data]
    CompareBytes -->|No| Allow[Allow Creation]
    
    NoQuota --> Allow
    Allow --> CreateNode[Create Node]
    CreateNode --> UpdateStats[Update Quota Stats]
    UpdateStats --> Done([Complete])
    
    Reject1 --> Error1([Quota Exception])
    Reject2 --> Error2([Quota Exception])
```

## Key Insights from Flowcharts

1. **Session Management is Continuous** - Heartbeats maintain liveness
2. **Leader Election is Deterministic** - Higher epoch/zxid/sid wins
3. **Writes are Coordinated** - 2PC ensures consistency across quorum
4. **Watches are One-Time** - Must re-register after trigger
5. **Locks are Fair** - Sequential ordering ensures FIFO
6. **Processing is Pipelined** - Different stages can work in parallel
7. **Failures are Handled** - Reconnection and session transfer built-in

These flowcharts represent the actual code flow in ZooKeeper's implementation and can be used to understand both the client-visible behavior and internal coordination mechanisms.