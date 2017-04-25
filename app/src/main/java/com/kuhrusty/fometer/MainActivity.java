package com.kuhrusty.fometer;

import android.content.Context;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * This has a background image of the meter, a TextView for the digital readout,
 * and an area you touch; when touched, we start beeping, and start a timer
 * which updates the readout.
 */
public class MainActivity extends AppCompatActivity
        implements MediaPlayer.OnCompletionListener, SensorEventListener {
    //private final String LOGBIT = "MainActivity";

    //  True if the user's finger is down and we're fiddling with the readout.
    private boolean calculating = false;
    private MediaPlayer beepPlayer = null;
    private CountDownTimer timer = null;
    private TextView readout = null;
    private ImageView givenLabel = null;
    private DecimalFormat readoutFormat = new DecimalFormat("0.0#####");
    double currentFsGiven;  //  the value we're currently displaying
    double targetFsGiven;  //  the value we're going to end with

    private SensorManager sensorManager;
    private Sensor sensor = null;

    //  the rest of these guys are used if we don't have a gravity sensor; in
    //  that case, we're looking for a certain number of move events toward the
    //  lower right.
    int updates = 0;
    int updateDecisionPoint = 4;
    //  how many events we've received hinting that the user gives 0 fs.
    int zeroHints = 0;
    int hintThreshold = 10;
    float lastX, lastY;

    /**
     * This listens for touch events on the "place finger here" image.  When
     * we get a touch down, we start the beep audio and start fiddling with the
     * readout; if we get a touch up before the beep audio ends, we kill the
     * audio and set the readout to "BAD READ."  If the audio ends before we get
     * a touch up, we quit fiddling with the readout and stop caring about the
     * touch up.
     */
    private final View.OnTouchListener fingerListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (calculating && (updates < updateDecisionPoint)) {
                    if ((lastX != 0) && (event.getX() > lastX) &&
                        (lastY != 0) && (event.getY() > lastY)) {
                        ++zeroHints;
                    }
                    lastX = event.getX();
                    lastY = event.getY();
                }
            } else if (event.getAction() == MotionEvent.ACTION_DOWN) {
                startCalculating();
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (calculating) {
                    abortCalculating();
                    return true;
                }
            }
            return false;
        }
    };

    //  MediaPlayer.OnCompletionListener
    @Override
    public void onCompletion(MediaPlayer mp) {
        mp.release();
        if (mp == beepPlayer) {
            beepPlayer = null;
        }
        doneCalculating();
    }

    /**
     * Start the beep audio and start fiddling with the readout.
     */
    private void startCalculating() {
        calculating = true;
        if (timer != null) {
            timer.cancel();
        }
        givenLabel.setVisibility(View.INVISIBLE);
        currentFsGiven = 0.8 + (Math.random() / 10.0);
        targetFsGiven = 1.0 + (Math.random() / 10.0);
        updates = 0;
        zeroHints = 0;
        lastX = 0;
        lastY = 0;
        setReadout(currentFsGiven);
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        //  We want it to last at least as long as the beep sound, which we,
        //  uhh, assume is less than 10 seconds...
        timer = new CountDownTimer(10000, 500) {
            @Override
            public void onTick(long l) {
                if (sensor == null) {
                    ++updates;
                    if ((updates == updateDecisionPoint) && (zeroHints >= hintThreshold)) {
                        decideZeroFs();
                    }
                }
                currentFsGiven += ((targetFsGiven - currentFsGiven) * 0.5);
                setReadout(currentFsGiven);
            }

            @Override
            public void onFinish() {
                //  well... we don't really care, because we expect to be
                //  cancelled before this gets hit.
            }
        };
        timer.start();

        beepPlayer = MediaPlayer.create(this, R.raw.beep);
        beepPlayer.setOnCompletionListener(this);
        //beepPlayer.setOnErrorListener(this);
        beepPlayer.start();
    }

    private void doneCalculating() {
        calculating = false;
        if (sensor != null) {
            sensorManager.unregisterListener(this, sensor);
        }
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        setReadout(targetFsGiven);
        givenLabel.setVisibility(View.VISIBLE);
    }

    /**
     * Kill the beep audio and set the readout to "BAD READ."
     */
    private void abortCalculating() {
        calculating = false;
        if (sensor != null) {
            sensorManager.unregisterListener(this, sensor);
        }
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (beepPlayer != null) {
            beepPlayer.release();
            beepPlayer = null;
        }
        givenLabel.setVisibility(View.INVISIBLE);
        readout.setText(getResources().getText(R.string.bad_read));
    }

    private void decideZeroFs() {
        targetFsGiven = Math.random() / 10000.0;
    }
    private void setReadout(double val) {
        readout.setText(readoutFormat.format(val));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);

        readout = (TextView)(findViewById(R.id.readout));
        readout.setTypeface(Typeface.createFromAsset(getAssets(),
                "fonts/TickingTimebombBB_ital.ttf"));

        givenLabel = (ImageView)(findViewById(R.id.givenLabel));

        View fingerBox = findViewById(R.id.fingerBox);
        fingerBox.setOnTouchListener(fingerListener);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if ((sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)) == null) {
//        if ((sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)) == null) {
//            Toast.makeText(this, getResources().getString(R.string.no_linear_accelerator),
//                    Toast.LENGTH_LONG).show();
        } else {
//            Toast.makeText(this, getResources().getString(R.string.instructions_shake),
//                    Toast.LENGTH_SHORT).show();
        }

//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//            }
//        });
    }

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.menu_main, menu);
//        return true;
//    }

//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        // Handle action bar item clicks here. The action bar will
//        // automatically handle clicks on the Home/Up button, so long
//        // as you specify a parent activity in AndroidManifest.xml.
//        int id = item.getItemId();
//
//        //noinspection SimplifiableIfStatement
//        if (id == R.id.action_settings) {
//            return true;
//        }
//
//        return super.onOptionsItemSelected(item);
//    }

//    @Override
//    protected void onResume() {
//        super.onResume();
//        if (sensor != null) {
//            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
//        }
//    }

    @Override
    protected void onPause() {
        sensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    public void onStop() {
        if (beepPlayer != null) {
            beepPlayer.release();
            beepPlayer = null;
        }
        super.onStop();
    }

    @Override
    public void onSensorChanged(SensorEvent ev) {
        if (ev.values[0] < 0.0) {
            decideZeroFs();
        }
        //  OK to unregister ourselves as a listener inside onSensorChanged()?
        sensorManager.unregisterListener(this, sensor);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        //  ehh.
    }
}
