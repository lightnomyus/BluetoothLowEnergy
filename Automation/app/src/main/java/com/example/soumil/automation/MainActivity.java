package com.example.soumil.automation;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.daimajia.numberprogressbar.NumberProgressBar;
import com.daimajia.numberprogressbar.OnProgressBarListener;

import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements OnProgressBarListener,View.OnClickListener {
    private Timer timer;

    private NumberProgressBar bnp;
    private Button StartTestButton;
    private Button CancelButton;
    private Button RestartButton;
    private Button EndTestButton;
    private Button BLEConnectButton;
    private TextView textView;
    private ServiceStateReceiver mServiceReceiver;
    public TextToSpeech tts;

    private static boolean mServiceBounded;
    private static boolean mServiceRunning;
    public static boolean mPermissionGranted;
    LocationManager mLocationManager;
    PowerManager mPowerManager;
    private BLEService.BLEServiceBinder mServiceBinder;
    public static final String TAG = MainActivity.class.getName();
    private BluetoothAdapter mBluetoothAdapter;
    private final Messenger mMessenger = new Messenger(new IncomingHandler());
    NotificationManager notificationManager;
    private static final int NOTIFY_ME_ID = 1336;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        StartTestButton = (Button) findViewById(R.id.StartTestButton);
        StartTestButton.setOnClickListener(this);
        CancelButton = (Button) findViewById(R.id.cancelTestButton);
        CancelButton.setOnClickListener(this);
        RestartButton = (Button) findViewById(R.id.RestartTestButton);
        RestartButton.setOnClickListener(this);
        EndTestButton = (Button) findViewById(R.id.EndTestButton);
        EndTestButton.setOnClickListener(this);
        BLEConnectButton = (Button) findViewById(R.id.ConnectButton);
        BLEConnectButton.setOnClickListener(this);
        textView = (TextView) findViewById(R.id.Progressbartxt);
        bnp = (NumberProgressBar) findViewById(R.id.numberbar1);
        bnp.setOnProgressBarListener(this);
        bnp.incrementProgressBy(1);
        if (savedInstanceState != null)
        {
            mPermissionGranted = savedInstanceState.getBoolean("Permission");
            mServiceBounded = savedInstanceState.getBoolean("Service Bounded");
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        if (mBluetoothAdapter == null) {
            Toast.makeText(this,"Bluetooth is not available", Toast.LENGTH_LONG).show();
        }
        tts =new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.US);
                    Log.d(TAG,"Language is set");
                }
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState)
    {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean("Permission",mPermissionGranted);
        savedInstanceState.putBoolean("Service Bounded",mServiceBounded);

    }
    protected void display_notification(String data , int state)
    {
        String CHANNEL_ID = "my_channel_01";// The id of the channel.
        CharSequence name = "Channel Name";// The user-visible name of the channel.
        Notification.Builder mBuilder;
        PendingIntent pendingIntent = null;
        if (state == 0) {
            Intent intent = new Intent(getApplicationContext(), MainActivity.class); // A new intent to launch another activity
        /* getActivity has the following parameters
        First - Context from which it is called
        Second - RequestCode for the sender. Using it later will give us the same pending intent
        Third - Intent object created before
        Fourth - FLAG- This one is used to state if a previous pendingIntent already exists or not. If it does then
        update it with the new intent. FLAG_UPDATE_CURRENT can be used for that.
         */
            pendingIntent = PendingIntent.getActivity(getApplicationContext(), (int) System.currentTimeMillis(), intent, 0);
        }
        else if (state == 1)
        {
            Intent myIntent = new Intent( Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            pendingIntent = PendingIntent.getActivity(getApplicationContext(), (int) System.currentTimeMillis(), myIntent, 0);
            //context.startActivity(myIntent);
        }
        // Notification is created using the notification manager.
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        int importance = NotificationManager.IMPORTANCE_HIGH;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
            mChannel.enableLights(true);
            mChannel.setLightColor(Color.RED);
            mChannel.setShowBadge(true);
            mChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(mChannel);
             mBuilder = new Notification.Builder(getApplicationContext(),CHANNEL_ID);
        }
        else
        {
             mBuilder = new Notification.Builder(getApplicationContext());

        }
        // This provides a builder interface to create a Notification object

        // Setting this flag as true enables auto deletion of notification on clicking
        mBuilder.setAutoCancel(true);
        mBuilder.setContentText(data);
        mBuilder.setContentTitle("BLE Service");
        mBuilder.setContentIntent(pendingIntent);
        mBuilder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
        mBuilder.setNumber(7);
        mBuilder.setChannelId(CHANNEL_ID);
        mBuilder.setSmallIcon(R.drawable.example_picture);
        Notification notification = mBuilder.build();
        notificationManager.notify(NOTIFY_ME_ID, notification);
    }


    @Override
    public void onResume()
    {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.action.service");
        filter.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
        filter.addAction("android.action.communication");
        filter.addAction("android.action.destroyed");
        filter.addAction("android.location.PROVIDERS_CHANGED");
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        mServiceReceiver = new ServiceStateReceiver();
        registerReceiver(mServiceReceiver, filter);

        if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            Log.d(TAG,"GPS is enabled");
            if (notificationManager != null)
                notificationManager.cancel(NOTIFY_ME_ID);
        }
        else
        {
            display_notification("GPS is disabled",1);
            Log.d(TAG,"GPS is disabled");
        }

        if (!mBluetoothAdapter.enable()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent,1);
        }
        String permissions[] = {Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_PRIVILEGED, Manifest.permission.ACCESS_COARSE_LOCATION};
        if (!mPermissionGranted)
        {
            ActivityCompat.requestPermissions(this, permissions,1);
        }
        else if (mPermissionGranted & mBluetoothAdapter.enable()) {
            bindService(new Intent(this,BLEService.class),mServiceConnection,Context.BIND_AUTO_CREATE);
        }

    }
    private void speakOut(String data) {

        String text = data;
        // tts = new TextToSpeech(this, this);
        //tts.setLanguage(Locale.US);
        tts.speak(text, TextToSpeech.QUEUE_ADD, null,null);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mServiceBounded)
            unbindService(mServiceConnection);
        mServiceBounded = false;
        try {
            if (mServiceReceiver != null)
                unregisterReceiver(mServiceReceiver);
        }catch (IllegalFormatException e)
        {
        }


    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mServiceBinder = (BLEService.BLEServiceBinder) iBinder;
            mServiceBinder.registerActivityMessenger(mMessenger);
            mServiceBounded = true;
            speakOut("Started Scanning");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mServiceBounded = false;

        }
    } ;

    @Override
    public void onRequestPermissionsResult(int resultCode, String permissions[], int[] grantResults)
    {
        switch(resultCode)
        {
            case 1:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    mPermissionGranted = true;
                    bindService(new Intent(this,BLEService.class),mServiceConnection,Context.BIND_AUTO_CREATE);

                }
                else
                {
                    mPermissionGranted = false;
                }
                break;
        }
        return;

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }


    }

    @Override
    public void onProgressChange(int current, int max) {
        if(current == max) {
            Toast.makeText(getApplicationContext(), "finish", Toast.LENGTH_SHORT).show();
            bnp.setProgress(0);
        }
    }

    @Override
    public void onClick(View view) {
        switch(view.getId())
        {
            case R.id.cancelTestButton:
                break;
            case R.id.ConnectButton:
                break;
            case R.id.EndTestButton:
                break;
            case R.id.StartTestButton:
                break;
            case R.id.RestartTestButton:
                break;
        }
    }

    public class IncomingHandler extends Handler
    {
        @Override
        public void handleMessage(Message message)
        {
            int responsecode = message.what;
            switch(responsecode)
            {
                default:
                    super.handleMessage(message);
            }
        }

    }

    public class ServiceStateReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.action.service"))
            {
                mServiceBounded = false;
                Toast.makeText(getApplicationContext(), "Service is unbinded",Toast.LENGTH_SHORT).show();
                speakOut("Stopped Scanning");

            }

            if (intent.getAction().matches("android.location.PROVIDERS_CHANGED")) {

                Log.d(TAG,"Location Providers Changed");
                if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                    Log.d(TAG,"GPS is enabled");
                    if (notificationManager!= null)
                        notificationManager.cancel(NOTIFY_ME_ID);
                }
                else
                {
                    display_notification("GPS is disabled",1);
                    Log.d(TAG,"GPS is disabled");
                }
            }

        }
    }
}
