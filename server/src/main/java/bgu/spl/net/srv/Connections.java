package bgu.spl.net.srv;

import java.io.IOException;

public interface Connections<T> {

    boolean send(int connectionId, T msg);

    void send(String channel, T msg);

    void disconnect(int connectionId);

    int getNewConnectionId(); //Added to keep BaseServer generic for now
    void addConnection(int connectionId, ConnectionHandler<T> handler);
}
