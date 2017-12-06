package com.example.soumil.automation;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
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
import android.os.Binder;
import android.os.IBinder;
import android.os.Messenger;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class BLEService extends Service  {


    public enum BLEState {SCANNING, CONNECTED, DISCONNECTED};
    public BLEState mBLEState;
    private final IBinder mBinder = new BLEServiceBinder();
    private Messenger mMessenger;

    public static boolean isServiceBinded;
    public static final String TAG = BLEService.class.getName();

    BluetoothAdapter mBluetoothAdapter;
    BluetoothManager mBluetoothManager;
    private BluetoothLeScanner mBluetoothLeScanner;
    private ScanSettings mScanSettings;
    private List<ScanFilter> mScanListFiler;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mBluetoothGattCharacteristic;
    private static String CLIENT_CHARACTERISTIC_CONFIGUARION_DESCRIPTOR = "00002902-0000-1000-8000-00805f9b34fb";
    private static String JANACARE_DATA_SERVICE = "82fcabcd-8ebd-4d31-b978-fc7bacebebde";
    private static String READ_CHARACTERISTIC = "82fcabce-8ebd-4d31-b978-fc7bacebebde";
    private static String WRITE_CHARACTERISTIC = "82fcabcf-8ebd-4d31-b978-fc7bacebebde";
    private static UUID READ = UUID.fromString(READ_CHARACTERISTIC);
    private static UUID WRITE = UUID.fromString(WRITE_CHARACTERISTIC);
    private static UUID SERVICE = UUID.fromString(JANACARE_DATA_SERVICE);
    private static UUID CCCD = UUID.fromString(CLIENT_CHARACTERISTIC_CONFIGUARION_DESCRIPTOR);
    private BluetoothStateReceiver mBluetoothStateReceiver;

    public BLEService() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        isServiceBinded = true;
        mBluetoothManager = (BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
        Toast.makeText(getApplicationContext(),"Service is binded",Toast.LENGTH_SHORT).show();
        IntentFilter filter = new IntentFilter();
        Log.d(TAG,"Service Started");
        filter.addAction(mBluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction("android.action.scan");
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mBluetoothStateReceiver = new BluetoothStateReceiver();
        registerReceiver(mBluetoothStateReceiver,filter);

        startScan();

        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        super.onUnbind(intent);
        isServiceBinded = false;
        Toast.makeText(getApplicationContext(),"Service is Unbinded",Toast.LENGTH_SHORT).show();
        unregisterReceiver(mBluetoothStateReceiver);
        Intent intent1 = new Intent("android.action.service").putExtra("unBind","Service Unbinded");
        sendBroadcast(intent1);
        stopScan();

        return true;
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        mBLEState = BLEState.SCANNING;
        mBluetoothManager = (BluetoothManager)  getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

    }

    @Override
    public int onStartCommand(final Intent intent, int id, int flags) {
        super.onStartCommand(intent, id, flags);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

    }

    public void startScan() {
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mScanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        mScanListFiler = new ArrayList<>();
        mBluetoothLeScanner.startScan(mScanListFiler,mScanSettings,mScanCallback);
        Log.d(TAG,"Started Scanning");

    }

    public void stopScan() {
        mBluetoothLeScanner.stopScan(mScanCallback);
        Log.d(TAG,"Stopped Scanning");

    }

    ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            ScanRecord mScanRecord = result.getScanRecord();
            int rssi = result.getRssi();
            Log.d(TAG,"Scan record is " +mScanRecord);
            Log.d(TAG,"RSSI is" +rssi);
        }

        @Override
        public void onScanFailed(int err_code) {
            super.onScanFailed(err_code);
            Log.d(TAG,"Scan Failed");
        }
    };

    public class BluetoothStateReceiver extends BroadcastReceiver
    {


        @Override
        public void onReceive(Context context, Intent intent) {

            final String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                switch(state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG,"Turned off");
                        Toast.makeText(getApplicationContext(),"Bluetooth Turned OFF",Toast.LENGTH_LONG).show();
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG,"Turned ON");
                        Toast.makeText(getApplicationContext(),"Bluetooth Turned ON",Toast.LENGTH_LONG).show();
                        break;
                }

            }
        }
    }

    public class BLEServiceBinder extends Binder {
        public void registerActivityMessenger(Messenger messenger){
            mMessenger = messenger;
        }


        public BLEState getBLEState() {

            return mBLEState;
        }

        public void cancelTest() {

        }

        public void endTest() {

        }

        public void startTest() {

        }
        public void restartTest() {

        }
        public void connect() {

        }

    }





}
