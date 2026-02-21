package chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.HashSet;

/**
 * A multithreaded chat room server.  When a client connects the
 * server requests a screen name by sending the client the
 * text "SUBMITNAME", and keeps requesting a name until
 * a unique one is received.  After a client submits a unique
 * name, the server acknowledges with "NAMEACCEPTED".  Then
 * all messages from that client will be broadcast to all other
 * clients that have submitted a unique screen name.  The
 * broadcast messages are prefixed with "MESSAGE ".
 *
 * Because this is just a teaching example to illustrate a simple
 * chat server, there are a few features that have been left out.
 * Two are very useful and belong in production code:
 *
 *     1. The protocol should be enhanced so that the client can
 *        send clean disconnect messages to the server.
 *
 *     2. The server should do some logging.
 */
public class ChatServer {

    private static final int PORT = 9001;

    /**
     * The set of all names of clients in the chat room.  Maintained
     * so that we can check that new clients are not registering name
     * already in use.
     */
    private static HashSet<String> names = new HashSet<String>();
    /**
     * The set of all the print writers for all the clients.  This
     * set is kept so we can easily broadcast messages.
     */
    private static HashSet<PrintWriter> writers = new HashSet<PrintWriter>();
    private static HashMap<String, PrintWriter> clientMap = new HashMap<String, PrintWriter>();

    public static void main(String[] args) throws Exception {
        System.out.println("The chat server is running.");
        // Create a ServerSocket to listen for incoming connection requests
        ServerSocket listener = new ServerSocket(PORT);
        try {
            while (true) {
                // Wait for a client to connect
                Socket socket = listener.accept();
                // Spawn a new handler thread to manage each client connection concurrently
                Thread handlerThread = new Thread(new Handler(socket));
                handlerThread.start();
            }
        } finally {
            listener.close();
        }
    }

    private static class Handler implements Runnable {
        private String name;
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        public Handler(Socket socket) {
            this.socket = socket;
        }
        /**
         * Services this thread's client by repeatedly requesting a
         * screen name until a unique one has been submitted, then
         * acknowledges the name and registers the output stream for
         * the client in a global set, then repeatedly gets inputs and
         * broadcasts them.
         */
        public void run() {
            try {
                // Initialize I/O streams for communicating with the client
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Request a name from this client.  Keep requesting until
                // a name is submitted that is not already used.  Note that
                // checking for the existence of a name and adding the name
                // must be done while locking the set of names.
                while (true) {
                    out.println("SUBMITNAME");
                    name = in.readLine();
                    if (name == null) {
                        return;
                    }
                    
                    // Task 5: Ensure thread safety of the shared variable 'names'.
                    synchronized (names) {
                        if (!name.isEmpty() && !names.contains(name)) {
                            names.add(name);
                            // Task 6: Add name to HashMap for point-to-point messaging
                            clientMap.put(name, out);
                            break;
                        }
                    }
                 }

                // Acknowledge name acceptance and register the output stream for broadcasting.
                out.println("NAMEACCEPTED");
                synchronized (writers) {
                    writers.add(out);
                }
                
                // Task 9: Broadcast the updated user list whenever a new client joins.
                broadcastUserList();

                // Communication phase: Receive messages and route them.
                while (true) {
                    String input = in.readLine();
                    if (input == null) {
                        return;
                    }
                    
                    // Task 6: Enable point-to-point messages using the format "Receiver>>Message".
                    if (input.contains(">>")) {
                        String[] parts = input.split(">>", 2);
                        String receiver = parts[0];
                        String message = parts[1];

                        synchronized (names) {
                            PrintWriter writer = clientMap.get(receiver);
                            // If receiver exists, send to them and echo to the sender.
                            if (writer != null) {
                                writer.println("MESSAGE " + name + ": " + message);
                                out.println("MESSAGE " + name + ": " + message); 
                            }
                        }
                    } else {
                        // Standard broadcast feature for general messages
                        synchronized (writers) {
                            for (PrintWriter writer : writers) {
                                writer.println("MESSAGE " + name + ": " + input);
                            }
                        }
                    }
                }
            }
            // Handle abrupt client disconnections without crashing the server thread.
            catch (SocketException e) {
                System.out.println("Connection lost for client: " + name);
            }
            catch (IOException e) {
                System.out.println(e);
            } finally {
            	// This client is going down!  Remove its name and its print
                // writer from the sets, and close its socket.
                if (name != null) {
                    synchronized (names) {
                        names.remove(name);
                        clientMap.remove(name);
                    }
                    // Task 9: Update lists for remaining clients after a departure.
                    broadcastUserList();
                }
                if (out != null) {
                    synchronized (writers) {
                        writers.remove(out);
                    }
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore closing errors.
                }
            }
        }

        /**
         * Task 9: Sends the current list of logged-in users to all clients.
         * Protocol prefix: "USERLIST" followed by comma-separated names.
         */
        private void broadcastUserList() {
            synchronized (names) {
                StringBuilder sb = new StringBuilder("USERLIST ");
                for (String n : names) {
                    sb.append(n).append(",");
                }
                String listMessage = sb.toString();
                synchronized (writers) {
                    for (PrintWriter writer : writers) {
                        writer.println(listMessage);
                    }
                }
            }
        }
    }
}