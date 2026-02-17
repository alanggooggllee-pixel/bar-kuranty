"""Send analysis reports to Telegram."""

import os
import logging

import requests

logger = logging.getLogger(__name__)

MAX_MESSAGE_LENGTH = 4096


def send_report(analysis: str, filename: str) -> None:
    """Send call analysis report to Telegram chat."""
    bot_token = os.environ["TELEGRAM_BOT_TOKEN"]
    chat_id = os.environ["TELEGRAM_CHAT_ID"]

    header = f"📞 Анализ звонка\n📁 {filename}\n{'—' * 30}\n\n"
    message = header + analysis

    if len(message) > MAX_MESSAGE_LENGTH:
        message = message[: MAX_MESSAGE_LENGTH - 3] + "..."

    url = f"https://api.telegram.org/bot{bot_token}/sendMessage"

    # Try Markdown first, fall back to plain text if parsing fails
    payload = {
        "chat_id": chat_id,
        "text": message,
        "parse_mode": "Markdown",
    }

    response = requests.post(url, json=payload, timeout=30)

    if response.status_code == 400 and "can't parse entities" in response.text:
        logger.warning("Markdown parse failed, sending as plain text")
        payload.pop("parse_mode")
        response = requests.post(url, json=payload, timeout=30)

    if response.status_code == 200:
        logger.info("Report sent to Telegram for %s", filename)
    else:
        logger.error(
            "Failed to send Telegram message: %s %s",
            response.status_code,
            response.text,
        )
        response.raise_for_status()
