package com.routermanager;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PreferenceHelper {
    private static final String PREF_NAME = "router_manager_prefs";
    private static final String KEY_ROUTERS = "saved_routers";
    private static final String KEY_LAST_ADDRESS = "last_address";

    private final SharedPreferences prefs;

    public PreferenceHelper(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveRouter(RouterInfo router) {
        List<RouterInfo> routers = getRouters();

        // Remove existing entry with same address
        routers.removeIf(r -> r.getAddress().equalsIgnoreCase(router.getAddress()));
        router.setLastUsed(System.currentTimeMillis());
        routers.add(0, router);

        saveRouterList(routers);
        prefs.edit().putString(KEY_LAST_ADDRESS, router.getAddress()).apply();
    }

    public List<RouterInfo> getRouters() {
        String json = prefs.getString(KEY_ROUTERS, "[]");
        List<RouterInfo> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(RouterInfo.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Collections.sort(list, (a, b) -> Long.compare(b.getLastUsed(), a.getLastUsed()));
        return list;
    }

    public RouterInfo getRouter(String address) {
        for (RouterInfo r : getRouters()) {
            if (r.getAddress().equalsIgnoreCase(address)) {
                return r;
            }
        }
        return null;
    }

    public void deleteRouter(String address) {
        List<RouterInfo> routers = getRouters();
        routers.removeIf(r -> r.getAddress().equalsIgnoreCase(address));
        saveRouterList(routers);
    }

    public String getLastAddress() {
        return prefs.getString(KEY_LAST_ADDRESS, "");
    }

    private void saveRouterList(List<RouterInfo> routers) {
        JSONArray arr = new JSONArray();
        for (RouterInfo r : routers) {
            try {
                arr.put(r.toJson());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        prefs.edit().putString(KEY_ROUTERS, arr.toString()).apply();
    }
}
