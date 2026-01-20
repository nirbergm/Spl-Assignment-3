package bgu.spl.net.api;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import bgu.spl.net.impl.data.Database;
import bgu.spl.net.impl.data.LoginStatus;
import bgu.spl.net.srv.Connections;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String>{

    private int connectionId;
    private Connections<String> connections;
    private Map<Integer, String> activeSubscriptions = new HashMap<>();
    private static AtomicInteger messageIdCounter = new AtomicInteger(0);
    String currentUser;
    boolean shouldTerminate = false;

    public void start(int connectionId, Connections<String> connections){
        this.connectionId = connectionId;
        this.connections = connections;
    }

    public String process(String message) {
        String[] lines = message.split("\n");
        if (lines.length == 0) {
            return null;
        }
        String command = lines[0];
        switch (command) {
            case "CONNECT":
                processConnect(lines);
                break;
            case "SEND":
                processSend(message, lines);
                break;
            case "SUBSCRIBE":
                processSubscribe(lines);
                break;
            case "UNSUBSCRIBE":
                processUnsubscribe(lines);
                break;
            case "DISCONNECT":
                processDisconnect(lines);
                break;
            default:
                System.out.println("Error: Unknown command " + command);
        }
        return null;
    }

    private String getHeaderValue(String[] lines, String headerName) {
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                break; 
            }
            if (line.startsWith(headerName + ":")) {
                return line.substring((headerName + ":").length()).trim();
            }
        }
        return null; 
    }

    public void processConnect(String[] lines) {
        String receipt = getHeaderValue(lines, "receipt");
        String login = getHeaderValue(lines, "login");
        String passcode = getHeaderValue(lines, "passcode");
        if (login == null || passcode == null) {
            sendError("Malformed Frame", "Missing login or passcode header", receipt);
            return;
        }
        Database db = Database.getInstance();
        LoginStatus status = db.login(connectionId, login, passcode);
        if (status == LoginStatus.LOGGED_IN_SUCCESSFULLY || status == LoginStatus.ADDED_NEW_USER) {
            this.currentUser = login;
            connections.send(connectionId, "CONNECTED\nversion:1.2\n\n\u0000");
        } else {
            String errorHeader = "Unknown Error";
            String errorBody = "Unknown Error";
            if (status == LoginStatus.WRONG_PASSWORD) {
                errorHeader = "Wrong password";
                errorBody = "Password does not match the username";
            } else if (status == LoginStatus.ALREADY_LOGGED_IN) {
                errorHeader = "User already logged in";
                errorBody = "User is already logged in active session";
            } else if (status == LoginStatus.CLIENT_ALREADY_CONNECTED) {
                errorHeader = "Client already connected";
                errorBody = "This connection is already associated with a user";
            }
            
            sendError(errorHeader, errorBody, receipt);
        }
    }

    public void processSend(String message, String[] lines) {
        String destination = getHeaderValue(lines, "destination");
        String receipt = getHeaderValue(lines, "receipt");
        String filename = getHeaderValue(lines, "file"); // נסה לקרוא את שם הקובץ
        if (filename != null) {
            // אם יש שם קובץ, תעד אותו ב-DB
            Database.getInstance().trackFileUpload(currentUser, filename, destination);
        }
        if (currentUser == null) {
            sendError("User not logged in", "You must perform login before sending messages.", receipt);
            return; 
        }
        if (destination == null) {
            sendError("Malformed Frame", "Missing 'destination' header in SEND frame.", receipt);
            return;
        }
        int msgId = messageIdCounter.incrementAndGet();
        String[] parts = message.split("\n\n", 2);
        String body = ""; 
        if (parts.length > 1) {
            body = parts[1]; 
        }
        if (!isSubscribed(destination)) {
            sendError("Not subscribed", "You cannot send messages to a channel you are not subscribed to.", receipt);
            return;
        }
        String messageFrame = "MESSAGE\n" +
                          "destination:" + destination + "\n" +
                          "message-id:" + msgId + "\n" + 
                          "\n" +
                          body;
        connections.send(destination, messageFrame);
        if (receipt != null) {
           connections.send(connectionId, "RECEIPT\nreceipt-id:" + receipt + "\n\n");
        }
    }

    public boolean isSubscribed(String destination){
        return activeSubscriptions.containsValue(destination);
    }

    public void processSubscribe(String[] lines) {
        String destination = getHeaderValue(lines, "destination");
        String idStr = getHeaderValue(lines, "id");
        String receipt = getHeaderValue(lines, "receipt");
        if (currentUser == null) {
            sendError("User not logged in", "You must perform login before subscribing.", receipt);
            return;
        }
        if (destination == null || idStr == null) {
            sendError("Malformed Frame", "Missing 'destination' or 'id' header", receipt);
            return;
        }
        int subscriptionId;
        try {
            subscriptionId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            sendError("Malformed Frame", "The 'id' header must be a number", receipt);
            return;
        }
        activeSubscriptions.put(subscriptionId, destination);
        connections.subscribe(destination, connectionId, subscriptionId);
        if (receipt != null) {
           connections.send(connectionId, "RECEIPT\nreceipt-id:" + receipt + "\n\n");
        }
    }

    public void processUnsubscribe(String[] lines) {
        String idStr = getHeaderValue(lines, "id");
        String receipt = getHeaderValue(lines, "receipt");
        if (currentUser == null) {
            sendError("User not logged in", "You must perform login before unsubscribing.", receipt);
            return;
        }
        if (idStr == null) {
            sendError("Malformed Frame", "Missing 'id' header", receipt);
            return;
        }
        int subscriptionId;
        try {
            subscriptionId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            sendError("Malformed Frame", "The 'id' header must be a number", receipt);
            return;
        }
        String channel = activeSubscriptions.remove(subscriptionId);
        if (channel == null) {
            sendError("Not Subscribed", "No subscription found with id " + subscriptionId, receipt);
            return;
        }
        connections.unsubscribe(channel, connectionId);
        if (receipt != null) {
           connections.send(connectionId, "RECEIPT\nreceipt-id:" + receipt + "\n\n");
        }

    }

    public void processDisconnect(String[] lines) {
        String receiptId = getHeaderValue(lines, "receipt");

        if (currentUser == null) {
            sendError("User not logged in", "You cannot disconnect if you are not logged in", receiptId);
            return;
        }
        if (receiptId == null) {
            sendError("Malformed Frame", "Missing 'receipt' header in DISCONNECT frame", null);
            return;
        }
        Database.getInstance().logout(connectionId);
        for (String channel : activeSubscriptions.values()) {
            connections.unsubscribe(channel, connectionId);
        }
        activeSubscriptions.clear();
        connections.send(connectionId, "RECEIPT\nreceipt-id:" + receiptId + "\n\n");
        shouldTerminate = true; 
    }

    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    private void sendError(String messageHeader, String errorBody, String recieptId) {
        String errorFrame = "ERROR\n";
        if (recieptId != null) {
            errorFrame += "receipt-id:" + recieptId + "\n";
        }
        errorFrame += "message:" + messageHeader + "\n\n" + errorBody;
        shouldTerminate = true;
        connections.send(connectionId, errorFrame);

    }
}
