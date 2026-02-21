package com.android.alpha.ui.map;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter that displays a list of {@link LocationSuggestion} items
 * and notifies a click listener when the user selects one.
 */
public class LocationSuggestionAdapter extends RecyclerView.Adapter<LocationSuggestionAdapter.ViewHolder> {

    // ─── INTERFACE ─────────────────────────────────────────────────────────────

    /** Callback triggered when a suggestion item is tapped. */
    public interface OnItemClickListener {
        void onItemClick(LocationSuggestion suggestion);
    }

    // ─── FIELDS ────────────────────────────────────────────────────────────────
    private final List<LocationSuggestion> suggestions = new ArrayList<>();
    private final OnItemClickListener      listener;

    // ══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates the adapter with an optional initial list of suggestions.
     * @param initialSuggestions the starting data set; ignored if null.
     * @param listener           click callback for item selection.
     */
    public LocationSuggestionAdapter(List<LocationSuggestion> initialSuggestions, OnItemClickListener listener) {
        if (initialSuggestions != null) suggestions.addAll(initialSuggestions);
        this.listener = listener;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADAPTER OVERRIDES
    // ══════════════════════════════════════════════════════════════════════════

    /** Inflates the simple list item layout and wraps it in a ViewHolder. */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ViewHolder(view);
    }

    /** Binds the suggestion's display name to the TextView and wires the item click. */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocationSuggestion suggestion = suggestions.get(position);
        holder.textView.setText(suggestion.displayName);
        holder.itemView.setOnClickListener(v -> listener.onItemClick(suggestion));
    }

    /** Returns the total number of suggestions currently in the list. */
    @Override
    public int getItemCount() { return suggestions.size(); }

    // ══════════════════════════════════════════════════════════════════════════
    // DATA MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Replaces the current suggestion list with the given data and refreshes the RecyclerView.
     * Does nothing if the new list is null.
     * @param newSuggestions the replacement data set.
     */
    @SuppressLint("NotifyDataSetChanged")
    public void updateData(List<LocationSuggestion> newSuggestions) {
        if (newSuggestions == null) return;
        suggestions.clear();
        suggestions.addAll(newSuggestions);
        notifyDataSetChanged();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VIEW HOLDER
    // ══════════════════════════════════════════════════════════════════════════

    /** Holds a reference to the TextView used to display a single suggestion's name. */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }
}