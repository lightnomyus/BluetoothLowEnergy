package com.example.soumil.automation;

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
import android.os.Binder;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static java.lang.Thread.sleep;

public class BLEService<E extends BleCallbacks> extends Service  {

    public enum BLEState {IDLE,SCANNING, CONNECTED, DISCONNECTED};
    public BLEState mBLEState;
    private final IBinder mBinder = new BLEServiceBinder();
    private Messenger mMessenger;
    private AdvertisingDataParser mAdvertisingDataParser;
    public static boolean isServiceBinded;
    public static final String TAG = BLEService.class.getName();
    private String deviceAddress;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothManager mBluetoothManager;
    private BluetoothLeScanner mBluetoothLeScanner;
    private ScanSettings mScanSettings;
    private List<ScanFilter> mScanListFiler;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mBluetoothGattReadCharacteristic;
    private BluetoothGattCharacteristic mBluetoothGattWriteCharacteristic;
    private static String CLIENT_CHARACTERISTIC_CONFIGUARION_DESCRIPTOR = "00002902-0000-1000-8000-00805f9b34fb";
    private static String JANACARE_DATA_SERVICE = "82fcabcd-8ebd-4d31-b978-fc7bacab2bfe";
    private static String READ_CHARACTERISTIC = "82fcabce-8ebd-4d31-b978-fc7bacab2bfe";
    private static String WRITE_CHARACTERISTIC = "82fcabcf-8ebd-4d31-b978-fc7bacab2bfe";
    private static UUID READ = UUID.fromString(READ_CHARACTERISTIC);
    private static UUID WRITE = UUID.fromString(WRITE_CHARACTERISTIC);
    private static UUID SERVICE = UUID.fromString(JANACARE_DATA_SERVICE);
    private static UUID CCCD = UUID.fromString(CLIENT_CHARACTERISTIC_CONFIGUARION_DESCRIPTOR);
    private BluetoothStateReceiver mBluetoothStateReceiver;
    private int mManufacturerID = 0;
    BluetoothGattService mService;
    int messageType;
    int currentTask;
    int taskStatus;
    int deviceStatus;
    int standbymode;
    int mPreviousTask;
    int batteryLevels;
    private int mStandbyStatus = 0;
    protected E mCallbacks;
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
        mBLEState = BLEState.SCANNING;
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
        if (mBluetoothLeScanner != null)
            mBluetoothLeScanner.stopScan(mScanCallback);
        Log.d(TAG,"Stopped Scanning");

    }

    public void parseAdvertisingData(byte[] data){
        mAdvertisingDataParser = AdvertisingDataParser.parser(data,deviceAddress);
        if (mAdvertisingDataParser.mParsingComplete) {
            mManufacturerID = mAdvertisingDataParser.getManufacturerID();

            if (mManufacturerID == 1036) {
                if (mMessenger != null) {
                        try {
                            mMessenger.send(Message.obtain(null, MainActivity.FOUND_DEVICE, null));
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
    }

    ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            ScanRecord mScanRecord = result.getScanRecord();
            int rssi = result.getRssi();
            BluetoothDevice device = result.getDevice();
            deviceAddress = device.getAddress();
            Log.d(TAG,"RSSI is" +rssi);
            parseAdvertisingData(mScanRecord.getBytes());
//            mCallbacks.onStartedScanning();
            if(mManufacturerID == 1036 && mBLEState == BLEState.CONNECTED){
                Log.d(TAG,"Device found");
                connectDevice(deviceAddress);

            }
        }

        @Override
        public void onScanFailed(int err_code) {
            super.onScanFailed(err_code);
            Log.d(TAG,"Scan Failed");
        }
    };

    private boolean connectDevice(String deviceAddress)
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
        stopScan();
        Log.d(TAG,"Calling connect method");
        mBluetoothGatt = device.connectGatt(this, false, mBluetoothGattCallback);
        return true;
    }

    public void enablenotifications() {

        mBluetoothGatt.setCharacteristicNotification(mBluetoothGattReadCharacteristic,true);
        BluetoothGattDescriptor descriptor = mBluetoothGattReadCharacteristic.getDescriptor(CCCD);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
    }

    public void getSupportedServices() {
        BluetoothGattService mService = mBluetoothGatt.getService(SERVICE);
        mBluetoothGattReadCharacteristic = mService.getCharacteristic(READ);
        mBluetoothGattWriteCharacteristic = mService.getCharacteristic(WRITE);
        enablenotifications();
    }

    public void sendData(int type, Object data) {
        if (mMessenger != null)
            try {
                mMessenger.send(Message.obtain(null, type, data));
            } catch (RemoteException e) {
                e.printStackTrace();
            }
    }

    public void getResult(byte[] data) throws UnsupportedEncodingException {

        messageType = (data[0]& 0xFF);
        currentTask = (data[1] & 0xFF);
        taskStatus = (data[2] & 0xFF);
        deviceStatus = (data[3] & 0xFF);
        standbymode = (data[4] & 0xFF);

        batteryLevels = ((data[6] & 0xFF) << 8)  | (data[5] & 0xFF);

        Log.d(TAG,"Current Task is " +currentTask);
        Log.d(TAG,"Task status is " +taskStatus);
        Log.d(TAG,"device Status is" + deviceStatus);
        Log.d(TAG,"Standby mode"+standbymode);
        Log.d(TAG,"battery levels are " +batteryLevels);
        if (standbymode == 1) {
            sendData(MainActivity.NOTIFY_ERROR,standbymode);
        }
        if (standbymode == 0) {
            sendData(MainActivity.NOTIFY_CURRENT_TASK_RUNNING,currentTask);
            sendData(MainActivity.BATTERY_STATE,batteryLevels);
        }

        if (currentTask > 3 & taskStatus == 1)
        {
            if (currentTask != mPreviousTask) {
                sendData(MainActivity.NOTIFY_DATA_CHANGE,null);
                mPreviousTask = currentTask;
            }
        }

        if (currentTask == 13) {
            sendData(MainActivity.NOTIFY_TASK_COMPETED,null);
        }

    }
    public void writeCharacteristic(byte[] data)
    {
        mBluetoothGattWriteCharacteristic.setValue(data);
        boolean status = mBluetoothGatt.writeCharacteristic(mBluetoothGattWriteCharacteristic);
        Log.d(TAG," "+status);
    }


    private BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            switch (newState)
            {
                case BluetoothProfile.STATE_CONNECTED:
                    sendData(MainActivity.NOTIFY_CONNECTION,null);
                    mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                    try {
                        sleep(600);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    mBluetoothGatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    sendData(MainActivity.NOTIFY_DISCONNECTION,null);
                    Log.d(TAG, "Disconnected from GATT server.");
                    try {
                        sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    mBluetoothGatt.close();
                    startScan();
                    break;

            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status)
        {
            getSupportedServices();
        }
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic gattCharacteristic, int status) {

        }
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic gattCharacteristic, int status) {
            Log.d(TAG,"Characteristic is written");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic gattCharacteristic) {
            final byte value[] = gattCharacteristic.getValue();
            try {
                getResult(value);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor gattDescriptor, int status) {
            Log.d(TAG,"Descriptor is written");
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

        public void goToLoadingPosition()
        {
            byte[] data;
            data = new byte[] {0x03};
            if (mBluetoothGatt != null)
                writeCharacteristic(data);

        }

        public void cancelTest() {
            byte[] data;
            data = new byte[] {0x06};
            if (mBluetoothGatt != null)
                writeCharacteristic(data);

        }

        public void endTest() {
            byte[] data;
            data = new byte[] {0x07};
            if (mBluetoothGatt != null)
                writeCharacteristic(data);
            mBLEState = BLEState.DISCONNECTED;

        }

        public void startTest() {
            byte[] data;
            data = new byte[] {0x05};
            if (mBluetoothGatt != null)
                writeCharacteristic(data);
        }

        public void connect() {
            mBLEState = BLEState.CONNECTED;

        }

    }





}
