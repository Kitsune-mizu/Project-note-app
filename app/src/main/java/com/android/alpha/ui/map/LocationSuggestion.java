package com.android.alpha.ui.map;

/**
 * Model class representing a location suggestion returned from a search query.
 * Automatically splits the display name into a main label and secondary detail on construction.
 */
public class LocationSuggestion {

    // ─── FIELDS ────────────────────────────────────────────────────────────────
    public String displayName;
    public double lat;
    public double lon;
    public String mainText;
    public String secondaryText;

    // ══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a new LocationSuggestion and parses the display name into
     * {@link #mainText} (before the first comma) and {@link #secondaryText} (the rest).
     * @param displayName the full location label (e.g. "Bandung, West Java, Indonesia").
     * @param lat         the latitude coordinate.
     * @param lon         the longitude coordinate.
     */
    public LocationSuggestion(String displayName, double lat, double lon) {
        this.displayName = displayName;
        this.lat         = lat;
        this.lon         = lon;
        parseDisplayName(displayName);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Splits the display name at the first comma.
     * The part before the comma becomes {@link #mainText};
     * the remainder (if any) becomes {@link #secondaryText}.
     */
    private void parseDisplayName(String displayName) {
        String[] parts  = displayName.split(",", 2);
        mainText        = parts[0].trim();
        secondaryText   = parts.length > 1 ? parts[1].trim() : "";
    }
}