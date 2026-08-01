package com.example.chatapp.utilities;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A fully local, rule-based grooming risk detector. No network calls and no AI model are
 * used here — detection has to keep working even when the chatbot's AI key isn't configured.
 *
 * Improvements over the previous version:
 *  - Matches use word boundaries via regex instead of naive substring search, so a phrase
 *    like "older" no longer fires inside an unrelated word such as "folder".
 *  - Phrases are grouped into behavioural categories (secrecy, isolation, sexual content
 *    requests, meeting requests, personal-info fishing, incentives, trust manipulation)
 *    instead of one flat keyword list.
 *  - Risk isn't just additive: when a message (or the conversation history passed in)
 *    touches multiple distinct categories, an escalation bonus is added. Real grooming
 *    tends to combine tactics (e.g. build trust, isolate from parents, then request
 *    something private) rather than rely on one type of phrase, so category diversity is
 *    itself a signal.
 *  - Repeated matches of the exact same phrase only count once per message, so padding a
 *    message with a repeated word can't inflate the score.
 */
public class GroomingDetector {

    public enum RiskLevel {
        SAFE, MEDIUM, HIGH
    }

    public enum Category {
        SECRECY_ISOLATION("secrecy or isolation from trusted adults"),
        TRUST_MANIPULATION("manipulative trust-building language"),
        SEXUAL_CONTENT("a request for sexual content or images"),
        MEETING_REQUEST("a request to meet in person privately"),
        PERSONAL_INFO("fishing for personal details"),
        GIFTS_MONEY("gifts or money used as an incentive");

        final String label;
        Category(String label) { this.label = label; }
    }

    public static class DetectionResult {
        public final RiskLevel riskLevel;
        public final int score;
        public final String reason;
        public final Set<Category> categories;

        public DetectionResult(RiskLevel riskLevel, int score, String reason, Set<Category> categories) {
            this.riskLevel = riskLevel;
            this.score = score;
            this.reason = reason;
            this.categories = categories;
        }
    }

    private static class RulePattern {
        final Pattern pattern;
        final int weight;
        final Category category;

        RulePattern(String phrase, int weight, Category category) {
            // \b word boundaries stop "older" from matching inside "folder", etc.
            // The phrase is wrapped in a non-capturing group before anchoring so the
            // boundaries apply to the whole alternation (e.g. "a|b|c"), not just its
            // first and last branches.
            this.pattern = Pattern.compile("\\b(?:" + phrase + ")\\b", Pattern.CASE_INSENSITIVE);
            this.weight = weight;
            this.category = category;
        }
    }

    private static final List<RulePattern> PATTERNS = new ArrayList<>();

    private static void add(String phrase, int weight, Category category) {
        PATTERNS.add(new RulePattern(phrase, weight, category));
    }

    static {
        // --- Secrecy / isolation from parents or other trusted adults ---
        add("don'?t tell (your )?(parents|mom|dad|mum|anyone)", 30, Category.SECRECY_ISOLATION);
        add("our (little )?secret", 28, Category.SECRECY_ISOLATION);
        add("(just |keep this )?between (you and me|us)", 22, Category.SECRECY_ISOLATION);
        add("parents? (won'?t|wouldn'?t) (know|understand)", 28, Category.SECRECY_ISOLATION);
        add("delete (this|our) (chat|conversation|messages?)", 24, Category.SECRECY_ISOLATION);
        add("don'?t let (them|anyone) (see|find out)", 20, Category.SECRECY_ISOLATION);

        // --- Manipulative trust-building / grooming flattery ---
        add("mature for (your|ur) age", 18, Category.TRUST_MANIPULATION);
        add("no one (else )?understands you( like i do)?", 16, Category.TRUST_MANIPULATION);
        add("i'?m the only one who (cares|understands)", 16, Category.TRUST_MANIPULATION);
        add("you can trust (only )?me", 14, Category.TRUST_MANIPULATION);
        add("i love you", 8, Category.TRUST_MANIPULATION);
        add("sweetie|darling|baby girl|baby boy", 8, Category.TRUST_MANIPULATION);

        // --- Requests for sexual content ---
        add("send (me )?(a )?nudes?", 45, Category.SEXUAL_CONTENT);
        add("(private|naked) (photo|picture|pic)s?", 40, Category.SEXUAL_CONTENT);
        add("show me your body", 40, Category.SEXUAL_CONTENT);
        add("take (your|ur) clothes off", 42, Category.SEXUAL_CONTENT);
        add("webcam show", 30, Category.SEXUAL_CONTENT);
        add("sexy", 20, Category.SEXUAL_CONTENT);
        add("undress", 30, Category.SEXUAL_CONTENT);

        // --- Requests to meet in person / isolate physically ---
        add("meet (up )?alone", 32, Category.MEETING_REQUEST);
        add("meet (up )?in person", 20, Category.MEETING_REQUEST);
        add("without (your|ur) parents", 26, Category.MEETING_REQUEST);
        add("(i'?ll|can i) pick you up", 22, Category.MEETING_REQUEST);
        add("come (over|to my place|to my house)", 20, Category.MEETING_REQUEST);

        // --- Fishing for personal / identifying details ---
        add("(are you|r u) home alone", 24, Category.PERSONAL_INFO);
        add("what'?s your address", 22, Category.PERSONAL_INFO);
        add("where do you live", 14, Category.PERSONAL_INFO);
        add("what school do you (go to|attend)", 14, Category.PERSONAL_INFO);
        add("how old are you", 6, Category.PERSONAL_INFO);
        add("send me your (location|number)", 20, Category.PERSONAL_INFO);

        // --- Gifts / money used as an incentive ---
        add("buy you (a )?gift", 14, Category.GIFTS_MONEY);
        add("send (you )?money", 14, Category.GIFTS_MONEY);
        add("gift card", 12, Category.GIFTS_MONEY);
        add("i'?ll pay you", 16, Category.GIFTS_MONEY);
    }

    // Score thresholds for classification.
    private static final int HIGH_THRESHOLD = 50;
    private static final int MEDIUM_THRESHOLD = 20;

    // Escalation bonuses applied when a message spans multiple distinct grooming categories,
    // reflecting how real grooming tends to combine tactics rather than rely on just one.
    private static final int BONUS_TWO_CATEGORIES = 10;
    private static final int BONUS_THREE_PLUS_CATEGORIES = 20;

    public static DetectionResult analyze(String message) {
        if (message == null || message.trim().isEmpty()) {
            return new DetectionResult(RiskLevel.SAFE, 0, "Empty message", new LinkedHashSet<>());
        }

        String normalized = normalize(message);

        int rawScore = 0;
        Set<Category> matchedCategories = new LinkedHashSet<>();
        // Track which exact phrases already matched so repeating one phrase can't inflate score.
        Set<String> matchedPhrases = new LinkedHashSet<>();

        for (RulePattern rule : PATTERNS) {
            Matcher matcher = rule.pattern.matcher(normalized);
            if (matcher.find()) {
                String key = rule.pattern.pattern();
                if (matchedPhrases.add(key)) {
                    rawScore += rule.weight;
                    matchedCategories.add(rule.category);
                }
            }
        }

        int bonus = 0;
        if (matchedCategories.size() >= 3) {
            bonus = BONUS_THREE_PLUS_CATEGORIES;
        } else if (matchedCategories.size() == 2) {
            bonus = BONUS_TWO_CATEGORIES;
        }

        int score = Math.min(100, rawScore + bonus);

        RiskLevel level;
        if (score >= HIGH_THRESHOLD) {
            level = RiskLevel.HIGH;
        } else if (score >= MEDIUM_THRESHOLD) {
            level = RiskLevel.MEDIUM;
        } else {
            level = RiskLevel.SAFE;
        }

        String reason = buildReason(level, matchedCategories);
        return new DetectionResult(level, score, reason, matchedCategories);
    }

    /** Lowercases and normalizes common contractions/spacing so patterns match reliably. */
    private static String normalize(String message) {
        String lower = message.toLowerCase();
        // Normalize curly apostrophes to straight ones so "don't"/"don't" both match.
        lower = lower.replace('\u2019', '\'');
        // Collapse repeated whitespace.
        lower = lower.replaceAll("\\s+", " ").trim();
        return lower;
    }

    private static String buildReason(RiskLevel level, Set<Category> categories) {
        if (categories.isEmpty()) {
            return "No significant risk patterns detected";
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Category category : categories) {
            if (i > 0) sb.append(", ");
            sb.append(category.label);
            i++;
        }
        return sb.toString();
    }
}
