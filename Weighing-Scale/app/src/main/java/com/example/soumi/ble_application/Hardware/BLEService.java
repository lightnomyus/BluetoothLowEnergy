package com.example.soumi.ble_application.Hardware;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.Settings;
import android.app.AlertDialog;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.example.soumi.ble_application.MainActivity;
import com.example.soumi.ble_application.R;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import static java.lang.Thread.sleep;

public class BLEService extends Service {

     TextView text;
    public enum ServiceState {
        SCANNING, IDLE
    }
    public ServiceState mState = ServiceState.IDLE;
    public static boolean isServiceBinded;
    private static final int NOTIFY_ME_ID = 1337;
    public BluetoothManager mBluetoothManager;
    public BluetoothLeScanner mBluetoothLeScanner;
    public BluetoothAdapter mBluetoothAdapter;
    public ScanSettings.Builder mBuilderScanSettings;
    public ScanSettings mScanSettings;
    public ScanFilter mScanFilter;
    public ScanFilter.Builder mBuilderScanFilter;
    List<ScanFilter> mScanFilterList;
    private final IBinder mBinder = new BluetoothServiceBinder();
    AdvertisingDataParser mAdvertisingDataParser;
    public static PowerManager.WakeLock WAKELOCK = null;
    private Messenger mActivityMessenger ;
    BluetoothStateReceiver mBluetoothStateReceiver;
    LocationManager mLocationManager;
    PowerManager mPowerManager;
    String mDeviceAddress;
    boolean mSetIfStable;
    BatteryManager mBatteryManager;
    int mRssi;
    byte[] mManufacturerSpecificData = null;
    byte[] mServiceData = null;
    private static final String TAG = BLEService.class.getSimpleName();
    double previousWeightNum = 0;
    public boolean dialog_flag;
    private boolean firstConnect = true;

    public BLEService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        isServiceBinded = true;
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        Log.d(TAG,"Service Unbindind");
        super.onUnbind(intent);
        isServiceBinded = false;
        Intent i = new Intent("android.action.service").putExtra("unbind","service Unbinded");
        sendBroadcast(i);
        return true;
    }

    @Override
    public void onDestroy()
    {
        WAKELOCK.release();
        WAKELOCK = null;
        show_notification("Service is destroyed",0);
        Log.d(TAG,"Service Destroyed");
        super.onDestroy();
        mState = ServiceState.IDLE;
        Intent intent = new Intent("android.action.destroyed").putExtra("destroy", "ServiceDestroyed");
        sendBroadcast(intent);
        unregisterReceiver(mBluetoothStateReceiver);
    }


    @Override
    public void onCreate()
    {
        super.onCreate();
        mBluetoothManager = (BluetoothManager)  getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mBatteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        mState = ServiceState.IDLE;
        Log.d(TAG,"Service Created");

    }

    @Override
    public int onStartCommand(final Intent intent , int flags, int a) {
        IntentFilter filter = new IntentFilter();
        Log.d(TAG,"Service Started");
       // filter.addAction(mBluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
        filter.addAction("android.action.scan");
        filter.addAction("android.location.PROVIDERS_CHANGED");
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mBluetoothStateReceiver = new BluetoothStateReceiver();
        registerReceiver(mBluetoothStateReceiver,filter);
        // if wakelock is already acquired then release it first and then acquire it again.
        if (WAKELOCK != null) {
            WAKELOCK.release();
            WAKELOCK = null;
            Log.d(TAG,"wakelock released");

        } if (WAKELOCK == null) {
            WAKELOCK = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            WAKELOCK.acquire();
            Log.d(TAG,"wakelock acquired");

        }if (mState == ServiceState.SCANNING && mBluetoothAdapter.isEnabled()) {
            // If the state is already scanning then restart the scan and reset the alarm
            startscan(false);
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Do scanning after 20 sec = 20000ms
                    startscan(true);
                }
            }, 20000);

        } else if (mState == ServiceState.IDLE && mBluetoothAdapter.isEnabled()) {
            // if the state is idle, then start scanning
            startscan(true);

        }
        // return START_STICKY so that the service is restarted automatically by the OS when killed
        return START_STICKY;
    }
    /* In most devices we have seen that scan stops to receive callbacks in doze mode even with wakelock acquired.
    Hence in order to make sure that the scan keeps running in the background, we restart the scan every 30mins using
    a alarm manager. For Android OS above 23, we use setExactAndAllowWhileIdle function to make sure alarm is
    triggered even in doze mode. The pending intent created is called android.action.scan which triggers the scan.
      */
    public void startAlarm(boolean enable)
    {
        Intent intent = new Intent("android.action.scan");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (enable) {
            if (Build.VERSION.SDK_INT >= 23) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + AlarmManager.INTERVAL_HALF_HOUR , pendingIntent);
                Log.d(TAG,"Alarm restarted after " + String.valueOf(System.currentTimeMillis() + AlarmManager.INTERVAL_HALF_HOUR));
            } else
                manager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), AlarmManager.INTERVAL_HALF_HOUR, pendingIntent);
        } else
            manager.cancel(pendingIntent);

    }
    /* This function first applies Hardware filter on the scanning results using either device name or address to conserve battery.
    Scan mode is also set to low power mode to conserve battery. Finally start scan function takes the scan settings,
    scan filter and scan callback as the arguments
    Scan Low Power Mode - Scan Interval - 500ms
    Scan balanced Mode - Scan Interval - 2000ms
    Scan Low Latency Mode - Scan Interval - 5000ms
    Scan restart time - 2000ms
     */
    public void startscan(boolean enable)
    {
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mScanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
        mBuilderScanFilter  = new ScanFilter.Builder();
        mScanFilter = mBuilderScanFilter.build();
        mScanFilterList = new ArrayList<>();
        mScanFilterList.add(mScanFilter);
        if (enable) {
            mBluetoothLeScanner.startScan(mScanFilterList, mScanSettings, mScanCallback);
            Log.d(TAG,"Started Scanning");
            mState = ServiceState.SCANNING;
            startAlarm(true);


        } else {
            mBluetoothLeScanner.stopScan(mScanCallback);
            mState = ServiceState.IDLE;
            Log.d(TAG,"Stopped Scanning");
            startAlarm(false);

        }
    }
    /*
     The peripheral device sends 31 bytes in the advertising packet. We can also request the device
     to send an additional 31 bytes of scan response data which in most cases is not implemented by
     the peripheral device (Is optional). Since this flow happens in a fraction of second, the scan callbcak
     gives us either a 31 byte or a 62 byte array. Hence we need to parse it accordingly.
    */
    ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            ScanRecord mScanRecord = result.getScanRecord();
            mRssi = result.getRssi();
            BluetoothDevice device = result.getDevice();
            mDeviceAddress = device.getAddress();
            if (device.getName() != null) {
                if (device.getName().contains("Jumper-medical_Scale")) {    //  MI_SCALE, Electronic Scale
                    Log.d(TAG, "rssi is" + mRssi);
                    Log.d(TAG,"ScanRecord is" + mScanRecord);
                    parse_advertising_data(mScanRecord.getBytes(), mDeviceAddress);
                }
            }
        }

        @Override
        public void onScanFailed(int err_code)
        {
            super.onScanFailed(err_code);
            Log.d(TAG,"Scan failed with err_code" + err_code);
        }
    };

    /*
    This function parses the advertised data from the weighing scale.
     */
    public void parse_advertising_data(byte[] bytes , String mDeviceAddress)
    {
        mAdvertisingDataParser = AdvertisingDataParser.parser(bytes, mDeviceAddress);

       /* if (mAdvertisingDataParser.mParsingComplete && mServiceData != null) {
            for (byte x : mManufacturerSpecificData) {
                String data = String.valueOf(String.format("%02X ", Byte.valueOf(x)).trim());
                Log.d(TAG, data);
            }
        }*/

        // MI Scale
        /*    mServiceData = mAdvertisingDataParser.getmServiceData().clone();
        if (mAdvertisingDataParser.mParsingComplete && mServiceData != null) {
            for(byte x:mServiceData){
                String data = String.valueOf(String.format("%02X ", Byte.valueOf(x)).trim());
                Log.d(TAG,data);
            }
            String status_flag = String.valueOf(String.format("%02X ", Byte.valueOf(mServiceData[0])).trim());
            int status = (Integer.valueOf(status_flag, 16));
            String binary = Integer.toBinaryString(status);
            Log.d(TAG,binary);
            String kgNum = String.valueOf(String.format("%02X ", Byte.valueOf(mServiceData[2])).trim()) + String.format("%02X ", Byte.valueOf(mServiceData[1])).trim();
            double weightNum = (Integer.valueOf(kgNum, 16));
            double weight = (weightNum/100);
            if (weight != previousWeightNum && weight!=0)
                show_notification("Your weight int lbs is" + weight);
            previousWeightNum = weight; */


            // Jumper Code below
        if (mAdvertisingDataParser.mParsingComplete) {
            if (mAdvertisingDataParser.getmManufacturerSpecificData() != null) {
                mManufacturerSpecificData = mAdvertisingDataParser.getmManufacturerSpecificData().clone();

                for (byte x : mManufacturerSpecificData) {
                    String data = String.valueOf(String.format("%02X ", Byte.valueOf(x)).trim());
                    Log.d(TAG, data);
                }
                String low = String.valueOf(String.format("%02X ", Byte.valueOf(mManufacturerSpecificData[1])).trim());
                double low_battery = (Integer.valueOf(low, 16));
                String kgNum = String.valueOf(String.format("%02X ", Byte.valueOf(mManufacturerSpecificData[4])).trim()) + String.format("%02X ", Byte.valueOf(mManufacturerSpecificData[3])).trim();
                double weightNum = (Integer.valueOf(kgNum, 16));
                double weight = (weightNum / 10) * 2.2046228;
                weight = Math.round(weight * 10.0) / 10.0;
                if (weight == 0) {
                    show_notification("Low battery",0);
                }
                else if (weight < 6.61 ) {
                    show_notification("Weight is not captured/Underweight",0);
                }
                else if (weight != previousWeightNum && weight != 0) {
                    if (!dialog_flag) {
                        show_dialogbox("Your Weight is", weight);
                        dialog_flag = true;
                    }
                    else
                    {
                        text.setText("Your weight is" + " " + weight);
                    }
                }

                    //show_notification("Your weight int lbs is" + weight,0);
                previousWeightNum = weight;

            }
        }

    }

    public void show_dialogbox(String data, double weight)
    {

        LayoutInflater layoutInflater = LayoutInflater.from(getApplicationContext());
        View promptview = layoutInflater.inflate(R.layout.show_dialog, null);
        AlertDialog.Builder alertdialogbuilder = new AlertDialog.Builder(this);
        alertdialogbuilder.setView(promptview);
        text = (TextView) promptview.findViewById(R.id.Text);

        text.setText(data + " " + weight);
        alertdialogbuilder.setCancelable(false).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Intent intent = new Intent(getApplicationContext(), MainActivity.class); // A new intent to launch another activity
                    intent.putExtra("fromNotification", true);
                    PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), (int) System.currentTimeMillis(), intent, 0);
                    try {
                        pendingIntent.send(getApplicationContext(),0,intent);
                    } catch (PendingIntent.CanceledException e) {
                        e.printStackTrace();
                    }
                    dialog_flag = false;
                }
        }).setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialog_flag = false;

                }
            });
            AlertDialog alert = alertdialogbuilder.create();
            alert.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            alert.show();


    }

    /* getActivity has the following parameters
           First - Context from which it is called
           Second - RequestCode for the sender. Using it later will give us the same pending intent
           Third - Intent object created before
           Fourth - FLAG- This one is used to state if a previous pendingIntent already exists or not. If it does then
           update it with the new intent. FLAG_UPDATE_CURRENT can be used for that.
            */
    public void show_notification(String text,int state)
    {
        PendingIntent pendingIntent = null;
        if (state == 0) {
            Intent intent = new Intent(getApplicationContext(), MainActivity.class); // A new intent to launch another activity
            intent.putExtra("fromNotification", true);
            pendingIntent = PendingIntent.getActivity(getApplicationContext(), (int) System.currentTimeMillis(), intent, 0);
        } else if (state == 1) {
            Intent myIntent = new Intent( Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            pendingIntent = PendingIntent.getActivity(getApplicationContext(), (int) System.currentTimeMillis(), myIntent, 0);
            //context.startActivity(myIntent);
        }
        // Notification is created using the notification manager.
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // This provides a builder interface to create a Notification object
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
        // Setting this flag as true enables auto deletion of notification on clicking
        mBuilder.setAutoCancel(true);
        mBuilder.setContentText(text);
        mBuilder.setContentTitle("Habits Weighing Scale");
        mBuilder.setContentIntent(pendingIntent);
        mBuilder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
        mBuilder.setNumber(7);
        mBuilder.setSmallIcon(R.drawable.appleicon);
        Notification notification = mBuilder.build();
        notificationManager.notify(NOTIFY_ME_ID,notification);
    }

    public class BluetoothServiceBinder extends Binder {

        // This is the first function which will be called to register the messenger object
        public void registerActivityMessenger(Messenger messenger) {
            mActivityMessenger = messenger;
        }

        public void StartBLEScan()
        {
            if (mBluetoothAdapter.isEnabled()) {
                startscan(false);
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Do scanning after 20 = 20000ms
                        startscan(true);
                    }
                }, 10000);
            }
        }

        // Can use this function to pass the device name to the Main Activity
        public String getDeviceName()
        {
            if (mAdvertisingDataParser == null)
            {
                return "Not found";
            }
            else if (mAdvertisingDataParser.getDeviceName() == null)
                return "Not Found";
            return mAdvertisingDataParser.getDeviceName();

        }

        // This function returns the State of the Bluetooth Service which can be scanning or idle
        public ServiceState getState() {
            return mState;
        }

        // This function can be used to change the scan settings based on whether the app is in foreground or background.
        public void scansettings( int scan_mode)
        {
            // Create a bluetooth scanner object and set the mode of scanning. This can be changed based on whether the app is running in background or not.

            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
            mBuilderScanSettings = new ScanSettings.Builder();
            switch(scan_mode) {
                case 0:
                    mBuilderScanSettings.setScanMode(mScanSettings.SCAN_MODE_LOW_LATENCY);
                    break;
                case 1:
                    mBuilderScanSettings.setScanMode(mScanSettings.SCAN_MODE_BALANCED);
                    break;
                case 2:
                    mBuilderScanSettings.setScanMode(mScanSettings.SCAN_MODE_LOW_POWER);
                    break;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                //mBuilderScanSettings.setMatchMode(mScanSettings.MATCH_MODE_STICKY);
            }
            mScanSettings = mBuilderScanSettings.build();
        }


        // UpdateUI function will be called to update the UI on the Main Activity
        public void updateUI( String key, Object data)
        {
            if (mActivityMessenger != null) {
                try {
                    mActivityMessenger.send(Message.obtain(null, MainActivity.MSG_UPDATE_UI, Pair.create(key,data)));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void transistionState(ServiceState tostate)
    {
        mState = tostate;

        if (mActivityMessenger != null) {
            try {
                mActivityMessenger.send(Message.obtain(null, MainActivity.MSG_NOTIFY_STATE_CHANGE, tostate));
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
    public class BluetoothStateReceiver extends BroadcastReceiver
    {

        @Override
        public void onReceive(Context context, Intent intent)
        {


            final String action = intent.getAction();
            Log.d(TAG,action);

            if (intent.getAction().matches("android.action.scan")) {
                // This intent is called from the alarm manager. We use this to restart the BLE scan.
                if (mBluetoothAdapter.isEnabled()) {
                    startscan(false);
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // Do scanning after 20 sec = 20000ms
                            startscan(true);
                        }
                    }, 20000);

                    if (Build.VERSION.SDK_INT >= 23) {
                        startAlarm(true);
                    }
                }
            }

            if (intent.getAction().matches("android.location.PROVIDERS_CHANGED")) {

                Log.d(TAG,"Location Providers Changed");
                if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                    Log.d(TAG,"GPS is enabled");
                    if (mBluetoothAdapter.isEnabled())
                        startscan(true);
                } else {
                    Log.d(TAG,"GPS is disabled");
                    if (mBluetoothAdapter.isEnabled())
                        startscan(false);
                    show_notification("GPS is disabled ",1);
                }
            }

            if (action.equals("android.bluetooth.adapter.action.STATE_CHANGED")) {

                if (firstConnect == true) {
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    switch (state) {
                        case BluetoothAdapter.STATE_OFF:

                            show_notification("BLE is turned off", 0);
                            firstConnect = false;
                            if (isServiceBinded) {
                                if (mActivityMessenger != null) {
                                    try {
                                        mActivityMessenger.send(Message.obtain(null, MainActivity.MSG_NOTIFY_STATE_CHANGE, "BLE turned OFF"));
                                    } catch (RemoteException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }

                            break;
                        case BluetoothAdapter.STATE_ON:
                            if (isServiceBinded) {
                                if (mActivityMessenger != null) {
                                    try {
                                        mActivityMessenger.send(Message.obtain(null, MainActivity.MSG_NOTIFY_STATE_CHANGE, "Start scanning"));
                                    } catch (RemoteException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                            startscan(true);
                            firstConnect = false;
                            break;
                    }
                }
                else
                {
                    firstConnect = false;
                }

            }

        }
    }

}



