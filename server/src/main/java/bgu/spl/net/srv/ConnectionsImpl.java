package bgu.spl.net.srv;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

//new class implemented according to page 9 of the assignemnt
public class ConnectionsImpl<T> implements Connections<T> {
    //map of connectionId to connectionHandler
    private ConcurrentHashMap <Integer, ConnectionHandler<T>> connectionsBook = new ConcurrentHashMap<>();
    //The value of the map is another map that maps connectionId to subscriptionId
    private ConcurrentHashMap<String, ConcurrentHashMap<Integer, Integer>> channelToSubscribers = new ConcurrentHashMap<>();
    //Will be used to generate unique message ids for broadcast messages
    private AtomicInteger messageIdCounter = new AtomicInteger(0);
    
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
    
   public void send(String channel, T msg) {
    if (msg == null || channel == null) return;
     //get the map of subscribers for the channel
    ConcurrentHashMap<Integer, Integer> subscribers = channelToSubscribers.get(channel);
    if (subscribers != null) { //check if there are subscribers to the channel
        for (Map.Entry<Integer, Integer> entry : subscribers.entrySet()) {//iterate over the subscribers
            int connectionId = entry.getKey();
            int subId = entry.getValue();
            
            //add the stomp headers to the message
            String stompMessage = addStompHeaders((String)msg, subId, channel);
            
            send(connectionId, (T)stompMessage);
        }
    }
}
    
    public void disconnect(int connectionId){
        //Remove and get the connection handler from the map
        ConnectionHandler<T> removed_handler = connectionsBook.remove(connectionId);
        if(removed_handler == null)
            System.out.println("no such connection id in the connections book");
        else{
            //Remove the connectionId from all channels' subscribers lists
            removeConnectionFromAllChannels(connectionId);
            try {
                //close the connection handler
                removed_handler.close();
            }catch (Exception e) {
                System.out.println("error in connection handler closing");;
            }
        }
        
    }

    //Generate unique message ids for broadcast messages
    private int generateMessageId(){
        return messageIdCounter.getAndIncrement();
    }

    //I'm not sure if this logic has to be done here, **review it later**
    private String addStompHeaders(String body, int subId, String channel) {
    return "MESSAGE\n" +
           "subscription:" + subId + "\n" +
           "destination:" + channel + "\n" +
           "message-id:" + generateMessageId() + "\n" +
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
}
