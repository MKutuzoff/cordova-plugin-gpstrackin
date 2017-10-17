package com.android.indy.gpstracking;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by miklk on 08.10.2017.
 */

public class GpsTracking extends CordovaPlugin {
    String TAG = "GpsTracking";
    CallbackContext context;

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        context = callbackContext;
        if(action.equals("start"))
        {
            Context context=this.cordova.getActivity().getApplicationContext();
            startService(new Intent(context, SendPositionService.class));
            callbackContext.success();
            return true;
        }
        return false;
    }
}
