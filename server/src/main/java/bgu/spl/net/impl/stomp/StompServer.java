package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.Server;
import bgu.spl.net.srv.Reactor;

public class StompServer {

    public static void main(String[] args) {
        //check for correct number of arguments
        if(args.length < 2){
            System.out.println("required input: <port> <server type>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String serverType = args[1];

        int nthreads = 4; //default number of threads
        if(args.length > 2){ //if number of threads is specified
            nthreads = Integer.parseInt(args[2]);
        }
        
        if(serverType.equals("tpc")){
            Server.threadPerClient(
                port, 
                () -> new StompMessagingProtocolImpl(), 
                () -> new StompEncoderDecoder()
            ).serve();
        }
        
        else if(serverType.equals("reactor")){
            Server.reactor(
                nthreads,
                port, 
                () -> new StompMessagingProtocolImpl(), 
                () -> new StompEncoderDecoder()
            ).serve();  
        }
    }
}
