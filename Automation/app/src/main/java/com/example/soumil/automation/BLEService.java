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
import android.os.Build;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
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
    private final static String ERROR_CONNECTION_STATE_CHANGE = "Error on connection state change";
    private final static String ERROR_DISCOVERY_SERVICE = "Error on discovering services";
    private final static String ERROR_AUTH_ERROR_WHILE_BONDED = "Phone has lost bonding information";
    private final static String ERROR_READ_CHARACTERISTIC = "Error on reading characteristic";
    private final static String ERROR_WRITE_CHARACTERISTIC = "Error on writing characteristic";
    private final static String ERROR_READ_DESCRIPTOR = "Error on reading descriptor";
    private final static String ERROR_WRITE_DESCRIPTOR = "Error on writing descriptor";
    private final static String ERROR_MTU_REQUEST = "Error on mtu request";
    private final static String ERROR_CONNECTION_PRIORITY_REQUEST = "Error on connection priority request";
    private int mManufacturerID = 0;
    int messageType;
    int currentTask;
    int taskStatus;
    int deviceStatus;
    int standbymode;
    int mPreviousTask;
    int batteryLevels;
    BluetoothDevice mdevice;
    public BLEService() {

    }

    @Override
    public IBinder onBind(Intent intent) {

        isServiceBinded = true;
        mBluetoothManager = (BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
        Toast.makeText(getApplicationContext(),"Service is binded",Toast.LENGTH_SHORT).show();
        registerReceiver(mBluetoothStateReceiver,new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(mBondingBroadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
        startScan();

        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        super.onUnbind(intent);
        isServiceBinded = false;
        Toast.makeText(getApplicationContext(),"Service is Unbinded",Toast.LENGTH_SHORT).show();
        unregisterReceiver(mBluetoothStateReceiver);
        unregisterReceiver(mBondingBroadcastReceiver);
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
        mBLEState = BLEState.SCANNING;
        Log.d(TAG,"Started Scanning");
    }

    public void stopScan() {
        if (mBluetoothLeScanner != null)
            mBluetoothLeScanner.stopScan(mScanCallback);
        mBLEState = BLEState.IDLE;
        Log.d(TAG,"Stopped Scanning");

    }

    public void parseAdvertisingData(byte[] data){
        mAdvertisingDataParser = AdvertisingDataParser.parser(data,deviceAddress);
        if (mAdvertisingDataParser.mParsingComplete) {
            mManufacturerID = mAdvertisingDataParser.getManufacturerID();

            if (mManufacturerID == 1036) {
                if (mMessenger != null) {
                        try {
                            mMessenger.send(Message.obtain(null, MainActivity.NOTIFY_FOUND_DEVICE, null));
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
        mdevice = device;
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
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null)
            return;
        final int properties = mBluetoothGattReadCharacteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0)
            return;
        mBluetoothGatt.setCharacteristicNotification(mBluetoothGattReadCharacteristic,true);
        final BluetoothGattDescriptor descriptor = mBluetoothGattReadCharacteristic.getDescriptor(CCCD);
        if (descriptor != null)
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
    }

    public void getSupportedServices() {
        BluetoothGattService mService = mBluetoothGatt.getService(SERVICE);
        mBluetoothGattReadCharacteristic = mService.getCharacteristic(READ);
        mBluetoothGattWriteCharacteristic = mService.getCharacteristic(WRITE);
        sendData(MainActivity.NOTIFY_DEVICE_READY,null);
      //  performBonding();
      //  enablenotifications();
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
            sendData(MainActivity.NOTIFY_BATTERY_STATE,batteryLevels);
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
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null || mBluetoothGattWriteCharacteristic == null)
            return;
        final int properties = mBluetoothGattWriteCharacteristic.getProperties();
        if ((properties & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0)
            return;

        mBluetoothGattWriteCharacteristic.setValue(data);
        boolean status = mBluetoothGatt.writeCharacteristic(mBluetoothGattWriteCharacteristic);
        Log.d(TAG," "+status);
    }

    public void deleteBond(){

        try {
            Method removeBond = BluetoothDevice.class.getMethod("removeBond");
            removeBond.invoke(mdevice);

        }catch (Exception e) {
            e.printStackTrace();
            return;
        }

    }

    public void performBonding() {
        boolean result = false;
        final BluetoothDevice bluetoothDevice = mdevice;
        if (bluetoothDevice == null)
            return;
        if (bluetoothDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            result = bluetoothDevice.createBond();
        } else {
            try {
                final Method createBond = bluetoothDevice.getClass().getMethod("createBond");
                if (createBond != null) {
                    createBond.invoke(bluetoothDevice);
                }

            } catch (final Exception e) {

            }
        }
        if (!result)
            Log.d(TAG,"Bonding Failed");

    }


    public void requestConnectionPriority() {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null)
            return;
        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);

    }

        private BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                mBLEState = BLEState.CONNECTED;
                sendData(MainActivity.NOTIFY_CONNECTION,null);

            } else {
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG,"Error:"+ Integer.toHexString(status));
                    }
                    sendData(MainActivity.NOTIFY_DISCONNECTION,null);
                    Log.d(TAG, "Disconnected from GATT server.");

                    startScan();
                } else {
                    if (status != BluetoothGatt.GATT_SUCCESS)
                        Log.d(TAG,"Error:"+ Integer.toHexString(status));
                }
            }
            switch (newState)
            {
                case BluetoothProfile.STATE_CONNECTED:
                    sendData(MainActivity.NOTIFY_CONNECTION,gatt);
                    requestConnectionPriority();

                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    sendData(MainActivity.NOTIFY_DISCONNECTION,null);
                    Log.d(TAG, "Disconnected from GATT server.");

                    startScan();
                    break;

            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status)
        {
            Log.d(TAG,"Services are discovered");
            //performBonding();
            //getSupportedServices();
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

    public BroadcastReceiver mBondingBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,-1);
            final int previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,-1);

            if (mBluetoothGatt == null || !device.getAddress().equals(mBluetoothGatt.getDevice().getAddress()))
                return;
            switch (bondState) {
                case BluetoothDevice.BOND_BONDING:
                    Log.d(TAG,"Bonding");
                    sendData(MainActivity.NOTIFY_BONDING,null);
                    break;
                case BluetoothDevice.BOND_BONDED:
                    Log.d(TAG,"Bonded");
                    sendData(MainActivity.NOTIFY_BONDED,null);
                    break;

            }
        }
    };

    public BroadcastReceiver mBluetoothStateReceiver = new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent) {

            final String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
                final int previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE,BluetoothAdapter.STATE_OFF);

                switch(state) {
                    case BluetoothAdapter.STATE_TURNING_OFF:
                    case BluetoothAdapter.STATE_OFF:
                        final String stateString = "Broadcast Action Received:" + BluetoothAdapter.ACTION_STATE_CHANGED + "state changed";
                        Log.d(TAG,stateString);
                        if (mBLEState == BLEState.CONNECTED && previousState != BluetoothAdapter.STATE_TURNING_OFF && previousState != BluetoothAdapter.STATE_OFF) {
                            sendData(MainActivity.NOTIFY_DISCONNECTION,null);
                        }
                        break;
                }

            }
        }
    };

    public class BLEServiceBinder extends Binder {
        public void registerActivityMessenger(Messenger messenger){

            mMessenger = messenger;
        }

        public void internaldiscoverServices() {
            mBluetoothGatt.discoverServices();
        }

        public void readServices() {
            getSupportedServices();
        }

        public void internalBond() {
            performBonding();
        }

        public void internalEnableNotifications() {
            enablenotifications();
        }

        public void internalDisconnection() {
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mBluetoothGatt.close();
            mBluetoothGatt = null;
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

        public void removeBond() {
            deleteBond();
        }

    }





}
