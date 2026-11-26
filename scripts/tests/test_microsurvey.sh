#!/usr/bin/env bash
# Test helper for Freerouting micro-surveys on Linux/macOS.
set -euo pipefail

MODE="${1:-LocalMock}"
ADMIN_KEY="${ADMIN_KEY:-test-admin-secret}"
API_URL="${API_URL:-http://localhost:37864/v1}"
SURVEY_FILE="${SURVEY_FILE:-fixtures/surveys/sample-survey.json}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

case "$MODE" in
  ClearCache)
    CACHE_FILE="${XDG_DATA_HOME:-$HOME/.local/share}/freerouting/surveys.json"
    rm -f "$CACHE_FILE" "$HOME/Library/Application Support/freerouting/data/surveys.json"
    echo "Survey cache cleared."
    ;;

  LocalMock)
    echo "Configuring local micro-survey mock environment..."
    export FREEROUTING__SURVEYS__ACTIVE_SURVEY="$REPO_ROOT/$SURVEY_FILE"
    export FREEROUTING__SURVEYS__IGNORE_CACHE="true"
    echo "FREEROUTING__SURVEYS__IGNORE_CACHE set to true (no manual cache clearing needed)"
    echo "Launching Freerouting..."
    ./gradlew run
    ;;

  StartServer)
    echo "Starting Freerouting API server with survey admin key..."
    export FREEROUTING__SURVEYS__ADMIN_KEY="$ADMIN_KEY"
    ./gradlew run --args="--api.enabled=true"
    ;;

  ApiPublish)
    echo "Publishing survey to $API_URL/surveys/active..."
    curl -i -X POST "$API_URL/surveys/active" \
      -H "Content-Type: application/json" \
      -H "X-Survey-Admin-Key: $ADMIN_KEY" \
      -d @"$SURVEY_FILE"
    echo ""
    ;;

  ApiGet)
    echo "Querying active survey from $API_URL/surveys/active..."
    curl -i -X GET "$API_URL/surveys/active"
    echo ""
    ;;

  ApiRetire)
    echo "Retiring active survey at $API_URL/surveys/active..."
    curl -i -X DELETE "$API_URL/surveys/active" \
      -H "X-Survey-Admin-Key: $ADMIN_KEY"
    echo ""
    ;;

  *)
    echo "Unknown mode: $MODE"
    echo "Supported modes: LocalMock, ClearCache, StartServer, ApiPublish, ApiGet, ApiRetire"
    exit 1
    ;;
esac
