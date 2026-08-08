package com.example.chatapp.activities;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;

import com.example.chatapp.R;
import com.example.chatapp.databinding.ActivityProfileBinding;
import com.example.chatapp.models.User;
import com.example.chatapp.utilities.Constants;
import com.example.chatapp.utilities.PreferenceManager;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

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
        binding.textUsername.setText(getString(R.string.username_format, user.name.toLowerCase().replace(" ", "")));
        if (user.image != null) {
            byte[] bytes = Base64.decode(user.image, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            binding.imageProfile.setImageBitmap(bitmap);
        }
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
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
                            if (avg > 70) {
                                binding.textSafetyStatus.setText(R.string.high_risk);
                                binding.textSafetyStatus.setTextColor(getColor(R.color.red));
                            } else if (avg > 30) {
                                binding.textSafetyStatus.setText(R.string.warning);
                                binding.textSafetyStatus.setTextColor(getColor(R.color.macos_traffic_yellow));
                            } else {
                                binding.textSafetyStatus.setText(R.string.secure);
                                binding.textSafetyStatus.setTextColor(getColor(R.color.green));
                            }
                        } else {
                            binding.textSafetyScore.setText(R.string.no_risk_detected);
                            binding.textSafetyStatus.setText(R.string.secure);
                            binding.textSafetyStatus.setTextColor(getColor(R.color.green));
                        }
                    } else {
                        binding.textSafetyScore.setText(R.string.safety_score_unavailable);
                    }
                });
    }
}
