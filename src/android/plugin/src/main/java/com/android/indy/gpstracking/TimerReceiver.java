package com.android.indy.gpstracking;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class TimerReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "TimerReceiver";

    private static final long REPEAT_MS = 5000;
    private static final long HALF_REPEAT_MS = 2000;

    private static long timerStep = 0;

    private static TrackingLocationListener tracking = null;

    public static void subscribeOnTimer(Context context) {
        PendingIntent pendingIntent = getPendingIntent(context);
        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pendingIntent);
        long atMillis = System.currentTimeMillis() + timerStep;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            am.setExact(AlarmManager.RTC_WAKEUP,  atMillis, pendingIntent );
        } else {
            am.set(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent );
        }
    }

    public static void kill(Context context) {
        Log.d(LOG_TAG, "kill timer receive");
        PendingIntent pendingIntent = getPendingIntent(context);
        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pendingIntent);
        if (tracking != null) {
            tracking.kill();
            tracking = null;
        }
    }

    private static PendingIntent getPendingIntent(Context context) {
        Intent intent = new Intent(context, TimerReceiver.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        }
        return PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }


    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(LOG_TAG, "timer receive");
        if (tracking == null) {
            tracking = new TrackingLocationListener(context);
        }
        if (tracking.hasLocation()) {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            String uri = getUri(tracking);
            new SendPositionTo().execute(wifiManager, uri);
            tracking.reset();
        }
        timerStep = REPEAT_MS;
        subscribeOnTimer(context);
    }

    private String getUri(TrackingLocationListener tracking) {
        if (tracking != null) {
            Log.d(LOG_TAG, "send position");
            while (!tracking.hasLocation()) {
                try {
                    Thread.currentThread();
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
            }
            Location location = tracking.getBestLocation();
            if (location != null) {
                String uri = String.format("https://gqg2n1fypzrh.runscope.net?provider=%s&accuracy=%s&altitude=%s&bearing=%s&latitude=%s&longitude=%s&time=%s",
                        location.getProvider(), location.getAccuracy(), location.getAltitude(), location.getBearing(), location.getLatitude(), location.getLongitude(), location.getTime());
                Log.d(LOG_TAG, "URI:" + uri);
                return uri;
            }
        }
        return  null;
    }

    private static class TrackingLocationListener implements LocationListener {

        private final long COLD_TIME_OFFSET = 30 * 1000;
        private final long HOT_TIME_OFFSET = 3 * 1000;

        private long timeTrigger = 0;
        private LocationManager locationManager = null;
        private Location netLocation = null;
        private Location gpsLocation = null;

        TrackingLocationListener(Context context) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Log.d(LOG_TAG, "start listener position");
                locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, HALF_REPEAT_MS, 0, this);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, HALF_REPEAT_MS, 0, this);
            }
        }

        boolean hasLocation() {
            return netLocation != null && gpsLocation != null || System.currentTimeMillis() > timeTrigger;
        }

        Location getBestLocation() {
            float netAccuracy = netLocation == null ? Float.MAX_VALUE : netLocation.getAccuracy();
            float gpsAccuracy = gpsLocation == null ? Float.MAX_VALUE : gpsLocation.getAccuracy();
            if (gpsAccuracy < netAccuracy)
                return gpsLocation;
            else if (netAccuracy < gpsAccuracy)
                return netLocation;
            else if (gpsAccuracy != Float.MAX_VALUE)
                return gpsLocation;
            else
                return null;
        }

        @Override
        public void onLocationChanged(Location location) {
            Log.d(LOG_TAG, location.toString());
            if (location.getProvider().equals(LocationManager.GPS_PROVIDER)) {
                timeTrigger -= (COLD_TIME_OFFSET - HOT_TIME_OFFSET);
                gpsLocation = location;
            } else {
                netLocation = location;
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }

        void reset() {
            timeTrigger = System.currentTimeMillis() + COLD_TIME_OFFSET;
            netLocation = null;
            gpsLocation = null;
        }


        void kill() {
            if (locationManager != null) {
                locationManager.removeUpdates(this);
                locationManager = null;
            }
        }

        @Override
        public String toString() {
            return String.format("best location: %s", getBestLocation());
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class SendPositionTo extends AsyncTask<Object, Void, Void> {
        @Override
        protected Void doInBackground(Object... objects) {
            WifiManager wifiManager = (WifiManager)objects[0];
            String uri = (String) objects[1];
            sendPosition(uri, wifiManager);
            return null;
        }

        private void sendPosition(String uri, WifiManager wifiManager) {
            if (uri != null) {
                WifiManager.WifiLock wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, LOG_TAG);
                wifiLock.acquire();
                try {
                    HttpURLConnection connection = null;
                    URL url = new URL(uri);
                    try {
                        connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("GET");
                        connection.connect();
                        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                            Log.d(LOG_TAG, "send position success");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (connection != null) {
                            connection.disconnect();
                        }
                    }
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
                wifiLock.release();
            }
        }
    }
}
