@echo off
rem Run the API key usage sync locally
rem Requires FREEROUTING__API_SERVER__AUTHENTICATION__GOOGLE_SHEETS__SHEET_URL,
rem FREEROUTING__API_SERVER__AUTHENTICATION__GOOGLE_SHEETS__GOOGLE_API_KEY, and
rem FREEROUTING__USAGE_AND_DIAGNOSTIC_DATA__BIGQUERY_SERVICE_ACCOUNT_KEY set in environment.

python "%~dp0sync_api_key_usage_to_sheets.py" %*
