package com.android.alpha.ui.splash;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.android.alpha.R;
import com.android.alpha.base.BaseActivity;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.ui.auth.LoginActivity;
import com.android.alpha.ui.main.MainActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
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
public class SplashActivity extends BaseActivity {

    // ─── Constants & Variables ───────────────────────────────────────────────

    private static final String TAG = "SplashActivity";
    private static final int LOCATION_PERMISSION_REQUEST = 101;
    private static final int REQUEST_CHECK_SETTINGS = 102;

    private WebView splashWebView;
    private LinearLayout greetingContainer;
    private ImageView imgFlag;
    private TextView tvGreeting;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;

    private String lastCountry = "";
    private boolean isFlagLoaded = false;
    private boolean isGreetingReady = false;

    private final Handler handler = new Handler();


    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        UserSession.init(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initViews();
        playSplashSequence();
    }


    // ─── Initialization ──────────────────────────────────────────────────────

    private void initViews() {
        splashWebView = findViewById(R.id.splashWebView);
        loadSplashSvg();

        String cTextUi = splashWebView.getTag() != null ? (String) splashWebView.getTag() : "#1E293B";

        greetingContainer = findViewById(R.id.greetingContainer);
        imgFlag = findViewById(R.id.imgFlag);
        tvGreeting = findViewById(R.id.tvGreeting);
        TextView tvVersion = findViewById(R.id.tvVersion);

        int colorInt = android.graphics.Color.parseColor(cTextUi);
        tvGreeting.setTextColor(colorInt);

        if (tvVersion != null) {
            tvVersion.setTextColor(colorInt);
            try {
                String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                tvVersion.setText(getString(R.string.splash_version_format, v));
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void loadSplashSvg() {
        SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String themeMode = prefs.getString("theme_mode", "system");
        String colorTheme = prefs.getString("color_theme", "blue");

        boolean isDark;
        if (themeMode.equals("dark")) {
            isDark = true;
        } else if (themeMode.equals("light")) {
            isDark = false;
        } else {
            int nightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            isDark = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }

        String cTextUi = isDark ? "#F1F5F9" : "#1E293B";
        splashWebView.setTag(cTextUi);

        String cMid, cAccent, cAccentLight, cBorder;

        if (isDark) {
            cMid = "#1E293B";
            cAccent = "#475569";
            cAccentLight = "#334155";
            cBorder = "#F1F5F9";
        } else {
            switch (colorTheme) {
                case "purple":
                    cMid = "#F3F0FF";
                    cAccent = "#A78BFA";
                    cAccentLight = "#DDD6FE";
                    cBorder = "#1E293B";
                    break;
                case "pink":
                    cMid = "#FCE7F3";
                    cAccent = "#F9A8D4";
                    cAccentLight = "#FBCFE8";
                    cBorder = "#1E293B";
                    break;
                case "green":
                    cMid = "#ECFDF5";
                    cAccent = "#6EE7B7";
                    cAccentLight = "#A7F3D0";
                    cBorder = "#1E293B";
                    break;
                case "orange":
                    cMid = "#FFF7ED";
                    cAccent = "#FB923C";
                    cAccentLight = "#FED7AA";
                    cBorder = "#1E293B";
                    break;
                default:
                    cMid = "#E8F4FD";
                    cAccent = "#7BA7D8";
                    cAccentLight = "#B8D4F1";
                    cBorder = "#1E293B";
                    break;
            }
        }

        WebSettings ws = splashWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        splashWebView.setBackgroundColor(Color.TRANSPARENT);

        String html = buildSvgHtml(cMid, cAccent, cAccentLight, cBorder);
        splashWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private String buildSvgHtml(String cMid, String cAccent, String cAccentLight, String cBorder) {
        return "<!DOCTYPE html><html><head>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>"
                + "html,body{margin:0;padding:0;background:transparent;display:flex;"
                + "align-items:center;justify-content:center;height:100%;width:100%;}"
                + "svg{width:100%;height:auto;max-width:320px;}"
                + ".bubble{animation:pulse 2s ease-in-out infinite;transform-origin:160px 95px;}"
                + ".d1{animation:dotblink 1.2s 0s infinite;}"
                + ".d2{animation:dotblink 1.2s 0.4s infinite;}"
                + ".d3{animation:dotblink 1.2s 0.8s infinite;}"
                + ".sp{animation:sparkle 2s ease-in-out infinite;}"
                + ".sp.s2{animation-delay:0.5s;}.sp.s3{animation-delay:1s;}"
                + ".sp.s4{animation-delay:1.5s;}.sp.s5{animation-delay:0.3s;}"
                + ".sp.s6{animation-delay:0.9s;}.sp.s7{animation-delay:1.3s;}"
                + ".sp.s8{animation-delay:0.7s;}"
                + ".bar-fill{animation:barload 2.4s ease-in-out infinite;}"
                + "@keyframes pulse{0%,100%{transform:scale(1);opacity:1;}50%{transform:scale(1.05);opacity:0.9;}}"
                + "@keyframes dotblink{0%,80%,100%{opacity:0.2;}40%{opacity:1;}}"
                + "@keyframes sparkle{0%,100%{opacity:0;transform:scale(0.5);}50%{opacity:1;transform:scale(1);}}"
                + "@keyframes barload{0%{width:0;}70%{width:116px;}85%{width:116px;}100%{width:0;}}"
                + "</style></head><body>"
                + "<svg viewBox='0 0 320 200' xmlns='http://www.w3.org/2000/svg'>"
                + "<g class='bubble'>"
                + "<rect x='56' y='34' width='8' height='8' fill='" + cBorder + "'/>"
                + "<rect x='64' y='30' width='8' height='8' fill='" + cBorder + "'/>"
                + "<rect x='72' y='28' width='96' height='6' fill='" + cBorder + "'/>"
                + "<rect x='168' y='24' width='8' height='8' fill='" + cBorder + "'/>"
                + "<rect x='176' y='24' width='8' height='6' fill='" + cBorder + "'/>"
                + "<rect x='184' y='26' width='56' height='6' fill='" + cBorder + "'/>"
                + "<rect x='240' y='24' width='6' height='6' fill='" + cBorder + "'/>"
                + "<rect x='246' y='26' width='6' height='6' fill='" + cBorder + "'/>"
                + "<rect x='252' y='30' width='6' height='76' fill='" + cBorder + "'/>"
                + "<rect x='246' y='106' width='6' height='8' fill='" + cBorder + "'/>"
                + "<rect x='238' y='108' width='8' height='8' fill='" + cBorder + "'/>"
                + "<rect x='68' y='114' width='170' height='6' fill='" + cBorder + "'/>"
                + "<rect x='60' y='108' width='8' height='6' fill='" + cBorder + "'/>"
                + "<rect x='54' y='100' width='6' height='8' fill='" + cBorder + "'/>"
                + "<rect x='48' y='48' width='6' height='52' fill='" + cBorder + "'/>"
                + "<rect x='54' y='40' width='6' height='8' fill='" + cBorder + "'/>"
                + "<rect x='60' y='38' width='184' height='72' fill='" + cAccentLight + "'/>"
                + "<rect x='54' y='46' width='6' height='58' fill='" + cAccentLight + "'/>"
                + "<rect x='244' y='36' width='6' height='70' fill='" + cAccentLight + "'/>"
                + "<rect x='64' y='108' width='176' height='6' fill='" + cAccentLight + "'/>"
                + "<rect x='74' y='44' width='158' height='58' fill='" + cAccent + "'/>"
                + "<rect x='66' y='52' width='8' height='44' fill='" + cAccent + "'/>"
                + "<rect x='232' y='52' width='8' height='44' fill='" + cAccent + "'/>"
                + "<rect x='74' y='96' width='158' height='6' fill='" + cAccent + "'/>"
                + "<rect x='208' y='80' width='24' height='18' fill='" + cAccentLight + "'/>"
                + "<rect x='82' y='52' width='142' height='44' fill='" + cMid + "'/>"
                + "<rect x='74' y='58' width='8' height='34' fill='" + cMid + "'/>"
                + "<rect x='224' y='58' width='8' height='26' fill='" + cMid + "'/>"
                + "<rect x='82' y='94' width='126' height='6' fill='" + cMid + "'/>"
                + "<rect x='204' y='76' width='18' height='16' fill='" + cAccentLight + "'/>"
                + "<rect class='d1' x='124' y='66' width='12' height='12' fill='" + cAccent + "'/>"
                + "<rect class='d2' x='144' y='66' width='12' height='12' fill='" + cAccent + "'/>"
                + "<rect class='d3' x='164' y='66' width='12' height='12' fill='" + cAccent + "'/>"
                + "</g>"
                + "<rect class='sp'    x='36'  y='34'  width='6' height='6' fill='" + cBorder + "'/>"
                + "<rect class='sp s2' x='42'  y='22'  width='6' height='6' fill='" + cBorder + "'/>"
                + "<rect class='sp s3' x='260' y='20'  width='6' height='6' fill='" + cBorder + "'/>"
                + "<rect class='sp s4' x='270' y='36'  width='6' height='6' fill='" + cBorder + "'/>"
                + "<rect class='sp s5' x='28'  y='88'  width='6' height='6' fill='" + cBorder + "'/>"
                + "<rect class='sp s6' x='272' y='100' width='6' height='6' fill='" + cBorder + "'/>"
                + "<rect class='sp s7' x='126' y='126' width='6' height='6' fill='" + cBorder + "'/>"
                + "<rect class='sp s8' x='194' y='130' width='6' height='6' fill='" + cBorder + "'/>"
                + "<rect x='102' y='148' width='116' height='10' fill='" + cMid + "'/>"
                + "<rect x='102' y='148' width='116' height='10' fill='none' stroke='" + cBorder + "' stroke-width='1.5'/>"
                + "<rect x='100' y='146' width='4' height='4' fill='" + cBorder + "'/>"
                + "<rect x='216' y='146' width='4' height='4' fill='" + cBorder + "'/>"
                + "<rect x='100' y='156' width='4' height='4' fill='" + cBorder + "'/>"
                + "<rect x='216' y='156' width='4' height='4' fill='" + cBorder + "'/>"
                + "<clipPath id='bc'><rect x='103' y='149' width='116' height='8'/></clipPath>"
                + "<g clip-path='url(#bc)'>"
                + "<rect class='bar-fill' x='103' y='149' width='0' height='8' fill='" + cAccent + "'/>"
                + "</g>"
                + "<text x='160' y='176' text-anchor='middle' font-family='monospace' "
                + "font-size='10' fill='" + cBorder + "' font-weight='bold' "
                + "letter-spacing='2' opacity='0.7'>LOADING...</text>"
                + "</svg></body></html>";
    }


    // ─── Navigation ──────────────────────────────────────────────────────────

    private void goNext() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        startActivity(new Intent(this, UserSession.getInstance().isLoggedIn() ? MainActivity.class : LoginActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }


    // ─── Animations ──────────────────────────────────────────────────────────

    private void playSplashSequence() {
        handler.postDelayed(this::checkLocationPermission, 3000);
    }

    private void preloadFlagAndGreeting(String countryCode) {
        isFlagLoaded = false;
        isGreetingReady = false;

        String flagUrl = "https://flagcdn.com/w320/" + countryCode + ".png";
        String greeting = getManualGreeting(countryCode);

        tvGreeting.setText(greeting);
        isGreetingReady = true;

        int size = 200;
        RequestOptions options = new RequestOptions()
                .override(size, (int) (size * 0.665f))
                .transform(new RoundedCorners(12));

        Glide.with(this)
                .load(flagUrl)
                .apply(options)
                .listener(new com.bumptech.glide.request.RequestListener<>() {
                    @Override
                    public boolean onLoadFailed(
                            @androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e,
                            Object model,
                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                            boolean isFirstResource) {
                        Log.e(TAG, "Flag load failed", e);
                        isFlagLoaded = true;
                        checkAndTransitionToPhase2();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(
                            android.graphics.drawable.Drawable resource,
                            Object model,
                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                            com.bumptech.glide.load.DataSource dataSource,
                            boolean isFirstResource) {
                        isFlagLoaded = true;
                        checkAndTransitionToPhase2();
                        return false;
                    }
                })
                .into(imgFlag);
    }

    private void checkAndTransitionToPhase2() {
        if (isFlagLoaded && isGreetingReady) {
            handler.post(this::transitionToPhase2);
        }
    }

    private void transitionToPhase2() {
        splashWebView.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction(() -> {
                    splashWebView.setVisibility(View.GONE);
                    showGreetingSequence();
                })
                .start();
    }

    private void showGreetingSequence() {
        greetingContainer.setVisibility(View.VISIBLE);
        greetingContainer.setAlpha(0f);

        imgFlag.setVisibility(View.VISIBLE);
        imgFlag.setAlpha(0f);
        imgFlag.setScaleX(0.8f);
        imgFlag.setScaleY(0.8f);

        imgFlag.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(600)
                .withStartAction(() -> greetingContainer.animate().alpha(1f).setDuration(600).start())
                .withEndAction(this::showGreetingText)
                .start();
    }

    private void showGreetingText() {
        tvGreeting.setVisibility(View.VISIBLE);
        tvGreeting.setAlpha(0f);
        tvGreeting.setTranslationY(20f);

        tvGreeting.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setStartDelay(200)
                .withEndAction(() -> handler.postDelayed(this::goNext, 2000))
                .start();
    }


    // ─── Location Permissions ────────────────────────────────────────────────

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST
            );
        } else {
            checkLocationSettings();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkLocationSettings();
        } else {
            getNetworkLocationFallback();
        }
    }


    // ─── Location Settings & Updates ─────────────────────────────────────────

    private void checkLocationSettings() {
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000)
                .setMinUpdateIntervalMillis(2000)
                .setWaitForAccurateLocation(true)
                .build();

        LocationSettingsRequest settingsRequest = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .setAlwaysShow(true)
                .build();

        SettingsClient client = LocationServices.getSettingsClient(this);
        client.checkLocationSettings(settingsRequest)
                .addOnSuccessListener(this, response -> startRealtimeLocationUpdates())
                .addOnFailureListener(this, e -> {
                    if (e instanceof ResolvableApiException) {
                        try {
                            ((ResolvableApiException) e).startResolutionForResult(this, REQUEST_CHECK_SETTINGS);
                        } catch (Exception ex) {
                            Log.e(TAG, "startResolution failed", ex);
                            getNetworkLocationFallback();
                        }
                    } else {
                        getNetworkLocationFallback();
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == RESULT_OK) {
                startRealtimeLocationUpdates();
            } else {
                getNetworkLocationFallback();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void startRealtimeLocationUpdates() {
        if (fusedLocationClient == null) {
            return;
        }

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location == null) {
                    return;
                }
                handleLocation(location);
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, getMainLooper());
    }


    // ─── Geocoding & Processing ──────────────────────────────────────────────

    private void handleLocation(Location location) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(SplashActivity.this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);

                runOnUiThread(() -> {
                    if (addresses != null && !addresses.isEmpty()) {
                        String countryName = addresses.get(0).getCountryName();
                        UserSession.getInstance().setDetectedCountryName(
                                countryName != null ? countryName : ""
                        );

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
        if (countryCode == null) {
            return;
        }

        countryCode = countryCode.toLowerCase(Locale.ROOT);

        if (!countryCode.equals(lastCountry)) {
            lastCountry = countryCode;
            preloadFlagAndGreeting(countryCode);
        }
    }


    // ─── Fallback Location (IP/Locale) ───────────────────────────────────────

    private void getNetworkLocationFallback() {
        new Thread(() -> {
            try {
                String countryCode = getCountryNameFromIP();
                runOnUiThread(() -> {
                    if (countryCode.isEmpty()) {
                        updateCountry(Locale.getDefault().getCountry());
                    } else {
                        updateCountry(countryCode);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Network location failed: " + e.getMessage());
                runOnUiThread(this::useLocaleFallback);
            }
        }).start();
    }

    private void useLocaleFallback() {
        updateCountry(Locale.getDefault().getCountry().toLowerCase());
    }

    private String getCountryNameFromIP() throws Exception {
        HttpURLConnection connection =
                (HttpURLConnection) new URL("https://ipapi.co/json/").openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);

        JSONObject json = new JSONObject(readResponse(connection));

        // simpan nama negara
        UserSession.getInstance().setDetectedCountryName(
                json.optString("country_name", "")
        );

        // return kode negara
        return json.optString("country_code", "")
                .toLowerCase(Locale.ROOT);
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }


    // ─── Greeting Helper ─────────────────────────────────────────────────────

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
}