package com.kuhrusty.fometer;

import android.content.Context;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.DecimalFormat;

/**
 * This has a background image of the meter, a TextView for the digital readout,
 * and an area you touch; when touched, we start beeping, and start a timer
 * which updates the readout.
 */
public class MainActivity extends AppCompatActivity
        implements SensorEventListener {
    //private final String LOGBIT = "MainActivity";

    //  True if the user's finger is down and we're fiddling with the readout.
    private boolean calculating = false;
    private MediaPlayer lowBeepPlayer = null;
    private MediaPlayer highBeepPlayer = null;
    private Thread timer = null;
    private TextView readout = null;
    private ImageView givenLabel = null;
    private DecimalFormat readoutFormat = new DecimalFormat("0.0#####");
    double currentFsGiven;  //  the value we're currently displaying
    double targetFsGiven;  //  the value we're going to end with

    //  Used by our timer thread to update the UI.
    private Runnable readoutUpdater = new Runnable() {
        @Override
        public void run() {
            setReadout(currentFsGiven);
        }
    };
    private Runnable doneCalculator = new Runnable() {
        @Override
        public void run() {
            doneCalculating();
        }
    };

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
     * readout; if we get a touch up before we're done calculating, we kill the
     * timer and set the readout to "BAD READ."
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

    /**
     * Start the beep audio and start fiddling with the readout.
     */
    private void startCalculating() {
        calculating = true;
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

        //  This was using CountDownTimer, but that was giving weird behavior
        //  (our first tick too early, and an almost-2-tick delay between the
        //  last tick and onFinish()), so instead this is just using a Thread,
        //  and a couple Runnables to update the UI thread.
        timer = new Thread(new Runnable() {
            @Override
            public void run() {
                int ticks = 0;
                long tickTime = 200;//250;
                long targetTime = System.currentTimeMillis() + tickTime;
                lowBeepPlayer.seekTo(0);
                lowBeepPlayer.start();

                while (calculating) {
                    try {
                        Thread.sleep(targetTime - System.currentTimeMillis());
                    } catch (InterruptedException ignored) {
                    }
                    if (!calculating) break;
                    ++ticks;
                    targetTime += tickTime;
                    if (sensor == null) {
                        ++updates;
                        if ((updates == updateDecisionPoint) && (zeroHints >= hintThreshold)) {
                            decideZeroFs();
                        }
                    }
                    currentFsGiven += ((targetFsGiven - currentFsGiven) * 0.5);
                    runOnUiThread(readoutUpdater);
                    //  if it's been a full second, start the second beep
                    if (((ticks == 5) || (ticks == 10)) && (lowBeepPlayer != null)) {
                        lowBeepPlayer.seekTo(0);
                        lowBeepPlayer.start();
                    } else if (ticks == 15) {
                        break;
                    }
                }
                //  we're done!  Start the high beep.
                if (calculating) {
                    runOnUiThread(doneCalculator);
                }
            }
        }, "timer");
        timer.start();
    }

    private void doneCalculating() {
        calculating = false;
        if (sensor != null) {
            sensorManager.unregisterListener(this, sensor);
        }
        //  presumably this was triggered by the timer was exiting run(), so we
        //  don't need to interrupt it.
        //if (timer != null) {
        //    timer.interrupt();  //  should see that calculating is false, and bail
            timer = null;
        //}
        setReadout(targetFsGiven);
        givenLabel.setVisibility(View.VISIBLE);
        highBeepPlayer.seekTo(0);
        highBeepPlayer.start();
    }

    /**
     * Kill the update timer and set the readout to "BAD READ."
     */
    private void abortCalculating() {
        calculating = false;
        if (sensor != null) {
            sensorManager.unregisterListener(this, sensor);
        }
        if (timer != null) {
            timer.interrupt();  //  should see that calculating is false, and bail
            timer = null;
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

        readout = (TextView)(findViewById(R.id.readout));
        readout.setTypeface(Typeface.createFromAsset(getAssets(),
                "fonts/TickingTimebombBB_ital.ttf"));

        givenLabel = (ImageView)(findViewById(R.id.givenLabel));

        View fingerBox = findViewById(R.id.fingerBox);
        fingerBox.setOnTouchListener(fingerListener);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        //  theoretically we don't care if that's null.
    }

    @Override
    public void onStart() {
        super.onStart();
        lowBeepPlayer = MediaPlayer.create(this, R.raw.lowbeep);
        highBeepPlayer = MediaPlayer.create(this, R.raw.highbeep);
    }

    @Override
    protected void onPause() {
        sensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    public void onStop() {
        if (timer != null) {
            calculating = false;
            timer.interrupt();  //  should see that calculating is false, and bail
            timer = null;
        }
        if (lowBeepPlayer != null) {
            lowBeepPlayer.release();
            lowBeepPlayer = null;
        }
        if (highBeepPlayer != null) {
            highBeepPlayer.release();
            highBeepPlayer = null;
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
