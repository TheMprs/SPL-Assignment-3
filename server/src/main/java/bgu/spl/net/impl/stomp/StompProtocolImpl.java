package bgu.spl.net.impl.stomp;
import bgu.spl.net.srv.Connections;

//new class to implement the StompMessagingProtocol interface according to its new interface 
public class StompProtocolImpl implements StompMessagingProtocol<String> {
    
    private boolean shouldTerminate = false;
    private Connections<String> connections;
    public void start(int connectionId, Connections<String> connections){
        
    }
    
    public void process(String message){
        if(message == null)
            return;
        String[] headers = message.split("\n");
        shouldTerminate = headers[0].equals("DISCONNECT");
        //action
        if(!shouldTerminate){
            switch (headers[0]) {
                case "CONNECT":
                    
                    break;
                case "SUBSCRIBE":
                    connections.subscribe(headers[1], headers[2]);
                    break;
                case "SEND":
                    
                    break;
                default:
                    
                    break;
            }
        }

    }
	
	/**
     * @return true if the connection should be terminated
     */
    public boolean shouldTerminate(){ 
        return shouldTerminate; 
    }
}
