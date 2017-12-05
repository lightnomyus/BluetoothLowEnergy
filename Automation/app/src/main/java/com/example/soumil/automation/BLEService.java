package com.example.soumil.automation;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Messenger;

public class BLEService extends Service {

    public enum BLEState {SCANNING, CONNECTED, DISCONNECTED};
    public BLEState mBLEState;
    private final IBinder mBinder = new BLEServiceBinder();
    private Messenger mMessenger;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothManager mBluetoothManager;
    public static boolean isServiceBinded;
    public static final String TAG = BLEService.class.getName();
    public BLEService() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        isServiceBinded = true;
        mBluetoothManager = (BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);

        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        super.onUnbind(intent);
        isServiceBinded = false;

        return true;
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        mBLEState = BLEState.SCANNING;
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
