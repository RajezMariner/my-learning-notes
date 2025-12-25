import java.io.*;
import java.net.*;

/**
 * Simple client to test MySimpleServer
 */
public class TestClient {
    
    public static void main(String[] args) {
        String host = "localhost";
        int port = 8888;
        
        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))) {
            
            System.out.println("Connected to server at " + host + ":" + port);
            
            // Read welcome message
            String welcome = in.readLine();
            System.out.println("Server: " + welcome);
            
            // Interactive client
            String command;
            while (true) {
                System.out.print("Enter command (or 'quit'): ");
                command = userInput.readLine();
                
                if (command == null || command.equalsIgnoreCase("quit")) {
                    out.println("QUIT");
                    break;
                }
                
                // Send command to server
                out.println(command);
                
                // Read response
                String response = in.readLine();
                System.out.println("Server: " + response);
            }
            
        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }
}