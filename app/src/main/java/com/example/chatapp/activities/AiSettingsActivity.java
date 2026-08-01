package com.example.chatapp.activities;

import android.os.Bundle;
import android.text.InputType;

import com.example.chatapp.R;
import com.example.chatapp.databinding.ActivityAiSettingsBinding;
import com.example.chatapp.utilities.Constants;
import com.example.chatapp.utilities.PreferenceManager;

/**
 * Lets the user paste in their own Gemini API key so the Safety Assistant chatbot can run.
 * The key is stored only in this device's private SharedPreferences via PreferenceManager,
 * and is never bundled into the app or sent anywhere except Google's Gemini endpoint.
 */
public class AiSettingsActivity extends BaseActivity {

    private ActivityAiSettingsBinding binding;
    private PreferenceManager preferenceManager;
    private boolean isKeyVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAiSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyEdgeToEdge(binding.main);
        preferenceManager = new PreferenceManager(getApplicationContext());
        loadExistingKey();
        setListeners();
    }

    private void loadExistingKey() {
        String existingKey = preferenceManager.getString(Constants.KEY_GEMINI_API_KEY);
        boolean configured = existingKey != null && !existingKey.trim().isEmpty();
        if (configured) {
            binding.inputApiKey.setText(existingKey);
        }
        updateStatusLabel(configured);
    }

    private void updateStatusLabel(boolean configured) {
        binding.textStatus.setText(configured
                ? R.string.ai_settings_status_configured
                : R.string.ai_settings_status_not_configured);
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.imageToggleVisibility.setOnClickListener(v -> toggleKeyVisibility());

        binding.buttonSave.setOnClickListener(v -> {
            String key = binding.inputApiKey.getText().toString().trim();
            if (key.isEmpty()) {
                showToast(getString(R.string.ai_settings_empty_error));
                return;
            }
            preferenceManager.putString(Constants.KEY_GEMINI_API_KEY, key);
            updateStatusLabel(true);
            showToast(getString(R.string.ai_settings_saved_toast));
        });

        binding.buttonClear.setOnClickListener(v -> {
            preferenceManager.putString(Constants.KEY_GEMINI_API_KEY, "");
            binding.inputApiKey.setText("");
            updateStatusLabel(false);
            showToast(getString(R.string.ai_settings_cleared_toast));
        });
    }

    private void toggleKeyVisibility() {
        isKeyVisible = !isKeyVisible;
        int selectionStart = binding.inputApiKey.getSelectionStart();
        int selectionEnd = binding.inputApiKey.getSelectionEnd();
        binding.inputApiKey.setInputType(isKeyVisible
                ? InputType.TYPE_CLASS_TEXT
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        binding.imageToggleVisibility.setImageResource(isKeyVisible ? R.drawable.ic_eye_off : R.drawable.ic_eye);
        if (selectionStart >= 0 && selectionEnd >= 0) {
            binding.inputApiKey.setSelection(selectionStart, selectionEnd);
        }
    }
}
