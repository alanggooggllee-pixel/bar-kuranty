"""Call quality analysis using Claude API or Google Gemini (free fallback)."""

import os
import logging

logger = logging.getLogger(__name__)

ANALYSIS_PROMPT = """\
Ты — эксперт по контролю качества обслуживания в premium кафе-баре «Куранты» (Ташкент).

## Шаг 1: Определи тип звонка

Внимательно прочитай транскрипт и определи тип звонка по содержанию:

- **Бронирование** — если гость хочет забронировать столик (ключевые слова: столик, бронь, резерв, забронировать, место, терраса, зал)
- **Заказ на вынос** — если гость хочет забрать заказ сам (ключевые слова: с собой, на вынос, заберу, самовывоз, take-away)
- **Заказ на доставку** — если гость хочет доставку (ключевые слова: доставка, привезти, доставьте, адрес, курьер)
- **Другое** — если звонок не подходит ни под одну категорию (вопросы, жалобы, и т.д.)

## Шаг 2: Проанализируй по соответствующему стандарту

### Стандарт для БРОНИРОВАНИЯ:
1. Приветствие: «Добрый день/вечер, кафе-бар Куранты, меня зовут [имя], чем могу помочь?»
2. Активное слушание — не перебивала гостя
3. Уточнение деталей: дата, время, количество гостей, рассадка (зал/терраса/бар)
4. Предложение альтернатив, если запрос невозможен
5. Подтверждение брони — повторила все детали
6. Благодарность и прощание

### Стандарт для ЗАКАЗА НА ВЫНОС:
1. Приветствие: «Добрый день/вечер, кафе-бар Куранты, меня зовут [имя], чем могу помочь?»
2. Активное слушание — не перебивала гостя
3. Приём заказа: записала все позиции (блюда, напитки, количество)
4. Уточнение деталей и пожеланий (без лука, острое, и т.д.)
5. Время готовности — назвала примерное время
6. Сумма заказа — озвучила итоговую стоимость
7. Подтверждение — повторила заказ целиком
8. Имя гостя — спросила имя для заказа
9. Благодарность и прощание

### Стандарт для ЗАКАЗА НА ДОСТАВКУ:
1. Приветствие: «Добрый день/вечер, кафе-бар Куранты, меня зовут [имя], чем могу помочь?»
2. Активное слушание — не перебивала гостя
3. Приём заказа: записала все позиции
4. Уточнение деталей и пожеланий
5. Адрес доставки — уточнила полный адрес, ориентиры
6. Контактный телефон — подтвердила номер для курьера
7. Стоимость — озвучила сумму заказа + доставка
8. Время доставки — назвала примерное время
9. Подтверждение — повторила заказ и адрес
10. Благодарность и прощание

Примечание: предупреждение о записи звонка воспроизводится автоматически, hostess НЕ обязана его произносить.

## Критерии оценки тона (для всех типов):
- Приветливость и доброжелательность
- Уверенность и компетентность
- Терпеливость
- Отсутствие фамильярности

## Шкала оценки:
- A — все пункты соблюдены, тон безупречный
- B — незначительные отклонения (1-2 пункта)
- C — существенные ошибки
- D — критические ошибки

## Формат ответа:

**Тип звонка**: [Бронирование / Заказ на вынос / Заказ на доставку / Другое]

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
