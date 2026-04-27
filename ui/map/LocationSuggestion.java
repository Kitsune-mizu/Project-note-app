package com.android.kitsune.ui.map;

// ---------- CLASS DEFINITION ----------

public class LocationSuggestion {

    // ---------- INSTANCE VARIABLES ----------

    public String displayName;
    public double lat;
    public double lon;
    public String mainText;
    public String secondaryText;

    // ---------- CONSTRUCTOR ----------

    public LocationSuggestion(String displayName, double lat, double lon) {
        this.displayName = displayName;
        this.lat = lat;
        this.lon = lon;

        // Logic to parse displayName into mainText and secondaryText
        String[] parts = displayName.split(",", 2);
        this.mainText = parts[0].trim();
        this.secondaryText = parts.length > 1 ? parts[1].trim() : "";
    }
}