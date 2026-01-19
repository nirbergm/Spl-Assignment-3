package bgu.spl.net.impl.stomp;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.api.StompMessageEncoderDecoder;
import bgu.spl.net.api.StompMessagingProtocolImpl;
import bgu.spl.net.srv.Server;

public class StompServer {

    public static void main(String[] args) {
        if (args.length < 2) { 
            System.out.println("didnt insert enough variables");
            return;
        }
        
        int nthreads = 4; // check how much
        int port = Integer.parseInt(args[0]);
        String serverType = args[1];

        ConcurrentHashMap<String,String> usersMap = new ConcurrentHashMap<>();
        ConcurrentHashMap<String,Integer> activeUsersMap = new ConcurrentHashMap<>();


        Supplier<MessagingProtocol<String>> protocolFactory = () -> new StompMessagingProtocolImpl();
        Supplier<MessageEncoderDecoder<String>> encoderFactory = () -> new StompMessageEncoderDecoder();

        if(serverType == "tpc"){
            Server.<String>threadPerClient(port,
                 protocolFactory,
                  encoderFactory).serve();
        }
        else if(serverType == "reactor"){
            Server.<String>reactor(nthreads, port,
                protocolFactory,
                encoderFactory).serve();
        }
    }
}
