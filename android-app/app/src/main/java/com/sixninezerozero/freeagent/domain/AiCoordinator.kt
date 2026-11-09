package com.sixninezerozero.freeagent.domain

interface AiClient {
    val provider: AiProvider

    suspend fun generate(
        apiKey: String,
        systemPrompt: String,
        messages: List<ChatMessage>,
    ): String
}

class AiCoordinator(
    private val groqClient: AiClient,
    private val geminiClient: AiClient,
) {
    init {
        require(groqClient.provider == AiProvider.GROQ)
        require(geminiClient.provider == AiProvider.GEMINI)
    }

    suspend fun reply(settings: AppSettings, messages: List<ChatMessage>): AiReply {
        val context = ConversationPolicy.contextForRequest(messages)
        val groqKey = ApiKeyValidator.normalise(settings.groqApiKey)
        val geminiKey = ApiKeyValidator.normalise(settings.geminiApiKey)

        if (groqKey.isBlank() && geminiKey.isBlank()) throw ApiFailure.MissingCredentials()

        if (groqKey.isBlank()) {
            return generateWith(geminiClient, geminiKey, settings.systemPrompt, context, false)
        }

        return try {
            generateWith(groqClient, groqKey, settings.systemPrompt, context, false)
        } catch (failure: ApiFailure) {
            if (!settings.fallbackEnabled || geminiKey.isBlank()) throw failure
            generateWith(geminiClient, geminiKey, settings.systemPrompt, context, true)
        }
    }

    /**
     * Asks the AI for a stock-footage search per scene. Returns null on any problem, so the
     * video is still made with the engine's own keywords.
     */
    suspend fun visualQueries(settings: AppSettings, scenes: List<String>): List<String>? {
        if (scenes.isEmpty()) return null
        val groqKey = ApiKeyValidator.normalise(settings.groqApiKey)
        val geminiKey = ApiKeyValidator.normalise(settings.geminiApiKey)
        val attempts = listOfNotNull(
            groqKey.takeIf(String::isNotBlank)?.let { groqClient to it },
            geminiKey.takeIf(String::isNotBlank)?.let { geminiClient to it },
        )
        val ask = listOf(ChatMessage(role = MessageRole.USER, content = VideoQueries.prompt(scenes)))
        for ((client, key) in attempts) {
            val reply = try {
                client.generate(key, VideoQueries.SYSTEM_PROMPT, ask)
            } catch (_: ApiFailure) {
                continue
            }
            VideoQueries.parse(reply, scenes.size)?.let { return it }
        }
        return null
    }

    private suspend fun generateWith(
        client: AiClient,
        apiKey: String,
        systemPrompt: String,
        messages: List<ChatMessage>,
        fallbackUsed: Boolean,
    ): AiReply {
        val text = client.generate(apiKey, withAppAbilities(systemPrompt), messages).trim()
        if (text.isEmpty()) throw ApiFailure.EmptyResponse(client.provider.displayName)
        return AiReply(text = text, provider = client.provider, fallbackUsed = fallbackUsed)
    }

    companion object {
        /** Added to the user's own instruction so the model knows what this app can do. */
        const val VIDEO_ABILITY =
            "This app can turn text into a short vertical MP4 video with a free AI voiceover, " +
                "captions and music. When the user asks for a video, reel, TikTok, short or MP4, " +
                "do not say you can't make videos. Write only the words to be spoken: 40 to 90 words, " +
                "plain sentences, no headings, no emoji, no hashtags, no stage directions or scene labels. " +
                "Then add one final line: \"Tap Make video below this message to create the MP4.\""

        fun withAppAbilities(systemPrompt: String): String = "$systemPrompt\n\n$VIDEO_ABILITY"
    }
}
