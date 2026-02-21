package chatserver;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

/**
 * A simple Swing-based client for the chat server.  Graphically
 * it is a frame with a text field for entering messages and a
 * textarea to see the whole dialog.
 *
 * The client follows the Chat Protocol which is as follows.
 * When the server sends "SUBMITNAME" the client replies with the
 * desired screen name.  The server will keep sending "SUBMITNAME"
 * requests as long as the client submits screen names that are
 * already in use.  When the server sends a line beginning
 * with "NAMEACCEPTED" the client is now allowed to start
 * sending the server arbitrary strings to be broadcast to all
 * chatters connected to the server.  When the server sends a
 * line beginning with "MESSAGE " then all characters following
 * this string should be displayed in its message area.
 */
public class ChatClient {

    private BufferedReader in;
    private PrintWriter out;
    private JFrame frame = new JFrame("Chatter");
    private JTextField textField = new JTextField(40);
    private JTextArea messageArea = new JTextArea(8, 40);
    
    // Task 8: List box to show all logged in clients
    private DefaultListModel<String> listModel = new DefaultListModel<>();
    private JList<String> userList = new JList<>(listModel);
    
    // Task 10: Checkbox to enable broadcasting or ignore specific selections 
    private JCheckBox broadcastCheck = new JCheckBox("Broadcast", true);

    /**
     * Constructs the client by laying out the GUI and registering a
     * listener with the textfield so that pressing Return in the
     * listener sends the textfield contents to the server.  Note
     * however that the textfield is initially NOT editable, and
     * only becomes editable AFTER the client receives the NAMEACCEPTED
     * message from the server.
     */
    public ChatClient() {

        // Layout GUI
        textField.setEditable(false);
        messageArea.setEditable(false);
        
        // Task 10: Selection mode allowed for multiple items to enable multicasting
        userList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        // Arrange the North panel to hold both text field and broadcast checkbox
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(textField, BorderLayout.CENTER);
        northPanel.add(broadcastCheck, BorderLayout.EAST);
        
        frame.getContentPane().add(northPanel, BorderLayout.NORTH);
        frame.getContentPane().add(new JScrollPane(messageArea), BorderLayout.CENTER);
        
        // Task 8: User list positioned on the right 
        frame.getContentPane().add(new JScrollPane(userList), BorderLayout.EAST);
        
        frame.pack();

        /**
         * Responds to pressing the enter key in the textfield by sending
         * the contents of the text field to the server.    Then clear
         * the text area in preparation for the next message.
         */
        textField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String messageText = textField.getText();
                
                // If Broadcast is checked, ignore list selection 
                if (broadcastCheck.isSelected()) {
                    out.println(messageText);
                } else {
                    // Task 6 & 10: Send to specific selected receivers
                    List<String> selectedUsers = userList.getSelectedValuesList();
                    
                    if (!selectedUsers.isEmpty()) {
                        for (String user : selectedUsers) {
                            // Protocol: ReceiverName>>Message
                            out.println(user + ">>" + messageText);
                        }
                    } else {
                        // Feedback if private messaging is intended but no user is selected
                        messageArea.append("SYSTEM: Please select a user or enable 'Broadcast'.\n");
                    }
                }
                textField.setText("");
            }
        });
    }

    /**
     * Prompts for and returns the server IP address
     */
    private String getServerAddress() {
        return JOptionPane.showInputDialog(
            frame,
            "Enter IP Address of the Server:",
            "Welcome to the Chatter",
            JOptionPane.QUESTION_MESSAGE);
    }

    /**
     * Prompts for and returns a unique screen name
     */
    private String getName() {
        return JOptionPane.showInputDialog(
            frame,
            "Choose a screen name:",
            "Screen name selection",
            JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * Connects to the server and enters the main processing loop.
     */
    private void run() throws IOException {

        // Make connection and initialize I/O streams
        String serverAddress = getServerAddress();
        Socket socket = new Socket(serverAddress, 9001);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        /**
         * Processing loop for incoming server messages.
         */
        while (true) {
            String line = in.readLine();
            if (line == null) break;

            if (line.startsWith("SUBMITNAME")) {
                // Server requests a name until a unique one is provided
                out.println(getName());
            } else if (line.startsWith("NAMEACCEPTED")) {
                // Enable typing once the server accepts the name
                textField.setEditable(true);
            } else if (line.startsWith("MESSAGE")) {
                // Display standard or private messages in the area
                messageArea.append(line.substring(8) + "\n");
            } else if (line.startsWith("USERLIST")) {
                // Task 9: Automatically update the list box 
                String[] users = line.substring(9).split(",");
                
                // Swing components must be updated on the Event Dispatch Thread
                SwingUtilities.invokeLater(() -> {
                    listModel.clear();
                    for (String u : users) {
                        if (!u.isEmpty()) {
                            listModel.addElement(u);
                        }
                    }
                });
            }
        }
    }

    /**
     * Main entry point to run the client.
     */
    public static void main(String[] args) throws Exception {
        ChatClient client = new ChatClient();
        client.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        client.frame.setVisible(true);
        client.run();
    }
}