package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.Server;
import bgu.spl.net.srv.Reactor;

public class StompServer {

    public static void main(String[] args) {
        // TODO: implement this
        int port = Integer.parseInt(args[0]);
        String serverType = args[1];

        if(serverType.equals("tpc")){
            Server.threadPerClient(
                port, 
                () -> new StompMessagingProtocol(), 
                () -> new StompEncoderDecoder()
            ).serve();
        }
        
        else if(serverType.equals("reactor")){
        }
    }
}
