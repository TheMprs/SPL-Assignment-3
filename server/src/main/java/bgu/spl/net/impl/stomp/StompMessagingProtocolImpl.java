package bgu.spl.net.impl.stomp;
import java.util.HashMap;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;
import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.impl.data.Database;
import bgu.spl.net.impl.data.LoginStatus;

//new class to implement the StompMessagingProtocol interface according to its new interface 
public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {
    
    private boolean shouldTerminate = false;
    private ConnectionsImpl<String> connections;
    private int connectionId;
    private String username = null; // null as long as not logged in
    private HashMap<Integer, String> channelIds = new HashMap<>(); //map of channel id to channel name
    //Singleton Database instance
    private final Database database = Database.getInstance();

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
        String channel = frame.getHeader("destination");
        String filename = frame.getHeader("filename");
        
        //check if user is logged in and subscribed to the channel
        if(connections.isUserLoggedIn(connectionId) && connections.isUserSubscribed(connectionId, channel)) {
            // Attempt to track in SQL but DON'T fail the whole operation if it fails
            boolean dbSuccess = database.trackFileUpload(username, filename, channel);
            
            if (!dbSuccess) {
                // Just log it on the server side, don't tell the client
                System.err.println("SERVER WARNING: Failed to log file upload to SQL for user: " + username);
                // We continue normally - the message will still be sent to others
            }
            
            //send message to all subscribers of the destination channel
                connections.send(channel, frame.getBody());

                

                //send receipt if requested
                if(frame.receiptRequested()){
                    connections.send(connectionId, frame.generateReceiptFrame().toString());
                }
        }
        else{ // user not logged in or isnt subscribed, send error frame and disconnect
            StompFrame errorFrame = frame.generateErrorFrame("User not logged in or not subscribed to channel: " + channel);
            connections.send(connectionId, errorFrame.toString());
            connections.disconnect(connectionId);
            shouldTerminate = true;
        }
        
    }

    private void handleConnect(StompFrame frame){
        String login = frame.getHeader("login");
        //register client in connections map
        LoginStatus connectionMessage = connections.connect(connectionId, login, frame.getHeader("passcode"));
        //if connection's successful, send connected frame
        if(connectionMessage.equals(LoginStatus.LOGGED_IN_SUCCESSFULLY) // successful login 
            || connectionMessage.equals(LoginStatus.ADDED_NEW_USER)){ //new user added and logged in
            this.username = login;
            //read version from accept-version header
            String version = frame.getHeader("accept-version");

            //generate and send connected frame
            StompFrame connectedFrame = frame.generateConnectedFrame(version);
            connections.send(connectionId, connectedFrame.toString());
        }
        else { //error connecting, send error frame and disconnect
            String errMsg ="";
            switch(connectionMessage){
                case ALREADY_LOGGED_IN:
                    errMsg = "User already logged in";
                    break;
                case WRONG_PASSWORD:
                    errMsg = "Wrong password";
                    break;
                case CLIENT_ALREADY_CONNECTED:
                    errMsg = "Client already connected";
                    break;
                case SQL_ERROR:
                    errMsg = "Database connection error during login";
                    break;
                default:
                    errMsg = "Unknown error, this should never happen :(";
                    break;
            }
            StompFrame errorFrame = frame.generateErrorFrame(errMsg);
            connections.send(connectionId, errorFrame.toString());
            connections.disconnect(connectionId);
            shouldTerminate = true;
        }
        
        //receipt isn't an option for CONNECT frames
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
        if(connections.isUserLoggedIn(connectionId)){
            //we only allow clients to subscribe once to each channel
            if(connections.isUserSubscribed(connectionId, frame.getHeader("destination"))){
                //send error frame and disconnect
                StompFrame errorFrame = frame.generateErrorFrame("User already subscribed to channel: " + frame.getHeader("destination"));
                connections.send(connectionId, errorFrame.toString());
                connections.disconnect(connectionId);
                shouldTerminate = true;
            }
            // we require unique subscription ids per client
            else if(channelIds.containsKey(Integer.parseInt(frame.getHeader("id")))){
                //send error frame and disconnect
                StompFrame errorFrame = frame.generateErrorFrame("Subscription id already in use: " + frame.getHeader("id"));
                connections.send(connectionId, errorFrame.toString());
                connections.disconnect(connectionId);
                shouldTerminate = true;
            }
            // user logged in, not subscribed yet and id not in use
            else{
                //log client's channel -> id translation in subscription map
                channelIds.put(Integer.parseInt(frame.getHeader("id")), frame.getHeader("destination"));
                //subscribe client to channel
                connections.subscribe(connectionId, frame.getHeader("destination"), Integer.parseInt(frame.getHeader("id")));
                //send receipt if requested    
                if(frame.receiptRequested()){
                    connections.send(connectionId, frame.generateReceiptFrame().toString());
                }
            }
        }
        else{
            //send error frame and disconnect
            StompFrame errorFrame = frame.generateErrorFrame("User not logged in");
            connections.send(connectionId, errorFrame.toString());
            connections.disconnect(connectionId);
            shouldTerminate = true;
        }
    }

    private void handleUnsubscribe(StompFrame frame){  
        if(connections.isUserLoggedIn(connectionId)){
            int subId = Integer.parseInt(frame.getHeader("id"));
            //get channel name based on subscription id from client's subscription map
            String channel = channelIds.get(subId);
            //remove channel from client's subscription map
            channelIds.remove(subId);
            //unsubscribe client from channel
            connections.unsubscribe(connectionId, channel);
            //send receipt if requested
            if(frame.receiptRequested()){
                connections.send(connectionId, frame.generateReceiptFrame().toString());
            }
        }
        //user not logged in
        else{
            //send error frame and disconnect
            StompFrame errorFrame = frame.generateErrorFrame("User not logged in");
            connections.send(connectionId, errorFrame.toString());
            connections.disconnect(connectionId);
            shouldTerminate = true;
        }
    }

    public boolean shouldTerminate(){ 
        return shouldTerminate; 
    }
}
