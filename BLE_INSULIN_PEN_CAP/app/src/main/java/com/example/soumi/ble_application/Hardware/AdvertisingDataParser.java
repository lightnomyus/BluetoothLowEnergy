package com.example.soumi.ble_application.Hardware;

import android.util.Log;

/**
 * Created by soumi on 10-12-2016.
 */

public final class AdvertisingDataParser {

    private static final String TAG = AdvertisingDataParser.class.getSimpleName();
    private static final int ADVERTISING_FLAGS = 0x01;
    private static final int SHORT_LOCAL_NAME = 0x08;
    private static final int COMPLETE_LOCAL_NAME = 0x09;
    private static final int TX_POWER_LEVEL = 0x0A;
    private static final int SERVICE_DATA = 0x16;
    private static final int MANUFACTURER_SPECIFIC_DATA = 0xFF;
    private static final int SERVICE_UUID_16_BIT_COMPLETE = 0x03;
    private static final int MASK = 0xFF;
    String mServiceUUID;
    int mAdvertisingFlags;
    int mManufacturerID;
    byte[] mManufacturerSpecificData;
    String mDeviceName;
    int mBatteryChangedIndication;
    int mTemperatureLevelIndication;
    int mDeviceStatus;
    int mBatteryStatus;
    int mTxPowerLevel;
    int mDeviceID;
    int mFirmwareVersion;
    int mTemperatureValue;
    int mBatteryValue;
    String mDeviceAddress;
    boolean mParsingComplete = false;

    public int getDeviceID()
    {
        return mDeviceID;
    }
    public int geBatteryLevels()
    {
        return mBatteryValue;
    }
    public int getTemperatureValue()
    {
        return mTemperatureValue;
    }
    public int getFirmwareVersion()
    {
        return mFirmwareVersion;
    }
    public String getDeviceName()
    {
        return mDeviceName;
    }
    public int getManufacturerID()
    {
        return mManufacturerID;
    }
    public String getDeviceAddress() {
        return mDeviceAddress;
    }
    public int getAdvertisingFlags()
    {

        return mAdvertisingFlags;
    }
    public boolean ismParsingComplete()
    {
        return mParsingComplete;
    }

    public boolean getBatteryChangedCondition()
    {
        boolean state = false;
        int battery_changed = mBatteryChangedIndication;
        switch (battery_changed) {
            case 0x01:
                state = true;
                break;
            case 0x02:
                state = false;
                break;
        }
        return state;
    }
    public boolean getTemperatureLevelIndication()
    {
        boolean state = false;
        int temperature_level_achieved = mTemperatureLevelIndication;
        switch (temperature_level_achieved)
        {
            case 0x01:
                state = true;
                break;
            case 0x02:
                state = false;
                break;
        }
        return state;

    }

    public int getDeviceStatus()
    {
        int device_status = mDeviceStatus;
        return device_status;
    }

    public String getBatteryState()
    {
        String batteryState = "Normal";
        int battery_state = mBatteryStatus;

        switch(battery_state)
        {
            case 0x01:
                batteryState = "Normal";
                break;
            case 0x02:
                batteryState = "Warning";
                break;
            case 0x03:
                batteryState = "Error";
                break;
        }
        return batteryState;
    }

    private AdvertisingDataParser(int advertisingFlags, int manufacturer_id, int BatteryChangedCondition, int TemperatureLevelIndication, int DeviceStatus, int BatteryState,  String device_name, boolean Parsing_complete, String deviceAddress, int DeviceID, int FirmwareVersion, int TemperatureValue, int BatteryLevels, int TxPowerLevel, String Service_uuid )
    {
        mAdvertisingFlags = advertisingFlags;
        mManufacturerID = manufacturer_id;
        mDeviceName = device_name;
        mBatteryChangedIndication = BatteryChangedCondition;
        mTemperatureLevelIndication = TemperatureLevelIndication;
        mDeviceStatus = DeviceStatus;
        mBatteryStatus = BatteryState;
        mParsingComplete = Parsing_complete;
        mDeviceAddress = deviceAddress;
        mDeviceID = DeviceID;
        mFirmwareVersion = FirmwareVersion;
        mTemperatureValue = TemperatureValue;
        mBatteryValue = BatteryLevels;
        mTxPowerLevel = TxPowerLevel;
        mServiceUUID = Service_uuid;

    }

    public static AdvertisingDataParser parser(byte scanrecord[], String deviceAddress) {

        if (scanrecord == null) {
            return null;
        }
        int currentPos = 0;
        int advertisingFlags = -1;
        String device_name = null;
        String device_address;
        int manufacturer_id = -1;
        byte[] manufacturer_specific_data;
        String Service_uuid = null;

        int BatteryChangedCondition = 0;
        int TemperatureLevelCondition = 0;
        int DeviceStatus = 0;
        int BatteryState = 0;
        int TxPowerLevel = 0;
        int BatteryLevel = 0;
        int TempLevel = 0;
        int DeviceID = 0;
        int FirmwareVersion = 0;
        boolean ParsingComplete = true;

        while (currentPos < scanrecord.length) {
            int length = scanrecord[currentPos++] & MASK;
            if (length == 0) {
                break;
            }
            int datalength = length - 1;
            int fieldtype = scanrecord[currentPos++] & MASK;

            switch (fieldtype) {
                case ADVERTISING_FLAGS:
                    advertisingFlags = scanrecord[currentPos] & MASK;
                    break;
                case COMPLETE_LOCAL_NAME:
                    byte[] bytes = extractbytes(scanrecord, currentPos, datalength);
                    device_name = new String(bytes);
                    Log.d(TAG,device_name);
                    break;

                case MANUFACTURER_SPECIFIC_DATA:
                    manufacturer_specific_data = extractbytes(scanrecord, currentPos + 2, datalength - 2);
                    for (int i = 0; i<manufacturer_specific_data.length;i++)
                    {
                        Log.d(TAG,"manufacturing_data"+ Byte.valueOf(manufacturer_specific_data[i]));
                    }
                    switch(manufacturer_specific_data[0]) {
                        case 0:
                            manufacturer_id = (scanrecord[currentPos] & MASK )+ ((scanrecord[currentPos + 1] & MASK) << 8);
                            Log.d(TAG,String.valueOf(manufacturer_id));
                            BatteryChangedCondition = manufacturer_specific_data[0];
                            TemperatureLevelCondition = manufacturer_specific_data[1];
                            DeviceStatus = manufacturer_specific_data[2];
                            BatteryState = manufacturer_specific_data[3];
                            break;
                        case 1:
                            DeviceID = manufacturer_specific_data[0];
                            FirmwareVersion = manufacturer_specific_data[1];
                            break;
                        }
                        break;
                    case TX_POWER_LEVEL:
                        TxPowerLevel = scanrecord[currentPos] & MASK;
                        break;
                    case SERVICE_DATA:
                        int suuid = (scanrecord[currentPos] & MASK) + ((scanrecord[currentPos + 1]& MASK) << 8);
                        Service_uuid = Integer.toHexString(suuid);
                        int servicedatalength = 2;
                        byte[] servicedataBytes = extractbytes(scanrecord, currentPos + servicedatalength, datalength - servicedatalength);
                        BatteryLevel = (3600/256)*((servicedataBytes[0])&MASK);
                        TempLevel = servicedataBytes[1];
                        break;
                }

                currentPos = currentPos + datalength;
            }

            ParsingComplete = true;
            device_address = deviceAddress;

        return new AdvertisingDataParser(advertisingFlags,manufacturer_id,BatteryChangedCondition, TemperatureLevelCondition, DeviceStatus, BatteryState, device_name, ParsingComplete, device_address, DeviceID, FirmwareVersion, TempLevel ,BatteryLevel , TxPowerLevel, Service_uuid);
    }

    private static byte[] extractbytes(byte scanRecord[], int start, int length)
    {
        byte[] bytes;
        bytes = new byte[length];
        System.arraycopy(scanRecord, start,bytes,0,length);
        return bytes;
    }

}
