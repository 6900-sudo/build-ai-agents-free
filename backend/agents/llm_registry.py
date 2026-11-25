"""LLM factory: Groq→Gemini fallback for most agents, Claude Haiku for Designer."""
import os
from langchain_core.language_models import BaseChatModel

_groq_available: bool | None = None  # cached per process; see get_groq_with_fallback


def get_groq_with_fallback() -> BaseChatModel:
    """Groq llama-3.3-70b primary; Gemini 2.5 Flash fallback.

    Probes Groq once per process and caches the result. Agents call this on
    every node run (and analyst/optimizer run again on every loop iteration),
    so a fresh "ping" invoke each time would double the Groq calls a job
    makes for no benefit once availability is known.
    """
    global _groq_available
    from langchain_groq import ChatGroq

    if _groq_available is None:
        try:
            ChatGroq(model="llama-3.3-70b-versatile", temperature=0.3).invoke("ping")
            _groq_available = True
        except Exception:
            _groq_available = False

    if _groq_available:
        return ChatGroq(model="llama-3.3-70b-versatile", temperature=0.3)

    from langchain_google_genai import ChatGoogleGenerativeAI
    return ChatGoogleGenerativeAI(model="gemini-2.5-flash", temperature=0.3)


def get_designer_llm() -> BaseChatModel:
    """Claude Haiku for Designer — best script quality per dollar. Falls back to Groq."""
    anthropic_key = os.getenv("ANTHROPIC_API_KEY", "")
    if anthropic_key:
        try:
            from langchain_anthropic import ChatAnthropic
            return ChatAnthropic(
                model="claude-3-5-haiku-20241022",
                max_tokens=2048,
                temperature=0.7,
            )
        except Exception:
            pass
    return get_groq_with_fallback()
