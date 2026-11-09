package com.sixninezerozero.freeagent.domain

/** Result of asking a provider whether a key works. */
sealed class KeyCheck {
    data object Works : KeyCheck()
    data class Rejected(val detail: String?) : KeyCheck()
    data object NoConnection : KeyCheck()
    data class CouldNotCheck(val detail: String?) : KeyCheck()
}

interface KeyChecker {
    suspend fun check(provider: AiProvider, apiKey: String): KeyCheck

    /** Checks a Pexels key (stock footage). */
    suspend fun checkPexels(apiKey: String): KeyCheck = KeyCheck.CouldNotCheck("not checked")
}

/** What the setup screen should show after the user taps Save. */
data class KeySetupResult(
    val groqKey: String,
    val geminiKey: String,
    val groqError: String? = null,
    val geminiError: String? = null,
    val pexelsKey: String = "",
    val pexelsError: String? = null,
    val generalError: String? = null,
    /** Shown after a successful save, e.g. keys were moved or could not be checked. */
    val notice: String? = null,
) {
    val canSave: Boolean
        get() = groqError == null && geminiError == null && pexelsError == null && generalError == null
}

/**
 * Cleans, sorts and checks the keys typed on the setup screen.
 * A key the provider rejects blocks saving. If the phone is offline the keys are
 * still saved, with a note that they could not be checked.
 */
class KeySetup(private val checker: KeyChecker) {
    suspend fun prepare(groqRaw: String, geminiRaw: String, pexelsRaw: String = ""): KeySetupResult {
        val pexels = ApiKeyValidator.normalise(pexelsRaw)
        val (groq, gemini) = ApiKeyValidator.arrange(groqRaw, geminiRaw)
        val swapped = (groq to gemini) !=
            (ApiKeyValidator.normalise(groqRaw) to ApiKeyValidator.normalise(geminiRaw))

        val formatResult = KeySetupResult(
            groqKey = groq,
            geminiKey = gemini,
            groqError = ApiKeyValidator.errorFor(groq, AiProvider.GROQ),
            geminiError = ApiKeyValidator.errorFor(gemini, AiProvider.GEMINI),
            pexelsKey = pexels,
            pexelsError = ApiKeyValidator.errorForPexels(pexels),
            generalError = if (groq.isEmpty() && gemini.isEmpty()) {
                "Add a Groq or Gemini API key. Both are free."
            } else {
                null
            },
        )
        if (!formatResult.canSave) return formatResult

        val notes = mutableListOf<String>()
        if (swapped) notes += "Your keys were in the wrong boxes, so they were swapped."

        var groqError: String? = null
        var geminiError: String? = null
        for ((provider, key) in listOf(AiProvider.GROQ to groq, AiProvider.GEMINI to gemini)) {
            if (key.isEmpty()) continue
            val name = provider.displayName
            when (val check = checker.check(provider, key)) {
                KeyCheck.Works -> notes += "$name key works."
                is KeyCheck.Rejected -> {
                    val message = buildString {
                        append("$name says this key isn't valid. Copy it again from the $name website")
                        append(", or make a new key.")
                        if (!check.detail.isNullOrBlank()) append(" ($name: ${check.detail})")
                    }
                    if (provider == AiProvider.GROQ) groqError = message else geminiError = message
                }
                KeyCheck.NoConnection ->
                    notes += "Saved, but the $name key couldn't be checked because there is no internet connection."
                is KeyCheck.CouldNotCheck ->
                    notes += "Saved, but $name couldn't check the key right now (${check.detail})."
            }
        }

        var pexelsError: String? = null
        if (pexels.isNotEmpty()) {
            when (val check = checker.checkPexels(pexels)) {
                KeyCheck.Works -> notes += "Pexels key works."
                is KeyCheck.Rejected -> pexelsError =
                    "Pexels says this key isn't valid. Copy it again from pexels.com/api." +
                    (check.detail?.takeIf(String::isNotBlank)?.let { " (Pexels: $it)" } ?: "")
                KeyCheck.NoConnection ->
                    notes += "Saved, but the Pexels key couldn't be checked because there is no internet connection."
                is KeyCheck.CouldNotCheck ->
                    notes += "Saved, but Pexels couldn't check the key right now (${check.detail})."
            }
        }

        return formatResult.copy(
            groqError = groqError,
            geminiError = geminiError,
            pexelsError = pexelsError,
            notice = notes.joinToString(" ").ifBlank { null },
        )
    }
}
