package com.example.soumi.ble_application;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.example.soumi.ble_application.Hardware.BLEService;
import java.util.IllegalFormatException;


public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    public static final int MSG_NOTIFY_STATE_CHANGE = 1;
    public static final int MSG_UPDATE_UI = 2;
    private static final int NOTIFY_ME_ID = 1336;
    public static boolean mServiceBounded;  // Variable to keep track of whether service is bounded to activity or not
    public static boolean mServiceRunning;
    public static boolean mPermission = false;
    private BLEService.BluetoothServiceBinder mServiceBinder;
    public boolean mActivityForegroundCheck;
    private final Messenger mMessenger = new Messenger(new IncomingHandler());
    private ServiceStateReceiver mServiceReceiver;
    private BluetoothAdapter mBtAdapter = null;
    public static final String TAG = MainActivity.class.getSimpleName();

    ProgressBar progressBar;
    LocationManager mLocationManager;
    PowerManager mPowerManager;
    NotificationManager notificationManager;
    BatteryManager mBatteryManager;
    MenuItem menuItem;
    boolean cameFromNotification;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null)
        {
            mServiceRunning = savedInstanceState.getBoolean("ServiceRunning");
            mServiceBounded = savedInstanceState.getBoolean("ServiceBinded");
            Log.d(TAG, "mServicerunning in onCreate" + mServiceRunning);
            Log.d(TAG,"mServiceBound in onCreate" + mServiceBounded);
        }
       // Fabric.with(this, new Crashlytics());
        setContentView(R.layout.activity_main);
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mBatteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (getIntent().getExtras() != null) {
            Bundle b = getIntent().getExtras();
            cameFromNotification = b.getBoolean("fromNotification");
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.action.service");
        //filter.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
        filter.addAction("android.action.destroyed");
       // filter.addAction("android.location.PROVIDERS_CHANGED");
        mServiceReceiver = new ServiceStateReceiver();
        registerReceiver(mServiceReceiver, filter);

    }
    @TargetApi(Build.VERSION_CODES.M)
    private void openOverlaySettings() {
        final Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivityForResult(intent, 2);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, e.getMessage());
        }
    }
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        Log.d(TAG, "onSaveInstanceState");
        savedInstanceState.putBoolean("ServiceRunning",mServiceRunning);
        savedInstanceState.putBoolean("ServiceBinded",mServiceBounded);
        savedInstanceState.putBoolean("Permission",mPermission);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mServiceRunning = savedInstanceState.getBoolean("ServiceRunning");
        mServiceBounded = savedInstanceState.getBoolean("ServiceBinded");
        mPermission = savedInstanceState.getBoolean("Permission");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main,menu);
        menuItem = menu.findItem(R.id.progress_search_bar);
        progressBar = (ProgressBar) MenuItemCompat.getActionView(menuItem);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId())
        {
            case R.id.action_search_bar:
                Toast.makeText(MainActivity.this,"Clicked on Search",Toast.LENGTH_LONG).show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Toast.makeText(MainActivity.this, "service connected", Toast.LENGTH_SHORT).show();
            mServiceBinder = (BLEService.BluetoothServiceBinder) iBinder;
            mServiceBinder.registerActivityMessenger(mMessenger);
            mServiceBounded = true;

            if (cameFromNotification) {
                mServiceBinder.StartBLEScan();
                Log.d(TAG,"Called start BLE SCAN");
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Toast.makeText(MainActivity.this, "service disconnected", Toast.LENGTH_SHORT).show();
            mServiceBounded = false;
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        mActivityForegroundCheck = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            openOverlaySettings();
        }
        // Add Bluetooth Permissions in the manifest file before writing the first bluetooth <code></code>
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "ble not supported", Toast.LENGTH_LONG).show();
            finish();
        }

        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onClick - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);

        }
        if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            Log.d(TAG,"GPS is disabled from activity");
            display_notification("Enable GPS for data to sync",1);
        }
        else if (mBtAdapter.isEnabled() && mPermission) {

            if (!mServiceRunning && !mServiceBounded) {

                startService(new Intent(getApplicationContext(), BLEService.class));
                bindService(new Intent(this, BLEService.class), mServiceConnection, Context.BIND_AUTO_CREATE);
                mServiceRunning = true;
            }
            else if (!mServiceBounded && mServiceRunning) {
                bindService(new Intent(this, BLEService.class), mServiceConnection, Context.BIND_AUTO_CREATE);
            }

            else if (mServiceBounded && !mServiceRunning) {
                startService(new Intent(getApplicationContext(), BLEService.class));
            }

        } else if (mBtAdapter.isEnabled() && !mPermission) {
            if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED)) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            }
        }



    }
    @Override
    protected void onPause() {
        super.onPause();
        if (mServiceRunning && mServiceBounded) {
            unbindService(mServiceConnection);
            mServiceBounded = false;
        }
        if (mServiceReceiver != null) {
            unregisterReceiver(mServiceReceiver);
            mServiceReceiver = null;
        }
        mActivityForegroundCheck = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Activity Destroyed");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    Log.d(TAG,"Permission Granted");
                    mPermission = true;

                } else {
                    mPermission = false;
                    // permission denied
                }
                return;
            }

        }
    }

    protected void display_notification(String data , int state)
    {
        PendingIntent pendingIntent = null;
        if (state == 0) {
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);// A new intent to launch another activity
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

            // This provides a builder interface to create a Notification object
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
            // Setting this flag as true enables auto deletion of notification on clicking
            mBuilder.setAutoCancel(true);
            mBuilder.setContentText(data);
            mBuilder.setContentTitle("BLE Service");
            mBuilder.setContentIntent(pendingIntent);
            mBuilder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
            mBuilder.setNumber(7);
            mBuilder.setSmallIcon(R.drawable.example_picture);
            Notification notification = mBuilder.build();
            notificationManager.notify(NOTIFY_ME_ID, notification);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

            case REQUEST_SELECT_DEVICE:
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);

                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Log.d(TAG, "BT enabled");
                    if (!mPermission)
                    {
                        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                                != PackageManager.PERMISSION_GRANTED)) {
                            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
                        }
                    }

                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    finish();
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
    }

    public class ServiceStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED"))
                startService(new Intent(getApplicationContext(), BLEService.class));

            if (intent.getAction().equals("android.action.unbinded")) {
                if (intent.getStringExtra("unbind").equals("service Unbinded"))
                    mServiceBounded = false;
            }
            if (intent.getAction().equals("android.action.destroyed")) {
                if (intent.getStringExtra("destroy").equals("ServiceDestroyed"))
                    mServiceRunning = false;
            }

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch(state) {
                    case BluetoothAdapter.STATE_ON:
                        if (!mServiceRunning)
                            display_notification("Service can be started again",0);
                }
            }
            if (intent.getAction().matches("android.location.PROVIDERS_CHANGED")) {

               Log.d(TAG,"Location Providers Changed");
                if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                    Log.d(TAG,"GPS is enabled");
                    notificationManager.cancel(NOTIFY_ME_ID);
                } else {
                    display_notification("GPS is disabled",1);
                    Log.d(TAG,"GPS is disabled");
                }
            }
        }
    }

    private void UpdateUi(Pair<String, Object> uiStatePair) {
        String key = uiStatePair.first;
        Object data = uiStatePair.second;
        switch (key) {
            case "Result":
                break;
            default:
                break;
        }
    }

    class IncomingHandler extends Handler {
        String final_data = null;

        @Override
        public void handleMessage(Message msg) {
            int respCode = msg.what;
            switch (respCode) {
                case MSG_NOTIFY_STATE_CHANGE: {

                  /*  BLEService.ServiceState serviceState = (BLEService.ServiceState) msg.obj;
                    switch(serviceState)
                    {
                        case SCANNING:
                            break;
                        case CONNECTED:
                            Toast.makeText(getApplicationContext(),"Connected",Toast.LENGTH_LONG).show();
                            break;
                        case DISCONNECTED:
                            break;
                    }*/
                }
                case MSG_UPDATE_UI:
                    break;
                default:
                    super.handleMessage(msg);
                    break;

            }
        }

    }
}


