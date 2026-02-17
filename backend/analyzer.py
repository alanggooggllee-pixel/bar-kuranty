"""Call quality analysis using Claude API or Google Gemini (free fallback)."""

import os
import logging

logger = logging.getLogger(__name__)

ANALYSIS_PROMPT = """\
Ты — эксперт по контролю качества обслуживания в premium кафе-баре «Куранты» (Ташкент).

Проанализируй транскрипт телефонного звонка hostess по следующим критериям:

## Обязательные элементы звонка:
1. Приветствие: «Добрый день/вечер, кафе-бар Куранты, меня зовут [имя], чем могу помочь?»
2. Предупреждение о записи: «Звонок записывается для улучшения качества обслуживания»
3. Активное слушание — не перебивала гостя
4. Уточнение деталей бронирования: дата, время, количество гостей, рассадка
5. Предложение альтернатив, если запрос невозможен
6. Подтверждение брони — повторила все детали
7. Доп. информация — предложила меню, спросила о спецпожеланиях
8. Благодарность и прощание

## Критерии оценки тона:
- Приветливость и доброжелательность
- Уверенность и компетентность
- Терпеливость
- Отсутствие фамильярности

## Шкала оценки:
- A — все пункты соблюдены, тон безупречный
- B — незначительные отклонения
- C — существенные ошибки
- D — критические ошибки

## Формат ответа:
**Оценка**: [A/B/C/D]

**Соблюдённые пункты**:
- [список]

**Замечания**:
- [список замечаний с конкретными цитатами]

**Рекомендации**:
- [что улучшить]

**Краткое резюме**: [1-2 предложения]
"""


def _analyze_with_claude(transcript: str) -> str:
    """Analyze using Claude API (Anthropic)."""
    import anthropic

    client = anthropic.Anthropic(api_key=os.environ["ANTHROPIC_API_KEY"])

    message = client.messages.create(
        model="claude-sonnet-4-5-20250929",
        max_tokens=2000,
        messages=[
            {
                "role": "user",
                "content": f"{ANALYSIS_PROMPT}\n\n## Транскрипт звонка:\n\n{transcript}",
            }
        ],
    )

    return message.content[0].text


def _analyze_with_gemini(transcript: str) -> str:
    """Analyze using Google Gemini API (free tier)."""
    import requests

    api_key = os.environ["GEMINI_API_KEY"]
    model = os.environ.get("GEMINI_MODEL", "gemini-2.5-flash")
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"

    payload = {
        "contents": [
            {
                "parts": [
                    {"text": f"{ANALYSIS_PROMPT}\n\n## Транскрипт звонка:\n\n{transcript}"}
                ]
            }
        ]
    }

    import time
    for attempt in range(3):
        response = requests.post(url, json=payload, timeout=60)
        if response.status_code == 429:
            wait = 10 * (attempt + 1)
            logger.warning("Rate limited, waiting %ds (attempt %d/3)", wait, attempt + 1)
            time.sleep(wait)
            continue
        response.raise_for_status()
        data = response.json()
        return data["candidates"][0]["content"]["parts"][0]["text"]

    response.raise_for_status()  # raise last error


def analyze_transcript(transcript: str) -> str:
    """Analyze a call transcript. Uses Claude if available, falls back to Gemini."""
    logger.info("Analyzing transcript (%d characters)", len(transcript))

    if os.environ.get("ANTHROPIC_API_KEY"):
        logger.info("Using Claude API")
        result = _analyze_with_claude(transcript)
    elif os.environ.get("GEMINI_API_KEY"):
        logger.info("Using Gemini API (free)")
        result = _analyze_with_gemini(transcript)
    else:
        raise RuntimeError("No API key found. Set ANTHROPIC_API_KEY or GEMINI_API_KEY")

    logger.info("Analysis complete")
    return result
