package bgu.spl.net.srv;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import bgu.spl.net.impl.data.Database;
import bgu.spl.net.impl.data.LoginStatus;
import bgu.spl.net.impl.data.User;
import bgu.spl.net.impl.stomp.StompFrame;

//new class implemented according to page 9 of the assignment
public class ConnectionsImpl<T> implements Connections<T> {
    //Singleton Database instance
    private final Database database = Database.getInstance();
    
    //All active connections. The key is the connectionId
    private ConcurrentHashMap<Integer, UserSession<T>> sessions = new ConcurrentHashMap<>();

    // map of channel name to its subscribers
    // subscribers are represented as <connectionId, subscriptionId>
    private ConcurrentHashMap<String, ConcurrentHashMap<Integer, Integer>> channelToSubscribers = new ConcurrentHashMap<>();
    
    //Used for generating unique message ids for broadcast messages
    private AtomicInteger messageIdCounter = new AtomicInteger(0);
    
    //Used for generating unique connection ids
    private AtomicInteger connectionIdCounter = new AtomicInteger(0);

    //code for user connection to socket, not necessarily logging in
    public void addConnection(int connectionId, ConnectionHandler<T> handler) {
        // We create a new session for this ID and handler.
        // the user hasn't logged in yet.
        sessions.put(connectionId, new UserSession<>(handler));
    }
    
    @Override
    public boolean send(int connectionId, T msg) {
        if (msg == null) return false;

        // Get the session associated with this ID
        UserSession<T> session = sessions.get(connectionId);
        
        // If user's online and has a valid handler, send the message
        if (session != null && session.getHandler() != null) {
            session.getHandler().send(msg);
            return true;
        }
        return false; 
    }
    
    //send a message to all online users subscribed to a specific channel.
    @Override
    public void send(String channel, T msg) {
        if (msg == null || channel == null) return;

        // Get all subscribers for this channel. 
        // subscribers are represented as <connectionId, subscriptionId>
        ConcurrentHashMap<Integer, Integer> subscribers = channelToSubscribers.get(channel);
        
        if (subscribers != null) {
            // Generate one message-id for the entire broadcast
            int messageId = generateMessageId();

            // Iterate over each online subscriber and send a personalized frame
            for (Map.Entry<Integer, Integer> entry : subscribers.entrySet()) {
                int connectionId = entry.getKey();
                int subId = entry.getValue();   
               
                Map<String,String> headers = new HashMap<>();
                headers.put("subscription", ""+subId);
                headers.put("message-id", ""+messageId);
                headers.put("destination", ""+channel);

                StompFrame frame = new StompFrame("MESSAGE", headers, (String)msg);
                
                send(connectionId, (T) frame.toString());
            }
        }
    }
    
    //"login" method
    public LoginStatus connect(int connectionId, String username, String password) {
        //delegate login to database
        LoginStatus status = database.login(connectionId, username, password);
        
        //in case of successful login, set the user to be online in the session map
        if(status == LoginStatus.LOGGED_IN_SUCCESSFULLY || status == LoginStatus.ADDED_NEW_USER){
            User user = database.getUserByConnectionId(connectionId);
            sessions.get(connectionId).setUser(user);
        }
        return status;
    }
    
    @Override 
    public void disconnect(int connectionId) {
        // 1. make user no longer "active" in the sessions map
        UserSession<T> session = sessions.remove(connectionId);

        // if user was online, proceed with logout and cleanup
        if (session != null) {
            User user = session.getUser();
            if (user != null) {
                // log the logout in the database
                database.logout(connectionId);
            }

            // 3. close the physical connection handler
            try {
                session.getHandler().close();
            } catch (Exception e) {
                // if socket already closed or failed, we ignore it
            }
        }
        
        // 4. Clean up all channel subscriptions for this connection
        removeConnectionFromAllChannels(connectionId);

    }

    public void subscribe(int connectionId, String channel , int subId) {
        // 1. if the channel does not exist in the map, create a new internal map for it.
        // 2. 'k' is the channel name (the key), we create a new map for this key.
        // * this operation is atomic and thread-safe.
        channelToSubscribers.computeIfAbsent(channel, k -> new ConcurrentHashMap<>())
        
        // 3. get the (new or existing) internal map and add the user's connectionId and subId.
            .put(connectionId, subId);
    }
    
    public void unsubscribe(int connectionId,String channel) {
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
    
    //generate unique connection id for new connection 
    //(Does not add the connection handler itself!)
    public int getNewConnectionId() {
        return connectionIdCounter.getAndIncrement();
    }
    
    //generate unique message ids for broadcast messages
    private int generateMessageId(){
        return messageIdCounter.getAndIncrement();
    }

    //private helper function for disconnect. Without it, disconnected clients would still be in the channels' subscribers lists
    private void removeConnectionFromAllChannels(int connectionId) {
        // we iterate over the channel names. 
        // ConcurrentHashMap's keySet is safe to iterate even if we remove items during the loop.
        for (String channelName : channelToSubscribers.keySet()) {
            unsubscribe(connectionId, channelName);
        }
    }

    //check if a user is logged in based on connectionId
    public boolean isUserLoggedIn(int connectionId) {
        User user = database.getUserByConnectionId(connectionId);
        return user != null && user.isLoggedIn();
    }

    //check if a user is subscribed to a specific channel
    public boolean isUserSubscribed(int connectionId, String channel) {
        //fetch subscribers map for the channel
        ConcurrentHashMap<Integer, Integer> subscribers = channelToSubscribers.get(channel);
        
        //check if channel exists and if the user is in its subscribers list
        return subscribers != null && subscribers.containsKey(connectionId);
    }

    // class to wrapping user with their respective connection handler
    private class UserSession<T> {
        private User user;
        private final ConnectionHandler<T> handler;
        
        public UserSession(ConnectionHandler<T> handler) {
            this.user = null;
            this.handler = handler;
        }

        public void setUser(User user) { this.user = user; }
        public ConnectionHandler<T> getHandler() { return handler; }
        public User getUser() { return user; }
    }

}
