package com.example.chatapp.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.PopupMenu;

import com.example.chatapp.R;
import com.example.chatapp.databinding.ActivityProfileBinding;
import com.example.chatapp.models.User;
import com.example.chatapp.utilities.Constants;
import com.example.chatapp.utilities.PreferenceManager;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends BaseActivity {

    private ActivityProfileBinding binding;
    private User user;
    private FirebaseFirestore database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyEdgeToEdge(binding.getRoot());
        init();
        loadUserDetails();
        setListeners();
        fetchSafetyScore();
    }

    private void init() {
        user = (User) getIntent().getSerializableExtra(Constants.KEY_USER);
        database = FirebaseFirestore.getInstance();
    }

    private void loadUserDetails() {
        binding.textName.setText(user.name);
        binding.textUsername.setText(user.email);
        if (user.image != null) {
            byte[] bytes = Base64.decode(user.image, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            binding.imageProfile.setImageBitmap(bitmap);
        }

        // Fetch full user details to ensure email and other data are present
        database.collection(Constants.KEY_COLLECTION_USERS)
                .document(user.id)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        user.email = documentSnapshot.getString(Constants.KEY_EMAIL);
                        binding.textUsername.setText(user.email);
                        String name = documentSnapshot.getString(Constants.KEY_NAME);
                        if (name != null) {
                            user.name = name;
                            binding.textName.setText(name);
                        }
                        if (user.image == null) {
                            user.image = documentSnapshot.getString(Constants.KEY_IMAGE);
                            if (user.image != null) {
                                byte[] bytes = Base64.decode(user.image, Base64.DEFAULT);
                                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                binding.imageProfile.setImageBitmap(bitmap);
                            }
                        }
                    }
                });
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        
        binding.layoutActionShield.setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), SafetyHubActivity.class);
            intent.putExtra("conversation", "Chatting with " + user.name);
            startActivity(intent);
        });

        binding.layoutActionAlert.setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), ReportActivity.class);
            intent.putExtra(Constants.KEY_USER_ID, user.id);
            startActivity(intent);
        });

        binding.layoutActionBlock.setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.block_user)
                    .setMessage("Are you sure you want to block this user?")
                    .setPositiveButton("Block", (dialog, which) -> {
                        showToast("User blocked");
                        // Implementation for blocking user could go here
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        binding.layoutActionMore.setOnClickListener(this::showMoreMenu);
    }

    private void showMoreMenu(View view) {
        View popupView = getLayoutInflater().inflate(R.layout.layout_profile_more_menu, null);
        android.widget.PopupWindow popupWindow = new android.widget.PopupWindow(
                popupView,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );

        popupWindow.setElevation(10);
        
        popupView.findViewById(R.id.menuMute).setOnClickListener(v -> {
            showToast(getString(R.string.mute_conversation));
            popupWindow.dismiss();
        });

        popupView.findViewById(R.id.menuSearch).setOnClickListener(v -> {
            showToast(getString(R.string.search_conversation));
            popupWindow.dismiss();
        });

        popupView.findViewById(R.id.menuDelete).setOnClickListener(v -> {
            showToast(getString(R.string.delete_conversation));
            popupWindow.dismiss();
        });

        // Calculate offset to align right edge of popup with right edge of anchor
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int xOffset = view.getWidth() - popupView.getMeasuredWidth();
        popupWindow.showAsDropDown(view, xOffset, 0);
    }

    private void fetchSafetyScore() {
        PreferenceManager preferenceManager = new PreferenceManager(getApplicationContext());
        String currentUserId = preferenceManager.getString(Constants.KEY_USER_ID);

        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, user.id)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, currentUserId)
                .whereEqualTo(Constants.KEY_IS_FLAGGED, true)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        int totalScore = 0;
                        int count = task.getResult().size();
                        for (var doc : task.getResult().getDocuments()) {
                            Long score = doc.getLong(Constants.KEY_RISK_SCORE);
                            if (score != null) totalScore += score.intValue();
                        }

                        if (count > 0) {
                            int avg = totalScore / count;
                            binding.textSafetyScore.setText(getString(R.string.risk_level_format, avg));
                            binding.progressBarSafety.setProgress(avg);
                            if (avg > 70) {
                                binding.textSafetyStatus.setText(R.string.high_risk);
                                binding.textSafetyStatus.setTextColor(getColor(R.color.red));
                                binding.progressBarSafety.setProgressTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.red)));
                            } else if (avg > 30) {
                                binding.textSafetyStatus.setText(R.string.warning);
                                binding.textSafetyStatus.setTextColor(getColor(R.color.macos_traffic_yellow));
                                binding.progressBarSafety.setProgressTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.macos_traffic_yellow)));
                            } else {
                                binding.textSafetyStatus.setText(R.string.secure);
                                binding.textSafetyStatus.setTextColor(getColor(R.color.green));
                                binding.progressBarSafety.setProgressTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.green)));
                            }
                        } else {
                            binding.textSafetyScore.setText(R.string.no_risk_detected);
                            binding.textSafetyStatus.setText(R.string.secure);
                            binding.textSafetyStatus.setTextColor(getColor(R.color.green));
                            binding.progressBarSafety.setProgress(0);
                            binding.progressBarSafety.setProgressTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.green)));
                        }
                    } else {
                        binding.textSafetyScore.setText(R.string.safety_score_unavailable);
                    }
                });
    }
}
