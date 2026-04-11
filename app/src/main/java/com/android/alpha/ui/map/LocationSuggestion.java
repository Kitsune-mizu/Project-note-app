package com.android.alpha.ui.map;

public class LocationSuggestion {

    // ─── Variables ───────────────────────────────────────────────────────────

    public String displayName;
    public double lat;
    public double lon;
    public String mainText;
    public String secondaryText;


    // ─── Constructor ─────────────────────────────────────────────────────────

    public LocationSuggestion(String displayName, double lat, double lon) {
        this.displayName = displayName;
        this.lat = lat;
        this.lon = lon;
        parseDisplayName(displayName);
    }


    // ─── Utilities ───────────────────────────────────────────────────────────

    private void parseDisplayName(String displayName) {
        String[] parts = displayName.split(",", 2);
        mainText = parts[0].trim();

        if (parts.length > 1) {
            secondaryText = parts[1].trim();
        } else {
            secondaryText = "";
        }
    }
}