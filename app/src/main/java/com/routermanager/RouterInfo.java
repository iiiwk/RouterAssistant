package com.routermanager;

import org.json.JSONException;
import org.json.JSONObject;

public class RouterInfo {
    private String address;
    private String username;
    private String password;
    private String alias;
    private long lastUsed;

    public RouterInfo(String address, String username, String password) {
        this.address = address;
        this.username = username;
        this.password = password;
        this.alias = "";
        this.lastUsed = System.currentTimeMillis();
    }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getAlias() { return alias != null ? alias : ""; }
    public void setAlias(String alias) { this.alias = alias; }

    public long getLastUsed() { return lastUsed; }
    public void setLastUsed(long lastUsed) { this.lastUsed = lastUsed; }

    public String getDisplayName() {
        return (alias != null && !alias.isEmpty()) ? alias : address;
    }

    public String getFullUrl() {
        String addr = address.trim();
        if (!addr.startsWith("http://") && !addr.startsWith("https://")) {
            addr = "http://" + addr;
        }
        return addr;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("address", address);
        obj.put("username", username);
        obj.put("password", password);
        obj.put("alias", alias != null ? alias : "");
        obj.put("lastUsed", lastUsed);
        return obj;
    }

    public static RouterInfo fromJson(JSONObject obj) throws JSONException {
        RouterInfo info = new RouterInfo(
                obj.getString("address"),
                obj.optString("username", ""),
                obj.optString("password", "")
        );
        info.setAlias(obj.optString("alias", ""));
        info.setLastUsed(obj.optLong("lastUsed", 0));
        return info;
    }
}
