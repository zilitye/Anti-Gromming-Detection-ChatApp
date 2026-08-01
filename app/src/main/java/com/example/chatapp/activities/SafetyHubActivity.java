package com.example.chatapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.example.chatapp.R;
import com.example.chatapp.adapters.SafetyHubAdapter;
import com.example.chatapp.databinding.ActivitySafetyHubBinding;
import com.example.chatapp.models.SafetyHubMessage;
import com.example.chatapp.utilities.Constants;
import com.example.chatapp.utilities.ErrorUtils;
import com.example.chatapp.utilities.HttpException;
import com.example.chatapp.utilities.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SafetyHubActivity extends BaseActivity {

    private ActivitySafetyHubBinding binding;
    private PreferenceManager preferenceManager;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private String conversationContext = "";
    private List<SafetyHubMessage> safetyHubMessages;
    private SafetyHubAdapter safetyHubAdapter;
    private JSONArray chatHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySafetyHubBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyEdgeToEdge(binding.main);
        preferenceManager = new PreferenceManager(getApplicationContext());
        conversationContext = getIntent().getStringExtra("conversation");
        if (conversationContext == null) conversationContext = "No context provided.";
        init();
        setListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAiAvailabilityBanner();
    }

    private void init() {
        safetyHubMessages = new ArrayList<>();
        safetyHubAdapter = new SafetyHubAdapter(safetyHubMessages);
        binding.chatRecyclerView.setAdapter(safetyHubAdapter);
        chatHistory = new JSONArray();

        // Add welcome message
        addMessage(getString(R.string.safety_hub_welcome), false);
        updateAiAvailabilityBanner();
    }

    private boolean isAiConfigured() {
        String key = preferenceManager.getString(Constants.KEY_GEMINI_API_KEY);
        return key != null && !key.trim().isEmpty();
    }

    private void updateAiAvailabilityBanner() {
        binding.layoutAiBanner.setVisibility(isAiConfigured() ? View.GONE : View.VISIBLE);
    }

    private void addMessage(String text, boolean isUser) {
        safetyHubMessages.add(new SafetyHubMessage(text, isUser, getReadableDateTime(new Date())));
        safetyHubAdapter.notifyItemInserted(safetyHubMessages.size() - 1);
        binding.chatRecyclerView.smoothScrollToPosition(safetyHubMessages.size() - 1);

        try {
            JSONObject content = new JSONObject();
            content.put("role", isUser ? "user" : "model");
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", text);
            parts.put(part);
            content.put("parts", parts);
            chatHistory.put(content);
        } catch (Exception ignored) {}
    }

    private String getReadableDateTime(Date date) {
        return new SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.getDefault()).format(date);
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.buttonSetupAi.setOnClickListener(v ->
                startActivity(new Intent(getApplicationContext(), AiSettingsActivity.class)));

        binding.imageSend.setOnClickListener(v -> {
            String prompt = binding.inputPrompt.getText().toString().trim();
            if (prompt.isEmpty()) return;
            if (!isAiConfigured()) {
                startActivity(new Intent(getApplicationContext(), AiSettingsActivity.class));
                return;
            }
            addMessage(prompt, true);
            binding.inputPrompt.setText("");
            callGeminiAPI("Q&A");
        });

        binding.buttonPolicy.setOnClickListener(v -> analyzePolicy());
        binding.buttonReport.setOnClickListener(v -> draftReport());
        binding.buttonResources.setOnClickListener(v -> recommendResources());
    }

    private void analyzePolicy() {
        if (!isAiConfigured()) {
            startActivity(new Intent(getApplicationContext(), AiSettingsActivity.class));
            return;
        }
        addMessage("Policy Analysis Requested", true);
        callGeminiAPI("Policy Analysis");
    }

    private void draftReport() {
        if (!isAiConfigured()) {
            startActivity(new Intent(getApplicationContext(), AiSettingsActivity.class));
            return;
        }
        addMessage("Report Assistant Requested", true);
        callGeminiAPI("Report Assistant");
    }

    private void recommendResources() {
        // This tool works locally regardless of whether an AI key is configured.
        String resources = "### Recommended Resources & Hotlines\n\n" +
                "- **Childhelp National Child Abuse Hotline**: 1-800-422-4453\n" +
                "- **Cyber Civil Rights Initiative (CCRI)**: 1-844-878-2274\n" +
                "- **National Suicide Prevention Lifeline**: 988\n" +
                "- **RAINN National Sexual Assault Hotline**: 1-800-656-HOPE\n\n" +
                "**Counseling Services:**\n" +
                "- Look for licensed therapists specializing in trauma and child safety.\n" +
                "- Consider online platforms like BetterHelp or local community health centers.";
        addMessage(resources, false);
    }

    private void callGeminiAPI(String mode) {
        String apiKey = preferenceManager.getString(Constants.KEY_GEMINI_API_KEY);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.imageSend.setEnabled(false);
        executor.execute(() -> {
            try {
                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-goog-api-key", apiKey);
                conn.setDoOutput(true);

                // Construct system instruction and combined history
                JSONArray contentsArray = new JSONArray();
                
                // Add System/Context Instruction as the first message
                JSONObject systemPrompt = new JSONObject();
                systemPrompt.put("role", "user");
                JSONArray systemParts = new JSONArray();
                JSONObject systemText = new JSONObject();
                systemText.put("text", "You are a compassionate, trained counsellor supporting survivors of online grooming and sexual harassment. " +
                        "Always respond with empathy and avoid victim-blaming language. " +
                        "Ask user to tap on Resources button provided in the ui. " +
                        "Mode: " + mode + ". " +
                        "Conversation context for reference: " + conversationContext + " " +
                        "Respond to the user's latest message based on this context and history.");

                systemParts.put(systemText);
                systemPrompt.put("parts", systemParts);
                contentsArray.put(systemPrompt);

                // Add conversation history
                for (int i = 0; i < chatHistory.length(); i++) {
                    contentsArray.put(chatHistory.get(i));
                }
                
                JSONObject requestBody = new JSONObject();
                requestBody.put("contents", contentsArray);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    
                    JSONObject responseJson = new JSONObject(response.toString());
                    String resultText = responseJson.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text");

                    runOnUiThread(() -> {
                        addMessage(resultText, false);
                        binding.progressBar.setVisibility(View.GONE);
                        binding.imageSend.setEnabled(true);
                    });
                } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                    runOnUiThread(() -> {
                        showToast("Your AI API key was rejected. Please check it in AI Assistant Settings.");
                        binding.progressBar.setVisibility(View.GONE);
                        binding.imageSend.setEnabled(true);
                        updateAiAvailabilityBanner();
                    });
                } else {
                    throw new HttpException(responseCode, ErrorUtils.getStatusName(responseCode), "Safety Hub API failed");
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (e instanceof HttpException) {
                        showToast(e.toString());
                    } else {
                        showToast("Connection failed");
                    }
                    binding.progressBar.setVisibility(View.GONE);
                    binding.imageSend.setEnabled(true);
                });
            }
        });
    }

    // Removed local showToast to use BaseActivity's version
}
