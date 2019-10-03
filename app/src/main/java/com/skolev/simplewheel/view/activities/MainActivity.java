package com.skolev.simplewheel.view.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;

import com.skolev.simplewheel.R;
import com.skolev.simplewheel.view.custom_views.simple_wheel.SimpleWheelView;


public class MainActivity extends AppCompatActivity implements SimpleWheelView.OnWheelEventListener {

    Button button;
    private SimpleWheelView wheel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.wheel = findViewById(R.id.wheel);
        this.wheel.setOnClickListener(v -> {
        });
        this.wheel.setListener(this);
        this.button = findViewById(R.id.button);
        this.button.setOnClickListener(v ->
        {
            this.wheel.reset();
            this.wheel.setEnabled(true);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.wheel.postDelayed(() -> wheel.setCenterText("SimpleWheel"), 2000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.wheel.destroy();
    }

    @Override
    public void onWheelSpin(int direction) {
        this.button.setEnabled(false);
        this.button.animate()
                .alpha(0)
                .scaleX(0f)
                .scaleY(0)
                .setDuration(500)
                .setInterpolator(new OvershootInterpolator())
                .start();
        this.button.setVisibility(View.VISIBLE);
    }

    @Override
    public void onWheelStop(int sector) {
        this.button.setEnabled(true);
        this.button.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(500)
                .setInterpolator(new OvershootInterpolator())
                .start();
    }
}
