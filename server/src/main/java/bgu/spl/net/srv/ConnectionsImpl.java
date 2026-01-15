package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionsImpl<T>  implements Connections<T>{

    public ConcurrentHashMap<Integer,ConnectionHandler<T>> handlerMap = new ConcurrentHashMap<>();
    public ConcurrentHashMap<String,ConcurrentLinkedQueue<Integer>> channelMap = new ConcurrentHashMap<>();

    private AtomicInteger idCounter = new AtomicInteger(0);

    // generate unique ID to map with and return it
    public int add(ConnectionHandler<T> handler){
        int id = idCounter.incrementAndGet();
        handlerMap.put(id,handler);

        return id;
    }

    public void subscribe(int connectionId, String channel){
        synchronized(channelMap){
            if(channelMap.containsKey(channel)){ //the channel exists 
                channelMap.get(channel).add(connectionId);
            }
            else{ //need to initiate new channel
                ConcurrentLinkedQueue<Integer> list = new ConcurrentLinkedQueue<>();
                list.add(connectionId);
                channelMap.put(channel,list);
            }
        }
    }

    public void unSubscribe(int connectionId, String channel){
        synchronized(channelMap){
            if(channelMap.containsKey(channel)){
                ConcurrentLinkedQueue<Integer> list = channelMap.get(channel);
                if(list.remove(connectionId)){
                }else{
                    throw new IllegalArgumentException("the client is not subscribed to the channel");
                }
            }else{
                throw new IllegalArgumentException("channel is not exists");
            }
        }
    }


    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler =  handlerMap.get(connectionId);
        if(handler != null){
            handler.send(msg);
            return true;
        }
        else{
            return false;
        }
    }

    @Override
    public void send(String channel, T msg){
        ConcurrentLinkedQueue<Integer> list = channelMap.get(channel);
        if(list == null) throw new IllegalArgumentException("the channel doesnt exist");

        for(int id: list){
            ConnectionHandler<T> handler =  handlerMap.get(id);
           if(handler != null) //in case got removed by another thread
                handler.send(msg);
        }
    }

    @Override
    public void disconnect(int connectionId) {
        handlerMap.remove(connectionId);
            
        for(ConcurrentLinkedQueue<Integer> list: channelMap.values()){
            list.remove(connectionId);
        }
    }
    
}
