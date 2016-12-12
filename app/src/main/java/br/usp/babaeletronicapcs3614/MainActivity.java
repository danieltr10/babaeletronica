package br.usp.babaeletronicapcs3614;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity {

    private Button mButton;
    private TextView mStatusText;
    private RadioGroup mClientServer;

    private ConnectionManager mConnectionManager;
    private AudioCall call;

    private boolean LISTEN = false;
    private boolean IN_CALL = false;

    public static final String BABY = "bebe";
    public static final String PARENTS = "pais";

    private static final int LISTENER_PORT = 50003;
    private static final int BROADCAST_PORT = 50002;
    private static final int BUF_SIZE = 1024;

    private static final int REQUEST_MICROPHONE = 32323;
    private static final int REQUEST_SPEAKERS = 232323;

    String babyIpAddress = "";
    String parentIpAddress = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mClientServer = (RadioGroup) findViewById(R.id.radio_group_cliente_servidor);
        mButton = (Button) findViewById(R.id.button);
        mStatusText = (TextView) findViewById(R.id.status_text);
        mStatusText.setText("Conexão não estabelecida");

        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mClientServer.getCheckedRadioButtonId() == R.id.baby) {

                    mConnectionManager = new ConnectionManager(BABY, getIP());
                    startBabyListener();

                } else if (mClientServer.getCheckedRadioButtonId() == R.id.parents) {

                    if (mConnectionManager == null) {
                        mConnectionManager = new ConnectionManager(PARENTS, getIP());
                    }

                    InetAddress ip = mConnectionManager.getClients().get(BABY);
                    if (ip != null) {
                        babyIpAddress = ip.toString().substring(1, ip.toString().length());
                        startParentListener();
                        makeCall();
                    }

                }
            }
        });

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_MICROPHONE);

        }
    }

    private InetAddress getIP() {
        try {

            WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipAddress = wifiInfo.getIpAddress();
            String addressString = (ipAddress & 0xFF) + "." +
                    ((ipAddress >> 8) & 0xFF) + "." +
                    ((ipAddress >> 16) & 0xFF) + "." +
                    "255";
            return InetAddress.getByName(addressString);
        }
        catch(UnknownHostException e) {
            e.printStackTrace();
            return null;
        }

    }

    private void startBabyListener() {
        LISTEN = true;
        Thread listener = new Thread(new Runnable() {

            @Override
            public void run() {

                try {
                    DatagramSocket socket = new DatagramSocket(LISTENER_PORT);
                    socket.setSoTimeout(1000);
                    byte[] buffer = new byte[BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
                    while(LISTEN) {
                        try {
                            socket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            String action = data.substring(0, 4);
                            if(action.equals("CAL:")) {
                                parentIpAddress = packet.getAddress().toString().substring(1, packet.getAddress().toString().length());
                                try {
                                    sendBabyMessage("ACC:");
                                    InetAddress address = InetAddress.getByName(parentIpAddress);
                                    IN_CALL = true;
                                    call = new AudioCall(address, BABY);
                                    call.startCall();
                                    call.muteSpeakers();
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            mStatusText.setText("Babá eletrônica ativada");
                                        }
                                    });
                                }
                                catch(Exception e) {

                                }
                            }
                        }
                        catch(Exception e) {
                            e.printStackTrace();
                        }
                    }
                    socket.disconnect();
                    socket.close();
                }
                catch(SocketException e) {
                    e.printStackTrace();
                }
            }
        });
        listener.start();
    }

    private void makeCall() {
        sendParentMessage("CAL:"+PARENTS, LISTENER_PORT);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatusText.setText("Estabelecendo conexão...");
            }
        });
    }

    private void endCall() {
        stopListener();
        if(IN_CALL) {
            call.endCall();
        }
        sendParentMessage("END:", BROADCAST_PORT);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatusText.setText("Conexão não estabelecida");
            }
        });

    }

    private void startParentListener() {
        // Create listener thread
        LISTEN = true;
        Thread listenThread = new Thread(new Runnable() {

            @Override
            public void run() {

                try {

                    DatagramSocket socket = new DatagramSocket(BROADCAST_PORT);
                    socket.setSoTimeout(15000);
                    byte[] buffer = new byte[BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
                    while(LISTEN) {

                        try {

                            socket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            String action = data.substring(0, 4);
                            if(action.equals("ACC:")) {
                                // Accept notification received. Start call
                                call = new AudioCall(packet.getAddress(), PARENTS);
                                call.startCall();
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        mStatusText.setText("Babá eletrônica ativada");
                                    }
                                });
                                IN_CALL = true;
                            }
                        }

                        catch(SocketTimeoutException e) {
                            if(!IN_CALL) {
                                endCall();
                                return;
                            }
                        }
                        catch(IOException e) {

                        }
                    }
                    socket.disconnect();
                    socket.close();
                    return;
                }
                catch(SocketException e) {
                    e.printStackTrace();
                }
            }
        });
        listenThread.start();
    }

    private void stopListener() {
        // Ends the listener thread
        LISTEN = false;
    }

    private void sendParentMessage(final String message, final int port) {
        Thread replyThread = new Thread(new Runnable() {

            @Override
            public void run() {

                try {

                    InetAddress address = InetAddress.getByName(babyIpAddress);
                    byte[] data = message.getBytes();
                    DatagramSocket socket = new DatagramSocket();
                    DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
                    socket.send(packet);
                    socket.disconnect();
                    socket.close();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        replyThread.start();
    }

    private void sendBabyMessage(final String message) {
        // Creates a thread for sending notifications
        Thread replyThread = new Thread(new Runnable() {

            @Override
            public void run() {

                try {

                    InetAddress address = InetAddress.getByName(parentIpAddress);
                    byte[] data = message.getBytes();
                    DatagramSocket socket = new DatagramSocket();
                    DatagramPacket packet = new DatagramPacket(data, data.length, address, BROADCAST_PORT);
                    socket.send(packet);
                    socket.disconnect();
                    socket.close();
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
        });
        replyThread.start();
    }



}
