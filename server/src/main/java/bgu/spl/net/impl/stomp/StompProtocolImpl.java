package bgu.spl.net.impl.stomp;
import java.util.HashMap;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;
import bgu.spl.net.api.StompMessagingProtocol;

//new class to implement the StompMessagingProtocol interface according to its new interface 
public class StompProtocolImpl implements StompMessagingProtocol<String> {
    
    private boolean shouldTerminate = false;
    private ConnectionsImpl<String> connections;
    private int connectionId;
    private HashMap<Integer, String> channelIds = new HashMap<>(); //map of channel id to channel name

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
                handleSend(frame);
                break;
            case "CONNECT":
                handleConnect(frame);
                break;
            case "DISCONNECT":
                handleDisconnect(frame);
                break;
            case "SUBSCRIBE":
                handleSubscribe(frame);
                break;
            case "UNSUBSCRIBE":
                handleUnsubscribe(frame);
                break;
            default:
                //should not reach here due to prior frame validity check
                break;
        }
    }

    private void handleSend(StompFrame frame){
        //send message to all subscribers of the destination channel
        connections.send(frame.getHeader("destination"), frame.getBody());
        //send receipt if requested
        if(frame.receiptRequested()){
            connections.send(connectionId, frame.generateReceiptFrame().toString());
        }
    }

    private void handleConnect(StompFrame frame){
        //register client in connections map
        connections.connect(connectionId, frame.getHeader("login"), frame.getHeader("passcode"));
        //send receipt if requested
        if(frame.receiptRequested()){
            connections.send(connectionId, frame.generateReceiptFrame().toString());
        }
    }

    private void handleDisconnect(StompFrame frame){
        //send receipt if requested before disconnecting
        if(frame.receiptRequested()){
            connections.send(connectionId, frame.generateReceiptFrame().toString());
        }

        channelIds.clear(); //clear subscription map
        shouldTerminate = true;
        //disconnect client
        connections.disconnect(connectionId);
    }

    private void handleSubscribe(StompFrame frame){
        //log client's channel -> id translation in subscription map
        channelIds.put(Integer.parseInt(frame.getHeader("id")), frame.getHeader("destination"));
        //subscribe client to channel
        connections.subscribe(connectionId, frame.getHeader("destination"), Integer.parseInt(frame.getHeader("id")));
        //send receipt if requested    
        if(frame.receiptRequested()){
            connections.send(connectionId, frame.generateReceiptFrame().toString());
        }
    }

    private void handleUnsubscribe(StompFrame frame){  
        //get channel name from client's subscription map
        String channel = channelIds.get(Integer.parseInt(frame.getHeader("id")));
        //remove channel from client's subscription map
        channelIds.remove(Integer.parseInt(frame.getHeader("id")));
        //unsubscribe client from channel
        connections.unsubscribe(connectionId, channel);
        //send receipt if requested
        if(frame.receiptRequested()){
            connections.send(connectionId, frame.generateReceiptFrame().toString());
        }
    }

    public boolean shouldTerminate(){ 
        return shouldTerminate; 
    }
}
