package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;

public class ConnectionsImpl <T> implements Connections <T> {
    private final ConcurrentHashMap<Integer, ConnectionHandler<T>> activeConnections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, Integer>> channels = new ConcurrentHashMap<>();
    
    public boolean send(int connectionId, T msg){
        ConnectionHandler<T> handler = activeConnections.get(connectionId);
        if (handler != null) {
            handler.send(msg);
            return true;
        }
        return false;
    }

    public void send(String channel, T msg){
      ConcurrentHashMap<Integer, Integer> subscribers = channels.get(channel);

        if (subscribers != null) {
            // עוברים על כל המנויים (Key=User, Value=SubID)
          for (Map.Entry<Integer, Integer> entry : subscribers.entrySet()) {
                Integer connId = entry.getKey();
                Integer subId = entry.getValue();

                // 1. שכפול והתאמת ההודעה (רק אם זה String, שזה המצב ב-STOMP)
                T msgToSend = msg;
                if (msg instanceof String) {
                    String originalMsg = (String) msg;
                    // טריק: הוספת ה-Header אחרי ירידת השורה הראשונה (אחרי הפקודה MESSAGE)
                    String modifiedMsg = originalMsg.replaceFirst("\n", "\nsubscription:" + subId + "\n");
                    msgToSend = (T) modifiedMsg;
                }

                // 2. שליחה עם מנגנון Lazy Removal
                boolean sent = send(connId, msgToSend);
                if (!sent) {
                    // המשתמש לא מחובר? נמחק אותו מהרשימה של הערוץ הזה
                    subscribers.remove(connId);
                }
            }
        }
    }

    public void disconnect(int connectionId){
        activeConnections.remove(connectionId);    
    }

    public void addConnection(int connectionId, ConnectionHandler<T> handler) {
        activeConnections.put(connectionId, handler);
    }

    public void subscribe(String channel, int connectionId, int subscriptionId) {
        channels.putIfAbsent(channel, new ConcurrentHashMap<>());
        channels.get(channel).put(connectionId, subscriptionId);
    }

    public void unsubscribe(String channel, int connectionId) {
        ConcurrentHashMap<Integer, Integer> subscribers = channels.get(channel);
        if (subscribers != null) {
            subscribers.remove(connectionId);
        }
    }
}
