import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Simple Server inspired by ZooKeeper's architecture
 * 
 * Features:
 * - Single accept thread listening on port
 * - NIO for handling multiple connections
 * - Request/response protocol
 * - Simple command processing
 */
public class MySimpleServer {
    
    private final int port;
    private final int maxConnections;
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private volatile boolean running = false;
    
    // Thread pool for processing requests (like ZooKeeper's worker threads)
    private ExecutorService requestProcessors;
    
    // Connection tracking (like ZooKeeper does)
    private final Map<SocketChannel, ClientConnection> connections = new ConcurrentHashMap<>();
    
    public MySimpleServer(int port, int maxConnections) {
        this.port = port;
        this.maxConnections = maxConnections;
        this.requestProcessors = Executors.newFixedThreadPool(4); // Like ZooKeeper's worker threads
    }
    
    /**
     * Configure and start the server (similar to ZooKeeper's configure + startup)
     */
    public void start() throws IOException {
        System.out.println("Starting server on port " + port + "...");
        
        // 1. Create server socket channel (like ZooKeeper)
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress(port));
        
        // 2. Create selector for NIO (like ZooKeeper's selector threads)
        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        running = true;
        
        // 3. Start the accept thread (like ZooKeeper's accept thread)
        Thread acceptThread = new Thread(this::acceptLoop, "AcceptThread");
        acceptThread.start();
        
        System.out.println("Server started! Listening on port " + port);
        System.out.println("Try: telnet localhost " + port);
        System.out.println("Commands: GET /path, SET /path value, LIST, QUIT");
    }
    
    /**
     * Main accept loop (similar to ZooKeeper's NIOServerCnxnFactory)
     */
    private void acceptLoop() {
        while (running) {
            try {
                // Wait for events (like ZooKeeper's selector.select())
                int readyChannels = selector.select(1000);
                
                if (readyChannels == 0) continue;
                
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    
                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    }
                    
                    keyIterator.remove();
                }
            } catch (IOException e) {
                System.err.println("Error in accept loop: " + e.getMessage());
            }
        }
    }
    
    /**
     * Handle new connections (like ZooKeeper's accept handling)
     */
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        
        if (clientChannel != null) {
            if (connections.size() >= maxConnections) {
                System.out.println("Max connections reached, rejecting client");
                clientChannel.close();
                return;
            }
            
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);
            
            ClientConnection connection = new ClientConnection(clientChannel);
            connections.put(clientChannel, connection);
            
            System.out.println("New client connected: " + clientChannel.getRemoteAddress());
            
            // Send welcome message (like ZooKeeper's session establishment)
            sendResponse(clientChannel, "Welcome to MySimpleServer! Type HELP for commands.\\n");
        }
    }
    
    /**
     * Handle client requests (like ZooKeeper's request processing)
     */
    private void handleRead(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientConnection connection = connections.get(clientChannel);
        
        if (connection == null) return;
        
        try {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            int bytesRead = clientChannel.read(buffer);
            
            if (bytesRead == -1) {
                // Client disconnected
                handleDisconnect(clientChannel);
                return;
            }
            
            if (bytesRead > 0) {
                buffer.flip();
                String request = new String(buffer.array(), 0, buffer.limit()).trim();
                
                // Process request in thread pool (like ZooKeeper's RequestProcessor)
                requestProcessors.submit(() -> processRequest(clientChannel, request));
            }
        } catch (IOException e) {
            System.err.println("Error reading from client: " + e.getMessage());
            handleDisconnect(clientChannel);
        }
    }
    
    /**
     * Process client requests (similar to ZooKeeper's request processing chain)
     */
    private void processRequest(SocketChannel clientChannel, String request) {
        System.out.println("Processing request: " + request);
        
        String[] parts = request.split("\\s+");
        String command = parts[0].toUpperCase();
        String response;
        
        try {
            switch (command) {
                case "GET":
                    response = handleGet(parts);
                    break;
                case "SET":
                    response = handleSet(parts);
                    break;
                case "LIST":
                    response = handleList();
                    break;
                case "HELP":
                    response = "Commands:\\n" +
                             "  GET /path - Get data at path\\n" +
                             "  SET /path value - Set data at path\\n" +
                             "  LIST - List all paths\\n" +
                             "  QUIT - Disconnect\\n";
                    break;
                case "QUIT":
                    response = "Goodbye!\\n";
                    sendResponse(clientChannel, response);
                    handleDisconnect(clientChannel);
                    return;
                default:
                    response = "Unknown command: " + command + "\\nType HELP for available commands.\\n";
            }
            
            sendResponse(clientChannel, response + "\\n");
        } catch (Exception e) {
            sendResponse(clientChannel, "Error: " + e.getMessage() + "\\n");
        }
    }
    
    // Simple in-memory data store (like ZooKeeper's DataTree)
    private final Map<String, String> dataStore = new ConcurrentHashMap<>();
    
    private String handleGet(String[] parts) {
        if (parts.length < 2) {
            return "Usage: GET /path";
        }
        String path = parts[1];
        String value = dataStore.get(path);
        return value != null ? "Value: " + value : "Path not found: " + path;
    }
    
    private String handleSet(String[] parts) {
        if (parts.length < 3) {
            return "Usage: SET /path value";
        }
        String path = parts[1];
        String value = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
        dataStore.put(path, value);
        return "Set " + path + " = " + value;
    }
    
    private String handleList() {
        if (dataStore.isEmpty()) {
            return "No data stored";
        }
        StringBuilder sb = new StringBuilder("Stored data:\\n");
        for (Map.Entry<String, String> entry : dataStore.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(" = ").append(entry.getValue()).append("\\n");
        }
        return sb.toString();
    }
    
    /**
     * Send response to client
     */
    private void sendResponse(SocketChannel clientChannel, String response) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(response.getBytes());
            clientChannel.write(buffer);
        } catch (IOException e) {
            System.err.println("Error sending response: " + e.getMessage());
            handleDisconnect(clientChannel);
        }
    }
    
    /**
     * Handle client disconnect (like ZooKeeper's connection cleanup)
     */
    private void handleDisconnect(SocketChannel clientChannel) {
        try {
            System.out.println("Client disconnected: " + clientChannel.getRemoteAddress());
            connections.remove(clientChannel);
            clientChannel.close();
        } catch (IOException e) {
            System.err.println("Error closing client connection: " + e.getMessage());
        }
    }
    
    /**
     * Stop the server
     */
    public void stop() throws IOException {
        running = false;
        
        // Close all client connections
        for (SocketChannel channel : connections.keySet()) {
            channel.close();
        }
        connections.clear();
        
        // Close server
        if (selector != null) selector.close();
        if (serverChannel != null) serverChannel.close();
        
        requestProcessors.shutdown();
        System.out.println("Server stopped");
    }
    
    /**
     * Client connection tracking (like ZooKeeper's ServerCnxn)
     */
    private static class ClientConnection {
        private final SocketChannel channel;
        private final long connectTime;
        
        public ClientConnection(SocketChannel channel) {
            this.channel = channel;
            this.connectTime = System.currentTimeMillis();
        }
    }
    
    /**
     * Main method to run the server
     */
    public static void main(String[] args) {
        int port = 8888;
        int maxConnections = 100;
        
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }
        
        MySimpleServer server = new MySimpleServer(port, maxConnections);
        
        try {
            server.start();
            
            // Keep server running until user presses Enter
            System.out.println("Press Enter to stop the server...");
            System.in.read();
            
            server.stop();
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}