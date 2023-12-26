package jp.or.myhome.sample.locationrecorder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class LocationDbHelper extends SQLiteOpenHelper{
    public static final String DATABASE_NAME = "LocationDb.db";
    public static final String TABLE_NAME = "LocationTbl";
    public static final int DATABASE_VERSION = 1;
    private static final String SQL_CREATE_ENTRIES = "CREATE TABLE " + TABLE_NAME + " (datetime INTEGER PRIMARY KEY, lat REAL, lng REAL, speed REAL)";
    private static final String SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS " + TABLE_NAME;

    LocationDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL( SQL_CREATE_ENTRIES );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL( SQL_DELETE_ENTRIES );
        onCreate(db);
    }

    public void deleteAllMessage(SQLiteDatabase db){
        db.delete(TABLE_NAME, null, null);
    }

    public LocationItem insertLocation(SQLiteDatabase db, long datetime, double lat, double lng, float speed){
        ContentValues values = new ContentValues();
        values.put("datetime", datetime);
        values.put("lat", lat);
        values.put("lng", lng);
        values.put("speed", speed);

        db.insert(TABLE_NAME, null, values);

        return new LocationItem(datetime, lat, lng, speed);
    }

    static public class LocationItem{
        public long datetime;
        public double lat;
        public double lng;
        public float speed;

        public LocationItem(long datetime, double lat, double lng, float speed){
            this.datetime = datetime;
            this.lat = lat;
            this.lng = lng;
            this.speed = speed;
        }
    }

    public List getLocationList(SQLiteDatabase db){
        Cursor cursor = db.query(TABLE_NAME, new String[] { "datetime", "lat", "lng", "speed" },
                null, null,
                null, null, "datetime ASC" );

        List list = new ArrayList<LocationItem>();
        while(cursor.moveToNext()) {
            list.add(new LocationItem(
                    cursor.getLong(0),
                    cursor.getDouble(1),
                    cursor.getDouble(2),
                    cursor.getFloat(3)
            ));
        }
        cursor.close();

        return list;
    }

    public void deleteLocationList(SQLiteDatabase db, long datetime_to){
        db.delete(TABLE_NAME, "datetime <= ?", new String[]{ String.valueOf(datetime_to) } );
    }
}
