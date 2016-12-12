package br.usp.babaeletronicapcs3614;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;

/**
 * Created by Daniel on 11/12/16.
 */

public class ConnectionManager {

    public static final int BROADCAST_PORT = 50001; // Socket on which packets are sent/received
    private static final int BROADCAST_INTERVAL = 10000; // Milliseconds
    private static final int BROADCAST_BUF_SIZE = 1024;
    private boolean BROADCAST = true;
    private boolean LISTEN = true;
    private HashMap<String, InetAddress> clients;
    private InetAddress broadcastIP;

    public ConnectionManager(String type, InetAddress broadcastIP) {

        clients = new HashMap<>();
        this.broadcastIP = broadcastIP;
        if (type == MainActivity.BABY) {
            broadcast(type, broadcastIP);
        } else {
            listen();
        }
    }

    public HashMap<String, InetAddress> getClients() {

        return clients;
    }

    public void addBaby(String name, InetAddress address) {
        if(!clients.containsKey(name)) {
            clients.put(name, address);
        }
    }

    public void removeBaby(String name) {
        if(clients.containsKey(name)) {
            clients.remove(name);
        }
    }


    public void broadcast(final String type, final InetAddress broadcastIP) {
        Thread broadcastThread = new Thread(new Runnable() {

            @Override
            public void run() {

                try {
                    String request = "ONL:"+type;
                    byte[] message = request.getBytes();
                    DatagramSocket socket = new DatagramSocket();
                    socket.setBroadcast(true);
                    DatagramPacket packet = new DatagramPacket(message, message.length, broadcastIP, BROADCAST_PORT);
                    while(BROADCAST) {
                        socket.send(packet);
                        Thread.sleep(BROADCAST_INTERVAL);
                    }
                    socket.disconnect();
                    socket.close();
                    return;
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
        });
        broadcastThread.start();
    }

    public void listen() {
        // Create the listener thread
        Thread listenThread = new Thread(new Runnable() {

            @Override
            public void run() {

                DatagramSocket socket;
                try {

                    socket = new DatagramSocket(BROADCAST_PORT);
                }
                catch (SocketException e) {

                    return;
                }
                byte[] buffer = new byte[BROADCAST_BUF_SIZE];

                while(LISTEN) {

                    listen(socket, buffer);
                }
                socket.disconnect();
                socket.close();
                return;
            }

            public void listen(DatagramSocket socket, byte[] buffer) {

                try {
                    // Esperando o bebê
                    DatagramPacket packet = new DatagramPacket(buffer, BROADCAST_BUF_SIZE);
                    socket.setSoTimeout(15000);
                    socket.receive(packet);
                    String data = new String(buffer, 0, packet.getLength());
                    String action = data.substring(0, 4);
                    if(action.equals("ONL:")) {
                        addBaby(data.substring(4, data.length()), packet.getAddress());
                    }
                    else if(action.equals("BYE:")) {
                        removeBaby(data.substring(4, data.length()));
                    }

                }
                catch(SocketTimeoutException e) {

                    if(LISTEN) {

                        listen(socket, buffer);
                    }
                    return;
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
        });
        listenThread.start();
    }

}
