package jp.or.myhome.sample.locationrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import android.Manifest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Iterator;
import java.util.List;

public class LocationService extends Service{
    Context context;
    NotificationManager notificationManager;
    SharedPreferences pref;
    FusedLocationProviderClient fusedLocationClient;
    LocationCallback locationCallback;

    static final String target_url_base = "https://【ThingsBoardサーバのホスト名】/api/v1/";
    static final String target_telemetry = "/telemetry";

    public static final String LocationUpdateAction = "LocationUpdateAction";
    public static final int DefaultMinTime = 5000; // msec
    public static final float DefaultMinDistance = 10.0f; // meter
    public static final int DefaultUploadDuration = 30000; // msec

    static final String CHANNEL_ID = "default";
    static final String NOTIFICATION_TITLE = "位置情報の記録中";
    static final int NOTIFICATION_ID = 1;
    static final int DEFAULT_TIMEOUT = 10000;
    LocationDbHelper helper;
    SQLiteDatabase db;
    int uploadDuration;
    long lastUploadDatetime = 0;
    boolean uploading = false;

    @Override
    public void onCreate() {
        super.onCreate();

        context = getApplicationContext();
        pref = getSharedPreferences("Private", MODE_PRIVATE);
        helper = new LocationDbHelper(this);
        db = helper.getWritableDatabase();

        notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if( notificationManager == null ) {
            Log.d(MainActivity.TAG, "NotificationManager not available");
            return;
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        if( fusedLocationClient == null ) {
            Log.d(MainActivity.TAG, "fusedLocationClient not available");
            return;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(MainActivity.TAG, "onStartCommand");

        uploadDuration = pref.getInt("uploadDuration", DefaultUploadDuration);

        if( notificationManager != null ){
            Intent notifyIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, NOTIFICATION_TITLE , NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
            Notification notification = new Notification.Builder(context, CHANNEL_ID)
                    .setContentTitle(NOTIFICATION_TITLE)
                    .setSmallIcon(android.R.drawable.btn_star)
                    .setAutoCancel(false)
                    .setShowWhen(true)
                    .setWhen(System.currentTimeMillis())
                    .setContentIntent(pendingIntent)
                    .build();
            startForeground(NOTIFICATION_ID, notification);

            startGPS();
        }

        return START_NOT_STICKY;
    }

    private void startGPS() {
        if (fusedLocationClient != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                return;

            int minTime = pref.getInt("minTime", DefaultMinTime);
            float minDistance = pref.getFloat("minDistance", DefaultMinDistance);

            LocationRequest locationRequest = new LocationRequest.Builder(minTime)
                    .setMinUpdateIntervalMillis(minTime)
                    .setMinUpdateDistanceMeters(minDistance)
                    .build();
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    super.onLocationResult(locationResult);
                    Log.d(MainActivity.TAG, "onLocationResult");

                    Location location = locationResult.getLastLocation();

                    long currentTime = System.currentTimeMillis();
                    LocationDbHelper.LocationItem item = helper.insertLocation(db, currentTime, location.getLatitude(), location.getLongitude(), location.getSpeed());

                    Intent broadcastIntent = new Intent();
                    broadcastIntent.setAction(LocationUpdateAction);
                    broadcastIntent.putExtra("datetime", item.datetime);
                    broadcastIntent.putExtra("lat", item.lat);
                    broadcastIntent.putExtra("lng", item.lng);
                    broadcastIntent.putExtra("speed", item.speed);
                    sendBroadcast(broadcastIntent);

                    if((currentTime - lastUploadDatetime ) >= uploadDuration )
                        uploadLocationList();
                }
            }, Looper.getMainLooper());
        }
    }

    private void uploadLocationList(){
        if( uploading )
            return;
        uploading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    List<LocationDbHelper.LocationItem> locationList = helper.getLocationList(db);
                    Iterator<LocationDbHelper.LocationItem> iterator = locationList.listIterator();
                    JSONArray records = new JSONArray();
                    long lastDatetime = 0;
                    while(iterator.hasNext()){
                        LocationDbHelper.LocationItem item = iterator.next();
                        JSONObject values = new JSONObject();
                        values.put("lat", item.lat);
                        values.put("lng", item.lng);
                        values.put("speed", item.speed);
                        values.put("datetime", item.datetime);
                        JSONObject record = new JSONObject();
                        record.put("ts", item.datetime);
                        record.put("values", values);
                        records.put(record);
                        lastDatetime = item.datetime;
                    }
                    if( records.length() > 0 ) {
                        String accessToken = pref.getString("accessToken", "DummyAccessToken");
                        HttpPostJson.doPost(target_url_base + accessToken + target_telemetry, records.toString(), DEFAULT_TIMEOUT);

                        helper.deleteLocationList(db, lastDatetime);
                        lastUploadDatetime = lastDatetime;
                        Log.d(MainActivity.TAG, "log uploaded");
                    }
                }catch(Exception ex){
                    Log.e(MainActivity.TAG, ex.getMessage());
                }finally {
                    uploading = false;
                }
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        Log.d(MainActivity.TAG, "onDestroy");

        if( locationCallback != null ){
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }

        uploadLocationList();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
