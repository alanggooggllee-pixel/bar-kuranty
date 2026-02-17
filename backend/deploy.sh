#!/bin/bash
# Deploy the Cloud Function to Google Cloud

set -euo pipefail

PROJECT_ID="${GCP_PROJECT_ID:?Set GCP_PROJECT_ID}"
REGION="${GCP_REGION:-us-central1}"
FUNCTION_NAME="call-quality-analyzer"

echo "Deploying $FUNCTION_NAME to $PROJECT_ID ($REGION)..."

gcloud functions deploy "$FUNCTION_NAME" \
  --project="$PROJECT_ID" \
  --region="$REGION" \
  --runtime=python311 \
  --trigger-http \
  --entry-point=handle_request \
  --allow-unauthenticated=false \
  --memory=512MB \
  --timeout=300s \
  --set-env-vars="GOOGLE_DRIVE_FOLDER_ID=${GOOGLE_DRIVE_FOLDER_ID:?Set GOOGLE_DRIVE_FOLDER_ID}" \
  --set-secrets="OPENAI_API_KEY=OPENAI_API_KEY:latest,ANTHROPIC_API_KEY=ANTHROPIC_API_KEY:latest,TELEGRAM_BOT_TOKEN=TELEGRAM_BOT_TOKEN:latest,TELEGRAM_CHAT_ID=TELEGRAM_CHAT_ID:latest"

echo ""
echo "Setting up Cloud Scheduler (every 5 minutes)..."

FUNCTION_URL=$(gcloud functions describe "$FUNCTION_NAME" \
  --project="$PROJECT_ID" \
  --region="$REGION" \
  --format='value(httpsTrigger.url)')

gcloud scheduler jobs create http "trigger-$FUNCTION_NAME" \
  --project="$PROJECT_ID" \
  --location="$REGION" \
  --schedule="*/5 * * * *" \
  --uri="$FUNCTION_URL" \
  --http-method=GET \
  --oidc-service-account-email="${PROJECT_ID}@appspot.gserviceaccount.com" \
  2>/dev/null || \
gcloud scheduler jobs update http "trigger-$FUNCTION_NAME" \
  --project="$PROJECT_ID" \
  --location="$REGION" \
  --schedule="*/5 * * * *" \
  --uri="$FUNCTION_URL" \
  --http-method=GET \
  --oidc-service-account-email="${PROJECT_ID}@appspot.gserviceaccount.com"

echo ""
echo "Deployment complete!"
echo "Function URL: $FUNCTION_URL"
