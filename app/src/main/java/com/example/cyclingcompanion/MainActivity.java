package com.example.cyclingcompanion;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    private Button bCreateRide, bViewHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //create ride button
        bCreateRide = (Button) findViewById(R.id.button_createRide);
        bCreateRide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //createRide activity
                Intent intent = new Intent(MainActivity.this, CreateRide.class);
                startActivity(intent);
            }
        });

        //ride history button
        bViewHistory = (Button) findViewById(R.id.button_viewPrev);
        bViewHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //rideHistory activity
                Intent intent = new Intent(MainActivity.this, RideHistory.class);
                startActivity(intent);
            }
        });
    }
}