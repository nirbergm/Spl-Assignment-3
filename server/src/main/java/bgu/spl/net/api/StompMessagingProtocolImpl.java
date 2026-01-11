package bgu.spl.net.api;

import javax.print.DocFlavor.STRING;

import bgu.spl.net.impl.data.Database;
import bgu.spl.net.impl.data.LoginStatus;
import bgu.spl.net.srv.Connections;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String>{
    private int connectionId;
    private Connections<String> connections;
    String currentUser;
    boolean shouldTerminate = false;

    public void start(int connectionId, Connections<String> connections){
        this.connectionId = connectionId;
        this.connections = connections;
    }

    public void process(String message) {
        String[] lines = message.split("\n");
        if (lines.length == 0) {
            return;
        }
        String command = lines[0];
        switch (command) {
            case "CONNECT":
                processConnect(lines);
                break;
            case "SEND":
                processSend(lines);
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
        String login = getHeaderValue(lines, "login");
        String passcode = getHeaderValue(lines, "passcode");
        if (login == null || passcode == null) {
            System.out.println("login or password inncorrect");
            return;
        }
        Database db = Database.getInstance();
        LoginStatus status = db.login(connectionId, login, passcode);
        if (status == LoginStatus.LOGGED_IN_SUCCESSFULLY || status == LoginStatus.ADDED_NEW_USER) {
            this.currentUser = login;
            connections.send(connectionId, "CONNECTED\nversion:1.2\n\n");
        } else {
            String errorHeader = "";
            String errorBody = "";
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

        
            connections.send(connectionId, "ERROR\nmessage:" + errorHeader + "\n\n" + errorBody);
            shouldTerminate = true;
            connections.disconnect(connectionId);
    }
    }

    public void processSend(String[] lines) {

    }

    public void processSubscribe(String[] lines) {

    }

    public void processUnsubscribe(String[] lines) {

    }

    public void processDisconnect(String[] lines) {

    }

    public boolean shouldTerminate() {
        return false;
    }
}
