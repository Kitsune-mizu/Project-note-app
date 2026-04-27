package com.android.kitsune.ui.map;

// ---------- IMPORTS ----------

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

// ---------- ADAPTER CLASS DEFINITION ----------

public class LocationSuggestionAdapter extends RecyclerView.Adapter<LocationSuggestionAdapter.ViewHolder> {

    // ---------- INTERFACES ----------

    public interface OnItemClickListener {
        void onItemClick(LocationSuggestion suggestion);
    }

    // ---------- INSTANCE VARIABLES ----------

    private List<LocationSuggestion> suggestions;
    private final OnItemClickListener listener;

    // ---------- CONSTRUCTOR ----------

    public LocationSuggestionAdapter(List<LocationSuggestion> suggestions, OnItemClickListener listener) {
        this.suggestions = suggestions;
        this.listener = listener;
    }

    // ---------- RECYCLER VIEW METHODS ----------

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
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
        return suggestions != null ? suggestions.size() : 0;
    }

    // ---------- DATA UPDATE METHOD ----------

    public void updateData(List<LocationSuggestion> newSuggestions) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return suggestions.size();
            }

            @Override
            public int getNewListSize() {
                return newSuggestions.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return suggestions.get(oldItemPosition).displayName
                        .equals(newSuggestions.get(newItemPosition).displayName);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                LocationSuggestion oldItem = suggestions.get(oldItemPosition);
                LocationSuggestion newItem = newSuggestions.get(newItemPosition);
                return oldItem.displayName.equals(newItem.displayName)
                        && oldItem.lat == newItem.lat
                        && oldItem.lon == newItem.lon;
            }
        });

        this.suggestions = newSuggestions;
        diffResult.dispatchUpdatesTo(this);
    }

    // ---------- VIEWHOLDER CLASS DEFINITION ----------

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }
}