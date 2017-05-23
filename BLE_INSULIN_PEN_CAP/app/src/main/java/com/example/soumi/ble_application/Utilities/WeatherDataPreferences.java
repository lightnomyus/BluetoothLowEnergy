package com.example.soumi.ble_application.Utilities;

import android.content.Context;

/**
 * Created by soumi on 15-03-2017.
 */

public class WeatherDataPreferences {
    public static final String PREF_CITY_NAME = "London";

    /*
     * In order to uniquely pinpoint the location on the map when we launch the
     * map intent, we store the latitude and longitude.
     */
    public static final String PREF_COORD_LAT = "coord_lat";
    public static final String PREF_COORD_LONG = "coord_long";

    /*
     * Before you implement methods to return your REAL preference for location,
     * we provide some default values to work with.
     */
    private static final String DEFAULT_WEATHER_LOCATION = "94043,USA";
    private static final double[] DEFAULT_WEATHER_COORDINATES = {37.4284, 122.0724};

    private static final String DEFAULT_MAP_LOCATION =
            "1600 Amphitheatre Parkway, Mountain View, CA 94043";

    public static String getPreferredWeatherLocation(Context context) {
        /** This will be implemented in a future lesson **/
        return getDefaultWeatherLocation();
    }
    private static String getDefaultWeatherLocation() {
        /** This will be implemented in a future lesson **/
        return PREF_CITY_NAME;
    }
}
