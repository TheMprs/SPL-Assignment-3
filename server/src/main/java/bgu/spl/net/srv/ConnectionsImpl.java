package bgu.spl.net.srv;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

//new class implemented according to page 9 of the assignemnt
public class ConnectionsImpl<T> implements Connections<T> {
   //All active connections. The key is the connectionId
    private ConcurrentHashMap<Integer, UserSession<T>> sessions = new ConcurrentHashMap<>();

    // Quick index to find active users by their username
    private ConcurrentHashMap<String, UserSession<T>> activeUsersByName = new ConcurrentHashMap<>();

    //The "database" of registered users (username -> password)
    private ConcurrentHashMap<String, String> userPasswords = new ConcurrentHashMap<>();

    //The value of the map is another map that maps connectionId to subscriptionId
    private ConcurrentHashMap<String, ConcurrentHashMap<Integer, Integer>> channelToSubscribers = new ConcurrentHashMap<>();
    /*
    //map of connectionId to connectionHandler
    private ConcurrentHashMap <Integer, ConnectionHandler<T>> connectionsBook = new ConcurrentHashMap<>();
    //The value of the map is another map that maps connectionId to subscriptionId
    private ConcurrentHashMap<String, ConcurrentHashMap<Integer, Integer>> channelToSubscribers = new ConcurrentHashMap<>();
    
    //Registered users "Database"
    //maps username to password
    private ConcurrentHashMap<String, String> userPasswords = new ConcurrentHashMap<>();

    //Active users (Session Management)
    //maps username to connectionId
    private ConcurrentHashMap<String, Integer> activeUsers = new ConcurrentHashMap<>();

    //maps connectionId to username
    private ConcurrentHashMap<Integer, String> userNamesPerConnection = new ConcurrentHashMap<>();
    
    */
    //Used for generating unique message ids for broadcast messages
    private AtomicInteger messageIdCounter = new AtomicInteger(0);
    
    //Used for generating unique connection ids
    private AtomicInteger connectionIdCounter = new AtomicInteger(0);
    public boolean send(int connectionId, T msg){
        //check if msg is null
        if(msg == null)
            return false;

        //get the connection handler from the map
        ConnectionHandler<T> handler = connectionsBook.get(connectionId);
        if (handler != null){ //check if the handler exists in the map
            handler.send(msg);
            return true;
        }
        return false; 
    }
    
   /**
     * Sends a message to all clients subscribed to a specific channel.
     */
    @Override
    public void send(String channel, T msg) {
        if (msg == null || channel == null) return;

        // Get all subscribers for this channel. subscribers is a map: connectionId to subscriptionId
        ConcurrentHashMap<Integer, Integer> subscribers = channelToSubscribers.get(channel);
        
        if (subscribers != null) {
            // Generate one message-id for the entire broadcast
            int messageId = generateMessageId();

            // Iterate over each subscriber and send a personalized frame
            for (Map.Entry<Integer, Integer> entry : subscribers.entrySet()) {
                int connectionId = entry.getKey();
                int subId = entry.getValue();
                
                // Create a STOMP MESSAGE frame with the specific subId and shared messageId
                String stompMessage = createStompMessageFrame((String)msg, subId, messageId, channel);
                
                // Use the single send method to deliver it
                send(connectionId, (T)stompMessage);
            }
        }
    }
    
    @Override 
    public void disconnect(int connectionId) {
        // 1. Remove the session from the sessions map using the connectionId
        UserSession<T> session = sessions.remove(connectionId);

        if (session != null) {
            // 2. If the user was logged in (had a username), remove from the active users index
            String username = session.getUsername();
            if (username != null) {
                activeUsersByName.remove(username);
            }

            // 3. Try to close the physical connection handler
            try {
                session.getHandler().close();
            } catch (Exception e) {
                // If already closed or failed, we ignore it
            }
        }

        // 4. Clean up all channel subscriptions for this connection
        removeConnectionFromAllChannels(connectionId);
    }

    public void subscribe(int connectionId, String channel , int subId) {
        // 1. If the channel does not exist in the map, create a new internal map for it.
        // 2. 'k' is the channel name (the key), we just create a new map for this key.
        // 3. This operation is atomic and thread-safe.
        channelToSubscribers.computeIfAbsent(channel, k -> new ConcurrentHashMap<>())
        
        // 4. Get the (new or existing) internal map and add the user's connectionId and subId.
                            .put(connectionId, subId);
    }
    
    public void unsubscribe(String channel, int connectionId) {
        // 1. Get the internal map for this specific channel
        ConcurrentHashMap<Integer, Integer> subscribers = channelToSubscribers.get(channel);
        
        // 2. If the channel exists, remove the user's connectionId from it
        if (subscribers != null) {
            subscribers.remove(connectionId);
            
            // Optional: If the channel is now empty, we can remove the channel itself to save memory
            if (subscribers.isEmpty()) {
                channelToSubscribers.remove(channel);
            }
        }
    }
    
    
    public String connect(int connectionId, String username, String password) {
        UserSession<T> currentSession = sessions.get(connectionId);

        //Checking if password is correct and register the user if needed.
        //No need to synch because password can't be changed, according to the assignment description.
        String existingPassword = userPasswords.putIfAbsent(username, password);
        if (existingPassword != null && !existingPassword.equals(password)) {
            return "Wrong password";
        }

        //Atomic login attempt - ensuring no double logins with the same username
        UserSession<T> alreadyConnected = activeUsersByName.putIfAbsent(username, currentSession);
        
        if (alreadyConnected != null) {
            //Someone is already logged in with this username
            return "User already logged in";
        }

        //Successful login, update the session info
        currentSession.setUsername(username);
        return "Success";
    }
    //Helper func to generate unique connection ids for new connections 
    //(Does not add the connection handler itself!)
    public int getNewConnectionId() {
        return connectionIdCounter.getAndIncrement();
    }
    
    //----------------
    //PRIVATE METHODS
    //----------------
    //Generate unique message ids for broadcast messages
    private int generateMessageId(){
        return messageIdCounter.getAndIncrement();
    }

    //I'm not sure if this logic has to be done here, **review it later**
   private String createStompMessageFrame(String body, int subId, int messageId, String channel) {
        return "MESSAGE\n" +
               "subscription:" + subId + "\n" +
               "destination:" + channel + "\n" +
               "message-id:" + messageId + "\n" +
               "\n" +
               body + "\u0000";
    }

    //Private helper function for disconnect. Without it, disconnected clients would still be in the channels' subscribers lists
    private void removeConnectionFromAllChannels(int connectionId) {
        //Iterate over all channels and remove the connectionId from their subscribers list
        for (ConcurrentHashMap<Integer, Integer> subscribers : channelToSubscribers.values()) {
        subscribers.remove(connectionId);
    }
}

    //Private class to manage each user's session
    private class UserSession<T> {
        private final ConnectionHandler<T> handler;
        private String username; // Nullable - will be set after successful CONNECT

        public UserSession(ConnectionHandler<T> handler) {
            this.handler = handler;
            this.username = null;
        }

        public void setUsername(String username) { this.username = username; }
        public String getUsername() { return username; }
        public ConnectionHandler<T> getHandler() { return handler; }
    }

}
