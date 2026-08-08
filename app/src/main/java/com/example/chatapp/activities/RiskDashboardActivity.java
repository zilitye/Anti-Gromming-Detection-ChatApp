package com.example.chatapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.example.chatapp.R;
import com.example.chatapp.databinding.ActivityRiskDashboardBinding;
import com.example.chatapp.utilities.Constants;
import com.example.chatapp.utilities.GroomingDetector;
import com.example.chatapp.utilities.PreferenceManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RiskDashboardActivity extends BaseActivity {

    private ActivityRiskDashboardBinding binding;
    private FirebaseFirestore database;
    private PreferenceManager preferenceManager;
    private String receiverId;
    private String conversationContext = "";
    private int currentRiskScore = 0;
    private String currentDetectedWords = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRiskDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyEdgeToEdge(binding.main);
        init();
        setListeners();
        fetchConversationAndAnalyze();
    }

    private void init() {
        // Warm up the on-device NLP grooming model in case it isn't already
        // loaded, so analysis below can use it instead of the keyword fallback.
        GroomingDetector.initialize(getApplicationContext());
        database = FirebaseFirestore.getInstance();
        preferenceManager = new PreferenceManager(getApplicationContext());
        receiverId = getIntent().getStringExtra(Constants.KEY_USER_ID);
        String receiverName = getIntent().getStringExtra(Constants.KEY_NAME);
        binding.textReceiverName.setText(getString(R.string.chat_with, receiverName));
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.buttonSafetyHub.setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), SafetyHubActivity.class);
            intent.putExtra("conversation", conversationContext);
            startActivity(intent);
        });
        binding.buttonReport.setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), ReportActivity.class);
            intent.putExtra(Constants.KEY_USER_ID, receiverId);
            intent.putExtra(Constants.KEY_RISK_SCORE, currentRiskScore);
            intent.putExtra(Constants.KEY_RISK_LEVEL, "Detected patterns: " + currentDetectedWords);
            intent.putExtra(Constants.KEY_MESSAGE, conversationContext);
            startActivity(intent);
        });
    }

    private void fetchConversationAndAnalyze() {
        binding.progressBar.setVisibility(View.VISIBLE);
        String currentUserId = preferenceManager.getString(Constants.KEY_USER_ID);

        // Fetch all messages sent by the receiver to the current user
        // Reverted to simple query (no orderBy) to avoid Firestore Index issues
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receiverId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, currentUserId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<String> messages = new ArrayList<>();
                        StringBuilder contextBuilder = new StringBuilder();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String message = document.getString(Constants.KEY_MESSAGE);
                            messages.add(message);
                            contextBuilder.append(message).append("\n");
                        }
                        conversationContext = contextBuilder.toString();

                        // Analysis (NLP model inference, when ready) runs on a
                        // background thread; results are delivered back here.
                        GroomingDetector.analyzeBatchAsync(messages, (results, totalScore) -> {
                            int flaggedCount = 0;
                            StringBuilder detectedWords = new StringBuilder();
                            for (GroomingDetector.DetectionResult result : results) {
                                if (result.riskLevel != GroomingDetector.RiskLevel.SAFE) {
                                    flaggedCount++;
                                    if (detectedWords.length() > 0) detectedWords.append(", ");
                                    detectedWords.append(result.reason
                                            .replace("Suspicious patterns detected: ", "")
                                            .replace("High risk patterns detected: ", "")
                                            .replace("Semantic match: ", ""));
                                }
                            }

                            currentRiskScore = totalScore;
                            currentDetectedWords = detectedWords.toString();
                            updateUI(totalScore, flaggedCount, detectedWords.toString());
                            binding.progressBar.setVisibility(View.GONE);
                        });
                    } else {
                        showToast("Failed to fetch messages for analysis.");
                        binding.progressBar.setVisibility(View.GONE);
                    }
                });
    }

    private void updateUI(int totalScore, int flaggedCount, String detectedWords) {
        int normalizedScore = Math.min(100, totalScore);
        binding.riskProgressBar.setProgress(normalizedScore);
        binding.textRiskScore.setText(String.format(Locale.getDefault(), "%d%%", normalizedScore));

        if (normalizedScore >= 50) {
            binding.textRiskLevel.setText("HIGH RISK");
            binding.textRiskLevel.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.red));
            binding.textSummary.setText("This conversation has multiple high-risk indicators (" + flaggedCount + " flagged messages). We strongly recommend ending this interaction.");
        } else if (normalizedScore >= 20) {
            binding.textRiskLevel.setText("MEDIUM RISK");
            binding.textRiskLevel.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.macos_traffic_yellow));
            binding.textSummary.setText("Some suspicious patterns were detected (" + flaggedCount + " messages). Please be cautious and avoid sharing private information.");
        } else {
            binding.textRiskLevel.setText("SAFE");
            binding.textRiskLevel.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.green));
            binding.textSummary.setText("No significant grooming patterns detected so far. Stay safe!");
        }

        if (flaggedCount > 0) {
            binding.textSummary.append("\n\nDetected patterns: " + detectedWords);
        }
    }
}
