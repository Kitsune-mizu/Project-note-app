package com.android.alpha.ui.map;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.alpha.R;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.ui.main.MainActivity;
import com.android.alpha.utils.LocaleHelper;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.gms.location.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fragment that displays an OpenStreetMap map view, supports location search with
 * Nominatim autocomplete, GPS location retrieval, marker placement, and reverse geocoding.
 * Calls {@link OnLocationSelectedListener} when the user confirms a location.
 */
public class MapFragment extends Fragment {

    // ─── CONSTANTS ─────────────────────────────────────────────────────────────
    /** Default map center point (Jakarta, Indonesia). */
    private final GeoPoint DEFAULT_POINT  = new GeoPoint(-6.2088, 106.8456);
    /** Duration in milliseconds that a reverse-geocode result stays valid in cache. */
    private static final long CACHE_DURATION = 5 * 60 * 1000; // 5 minutes

    // ─── UI COMPONENTS ─────────────────────────────────────────────────────────
    private MapView            mapView;
    private EditText           etSearch;
    private TextView           tvLocationName;
    private RecyclerView       rvSuggestions;
    private ShimmerFrameLayout shimmerLayout;

    // ─── LOCATION & NETWORK ────────────────────────────────────────────────────
    private FusedLocationProviderClient fusedLocationClient;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ─── STATE & ADAPTERS ──────────────────────────────────────────────────────
    private GeoPoint                  selectedPoint;
    private Marker                    currentMarker;
    private LocationSuggestionAdapter suggestionAdapter;
    private OnLocationSelectedListener listener;
    private Runnable                  reverseRunnable;

    private Typeface getFont() {
        try {
            return androidx.core.content.res.ResourcesCompat.getFont(
                    requireContext(), R.font.linottesemibold);
        } catch (Exception e) {
            return Typeface.DEFAULT;
        }
    }

    // ─── CACHE ─────────────────────────────────────────────────────────────────
    private final Map<String, CacheEntry> locationCache = new HashMap<>();

    /** Simple timestamped cache entry for reverse-geocoded addresses. */
    private static class CacheEntry {
        String address;
        long   timestamp;

        CacheEntry(String address, long timestamp) {
            this.address   = address;
            this.timestamp = timestamp;
        }
    }

    // ─── INTERFACE ─────────────────────────────────────────────────────────────

    /** Callback triggered when the user confirms a location selection. */
    public interface OnLocationSelectedListener {
        void onLocationSelected(String location);
    }

    /** Registers the listener that receives the confirmed location string. */
    public void setOnLocationSelectedListener(OnLocationSelectedListener listener) {
        this.listener = listener;
    }

    // ─── PERMISSION LAUNCHER ───────────────────────────────────────────────────

    /** Requests ACCESS_FINE_LOCATION and triggers GPS fetch if granted. */
    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) getMyLocation();
                else           showToast(R.string.location_permission_denied);
            });

    // ══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    /** Inflates the layout, sets up all components, and triggers initial GPS location fetch. */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_map, container, false);

        initViews(rootView);
        setupToolbar();
        setupMapView();
        setupRecyclerView();
        setupListeners(rootView);
        getMyLocation();

        return rootView;
    }

    /** Resumes the OSMDroid map tile rendering. */
    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    /** Pauses the OSMDroid map tile rendering. */
    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION & SETUP
    // ══════════════════════════════════════════════════════════════════════════

    /** Binds all view references and initializes the fused location client. */
    private void initViews(View rootView) {
        mapView               = rootView.findViewById(R.id.osm_map);
        etSearch              = rootView.findViewById(R.id.etSearch);
        tvLocationName        = rootView.findViewById(R.id.tvLocationName);
        rvSuggestions         = rootView.findViewById(R.id.rvSuggestions);
        shimmerLayout         = rootView.findViewById(R.id.shimmerLocation);
        fusedLocationClient   = LocationServices.getFusedLocationProviderClient(requireActivity());

        Typeface tf = getFont();

        etSearch.setTypeface(tf);
        tvLocationName.setTypeface(tf);
    }

    /** Applies the current app language and sets the activity title for this screen. */
    private void setupToolbar() {
        if (getActivity() != null) {
            ((MainActivity) getActivity()).applyCurrentLanguage();
            getActivity().setTitle(R.string.map_title);
        }
    }

    /**
     * Configures OSMDroid with the OpenStreetMap tile source, enables multi-touch,
     * sets the initial zoom and center, and adds a tap listener overlay.
     */
    private void setupMapView() {
        LocaleHelper.setLocale(requireContext(), getLanguage());

        Configuration.getInstance().load(requireContext(),
                requireContext().getSharedPreferences("osmdroid", 0));
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());
        Configuration.getInstance().setOsmdroidBasePath(requireContext().getCacheDir());
        Configuration.getInstance().setOsmdroidTileCache(requireContext().getCacheDir());

        mapView.setMultiTouchControls(true);
        mapView.setTileSource(new XYTileSource(
                "Mapnik", 0, 19, 256, ".png",
                new String[]{"https://tile.openstreetmap.org/"}
        ));

        IMapController controller = mapView.getController();
        controller.setZoom(10.0);
        controller.setCenter(DEFAULT_POINT);

        mapView.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint point) { updateMarker(point); return true; }
            @Override public boolean longPressHelper(GeoPoint point)          { return false; }
        }));
    }

    /**
     * Sets up the suggestion RecyclerView and its adapter.
     * On item selection, updates the search field, moves the map, and shows the location name.
     */
    private void setupRecyclerView() {
        rvSuggestions.setLayoutManager(new LinearLayoutManager(getContext()));
        suggestionAdapter = new LocationSuggestionAdapter(new ArrayList<>(), suggestion -> {
            etSearch.setText(suggestion.displayName);
            rvSuggestions.setVisibility(View.GONE);

            GeoPoint point = new GeoPoint(suggestion.lat, suggestion.lon);
            updateMarker(point);
            mapView.getController().setZoom(16.0);
            mapView.getController().animateTo(point);
            tvLocationName.setText(suggestion.displayName);
        });
        rvSuggestions.setAdapter(suggestionAdapter);
    }

    /**
     * Wires all button click listeners, the search TextWatcher (fires after 2+ chars),
     * and a map touch listener that hides the suggestions list.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void setupListeners(View rootView) {
        rootView.findViewById(R.id.btnCancel)
                .setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        rootView.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            if (selectedPoint != null && listener != null) {
                listener.onLocationSelected(tvLocationName.getText().toString());
                requireActivity().getSupportFragmentManager().popBackStack();
            } else {
                showToast(R.string.select_location_prompt);
            }
        });

        rootView.findViewById(R.id.btnMyLocation).setOnClickListener(v -> getMyLocation());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 2) fetchSuggestions(s.toString().trim());
                else                rvSuggestions.setVisibility(View.GONE);
            }
        });

        mapView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) v.performClick();
            rvSuggestions.setVisibility(View.GONE);
            return false;
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAP MARKER
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Removes the previous marker, places a new draggable marker at the given point,
     * updates {@link #selectedPoint}, and triggers reverse geocoding.
     */
    private void updateMarker(GeoPoint point) {
        if (currentMarker != null) mapView.getOverlays().remove(currentMarker);

        currentMarker = new Marker(mapView);
        currentMarker.setPosition(point);
        currentMarker.setTitle(getString(R.string.selected_location));
        currentMarker.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_marker));
        currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        currentMarker.setDraggable(true);

        currentMarker.setOnMarkerDragListener(new Marker.OnMarkerDragListener() {
            @Override public void onMarkerDragStart(Marker marker) {}
            @Override public void onMarkerDrag(Marker marker)      { selectedPoint = marker.getPosition(); }
            @Override public void onMarkerDragEnd(Marker marker) {
                selectedPoint = marker.getPosition();
                fetchLocationName(selectedPoint);
            }
        });

        mapView.getOverlays().add(currentMarker);
        mapView.invalidate();
        selectedPoint = point;
        fetchLocationName(point);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOCATION SERVICES
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Requests the device's current GPS location using the FusedLocationProviderClient.
     * Requests the ACCESS_FINE_LOCATION permission first if not already granted.
     * Shows a Toast if the GPS is off or the location cannot be retrieved.
     */
    @SuppressLint("MissingPermission")
    private void getMyLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build();

        LocationSettingsRequest settingsRequest = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .build();

        LocationServices.getSettingsClient(requireActivity())
                .checkLocationSettings(settingsRequest)
                .addOnSuccessListener(response ->
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                .addOnSuccessListener(location -> {
                                    if (location != null) {
                                        GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
                                        updateMarker(point);
                                        mapView.getController().setZoom(16.0);
                                        mapView.getController().animateTo(point);
                                    } else {
                                        showToast(R.string.gps_unavailable);
                                    }
                                })
                                .addOnFailureListener(e -> showToast(R.string.failed_get_location))
                )
                .addOnFailureListener(e -> showToast(R.string.turn_on_gps_prompt));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NOMINATIM API CALLS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Fetches location autocomplete suggestions from Nominatim for the given query string.
     * Results are posted to the adapter on the main thread. Hides the list on failure or empty result.
     */
    private void fetchSuggestions(String query) {
        rvSuggestions.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                String url = String.format(Locale.US,
                        "https://nominatim.openstreetmap.org/search?format=json&accept-language=%s&q=%s&limit=5",
                        getLanguage(), query.replace(" ", "+"));

                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "AlphaApp/1.0")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) return;

                    JSONArray arr = new JSONArray(response.body().string());
                    List<LocationSuggestion> newList = new ArrayList<>();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        double lat = obj.optDouble("lat", Double.NaN);
                        double lon = obj.optDouble("lon", Double.NaN);
                        if (Double.isNaN(lat) || Double.isNaN(lon)) continue;

                        newList.add(new LocationSuggestion(
                                obj.optString("display_name", getString(R.string.unknown_location_api)),
                                lat, lon));
                    }

                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        suggestionAdapter.updateData(newList);
                        rvSuggestions.setVisibility(newList.isEmpty() ? View.GONE : View.VISIBLE);
                    });
                }

            } catch (Exception e) {
                mainHandler.post(() -> rvSuggestions.setVisibility(View.GONE));
            }
        }).start();
    }

    /**
     * Starts a debounced (300 ms) reverse geocode lookup for the given point.
     * Shows a shimmer while loading. Checks the in-memory cache before making a network call.
     * Falls back to Photon if Nominatim returns no result.
     */
    private void fetchLocationName(GeoPoint point) {
        shimmerLayout.setVisibility(View.VISIBLE);
        shimmerLayout.startShimmer();

        String key = point.getLatitude() + "," + point.getLongitude();
        long   now = System.currentTimeMillis();

        // Return cached result if still valid
        CacheEntry cached = locationCache.get(key);
        if (cached != null && (now - cached.timestamp) < CACHE_DURATION) {
            tvLocationName.setText(cached.address);
            shimmerLayout.stopShimmer();
            return;
        }

        // Debounce: cancel any pending reverse lookup
        if (reverseRunnable != null) mainHandler.removeCallbacks(reverseRunnable);

        reverseRunnable = () -> new Thread(() -> {
            String address = reverseNominatim(point);
            if (address == null || address.isEmpty()) address = reversePhoton(point);
            if (address == null || address.isEmpty()) address = getString(R.string.unknown_location);
            else locationCache.put(key, new CacheEntry(address, now));

            final String finalAddress = address;
            mainHandler.post(() -> {
                tvLocationName.setText(finalAddress);
                shimmerLayout.stopShimmer();
            });
        }).start();

        mainHandler.postDelayed(reverseRunnable, 300);
    }

    /**
     * Calls the Nominatim reverse geocode endpoint and assembles a formatted address string.
     * @return the formatted address, or null on failure.
     */
    private String reverseNominatim(GeoPoint point) {
        try {
            String url = String.format(Locale.US,
                    "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%f&lon=%f&zoom=18&addressdetails=1",
                    point.getLatitude(), point.getLongitude());

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "AlphaApp/1.0")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) return null;

                JSONObject obj  = new JSONObject(response.body().string());
                JSONObject addr = obj.optJSONObject("address");
                if (addr == null) return obj.optString("display_name", null);

                String house    = addr.optString("house_number", "");
                String road     = addr.optString("road", "");
                String suburb   = addr.optString("suburb", addr.optString("village", ""));
                String city     = addr.optString("city", addr.optString("town", addr.optString("county", "")));
                String state    = addr.optString("state", "");
                String province = addr.optString("province", "");
                String postcode = addr.optString("postcode", "");
                String country  = addr.optString("country", "");

                return formatAddress(house, road, suburb, city, state, province, postcode, country);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Calls the Photon reverse geocode endpoint as a fallback to Nominatim.
     * @return the formatted address, or null on failure.
     */
    private String reversePhoton(GeoPoint point) {
        try {
            String url = String.format(Locale.US,
                    "https://photon.komoot.io/reverse?lat=%f&lon=%f",
                    point.getLatitude(), point.getLongitude());

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "AlphaApp/1.0")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) return null;

                JSONObject root     = new JSONObject(response.body().string());
                JSONArray  features = root.optJSONArray("features");
                if (features == null || features.length() == 0) return null;

                JSONObject props = features.getJSONObject(0).getJSONObject("properties");

                String road     = props.optString("street", "");
                String suburb   = props.optString("suburb", "");
                String city     = props.optString("city", "");
                String state    = props.optString("state", "");
                String country  = props.optString("country", "");
                String postcode = props.optString("postcode", "");

                return formatAddress(road, suburb, city, state, postcode, country);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Joins non-empty address parts into a single comma-separated string.
     * @param parts varargs of address components (nulls and empty strings are skipped).
     * @return the assembled address string.
     */
    private String formatAddress(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(p);
            }
        }
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ══════════════════════════════════════════════════════════════════════════

    /** Returns the user's saved language code, defaulting to "en" if unset. */
    private String getLanguage() {
        String lang = UserSession.getInstance().getLanguage();
        return (lang == null || lang.isEmpty()) ? "en" : lang;
    }

    /** Shows a short Toast using the given string resource ID. */
    private void showToast(int res) {
        Toast.makeText(getContext(), res, Toast.LENGTH_SHORT).show();
    }
}