package bgu.spl.net.impl.stomp;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.api.StompMessagingProtocol;

//new class to implement the StompMessagingProtocol interface according to its new interface 
public class StompProtocolImpl implements StompMessagingProtocol<String> {
    
    private boolean shouldTerminate = false;

    private Connections<String> connections;
    private int connectionId;

    public void start(int connectionId, Connections<String> connections){
        this.connectionId = connectionId;
        this.connections = connections;
    }


    public void process(String message){
        if(message == null)
            return;
                
        StompFrame frame = new StompFrame(message);

        switch (frame.getCommand()) {
            case "SEND":
                connections.send(message, frame.getBody());
                break;
            case "CONNECT":
                connections.connect(connectionId, frame.getHeader("login"), frame.getHeader("passcode"));
                break;
            case "DISCONNECT":
                connections.disconnect(connectionId);
                break;
            case "SUBSCRIBE":
                connections.subscribe(connectionId, frame.getHeader("destination"), frame.getHeader("id"));
                break;
            case "UNSUBSCRIBE":
                connections.unsubscribe(frame.getHeader("id"));
                break;
            default:
                //error frame was already generated in StompFrame class
                connections.send(connectionId, frame.toString());
                connections.disconnect(connectionId);
                shouldTerminate = true;
        }
    }
	
	/**
     * @return true if the connection should be terminated
     */
    public boolean shouldTerminate(){ 
        return shouldTerminate; 
    }
}
