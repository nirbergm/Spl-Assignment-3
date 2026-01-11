package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ConnectionsImpl <T> implements Connections <T> {
    private final ConcurrentHashMap<Integer, ConnectionHandler<T>> activeConnections = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Integer>> channels = new ConcurrentHashMap<>();
    
    public boolean send(int connectionId, T msg){
        ConnectionHandler<T> handler = activeConnections.get(connectionId);
        if (handler != null) {
            handler.send(msg);
            return true;
        }
        return false;
    }

    public void send(String channel, T msg){
        ConcurrentLinkedQueue<Integer> subscribers = channels.get(channel);
        if (subscribers != null) {
            for (Integer connectionId : subscribers) {
                send(connectionId, msg); 
            }
        }
    }

    public void disconnect(int connectionId){
        activeConnections.remove(connectionId);    
    }

    public void addConnection(int connectionId, ConnectionHandler<T> handler) {
        activeConnections.put(connectionId, handler);
    }

    public void subscribe(String channel, int connectionId) {
        channels.putIfAbsent(channel, new ConcurrentLinkedQueue<>());
        channels.get(channel).add(connectionId);
    }

    public void unsubscribe(String channel, int connectionId) {
        ConcurrentLinkedQueue<Integer> subscribers = channels.get(channel);
        if (subscribers != null) {
            subscribers.remove(connectionId);
        }
    }
}
