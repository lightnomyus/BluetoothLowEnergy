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
    int mTxPowerLevel;
    String mDeviceAddress;
    boolean mParsingComplete = false;


    public String getDeviceName()
    {
        return mDeviceName;
    }
    public byte[] getmManufacturerSpecificData()
    {
        return mManufacturerSpecificData;
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


    private AdvertisingDataParser(int AdvertisingFlag,byte[] ManufacturerData ,  String DeviceName, boolean ParsingComplete, String DeviceAddress, int TxPowerLevel, String ServiceUuid )
    {
        mAdvertisingFlags = AdvertisingFlag;
        mManufacturerSpecificData = ManufacturerData.clone();
        mDeviceName = DeviceName;
        mParsingComplete = ParsingComplete;
        mDeviceAddress = DeviceAddress;
        mTxPowerLevel = TxPowerLevel;
        mServiceUUID = ServiceUuid;

    }

    public static AdvertisingDataParser parser(byte scanRecord[], String deviceAddress) {

        if (scanRecord == null) {
            return null;
        }
        int currentPos = 0;
        int advertising_flag = -1;
        String device_name = null;
        String device_address;
        byte[] manufacturer_specific_data = null;
        String service_uuid = null;


        int TxPowerLevel = 0;
        boolean ParsingComplete;

        while (currentPos < scanRecord.length) {
            int length = scanRecord[currentPos++] & MASK;
            if (length == 0) {
                break;
            }
            int datalength = length - 1;
            int fieldtype = scanRecord[currentPos++] & MASK;

            switch (fieldtype) {
                case ADVERTISING_FLAGS:
                    advertising_flag = scanRecord[currentPos] & MASK;
                    break;
                case COMPLETE_LOCAL_NAME:
                    byte[] bytes = extractbytes(scanRecord, currentPos, datalength);
                    device_name = new String(bytes);
                    Log.d(TAG,device_name);
                    break;

                case MANUFACTURER_SPECIFIC_DATA:
                    manufacturer_specific_data = extractbytes(scanRecord, currentPos + 2, datalength - 2);
                        break;
                    case TX_POWER_LEVEL:
                        TxPowerLevel = scanRecord[currentPos] & MASK;
                        break;
                    case SERVICE_DATA:
                        int suuid = (scanRecord[currentPos] & MASK) + ((scanRecord[currentPos + 1]& MASK) << 8);
                        service_uuid = Integer.toHexString(suuid);
                        //int servicedatalength = 2;
                        //byte[] servicedataBytes = extractbytes(scanrecord, currentPos + servicedatalength, datalength - servicedatalength);
                        break;
                }

                currentPos = currentPos + datalength;
            }

            ParsingComplete = true;
            device_address = deviceAddress;

        return new AdvertisingDataParser(advertising_flag,manufacturer_specific_data, device_name, ParsingComplete, device_address, TxPowerLevel, service_uuid);
    }

    private static byte[] extractbytes(byte scanRecord[], int start, int length)
    {
        byte[] bytes;
        bytes = new byte[length];
        System.arraycopy(scanRecord, start,bytes,0,length);
        return bytes;
    }

}
