package com.example.chatapp.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.chatapp.R;
import com.example.chatapp.adapters.ChatAdapter;
import com.example.chatapp.databinding.ActivityChatBinding;
import com.example.chatapp.models.ChatMessage;
import com.example.chatapp.models.User;
import com.example.chatapp.network.ApiClient;
import com.example.chatapp.network.ApiService;
import com.example.chatapp.utilities.AccessTokenManager;
import com.example.chatapp.utilities.Constants;
import com.example.chatapp.utilities.ErrorUtils;
import com.example.chatapp.utilities.GroomingDetector;
import com.example.chatapp.utilities.HttpException;
import com.example.chatapp.utilities.PreferenceManager;
import android.widget.Toast;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.pipeline.Expression;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends BaseActivity {

    private ActivityChatBinding binding;
    private User receiverUser;
    private List<ChatMessage> chatMessages;
    private ChatAdapter chatAdapter;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore database;
    private String conversionId = null;
    private Boolean isReceiverAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyEdgeToEdge(binding.main);
        setListeners();
        loadReceiverDetails();
        init();
        listenMessages();

    }

    private void init(){
        // Warm up the on-device NLP grooming model as early as possible so
        // it's ready by the time the user sends a message.
        GroomingDetector.initialize(getApplicationContext());
        preferenceManager = new PreferenceManager(getApplicationContext());
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(
                chatMessages,
                getBitmapFromEncodedString(receiverUser.image),
                preferenceManager.getString(Constants.KEY_USER_ID)
        );
        binding.chatRecyclerView.setAdapter(chatAdapter);
        database = FirebaseFirestore.getInstance();

    }
    private Bitmap getBitmapFromEncodedString(String encodedImage){
        if(encodedImage != null){
            byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }else{
            return null;
        }

    }

    private void sendMessage() {
        String messageText = binding.inputMessage.getText().toString();
        if (messageText.trim().isEmpty()) {
            return;
        }

        binding.progressBar.setVisibility(android.view.View.VISIBLE);
        GroomingDetector.analyze(messageText, new GroomingDetector.DetectionCallback() {
            @Override
            public void onResult(GroomingDetector.DetectionResult result) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(android.view.View.GONE);
                    if (result.riskLevel == GroomingDetector.RiskLevel.HIGH) {
                        showHighRiskAlert(result);
                    } else if (result.riskLevel == GroomingDetector.RiskLevel.MEDIUM) {
                        showMediumRiskWarning(result);
                    } else {
                        performSendMessage(messageText, false, 0, null, null);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(android.view.View.GONE);
                    // Fallback to sending anyway or show error
                    performSendMessage(messageText, false, 0, null, null);
                });
            }
        });
    }

    private void showMediumRiskWarning(GroomingDetector.DetectionResult result) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.safety_warning)
                .setMessage(getString(R.string.grooming_warning_message, result.reason))
                .setPositiveButton(R.string.send_anyway, (dialog, which) -> performSendMessage(binding.inputMessage.getText().toString(), true, result.score, result.riskLevel.name(), result.reason))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showHighRiskAlert(GroomingDetector.DetectionResult result) {
        String blockedMessage = binding.inputMessage.getText().toString();
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.security_alert)
                .setMessage(R.string.high_risk_alert_message)
                .setPositiveButton(R.string.report_get_help, (dialog, which) -> {
                    logHighRiskIncident(result);

                    // Build Conversation Context - ONLY include sender's (current user) messages
                    String currentUserId = preferenceManager.getString(Constants.KEY_USER_ID);
                    List<String> senderMessages = new ArrayList<>();
                    StringBuilder contextBuilder = new StringBuilder();
                    for (ChatMessage chatMessage : chatMessages) {
                        if (chatMessage.senderId.equals(currentUserId)) {
                            senderMessages.add(chatMessage.message);
                            contextBuilder.append(chatMessage.message).append("\n");
                        }
                    }

                    // Re-scoring history runs the (possibly NLP-based) detector on
                    // each past message, so it's done off the main thread.
                    GroomingDetector.analyzeBatchAsync(senderMessages, (results, totalScore) -> {
                        Intent intent = new Intent(getApplicationContext(), ReportActivity.class);
                        intent.putExtra(Constants.KEY_USER_ID, receiverUser.id);
                        intent.putExtra(Constants.KEY_RISK_SCORE, totalScore + result.score);
                        intent.putExtra(Constants.KEY_RISK_LEVEL, result.reason);
                        intent.putExtra(Constants.KEY_MESSAGE, contextBuilder.toString() + "Blocked: " + blockedMessage);
                        startActivity(intent);
                    });
                })
                .setNegativeButton(R.string.close, (dialog, which) -> logHighRiskIncident(result))
                .show();
    }

    private void logHighRiskIncident(GroomingDetector.DetectionResult result) {
        HashMap<String, Object> incident = new HashMap<>();
        incident.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        incident.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
        incident.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());
        incident.put(Constants.KEY_TIMESTAMP, new Date());
        incident.put(Constants.KEY_IS_FLAGGED, true);
        incident.put(Constants.KEY_RISK_SCORE, result.score);
        incident.put(Constants.KEY_RISK_LEVEL, result.riskLevel.name());
        database.collection("flagged_incidents").add(incident);
        binding.inputMessage.setText(null);
    }

    private void performSendMessage(String messageText, boolean isFlagged, int riskScore, String riskLevel, String flaggedReason) {
        HashMap<String, Object> message = new HashMap<>();
        message.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        message.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
        message.put(Constants.KEY_MESSAGE, messageText);
        message.put(Constants.KEY_TIMESTAMP, new Date());
        message.put(Constants.KEY_IS_FLAGGED, isFlagged);
        if (isFlagged) {
            message.put(Constants.KEY_RISK_SCORE, riskScore);
            message.put(Constants.KEY_RISK_LEVEL, riskLevel);
            message.put(Constants.KEY_FLAGGED_REASON, flaggedReason);
        }

        database.collection(Constants.KEY_COLLECTION_CHAT).add(message);
        if (conversionId != null) {
            updateConversion(messageText);
        } else {
            HashMap<String, Object> conversion = new HashMap<>();
            conversion.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
            conversion.put(Constants.KEY_SENDER_NAME, preferenceManager.getString(Constants.KEY_NAME));
            conversion.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
            conversion.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
            conversion.put(Constants.KEY_RECEIVER_NAME, receiverUser.name);
            conversion.put(Constants.KEY_RECEIVER_IMAGE, receiverUser.image);
            conversion.put(Constants.KEY_LAST_MESSAGE, messageText);
            conversion.put(Constants.KEY_TIMESTAMP, new Date());
            addConversion(conversion);
        }
        if (!isReceiverAvailable) {
            try {
                if (receiverUser.token == null || receiverUser.token.trim().isEmpty()) {
                    showToast("Receiver token is missing. Notification not sent.");
                } else {
                    JSONObject data = new JSONObject();
                    data.put(Constants.KEY_USER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
                    data.put(Constants.KEY_NAME, preferenceManager.getString(Constants.KEY_NAME));
                    data.put(Constants.KEY_FCM_TOKEN, preferenceManager.getString(Constants.KEY_FCM_TOKEN));
                    data.put(Constants.KEY_MESSAGE, messageText);

                    JSONObject notification = new JSONObject();
                    notification.put("title", preferenceManager.getString(Constants.KEY_NAME));
                    notification.put("body", messageText);

                    JSONObject notificationMessage = new JSONObject();
                    notificationMessage.put(Constants.REMOTE_MSG_DATA, data);
                    notificationMessage.put("notification", notification);
                    notificationMessage.put(Constants.REMOTE_MSG_TOKEN, receiverUser.token);

                    JSONObject body = new JSONObject();
                    body.put(Constants.REMOTE_MSG_MESSAGE, notificationMessage);

                    sendNotification(body.toString());
                }
            } catch (Exception exception) {
                showToast(exception.getMessage());
            }
        }
        binding.inputMessage.setText(null);
    }

    // Removed local showToast to use BaseActivity's version

// -----------------------------------------------------------------------
// Replace the existing sendNotification() method in ChatActivity.java
// with this version. Everything else in the file stays the same.
// -----------------------------------------------------------------------

    private String getFCMErrorHint(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);
            if (json.has("error")) {
                JSONObject error = json.getJSONObject("error");
                String status = error.optString("status");

                // Try to get specific FCM error code from details
                String errorCode = "";
                if (error.has("details")) {
                    JSONArray details = error.getJSONArray("details");
                    for (int i = 0; i < details.length(); i++) {
                        JSONObject detail = details.getJSONObject(i);
                        if (detail.has("errorCode")) {
                            errorCode = detail.getString("errorCode");
                            break;
                        }
                    }
                }

                if ("UNREGISTERED".equals(errorCode)) {
                    return "The recipient has uninstalled the app or their session expired.";
                } else if ("INVALID_ARGUMENT".equals(status) || "INVALID_ARGUMENT".equals(errorCode)) {
                    return "The notification format is invalid. Please contact support.";
                } else if ("SENDER_ID_MISMATCH".equals(errorCode)) {
                    return "The app's configuration doesn't match the server. Please update the app.";
                } else if ("QUOTA_EXCEEDED".equals(errorCode)) {
                    return "Too many notifications sent. Please wait a moment.";
                } else if ("UNAVAILABLE".equals(status)) {
                    return "Notification server is temporarily busy. Try again later.";
                }

                return error.optString("message", "Unknown notification error");
            }
        } catch (Exception e) {
            return "Failed to send notification (Technical error: " + responseBody + ")";
        }
        return "Unknown error occurred";
    }

    private void sendNotification(String messageBody) {
        AccessTokenManager.getInstance(getApplicationContext()).getProjectId(new AccessTokenManager.TokenCallback() {
            @Override
            public void onToken(String projectId) {
                Constants.getRemoteMsgHeaders(getApplicationContext(), new Constants.HeadersCallback() {
                    @Override
                    public void onHeaders(HashMap<String, String> headers) {
                        // getRemoteMsgHeaders runs on a background thread, so hop back
                        // to the main thread before touching Retrofit / UI.
                        runOnUiThread(() ->
                                ApiClient.getClient().create(ApiService.class)
                                        .sendMessage(projectId, headers, messageBody)
                                        .enqueue(new Callback<String>() {
                                            @Override
                                            public void onResponse(@NonNull Call<String> call,
                                                                   @NonNull Response<String> response) {
                                                if (response.isSuccessful()) {
                                                    try {
                                                        if (response.body() != null) {
                                                            JSONObject responseJson = new JSONObject(response.body());
                                                            if (responseJson.has("name")) {
                                                                showToast("Notification sent successfully");
                                                            } else if (responseJson.has("error")) {
                                                                showToast("Failed to send notification");
                                                            }
                                                        }
                                                    } catch (JSONException e) {
                                                        e.printStackTrace();
                                                    }
                                                } else {
                                                    String cleanError = ErrorUtils.getCleanErrorMessage(response.code());
                                                    showToast(cleanError);
                                                }
                                            }

                                            @Override
                                            public void onFailure(@NonNull Call<String> call,
                                                                  @NonNull Throwable t) {
                                                showToast("Network error occurred");
                                            }
                                        })
                        );
                    }

                    @Override
                    public void onError(Exception e) {
                        runOnUiThread(() -> {
                            if (e instanceof HttpException) {
                                showToast(e.toString());
                            } else {
                                showToast("Connection failed");
                            }
                        });
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> showToast("Internal error occurred"));
            }
        });
    }
    private void listenAvailabilityOfReceiver(){
        database.collection(Constants.KEY_COLLECTION_USERS).document(
                receiverUser.id
        ).addSnapshotListener(ChatActivity.this,(value, error) -> {
            if(error != null){
                return;
            }
            if(value != null){
                if(value.getLong(Constants.KEY_AVAILABILITY)!=null){
                    int availability = Objects.requireNonNull(
                            value.getLong(Constants.KEY_AVAILABILITY)
                    ).intValue();
                    isReceiverAvailable = availability == 1;
                }
                receiverUser.token = value.getString(Constants.KEY_FCM_TOKEN);
                if(receiverUser.image == null){
                    receiverUser.image = value.getString(Constants.KEY_IMAGE);
                    chatAdapter.setReceiverProfileImage(getBitmapFromEncodedString(receiverUser.image));
                    chatAdapter.notifyItemRangeChanged(0, chatMessages.size());
                }
            }
            if(isReceiverAvailable){
                binding.textAvailability.setVisibility(View.VISIBLE);
            }else{
                binding.textAvailability.setVisibility(View.GONE);
            }

        });

    }

    private void listenMessages(){
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.id)
                .addSnapshotListener(eventListener);
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if(error != null){
            return;
        }
        if(value != null){
            for (DocumentChange documentChange : value.getDocumentChanges()){
                if(documentChange.getType() == DocumentChange.Type.ADDED){
                    String messageId = documentChange.getDocument().getId();
                    boolean isAlreadyAdded = false;
                    for (ChatMessage message : chatMessages) {
                        if (message.id != null && message.id.equals(messageId)) {
                            isAlreadyAdded = true;
                            break;
                        }
                    }
                    if (isAlreadyAdded) continue;

                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.id = messageId;
                    chatMessage.senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                    chatMessage.receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                    chatMessage.message = documentChange.getDocument().getString(Constants.KEY_MESSAGE);
                    chatMessage.dateTime = getReadableDateTime(documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP));
                    chatMessage.dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                    Boolean isFlaggedObj = documentChange.getDocument().getBoolean(Constants.KEY_IS_FLAGGED);
                    chatMessage.isFlagged = isFlaggedObj != null && isFlaggedObj;
                    chatMessage.flaggedReason = documentChange.getDocument().getString(Constants.KEY_FLAGGED_REASON);
                    chatMessages.add(chatMessage);
                }

            }
            Collections.sort(chatMessages, (obj1, obj2) -> obj1.dateObject.compareTo(obj2.dateObject));
            chatAdapter.notifyDataSetChanged();
            if (!chatMessages.isEmpty()) {
                binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
            }
            binding.chatRecyclerView.setVisibility(View.VISIBLE);
        }
        binding.progressBar.setVisibility(View.GONE);
        if(conversionId == null){
            checkForConversion();
        }
    };

    private void loadReceiverDetails(){
        receiverUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);
        binding.textName.setText(receiverUser.name);
    }

    private void setListeners(){

        binding.imageBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.layoutSend.setOnClickListener(v -> sendMessage());
        binding.imageInfo.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(getApplicationContext(), RiskDashboardActivity.class);
            intent.putExtra(Constants.KEY_USER_ID, receiverUser.id);
            intent.putExtra(Constants.KEY_NAME, receiverUser.name);
            startActivity(intent);
        });
    }

    private String getReadableDateTime(Date date){
        return new SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.getDefault()).format(date);
    }

    private void updateConversion(String message){
        DocumentReference documentReference =
                database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversionId);
        documentReference.update(
                Constants.KEY_LAST_MESSAGE, message,
                Constants.KEY_TIMESTAMP, new Date()
        );
    }

    private void addConversion(HashMap<String, Object> conversion){
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .add(conversion)
                .addOnSuccessListener(documentReference -> conversionId = documentReference.getId());
    }

    private void checkForConversion(){
        if(chatMessages.size() != 0){
            checkForConversionRemotely(
                    preferenceManager.getString(Constants.KEY_USER_ID),
                    receiverUser.id
            );
            checkForConversionRemotely(
                    receiverUser.id,
                    preferenceManager.getString(Constants.KEY_USER_ID)
            );
        }
    }

    private void checkForConversionRemotely(String senderId, String receiverId){
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(conversionOnCompleteListener);
    }

    private final OnCompleteListener<QuerySnapshot> conversionOnCompleteListener = task -> {
        if(task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size()>0){
            DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
            conversionId = documentSnapshot.getId();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        listenAvailabilityOfReceiver();
    }
}