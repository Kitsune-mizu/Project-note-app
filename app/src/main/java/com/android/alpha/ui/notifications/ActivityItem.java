package com.android.alpha.ui.notifications;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * Model data untuk satu item aktivitas yang ditampilkan di halaman notifikasi.
 * Mengimplementasikan Parcelable agar bisa dikirim antar komponen Android.
 */
public class ActivityItem implements Parcelable {

    // --- Fields ---

    // Resource ID string untuk judul dan deskripsi aktivitas
    private final int titleResId;
    private final int descriptionResId;

    // Waktu aktivitas terjadi (dalam milliseconds)
    private final long timestamp;

    // Resource ID ikon dan warna aksen item
    private final int iconRes;
    private final int color;

    // ID pengguna yang terkait dengan aktivitas ini
    private final String userId;

    // --- Constructor ---

    /**
     * @param titleResId       Resource ID judul aktivitas
     * @param descriptionResId Resource ID deskripsi aktivitas
     * @param timestamp        Waktu kejadian aktivitas (ms)
     * @param iconRes          Resource ID ikon
     * @param color            Warna aksen item
     * @param userId           ID pengguna terkait
     */
    public ActivityItem(int titleResId, int descriptionResId, long timestamp,
                        int iconRes, int color, String userId) {
        this.titleResId       = titleResId;
        this.descriptionResId = descriptionResId;
        this.timestamp        = timestamp;
        this.iconRes          = iconRes;
        this.color            = color;
        this.userId           = userId;
    }

    // --- Getters ---

    public int    getTitleResId()       { return titleResId; }
    public int    getDescriptionResId() { return descriptionResId; }
    public long   getTimestamp()        { return timestamp; }
    public int    getIconRes()          { return iconRes; }
    public int    getColor()            { return color; }
    public String getUserId()           { return userId; }

    // --- Equality & Hashing ---

    /**
     * Dua ActivityItem dianggap sama jika memiliki nilai field yang identik
     * (tidak termasuk userId, karena item yang sama bisa dimiliki user berbeda).
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActivityItem that)) return false;
        return timestamp        == that.timestamp
                && iconRes          == that.iconRes
                && color            == that.color
                && titleResId       == that.titleResId
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

    // --- Parcelable Implementation ---

    /** Baca data dari Parcel (urutan harus sama dengan writeToParcel) */
    protected ActivityItem(Parcel in) {
        titleResId       = in.readInt();
        descriptionResId = in.readInt();
        timestamp        = in.readLong();
        iconRes          = in.readInt();
        color            = in.readInt();
        userId           = in.readString();
    }

    /** Tulis semua field ke Parcel untuk dikirim antar komponen */
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
    public int describeContents() { return 0; }

    /** Creator standar Parcelable untuk membuat instance dari Parcel atau array */
    public static final Creator<ActivityItem> CREATOR = new Creator<>() {
        @Override
        public ActivityItem createFromParcel(Parcel in) { return new ActivityItem(in); }

        @Override
        public ActivityItem[] newArray(int size) { return new ActivityItem[size]; }
    };
}