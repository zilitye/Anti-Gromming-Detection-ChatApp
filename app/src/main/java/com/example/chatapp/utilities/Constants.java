package com.example.chatapp.utilities;

import android.content.Context;

import java.util.HashMap;

public class Constants {
    public static final String KEY_COLLECTION_USERS = "users";
    public static final String KEY_NAME = "name";
    public static final String KEY_EMAIL = "email";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_PREFERENCE_NAME = "chatAppPreference";
    public static final String KEY_IS_SIGNED_IN = "isSignedIn";
    public static final String KEY_USER_ID = "userId";
    public static final String KEY_IMAGE = "image";
    public static final String KEY_FCM_TOKEN = "fcmToken";
    public static final String KEY_USER = "user";
    public static final String KEY_COLLECTION_CHAT = "chat";
    public static final String KEY_SENDER_ID = "senderId";
    public static final String KEY_RECEIVER_ID = "receiverId";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_TIMESTAMP = "timestamp";
    public static final String KEY_COLLECTION_CONVERSATIONS = "conversations";
    public static final String KEY_SENDER_NAME = "senderName";
    public static final String KEY_RECEIVER_NAME = "receiverName";
    public static final String KEY_SENDER_IMAGE = "senderImage";
    public static final String KEY_RECEIVER_IMAGE = "receiverImage";
    public static final String KEY_LAST_MESSAGE = "lastMessage";
    public static final String KEY_AVAILABILITY = "availability";
    public static final String KEY_RISK_SCORE = "riskScore";
    public static final String KEY_IS_FLAGGED = "isFlagged";
    public static final String KEY_RISK_LEVEL = "riskLevel";
    public static final String KEY_FLAGGED_REASON = "flaggedReason";
    // The Gemini API key is no longer hardcoded into the app. Each user supplies their
    // own key via AiSettingsActivity, and it is stored locally under this preference key.
    public static final String KEY_GEMINI_API_KEY = "geminiApiKey";
    public static final String REMOTE_MSG_AUTHORIZATION = "Authorization";
    public static final String REMOTE_MSG_CONTENT_TYPE = "Content-Type";
    public static final String REMOTE_MSG_DATA = "data";
    public static final String REMOTE_MSG_REGISTRATION_IDS = "registration_ids";
    public static final String REMOTE_MSG_MESSAGE = "message";
    public static final String REMOTE_MSG_TOKEN = "token";

    public interface HeadersCallback {
        void onHeaders(HashMap<String, String> headers);
        default void onError(Exception e) {}
    }

    /**
     * Asynchronously builds FCM headers with a fresh (auto-refreshed) access token.
     * The callback is invoked on a background thread — call runOnUiThread / post to
     * main handler if you need to update UI afterward.
     *
     * @param context  any Context (Application context used internally)
     * @param callback receives the ready-to-use header map
     */
    public static void getRemoteMsgHeaders(Context context, HeadersCallback callback) {
        AccessTokenManager.getInstance(context).getToken(new AccessTokenManager.TokenCallback() {
            @Override
            public void onToken(String accessToken) {
                HashMap<String, String> headers = new HashMap<>();
                headers.put(REMOTE_MSG_AUTHORIZATION, "Bearer " + accessToken);
                headers.put(REMOTE_MSG_CONTENT_TYPE, "application/json");
                callback.onHeaders(headers);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }
}
