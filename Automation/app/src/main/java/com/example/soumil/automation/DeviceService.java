package com.example.soumil.automation;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

import java.util.UUID;
import java.util.logging.Handler;

public class DeviceService<E extends BleCallbacks> extends Service {

    public final String TAG = DeviceService.class.getSimpleName();
    private static String CLIENT_CHARACTERISTIC_CONFIGUARION_DESCRIPTOR = "00002902-0000-1000-8000-00805f9b34fb";
    private static String JANACARE_DATA_SERVICE = "82fcabcd-8ebd-4d31-b978-fc7bacab2bfe";
    private static String READ_CHARACTERISTIC = "82fcabce-8ebd-4d31-b978-fc7bacab2bfe";
    private static String WRITE_CHARACTERISTIC = "82fcabcf-8ebd-4d31-b978-fc7bacab2bfe";
    private static UUID READ = UUID.fromString(READ_CHARACTERISTIC);
    private static UUID WRITE = UUID.fromString(WRITE_CHARACTERISTIC);
    private static UUID SERVICE = UUID.fromString(JANACARE_DATA_SERVICE);
    private static UUID CCCD = UUID.fromString(CLIENT_CHARACTERISTIC_CONFIGUARION_DESCRIPTOR);

    private final Object mLock = new Object();
    public BluetoothGatt mBluetoothGatt;
    private boolean mConnected;
    protected BluetoothDevice mBluetoothDevice;
    private boolean mInitialConnection;
    private boolean mUserDisconnected;
    protected E mCallbacks;
    public Context mContext;
    protected final android.os.Handler mHandler = null;
    private int mConnectionState = BluetoothGatt.STATE_DISCONNECTED;
    private BleManagerGattCallback mGattCallback;

    public DeviceService(Context context) {
        mContext = context;
       // mHandler = new Handler();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public int getConnectionState() {
        return mConnectionState;
    }

    protected boolean shouldReconnect() {
        return false;
    }

    public void connect(final BluetoothDevice bluetoothDevice) {
        if (mConnected)
            return;
        synchronized (mLock) {
            if (mBluetoothGatt != null) {
                if (!mInitialConnection) {
                    mBluetoothGatt.close();
                    mBluetoothGatt = null;
                    try {
                        Thread.sleep(200);
                    } catch (final InterruptedException e) {

                    }
                }
                else {
                    mInitialConnection = false;
                    mConnectionState = BluetoothGatt.STATE_CONNECTING;
                    mCallbacks.onDeviceConnecting(bluetoothDevice);
                    mBluetoothGatt.connect();
                    return;
                }
            } else {
                mContext.registerReceiver(mBluetoothStateReceiver,new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            }
        }
        final boolean shouldAutoConnect = shouldReconnect();
        mUserDisconnected = !shouldAutoConnect;
        if (shouldAutoConnect)
            mInitialConnection = true;
        mBluetoothDevice = bluetoothDevice;
        mConnectionState = BluetoothGatt.STATE_CONNECTING;
        mCallbacks.onDeviceConnecting(bluetoothDevice);
        mBluetoothGatt = mBluetoothDevice.connectGatt(mContext,false,mGattCallback);
    }

    public boolean disconnect() {
        mUserDisconnected = true;
        mInitialConnection = false;
        if (mBluetoothGatt != null) {
            mConnectionState = BluetoothGatt.STATE_DISCONNECTING;
            mCallbacks.onDeviceDisconnecting(mBluetoothGatt.getDevice());
            return true;
        }
        return false;
    }



    protected abstract class BleManagerGattCallback extends BluetoothGattCallback {


    }

    public final BroadcastReceiver mBluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.STATE_OFF);
            final int previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE,BluetoothAdapter.STATE_OFF);

            switch(state) {
                case BluetoothAdapter.STATE_TURNING_OFF:
                case BluetoothAdapter.STATE_OFF:
                    if (mConnected && previousState != BluetoothAdapter.STATE_TURNING_OFF && previousState != BluetoothAdapter.STATE_OFF) {
                        notifyDeviceDisconnected(mBluetoothDevice);
                    }
                    close();
                    break;
            }
        }
    };

    private void notifyDeviceDisconnected(final BluetoothDevice bluetoothDevice) {
        mConnected = false;
        if (mUserDisconnected) {
            mCallbacks.onDeviceDisconnected(bluetoothDevice);
        }
        else {
        }

    }

    public void close() {
        try {
            mContext.unregisterReceiver(mBluetoothStateReceiver);
            // unregister receiver
        } catch (Exception e) {

        }
        synchronized (mLock) {
            if (mBluetoothGatt != null) {
                mBluetoothGatt.close();
                mBluetoothGatt = null;
            }
        }
        mConnected = false;
        mInitialConnection = false;
        mBluetoothDevice = null;
    }
}
