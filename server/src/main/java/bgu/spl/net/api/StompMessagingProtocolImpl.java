package bgu.spl.net.api;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import bgu.spl.net.impl.data.Database;
import bgu.spl.net.impl.data.LoginStatus;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {
    private int connectionId;
    private ConnectionsImpl<String> connections;
    private ConcurrentHashMap<String,String> subscriptionMap = new ConcurrentHashMap<>(); //subscriptionID : channel
    private boolean shouldTerminate;
    private String loggedInUser;
    private AtomicInteger messageIdCounter= new AtomicInteger(0);

    @Override
    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId; 
        this.connections = (ConnectionsImpl<String>) connections; 
    }

    @Override
    public String process(String message) {
        String[] lines = message.split("\n");
        if(lines.length == 0) return null;

        String command = lines[0];
        
        switch (command) {
            case "CONNECT":
                return processConnect(lines,message);
            case "SEND" :
                return processSend(lines,message);
            case "SUBSCRIBE":
                return processSubscribe(lines,message);
            case "UNSUBSCRIBE":
                return processUnsubscribe(lines, message);
            case "DISCONNECT":
                return processDisConnect(lines,message);
            default: System.out.println("Error: Unknown command " + command);
                break;
        }



        return null;
    }
    /**
     * return HeaderName appropriate value.
     **/
    private String getHeaderVal(String[] lines, String HeaderName){ 

        if(lines == null || HeaderName == null) throw new NullPointerException("paramters cant be null");

        for(int i = 1; i < lines.length; i++){

            if(lines[i].trim().isEmpty()) break;

            int colonIndex = lines[i].indexOf(":"); //check colon index for sepreating key and value
            if(colonIndex == -1) continue;
            
            String currHeaderName = lines[i].substring(0,colonIndex).trim();
            if(HeaderName.equalsIgnoreCase(currHeaderName)){
                return lines[i].substring(colonIndex + 1).trim(); //return the value of the right header
            }
        }
        return null;
    }

    private String processConnect(String[] lines,String message){

        String login = getHeaderVal(lines, "login");
        String passcode = getHeaderVal(lines, "passcode");
        String receiptId = getHeaderVal(lines, "receipt-id:");

        LoginStatus status = Database.getInstance().login(connectionId, login, passcode);

        switch (status) {
            case ADDED_NEW_USER:
            case LOGGED_IN_SUCCESSFULLY:
                this.loggedInUser = login; 
                return createConnectedFrame();

            case WRONG_PASSWORD:
                return createErrorFrame(message, "Wrong password", null, receiptId);

            case ALREADY_LOGGED_IN:
                return createErrorFrame(message, "User already logged in", null, receiptId);

            case CLIENT_ALREADY_CONNECTED:
                return createErrorFrame(message, "The client is already logged in, log out before trying again", null, receiptId);
                
            default:
                return null;
        }
    }


    private String processSend(String[] lines,String message){
        String destination = getHeaderVal(lines, "destination");
        String receipt = getHeaderVal(lines, "receipt");

        if (loggedInUser == null) {
            return createErrorFrame(message, "User not logged in", "You must login before sending messages", receipt);
        }

        if (destination == null) {
            return createErrorFrame(message, "Malformed frame", "Missing destination header", receipt);
        }

        if (!subscriptionMap.containsValue(destination)) { //check if subscribed
            return createErrorFrame(message, "Not subscribed", "You are not subscribed to channel: " + destination, receipt);
        }

        int bodyStartIndex = message.indexOf("\n\n");
        String body = "";
        if (bodyStartIndex != -1) {
            body = message.substring(bodyStartIndex + 2);
        }

        int msgId = messageIdCounter.getAndIncrement();

        String messageFrame = createMessageFrame(destination, msgId, body);

        connections.send(destination, messageFrame);

        if(receipt!= null){
            return createReceiptFrame(receipt);
        }

        return null;
    }



    private String processSubscribe(String[] lines,String message){
        String destination = getHeaderVal(lines, "destination");
        String receipt = getHeaderVal(lines, "receipt");
        String id = getHeaderVal(lines, "id");

        if (loggedInUser == null) {
            return createErrorFrame(message, "User not logged in", "You must login before sending messages", receipt);
        }

        if (destination == null) {
            return createErrorFrame(message, "Malformed frame", "Missing destination header", receipt);
        }

        if(id == null){
            return createErrorFrame(message, "User didnt send id to make this subscription",null,receipt);
        }
        
        subscriptionMap.putIfAbsent(id, destination);
        connections.subscribe(connectionId, destination);

        if(receipt != null) return createReceiptFrame(receipt);

        return null;
    }


    private String processUnsubscribe(String[] lines,String message){

        String receipt = getHeaderVal(lines, "receipt");
        String id = getHeaderVal(lines, "id");

        if (loggedInUser == null) {
            return createErrorFrame(message, "User not logged in", "You must login before sending messages", receipt);
        }

        if(id == null){
            return createErrorFrame(message, "User didnt send id to identify this subscription",null,receipt);
        }
        if(!subscriptionMap.containsKey(id)){
            return createErrorFrame(message,"There is no suitable id subscribed",null,receipt);
        }

        String channel = subscriptionMap.remove(id);//get the channel we want to remove from

        connections.unSubscribe(connectionId,channel);

        if(receipt != null){
            return createReceiptFrame(receipt);
        }
        return null;
    }

    private String processDisConnect(String[] lines,String message){

        String receiptId = getHeaderVal(lines, "receipt");

        if ( loggedInUser == null) {
            return createErrorFrame(message,"User not logged in", "You cannot disconnect if you are not logged in",receiptId);
        }

        if (receiptId == null) {
            return createErrorFrame(message,"Malformed Frame", "Missing 'receipt' header in DISCONNECT frame", receiptId);
        }
        connections.disconnect(connectionId);
        Database.getInstance().logout(connectionId);
        
        shouldTerminate = true;        
        return createReceiptFrame(receiptId);

    }

    //Auxilary functions for creating frames
    private String createErrorFrame(String message,String shortErrorMessage,String fullErrorMessage, String recieptID){
        StringBuilder sb = new StringBuilder();
        sb.append("ERROR").append('\n');

        if(recieptID != null){
            sb.append("receipt-id: " + recieptID).append('\n');
        }
        sb.append("message: ");
        sb.append(shortErrorMessage).append('\n');
        sb.append('\n');
        sb.append("The message:" + '\n' + "------" + '\n');
        sb.append(message).append('\n');
        sb.append('\n');
        sb.append("------" + '\n');
        if(fullErrorMessage != null){
            sb.append(fullErrorMessage).append('\n');
        }
        sb.append("\u0000");
        return sb.toString();
    }

    private String createConnectedFrame(){
        StringBuilder sb = new StringBuilder();
        sb.append("CONNECTED").append('\n');
        sb.append("version:1.2").append('\n');
        sb.append('\n');
        sb.append("\u0000");
        return(sb.toString());
    }

    private String createMessageFrame(String destination, int messageId, String body) {

        StringBuilder sb = new StringBuilder();
        sb.append("MESSAGE").append('\n');
        sb.append("destination:").append(destination).append('\n');
        sb.append("message-id:").append(messageId).append('\n');
        sb.append('\n');
        sb.append(body);
        sb.append("\u0000");
        return sb.toString();
    }

    private String createReceiptFrame(String receiptId) {
        StringBuilder sb = new StringBuilder();
        sb.append("RECEIPT").append('\n');
        sb.append("receipt-id:").append(receiptId).append('\n');
        sb.append('\n');
        sb.append("\u0000");
        return sb.toString();
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }
    
}
