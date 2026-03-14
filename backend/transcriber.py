"""Speech-to-text transcription using Gemini or OpenAI Whisper API."""

import base64
import io
import os
import logging

import requests

logger = logging.getLogger(__name__)


def _transcribe_with_whisper(audio_content: bytes, filename: str) -> str:
    """Transcribe using OpenAI Whisper API."""
    from openai import OpenAI

    client = OpenAI(api_key=os.environ["OPENAI_API_KEY"])
    audio_file = io.BytesIO(audio_content)
    audio_file.name = filename

    response = client.audio.transcriptions.create(
        model="whisper-1",
        file=audio_file,
        response_format="text",
    )
    return response.strip()


def _transcribe_with_gemini(audio_content: bytes, filename: str) -> str:
    """Transcribe using Gemini API (free, supports audio input)."""
    api_key = os.environ["GEMINI_API_KEY"]
    model = os.environ.get("GEMINI_MODEL", "gemini-2.5-flash")
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"

    ext = filename.rsplit(".", 1)[-1].lower()
    mime_map = {"m4a": "audio/mp4", "mp3": "audio/mpeg", "wav": "audio/wav", "aac": "audio/aac"}
    mime_type = mime_map.get(ext, "audio/mp4")

    audio_b64 = base64.b64encode(audio_content).decode("utf-8")

    payload = {
        "contents": [
            {
                "parts": [
                    {"inline_data": {"mime_type": mime_type, "data": audio_b64}},
                    {"text": "Транскрибируй это аудио дословно. Выведи только текст разговора, без комментариев. Если есть несколько говорящих, обозначь их."},
                ]
            }
        ]
    }

    import time
    for attempt in range(3):
        response = requests.post(url, json=payload, timeout=180)
        if response.status_code == 429:
            wait = 10 * (attempt + 1)
            logger.warning("Rate limited, waiting %ds (attempt %d/3)", wait, attempt + 1)
            time.sleep(wait)
            continue
        response.raise_for_status()
        data = response.json()

        candidates = data.get("candidates", [])
        if not candidates:
            logger.warning("Gemini returned no candidates for %s", filename)
            return ""
        content = candidates[0].get("content", {})
        parts = content.get("parts", [])
        if not parts or "text" not in parts[0]:
            finish_reason = candidates[0].get("finishReason", "unknown")
            logger.warning("Gemini returned no text (finishReason=%s) for %s", finish_reason, filename)
            return ""
        return parts[0]["text"].strip()

    response.raise_for_status()


def transcribe_audio(audio_content: bytes, filename: str) -> str:
    """Transcribe audio. Uses Whisper if available, falls back to Gemini."""
    logger.info("Transcribing %s (%d bytes)", filename, len(audio_content))

    if os.environ.get("OPENAI_API_KEY"):
        logger.info("Using Whisper API")
        transcript = _transcribe_with_whisper(audio_content, filename)
    elif os.environ.get("GEMINI_API_KEY"):
        logger.info("Using Gemini API for transcription")
        transcript = _transcribe_with_gemini(audio_content, filename)
    else:
        raise RuntimeError("No API key found. Set OPENAI_API_KEY or GEMINI_API_KEY")

    logger.info("Transcription complete: %d characters", len(transcript))
    return transcript
