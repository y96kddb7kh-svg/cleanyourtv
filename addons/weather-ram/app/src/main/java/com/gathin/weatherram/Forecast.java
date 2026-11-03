package com.gathin.weatherram;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Open-Meteo, which needs no key: the place search and the weather now. The only thing sent is the place name
 * typed in the search, then that place's latitude and longitude. Every call blocks, so the activity makes them
 * off the UI thread.
 */
final class Forecast {
    static final long REFRESH_MS = 30 * 60_000L;
    static final long RETRY_MS = 5 * 60_000L;
    private static final int TIMEOUT_MS = 8_000;

    private Forecast() { }

    /** A place from the search: its name, region and country for the list, and where it is. */
    static final class Place {
        final String name;
        final String detail;
        final double latitude;
        final double longitude;

        Place(String name, String detail, double latitude, double longitude) {
            this.name = name;
            this.detail = detail;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /** The weather now: degrees Celsius as read (air and feels-like), the WMO code, day or night, humidity % and wind km/h. */
    static final class Report {
        final double tempC;
        final double feelsC;
        final int code;
        final boolean day;
        final int humidity;
        final int windKmh;

        Report(double tempC, double feelsC, int code, boolean day, int humidity, int windKmh) {
            this.tempC = tempC;
            this.feelsC = feelsC;
            this.code = code;
            this.day = day;
            this.humidity = humidity;
            this.windKmh = windKmh;
        }
    }

    /** Up to five places matching what was typed, best match first. Empty when nothing matches. */
    static List<Place> search(String name) throws IOException, JSONException {
        JSONObject reply = get("https://geocoding-api.open-meteo.com/v1/search?count=5&format=json&name="
                + URLEncoder.encode(name.trim(), "UTF-8") + "&language=" + Locale.getDefault().getLanguage());
        List<Place> out = new ArrayList<>();
        JSONArray results = reply.optJSONArray("results");
        if (results == null) return out;
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            StringBuilder detail = new StringBuilder(r.optString("admin1"));
            String country = r.optString("country");
            if (!country.isEmpty()) detail.append(detail.length() > 0 ? ", " : "").append(country);
            out.add(new Place(r.getString("name"), detail.toString(), r.getDouble("latitude"), r.getDouble("longitude")));
        }
        return out;
    }

    /** The weather at a place, now. */
    static Report fetch(double latitude, double longitude) throws IOException, JSONException {
        String where = String.format(Locale.ROOT, "latitude=%.4f&longitude=%.4f", latitude, longitude);
        JSONObject reply = get("https://api.open-meteo.com/v1/forecast?" + where + "&models=icon_seamless"
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,is_day,wind_speed_10m"
                + "&timezone=auto&forecast_days=1");
        JSONObject current = reply.getJSONObject("current");
        double temp = current.getDouble("temperature_2m");
        return new Report(temp, current.getDouble("apparent_temperature"),
                current.getInt("weather_code"), current.getInt("is_day") == 1,
                (int) Math.round(current.getDouble("relative_humidity_2m")),
                (int) Math.round(current.optDouble("wind_speed_10m", 0)));
    }

    private static JSONObject get(String url) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }

    /** Whole degrees for the page: Celsius as read, or Fahrenheit converted before rounding. */
    static int degrees(double celsius, boolean fahrenheit) {
        return (int) Math.round(fahrenheit ? celsius * 9 / 5 + 32 : celsius);
    }

    /** Whole miles per hour from km/h. */
    static int mph(int kmh) {
        return (int) Math.round(kmh / 1.609344);
    }

    /** The words for a WMO weather code, as fine as the code goes ("Light snow", not "Snow"). */
    static int sky(int code, boolean day) {
        switch (code) {
            case 0: return day ? R.string.sky_sunny : R.string.sky_clear;
            case 1: return day ? R.string.sky_mostly_sunny : R.string.sky_mostly_clear;
            case 2: return R.string.sky_partly_cloudy;
            case 3: return R.string.sky_cloudy;
            case 45: case 48: return R.string.sky_fog;
            case 51: case 53: case 55: return R.string.sky_drizzle;
            case 56: case 57: return R.string.sky_freezing_drizzle;
            case 61: return R.string.sky_light_rain;
            case 63: return R.string.sky_rain;
            case 65: return R.string.sky_heavy_rain;
            case 66: case 67: return R.string.sky_freezing_rain;
            case 71: return R.string.sky_light_snow;
            case 73: return R.string.sky_snow;
            case 75: return R.string.sky_heavy_snow;
            case 77: return R.string.sky_snow_grains;
            case 80: case 81: case 82: return R.string.sky_showers;
            case 85: case 86: return R.string.sky_snow_showers;
            default: return code >= 95 ? R.string.sky_thunderstorm : R.string.sky_cloudy;
        }
    }

    /** A full-screen gradient in the weather's colours, dark at the bottom. */
    static Drawable backdrop(int code, boolean day) {
        int[] colours;
        if (code <= 1) {
            colours = day ? new int[]{0xFF2A6AA8, 0xFF0A1626} : new int[]{0xFF0D1B3E, 0xFF020308};
        } else if (code == 45 || code == 48) {
            colours = new int[]{0xFF5A6068, 0xFF101113};
        } else if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            colours = new int[]{0xFF6F8497, 0xFF101820};
        } else if (code >= 95) {
            colours = new int[]{0xFF2D2140, 0xFF07050C};
        } else if (code >= 51) {
            colours = new int[]{0xFF1E2C3D, 0xFF06090E};
        } else {
            colours = new int[]{0xFF3B4654, 0xFF0C0F14};
        }
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colours);
    }
}
