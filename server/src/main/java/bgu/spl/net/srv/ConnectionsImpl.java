package bgu.spl.net.srv;
//new class implemented according to page 9 of the assignemnt
public class ConnectionsImpl<T> implements Connections<T> {
    public boolean send(int connectionId, T msg){ return false; }
    public void send(String channel, T msg){}
    public void disconnect(int connectionId){}
}
