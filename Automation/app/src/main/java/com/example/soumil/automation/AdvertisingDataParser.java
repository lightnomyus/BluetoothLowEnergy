package com.example.soumil.automation;

import android.util.Log;

/**
 * Created by soumi on 07-12-2017.
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
   // String mServiceUUID;
    int mAdvertisingFlags;
    int mManufacturerID;
    byte[] mManufacturerSpecificData;
    String mDeviceName;
    int mBatteryState;
    int mDeviceStatus;
    int mTxPowerLevel;
    int mBondStaus;
    int mStandbyStatus;
    //int mDeviceID;
    //int mFirmwareVersion;
    //int mTemperatureValue;
    //int mBatteryValue;
    String mDeviceAddress;
    boolean mParsingComplete = false;

    public int getStandbyStatus() {
        return mStandbyStatus;
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

    public int isDeviceBonded() {
        return mBondStaus;
    }

    public int getDeviceStatus()
    {
        return mDeviceStatus;
    }

    public int getBatteryState()
    {
       return mBatteryState;
    }

    private AdvertisingDataParser(int advertisingFlags, int manufacturerId, int DeviceStatus, int BatteryState,  String deviceName, boolean parsingComplete, String deviceAddress, int bondStatus, int standbystatus)
    {
        mAdvertisingFlags = advertisingFlags;
        mManufacturerID = manufacturerId;
        mDeviceName = deviceName;
        mDeviceStatus = DeviceStatus;
        mBatteryState = BatteryState;
        mParsingComplete = parsingComplete;
        mDeviceAddress = deviceAddress;
        mBondStaus = bondStatus;
        mStandbyStatus = standbystatus;

    }

    public static AdvertisingDataParser parser(byte scanrecord[], String deviceAddress) {

        if (scanrecord == null) {
            return null;
        }
        int currentPos = 0;
        int advertisingFlags = -1;
        String deviceName = null;
        int manufacturerId = -1;
        byte[] manufacturerSpecificData;
        int deviceStatus = 0;
        int batteryState = 0;
        boolean ParsingComplete = true;
        int bondStatus = 0;
        int standbyStatus = 0;

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
                    if (bytes != null)
                        deviceName = new String(bytes);
                    Log.d(TAG,deviceName);
                    break;

                case MANUFACTURER_SPECIFIC_DATA:
                    manufacturerSpecificData = extractbytes(scanrecord, currentPos + 2, datalength - 2);
                    if (manufacturerSpecificData != null) {
                        manufacturerId = (scanrecord[currentPos] & MASK) + ((scanrecord[currentPos + 1] & MASK) << 8);
                        Log.d(TAG, String.valueOf(manufacturerId));
                        if (manufacturerSpecificData.length == 2) {
                            bondStatus = manufacturerSpecificData[0];
                            deviceStatus = manufacturerSpecificData[1];
                        }
                    }
                    break;

                case TX_POWER_LEVEL:
                    break;
                case SERVICE_DATA:
                    break;
            }

            currentPos = currentPos + datalength;
        }

        ParsingComplete = true;

        return new AdvertisingDataParser(advertisingFlags,manufacturerId, deviceStatus, batteryState, deviceName, ParsingComplete, deviceAddress, bondStatus,standbyStatus);
    }

    private static byte[] extractbytes(byte scanRecord[], int start, int length)
    {
        byte[] bytes;
        bytes = new byte[length];
        System.arraycopy(scanRecord, start,bytes,0,length);
        return bytes;
    }

}
