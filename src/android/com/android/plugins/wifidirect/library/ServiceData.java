package com.android.plugins.wifidirect.library;

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by JasonYang on 2017/9/21.
 */
public class ServiceData implements Parcelable {

    private String type;
    private String domain;
    private String name;
    private int port;
    private JSONObject txtRecords;

    public ServiceData(String type, String domain, String name, int port, JSONObject textRecords) {
        this.type = type;
        this.domain = domain;
        this.name = name;
        this.port = port;
        this.txtRecords = textRecords;
    }

    public String getFullDomainName() {
        String _type = type == null ? "" : type;
        String _domain = domain == null ? "" : domain;
        return _type + _domain;
    }

    public String getType() {
        return type;
    }

    public String getDomain() {
        return domain;
    }

    public String getName() {
        return name;
    }

    public int getPort() {
        return port;
    }

    public JSONObject getTxtRecords() {
        return txtRecords;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.type);
        dest.writeString(this.domain);
        dest.writeString(this.name);
        dest.writeInt(this.port);
        HashMap<String, String> txtRecords = new HashMap<String, String>();
        if (this.txtRecords != null) {
            Iterator<String> iter = this.txtRecords.keys();
            while (iter.hasNext()) {
                String key = iter.next();
                try {
                    txtRecords.put(key, this.txtRecords.getString(key));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        dest.writeSerializable(txtRecords);
    }

    protected ServiceData(Parcel in) {
        this.type = in.readString();
        this.domain = in.readString();
        this.name = in.readString();
        this.port = in.readInt();
        HashMap<String, String> txtRecords = (HashMap<String, String>) in.readSerializable();
        this.txtRecords = new JSONObject();
        Iterator<Map.Entry<String, String>> iter = txtRecords.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, String> entry = iter.next();
            try {
                this.txtRecords.put(entry.getKey(), entry.getValue());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public static final Creator<ServiceData> CREATOR = new Creator<ServiceData>() {
        @Override
        public ServiceData createFromParcel(Parcel source) {
            return new ServiceData(source);
        }

        @Override
        public ServiceData[] newArray(int size) {
            return new ServiceData[size];
        }
    };

    @Override
    public String toString() {
        return "ServiceData{" +
                "type='" + type + '\'' +
                ", domain='" + domain + '\'' +
                ", name='" + name + '\'' +
                ", port=" + port +
                ", txtRecords=" + txtRecords +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceData)) return false;

        ServiceData that = (ServiceData) o;

        if (getPort() != that.getPort()) return false;
        if (getType() != null ? !getType().equals(that.getType()) : that.getType() != null)
            return false;
        if (getDomain() != null ? !getDomain().equals(that.getDomain()) : that.getDomain() != null)
            return false;
        if (getName() != null ? !getName().equals(that.getName()) : that.getName() != null)
            return false;
        return getTxtRecords() != null ? getTxtRecords().equals(that.getTxtRecords()) : that.getTxtRecords() == null;
    }

    @Override
    public int hashCode() {
        int result = getType() != null ? getType().hashCode() : 0;
        result = 31 * result + (getDomain() != null ? getDomain().hashCode() : 0);
        result = 31 * result + (getName() != null ? getName().hashCode() : 0);
        result = 31 * result + getPort();
        result = 31 * result + (getTxtRecords() != null ? getTxtRecords().hashCode() : 0);
        return result;
    }
}
