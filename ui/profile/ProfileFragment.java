package com.android.kitsune.ui.profile;

// ---------- IMPORTS ----------

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
import android.widget.DatePicker;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.android.kitsune.ui.home.ActivityItem;
import com.android.kitsune.MainActivity;
import com.android.kitsune.ui.map.MapFragment;
import com.android.kitsune.R;
import com.android.kitsune.ui.common.Refreshable;
import com.android.kitsune.data.session.UserSession;
import com.android.kitsune.utils.DialogUtils; // Assuming DialogUtils is external
import com.android.kitsune.utils.ShimmerHelper;
import com.bumptech.glide.Glide;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;

// ---------- FRAGMENT CLASS DEFINITION ----------

public class ProfileFragment extends Fragment implements Refreshable {

    // --- UI VIEWS ---
    private ImageView ivProfile, ivBackground;
    private LottieAnimationView lottieProfile;
    private FloatingActionButton fabEditProfile, fabEditBg;
    private ImageButton ibEditName, ibEditEmail, ibEditBirthday, ibEditLocation;
    private TextView tvProfileName, tvProfileEmail, tvFullName, tvEmail, tvBirthday, tvLocation;
    private ShimmerFrameLayout shimmerLayout;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View scrollViewProfile;

    // --- DATA & UTILITIES ---
    private ActivityResultLauncher<Intent> profilePicLauncher;
    private ActivityResultLauncher<Intent> bgPicLauncher;

    private static final String TAG = "ProfileFragment";

    // --- LIFECYCLE METHODS ---

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_profile, container, false);
        initializeViews(v);

        setupLaunchers();
        setupListeners();
        setupSwipeRefresh();

        // Show Shimmer effect on initial load
        ShimmerHelper.show(shimmerLayout, scrollViewProfile);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            refreshProfileData();
            ShimmerHelper.hide(shimmerLayout, scrollViewProfile);
        }, 1200);

        return v;
    }

    // --- INITIALIZATION & SETUP ---

    private void initializeViews(View v) {
        swipeRefreshLayout = v.findViewById(R.id.swipeRefreshLayout);
        shimmerLayout = v.findViewById(R.id.shimmerLayout);
        scrollViewProfile = v.findViewById(R.id.scrollViewProfile);

        ivProfile = v.findViewById(R.id.ivProfile);
        ivBackground = v.findViewById(R.id.ivBackground);
        lottieProfile = v.findViewById(R.id.lottieProfile);
        fabEditProfile = v.findViewById(R.id.fabEditProfile);
        fabEditBg = v.findViewById(R.id.fabEditBg);
        ibEditName = v.findViewById(R.id.ibEditName);
        ibEditEmail = v.findViewById(R.id.ibEditEmail);
        ibEditBirthday = v.findViewById(R.id.ibEditBirthday);
        ibEditLocation = v.findViewById(R.id.ibEditLocation);
        tvProfileName = v.findViewById(R.id.tvProfileName);
        tvProfileEmail = v.findViewById(R.id.tvProfileEmail);
        tvFullName = v.findViewById(R.id.tvFullName);
        tvEmail = v.findViewById(R.id.tvEmail);
        tvBirthday = v.findViewById(R.id.tvBirthday);
        tvLocation = v.findViewById(R.id.tvLocation);
    }

    private void setupLaunchers() {
        profilePicLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                r -> {
                    if (r.getResultCode() == Activity.RESULT_OK && r.getData() != null) {
                        Uri img = r.getData().getData();
                        if (img != null) {
                            try {
                                UserSession.getInstance().saveProfileData("photoPath", img.toString());
                                refreshProfileData();
                                notifyChange();
                                UserSession.getInstance().notifyProfileUpdated();
                            } catch (Exception e) {
                                Log.e(TAG, "Error saving profile picture: " + e.getMessage(), e);
                                Toast.makeText(requireContext(), R.string.error_saving_data, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });

        bgPicLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                r -> {
                    if (r.getResultCode() == Activity.RESULT_OK && r.getData() != null) {
                        Uri img = r.getData().getData();
                        if (img != null) {
                            try {
                                UserSession.getInstance().saveProfileData("backgroundPath", img.toString());
                                Glide.with(this).load(img).into(ivBackground);
                                notifyChange();
                            } catch (Exception e) {
                                Log.e(TAG, "Error saving background picture: " + e.getMessage(), e);
                                Toast.makeText(requireContext(), R.string.error_saving_data, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

    private void setupListeners() {
        fabEditProfile.setOnClickListener(v -> selectImage(profilePicLauncher));
        fabEditBg.setOnClickListener(v -> selectImage(bgPicLauncher));

        // Use key "username" for the full/display name field
        ibEditName.setOnClickListener(v -> editText("username", tvFullName));
        ibEditEmail.setOnClickListener(v -> editText("email", tvEmail));
        ibEditBirthday.setOnClickListener(v -> pickDate());
        ibEditLocation.setOnClickListener(v -> openMapPicker());
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(this::onRefreshRequested);
    }

    // --- PROFILE DATA MANAGEMENT ---

    public void refreshProfileData() {
        String sessionUsername = UserSession.getInstance().getUsername();
        String defaultUsername = getString(R.string.default_username);
        String currentUsername = (sessionUsername != null && !sessionUsername.isEmpty()) ? sessionUsername : defaultUsername;

        String defaultEmail = currentUsername + "@example.com";
        String defaultBirthday = getString(R.string.default_birthday);
        String defaultLocation = getString(R.string.default_location);

        String fullName = currentUsername;
        String email = defaultEmail;
        String birthday = defaultBirthday;
        String loc = defaultLocation;
        String profilePic = "";
        String bgPic = "";

        // Check if there's an active session to load personalized data
        if (sessionUsername != null && !sessionUsername.isEmpty()) {
            try {
                // Load current user profile JSON via UserSession
                JSONObject profileJson = UserSession.getInstance().loadCurrentProfileJson();

                if (profileJson != null) {
                    fullName = profileJson.optString("username", sessionUsername);
                    email = profileJson.optString("email", defaultEmail);
                    birthday = profileJson.optString("birthday", defaultBirthday);
                    loc = profileJson.optString("location", defaultLocation);
                    profilePic = profileJson.optString("photoPath", "");
                    bgPic = profileJson.optString("backgroundPath", "");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading personalized profile data: " + e.getMessage(), e);
            }
        } else {
            Log.w(TAG, "No active user session, displaying default profile data.");
        }

        // Update UI
        tvProfileName.setText(fullName);
        tvProfileEmail.setText(email);
        tvFullName.setText(fullName);
        tvEmail.setText(email);
        tvBirthday.setText(birthday);
        tvLocation.setText(loc);

        // Logic display Lottie/Image (Profile Pic)
        if (!profilePic.isEmpty()) {
            lottieProfile.setVisibility(View.GONE);
            ivProfile.setVisibility(View.VISIBLE);
            Glide.with(this).load(profilePic).circleCrop().into(ivProfile);
        } else {
            ivProfile.setVisibility(View.GONE);
            lottieProfile.setVisibility(View.VISIBLE);
            lottieProfile.setAnimation(R.raw.profile_animation);
            lottieProfile.setRepeatCount(LottieDrawable.INFINITE);
            lottieProfile.playAnimation();
        }

        // Logic display Background Pic
        if (!bgPic.isEmpty()) {
            Glide.with(this).load(bgPic).into(ivBackground);
        } else {
            // Default background color/placeholder
            ivBackground.setImageResource(R.color.md_theme_light_surface);
        }
    }

    // --- EDIT UTILITIES ---

    private void selectImage(ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        launcher.launch(intent);
    }

    private void editText(String key, TextView target) {
        String currentValue = target.getText().toString();

        // Map internal key to display string for dialogs
        String titleKey = key.equals("username") ? getString(R.string.field_full_name) :
                key.equals("email") ? getString(R.string.field_email) :
                        key.replace("_", " "); // Fallback for other keys

        String dialogTitle = getString(R.string.edit_dialog_title, titleKey);
        String dialogHint = getString(R.string.edit_dialog_hint, titleKey);

        DialogUtils.showInputDialog(
                requireContext(),
                dialogTitle,
                dialogHint,
                currentValue,
                getString(R.string.action_save),
                getString(R.string.action_cancel),
                newText -> {
                    target.setText(newText);
                    try {
                        // Save using UserSession
                        UserSession.getInstance().saveProfileData(key, newText);
                        notifyChange();
                        UserSession.getInstance().notifyProfileUpdated();
                    } catch (Exception e) {
                        Log.e(TAG, "Error saving profile key: " + key + ": " + e.getMessage(), e);
                        Toast.makeText(requireContext(), R.string.error_saving_data, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        DatePickerDialog dpd = new DatePickerDialog(requireContext(),
                (DatePicker view, int y, int m, int d) -> {
                    String date = String.format(Locale.getDefault(), "%s %d, %d", getMonth(m), d, y);

                    tvBirthday.setText(date);
                    try {
                        // Save using UserSession
                        UserSession.getInstance().saveProfileData("birthday", date);
                        notifyChange();
                    } catch (Exception e) {
                        Log.e(TAG, "Error saving birthday: " + e.getMessage(), e);
                        Toast.makeText(requireContext(), R.string.error_saving_data, Toast.LENGTH_SHORT).show();
                    }
                },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dpd.show();
    }

    private String getMonth(int m) {
        return new java.text.DateFormatSymbols().getMonths()[m];
    }

    private void openMapPicker() {
        MapFragment map = new MapFragment();
        map.setOnLocationSelectedListener(loc -> {
            tvLocation.setText(loc);
            try {
                // Save using UserSession
                UserSession.getInstance().saveProfileData("location", loc);
                notifyChange();
            } catch (Exception e) {
                Log.e(TAG, "Error saving location: " + e.getMessage(), e);
                Toast.makeText(requireContext(), R.string.error_saving_data, Toast.LENGTH_SHORT).show();
            }
        });
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, map).addToBackStack(null).commit();
    }

    // --- NOTIFICATION & ACTIVITY LOG ---

    private void notifyChange() {
        String username = UserSession.getInstance().getUsername();
        if (username == null || username.isEmpty()) return;

        UserSession.UserData userData = UserSession.getInstance().getUserData(username);
        if (userData == null) return;

        String userId = userData.userId;

        UserSession.getInstance().addActivity(new ActivityItem(
                R.string.activity_profile_updated_title,     // titleResId
                R.string.activity_profile_updated_desc,      // descriptionResId
                System.currentTimeMillis(),                  // timestamp
                R.drawable.ic_person,                        // icon
                ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary), // color
                userId                                       // userId
        ));

        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).showNotificationBadge();
    }


    // --- REFRESHABLE IMPLEMENTATION ---

    @Override
    public void onRefreshRequested() {
        ShimmerHelper.show(shimmerLayout, scrollViewProfile);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                refreshProfileData();
                Toast.makeText(requireContext(), R.string.toast_profile_updated, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Error while refreshing profile data", e);
            } finally {
                ShimmerHelper.hide(shimmerLayout, scrollViewProfile);
                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }
        }, 1200);
    }
}