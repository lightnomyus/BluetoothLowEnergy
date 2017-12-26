package com.example.soumil.automation;

import android.bluetooth.BluetoothDevice;

public interface BleCallbacks {

    void onDeviceConnecting(final BluetoothDevice bluetoothDevice);

    void onDeviceConnected(final BluetoothDevice bluetoothDevice);

    void onDeviceDisconnecting(final BluetoothDevice bluetoothDevice);

    void onDeviceDisconnected(final BluetoothDevice bluetoothDevice);

    void onServicesDiscovered(final BluetoothDevice bluetoothDevice);

    void onDeviceReady(final BluetoothDevice bluetoothDevice);

    void onError(final BluetoothDevice bluetoothDevice);

    void onBonding(final BluetoothDevice bluetoothDevice);

    void onBonded(final BluetoothDevice bluetoothDevice);

    void onDeviceNotSupported(final BluetoothDevice bluetoothDevice);

    void onStartedScanning();


}