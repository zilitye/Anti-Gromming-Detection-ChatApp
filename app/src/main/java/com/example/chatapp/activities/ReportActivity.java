package com.example.chatapp.activities;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.TextView;

import com.example.chatapp.R;
import com.example.chatapp.databinding.ActivityReportBinding;
import com.example.chatapp.models.ChatMessage;
import com.example.chatapp.utilities.Constants;
import com.example.chatapp.utilities.PreferenceManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class ReportActivity extends BaseActivity {

    private ActivityReportBinding binding;
    private FirebaseFirestore database;
    private PreferenceManager preferenceManager;
    private String receiverId;
    private int riskScore;
    private int currentStep = 1;
    private String selectedCategory = "";
    private List<ChatMessage> flaggedMessages;
    private ChatMessage selectedMessage;

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
        flaggedMessages = new ArrayList<>();
        updateStepUI();
    }

    private void updateStepUI() {
        if (currentStep == 1) {
            binding.layoutStep1.setVisibility(View.VISIBLE);
            binding.layoutStep2.setVisibility(View.GONE);
            binding.buttonAction.setText(R.string.next);
            binding.textTitle.setText(R.string.report_incident);
        } else {
            binding.layoutStep1.setVisibility(View.GONE);
            binding.layoutStep2.setVisibility(View.VISIBLE);
            binding.buttonAction.setText(R.string.submit_report);
            binding.textTitle.setText(R.string.report_details);
        }
    }

    private void loadData() {
        receiverId = getIntent().getStringExtra(Constants.KEY_USER_ID);
        String receiverName = getIntent().getStringExtra(Constants.KEY_NAME);
        riskScore = getIntent().getIntExtra(Constants.KEY_RISK_SCORE, 0);
        String reason = getIntent().getStringExtra(Constants.KEY_RISK_LEVEL);
        String messageText = getIntent().getStringExtra(Constants.KEY_MESSAGE);
        
        if (receiverName != null) {
            binding.textReportTarget.setText(getString(R.string.report_user, receiverName));
        }

        if (messageText != null && !messageText.trim().isEmpty()) {
            selectedMessage = new ChatMessage();
            selectedMessage.message = messageText;
            selectedMessage.flaggedReason = reason;
            displaySelectedMessage();
        } else {
            binding.layoutFlaggedInfo.setVisibility(View.GONE);
        }

        fetchFlaggedMessages();
    }

    private void fetchFlaggedMessages() {
        if (receiverId == null) return;

        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receiverId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_IS_FLAGGED, true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    flaggedMessages.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        ChatMessage message = new ChatMessage();
                        message.id = document.getId();
                        message.message = document.getString(Constants.KEY_MESSAGE);
                        message.flaggedReason = document.getString(Constants.KEY_FLAGGED_REASON);
                        flaggedMessages.add(message);
                    }
                    
                    if (!flaggedMessages.isEmpty()) {
                        binding.layoutMessageSelector.setVisibility(View.VISIBLE);
                        if (selectedMessage == null) {
                            selectedMessage = flaggedMessages.get(0);
                            displaySelectedMessage();
                        }
                    }
                });
    }

    private void displaySelectedMessage() {
        if (selectedMessage != null) {
            binding.layoutFlaggedInfo.setVisibility(View.VISIBLE);
            binding.textMessage.setText(selectedMessage.message);
            if (selectedMessage.flaggedReason != null && !selectedMessage.flaggedReason.trim().isEmpty()) {
                binding.textReason.setText(String.format("Reason: %s", selectedMessage.flaggedReason));
                binding.textReason.setVisibility(View.VISIBLE);
            } else {
                binding.textReason.setVisibility(View.GONE);
            }
        }
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> handleBackAction());
        
        binding.buttonAction.setOnClickListener(v -> {
            if (currentStep == 1) {
                int selectedId = binding.radioGroupCategories.getCheckedRadioButtonId();
                if (selectedId == -1) {
                    showToast("Please select a category");
                    return;
                }
                RadioButton selectedRadio = findViewById(selectedId);
                selectedCategory = selectedRadio.getText().toString();
                currentStep = 2;
                updateStepUI();
            } else {
                submitReport();
            }
        });

        binding.layoutMessageSelector.setOnClickListener(v -> showMessageSelectionDialog());
        
        binding.inputFeedback.setOnFocusChangeListener((v, hasFocus) -> binding.inputFeedback.setActivated(hasFocus));
    }

    private void showMessageSelectionDialog() {
        if (flaggedMessages.isEmpty()) return;

        View popupView = LayoutInflater.from(this).inflate(R.layout.layout_popup_menu, null);
        LinearLayout menuContainer = (LinearLayout) popupView;
        menuContainer.removeAllViews();
        menuContainer.setPadding(0, (int) getResources().getDimension(com.intuit.sdp.R.dimen._4sdp), 0, (int) getResources().getDimension(com.intuit.sdp.R.dimen._4sdp));

        int width = binding.layoutMessageSelector.getWidth();
        
        PopupWindow popupWindow = new PopupWindow(
                popupView,
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );

        for (int i = 0; i < flaggedMessages.size(); i++) {
            ChatMessage message = flaggedMessages.get(i);
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_message_dropdown, menuContainer, false);
            TextView textMessage = itemView.findViewById(R.id.textMessage);
            textMessage.setText(message.message);
            
            final int index = i;
            itemView.setOnClickListener(v -> {
                selectedMessage = flaggedMessages.get(index);
                displaySelectedMessage();
                popupWindow.dismiss();
            });
            
            menuContainer.addView(itemView);

            // Add separator if not the last item
            if (i < flaggedMessages.size() - 1) {
                View separator = new View(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                params.setMargins((int) getResources().getDimension(com.intuit.sdp.R.dimen._12sdp), 0,
                        (int) getResources().getDimension(com.intuit.sdp.R.dimen._12sdp), 0);
                separator.setLayoutParams(params);
                separator.setBackgroundColor(getColor(R.color.separator));
                menuContainer.addView(separator);
            }
        }

        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(8f);
        popupWindow.showAsDropDown(binding.layoutMessageSelector, 0, 0);
    }

    private void handleBackAction() {
        if (currentStep == 2) {
            currentStep = 1;
            updateStepUI();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        handleBackAction();
    }

    private void submitReport() {
        HashMap<String, Object> incident = new HashMap<>();
        incident.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        incident.put(Constants.KEY_RECEIVER_ID, receiverId);
        incident.put(Constants.KEY_MESSAGE, selectedMessage != null ? selectedMessage.message : "");
        incident.put(Constants.KEY_TIMESTAMP, new Date());
        incident.put(Constants.KEY_IS_FLAGGED, true);
        incident.put(Constants.KEY_RISK_SCORE, riskScore);
        incident.put("reportCategory", selectedCategory);
        
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
