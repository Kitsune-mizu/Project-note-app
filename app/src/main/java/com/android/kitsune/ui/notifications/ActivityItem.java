package com.android.kitsune.ui.notifications;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class ActivityItem implements Parcelable {

    // ─── Variables ───────────────────────────────────────────────────────────

    private final int titleResId;
    private final int descriptionResId;
    private final long timestamp;
    private final int iconRes;
    private final int color;
    private final String userId;


    // ─── Constructor ─────────────────────────────────────────────────────────

    public ActivityItem(int titleResId, int descriptionResId, long timestamp,
                        int iconRes, int color, String userId) {
        this.titleResId = titleResId;
        this.descriptionResId = descriptionResId;
        this.timestamp = timestamp;
        this.iconRes = iconRes;
        this.color = color;
        this.userId = userId;
    }


    // ─── Getters ─────────────────────────────────────────────────────────────

    public int getTitleResId() {
        return titleResId;
    }

    public int getDescriptionResId() {
        return descriptionResId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getIconRes() {
        return iconRes;
    }

    public int getColor() {
        return color;
    }

    public String getUserId() {
        return userId;
    }


    // ─── Equality & Hashing ──────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof ActivityItem that)) {
            return false;
        }

        return timestamp == that.timestamp
                && iconRes == that.iconRes
                && color == that.color
                && titleResId == that.titleResId
                && descriptionResId == that.descriptionResId;
    }

    @Override
    public int hashCode() {
        int result = titleResId;
        result = 31 * result + descriptionResId;
        result = 31 * result + Long.hashCode(timestamp);
        result = 31 * result + iconRes;
        result = 31 * result + color;
        return result;
    }


    // ─── Parcelable Implementation ───────────────────────────────────────────

    protected ActivityItem(Parcel in) {
        titleResId = in.readInt();
        descriptionResId = in.readInt();
        timestamp = in.readLong();
        iconRes = in.readInt();
        color = in.readInt();
        userId = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(titleResId);
        dest.writeInt(descriptionResId);
        dest.writeLong(timestamp);
        dest.writeInt(iconRes);
        dest.writeInt(color);
        dest.writeString(userId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ActivityItem> CREATOR = new Creator<>() {
        @Override
        public ActivityItem createFromParcel(Parcel in) {
            return new ActivityItem(in);
        }

        @Override
        public ActivityItem[] newArray(int size) {
            return new ActivityItem[size];
        }
    };
}