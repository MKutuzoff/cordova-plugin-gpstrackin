package com.android.indy.gpstracking;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class SendPositionService extends Service {

    final String LOG_TAG = "SendPositionService";

    public SendPositionService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(LOG_TAG, "onBind");
        return null;
    }

    @Override
    public void onCreate() {
        Log.d(LOG_TAG, "Create service");
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOG_TAG, "Start service");
        TimerReceiver.subscribeOnTimer(this);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "Destroy service");
        TimerReceiver.kill(this);
        super.onDestroy();
    }
}
