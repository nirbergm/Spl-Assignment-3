package bgu.spl.net.api;

import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String>{
    public void start(int connectionId, Connections<String> connections){
        connections = new ConnectionsImpl<>();
    }

    public void process(String message) {

    }

    public boolean shouldTerminate() {
        return false;
    }
}
