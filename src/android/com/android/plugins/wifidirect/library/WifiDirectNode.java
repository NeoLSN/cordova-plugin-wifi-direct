package com.android.plugins.wifidirect.library;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by JasonYang on 2017/9/20.
 */

class NodeState {
    private int state;
    public static final int DISCONNECTED = 1;
    public static final int INITIATED = 2;
    public static final int CONNECTING = 3;
    public static final int CONNECTED = 4;
    public static final int DISCONNECTING = 5;

    public NodeState(int s) {
        this.state = s;
    }

    public int get() {
        return this.state;
    }

    public void set(int s) {
        this.state = s;
    }
}

public class WifiDirectNode implements WifiP2pManager.ConnectionInfoListener,
        WifiP2pManager.GroupInfoListener, WifiP2pManager.PeerListListener {

    private static final long periodicInterval = 15000;

    private Context context;
    private Handler handler;
    private ServiceData serviceData;

    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver receiver;
    private IntentFilter intentFilter;

    private Runnable periodicDiscovery = null;
    private Runnable periodicFind = null;
    private Runnable requestConnectionInfo = null;

    private boolean isEnabled = true;

    private WifiP2pDevice device;
    private WifiP2pConfig peerConfig = null;
    private boolean pendingConnect = false;
    private WifiP2pDevice groupOwner;
    private List<WifiP2pDevice> peers;
    private List<WifiP2pDevice> services;

    private NodeState nodeState = new NodeState(NodeState.DISCONNECTED);

    private InternalCallback connectionCallback = new InternalCallback();
    private DiscoveringCallback discoveringCallback;

    public interface ConnectionCallback {

        void onConnect();

        void onConnectError(String message);
    }

    private class InternalCallback implements ConnectionCallback {

        private ConnectionCallback callback;

        @Override
        public void onConnect() {
            if (callback != null) {
                callback.onConnect();
                callback = null;
            }
        }

        @Override
        public void onConnectError(String message) {
            if (callback != null) {
                callback.onConnectError(message);
                callback = null;
            }
        }

        public void attach(ConnectionCallback callback) {
            this.callback = callback;
        }
    }

    public interface DiscoveringCallback {

        void onDevicesUpdate(List<WifiP2pDevice> updates);
    }

    public WifiDirectNode(final Context context, ServiceData serviceData) {
        this.context = context;
        this.serviceData = serviceData;

        handler = new Handler(context.getMainLooper());

        services = new ArrayList<WifiP2pDevice>();
        peers = new ArrayList<WifiP2pDevice>();

        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        wifiP2pManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        channel = wifiP2pManager.initialize(context, context.getMainLooper(), null);
        receiver = new WiFiDirectNodeReceiver(this);

        setupDnsSdResponsor();
        startup();
    }

    private void startup() {
        periodicDiscovery = new Runnable() {
            public void run() {
                if (!isEnabled) {
                    return;
                }

                wifiP2pManager.discoverServices(channel,
                        new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                            }

                            public void onFailure(int reasonCode) {
                                stopDiscovering();
                            }
                        });
                handler.postDelayed(periodicDiscovery, periodicInterval);
            }
        };

        periodicFind = new Runnable() {
            public void run() {
                if (!isEnabled) {
                    return;
                }

                wifiP2pManager.discoverPeers(channel, null);
                handler.postDelayed(periodicFind, periodicInterval);
            }
        };

        requestConnectionInfo = new Runnable() {
            public void run() {
                if (!isEnabled) {
                    return;
                }

                wifiP2pManager.requestConnectionInfo(channel, WifiDirectNode.this);
            }
        };

        context.registerReceiver(receiver, intentFilter);
    }

    private void setupDnsSdResponsor() {
        WifiP2pManager.DnsSdServiceResponseListener serviceListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String serviceNameAndTP,
                    WifiP2pDevice sourceDevice) {
                Log.d(Constants.TAG, "Found " + instanceName + " " + serviceNameAndTP);
            }
        };

        WifiP2pManager.DnsSdTxtRecordListener txtRecordListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            public void onDnsSdTxtRecordAvailable(String serviceFullDomainName,
                    Map<String, String> record, WifiP2pDevice device) {
                if (!services.isEmpty()) {
                    for (WifiP2pDevice found : services) {
                        if (found.deviceName.equals(device.deviceName)) {
                            return;
                        }
                    }
                }

                String serviceType = serviceData.getFullDomainName();
                if (serviceFullDomainName != null && serviceFullDomainName.equals(serviceType)) {
                    services.add(device);
                }
            }
        };

        wifiP2pManager.setDnsSdResponseListeners(channel, serviceListener, txtRecordListener);
    }

    public void startDiscovering() {
        if (wifiP2pManager == null) return;
        services.clear();
        // doDiscoverServices(true);
        doFindPeers(true);
    }

    private void doDiscoverServices(boolean start) {
        handler.removeCallbacks(periodicDiscovery);

        wifiP2pManager.clearServiceRequests(channel, null);

        String serviceType = serviceData.getFullDomainName();
        WifiP2pDnsSdServiceRequest serviceRequest;
        if (serviceData.getName() != null && !TextUtils.isEmpty(serviceType)) {
            serviceRequest = WifiP2pDnsSdServiceRequest
                    .newInstance(serviceData.getName(), serviceType);
        } else if (!TextUtils.isEmpty(serviceType)) {
            serviceRequest = WifiP2pDnsSdServiceRequest.newInstance(serviceType);
        } else {
            serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        }

        wifiP2pManager.addServiceRequest(channel, serviceRequest,
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onFailure(int errorCode) {
                    }
                });
        if (start)
            periodicDiscovery.run();
    }

    private void doFindPeers(boolean start) {
        handler.removeCallbacks(periodicFind);
        wifiP2pManager.stopPeerDiscovery(channel, null);
        if (start)
            periodicFind.run();
    }

    public void stopDiscovering() {
        if (wifiP2pManager == null) return;
        services.clear();
        // doDiscoverServices(false);
        doFindPeers(false);
    }

    public void requestPeers() {
        if (wifiP2pManager == null) return;
        this.wifiP2pManager.requestPeers(channel, this);
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peers) {
        if (wifiP2pManager == null) return;
        this.peers.clear();
        this.peers.addAll(peers.getDeviceList());

        if (discoveringCallback != null) {
            discoveringCallback.onDevicesUpdate(new ArrayList<WifiP2pDevice>(this.peers));
        }
    }

    @Override
    public void onGroupInfoAvailable(WifiP2pGroup group) {
        synchronized (nodeState) {
            if (nodeState.get() != NodeState.CONNECTED)
                return;
        }

        groupOwner = group.getOwner();
    }

    public void connect(final String deviceAddress, final int groupOwnerIntent,
            final ConnectionCallback cb) {
        if (wifiP2pManager == null) {
            if (cb != null) cb.onConnectError("Shutdown!");
            return;
        }

        handler.removeCallbacks(requestConnectionInfo);

        if (!isEnabled) {
            if (cb != null) cb.onConnectError("Wifi direct is not enabled.");
            return;
        }

        if (deviceAddress == null || deviceAddress.isEmpty()) {
            if (cb != null) cb.onConnectError("Device address empty.");
            return;
        }

        if (nodeState.get() != NodeState.DISCONNECTED) {
            if (cb != null) cb.onConnectError("Already connected or in progress.");
            return;
        }

        connectionCallback.attach(cb);

        peerConfig = new WifiP2pConfig();
        peerConfig.deviceAddress = deviceAddress;
        peerConfig.groupOwnerIntent = groupOwnerIntent;
        peerConfig.wps.setup = WpsInfo.PBC;

        if (nodeState.get() == NodeState.CONNECTED && groupOwner != null) {
            if (groupOwner.deviceAddress.equals(deviceAddress)) {
                wifiP2pManager.requestGroupInfo(channel, this);
                connectionCallback.onConnect();
            } else {
                pendingConnect = true;
            }
        } else {
            initiateConnect();
        }
    }

    public void requestConnectionInfo() {
        if (wifiP2pManager == null) return;
        wifiP2pManager.requestConnectionInfo(channel, this);
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        if (wifiP2pManager == null) return;

        handler.removeCallbacks(requestConnectionInfo);
        if (!info.groupFormed) {
            groupOwner = null;
        }

        switch (nodeState.get()) {
            case NodeState.INITIATED:
            case NodeState.CONNECTING:
                if (info.groupFormed) {
                    nodeState.set(NodeState.CONNECTED);
                    wifiP2pManager.requestGroupInfo(channel, this);

                    connectionCallback.onConnect();
                } else {
                    wifiP2pManager.cancelConnect(channel, null);

                    nodeState.set(NodeState.DISCONNECTED);
                    peerConfig = null;

                    connectionCallback.onConnectError("Timeout");
                }
                break;
            case NodeState.CONNECTED:
                if (!info.groupFormed) {
                    nodeState.set(NodeState.DISCONNECTED);
                    peerConfig = null;
                }
                break;
            case NodeState.DISCONNECTING:
            case NodeState.DISCONNECTED:
                if (info.groupFormed) {
                    nodeState.set(NodeState.CONNECTED);
                    wifiP2pManager.requestGroupInfo(channel, this);

                    if (pendingConnect && peerConfig != null) {
                        peerConfig = null;
                    }

                    pendingConnect = false;
                } else {
                    if (!pendingConnect && peerConfig != null) {
                        peerConfig = null;
                    }
                    nodeState.set(NodeState.DISCONNECTED);

                    if (pendingConnect)
                        initiateConnect();
                }
                break;
        }
    }

    private void initiateConnect() {
        pendingConnect = false;
        if (peerConfig == null)
            return;
        nodeState.set(NodeState.INITIATED);
        wifiP2pManager.connect(channel, peerConfig,
                new WifiP2pManager.ActionListener() {
                    public void onSuccess() {
                        if (nodeState.get() == NodeState.INITIATED) {
                            nodeState.set(NodeState.CONNECTING);
                        }

                        handler.postDelayed(requestConnectionInfo, 30000);
                    }

                    public void onFailure(int reasonCode) {
                        nodeState.set(NodeState.DISCONNECTED);
                        peerConfig = null;

                        connectionCallback.onConnectError("connect failed: " + reasonCode);
                    }
                });
    }

    public void disconnect() {
        if (wifiP2pManager == null) return;

        connectionCallback.onConnectError("Disconnect!");

        if (!isEnabled) return;

        switch (nodeState.get()) {
            case NodeState.INITIATED:
            case NodeState.CONNECTING:
                handler.removeCallbacks(requestConnectionInfo);

                nodeState.set(NodeState.DISCONNECTING);
                wifiP2pManager.cancelConnect(channel,
                        new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                                nodeState.set(NodeState.DISCONNECTED);
                                peerConfig = null;
                            }

                            public void onFailure(int reasonCode) {
                                nodeState.set(NodeState.DISCONNECTED);
                                peerConfig = null;
                            }
                        });
                break;
            case NodeState.CONNECTED:
                nodeState.set(NodeState.DISCONNECTING);
                wifiP2pManager.removeGroup(channel,
                        new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                            }

                            public void onFailure(int reasonCode) {
                                nodeState.set(NodeState.DISCONNECTED);
                                peerConfig = null;
                            }
                        });
                break;
        }
    }

    public void shutdown() {
        if (wifiP2pManager == null) return;
        WifiP2pManager.ActionListener noop = new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure(int reason) {
            }
        };
        stopDiscovering();
        disconnect();
        handler.removeCallbacks(requestConnectionInfo);
        wifiP2pManager.clearLocalServices(channel, noop);
        wifiP2pManager.stopPeerDiscovery(channel, noop);

        if (receiver != null) {
            context.unregisterReceiver(receiver);
        }

        peers.clear();
        services.clear();

        device = null;
        wifiP2pManager = null;
        channel = null;
        context = null;
        discoveringCallback = null;
        connectionCallback = null;
    }

    public WifiP2pDevice getDevice() {
        return this.device;
    }

    public void setDevice(WifiP2pDevice thisDevice) {
        this.device = thisDevice;
    }

    public void setDiscoveringCallback(DiscoveringCallback callback) {
        discoveringCallback = callback;
    }

    public ServiceData getServiceData() {
        return serviceData;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
        if (!isEnabled) {
            stopDiscovering();
            disconnect();
        }
    }
}
