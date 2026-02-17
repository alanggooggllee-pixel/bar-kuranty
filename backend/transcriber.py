"""Speech-to-text transcription using OpenAI Whisper API."""

import io
import os
import logging

from openai import OpenAI

logger = logging.getLogger(__name__)

WHISPER_MODEL = "whisper-1"


def transcribe_audio(audio_content: bytes, filename: str) -> str:
    """Transcribe audio bytes using OpenAI Whisper API.

    Whisper handles Russian, Uzbek, and English automatically.
    """
    client = OpenAI(api_key=os.environ["OPENAI_API_KEY"])

    audio_file = io.BytesIO(audio_content)
    audio_file.name = filename

    logger.info("Transcribing %s (%d bytes)", filename, len(audio_content))

    response = client.audio.transcriptions.create(
        model=WHISPER_MODEL,
        file=audio_file,
        response_format="text",
    )

    transcript = response.strip()
    logger.info("Transcription complete: %d characters", len(transcript))
    return transcript
