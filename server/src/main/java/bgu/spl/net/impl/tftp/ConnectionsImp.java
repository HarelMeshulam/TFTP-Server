package bgu.spl.net.impl.tftp;

import bgu.spl.net.srv.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImp<T> implements Connections<T> {
    public volatile ConcurrentHashMap<Integer, ConnectionHandler<T>> idToHandler;
    public volatile ConcurrentHashMap<Integer, Boolean> idsLogin;
    public volatile ConcurrentHashMap<Integer, String> idsUserName;

    public ConnectionsImp() {
        idToHandler = new ConcurrentHashMap<>();
        idsLogin = new ConcurrentHashMap<>();
        idsUserName = new ConcurrentHashMap<>();
    }

    public void connect(int connectionId, ConnectionHandler<T> handler) {
        System.out.println("Id " + connectionId + " has joined the server");
        idsLogin.put(connectionId, true);
        idToHandler.put(connectionId, handler);
    }

    public boolean send(int connectionId, T msg) {
        if (idsLogin.containsKey(connectionId)) { //If the user id exists
            idToHandler.get(connectionId).send(msg);
            return true;
        }
        return false;

    }

    public void disconnect(int connectionId) {
        idsLogin.put(connectionId, false);
        idToHandler.remove(connectionId);
    }
}
