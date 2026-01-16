package bgu.spl.net.impl.stomp;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;
import bgu.spl.net.api.StompMessagingProtocol;

//new class to implement the StompMessagingProtocol interface according to its new interface 
public class StompProtocolImpl implements StompMessagingProtocol<String> {
    
    private boolean shouldTerminate = false;

    private ConnectionsImpl<String> connections;
    private int connectionId;

    public void start(int connectionId, Connections<String> connections){
        this.connectionId = connectionId;
        this.connections = (ConnectionsImpl<String>) connections;
    }

    public void process(String message){
        //create Stomp frame from the message
        StompFrame frame = new StompFrame(message);

        //check frame validity, if needed create and send error frame, then disconnect
        if(!frame.checkFrame()){
            StompFrame errorFrame = frame.generateErrorFrame();
            connections.send(connectionId, errorFrame.toString());
            connections.disconnect(connectionId);
            shouldTerminate = true;
            return;
        }

        switch (frame.getCommand()) {
            case "SEND":
                connections.send(frame.getHeader("destination"), frame.getBody());
                break;
            case "CONNECT":
                connections.connect(connectionId, frame.getHeader("login"), frame.getHeader("passcode"));
                break;
            case "DISCONNECT":
                connections.disconnect(connectionId);
                shouldTerminate = true;
                break;
            case "SUBSCRIBE":
                connections.subscribe(connectionId, frame.getHeader("destination"), Integer.parseInt(frame.getHeader("id")));
                break;
            case "UNSUBSCRIBE":
                connections.unsubscribe(connectionId, frame.getHeader("id"));
                break;
            default:
                //should not reach here due to prior frame validity check
                break;
        }
    }
	
	/**
     * @return true if the connection should be terminated
     */
    public boolean shouldTerminate(){ 
        return shouldTerminate; 
    }
}
