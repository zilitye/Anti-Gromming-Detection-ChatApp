package com.example.chatapp.activities;

import android.os.Bundle;
import android.view.View;

import com.example.chatapp.R;
import com.example.chatapp.databinding.ActivityReportBinding;
import com.example.chatapp.utilities.Constants;
import com.example.chatapp.utilities.PreferenceManager;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;

public class ReportActivity extends BaseActivity {

    private ActivityReportBinding binding;
    private FirebaseFirestore database;
    private PreferenceManager preferenceManager;
    private String receiverId;
    private int riskScore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyEdgeToEdge(binding.main);
        init();
        setListeners();
        loadData();
    }

    private void init() {
        database = FirebaseFirestore.getInstance();
        preferenceManager = new PreferenceManager(getApplicationContext());
    }

    private void loadData() {
        receiverId = getIntent().getStringExtra(Constants.KEY_USER_ID);
        riskScore = getIntent().getIntExtra(Constants.KEY_RISK_SCORE, 0);
        String reason = getIntent().getStringExtra(Constants.KEY_RISK_LEVEL);
        String message = getIntent().getStringExtra(Constants.KEY_MESSAGE);
        
        if (reason != null && !reason.trim().isEmpty()) {
            binding.textReason.setText(reason);
            binding.layoutReason.setVisibility(View.VISIBLE);
            binding.headerContext.setText(R.string.flagged_content);
        } else {
            binding.layoutReason.setVisibility(View.GONE);
            binding.headerContext.setText(R.string.your_message);
        }
        
        binding.textMessage.setText(message);
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.buttonSubmitReport.setOnClickListener(v -> submitReport());
        
        binding.inputFeedback.setOnFocusChangeListener((v, hasFocus) -> binding.inputFeedback.setActivated(hasFocus));
    }

    private void submitReport() {
        HashMap<String, Object> incident = new HashMap<>();
        incident.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        incident.put(Constants.KEY_RECEIVER_ID, receiverId);
        incident.put(Constants.KEY_MESSAGE, binding.textMessage.getText().toString());
        incident.put(Constants.KEY_TIMESTAMP, new Date());
        incident.put(Constants.KEY_IS_FLAGGED, true);
        incident.put(Constants.KEY_RISK_SCORE, riskScore);
        
        String feedback = binding.inputFeedback.getText().toString().trim();
        if (!feedback.isEmpty()) {
            incident.put("userFeedback", feedback);
        }

        String reason = binding.textReason.getText().toString();
        incident.put(Constants.KEY_RISK_LEVEL, reason.isEmpty() ? "MANUAL" : reason);
        
        database.collection("flagged_incidents").add(incident)
                .addOnSuccessListener(documentReference -> {
                    showToast("Report submitted successfully. Thank you for keeping the community safe.");
                    finish();
                })
                .addOnFailureListener(e -> showToast("Failed to submit report. Please try again."));
    }
}
