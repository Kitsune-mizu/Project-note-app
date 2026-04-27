package com.android.kitsune.ui.splash;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;

import com.airbnb.lottie.LottieAnimationView;
import com.android.kitsune.MainActivity;
import com.android.kitsune.R;
import com.android.kitsune.data.session.UserSession;
import com.android.kitsune.ui.auth.LoginActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final int LOCATION_PERMISSION_REQUEST = 101;

    private FrameLayout flagContainer;
    private ImageView imgFlag;
    private TextView tvGreeting;
    private LottieAnimationView lottieAnimationView;
    private final Handler handler = new Handler();

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private String lastCountry = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        UserSession.init(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        lottieAnimationView = findViewById(R.id.lottieAnimationView);
        flagContainer = findViewById(R.id.flagContainer);
        imgFlag = findViewById(R.id.imgFlag);
        tvGreeting = findViewById(R.id.tvGreeting);

        // Signature font
        tvGreeting.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat));

        playSplashSequence();
    }

    private void playSplashSequence() {
        lottieAnimationView.setAnimation(R.raw.splash_animation);
        lottieAnimationView.playAnimation();
        handler.postDelayed(this::checkLocationPermission, 3000);
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
        } else {
            startRealtimeLocationUpdates();
        }
    }

    @SuppressLint("MissingPermission")
    private void startRealtimeLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000)
                .setMinUpdateIntervalMillis(2000)
                .setWaitForAccurateLocation(true)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location == null) return;
                handleLocation(location);
            }
        };

        fusedLocationClient.requestLocationUpdates(request, locationCallback, getMainLooper());
    }

    private void handleLocation(Location location) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                String countryCode = addresses.get(0).getCountryCode().toLowerCase(Locale.ROOT);
                if (!countryCode.equals(lastCountry)) {
                    lastCountry = countryCode;
                    showFlagSequence(countryCode);
                }
            } else {
                getNetworkLocationFallback();
            }
        } catch (Exception e) {
            Log.e(TAG, "Geocoder error: " + e.getMessage(), e);
            getNetworkLocationFallback();
        }
    }

    private void getNetworkLocationFallback() {
        new Thread(() -> {
            try {
                String countryCode = getCountryCodeFromIP();
                runOnUiThread(() -> {
                    if (!countryCode.isEmpty() && !countryCode.equals(lastCountry)) {
                        lastCountry = countryCode;
                        showFlagSequence(countryCode);
                    } else {
                        useLocaleFallback();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Network lokasi gagal: " + e.getMessage());
                runOnUiThread(this::useLocaleFallback);
            }
        }).start();
    }

    private String getCountryCodeFromIP() throws Exception {
        URL url = new URL("https://ipapi.co/json/");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            JSONObject json = new JSONObject(response.toString());
            return json.optString("country_code", "").toLowerCase(Locale.ROOT);
        }
    }

    private void useLocaleFallback() {
        Locale locale = Locale.getDefault();
        String countryCode = locale.getCountry().toLowerCase();
        if (!countryCode.equals(lastCountry)) {
            lastCountry = countryCode;
            showFlagSequence(countryCode);
        }
    }

    private void showFlagSequence(String countryCode) {
        if (countryCode == null || countryCode.isEmpty()) return;

        String flagUrl = "https://flagcdn.com/w320/" + countryCode + ".png";

        fadeOut(lottieAnimationView, () -> {
            flagContainer.setVisibility(View.VISIBLE);

            int size = (int) (tvGreeting.getLineHeight() * 1.2f);

            RequestOptions options = new RequestOptions()
                    .override(size, size)
                    .transform(new RoundedCorners(size / 4));

            Glide.with(this)
                    .load(flagUrl)
                    .apply(options)
                    .transition(DrawableTransitionOptions.withCrossFade(800))
                    .into(imgFlag);

            fadeIn(imgFlag, () -> {
                String languageCode = getLanguageFromCountryISO639_1(countryCode);

                new Thread(() -> {
                    String greeting = translateGreeting(languageCode, countryCode);
                    runOnUiThread(() -> animateGreetingChange(greeting));
                }).start();
            });
        });
    }

    private String getLanguageFromCountryISO639_1(String countryCode) {
        try {
            Locale locale = new Locale("", countryCode);
            String lang = locale.getLanguage();
            if (lang.isEmpty()) {
                switch (countryCode) {
                    case "ru": return "ru";
                    case "jp": return "ja";
                    case "kr": return "ko";
                    case "cn": return "zh";
                    default: return Locale.getDefault().getLanguage();
                }
            }
            return lang.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return Locale.getDefault().getLanguage();
        }
    }

    private String getManualGreeting(String countryCode) {
        switch (countryCode.toLowerCase()) {
            case "us":
            case "gb":
                return "Hello!"; // Amerika & Inggris
            case "id":
                return "Halo!"; // Indonesia
            case "fr":
                return "Bonjour!"; // Prancis
            case "de":
                return "Hallo!"; // Jerman
            case "es":
                return "¡Hola!"; // Spanyol
            case "it":
                return "Ciao!"; // Italia
            case "jp":
                return "こんにちは"; // Jepang
            case "kr":
                return "안녕하세요"; // Korea
            case "cn":
                return "你好"; // Cina
            case "ru":
                return "Привет!"; // Rusia
            default:
                return "Hello!"; // fallback umum
        }

    }

    private void animateGreetingChange(String newText) {
        tvGreeting.animate().cancel();
        tvGreeting.setAlpha(0f);
        tvGreeting.setVisibility(View.VISIBLE);

        tvGreeting.setText(newText); // set teks dulu

        tvGreeting.animate()
                .alpha(1f)
                .setDuration(600)
                .withEndAction(() -> handler.postDelayed(this::goNext, 2000))
                .start();
    }

    private String translateGreeting(String targetLang, String countryCode) {
        try {
            JSONObject jsonInput = new JSONObject();
            jsonInput.put("q", "Hello!");
            jsonInput.put("source", "en");
            jsonInput.put("target", targetLang);
            jsonInput.put("format", "text");

            HttpURLConnection connection = openPostConnection();
            try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
                writer.write(jsonInput.toString());
                writer.flush();
            }

            int code = connection.getResponseCode();
            if (code != 200) return getManualGreeting(countryCode);

            String response = readResponse(connection);
            if (response.startsWith("<!DOCTYPE")) return getManualGreeting(countryCode);

            JSONObject jsonResponse = new JSONObject(response);
            return jsonResponse.optString("translatedText", getManualGreeting(countryCode));
        } catch (Exception e) {
            Log.e(TAG, "Terjemahan gagal: " + e.getMessage(), e);
            return getManualGreeting(countryCode);
        }
    }

    private HttpURLConnection openPostConnection() throws Exception {
        URL url = new URL("https://libretranslate.com/translate");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(6000);
        return connection;
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            return response.toString();
        }
    }

    private void fadeIn(View view, Runnable endAction) {
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate().alpha(1f).setDuration(800).withEndAction(endAction).start();
    }

    private void fadeOut(View view, Runnable endAction) {
        view.animate().alpha(0f).setDuration(600).withEndAction(() -> {
            view.setVisibility(View.GONE);
            if (endAction != null) endAction.run();
        }).start();
    }

    private void goNext() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        boolean loggedIn = UserSession.getInstance().isLoggedIn();
        Intent intent = new Intent(this, loggedIn ? MainActivity.class : LoginActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRealtimeLocationUpdates();
            } else {
                getNetworkLocationFallback();
            }
        }
    }
}
