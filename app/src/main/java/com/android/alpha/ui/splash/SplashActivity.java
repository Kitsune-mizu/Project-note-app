package com.android.alpha.ui.splash;

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
import com.android.alpha.R;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.ui.auth.LoginActivity;
import com.android.alpha.ui.main.MainActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final int LOCATION_PERMISSION_REQUEST = 101;
    private static final int REQUEST_CHECK_SETTINGS      = 102;

    private FrameLayout         flagContainer;
    private ImageView           imgFlag;
    private TextView            tvGreeting;
    private LottieAnimationView lottieAnimationView;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback            locationCallback;
    private LocationRequest             locationRequest;

    private String lastCountry = "";

    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        UserSession.init(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initViews();
        playSplashSequence();
    }

    private void initViews() {
        lottieAnimationView = findViewById(R.id.lottieAnimationView);
        flagContainer       = findViewById(R.id.flagContainer);
        imgFlag             = findViewById(R.id.imgFlag);
        tvGreeting          = findViewById(R.id.tvGreeting);
        tvGreeting.setTypeface(ResourcesCompat.getFont(this, R.font.montserrat));

        TextView tvVersion = findViewById(R.id.tvVersion);
        if (tvVersion != null) {
            try {
                String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                tvVersion.setText(getString(R.string.splash_version_format, v));
            } catch (Exception ignored) {}
        }
    }

    private void playSplashSequence() {
        lottieAnimationView.setAnimation(R.raw.splash_animation);
        lottieAnimationView.playAnimation();
        handler.postDelayed(this::checkLocationPermission, 3000);
    }

    private void goNext() {
        if (fusedLocationClient != null && locationCallback != null)
            fusedLocationClient.removeLocationUpdates(locationCallback);

        startActivity(new Intent(this,
                UserSession.getInstance().isLoggedIn() ? MainActivity.class : LoginActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    // ── Cek izin lokasi ────────────────────────────────────────────────────────

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        } else {
            checkLocationSettings();   // izin sudah ada → cek GPS aktif
        }
    }

    // ── Cek & minta GPS aktif (seperti Google Maps) ────────────────────────────

    /**
     * Menggunakan LocationSettingsRequest dari Google Play Services.
     * Jika GPS mati, muncul dialog sistem "Aktifkan lokasi?" tanpa masuk ke Settings.
     */
    private void checkLocationSettings() {
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000)
                .setMinUpdateIntervalMillis(2000)
                .setWaitForAccurateLocation(true)
                .build();

        LocationSettingsRequest settingsRequest = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .setAlwaysShow(true)   // selalu tampilkan dialog meski user pernah menolak
                .build();

        SettingsClient client = LocationServices.getSettingsClient(this);
        client.checkLocationSettings(settingsRequest)
                .addOnSuccessListener(this, response -> {
                    // GPS sudah aktif → langsung mulai update
                    startRealtimeLocationUpdates();
                })
                .addOnFailureListener(this, e -> {
                    if (e instanceof ResolvableApiException) {
                        // Tampilkan dialog "Aktifkan lokasi?" bawaan sistem
                        try {
                            ((ResolvableApiException) e).startResolutionForResult(
                                    this, REQUEST_CHECK_SETTINGS);
                        } catch (Exception ex) {
                            Log.e(TAG, "startResolution failed", ex);
                            getNetworkLocationFallback();
                        }
                    } else {
                        // Tidak bisa diselesaikan → fallback
                        getNetworkLocationFallback();
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == RESULT_OK) {
                // User mengaktifkan GPS
                startRealtimeLocationUpdates();
            } else {
                // User menolak → fallback IP
                getNetworkLocationFallback();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkLocationSettings();   // izin baru diberikan → cek GPS
        } else {
            getNetworkLocationFallback();
        }
    }

    // ── Location updates ───────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private void startRealtimeLocationUpdates() {
        if (fusedLocationClient == null) return;

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location == null) return;
                handleLocation(location);
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, getMainLooper());
    }

    // ── Geolocation processing ─────────────────────────────────────────────────

    private void handleLocation(Location location) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(SplashActivity.this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
                runOnUiThread(() -> {
                    if (addresses != null && !addresses.isEmpty()) {
                        updateCountry(addresses.get(0).getCountryCode());
                    } else {
                        getNetworkLocationFallback();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Geocoder error: " + e.getMessage());
                runOnUiThread(this::getNetworkLocationFallback);
            }
        }).start();
    }

    private void updateCountry(String countryCode) {
        if (countryCode == null) return;
        countryCode = countryCode.toLowerCase(Locale.ROOT);
        if (!countryCode.equals(lastCountry)) {
            lastCountry = countryCode;
            showFlagSequence(countryCode);
        }
    }

    // ── Fallback ───────────────────────────────────────────────────────────────

    private void getNetworkLocationFallback() {
        new Thread(() -> {
            try {
                String countryCode = getCountryCodeFromIP();
                runOnUiThread(() -> updateCountry(
                        countryCode.isEmpty() ? Locale.getDefault().getCountry() : countryCode));
            } catch (Exception e) {
                Log.e(TAG, "Network location failed: " + e.getMessage());
                runOnUiThread(this::useLocaleFallback);
            }
        }).start();
    }

    private void useLocaleFallback() {
        updateCountry(Locale.getDefault().getCountry().toLowerCase());
    }

    private String getCountryCodeFromIP() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://ipapi.co/json/").openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);
        return new JSONObject(readResponse(connection))
                .optString("country_code", "")
                .toLowerCase(Locale.ROOT);
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            return response.toString();
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────────

    private void showFlagSequence(String countryCode) {
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

            fadeIn(imgFlag, () ->
                    new Thread(() -> {
                        String greeting = getManualGreeting(countryCode);
                        runOnUiThread(() -> animateGreetingChange(greeting));
                    }).start());
        });
    }

    private void animateGreetingChange(String newText) {
        tvGreeting.animate().cancel();
        tvGreeting.setAlpha(0f);
        tvGreeting.setVisibility(View.VISIBLE);
        tvGreeting.setText(newText);
        tvGreeting.animate()
                .alpha(1f)
                .setDuration(600)
                .withEndAction(() -> handler.postDelayed(this::goNext, 2000))
                .start();
    }

    private String getManualGreeting(String countryCode) {
        return switch (countryCode.toLowerCase()) {
            case "id" -> "Halo!";
            case "my" -> "Hai!";
            case "ph" -> "Kamusta!";
            case "th" -> "สวัสดี";
            case "vn" -> "Xin chào!";
            case "la" -> "ສະບາຍດີ";
            case "kh" -> "សួស្តី";
            case "mm" -> "မင်္ဂလာပါ";
            case "jp" -> "こんにちは";
            case "kr" -> "안녕하세요";
            case "cn", "tw", "hk" -> "你好";
            case "in" -> "नमस्ते";
            case "pk", "sa" -> "السلام عليكم";
            case "bd" -> "হ্যালো";
            case "ae", "eg", "ma", "dz" -> "مرحبا";
            case "il" -> "שלום";
            case "ir" -> "سلام";
            case "tr" -> "Merhaba!";
            case "fr" -> "Bonjour!";
            case "de", "nl", "be", "no" -> "Hallo!";
            case "es", "mx", "ar", "cl", "co", "ve", "pe" -> "¡Hola!";
            case "pt", "br" -> "Olá!";
            case "it" -> "Ciao!";
            case "ch" -> "Grüezi!";
            case "gr" -> "Γειά σου";
            case "se", "dk" -> "Hej!";
            case "fi" -> "Hei!";
            case "ru" -> "Привет!";
            case "ua" -> "Привіт!";
            case "cz", "sk" -> "Ahoj!";
            case "hu" -> "Szia!";
            case "ro" -> "Salut!";
            case "bg" -> "Здравей";
            case "pl" -> "Cześć!";
            case "ke" -> "Jambo!";
            case "et" -> "Selam!";
            default -> "Hello!";
        };
    }

    private void fadeIn(View view, Runnable endAction) {
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate().alpha(1f).setDuration(800).withEndAction(endAction).start();
    }

    private void fadeOut(View view, Runnable endAction) {
        view.animate().alpha(0f).setDuration(600)
                .withEndAction(() -> {
                    view.setVisibility(View.GONE);
                    if (endAction != null) endAction.run();
                }).start();
    }
}