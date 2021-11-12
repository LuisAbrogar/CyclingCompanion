package com.example.cyclingcompanion;
import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class CreateRide extends FragmentActivity implements OnMapReadyCallback, SensorEventListener {
    //UI
    private Button bMenu, bStart, bEnd;
    private TextView tSpeedT, tSpeed, tElev, tElevT, tDist, tDistT, tTime, tTimeT;

    //location services
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private int REQUEST_INTERVAL = 2000; //rate in ms
    private static final String[] LOCATION_PERMS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };
    private Location startLocation = null, endLocation = null, currentLocation = null;
    private double c_lat, c_lng;
    private boolean rideStarted = false;

    //time
    private int seconds = 0;
    private String s_time;
    private Handler handler;

    //speed
    private double avgSpeed = 0;
    private int ctrSpeed = 0;
    private String s_speed;

    //distance
    private double distance = 0;
    private String s_distance;

    //elevation
    private SensorManager sensorManager;
    private float[] accelerometerR = new float[3];
    private float[] magnetometerR = new float[3];
    private float[] rotationMatrix = new float[9];
    private float[] orientationAngles = new float[3];
    private float avgElev = 0;
    private int ctrElev = 0;
    private String s_elevation;

    //map
    private GoogleMap mMap;
    private Polyline polyline = null;
    private List<LatLng> route = new ArrayList<>();

    //firebase
    private FirebaseDatabase db = FirebaseDatabase.getInstance();

    //popup alert / dialog
    private AlertDialog.Builder builder;
    private String s_ridename;
    private EditText input;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_createride);
        //Dialog---------------------------------------------------------------------------------------
        input = new EditText(this);
        builder = new AlertDialog.Builder(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setTitle("Enter Ride Name");
        builder.setView(input);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                s_ridename = input.getText().toString();
            }
        });
        builder.show();
        //TEXT VIEWS------------------------------------------------------------------------------------
        tSpeedT = (TextView) findViewById(R.id.tv_CR_title_speed);
        tSpeed = (TextView) findViewById(R.id.tv_CR_speed);
        tElevT = (TextView) findViewById(R.id.tv_CR_title_elevation);
        tElev = (TextView) findViewById(R.id.tv_CR_elevation);
        tDistT = (TextView) findViewById(R.id.tv_CR_title_distance);
        tDist = (TextView) findViewById(R.id.tv_CR_distance);
        tTimeT = (TextView) findViewById(R.id.tv_CR_title_time);
        tTime = (TextView) findViewById(R.id.tv_CR_time);
        //SENSOR---------------------------------------------------------------------------------------
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        //BUTTONS---------------------------------------------------------------------------------------
        //start ride
        bStart = (Button) findViewById(R.id.button_CR_startRide);
        bStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRide();
            }
        });

        //end ride
        bEnd = (Button) findViewById(R.id.button_CR_endRide);
        bEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endRide();
            }
        });
        //back to main menu
        bMenu = (Button) findViewById(R.id.button_CR_mainMenu);
        bMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //createRide activity
                Intent intent = new Intent(CreateRide.this, MainActivity.class);
                startActivity(intent);
            }
        });
        //STARTUP--------------------------------------------------------------
        //invisible stats and 'end ride' button
        tSpeedT.setVisibility(View.INVISIBLE);
        tSpeed.setVisibility(View.INVISIBLE);
        tElev.setVisibility(View.INVISIBLE);
        tElevT.setVisibility(View.INVISIBLE);
        tDist.setVisibility(View.INVISIBLE);
        tDistT.setVisibility(View.INVISIBLE);
        tTime.setVisibility(View.INVISIBLE);
        tTimeT.setVisibility(View.INVISIBLE);
        bEnd.setVisibility(View.INVISIBLE);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        startLocationUpdates();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.CR_map);
        mapFragment.getMapAsync(this);
    }

    /**
     * Starting Ride
     */
    private void startRide() {
        //visible stats and 'end ride', invisible 'start ride'
        tSpeedT.setVisibility(View.VISIBLE);
        tSpeed.setVisibility(View.VISIBLE);
        tElev.setVisibility(View.VISIBLE);
        tElevT.setVisibility(View.VISIBLE);
        tDist.setVisibility(View.VISIBLE);
        tDistT.setVisibility(View.VISIBLE);
        tTime.setVisibility(View.VISIBLE);
        tTimeT.setVisibility(View.VISIBLE);
        bStart.setVisibility(View.INVISIBLE);
        bEnd.setVisibility(View.VISIBLE);

        //register sensor listeners
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if(accelerometer != null)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        Sensor magnetic = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if(magnetic != null)
            sensorManager.registerListener(this, magnetic, SensorManager.SENSOR_DELAY_NORMAL);




        rideStarted = true;
        runClock();
    }

    /**
     * Ending Ride
     * Mark end with red marker
     */
    private void endRide(){
        bEnd.setVisibility(View.INVISIBLE);
        rideStarted = false;
        stopLocationUpdates();
        c_lat = endLocation.getLatitude();
        c_lng = endLocation.getLongitude();
        LatLng c_location = new LatLng(c_lat, c_lng);
        mMap.addMarker(new MarkerOptions()
                .position(c_location)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        );
        //avg speed
        tSpeedT.setText("Average Speed:");
        avgSpeed /= ctrSpeed;
        s_speed = String.format(Locale.getDefault(),"%02.2f mph",avgSpeed);
        tSpeed.setText(s_speed);

        //total distance
        tDistT.setText("Total Distance:");

        //total time
        tTimeT.setText("Total Time:");

        //avg elevation
        tElevT.setText("Average Elevation:");
        avgElev /= ctrElev;
        s_elevation = String.format(Locale.getDefault(),"%02.2f degrees",avgElev);
        tElev.setText(s_elevation);
        //stop sensor updates
        sensorManager.unregisterListener(this);

        //store into firebase using a HashMap
        HashMap<String, Object> info = new HashMap<>();
        info.put("Avg Speed", s_speed);
        info.put("Avg Elevation", s_elevation);
        info.put("Total Distance", s_distance);
        info.put("Elapsed Time", s_time);


        db.getReference().child("Rides").child(s_ridename).updateChildren(info);
        Toast t = Toast.makeText(getApplicationContext(), "Ride Successfully Saved!", Toast.LENGTH_SHORT);
        t.show();

        stopLocationUpdates();



    }

   //location
    private void startLocationUpdates(){
        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(REQUEST_INTERVAL);

        //requesting permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(LOCATION_PERMS, 100);
            }
        }

        //check permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("CREATE_RIDE", "FAIL");
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, mLocationCallback, Looper.getMainLooper());
    }

    private LocationCallback mLocationCallback = new LocationCallback() {
      @Override
      public void onLocationResult(LocationResult locationResult) {
          if(locationResult == null)
              startLocationUpdates();
          currentLocation = locationResult.getLastLocation();
          c_lat = currentLocation.getLatitude();
          c_lng = currentLocation.getLongitude();
          LatLng c_location = new LatLng(c_lat, c_lng);
          route.add(c_location);
          mMap.moveCamera((CameraUpdateFactory.newLatLng(c_location)));
          mMap.moveCamera((CameraUpdateFactory.newLatLngZoom(c_location, 18)));

          //current location is start if start is not previously defined
          //mark with green marker
          if(startLocation == null && rideStarted) {
              startLocation = currentLocation;
              mMap.addMarker(new MarkerOptions()
                      .position(c_location)
                      .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
              );
          }
          //draw line
          //set end as current location
          //
          else if (rideStarted){
              //drawing polyline
              //quickly erase old polyline and draw new one with updated route
              if(polyline != null) polyline.remove();
              PolylineOptions polylineOptions = new PolylineOptions()
                      .addAll(route)
                      .clickable(true)
                      .color(Color.BLUE);
              polyline = mMap.addPolyline(polylineOptions);



              //calculating speed
              //gather stats for average speed
              double speed = currentLocation.getSpeed() * 2.237;
              avgSpeed += speed;
              ctrSpeed++;
              String s = String.format(Locale.getDefault(), "%02.2f mph", speed);
              tSpeed.setText(s);


              //calculating distance
              //from start if end is null
              //from end otherwise
              if(endLocation == null)
                  distance += currentLocation.distanceTo(startLocation) / 1609;
              else
                  distance += currentLocation.distanceTo(endLocation) / 1609;

              s_distance = String.format(Locale.getDefault(), "%02.2f mi.", distance);
              tDist.setText(s_distance);

              //get elevation
              //gather stats for average elevation
              updateOrientation();
              float elev = orientationAngles[1] * -100;
              avgElev += elev;
              ctrElev++;
              String e = String.format(Locale.getDefault(), "%02.2f degrees", elev);
              tElev.setText(e);


              //set current location as end
              endLocation = currentLocation;


          }

      }
    };

    //clock function
    private void runClock() {
        handler = new Handler();
        handler.post(new Runnable() {
           @Override
           public void run() {
               int secs = seconds % 60;
               int mins = (seconds % 3600) / 60;
               int hrs = seconds / 3600;

               //clock format
               s_time = String.format(Locale.getDefault(), "%d:%02d:%02d", hrs, mins, secs );
               tTime.setText(s_time);
               if(rideStarted)
                   seconds++;
               handler.postDelayed(this, 1000);
           }
        });

    }
    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(mLocationCallback);
    }
    //Sensors
    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            System.arraycopy(event.values, 0, accelerometerR, 0, accelerometerR.length);
        else if(event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            System.arraycopy(event.values, 0, magnetometerR, 0, magnetometerR.length);
    }
    //updates matrices
    public void updateOrientation() {
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerR, magnetometerR);
        SensorManager.getOrientation(rotationMatrix,orientationAngles);
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

}