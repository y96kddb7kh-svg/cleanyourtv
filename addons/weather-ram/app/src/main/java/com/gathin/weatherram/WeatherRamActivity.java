package com.gathin.weatherram;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileReader;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One screen with the local weather and the RAM in use: the place, the temperature now, the conditions, and a
 * memory bar with its percentage. The weather comes from Open-Meteo and is read only while the page is on
 * screen (every {@link Forecast#REFRESH_MS}, or {@link Forecast#RETRY_MS} after a failure); the RAM is read
 * every ten seconds from /proc/meminfo, which needs no permission. The place is chosen once with a search and
 * kept in the app's preferences.
 */
public final class WeatherRamActivity extends Activity {
    private static final String PREFS = "place";
    private static final long RAM_MS = 10_000L;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::refreshWeather;
    private final Runnable ramTick = this::refreshRam;
    private final Runnable clockTick = this::refreshClock;
    private SharedPreferences place;
    private View root;
    private TextView message;
    private TextView clockView;
    private TextView zoneOneView;
    private TextView zoneTwoView;
    private TextView placeView;
    private TextView tempView;
    private TextView skyView;
    private TextView detailsView;
    private TextView updatedView;
    private TextView ramText;
    private View ramFill;
    private boolean resumed;
    private int requestId;
    private int searchId; // City searches are independent of forecast refreshes.
    private boolean fahrenheit;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        place = getSharedPreferences(PREFS, MODE_PRIVATE);
        fahrenheit = "f".equalsIgnoreCase(getString(R.string.temperature_unit).trim());

        root = new LinearLayout(this);
        ((LinearLayout) root).setOrientation(LinearLayout.VERTICAL);
        ((LinearLayout) root).setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(48);
        root.setPadding(pad, dp(40), pad, dp(32));
        root.setBackgroundColor(0xFF0A1626);
        setContentView(root);

        message = text(dp(26), true);
        clockView = text(dp(24), true);
        LinearLayout zonesRow = new LinearLayout(this);
        zonesRow.setOrientation(LinearLayout.HORIZONTAL);
        zonesRow.setGravity(Gravity.CENTER_HORIZONTAL);
        ((LinearLayout) root).addView(zonesRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        zoneOneView = zone(zonesRow, 0);   // both flags are emoji in the name strings, not drawables
        TextView separator = new TextView(this);
        separator.setText(R.string.zone_separator);
        separator.setTextSize(22);
        separator.setTextColor(0xE6FFFFFF);
        separator.setPadding(dp(16), dp(6), dp(16), dp(6));
        zonesRow.addView(separator);
        zoneTwoView = zone(zonesRow, 0);   // Martinique's flag is the emoji in its name string, not a drawable
        placeView = text(dp(30), true);
        tempView = text(dp(96), true);
        skyView = text(dp(30), true);
        detailsView = text(dp(22), false);
        updatedView = text(dp(18), false);
        TextView ramLabel = text(dp(24), true);
        ramLabel.setText(R.string.ram_label);
        ramText = text(dp(24), true);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0x44FFFFFF);
        ramFill = new View(this);
        ramFill.setBackgroundColor(0xFF74A987);
        bar.addView(ramFill, new LinearLayout.LayoutParams(0, dp(14)));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(560), dp(14));
        barParams.setMargins(0, dp(12), 0, dp(12));
        ((LinearLayout) root).addView(bar, barParams);
        Button changeCity = new Button(this);
        changeCity.setText(R.string.change_city);
        changeCity.setOnClickListener(v -> pickCity());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.setMargins(0, dp(20), 0, 0);
        ((LinearLayout) root).addView(changeCity, buttonParams);

        if (!place.contains("name")) {
            message.setText(R.string.pick_first);
            message.setVisibility(View.VISIBLE);
            pickCity();
        } else {
            placeView.setText(place.getString("name", ""));
        }
        changeCity.requestFocus();
    }

    private TextView text(int sizeSp, boolean bright) {
        TextView view = new TextView(this);
        view.setTextSize(sizeSp);
        view.setTextColor(bright ? Color.WHITE : 0xCCFFFFFF);
        view.setGravity(Gravity.CENTER_HORIZONTAL);
        view.setPadding(0, dp(6), 0, dp(6));
        ((LinearLayout) root).addView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** One zone of the row: its flag drawable (0 when the flag is an emoji in the name), then "name time". */
    private TextView zone(LinearLayout row, int flagResource) {
        if (flagResource != 0) {
            android.widget.ImageView flag = new android.widget.ImageView(this);
            flag.setImageResource(flagResource);
            LinearLayout.LayoutParams flagParams = new LinearLayout.LayoutParams(dp(36), dp(24));
            flagParams.setMargins(dp(16), dp(6), dp(8), 0);
            row.addView(flag, flagParams);
        } else {
            TextView pad = new TextView(this);
            pad.setPadding(dp(16), 0, dp(8), 0);
            row.addView(pad, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        TextView view = new TextView(this);
        view.setTextSize(22);
        view.setTextColor(0xE6FFFFFF);
        view.setPadding(0, dp(6), 0, dp(6));
        row.addView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return view;
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        refreshRam();
        refreshClock();
        if (place.contains("name")) refreshWeather();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        requestId++;
        searchId++;
        main.removeCallbacks(tick);
        main.removeCallbacks(ramTick);
        main.removeCallbacks(clockTick);   // nothing runs while the page is not on screen
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    /** The time and date line plus the two other zones, locale-formatted, ticking every second on screen. */
    private void refreshClock() {
        main.removeCallbacks(clockTick);
        if (!resumed) return;
        Date now = new Date();
        clockView.setText(DateFormat.getTimeFormat(this).format(now) + " \u00b7 "
                + DateFormat.getDateFormat(this).format(now));
        DateTimeFormatter time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT, FormatStyle.SHORT)
                .withLocale(Locale.getDefault());
        zoneOneView.setText(getString(R.string.zone_line, getString(R.string.zone_reunion),
                LocalTime.now(ZoneId.of("Indian/Reunion")).format(time)));
        zoneTwoView.setText(getString(R.string.zone_line, getString(R.string.zone_martinique),
                LocalTime.now(ZoneId.of("America/Martinique")).format(time)));
        main.postDelayed(clockTick, 1000L);
    }

    private void refreshWeather() {
        main.removeCallbacks(tick);
        if (!resumed || !place.contains("name")) return;
        int request = ++requestId;
        double lat = Double.longBitsToDouble(place.getLong("lat", 0));
        double lon = Double.longBitsToDouble(place.getLong("lon", 0));
        io.execute(() -> {
            Forecast.Report report;
            try {
                report = Forecast.fetch(lat, lon);
            } catch (Exception e) {
                report = null;
            }
            Forecast.Report result = report;
            main.post(() -> {
                if (isDestroyed() || !resumed || request != requestId) return;
                if (result != null) {
                    show(result);
                    main.postDelayed(tick, Forecast.REFRESH_MS);
                } else if (tempView.getText().length() == 0) {
                    message.setText(R.string.offline);
                    message.setVisibility(View.VISIBLE);
                    main.postDelayed(tick, Forecast.RETRY_MS);
                } else {
                    main.postDelayed(tick, Forecast.RETRY_MS);
                }
            });
        });
    }

    private void refreshRam() {
        main.removeCallbacks(ramTick);
        if (!resumed) return;
        io.execute(() -> {
            long[] memory;
            try (FileReader reader = new FileReader("/proc/meminfo")) {
                memory = Ram.readMb(reader);
            } catch (Exception e) {
                memory = new long[]{-1, -1};
            }
            long[] result = memory;
            main.post(() -> {
                if (isDestroyed() || !resumed) return;
                int percent = Ram.usedPercent(result[0], result[1]);
                if (percent >= 0) {
                    ramText.setText(getString(R.string.ram_line, percent, Ram.totalLabel(result[1])));
                    ramText.setVisibility(View.VISIBLE);
                    ramFill.getLayoutParams().width = dp(560) * percent / 100;
                    ramFill.requestLayout();
                } else {
                    ramText.setText(R.string.ram_unknown);
                }
                main.postDelayed(ramTick, RAM_MS);
            });
        });
    }

    private void show(Forecast.Report r) {
        root.setBackground(Forecast.backdrop(r.code, r.day));
        placeView.setText(place.getString("name", ""));
        tempView.setText(Forecast.degrees(r.tempC, fahrenheit) + "°");
        skyView.setText(Forecast.sky(r.code, r.day));
        StringBuilder details = new StringBuilder();
        details.append(getString(R.string.feels_like, Forecast.degrees(r.feelsC, fahrenheit)));
        details.append(" · ").append(getString(R.string.humidity, r.humidity));
        details.append(" · ").append(fahrenheit ? getString(R.string.wind_mph, Forecast.mph(r.windKmh))
                : getString(R.string.wind, r.windKmh));
        detailsView.setText(details);
        updatedView.setText(getString(R.string.updated, DateFormat.getTimeFormat(this).format(new Date())));
        message.setVisibility(View.GONE);
    }

    /** Type a city, pick it from the matches, keep it. Back leaves the current place as it was. */
    private void pickCity() {
        searchId++;
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint(R.string.pick_hint);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        int pad = dp(24);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(name, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.pick_title)
                .setView(box)
                .setPositiveButton(R.string.pick_search, (d, w) -> search(name.getText().toString()))
                .create();
        name.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            dialog.dismiss();
            search(name.getText().toString());
            return true;
        });
        dialog.show();
        name.requestFocus();
    }

    private void search(String typed) {
        if (!resumed) return;
        int search = ++searchId;
        if (typed.trim().isEmpty()) {
            pickCity();
            return;
        }
        io.execute(() -> {
            List<Forecast.Place> found;
            try {
                found = Forecast.search(typed);
            } catch (Exception e) {
                found = null;
            }
            List<Forecast.Place> places = found;
            main.post(() -> {
                if (isDestroyed() || !resumed || search != searchId) return;
                if (places == null) {
                    Toast.makeText(this, R.string.offline, Toast.LENGTH_LONG).show();
                } else if (places.isEmpty()) {
                    Toast.makeText(this, getString(R.string.pick_none, typed.trim()), Toast.LENGTH_LONG).show();
                    pickCity();
                } else {
                    choose(places);
                }
            });
        });
    }

    private void choose(List<Forecast.Place> places) {
        CharSequence[] rows = new CharSequence[places.size()];
        for (int i = 0; i < rows.length; i++) {
            Forecast.Place p = places.get(i);
            rows[i] = p.detail.isEmpty() ? p.name : p.name + " · " + p.detail;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.pick_title)
                .setItems(rows, (d, which) -> {
                    Forecast.Place p = places.get(which);
                    place.edit()
                            .putString("name", p.name)
                            .putLong("lat", Double.doubleToRawLongBits(p.latitude))
                            .putLong("lon", Double.doubleToRawLongBits(p.longitude))
                            .apply();
                    tempView.setText("");
                    placeView.setText(p.name);
                    message.setText(R.string.loading);
                    message.setVisibility(View.VISIBLE);
                    refreshWeather();
                })
                .show();
    }
}
