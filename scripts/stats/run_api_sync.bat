@echo off
rem Run the API key usage sync locally
rem Requires FREEROUTING_API_KEY_SPREADSHEET_ID and
rem FREEROUTING__USAGE_AND_DIAGNOSTIC_DATA__BIGQUERY_SERVICE_ACCOUNT_KEY set in environment.

python "%~dp0sync_api_key_usage_to_sheets.py" %*
