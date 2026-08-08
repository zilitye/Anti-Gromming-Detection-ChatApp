package com.example.chatapp.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.example.chatapp.R;
import com.example.chatapp.adapters.SafetyHubAdapter;
import com.example.chatapp.databinding.ActivitySafetyHubBinding;
import com.example.chatapp.models.SafetyHubMessage;
import com.example.chatapp.utilities.Constants;
import com.example.chatapp.utilities.ErrorUtils;
import com.example.chatapp.utilities.HttpException;

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
        conversationContext = getIntent().getStringExtra("conversation");
        if (conversationContext == null) conversationContext = "No context provided.";
        init();
        setListeners();
    }

    private void init() {
        safetyHubMessages = new ArrayList<>();
        safetyHubAdapter = new SafetyHubAdapter(safetyHubMessages);
        binding.chatRecyclerView.setAdapter(safetyHubAdapter);
        chatHistory = new JSONArray();

        // Add welcome message
        addMessage(getString(R.string.safety_hub_welcome), false);
    }

    private void addMessage(String text, boolean isUser) {
        safetyHubMessages.add(new SafetyHubMessage(text, isUser, getReadableDateTime(new Date())));
        safetyHubAdapter.notifyItemInserted(safetyHubMessages.size() - 1);
        binding.chatRecyclerView.smoothScrollToPosition(safetyHubMessages.size() - 1);

        try {
            JSONObject content = new JSONObject();
            content.put("role", isUser ? "user" : "assistant");
            content.put("content", text);
            chatHistory.put(content);
        } catch (Exception ignored) {}
    }

    private String getReadableDateTime(Date date) {
        return new SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.getDefault()).format(date);
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.imageSend.setOnClickListener(v -> {
            String prompt = binding.inputPrompt.getText().toString().trim();
            if (!prompt.isEmpty()) {
                addMessage(prompt, true);
                binding.inputPrompt.setText("");
                callOpenAIAPI("Q&A");
            }
        });

        binding.buttonPolicy.setOnClickListener(v -> analyzePolicy());
        binding.buttonReport.setOnClickListener(v -> draftReport());
        binding.buttonResources.setOnClickListener(v -> recommendResources());
    }

    private void analyzePolicy() {
        addMessage("Policy Analysis Requested", true);
        callOpenAIAPI("Policy Analysis");
    }

    private void draftReport() {
        addMessage("Report Assistant Requested", true);
        callOpenAIAPI("Report Assistant");
    }

    private void recommendResources() {
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

    private void callOpenAIAPI(String mode) {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.imageSend.setEnabled(false);
        Log.d("SafetyHub", "Calling OpenAI API in mode: " + mode);
        executor.execute(() -> {
            try {
                URL url = new URL("https://api.openai.com/v1/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + Constants.OPENAI_API_KEY);
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                // Construct messages array for OpenAI
                JSONArray messagesArray = new JSONArray();
                
                // Add System/Context Instruction as the first message
                JSONObject systemPrompt = new JSONObject();
                systemPrompt.put("role", "system");
                systemPrompt.put("content", "You are a compassionate, trained counsellor supporting survivors of online grooming and sexual harassment. " +
                        "Always respond with empathy and avoid victim-blaming language. " +
                        "Ask user to tap on Resources button provided in the ui. " +
                        "Mode: " + mode + ". " +
                        "Conversation context for reference: " + conversationContext + " " +
                        "Respond to the user's latest message based on this context and history.");
                messagesArray.put(systemPrompt);

                // Add conversation history
                for (int i = 0; i < chatHistory.length(); i++) {
                    messagesArray.put(chatHistory.get(i));
                }
                
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", "gpt-4o-mini");
                requestBody.put("messages", messagesArray);

                Log.d("SafetyHub", "Request: " + requestBody.toString());

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                Log.d("SafetyHub", "Response Code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    
                    Log.d("SafetyHub", "Response Body: " + response.toString());

                    JSONObject responseJson = new JSONObject(response.toString());
                    String resultText = responseJson.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");

                    runOnUiThread(() -> {
                        addMessage(resultText, false);
                        binding.progressBar.setVisibility(View.GONE);
                        binding.imageSend.setEnabled(true);
                    });
                } else {
                    Log.e("SafetyHub", "API Error: " + responseCode);
                    throw new HttpException(responseCode, ErrorUtils.getStatusName(responseCode), "Safety Hub API failed with code: " + responseCode);
                }
            } catch (Exception e) {
                Log.e("SafetyHub", "API Call failed", e);
                runOnUiThread(() -> {
                    if (e instanceof HttpException) {
                        showToast(e.toString());
                    } else {
                        showToast("Connection failed: " + e.getMessage());
                    }
                    binding.progressBar.setVisibility(View.GONE);
                    binding.imageSend.setEnabled(true);
                });
            }
        });
    }

    // Removed local showToast to use BaseActivity's version
}
