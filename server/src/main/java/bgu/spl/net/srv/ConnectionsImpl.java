package bgu.spl.net.srv;

public class ConnectionsImpl <T > implements Connections <T> {

    
    public boolean send(int connectionId, T msg){
        return true;
    }

    public void send(String channel, T msg){
        
    }

    public void disconnect(int connectionId){

    }
}
