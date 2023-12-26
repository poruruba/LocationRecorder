package jp.or.myhome.sample.locationrecorder;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, Handler.Callback {
    public static final String TAG = "TAG";
    private static final int REQUEST_PERMISSION_LOCATION = 101;
    UIHandler handler;
    SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    LocationDbHelper.LocationItem lastLocation;
    SharedPreferences pref;
    private static final String DefaultAccessToken = "【ThingsBoardのアクセストークン】";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new UIHandler(this);
        pref = getSharedPreferences("Private", MODE_PRIVATE);

        Button btn;
        btn = (Button) findViewById(R.id.btn_start);
        btn.setOnClickListener(this);
        btn = (Button) findViewById(R.id.btn_stop);
        btn.setOnClickListener(this);

        int minTime = pref.getInt("minTime", LocationService.DefaultMinTime);
        float minDistance = pref.getFloat("minDistance", LocationService.DefaultMinDistance);
        int minUploadDuration = pref.getInt("uploadDuration", LocationService.DefaultUploadDuration);
        String accessToken = pref.getString("accessToken", DefaultAccessToken);

        EditText edit;
        edit = (EditText)findViewById(R.id.edit_mintime);
        edit.setText(String.valueOf(minTime));
        edit = (EditText)findViewById(R.id.edit_mindistance);
        edit.setText(String.valueOf(minDistance));
        edit = (EditText)findViewById(R.id.edit_uploadduration);
        edit.setText(String.valueOf(minUploadDuration));
        edit = (EditText)findViewById(R.id.edit_accesstoken);
        edit.setText(accessToken);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(LocationService.LocationUpdateAction);
        registerReceiver(mReceiver, intentFilter);

        checkLocationPermissions();
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive");

            Bundle bundle = intent.getExtras();
            long datetime = bundle.getLong("datetime");
            double lat = bundle.getDouble("lat");
            double lng = bundle.getDouble("lng");
            float speed = bundle.getFloat("speed");
            LocationDbHelper.LocationItem item = new LocationDbHelper.LocationItem(datetime, lat, lng, speed);
            handler.sendUIMessage(UIHandler.MSG_ID_OBJECT, item);
        }
    };

    private void checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSION_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                Toast.makeText(this, "位置情報の許可がないので計測できません", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.btn_start) {
            EditText text;
            text = (EditText)findViewById(R.id.edit_mintime);
            int minTime = Integer.parseInt(text.getText().toString());
            text.setEnabled(false);
            text.setFocusable(false);
            text = (EditText)findViewById(R.id.edit_mindistance);
            float minDistance = Float.parseFloat(text.getText().toString());
            text.setEnabled(false);
            text.setFocusable(false);
            text = (EditText)findViewById(R.id.edit_uploadduration);
            int uploadDuration = Integer.parseInt(text.getText().toString());
            text.setEnabled(false);
            text.setFocusable(false);
            text = (EditText)findViewById(R.id.edit_accesstoken);
            String accessToken = text.getText().toString();
            text.setEnabled(false);
            text.setFocusable(false);

            SharedPreferences.Editor editor = pref.edit();
            editor.putString("accessToken", accessToken);
            editor.putInt("minTime", minTime);
            editor.putFloat("minDistance", minDistance);
            editor.putInt("uploadDuration", uploadDuration);
            editor.apply();

            Intent intent = new Intent(this, LocationService.class);
            startForegroundService(intent);
        } else if (id == R.id.btn_stop) {
            Intent intent = new Intent(this, LocationService.class);
            stopService(intent);

            EditText text;
            text = (EditText)findViewById(R.id.edit_mintime);
            text.setEnabled(true);
            text.setFocusable(true);
            text = (EditText)findViewById(R.id.edit_mindistance);
            text.setEnabled(true);
            text.setFocusable(true);
            text = (EditText)findViewById(R.id.edit_uploadduration);
            text.setEnabled(true);
            text.setFocusable(true);
            text = (EditText)findViewById(R.id.edit_accesstoken);
            text.setEnabled(true);
            text.setFocusable(true);
        }
    }

    @Override
    public boolean handleMessage(Message message) {
        if( message.what == UIHandler.MSG_ID_OBJECT ){
            lastLocation = (LocationDbHelper.LocationItem)message.obj;

            TextView text;
            text = (TextView)findViewById(R.id.txt_datetime);
            text.setText(format.format(lastLocation.datetime));
            text = (TextView)findViewById(R.id.txt_lat);
            text.setText(String.valueOf(lastLocation.lat));
            text = (TextView)findViewById(R.id.txt_lng);
            text.setText(String.valueOf(lastLocation.lng));
            text = (TextView)findViewById(R.id.txt_speed);
            text.setText(String.valueOf(lastLocation.speed));

            return true;
        }
        return false;
    }
}