#!/usr/bin/env python3
"""Sync API key usage statistics from BigQuery to Google Sheets.

This script queries the Freerouting BigQuery analytics dataset (tables: api_usage,
job_lifecycle) to calculate per-API-key metrics:
  - Sessions Created (count of successful POST /v1/sessions/create)
  - Boards Started (count of successful POST /v1/jobs/{jobId}/start)
  - Boards Completed (count of jobs that finished with COMPLETED status)
  - Total API Calls (total HTTP requests recorded)
  - Last Active Date (timestamp of the most recent API call)
  - Last Host/Tool (e.g. KiCad/10, python-client)

It matches API keys from the Google Sheet by computing their SHA-256 hash (in lowercase hex),
and updates corresponding columns in the Google Sheet.

Prerequisites:
  pip install -r scripts/stats/requirements.txt
"""

import argparse
import hashlib
import json
import os
import sys
from typing import Any, Dict, List, Optional

try:
    import google.auth
    from google.cloud import bigquery
    from google.oauth2 import service_account
    import gspread
except ImportError:
    google_auth = None
    bigquery = None
    service_account = None
    gspread = None
else:
    google_auth = google.auth

DEFAULT_PROJECT_ID = "freerouting-analytics"
DEFAULT_DATASET_ID = "freerouting_application"

METRIC_COLUMNS = [
    "Sessions Created",
    "Boards Started",
    "Boards Completed",
    "Total API Calls",
    "Last Active",
    "Last Host/Tool",
]


def get_credentials(key_path_or_json: Optional[str] = None) -> Any:
    """Resolve Google service account credentials."""
    scopes = [
        "https://www.googleapis.com/auth/bigquery",
        "https://www.googleapis.com/auth/spreadsheets",
        "https://www.googleapis.com/auth/drive",
    ]

    raw = key_path_or_json or os.environ.get(
        "FREEROUTING__USAGE_AND_DIAGNOSTIC_DATA__BIGQUERY_SERVICE_ACCOUNT_KEY"
    ) or os.environ.get("GOOGLE_APPLICATION_CREDENTIALS")

    if not raw:
        # Fall back to default application credentials if available
        if google_auth is not None:
            creds, _ = google_auth.default(scopes=scopes)
            return creds
        return None

    if os.path.exists(raw):
        return service_account.Credentials.from_service_account_file(raw, scopes=scopes)

    # Raw JSON string in environment variable
    try:
        data = json.loads(raw)
        return service_account.Credentials.from_service_account_info(data, scopes=scopes)
    except json.JSONDecodeError as exc:
        raise ValueError(f"Failed to parse service account JSON from input/environment: {exc}")


def query_bigquery_stats(
    credentials: Any,
    project_id: str = DEFAULT_PROJECT_ID,
    dataset_id: str = DEFAULT_DATASET_ID,
) -> Dict[str, Dict[str, Any]]:
    """Execute aggregation query in BigQuery and return dict keyed by api_key_hash."""
    client = bigquery.Client(project=project_id, credentials=credentials)

    sql = f"""
    WITH api_requests AS (
      SELECT
        api_key_hash,
        profile_email,
        profile_id,
        environment_host,
        api_route,
        REGEXP_EXTRACT(api_path, r'v1/jobs/([a-f0-9\-]+)/') AS job_id,
        http_status,
        PARSE_TIMESTAMP('%Y-%m-%d %H:%M:%E*S UTC', timestamp) AS event_time
      FROM `{project_id}.{dataset_id}.api_usage`
      WHERE api_key_hash IS NOT NULL
    ),
    completed_jobs AS (
      SELECT DISTINCT
        job_id
      FROM `{project_id}.{dataset_id}.job_lifecycle`
      WHERE status IN ('SUCCEEDED', 'COMPLETED')
    )
    SELECT
      r.api_key_hash,
      ARRAY_AGG(r.environment_host IGNORE NULLS ORDER BY r.event_time DESC LIMIT 1)[SAFE_OFFSET(0)] AS last_environment_host,
      COUNT(DISTINCT CASE WHEN r.api_route IN ('POST v1/sessions/create', 'POST /v1/sessions/create') AND r.http_status = 200 THEN r.event_time END) AS sessions_created,
      COUNT(DISTINCT CASE WHEN (
        r.api_route IN ('PUT v1/jobs/{id}/start', 'POST /v1/jobs/{jobId}/start')
        OR r.api_route IN ('POST v1/autoroute', 'POST /v1/autoroute')
      ) AND r.http_status IN (200, 202) THEN COALESCE(r.job_id, CAST(r.event_time AS STRING)) END) AS boards_started,
      GREATEST(
        COUNT(DISTINCT CASE WHEN r.api_route IN ('PUT v1/jobs/{id}/start', 'POST /v1/jobs/{jobId}/start') AND r.http_status IN (200, 202) AND c.job_id IS NOT NULL THEN r.job_id END),
        COUNT(DISTINCT CASE WHEN (
          r.api_route IN ('GET v1/jobs/{id}/output', 'GET v1/jobs/{id}/output/json', 'GET /v1/jobs/{jobId}/output', 'GET /v1/jobs/{jobId}/output/json', 'GET /v1/jobs/{jobId}/output/file')
          OR r.api_route IN ('POST v1/autoroute', 'POST /v1/autoroute')
        ) AND r.http_status = 200 THEN COALESCE(r.job_id, CAST(r.event_time AS STRING)) END)
      ) AS boards_completed,
      COUNT(*) AS total_api_calls,
      MIN(r.event_time) AS first_used_at,
      MAX(r.event_time) AS last_used_at
    FROM api_requests r
    LEFT JOIN completed_jobs c ON r.job_id = c.job_id
    GROUP BY r.api_key_hash
    """

    print("Running BigQuery aggregation query...")
    query_job = client.query(sql)
    results = query_job.result()

    stats_by_hash: Dict[str, Dict[str, Any]] = {}
    for row in results:
        stats_by_hash[row.api_key_hash] = {
            "sessions_created": row.sessions_created or 0,
            "boards_started": row.boards_started or 0,
            "boards_completed": row.boards_completed or 0,
            "total_api_calls": row.total_api_calls or 0,
            "last_used_at": str(row.last_used_at)[:19] if row.last_used_at else "",
            "last_environment_host": row.last_environment_host or "",
        }

    print(f"Retrieved usage metrics for {len(stats_by_hash)} unique API key hashes.")
    return stats_by_hash


def sync_to_google_sheet(
    credentials: Any,
    spreadsheet_id_or_url: str,
    stats_by_hash: Dict[str, Dict[str, Any]],
    dry_run: bool = False,
) -> None:
    """Match API keys from the sheet, compute SHA-256, and update columns."""
    gc = gspread.authorize(credentials)

    if spreadsheet_id_or_url.startswith("https://"):
        sheet = gc.open_by_url(spreadsheet_id_or_url).sheet1
    else:
        sheet = gc.open_by_key(spreadsheet_id_or_url).sheet1

    all_values = sheet.get_all_values()
    if not all_values:
        print("Google Sheet is empty.")
        return

    headers = all_values[0]
    try:
        api_key_col_idx = headers.index("API Key")
    except ValueError:
        for idx, h in enumerate(headers):
            if "api key" in h.lower():
                api_key_col_idx = idx
                break
        else:
            raise ValueError("Could not find 'API Key' column in Google Sheet headers: " + str(headers))

    # Ensure metric columns exist in header
    col_indices: Dict[str, int] = {}
    missing_cols: List[str] = []
    for col_name in METRIC_COLUMNS:
        if col_name in headers:
            col_indices[col_name] = headers.index(col_name)
        else:
            missing_cols.append(col_name)

    if missing_cols and not dry_run:
        start_col = len(headers) + 1
        for i, col_name in enumerate(missing_cols):
            col_indices[col_name] = len(headers) + i
            sheet.update_cell(1, start_col + i, col_name)
        print(f"Added missing columns to sheet header: {missing_cols}")

    updates_to_perform = []
    matched_count = 0

    print(f"\nProcessing {len(all_values) - 1} rows from Google Sheet...")
    for row_idx, row in enumerate(all_values[1:], start=2):
        if len(row) <= api_key_col_idx:
            continue
        raw_key = row[api_key_col_idx].strip()
        if not raw_key:
            continue

        key_hash = hashlib.sha256(raw_key.encode("utf-8")).hexdigest()
        metrics = stats_by_hash.get(key_hash)

        if metrics:
            matched_count += 1
            row_data = [
                metrics["sessions_created"],
                metrics["boards_started"],
                metrics["boards_completed"],
                metrics["total_api_calls"],
                metrics["last_used_at"],
                metrics["last_environment_host"],
            ]
            print(
                f"Row {row_idx}: Key ending in ...{raw_key[-6:]} -> "
                f"Sessions: {metrics['sessions_created']}, "
                f"Started: {metrics['boards_started']}, "
                f"Completed: {metrics['boards_completed']}, "
                f"Calls: {metrics['total_api_calls']}, "
                f"Last: {metrics['last_used_at']}"
            )
            updates_to_perform.append((row_idx, row_data))
        else:
            # Key has zero recorded calls
            row_data = [0, 0, 0, 0, "Never", ""]
            updates_to_perform.append((row_idx, row_data))

    if dry_run:
        print(f"\n[DRY RUN] Would update {len(updates_to_perform)} rows ({matched_count} active keys). No changes written.")
        return

    # Write batch updates to the sheet
    if updates_to_perform:
        print(f"\nWriting updates for {len(updates_to_perform)} rows to Google Sheet...")
        cells_to_update = []
        for row_idx, row_data in updates_to_perform:
            for metric_idx, col_name in enumerate(METRIC_COLUMNS):
                col_num = col_indices[col_name] + 1
                cells_to_update.append(
                    gspread.Cell(row=row_idx, col=col_num, value=row_data[metric_idx])
                )

        sheet.update_cells(cells_to_update)
        print("Successfully updated Google Sheet with API usage metrics!")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Sync Freerouting API usage stats from BigQuery to Google Sheets"
    )
    parser.add_argument(
        "--spreadsheet-id",
        "-s",
        help="Google Sheets Spreadsheet ID or full URL",
        default=os.environ.get("FREEROUTING_API_KEY_SPREADSHEET_ID")
        or os.environ.get("FREEROUTING_API_KEY_SPREADSHEET_URL"),
    )
    parser.add_argument(
        "--service-account-key",
        "-k",
        help="Path to service account JSON file, or raw JSON content",
        default=None,
    )
    parser.add_argument(
        "--project",
        default=DEFAULT_PROJECT_ID,
        help=f"BigQuery project ID (default: {DEFAULT_PROJECT_ID})",
    )
    parser.add_argument(
        "--dataset",
        default=DEFAULT_DATASET_ID,
        help=f"BigQuery dataset ID (default: {DEFAULT_DATASET_ID})",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Query BigQuery and compute matches, but do not write to Google Sheet",
    )

    args = parser.parse_args()

    if not bigquery or not gspread:
        print(
            "Required libraries not installed. Please run:\n"
            "  pip install google-cloud-bigquery gspread google-auth",
            file=sys.stderr,
        )
        sys.exit(1)

    if not args.spreadsheet_id:
        print(
            "Error: Spreadsheet ID or URL is required.\n"
            "Specify via --spreadsheet-id <ID> or set FREEROUTING_API_KEY_SPREADSHEET_ID env var.",
            file=sys.stderr,
        )
        sys.exit(1)

    try:
        credentials = get_credentials(args.service_account_key)
        stats = query_bigquery_stats(
            credentials=credentials,
            project_id=args.project,
            dataset_id=args.dataset,
        )
        sync_to_google_sheet(
            credentials=credentials,
            spreadsheet_id_or_url=args.spreadsheet_id,
            stats_by_hash=stats,
            dry_run=args.dry_run,
        )
    except Exception as exc:
        print(f"Error during sync: {exc}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
