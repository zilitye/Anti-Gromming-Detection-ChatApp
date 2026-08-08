package com.example.chatapp.utilities

import android.content.Context
import android.util.Log
import com.ml.shubham0204.sentence_embeddings.SentenceEmbedding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

/**
 * Local, on-device semantic (NLP) grooming detector.
 *
 * This replaces plain keyword/substring matching with sentence-embedding
 * cosine similarity: every incoming message is embedded with a small
 * transformer model (all-MiniLM-L6-v2, quantized to int8 for arm64) that
 * runs entirely on-device via ONNX Runtime -- the same approach used by the
 * reference Sentence-Embeddings-Android project -- and compared against
 * curated example sentences for known grooming tactics.
 *
 * Because it compares *meaning* rather than exact words, it can catch
 * paraphrases and novel wording a fixed keyword list would miss (e.g.
 * "send me a pic with nothing on" or "let's keep this just between us"),
 * while still returning the same [GroomingDetector.RiskLevel] / score shape
 * the rest of the app already understands.
 *
 * The model (`model.onnx`) and tokenizer (`tokenizer.json`) ship as app
 * assets under `assets/nlp/all-minilm-l6-v2/` -- no network access is
 * needed at runtime. ONNX Runtime needs a real file path to open a session,
 * so the model is copied once from assets into app-private storage on first
 * use; the tokenizer is small enough to read directly into memory.
 */
object NLPGroomingEngine {

    private const val TAG = "NLPGroomingEngine"

    private const val ASSET_DIR = "nlp/all-minilm-l6-v2"
    private const val MODEL_ASSET = "$ASSET_DIR/model.onnx"
    private const val TOKENIZER_ASSET = "$ASSET_DIR/tokenizer.json"
    private const val MODEL_DIR_NAME = "nlp_models/all-minilm-l6-v2"

    private val engineScope = CoroutineScope(Dispatchers.IO + Job())
    private val initMutex = Mutex()
    private val sentenceEmbedding = SentenceEmbedding()

    @Volatile
    private var ready = false

    @Volatile
    private var initializing = false

    private lateinit var categoryEmbeddings: Map<RiskCategory, List<FloatArray>>

    data class NLPResult(
        val riskLevel: GroomingDetector.RiskLevel,
        val score: Int,
        val reason: String
    )

    /**
     * Categories of grooming tactics, each backed by a handful of example
     * sentences. A message is scored per-category by the *best* (max)
     * cosine similarity between its embedding and that category's examples.
     * Weight = how much a strong match in that category contributes to the
     * overall risk score; threshold = minimum similarity to count as a hit.
     */
    private enum class RiskCategory(
        val weight: Int,
        val threshold: Float,
        val label: String,
        val examples: List<String>
    ) {
        SEXUAL_SOLICITATION(
            weight = 45,
            threshold = 0.42f,
            label = "sexual solicitation",
            examples = listOf(
                "Can you send me a naked picture of yourself?",
                "Send me a photo without your clothes on.",
                "Take your clothes off and show me on camera.",
                "You looked really sexy in that photo you posted.",
                "I want to see more private pictures of your body."
            )
        ),
        SECRECY_ISOLATION(
            weight = 35,
            threshold = 0.42f,
            label = "secrecy / isolation request",
            examples = listOf(
                "Please don't tell your parents about our conversation.",
                "This has to stay a secret just between us.",
                "Delete our messages so nobody finds out.",
                "Don't tell anyone we're talking, it's our little secret."
            )
        ),
        MEETING_REQUEST(
            weight = 40,
            threshold = 0.42f,
            label = "in-person meeting request",
            examples = listOf(
                "Let's meet up somewhere alone, just the two of us.",
                "I can pick you up when your parents aren't home.",
                "Come over to my place when nobody else is around.",
                "We should meet in person without telling anyone."
            )
        ),
        TRUST_MANIPULATION(
            weight = 15,
            threshold = 0.45f,
            label = "trust-building manipulation",
            examples = listOf(
                "You're so mature for your age, not like other kids.",
                "I feel like I understand you better than anyone else does.",
                "You can trust me with anything, I would never hurt you.",
                "I really think we have a special connection together."
            )
        ),
        GIFT_INCENTIVE(
            weight = 15,
            threshold = 0.45f,
            label = "gift / incentive offer",
            examples = listOf(
                "I can buy you a gift if you do this favor for me.",
                "Don't worry about money, I'll send you some.",
                "I'll give you whatever you want if you send that."
            )
        )
    }

    @JvmStatic
    fun isModelReady(): Boolean = ready

    /**
     * Kicks off (once) copying/loading the local sentence-embedding model
     * from app assets in the background. Safe to call multiple times/from
     * multiple screens -- only the first call actually does the work. Call
     * this early (e.g. when the chat or main screen is created) so the
     * model is warmed up by the time the user is chatting.
     */
    @JvmStatic
    fun initialize(context: Context) {
        if (ready || initializing) return
        engineScope.launch {
            initMutex.withLock {
                if (ready || initializing) return@withLock
                initializing = true
                try {
                    val appContext = context.applicationContext
                    val modelDir = File(appContext.filesDir, MODEL_DIR_NAME).apply { mkdirs() }
                    val modelFile = File(modelDir, "model.onnx")

                    // ONNX Runtime needs a real file path, so copy the (larger)
                    // model out of the APK's compressed assets once, on first run.
                    if (!modelFile.exists() || modelFile.length() == 0L) {
                        copyAssetToFile(appContext, MODEL_ASSET, modelFile)
                    }
                    // The tokenizer is small; read it straight into memory each time.
                    val tokenizerBytes = appContext.assets.open(TOKENIZER_ASSET).use { it.readBytes() }

                    sentenceEmbedding.init(
                        modelFilepath = modelFile.absolutePath,
                        tokenizerBytes = tokenizerBytes,
                        useTokenTypeIds = true,
                        outputTensorName = "last_hidden_state",
                        useFP16 = false,
                        useXNNPack = false,
                        normalizeEmbeddings = true
                    )

                    // Pre-compute reference embeddings once, up front, so
                    // per-message analysis only has to embed the message itself.
                    categoryEmbeddings = RiskCategory.values().associateWith { category ->
                        category.examples.map { sentenceEmbedding.encode(it) }
                    }

                    ready = true
                    Log.d(TAG, "NLP grooming engine ready (on-device, all-MiniLM-L6-v2, bundled assets).")
                } catch (e: Exception) {
                    // Model/tokenizer failed to load from assets (shouldn't normally
                    // happen since they ship with the APK). GroomingDetector will
                    // keep using the keyword-based fallback until this succeeds.
                    Log.w(TAG, "NLP model unavailable, staying on rule-based fallback: ${e.message}")
                    ready = false
                } finally {
                    initializing = false
                }
            }
        }
    }

    private fun copyAssetToFile(context: Context, assetPath: String, destination: File) {
        context.assets.open(assetPath).use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        }
    }

    /** Suspend API -- for callers already on a coroutine. */
    suspend fun analyze(message: String): NLPResult {
        val embedding = sentenceEmbedding.encode(message)
        var totalScore = 0
        val matched = mutableListOf<String>()

        for (category in RiskCategory.values()) {
            val examples = categoryEmbeddings[category].orEmpty()
            val bestSimilarity = examples.maxOfOrNull { cosineSimilarity(embedding, it) } ?: 0f
            if (bestSimilarity >= category.threshold) {
                // Scale the category's weight by how far past the threshold the
                // match is, so a borderline match contributes less than a very
                // close semantic match.
                val strength = ((bestSimilarity - category.threshold) / (1f - category.threshold))
                    .coerceIn(0f, 1f)
                totalScore += (category.weight * (0.6f + 0.4f * strength)).toInt()
                matched.add("${category.label} (${(bestSimilarity * 100).toInt()}% match)")
            }
        }

        val riskLevel = when {
            totalScore >= 50 -> GroomingDetector.RiskLevel.HIGH
            totalScore >= 20 -> GroomingDetector.RiskLevel.MEDIUM
            else -> GroomingDetector.RiskLevel.SAFE
        }

        val reason = if (matched.isEmpty()) {
            "No grooming patterns detected (semantic analysis)"
        } else {
            "Semantic match: " + matched.joinToString(", ")
        }

        return NLPResult(riskLevel, totalScore, reason)
    }

    /**
     * Blocking API for Java callers. Must only be invoked off the main
     * thread -- inference involves on-device model execution and should
     * never run on the UI thread. [GroomingDetector] takes care of that.
     */
    @JvmStatic
    fun analyzeBlocking(message: String): NLPResult = runBlocking { analyze(message) }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var magA = 0f
        var magB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            magA += a[i] * a[i]
            magB += b[i] * b[i]
        }
        val denom = sqrt(magA) * sqrt(magB)
        return if (denom == 0f) 0f else dot / denom
    }
}
