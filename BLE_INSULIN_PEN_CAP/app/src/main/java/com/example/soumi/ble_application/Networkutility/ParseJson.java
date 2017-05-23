package com.example.soumi.ble_application.Networkutility;


import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;

public class ParseJson {
    public static String getSimpleStringFromJson(Context context, String jsondata) throws JSONException
    {
        final String OWM_MESSAGE_CODE = "cod";  // error code
        final String OWM_LIST = "coord";
        final String OWM_TEMPERATURE = "main";
        final String OWM_MAX = "temp_max";
        final String OWM_MIN = "temp_min";
        double high;
        double low;
        String highAndLow;
        String[] parsedWeatherData = null;
        JSONObject jsonObject = new JSONObject(jsondata);
        if (jsonObject.has(OWM_MESSAGE_CODE))
        {
            int errorCode = jsonObject.getInt(OWM_MESSAGE_CODE);

            switch (errorCode) {
                case HttpURLConnection.HTTP_OK:
                    break;
                case HttpURLConnection.HTTP_NOT_FOUND:
                    /* Location invalid */
                    return null;
                default:
                    /* Server probably down */
                    return null;
            }

        }
       // JSONArray weatherArray = jsonObject.getJSONArray(OWM_LIST);
        //JSONObject weatherforecast = weatherArray.getJSONObject(0);
        JSONObject temperatureForecast = jsonObject.getJSONObject(OWM_TEMPERATURE);
        high = temperatureForecast.getDouble(OWM_MAX);
        low = temperatureForecast.getDouble(OWM_MIN);
        highAndLow = formatHighLows(high,low);
        return highAndLow;

    }
    public static String formatHighLows( double high, double low) {
        long roundedHigh = Math.round(high);
        long roundedLow = Math.round(low);

        String formattedHigh = String.valueOf(roundedHigh);
        String formattedLow = String.valueOf(roundedLow);

        String highLowStr = formattedHigh + " / " + formattedLow;
        return highLowStr;
    }

}
