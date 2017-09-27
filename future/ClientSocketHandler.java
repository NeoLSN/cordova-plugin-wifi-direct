
package com.android.plugins.wifidirect.library;

import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class ClientSocketHandler extends Thread {

    private Handler handler;
    private InetAddress address;
    private int port;
    private Callback cb;

    private SocketChannel scChannel = null;
    private Selector clientSelector;

    public ClientSocketHandler(Handler handler, InetAddress groupOwnerAddress, int port,
            Callback cb) {
        this.handler = handler;
        this.address = groupOwnerAddress;
        this.port = port;
        this.cb = cb;
    }

    @Override
    public void run() {
        try {
            init(handler, address, port);
            this.handler = null;
            this.address = null;
            this.cb = null;
        } catch (IOException e) {
            close();
            Log.i("Jason", "ClientSocketHandler fail -> ", e);
            if (cb != null) {
                cb.onFailure();
                cb = null;
            }
        }
    }

    private void init(Handler handler, InetAddress address, int port) throws IOException {
        SocketChannel sChannel = connectTo(address.getHostAddress(), port);
        clientSelector = Selector.open();
        scChannel = sChannel;
        sChannel.register(clientSelector, SelectionKey.OP_READ);

        CommunicationProcessor cp = new CommunicationProcessor(clientSelector, handler);
        new Thread(cp).start();
    }

    private SocketChannel connectTo(String hostname, int port) throws IOException {
        SocketChannel sChannel;
        sChannel = createSocketChannel(hostname, port);
        // Before the socket is usable, the connection must be completed. finishConnect().
        while (!sChannel.finishConnect()) ;
        // Socket channel is now ready to use
        return sChannel;
    }

    private SocketChannel createSocketChannel(String hostName, int port) throws IOException {
        SocketChannel sChannel = SocketChannel.open();
        sChannel.configureBlocking(false);
        sChannel.connect(new InetSocketAddress(hostName, port));
        return sChannel;
    }

    public void setClientChannel(SocketChannel scChannel) {
        if (scChannel != null)
            this.scChannel = scChannel;
    }

    public SocketChannel getClientChannel() {
        return scChannel;
    }

    public void close() {
        if (scChannel != null) {
            try {
                scChannel.close();
                clientSelector.close();
            } catch (Exception ignored) {
            } finally {
                clientSelector = null;
                scChannel = null;
            }
        }
    }

    public interface Callback {

        void onFailure();
    }
}
