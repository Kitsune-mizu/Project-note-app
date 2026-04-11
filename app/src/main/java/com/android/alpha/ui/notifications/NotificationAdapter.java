package com.android.alpha.ui.notifications;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.android.alpha.R;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.utils.DialogUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends ListAdapter<ActivityItem, NotificationAdapter.ViewHolder> {

    // ─── Constants ───────────────────────────────────────────────────────────

    private static final String TAG = "NotificationAdapter";


    // ─── Constructor ─────────────────────────────────────────────────────────

    public NotificationAdapter() {
        super(DIFF_CALLBACK);
        setHasStableIds(true);
    }


    // ─── Adapter Overrides ───────────────────────────────────────────────────

    @Override
    public long getItemId(int position) {
        return getItem(position).getTimestamp();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_activity, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ActivityItem item = getItem(position);
        Context context = holder.itemView.getContext();

        try {
            bindTextAndIcon(holder, context, item);
        } catch (Exception e) {
            Log.e(TAG, "onBindViewHolder error: " + e.getMessage(), e);
            applyFallbackUI(holder);
        }

        holder.itemView.setOnLongClickListener(v -> {
            DialogUtils.showConfirmDialog(
                    context,
                    context.getString(R.string.dialog_delete_notif_title),
                    context.getString(R.string.dialog_delete_notif_message),
                    context.getString(R.string.action_delete),
                    context.getString(R.string.action_cancel),
                    () -> deleteNotification(item),
                    null
            );
            return true;
        });
    }


    // ─── Binding Helpers ─────────────────────────────────────────────────────

    private void bindTextAndIcon(@NonNull ViewHolder holder, Context context, ActivityItem item) {
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.text_color, tv, true);
        int color = tv.data;

        holder.tvTime.setText(formatTimestamp(item.getTimestamp()));
        holder.tvTitle.setText(getSafeString(context, item.getTitleResId()));
        holder.tvDesc.setText(getSafeString(context, item.getDescriptionResId()));

        holder.tvTime.setTextColor(color);
        holder.tvTitle.setTextColor(color);
        holder.tvDesc.setTextColor(color);

        holder.ivIcon.setImageResource(safeIcon(context, item.getIconRes()));

        try {
            holder.ivIcon.setColorFilter(item.getColor());
        } catch (Exception ignore) {
        }
    }

    private void applyFallbackUI(ViewHolder holder) {
        holder.tvTitle.setText("");
        holder.tvDesc.setText("");
        holder.tvTime.setText("");
        holder.ivIcon.setImageResource(R.drawable.ic_notification_default);
    }


    // ─── Utility Methods ─────────────────────────────────────────────────────

    private int safeIcon(Context context, int resId) {
        if (resId == 0) {
            return R.drawable.ic_notification_default;
        }

        try {
            AppCompatResources.getDrawable(context, resId);
            return resId;
        } catch (Exception e) {
            return R.drawable.ic_notification_default;
        }
    }

    private String formatTimestamp(long timestamp) {
        Locale locale = new Locale(UserSession.getInstance().getLanguage());
        return new SimpleDateFormat("d MMM yyyy • HH:mm", locale)
                .format(new Date(timestamp));
    }

    private String getSafeString(Context context, int resId) {
        if (resId == 0) {
            return "";
        }

        try {
            return context.getString(resId);
        } catch (Resources.NotFoundException e) {
            return "";
        }
    }

    private void deleteNotification(ActivityItem item) {
        List<ActivityItem> current = new ArrayList<>(getCurrentList());
        current.remove(item);
        submitList(current);
        UserSession.getInstance().saveActivities(current);
    }


    // ─── DiffUtil Callback ───────────────────────────────────────────────────

    private static final DiffUtil.ItemCallback<ActivityItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull ActivityItem o, @NonNull ActivityItem n) {
            return o.getTimestamp() == n.getTimestamp();
        }

        @Override
        public boolean areContentsTheSame(@NonNull ActivityItem o, @NonNull ActivityItem n) {
            return o.equals(n);
        }
    };


    // ─── ViewHolder ──────────────────────────────────────────────────────────

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDesc, tvTime;
        ImageView ivIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvActivityTitle);
            tvDesc = itemView.findViewById(R.id.tvActivityDesc);
            tvTime = itemView.findViewById(R.id.tvActivityTime);
            ivIcon = itemView.findViewById(R.id.iconActivity);
        }
    }
}