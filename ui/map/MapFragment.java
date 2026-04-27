package com.android.kitsune.ui.map;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.android.kitsune.MainActivity;
import com.android.kitsune.R;
import com.android.kitsune.data.session.UserSession;
import com.android.kitsune.utils.LocaleHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MapFragment extends Fragment {

    private MapView mapView;
    private EditText etSearch;
    private TextView tvLocationName;
    private RecyclerView rvSuggestions;
    private GeoPoint selectedPoint;
    private Marker currentMarker;
    private FusedLocationProviderClient fusedLocationClient;
    private final OkHttpClient client = new OkHttpClient();
    private LocationSuggestionAdapter suggestionAdapter;
    private final List<LocationSuggestion> suggestionList = new ArrayList<>();
    private OnLocationSelectedListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnLocationSelectedListener {
        void onLocationSelected(String location);
    }

    public void setOnLocationSelectedListener(OnLocationSelectedListener listener) {
        this.listener = listener;
    }

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) getMyLocation();
                else Toast.makeText(getContext(), R.string.location_permission_denied, Toast.LENGTH_SHORT).show();
            });

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_map, container, false);

        // Set toolbar / activity title
        if (getActivity() != null) {
            ((MainActivity) getActivity()).applyCurrentLanguage();
            getActivity().setTitle(R.string.map_title);
        }

        mapView = rootView.findViewById(R.id.osm_map);
        etSearch = rootView.findViewById(R.id.etSearch);
        tvLocationName = rootView.findViewById(R.id.tvLocationName);
        rvSuggestions = rootView.findViewById(R.id.rvSuggestions);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        setupMapView();
        setupRecyclerView();
        setupListeners(rootView);

        getMyLocation();

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    private void setupMapView() {
        String languageCode = UserSession.getInstance().getLanguage();
        if (languageCode == null || languageCode.isEmpty()) languageCode = "en";

        LocaleHelper.setLocale(requireContext(), languageCode);

        // Load OSMdroid config
        Configuration.getInstance().load(requireContext(), requireContext().getSharedPreferences("osmdroid", 0));
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        mapView.setMultiTouchControls(true);

        // Mapnik tiles
        ITileSource tileSource = new XYTileSource(
                "Mapnik",
                0, 19, 256, ".png",
                new String[]{"https://tile.openstreetmap.org/{z}/{x}/{y}.png"}
        );
        mapView.setTileSource(tileSource);

        IMapController mapController = mapView.getController();
        mapController.setZoom(10.0);
        mapController.setCenter(new GeoPoint(-6.2088, 106.8456)); // Jakarta default

        MapEventsOverlay eventsOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint point) {
                updateMarker(point);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint point) {
                return false;
            }
        });
        mapView.getOverlays().add(eventsOverlay);
    }

    private void setupRecyclerView() {
        rvSuggestions.setLayoutManager(new LinearLayoutManager(getContext()));
        suggestionAdapter = new LocationSuggestionAdapter(suggestionList, suggestion -> {
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

    @SuppressLint("ClickableViewAccessibility")
    private void setupListeners(View rootView) {
        MaterialButton btnCancel = rootView.findViewById(R.id.btnCancel);
        MaterialButton btnConfirm = rootView.findViewById(R.id.btnConfirm);
        MaterialButton btnMyLocation = rootView.findViewById(R.id.btnMyLocation);

        btnCancel.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        btnConfirm.setOnClickListener(v -> {
            if (selectedPoint != null && listener != null) {
                listener.onLocationSelected(tvLocationName.getText().toString());
                requireActivity().getSupportFragmentManager().popBackStack();
            } else {
                Toast.makeText(getContext(), R.string.select_location_prompt, Toast.LENGTH_SHORT).show();
            }
        });
        btnMyLocation.setOnClickListener(v -> getMyLocation());

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.length() > 2) fetchSuggestions(query);
                else rvSuggestions.setVisibility(View.GONE);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        mapView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) v.performClick();
            if (rvSuggestions.getVisibility() == View.VISIBLE) rvSuggestions.setVisibility(View.GONE);
            return false;
        });
    }

    private void updateMarker(GeoPoint point) {
        if (currentMarker != null) mapView.getOverlays().remove(currentMarker);

        Marker marker = new Marker(mapView);
        marker.setPosition(point);
        marker.setTitle(getString(R.string.selected_location));
        marker.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_marker));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setDraggable(true);

        marker.setOnMarkerDragListener(new Marker.OnMarkerDragListener() {
            @Override public void onMarkerDragStart(Marker marker) {}
            @Override public void onMarkerDrag(Marker marker) { selectedPoint = marker.getPosition(); }
            @Override public void onMarkerDragEnd(Marker marker) {
                selectedPoint = marker.getPosition();
                fetchLocationName(selectedPoint);
            }
        });

        mapView.getOverlays().add(marker);
        mapView.invalidate();
        currentMarker = marker;
        selectedPoint = point;
        fetchLocationName(point);
    }

    private void fetchSuggestions(String query) {
        rvSuggestions.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                String languageCode = UserSession.getInstance().getLanguage();
                if (languageCode == null || languageCode.isEmpty()) languageCode = "en";

                String url = String.format(Locale.US,
                        "https://nominatim.openstreetmap.org/search?format=json&q=%s&limit=5&accept-language=%s",
                        query.replace(" ", "+"),
                        languageCode);

                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "AlphaApp/1.0 (alpha@example.com)")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String json = response.body().string();
                        JSONArray array = new JSONArray(json);

                        List<LocationSuggestion> newSuggestions = new ArrayList<>();
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject obj = array.getJSONObject(i);
                            double lat = obj.getDouble("lat");
                            double lon = obj.getDouble("lon");
                            String name = obj.optString("display_name", "Unknown");
                            newSuggestions.add(new LocationSuggestion(name, lat, lon));
                        }

                        mainHandler.post(() -> {
                            if (!newSuggestions.isEmpty()) {
                                suggestionAdapter.updateData(newSuggestions);
                            } else {
                                rvSuggestions.setVisibility(View.GONE);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() -> rvSuggestions.setVisibility(View.GONE));
            }
        }).start();
    }

    private void fetchLocationName(GeoPoint point) {
        tvLocationName.setText(R.string.loading_location);
        new Thread(() -> {
            try {
                String languageCode = UserSession.getInstance().getLanguage();
                if (languageCode == null || languageCode.isEmpty()) languageCode = "en";

                String url = String.format(Locale.US,
                        "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%f&lon=%f&accept-language=%s",
                        point.getLatitude(),
                        point.getLongitude(),
                        languageCode);

                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "AlphaApp/1.0 (alpha@example.com)")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String json = response.body().string();
                        JSONObject obj = new JSONObject(json);
                        String displayName = obj.optString("display_name", getString(R.string.unknown_location));

                        mainHandler.post(() -> tvLocationName.setText(displayName));
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() -> tvLocationName.setText(getString(R.string.failed_load_location)));
            }
        }).start();
    }

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

        SettingsClient settingsClient = LocationServices.getSettingsClient(requireActivity());
        settingsClient.checkLocationSettings(settingsRequest)
                .addOnSuccessListener(settingsResponse ->
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                .addOnSuccessListener(location -> {
                                    if (location != null) {
                                        GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
                                        updateMarker(point);
                                        mapView.getController().setZoom(16.0);
                                        mapView.getController().animateTo(point);
                                    } else {
                                        Toast.makeText(getContext(), R.string.gps_unavailable, Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .addOnFailureListener(e -> Toast.makeText(getContext(), R.string.failed_get_location, Toast.LENGTH_SHORT).show())
                )
                .addOnFailureListener(e -> Toast.makeText(getContext(), R.string.turn_on_gps_prompt, Toast.LENGTH_SHORT).show());
    }
}
