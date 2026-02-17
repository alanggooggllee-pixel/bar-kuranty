"""Cloud Function entry point: process new call recordings from Google Drive."""

import io
import os
import json
import logging

import functions_framework
from googleapiclient.discovery import build
from googleapiclient.http import MediaIoBaseDownload
from google.oauth2 import service_account

from transcriber import transcribe_audio
from analyzer import analyze_transcript
from telegram_bot import send_report

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

SCOPES = ["https://www.googleapis.com/auth/drive.readonly"]
PROCESSED_FILE = "/tmp/processed_files.json"


def get_drive_service():
    """Build Google Drive API service using service account credentials."""
    creds_json = os.environ.get("GOOGLE_CREDENTIALS_JSON")
    if creds_json:
        info = json.loads(creds_json)
        creds = service_account.Credentials.from_service_account_info(info, scopes=SCOPES)
    else:
        creds = service_account.Credentials.from_service_account_file(
            os.environ.get("GOOGLE_APPLICATION_CREDENTIALS", "credentials.json"),
            scopes=SCOPES,
        )
    return build("drive", "v3", credentials=creds)


def load_processed_ids() -> set:
    """Load set of already-processed file IDs."""
    try:
        with open(PROCESSED_FILE) as f:
            return set(json.load(f))
    except (FileNotFoundError, json.JSONDecodeError):
        return set()


def save_processed_ids(ids: set) -> None:
    """Persist processed file IDs."""
    with open(PROCESSED_FILE, "w") as f:
        json.dump(list(ids), f)


def list_new_recordings(service, folder_id: str, processed_ids: set) -> list:
    """List audio files in the Drive folder that haven't been processed yet."""
    query = (
        f"'{folder_id}' in parents and "
        "mimeType contains 'audio/' and "
        "trashed = false"
    )
    results = (
        service.files()
        .list(q=query, fields="files(id, name, createdTime)", orderBy="createdTime desc", pageSize=20)
        .execute()
    )
    files = results.get("files", [])
    return [f for f in files if f["id"] not in processed_ids]


def download_file(service, file_id: str) -> bytes:
    """Download a file from Google Drive by ID."""
    request = service.files().get_media(fileId=file_id)
    buffer = io.BytesIO()
    downloader = MediaIoBaseDownload(buffer, request)
    done = False
    while not done:
        _, done = downloader.next_chunk()
    return buffer.getvalue()


def process_recording(service, file_info: dict) -> None:
    """Full pipeline: download → transcribe → analyze → report."""
    file_id = file_info["id"]
    filename = file_info["name"]

    logger.info("Processing recording: %s", filename)

    audio_content = download_file(service, file_id)
    logger.info("Downloaded %s (%d bytes)", filename, len(audio_content))

    transcript = transcribe_audio(audio_content, filename)
    if not transcript:
        logger.warning("Empty transcript for %s, skipping", filename)
        return

    analysis = analyze_transcript(transcript)

    send_report(analysis, filename)
    logger.info("Completed processing: %s", filename)


@functions_framework.http
def handle_request(request):
    """HTTP Cloud Function entry point, triggered by Cloud Scheduler."""
    folder_id = os.environ["GOOGLE_DRIVE_FOLDER_ID"]

    service = get_drive_service()
    processed_ids = load_processed_ids()

    new_files = list_new_recordings(service, folder_id, processed_ids)

    if not new_files:
        logger.info("No new recordings found")
        return "No new recordings", 200

    results = []
    for file_info in new_files:
        try:
            process_recording(service, file_info)
            processed_ids.add(file_info["id"])
            results.append(f"OK: {file_info['name']}")
        except Exception:
            logger.exception("Failed to process %s", file_info["name"])
            results.append(f"FAIL: {file_info['name']}")

    save_processed_ids(processed_ids)
    return "\n".join(results), 200


if __name__ == "__main__":
    """Local testing: run the pipeline directly."""
    import sys

    if len(sys.argv) < 2:
        print("Usage: python main.py <path-to-audio-file>")
        sys.exit(1)

    audio_path = sys.argv[1]
    with open(audio_path, "rb") as f:
        audio_data = f.read()

    transcript = transcribe_audio(audio_data, os.path.basename(audio_path))
    print(f"\n--- Транскрипт ---\n{transcript}\n")

    analysis = analyze_transcript(transcript)
    print(f"\n--- Анализ ---\n{analysis}\n")

    if os.environ.get("TELEGRAM_BOT_TOKEN"):
        send_report(analysis, os.path.basename(audio_path))
        print("Report sent to Telegram!")
