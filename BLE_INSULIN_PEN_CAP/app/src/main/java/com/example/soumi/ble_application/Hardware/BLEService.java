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
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.util.Pair;
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


    public enum ServiceState {
        SCANNING, CONNECTING, CONNECTED, BONDED, DISCONNECTED, IDLE
    }
    public ServiceState mState;
    public static boolean isServiceBinded;
    private static final int NOTIFY_ME_ID = 1337;
    public BluetoothManager mBluetoothManager;
    public BluetoothLeScanner mBluetoothLeScanner;
    public BluetoothAdapter mBluetoothAdapter;
    public BluetoothGatt mBluetoothGatt;
    public ScanSettings.Builder mBuilderScanSettings;
    public ScanSettings mScanSettings;
    public ScanFilter mScanFilter;
    public ScanFilter.Builder mBuilderScanFilter;
    List<ScanFilter> mScanFilterList;
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static String BLE_SERVICE = "82fcabcd-8ebd-4d31-b978-fc7bacebebde";
    public static String TX_CHARACTERISTIC = "82fcabce-8ebd-4d31-b978-fc7bacebebde";
    public static UUID READ = UUID.fromString(TX_CHARACTERISTIC);
    public static UUID SERVICE = UUID.fromString(BLE_SERVICE);
    public static UUID CCCD = UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG);
    BluetoothGattCharacteristic mCharacteristic;
    private String mBluetoothDeviceAddress;
    private final IBinder mBinder = new BluetoothServiceBinder();
    AdvertisingDataParser advertisingDataParser;
    byte[] data;
    private static final String TAG = BLEService.class.getSimpleName();   // TAG to be used while logging
    public static PowerManager.WakeLock WAKELOCK = null;
    private Messenger mActivityMessenger ;
    BluetoothStateReceiver mBluetoothStateReceiver;
    LocationManager locationManager;
    PowerManager powerManager;
    String deviceAddress;
    BatteryManager mBatteryManager;
    int mID = 0;
    int rssi;
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
        show_notification("Service is destroyed");
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
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        mState = ServiceState.IDLE;
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mBatteryManager =
                (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        Log.d(TAG,"Service Created");

    }

    @Override
    public int onStartCommand(final Intent intent , int flags, int a) {
        // Here we first register for the Bluetooth Global Intents that would tell us if the bluetooth is turned off by the user and also if the bond state is changed.
        IntentFilter filter = new IntentFilter();
        Log.d(TAG,"Service Started");
        filter.addAction(mBluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction("android.action.scan");
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mBluetoothStateReceiver = new BluetoothStateReceiver();
        registerReceiver(mBluetoothStateReceiver,filter);


        if (WAKELOCK != null)
        {
            WAKELOCK.release();
            WAKELOCK = null;
            Log.d(TAG,"wakelock released");

        }
        if (WAKELOCK == null)
        {
            WAKELOCK = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            WAKELOCK.acquire();
            Log.d(TAG,"wakelock acquired");
        }

        if (mState == ServiceState.CONNECTED) {
            startscan(false);
            startAlarm(false);
        }
        else if (mState == ServiceState.SCANNING)
        {
            startscan(false);
            startAlarm(true);
            startscan(true);
        }
        else if (mState == ServiceState.IDLE || mState == ServiceState.DISCONNECTED)
        {
            startscan(true);
            startAlarm(true);

        }
        return START_STICKY;
    }
    public void startAlarm(boolean enable)
    {
        Intent intent = new Intent("android.action.scan");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (enable) {
            if (Build.VERSION.SDK_INT >= 23) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + AlarmManager.INTERVAL_HALF_HOUR , pendingIntent);
                Log.d(TAG,"Alarm restarted after " + String.valueOf(System.currentTimeMillis() + AlarmManager.INTERVAL_HALF_HOUR));
            }
            else {
                manager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), AlarmManager.INTERVAL_FIFTEEN_MINUTES/5, pendingIntent);
            }
        } else {
            manager.cancel(pendingIntent);
        }
    }
    public void startscan(boolean enable)
    {
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mScanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();
        mScanFilterList = new ArrayList<>();
        //mScanFilterList.add(mScanfilter);
        if (enable) {

            mBluetoothLeScanner.startScan(mScanFilterList,mScanSettings,mScanCallback);
            Log.d(TAG,"Started Scanning");
            mState = ServiceState.SCANNING;
        } else {
            mBluetoothLeScanner.stopScan(mScanCallback);
            mState = ServiceState.IDLE;
            Log.d(TAG,"Stopped Scanning");
        }
    }
    ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            ScanRecord mScanRecord = result.getScanRecord();
            rssi = result.getRssi();
            Log.d(TAG,"Scan Record is "+mScanRecord);
            Log.d(TAG,"rssi is" + rssi);
           if (mScanRecord.getBytes() != null && result.getDevice().getAddress().equals("F5:84:A7:B8:1A:7B") || result.getDevice().getAddress().equals("FB:23:AF:42:5C:56"))
            {
                Log.d(TAG,mScanRecord.toString());
                parse_data(mScanRecord.getBytes());
                BluetoothDevice device = result.getDevice();
                deviceAddress = device.getAddress();
            }
            BluetoothDevice device = result.getDevice();

            String device_name = device.getName();


        }
        @Override
        public void onScanFailed(int err_code)
        {
            super.onScanFailed(err_code);
        }
    };
    public void parse_data(byte[] bytes)
    {
        advertisingDataParser = AdvertisingDataParser.parser(bytes, deviceAddress);

         if (advertisingDataParser.mParsingComplete) {
              mID = advertisingDataParser.getManufacturerID();
            }
         if (mID == 1035) {
                connectToDevice(deviceAddress);
            }
    }

    private boolean connectToDevice(String deviceAddress)
    {
        if (mBluetoothAdapter == null || deviceAddress == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(deviceAddress);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        startscan(false);
        mBluetoothGatt = device.connectGatt(this, false, mBluetoothGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = deviceAddress;
        mState = ServiceState.CONNECTING;
        //
        transistionState(mState);
        return true;
    }


    public void enablenotifications()
    {
        BluetoothGattService mService = mBluetoothGatt.getService(SERVICE);
        BluetoothGattCharacteristic characteristic = mService.getCharacteristic(READ);

        mBluetoothGatt.setCharacteristicNotification(characteristic,true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);

    }

    public void getSupportedServices() {


        BluetoothGattService mService = mBluetoothGatt.getService(SERVICE);
        mCharacteristic = mService.getCharacteristic(READ);
        enablenotifications();
    }

    public void writeCharacteristic(byte[] data)
    {
       // BluetoothGattService mService = mBluetoothGatt.getService(SERVICE);
        //BluetoothGattCharacteristic mCharacteristic = mService.getCharacteristic(WRITE);
        mCharacteristic.setValue(data);
        boolean status = mBluetoothGatt.writeCharacteristic(mCharacteristic);
        Log.d(TAG," "+status);
    }

    public void write_timestamp()
    {
        int unixTime = (int) (System.currentTimeMillis() / 1000);
        Log.d(TAG,String.valueOf(unixTime));
        byte header = 0;
        data = new byte[]{
                header,
                (byte) (unixTime >> 24),
                (byte) (unixTime >> 16),
                (byte) (unixTime >> 8),
                (byte) (unixTime)
        };
        try {
            sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        writeCharacteristic(data);


    }
    public void write_name()
    {
        try {
            sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        writeCharacteristic(data);

    }
    public void getResult(byte b[]) throws UnsupportedEncodingException {

        int readings_count = b[b.length-1];
        int next_packet = b[b.length - 2];
        int seconds;
        long time;
        Log.d(TAG,"readings_count is" + String.valueOf(readings_count));

        if (next_packet == 0) {
            long time_array[] = new long[readings_count + 1];
            long seconds_array[] = new long[readings_count + 1];
            for (int i = 0 , j = 0; i< b.length-2;j++)
            {
                time_array[j] = ((b[i] & 0xFF) << 24) + ((b[i + 1]& 0xFF) << 16) + ((b[i + 2]& 0xFF) << 8) + (b[i + 3] & 0xFF);
                seconds_array[j] = ((b[i+4]& 0xFF) << 8) + (b[i+5]& 0xFF);
                if (readings_count == j)
                {
                    break;
                }
                i += 6;
            }
            time = time_array[0];
            Log.d(TAG, "Timestamp Received is " + String.valueOf(time));
            if (seconds_array[0] > 2)
            {
                Date date = new Date(time*1000L); // *1000 is to convert seconds to milliseconds
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss"); // the format of your date
                sdf.setTimeZone(TimeZone.getDefault()); // give a timezone reference for formating (see comment at the bottom
                String formattedDate = sdf.format(date);
                System.out.println(formattedDate);
                show_notification("You have used insulin pen at " + formattedDate);
            }
            else
            {
                show_notification("It seems like you used the insulin pen but not properly");
            }

            Log.d(TAG, "Number of seconds is " + String.valueOf(seconds_array[0]));
            write_timestamp();
        }
        else if (next_packet == 1)
        {
            long time_array[] = new long[readings_count + 1];
            long seconds_array[] = new long[readings_count + 1];
            for (int i = 0 , j = 0; i< (b.length-2);j++)
            {
                time_array[j] = ((b[i] & 0xFF) << 24) + ((b[i + 1]& 0xFF) << 16) + ((b[i + 2]& 0xFF) << 8) + (b[i + 3] & 0xFF);
                seconds_array[j] = ((b[i+4]& 0xFF) << 8) + (b[i+5]& 0xFF);
                i += 6;
            }
            Log.d(TAG,"1st time received is" + String.valueOf(time_array[0]));
            Log.d(TAG,"2nd time received is" + String.valueOf(time_array[1]));
            Log.d(TAG,"3rd time received is " + String.valueOf(time_array[2]));

        }
        /*if (mActivityMessenger != null)
        {
            try {
                mActivityMessenger.send(Message.obtain(null, MainActivity.MSG_UPDATE_UI, result));
            } catch (RemoteException e) {
                e.printStackTrace();
            }

        }*/


    }
    public void show_notification(String text)
    {
        Intent intent = new Intent(getApplicationContext(),MainActivity.class); // A new intent to launch another activity
        /* getActivity has the following parameters
        First - Context from which it is called
        Second - RequestCode for the sender. Using it later will give us the same pending intent
        Third - Intent object created before
        Fourth - FLAG- This one is used to state if a previous pendingIntent already exists or not. If it does then
        update it with the new intent. FLAG_UPDATE_CURRENT can be used for that.
         */
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), (int)System.currentTimeMillis(),intent,0 );

        // Notification is created using the notification manager.
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // This provides a builder interface to create a Notification object
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
        // Setting this flag as true enables auto deletion of notification on clicking
        mBuilder.setAutoCancel(true);
        mBuilder.setContentText(text);
        mBuilder.setContentTitle("Insulin Pen Cap");
        mBuilder.setContentIntent(pendingIntent);
        mBuilder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
        mBuilder.setNumber(7);
        mBuilder.setSmallIcon(R.drawable.appleicon);
        Notification notification = mBuilder.build();
        notificationManager.notify(NOTIFY_ME_ID,notification);
    }
    private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            switch (newState)
            {
                case BluetoothProfile.STATE_CONNECTED:
                    mBluetoothLeScanner.stopScan(mScanCallback);
                    startAlarm(false);
                    mState = ServiceState.CONNECTED;
                    mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                    try {
                        sleep(600);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    //show_notification("Device is connected");
                    mBluetoothGatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:

                   // show_notification("Device is disconnected");
                    mState = ServiceState.DISCONNECTED;

                    Log.d(TAG, "Disconnected from GATT server.");
                    //mBluetoothGatt = null;
                    //
                    try {
                        sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    mBluetoothGatt.close();
                    try {
                        sleep(15000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    startscan(true);
                    startAlarm(true);
                    break;
            }
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status)
        {
            getSupportedServices();

        }
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
        {
            // Not used
        }
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
        {

            Log.d(TAG,"Characteristic Written");
            //    mBluetoothGatt.disconnect();


        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)
        {
           final byte value[] = characteristic.getValue();
            try {
                getResult(value);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status)
        {
            Log.d(TAG,"Descriptor Written");
        }

    };
    public class BluetoothServiceBinder extends Binder {

        // This is the first function which will be called to register the messenger object
        public void registerActivityMessenger(Messenger messenger) {
            mActivityMessenger = messenger;
        }
        public void disconnectDevice()
        {
            if (mBluetoothGatt == null)
            {
                Log.d(TAG,"Gatt not initialised");
            }
            else {
                mBluetoothGatt.disconnect();
            }
           // mBluetoothGatt.close();

        }
        public void StartBLEScan()
        {
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Do scanning after 10s = 10000ms
                    startscan(true);
                }
            }, 10000);


        }
        public void WriteData(byte[] value) throws UnsupportedEncodingException {


                data = Arrays.copyOf(value, value.length);

        }

        public int getManufacturerID()
        {
            if (advertisingDataParser == null)
            {
                return 100;
            }
            else if (advertisingDataParser.getManufacturerID() == 0)
                return 100;
            return advertisingDataParser.getManufacturerID();
        }
        public String getDeviceName()
        {
            if (advertisingDataParser == null)
            {
                return "Not found";
            }
            else if (advertisingDataParser.getDeviceName() == null)
                return "Not Found";
            return advertisingDataParser.getDeviceName();

        }
        public int getBatteryLevel()
        {
            if (advertisingDataParser == null)
            {
                return 100;
            }
            else if (advertisingDataParser.mBatteryValue == 0)
                return 100;
            return advertisingDataParser.mBatteryValue;
        }
        public String getDeviceAddress()
        {
            if (advertisingDataParser == null)
            {
                return "Not found";
            }
            else if (advertisingDataParser.getDeviceAddress() == null)
                return "Not Found";
            return advertisingDataParser.getDeviceAddress();
        }
        public int getTemperatureLevel()
        {
            if (advertisingDataParser == null)
            {
                return 100;
            }
            else if (advertisingDataParser.mTemperatureValue == 0)
                return 100;

            return advertisingDataParser.mTemperatureValue;
        }

        // This function returns the State of the Bluetooth Service which can be connected, bonded, scanning etc
        public ServiceState getState() {
            return mState;
        }

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

        // Scan Filter sets the filters to be used for scanning
        public void scanFilter(String devicename, String deviceaddress)
        {
            mBuilderScanFilter = new ScanFilter.Builder();
            mBuilderScanFilter.setDeviceName(devicename);
            mBuilderScanFilter.setDeviceAddress(deviceaddress);
            mScanFilter = mBuilderScanFilter.build();
            mScanFilterList.add(mScanFilter);
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
        // The scan
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
            if (intent.getAction().matches("android.action.scan"))
            {
                startAlarm(false);
               // WAKELOCK.release();
                startscan(false);
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
               // WAKELOCK.acquire();
                //Log.d(TAG,"wakelock acquired");
                startscan(true);
                if (Build.VERSION.SDK_INT >= 23) {
                    startAlarm(true);
                }

            }

            if (intent.getAction().matches("android.location.PROVIDERS_CHANGED")) {
                Toast.makeText(context, "in android.location.PROVIDERS_CHANGED",
                        Toast.LENGTH_SHORT).show();
                Log.d(TAG,"State Changed");

            }


            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED))
            {

                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch(state) {
                    case BluetoothAdapter.STATE_OFF:
                        if (isServiceBinded) {
                            if (mActivityMessenger != null) {
                                try {
                                    mActivityMessenger.send(Message.obtain(null, MainActivity.MSG_NOTIFY_STATE_CHANGE, "BLE turned OFF"));
                                } catch (RemoteException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        else
                        {
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
                        break;
                }
            }
            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                switch (state) {
                    case BluetoothDevice.BOND_BONDING:
                        break;
                    case BluetoothDevice.BOND_BONDED:
                        mState = ServiceState.BONDED;
                       // transistionState(mState);
                        break;
                }
            }
            if (intent.getAction().equals("android.intent.action.MAIN"))
            {
                String deviceAddress = intent.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                connectToDevice(deviceAddress);

            }
        }
    }

}



