package com.android.plugins.wifidirect.library;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class CommunicationProcessor implements Runnable {

    private Selector selector;
    private Handler handler;

    public CommunicationProcessor(Selector selector, Handler handler) {
        this.selector = selector;
        this.handler = handler;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                int readyChannels = selector.select();
                if(readyChannels == 0) continue;

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keys = selectedKeys.iterator();
                while (keys.hasNext()) {
                    SelectionKey selKey = keys.next();
                    Log.d(Constants.TAG, "select : selectionkey: " + selKey.attachment());

                    try {
                        processSelectionKey(selector, selKey);  // process the selection key.
                    } catch (IOException e) {
                        selKey.cancel();
                        Log.e(Constants.TAG, "select : io exception in processing selector event: " + e.toString());
                    }
                    keys.remove();
                }
            } catch (Exception e) {
                Log.e(Constants.TAG, "Exception in selector: " + e.toString());
                break;
            }
        }
    }

    private void processSelectionKey(Selector selector, SelectionKey selKey) throws IOException {
        if (selKey.isValid() && selKey.isAcceptable()) {
            ServerSocketChannel ssChannel = (ServerSocketChannel) selKey.channel();
            SocketChannel sChannel = ssChannel.accept();
            sChannel.configureBlocking(false);
            SelectionKey socketKey = sChannel.register(selector, SelectionKey.OP_READ);
            socketKey.attach("accepted_client " + sChannel.socket().getInetAddress().getHostAddress());
            Log.d(Constants.TAG, "processSelectionKey : accepted a client connection: " + sChannel.socket().getInetAddress().getHostAddress());
            handler.obtainMessage(Constants.MSG_NEW_CLIENT, sChannel).sendToTarget();
        } else if (selKey.isValid() && selKey.isConnectable()) {
            SocketChannel sChannel = (SocketChannel) selKey.channel();
            boolean success = sChannel.finishConnect();
            if (!success) {
                selKey.cancel();
                Log.e(Constants.TAG, " processSelectionKey : finish connection not success !");
            }
            Log.d(Constants.TAG, "processSelectionKey : this client connect to remote success: ");
            handler.obtainMessage(Constants.MSG_FINISH_CONNECT, sChannel).sendToTarget();
        } else if (selKey.isValid() && selKey.isReadable()) {
            SocketChannel sChannel = (SocketChannel) selKey.channel();
            Log.d(Constants.TAG, "processSelectionKey : remote client is readable, read data: " + selKey.attachment());
            doReadable(sChannel);
        } else if (selKey.isValid() && selKey.isWritable()) {
            SocketChannel sChannel = (SocketChannel) selKey.channel();
            Log.d(Constants.TAG, "processSelectionKey : remote client is writable, write data: ");
        }
    }


    private void doReadable(SocketChannel sChannel) {
        String data = readData(sChannel);
        if (data != null) {
            Bundle b = new Bundle();
            b.putString(Constants.MSG_PAYLOAD, data);
            Message msg = handler.obtainMessage();
            msg.what = Constants.MSG_BROADCAST_DATA;
            msg.obj = sChannel;
            msg.setData(b);
            msg.sendToTarget();
        }
    }

    private String readData(SocketChannel sChannel) {
        ByteBuffer buf = ByteBuffer.allocate(1024 * 4);
        String jsonString = null;

        try {
            int numBytesRead = sChannel.read(buf);
            if (numBytesRead == -1) {
                Log.e(Constants.TAG, "readData : channel closed due to read -1: ");
                sChannel.close();
                handler.obtainMessage(Constants.MSG_BROKEN_CONN, sChannel).sendToTarget();
            } else {
                jsonString = new String(buf.array()).trim();
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "readData : exception: " + e.toString());
            handler.obtainMessage(Constants.MSG_BROKEN_CONN, sChannel).sendToTarget();
        }

        Log.d(Constants.TAG, "readData: content: " + jsonString);
        return jsonString;
    }
}
