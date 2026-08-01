package com.example.chatapp;

import org.junit.Test;
import static org.junit.Assert.*;

import com.example.chatapp.utilities.GroomingDetector;

public class GroomingDetectorTest {

    @Test
    public void testSafeMessage() {
        GroomingDetector.DetectionResult result = GroomingDetector.analyze("Hello, how are you today?");
        assertEquals(GroomingDetector.RiskLevel.SAFE, result.riskLevel);
        assertEquals(0, result.score);
    }

    @Test
    public void testEmptyMessage() {
        GroomingDetector.DetectionResult result = GroomingDetector.analyze("");
        assertEquals(GroomingDetector.RiskLevel.SAFE, result.riskLevel);
        assertEquals(0, result.score);
    }

    @Test
    public void testHighRiskMessage_combinesSecrecyAndSexualContent() {
        // "don't tell your parents" (secrecy) + "our secret" (secrecy) + "private photo" (sexual content)
        // spans 2 categories, so an escalation bonus is added on top of the raw phrase weights.
        GroomingDetector.DetectionResult result =
                GroomingDetector.analyze("Don't tell your parents, it's our secret. Send me a private photo.");
        assertEquals(GroomingDetector.RiskLevel.HIGH, result.riskLevel);
        assertTrue(result.score >= 50);
        assertTrue(result.categories.contains(GroomingDetector.Category.SECRECY_ISOLATION));
        assertTrue(result.categories.contains(GroomingDetector.Category.SEXUAL_CONTENT));
    }

    @Test
    public void testMediumRiskMessage_multiCategoryEscalation() {
        // Neither phrase alone is high risk, but touching two different categories
        // (personal info fishing + a gift incentive) earns an escalation bonus.
        GroomingDetector.DetectionResult result =
                GroomingDetector.analyze("Where do you live? I could get you a gift card.");
        assertEquals(GroomingDetector.RiskLevel.MEDIUM, result.riskLevel);
        assertTrue(result.score >= 20 && result.score < 50);
        assertEquals(2, result.categories.size());
    }

    @Test
    public void testMeetAloneIsHighRiskOnItsOwn() {
        GroomingDetector.DetectionResult result = GroomingDetector.analyze("Can we meet alone this weekend?");
        assertTrue(result.score >= 20);
        assertTrue(result.categories.contains(GroomingDetector.Category.MEETING_REQUEST));
    }

    @Test
    public void testWordBoundaryPreventsFalsePositiveInsideLargerWord() {
        // Regression test: pattern matching must respect word boundaries so a rule phrase
        // that happens to appear as a substring of an unrelated word does not fire.
        GroomingDetector.DetectionResult result = GroomingDetector.analyze("giftcardholders get engiftcarded discounts");
        assertEquals(GroomingDetector.RiskLevel.SAFE, result.riskLevel);
        assertEquals(0, result.score);
    }

    @Test
    public void testRepeatedPhraseDoesNotInflateScore() {
        GroomingDetector.DetectionResult firstResult = GroomingDetector.analyze("our secret");
        GroomingDetector.DetectionResult repeatedResult =
                GroomingDetector.analyze("our secret our secret our secret");
        assertEquals(firstResult.score, repeatedResult.score);
    }

    @Test
    public void testCaseInsensitiveAndContractionMatching() {
        GroomingDetector.DetectionResult result = GroomingDetector.analyze("DONT TELL YOUR PARENTS about this");
        assertTrue(result.categories.contains(GroomingDetector.Category.SECRECY_ISOLATION));
    }
}
