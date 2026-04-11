package com.android.alpha.ui.map;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.alpha.R;

import java.util.ArrayList;
import java.util.List;

public class LocationSuggestionAdapter extends RecyclerView.Adapter<LocationSuggestionAdapter.ViewHolder> {

    // ─── Interfaces ──────────────────────────────────────────────────────────

    public interface OnItemClickListener {
        void onItemClick(LocationSuggestion suggestion);
    }


    // ─── Variables ───────────────────────────────────────────────────────────

    private final List<LocationSuggestion> suggestions = new ArrayList<>();
    private final OnItemClickListener listener;


    // ─── Constructor ─────────────────────────────────────────────────────────

    public LocationSuggestionAdapter(List<LocationSuggestion> initialSuggestions, OnItemClickListener listener) {
        if (initialSuggestions != null) {
            suggestions.addAll(initialSuggestions);
        }
        this.listener = listener;
    }


    // ─── Adapter Overrides ───────────────────────────────────────────────────

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_location, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocationSuggestion suggestion = suggestions.get(position);
        holder.textView.setText(suggestion.displayName);
        holder.itemView.setOnClickListener(v -> listener.onItemClick(suggestion));
    }

    @Override
    public int getItemCount() {
        return suggestions.size();
    }


    // ─── Data Management ─────────────────────────────────────────────────────

    @SuppressLint("NotifyDataSetChanged")
    public void updateData(List<LocationSuggestion> newSuggestions) {
        if (newSuggestions == null) {
            return;
        }
        suggestions.clear();
        suggestions.addAll(newSuggestions);
        notifyDataSetChanged();
    }


    // ─── ViewHolder ──────────────────────────────────────────────────────────

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.tvLocation);
        }
    }
}