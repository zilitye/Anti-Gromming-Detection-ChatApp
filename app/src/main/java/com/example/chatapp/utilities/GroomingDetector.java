package com.example.chatapp.utilities;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Detects grooming-risk language in chat messages.
 * <p>
 * Detection now runs on a local, on-device NLP model (sentence embeddings +
 * semantic similarity -- see {@link NLPGroomingEngine}) instead of plain
 * keyword matching, so it can recognize grooming tactics phrased in ways
 * that don't literally contain one of a fixed list of words (e.g. "send a
 * pic with nothing on" instead of "nude").
 * <p>
 * A lightweight keyword-based scorer is kept as an automatic fallback for
 * the brief window before the local model finishes downloading/loading on
 * first launch, for offline use before the model has ever been downloaded,
 * and for plain JVM unit tests where the on-device ML runtime isn't
 * available -- so the app is never left without any protection at all.
 */
public class GroomingDetector {

    private static final Executor executor = Executors.newSingleThreadExecutor();

    // Lazy load Handler to avoid ExceptionInInitializerError in unit tests
    private static class HandlerHolder {
        private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    }

    public enum RiskLevel {
        SAFE, MEDIUM, HIGH
    }

    public static class DetectionResult {
        public RiskLevel riskLevel;
        public int score;
        public String reason;

        public DetectionResult(RiskLevel riskLevel, int score, String reason) {
            this.riskLevel = riskLevel;
            this.score = score;
            this.reason = reason;
        }
    }

    public interface DetectionCallback {
        void onResult(DetectionResult result);
        void onError(Exception e);
    }

    public interface BatchCallback {
        void onResult(List<DetectionResult> results, int totalScore);
    }

    /**
     * Starts downloading/loading the local sentence-embedding model in the
     * background. Safe to call repeatedly (e.g. from every activity's
     * onCreate) -- only the first call does any work. Call this as early as
     * possible (app/first activity launch) so the NLP engine is warmed up by
     * the time the user starts chatting.
     */
    public static void initialize(Context context) {
        NLPGroomingEngine.initialize(context.getApplicationContext());
    }

    /**
     * Primary synchronous analysis.
     * <p>
     * Uses the on-device NLP model when it is ready AND this call is not on
     * the main thread (model inference must never block the UI thread).
     * Otherwise falls back to fast keyword-based scoring, so callers on the
     * main thread -- or plain JVM unit tests -- never block or crash.
     * Prefer {@link #analyze(String, DetectionCallback)} or
     * {@link #analyzeBatchAsync(List, BatchCallback)} from UI code.
     */
    public static DetectionResult analyze(String message) {
        if (message == null || message.trim().isEmpty()) {
            return new DetectionResult(RiskLevel.SAFE, 0, "Empty message");
        }

        if (NLPGroomingEngine.isModelReady() && !isOnMainThread()) {
            return toDetectionResult(NLPGroomingEngine.analyzeBlocking(message));
        }
        return analyzeRuleBased(message);
    }

    /**
     * True if called on the main/UI thread. Defaults to "true" (i.e. treat
     * as main thread, use the safe rule-based path) if Looper can't be
     * queried at all -- e.g. plain JVM unit tests with no Android runtime.
     */
    private static boolean isOnMainThread() {
        try {
            return Looper.myLooper() == Looper.getMainLooper();
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Asynchronous analysis: always runs off the main thread, prefers the
     * NLP engine once it has finished loading (falling back to keyword
     * scoring until then), and invokes {@code callback} on the main thread.
     */
    public static void analyze(String message, DetectionCallback callback) {
        if (message == null || message.trim().isEmpty()) {
            callback.onResult(new DetectionResult(RiskLevel.SAFE, 0, "Empty message"));
            return;
        }

        executor.execute(() -> {
            try {
                DetectionResult result = NLPGroomingEngine.isModelReady()
                        ? toDetectionResult(NLPGroomingEngine.analyzeBlocking(message))
                        : analyzeRuleBased(message);
                postResult(callback, result);
            } catch (Exception e) {
                try {
                    HandlerHolder.mainHandler.post(() -> callback.onError(e));
                } catch (Exception ignored) {
                    callback.onError(e);
                }
            }
        });
    }

    /**
     * Analyzes a batch of messages (e.g. conversation history) on a single
     * background thread and reports back on the main thread, so callers
     * (such as risk-dashboard or history scans) never need to run NLP
     * inference on the UI thread themselves.
     */
    public static void analyzeBatchAsync(List<String> messages, BatchCallback callback) {
        executor.execute(() -> {
            List<DetectionResult> results = new ArrayList<>();
            int totalScore = 0;
            boolean nlpReady = NLPGroomingEngine.isModelReady();
            for (String message : messages) {
                DetectionResult result = nlpReady
                        ? toDetectionResult(NLPGroomingEngine.analyzeBlocking(message))
                        : analyzeRuleBased(message);
                results.add(result);
                if (result.riskLevel != RiskLevel.SAFE) {
                    totalScore += result.score;
                }
            }
            int finalTotalScore = totalScore;
            try {
                HandlerHolder.mainHandler.post(() -> callback.onResult(results, finalTotalScore));
            } catch (Exception ignored) {
                callback.onResult(results, finalTotalScore);
            }
        });
    }

    private static DetectionResult toDetectionResult(NLPGroomingEngine.NLPResult r) {
        return new DetectionResult(r.getRiskLevel(), r.getScore(), r.getReason());
    }

    private static void postResult(DetectionCallback callback, DetectionResult result) {
        try {
            HandlerHolder.mainHandler.post(() -> callback.onResult(result));
        } catch (Exception e) {
            callback.onResult(result);
        }
    }

    // ---- Keyword-based fallback (used only while the local NLP model isn't loaded) ----

    private static final Map<String, Integer> GROOMING_KEYWORDS = new HashMap<>();

    static {
        // High risk keywords/phrases
        GROOMING_KEYWORDS.put("don't tell", 30);
        GROOMING_KEYWORDS.put("our secret", 30);
        GROOMING_KEYWORDS.put("private photo", 40);
        GROOMING_KEYWORDS.put("nude", 50);
        GROOMING_KEYWORDS.put("sexy", 25);
        GROOMING_KEYWORDS.put("meet alone", 40);
        GROOMING_KEYWORDS.put("parents won't know", 35);

        // Medium risk keywords/phrases
        GROOMING_KEYWORDS.put("webcam", 20);
        GROOMING_KEYWORDS.put("lonely", 15);
        GROOMING_KEYWORDS.put("gift", 10);
        GROOMING_KEYWORDS.put("money", 10);
        GROOMING_KEYWORDS.put("older", 5);
        GROOMING_KEYWORDS.put("sweetie", 10);
        GROOMING_KEYWORDS.put("darling", 10);
    }

    private static DetectionResult analyzeRuleBased(String message) {
        if (message == null || message.trim().isEmpty()) {
            return new DetectionResult(RiskLevel.SAFE, 0, "Empty message");
        }

        String lowerMessage = message.toLowerCase();
        int score = 0;
        StringBuilder detectedPatterns = new StringBuilder();

        for (Map.Entry<String, Integer> entry : GROOMING_KEYWORDS.entrySet()) {
            if (lowerMessage.contains(entry.getKey())) {
                score += entry.getValue();
                if (detectedPatterns.length() > 0) detectedPatterns.append(", ");
                detectedPatterns.append(entry.getKey());
            }
        }

        RiskLevel level;
        String reason;
        if (score >= 50) {
            level = RiskLevel.HIGH;
            reason = "Keyword match (high risk): " + detectedPatterns.toString();
        } else if (score >= 20) {
            level = RiskLevel.MEDIUM;
            reason = "Keyword match (suspicious): " + detectedPatterns.toString();
        } else {
            level = RiskLevel.SAFE;
            reason = "No risk patterns detected (keyword scan)";
        }

        return new DetectionResult(level, score, reason);
    }

    /**
     * @deprecated Use {@link #analyze(String)} instead.
     */
    @Deprecated
    public static DetectionResult analyzeSync(String message) {
        return analyze(message);
    }
}
