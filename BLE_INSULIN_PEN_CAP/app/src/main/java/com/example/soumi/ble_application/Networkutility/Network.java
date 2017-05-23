package com.example.soumi.ble_application.Networkutility;

import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;


public class Network {
    public static String TAG = Network.class.getSimpleName();
    public static final String DYNAMIC_WEATHER_URL = "https://andfun-weather.udacity.com/weather";
    public static final String STATIC_WEATHER_URL = "http://api.openweathermap.org/data/2.5/weather";
    public static final String key = "6f4570509be1ed802358bb3582467429";
    public static final String FORECAST_BASE_URL = STATIC_WEATHER_URL;
    final static String QUERY_PARAM = "q";
    final static String KEY_PARAM = "appid";
    final static String LAT_PARAM = "lat";
    final static String LON_PARAM = "lon";
    final static String FORMAT_PARAM = "mode";
    final static String UNITS_PARAM = "units";
    final static String DAYS_PARAM = "cnt";
    private static final String format = "json";
    private static final String units = "metric";
    private static final int numDays = 1;


    public static URL buildurl(String locationquery)
    {
        Uri builduri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                .appendQueryParameter(QUERY_PARAM,locationquery)
                .appendQueryParameter(KEY_PARAM,key)
                .appendQueryParameter(UNITS_PARAM,units)
                .build();
        URL url = null;
        try
        {
            url = new URL(builduri.toString());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        Log.v(TAG, "Built URI " + url);
        return url;
    }

    public static String getResponseFromHTTP(URL url) throws IOException
    {
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        try {
            InputStream inputStream = urlConnection.getInputStream();
            Scanner scanner = new Scanner(inputStream);
            scanner.useDelimiter("\\A");
            boolean hasInput = scanner.hasNext();
            if (hasInput)
            {
                return scanner.next();
            }
            else
            {
                return null;
            }
        } finally {
            urlConnection.disconnect();
        }
    }


}
