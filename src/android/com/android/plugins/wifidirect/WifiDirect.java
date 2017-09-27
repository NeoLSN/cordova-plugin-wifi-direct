package com.android.plugins.wifidirect;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.util.Log;

import com.android.plugins.wifidirect.library.ServiceData;
import com.android.plugins.wifidirect.library.WifiDirectNode;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Created by JasonYang on 2017/9/20.
 */
public class WifiDirect extends CordovaPlugin {

    private static final String TAG = WifiDirect.class.getSimpleName();

    private static final String FIELD_DEVICE_NAME = "deviceName";
    private static final String FIELD_ADDRESS = "address";
    private static final String FIELD_PRIMARY_TYPE = "primaryType";
    private static final String FIELD_SECONDARY_TYPE = "secondaryType";
    private static final String FIELD_STATUS = "status";

    private WifiDirectNode node;

    public WifiDirect() {
    }

    @Override
    public void onDestroy() {
        shutdown();
    }

    @Override
    public boolean execute(String actionAsString, JSONArray args, CallbackContext callbackContext) {
        Action action;
        try {
            action = Action.valueOf(actionAsString);
        } catch (IllegalArgumentException e) {
            // shouldn't ever happen
            Log.e(TAG, "unexpected error", e);
            return false;
        }

        try {
            return executeAndPossiblyThrow(action, args, callbackContext);
        } catch (JSONException e) {
            Log.e(TAG, "unexpected error", e);
            return false;
        }
    }

    private boolean executeAndPossiblyThrow(Action action, JSONArray args,
            final CallbackContext callbackContext) throws JSONException {
        switch (action) {
            case getInstance: {
                try {
                    if (node == null) {
                        final String type = args.optString(0);
                        final String domain = args.optString(1);
                        final String name = args.optString(2);
                        final int port = args.optInt(3);
                        final JSONObject props = args.optJSONObject(4);

                        ServiceData serviceData = new ServiceData(type, domain, name, port, props);
                        Context context = this.cordova.getActivity().getApplicationContext();
                        node = new WifiDirectNode(context, serviceData);
                        callbackContext.success(serviceData.hashCode());
                    } else {
                        callbackContext.success(node.getServiceData().hashCode());
                    }
                } catch (Exception e) {
                    callbackContext.error("Device does not support Wifi-Direct.");
                }
            }
            break;
            case startDiscovering: {
                if (node != null) {
                    node.setDiscoveringCallback(new WifiDirectNode.DiscoveringCallback() {
                        @Override
                        public void onDevicesUpdate(List<WifiP2pDevice> updates) {
                            JSONArray status = new JSONArray();
                            if (updates != null && !updates.isEmpty()) {
                                for (WifiP2pDevice wifiP2pDevice : updates) {
                                    try {
                                        JSONObject device = new JSONObject();
                                        device.put(FIELD_DEVICE_NAME, wifiP2pDevice.deviceName);
                                        device.put(FIELD_ADDRESS, wifiP2pDevice.deviceAddress);
                                        device.put(FIELD_PRIMARY_TYPE, wifiP2pDevice.primaryDeviceType);
                                        device.put(FIELD_SECONDARY_TYPE, wifiP2pDevice.secondaryDeviceType);
                                        device.put(FIELD_STATUS, wifiP2pDevice.status);
                                        status.put(device);
                                    } catch (JSONException e) {
                                        Log.e(TAG, "Error: " + e.getMessage());
                                    }
                                }
                            }

                            PluginResult result = new PluginResult(PluginResult.Status.OK, status);
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        }
                    });
                    node.startDiscovering();
                } else {
                    callbackContext.error("Wifi Direct has been shut down.");
                }
            }
            break;
            case stopDiscovering: {
                if (node != null) {
                    node.setDiscoveringCallback(null);
                    node.stopDiscovering();
                    callbackContext.success();
                } else {
                    callbackContext.error("Wifi Direct has been shut down.");
                }
            }
            break;
            case connect: {
                final JSONObject device = args.optJSONObject(0);
                if (node != null) {
                    try {
                        String address = device.getString(FIELD_ADDRESS);
                        WifiDirectNode.ConnectionCallback cb = new WifiDirectNode.ConnectionCallback() {
                            @Override
                            public void onConnect() {
                                callbackContext.success();
                            }

                            @Override
                            public void onConnectError(String message) {
                                callbackContext.error("Connection error -> " + message);
                            }
                        };
                        node.connect(address, 0, cb);
                    } catch (JSONException e) {
                        callbackContext.error("Unexpected Error happened.");
                    }
                } else {
                    callbackContext.error("Wifi Direct has been shut down.");
                }
            }
            break;
            case disconnect: {
                if (node != null) {
                    node.disconnect();
                    callbackContext.success();
                } else {
                    callbackContext.error("Wifi Direct has been shut down.");
                }
            }
            break;
            case shutdown: {
                if (node != null) {
                    node.shutdown();
                    node = null;
                }
                callbackContext.success();
            }
            break;
        }
        return true;
    }

    private void shutdown() {
        if (node != null) {
            node.shutdown();
            node = null;
        }
    }

    private enum Action {
        getInstance,
        startDiscovering,
        stopDiscovering,
        connect,
        disconnect,
        shutdown,
    }
}
