package com.android.plugins.wifidirect.library;

import android.os.Handler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

public class GroupOwnerSocketHandler extends Thread {

    private Handler handler;
    private int port;
    private Callback cb;

    private ServerSocketChannel ssChannel;
    private Selector serverSelect;

    public GroupOwnerSocketHandler(Handler handler, int port, Callback cb) {
        this.handler = handler;
        this.port = port;
        this.cb = cb;
    }

    @Override
    public void run() {
        try {
            init(handler, port);
            this.handler = null;
            this.cb = null;
        } catch (IOException e) {
            close();
            if (cb != null) {
                cb.onFailure();
                cb = null;
            }
        }
    }

    private void init(Handler handler, int port) throws IOException {
        ssChannel = ServerSocketChannel.open();
        ssChannel.configureBlocking(false);
        ServerSocket socket = ssChannel.socket();
        socket.bind(new InetSocketAddress(port));
        serverSelect = Selector.open();
        SelectionKey acceptKey = ssChannel.register(serverSelect, SelectionKey.OP_ACCEPT);
        acceptKey.attach("accept_channel");

        CommunicationProcessor cp = new CommunicationProcessor(serverSelect, handler);
        new Thread(cp).start();
    }

    public void close() {
        if (ssChannel != null) {
            try {
                ssChannel.close();
                serverSelect.close();
            } catch (Exception ignored) {

            } finally {
                ssChannel = null;
                serverSelect = null;
            }
        }
    }

    public interface Callback {

        void onFailure();
    }
}
