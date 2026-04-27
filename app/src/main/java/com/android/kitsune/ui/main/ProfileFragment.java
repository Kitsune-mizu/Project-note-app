package com.android.kitsune.ui.main;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.airbnb.lottie.LottieAnimationView;
import com.android.kitsune.R;
import com.android.kitsune.base.BaseActivity;
import com.android.kitsune.data.session.UserSession;
import com.android.kitsune.ui.common.Refreshable;
import com.android.kitsune.ui.notifications.ActivityItem;
import com.android.kitsune.ui.map.MapFragment;
import com.android.kitsune.utils.DialogUtils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.yalantis.ucrop.UCrop;

import org.json.JSONObject;

import java.io.File;
import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Locale;

public class ProfileFragment extends Fragment implements
        MainActivity.ToolbarTitleProvider,
        Refreshable {

    // ─── Constants ───────────────────────────────────────────────────────────

    private static final String TAG = "ProfileFragment";
    private static final int SHIMMER_DELAY_MS = 1200;
    private static final int REFRESH_DELAY_MS = 800;

    // ─── Views ───────────────────────────────────────────────────────────────

    private ImageView ivProfile;
    private LottieAnimationView lottieProfile;
    private com.google.android.material.card.MaterialCardView layoutAvatarContainer;

    private LinearLayout layoutEditName, layoutEditEmail, layoutEditBirthday, layoutEditLocation;

    private TextView tvProfileName, tvProfileEmail, tvFullName, tvEmail, tvBirthday, tvLocation;
    private ShimmerFrameLayout shimmerLayout;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View scrollViewProfile;

    // ─── State ───────────────────────────────────────────────────────────────

    private boolean isFirstLoad = true;

    // ─── Launchers ───────────────────────────────────────────────────────────

    private ActivityResultLauncher<Intent> profilePicLauncher;

    private final ActivityResultLauncher<Intent> cropLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> handleCropResult(result.getResultCode(), result.getData())
    );


    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_profile, container, false);

        initializeViews(v);
        setupLaunchers();
        setupListeners();
        setupSwipeRefresh();
        handleFirstLoad();

        return v;
    }

    @Override
    public int getToolbarTitleRes() {
        return R.string.menu_title_profile;
    }


    // ─── Initialization ──────────────────────────────────────────────────────

    private void initializeViews(View v) {
        swipeRefreshLayout      = v.findViewById(R.id.swipeRefreshLayout);
        shimmerLayout           = v.findViewById(R.id.shimmerLayout);
        scrollViewProfile       = v.findViewById(R.id.scrollViewProfile);
        ivProfile               = v.findViewById(R.id.ivProfile);
        lottieProfile           = v.findViewById(R.id.lottieProfile);
        layoutAvatarContainer   = v.findViewById(R.id.layoutAvatarContainer);

        layoutEditName          = v.findViewById(R.id.layoutEditName);
        layoutEditEmail         = v.findViewById(R.id.layoutEditEmail);
        layoutEditBirthday      = v.findViewById(R.id.layoutEditBirthday);
        layoutEditLocation      = v.findViewById(R.id.layoutEditLocation);

        tvProfileName           = v.findViewById(R.id.tvProfileName);
        tvProfileEmail          = v.findViewById(R.id.tvProfileEmail);
        tvFullName              = v.findViewById(R.id.tvFullName);
        tvEmail                 = v.findViewById(R.id.tvEmail);
        tvBirthday              = v.findViewById(R.id.tvBirthday);
        tvLocation              = v.findViewById(R.id.tvLocation);

        ((BaseActivity) requireActivity()).applyFont(
                tvProfileName, tvProfileEmail, tvFullName,
                tvEmail, tvBirthday, tvLocation
        );
    }

    private void setupLaunchers() {
        profilePicLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) startCrop(uri);
                    }
                }
        );
    }

    private void setupListeners() {
        layoutAvatarContainer.setOnClickListener(v -> showProfilePictureBottomSheet());

        View.OnClickListener editName     = v -> editText("username", tvFullName);
        View.OnClickListener editEmail    = v -> editText("email", tvEmail);
        View.OnClickListener editBirthday = v -> pickDate();
        View.OnClickListener editLocation = v -> openMapPicker();

        layoutEditName.setOnClickListener(editName);
        layoutEditEmail.setOnClickListener(editEmail);
        layoutEditBirthday.setOnClickListener(editBirthday);
        layoutEditLocation.setOnClickListener(editLocation);
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(this::onRefreshRequested);
    }

    private void handleFirstLoad() {
        if (!isFirstLoad) {
            refreshProfileData();
            return;
        }

        isFirstLoad = false;
        shimmerLayout.setVisibility(View.VISIBLE);
        shimmerLayout.startShimmer();
        scrollViewProfile.setVisibility(View.GONE);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            refreshProfileData();
            shimmerLayout.stopShimmer();
            shimmerLayout.setVisibility(View.GONE);
            scrollViewProfile.setVisibility(View.VISIBLE);
        }, SHIMMER_DELAY_MS);
    }


    // ─── BottomSheet: Change Photo ───────────────────────────────────────────

    private void showProfilePictureBottomSheet() {
        DialogUtils.showConfirmDialog(
                requireContext(),
                getString(R.string.bs_profile_picture_title),
                getString(R.string.bs_profile_picture_message),
                getString(R.string.bs_action_change_picture),
                getString(R.string.action_cancel),
                this::selectPhoto,
                null
        );
    }


    // ─── Profile Data ────────────────────────────────────────────────────────

    public void refreshProfileData() {
        UserSession session = UserSession.getInstance();
        JSONObject json = session.loadCurrentProfileJson();

        String username = session.getUsername();
        String fullName = (username != null && !username.isEmpty())
                ? username
                : getString(R.string.default_username);

        if (json != null) {
            fullName = json.optString("username", fullName);
            tvEmail.setText(json.optString("email", fullName + "@example.com"));
            tvBirthday.setText(json.optString("birthday", getString(R.string.default_birthday)));
            tvLocation.setText(json.optString("location", getString(R.string.default_location)));
            loadProfileImage(json.optString("photoPath"));
        }

        tvProfileName.setText(fullName);
        tvFullName.setText(fullName);
        tvProfileEmail.setText(tvEmail.getText());
    }

    private void loadProfileImage(String path) {
        if (path == null || path.trim().isEmpty()) {
            showLottie();
            return;
        }

        String realPath = Uri.parse(path).getPath();
        if (realPath == null) {
            Log.w(TAG, "Invalid photo path: " + path);
            showLottie();
            return;
        }

        File file = new File(realPath);
        if (!file.exists()) {
            Log.w(TAG, "Profile image missing: " + file.getAbsolutePath());
            ivProfile.setImageResource(R.drawable.ic_person);
            showPhoto();
            return;
        }

        // BYPASS GLIDE CACHE: Ensure the new image is always loaded even if the name is similar
        Glide.with(this)
                .load(file)
                .circleCrop()
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(ivProfile);

        showPhoto();
    }

    private void showLottie() {
        ivProfile.setVisibility(View.GONE);
        lottieProfile.setVisibility(View.VISIBLE);
        lottieProfile.setAnimation(R.raw.profile_animation);
        lottieProfile.playAnimation();
    }

    private void showPhoto() {
        lottieProfile.setVisibility(View.GONE);
        ivProfile.setVisibility(View.VISIBLE);
    }


    // ─── Photo Selection & Crop ──────────────────────────────────────────────

    private void selectPhoto() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*");

        // EXPLICIT FORMATS: Restrict picker to JPG, PNG, and GIF
        String[] mimeTypes = {"image/jpeg", "image/png", "image/gif"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        profilePicLauncher.launch(intent);
    }

    private void startCrop(Uri sourceUri) {
        File dir = new File(requireContext().getFilesDir(), "profile");
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Cannot create profile directory");
            return;
        }

        // PREVENT LEAK: Delete all old photo files in the profile folder before cropping
        // Added boolean check to fix Android Studio warning "Result of 'File.delete()' is ignored"
        File[] oldFiles = dir.listFiles();
        if (oldFiles != null) {
            for (File oldFile : oldFiles) {
                if (!oldFile.delete()) {
                    Log.w(TAG, "Failed to delete old file: " + oldFile.getName());
                }
            }
        }

        // Create a new filename using a timestamp to force Glide to load the new file
        String uniqueFileName = "profile_" + System.currentTimeMillis() + ".jpg";
        Uri destinationUri = Uri.fromFile(new File(dir, uniqueFileName));

        UCrop.Options options = new UCrop.Options();
        options.setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG);
        options.setCompressionQuality(90);
        options.setCircleDimmedLayer(true);

        cropLauncher.launch(
                UCrop.of(sourceUri, destinationUri)
                        .withAspectRatio(1, 1)
                        .withOptions(options)
                        .getIntent(requireContext())
        );
    }

    private void handleCropResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri resultUri = UCrop.getOutput(data);
            if (resultUri == null) return;

            try {
                saveProfileSafely("photoPath", resultUri.toString());

                // BYPASS GLIDE CACHE: Here as well
                Glide.with(this)
                        .load(resultUri)
                        .circleCrop()
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(ivProfile);

                showPhoto();
                notifyChange();
            } catch (Exception e) {
                Log.e(TAG, "Error saving cropped image", e);
                Toast.makeText(requireContext(), R.string.error_saving_data, Toast.LENGTH_SHORT).show();
            }

        } else if (resultCode == UCrop.RESULT_ERROR) {
            Throwable err = (data != null) ? UCrop.getError(data) : new Exception("Unknown crop error");
            Log.e(TAG, "Crop error", err);
            Toast.makeText(requireContext(), R.string.error_crop_image, Toast.LENGTH_SHORT).show();
        }
    }


    // ─── Profile Editing ─────────────────────────────────────────────────────

    private void saveProfileSafely(String key, String value) {
        try {
            UserSession.getInstance().saveProfileData(key, value);
        } catch (Exception e) {
            Log.e(TAG, "Error saving profile data [" + key + "]", e);
            Toast.makeText(requireContext(), R.string.error_saving_data, Toast.LENGTH_SHORT).show();
        }
    }

    private void editText(String key, TextView target) {
        String label = switch (key) {
            case "username" -> getString(R.string.field_full_name);
            case "email"    -> getString(R.string.field_email);
            case "birthday" -> getString(R.string.field_birthday);
            case "location" -> getString(R.string.field_location);
            default         -> key;
        };

        DialogUtils.showInputDialog(
                requireContext(),
                getString(R.string.edit_dialog_title, label),
                getString(R.string.edit_dialog_hint, label),
                target.getText().toString(),
                getString(R.string.action_save),
                getString(R.string.action_cancel),
                newText -> {
                    target.setText(newText);
                    saveProfileSafely(key, newText);
                    notifyChange();
                }
        );
    }

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(requireContext(),
                (view, year, month, day) -> {
                    String date = String.format(Locale.getDefault(), "%s %d, %d",
                            new DateFormatSymbols().getMonths()[month], day, year);
                    tvBirthday.setText(date);
                    saveProfileSafely("birthday", date);
                    notifyChange();
                },
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void openMapPicker() {
        MapFragment map = new MapFragment();
        map.setOnLocationSelectedListener(loc -> {
            tvLocation.setText(loc);
            saveProfileSafely("location", loc);
            notifyChange();
        });

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, map)
                .addToBackStack(null)
                .commit();
    }


    // ─── Notify & Refresh ────────────────────────────────────────────────────

    private void notifyChange() {
        UserSession session = UserSession.getInstance();
        String username = session.getUsername();
        if (username == null || username.isEmpty()) return;

        UserSession.UserData data = session.getUserData(username);
        if (data == null) return;

        BaseActivity activity = (BaseActivity) requireActivity();

        session.addActivity(new ActivityItem(
                R.string.activity_profile_updated_title,
                R.string.activity_profile_updated_desc,
                System.currentTimeMillis(),
                R.drawable.ic_person,
                activity.getAttrColor(R.attr.text_color),
                data.userId
        ));

        session.notifyProfileUpdated();

        if (activity instanceof MainActivity) {
            ((MainActivity) activity).showNotificationBadge();
        }
    }

    @Override
    public void onRefreshRequested() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            refreshProfileData();
            Toast.makeText(requireContext(), R.string.toast_profile_updated, Toast.LENGTH_SHORT).show();
            swipeRefreshLayout.setRefreshing(false);
        }, REFRESH_DELAY_MS);
    }
}