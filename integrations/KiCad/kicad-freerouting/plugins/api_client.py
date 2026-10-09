# ---------------------------------------------------------------------------
# api_client.py — Freerouting REST API client
# ---------------------------------------------------------------------------
# ``FreeroutingApiClient`` wraps the Freerouting v1 REST API using only
# the Python standard library (``urllib``).  It handles:
#   * HTTP requests with JSON bodies and default headers.
#   * Session and job lifecycle (create, enqueue, upload, start, poll).
#   * Downloading routing results as KiCad JSON.
# ---------------------------------------------------------------------------

import socket
import urllib.error
import urllib.parse
import urllib.request
import uuid
import logging

# Ensure raw sockets can never block indefinitely on Windows
socket.setdefaulttimeout(30.0)

try:
    from .config import (
        API_JOB_TIMEOUT,
        API_POLL_INTERVAL,
        API_REQUEST_TIMEOUT,
        DEFAULT_FR_API_BASE_URL,
    )
except (ImportError, ValueError):
    from config import (
        API_JOB_TIMEOUT,
        API_POLL_INTERVAL,
        API_REQUEST_TIMEOUT,
        DEFAULT_FR_API_BASE_URL,
    )

logger = logging.getLogger("freerouting")


def _flush_log_handlers():
    """Flushes file-based log handlers without blocking on unconsumed stdout pipes."""
    for h in logging.getLogger().handlers:
        try:
            if isinstance(h, logging.FileHandler):
                h.flush()
        except Exception as e:
            logger.debug("Failed to flush logger handler: %s", e)
    for h in logging.root.handlers:
        try:
            if isinstance(h, logging.FileHandler):
                h.flush()
        except Exception as e:
            logger.debug("Failed to flush root handler: %s", e)


class FreeroutingApiClient:
    """Minimal client for the Freerouting REST API (v1).

    Designed for the JSON/API bridge workflow where the KiCad plugin acts as
    a local client against a Freerouting server running on localhost.

    All public methods return ``None`` or ``False`` on failure and log
    the error to stdout.
    """

    def __init__(self, base_url=DEFAULT_FR_API_BASE_URL, api_key="", profile_id=None):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.profile_id = profile_id or str(uuid.uuid4())
        self._opener = self._build_opener()

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _build_opener(self):
        """Build a urllib opener bypassing system proxy."""
        return urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def _request(self, method, path, data=None, timeout=API_REQUEST_TIMEOUT):
        """Make an HTTP request and return ``(status_code, body)``."""
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "Freerouting-Environment-Host": "KiCad/10",
            "Freerouting-Profile-ID": self.profile_id,
            "Connection": "close",
        }
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"

        body_bytes = None
        if data is not None:
            if isinstance(data, bytes):
                body_bytes = data
            elif isinstance(data, str):
                body_bytes = data.encode("utf-8")
            else:
                body_bytes = __import__("json").dumps(data).encode("utf-8")

        url = f"{self.base_url}{path}"
        req = urllib.request.Request(url, data=body_bytes, headers=headers, method=method)

        opener = getattr(self, "_opener", None) or urllib.request.build_opener(urllib.request.ProxyHandler({}))
        try:
            with opener.open(req, timeout=timeout) as resp:
                body_str = resp.read().decode("utf-8", errors="replace")
                return resp.status, body_str
        except urllib.error.HTTPError as e:
            err_body = e.read().decode("utf-8", errors="replace")
            return e.code, err_body
        except Exception as e:
            raise ConnectionError(f"Could not connect to {self.base_url}{path}: {e}") from e

    # ------------------------------------------------------------------
    # Server health
    # ------------------------------------------------------------------

    def health_check(self):
        """Return ``True`` if the API server is reachable."""
        try:
            status, _ = self._request("GET", "/v1/system/status", timeout=5)
            return status == 200
        except Exception:
            return False

    # ------------------------------------------------------------------
    # Session management
    # ------------------------------------------------------------------

    def create_session(self, host_name="KiCad"):
        """Create a new routing session.

        Returns:
            Session ID string, or ``None`` on failure.
        """
        path = f"/v1/sessions/create?host={urllib.parse.quote(host_name)}"
        status, body = self._request("POST", path)
        if status == 200:
            try:
                data = __import__("json").loads(body)
                return data.get("sessionId") or data.get("id") or data.get("session_id")
            except __import__("json").JSONDecodeError:
                return body.strip().strip('"')
        logger.error(f"Failed to create session: HTTP {status} — {body}")
        return None

    def set_monitored_session(self, session_id):
        """Bind the session to the GUI visualizer (if GUI is enabled).

        Returns:
            ``True`` if the server accepted the request.
        """
        status, _ = self._request("PUT", f"/v1/sessions/{session_id}/monitor")
        return status == 200

    # ------------------------------------------------------------------
    # Job management
    # ------------------------------------------------------------------

    def enqueue_job(self, session_id, job_name="KiCad JSON Job"):
        """Enqueue a new job in the given session.

        Returns:
            Job ID string, or ``None`` on failure.
        """
        payload = {"session_id": session_id, "name": job_name}
        status, body = self._request(
            "POST", "/v1/jobs/enqueue",
            data=__import__("json").dumps(payload),
        )
        if status == 200:
            try:
                data = __import__("json").loads(body)
                return data.get("id") or data.get("jobId") or data.get("job_id")
            except __import__("json").JSONDecodeError:
                return body.strip().strip('"')
        logger.error(f"Failed to enqueue job: HTTP {status} — {body}")
        return None

    def upload_json_input(self, job_id, json_str):
        """Upload KiCad JSON board data to a job.

        Returns:
            ``True`` on success.
        """
        status, body = self._request("POST", f"/v1/jobs/{job_id}/input/json", data=json_str)
        if status == 200:
            return True
        logger.error(f"Failed to upload JSON input: HTTP {status} — {body}")
        return False

    def start_job(self, job_id):
        """Start a queued job.

        Returns:
            ``True`` on success.
        """
        status, body = self._request("PUT", f"/v1/jobs/{job_id}/start")
        if status == 200:
            return True
        logger.error(f"Failed to start job: HTTP {status} — {body}")
        return False

    def cancel_job(self, job_id):
        """Cancel a running job.

        Returns:
            ``True`` on success.
        """
        status, _ = self._request("PUT", f"/v1/jobs/{job_id}/cancel")
        return status == 200

    def get_job_status(self, job_id):
        """Get the current job status.

        Returns:
            Parsed JSON dict, or ``None`` on failure.
        """
        try:
            status, body = self._request("GET", f"/v1/jobs/{job_id}?compact=true", timeout=10)
            if status == 200:
                try:
                    return __import__("json").loads(body)
                except __import__("json").JSONDecodeError:
                    return None
            else:
                logger.info(f"get_job_status for {job_id} returned HTTP {status}: {body[:200]}")
        except Exception as e:
            logger.info(f"Error querying job status for {job_id}: {e}")
        return None

    def download_json_output(self, job_id, max_retries=5, retry_delay=0.5):
        """Download the routing result as KiCad JSON with retries.

        Returns:
            JSON string, or ``None`` on failure.
        """
        import time as _time

        for attempt in range(1, max_retries + 1):
            try:
                status, body = self._request(
                    "GET", f"/v1/jobs/{job_id}/output/json", timeout=30
                )
                if status == 200 and body:
                    return body
                elif status in (202, 204):
                    logger.debug(
                        f"JSON output for {job_id} not ready yet (HTTP {status}), retrying ({attempt}/{max_retries})..."
                    )
                else:
                    logger.warning(
                        f"Download JSON output attempt {attempt}/{max_retries} returned HTTP {status}"
                    )
            except Exception as dl_err:
                logger.warning(
                    f"Download JSON output attempt {attempt}/{max_retries} failed: {dl_err}"
                )
            if attempt < max_retries:
                _time.sleep(retry_delay)

        logger.error(f"Failed to download JSON output for {job_id} after {max_retries} attempts.")
        return None

    # ------------------------------------------------------------------
    # Blocking wait
    # ------------------------------------------------------------------

    def wait_for_job_completion(
        self,
        job_id,
        poll_interval=None,
        timeout=None,
        progress_callback=None,
        cancel_event=None,
        completion_event=None,
        terminal_state_holder=None,
    ):
        """Poll the job until it completes or times out.

        Args:
            poll_interval: Seconds between polls (default ``API_POLL_INTERVAL``).
            timeout: Maximum seconds to wait (default ``API_JOB_TIMEOUT``).
            progress_callback: Optional callable ``f(state, elapsed, info)``.
            cancel_event: Optional ``threading.Event`` to signal cancellation.
            completion_event: Optional ``threading.Event`` to signal completion from log tailer.
            terminal_state_holder: Optional list ``[state_string]`` holding terminal state from log.

        Returns:
            ``(success, output_json)`` tuple.
        """
        import time as _time
        from datetime import datetime, timezone

        poll_interval = poll_interval or API_POLL_INTERVAL
        timeout = timeout or API_JOB_TIMEOUT
        start = _time.time()
        consecutive_none = 0

        while True:
            if cancel_event and cancel_event.is_set():
                end_utc = datetime.now(timezone.utc).isoformat()
                logger.info(f"Cancellation requested for job {job_id} (elapsed: {_time.time() - start:.2f}s, finished at UTC: {end_utc}).")
                self.cancel_job(job_id)
                return False, None

            if completion_event and completion_event.is_set():
                term_state = terminal_state_holder[0] if terminal_state_holder else "COMPLETED"
                elapsed = _time.time() - start
                logger.info(f"Completion signaled via log tailer ({term_state}) for job {job_id}.")
                if progress_callback:
                    try:
                        progress_callback(term_state, elapsed, {"state": term_state})
                    except Exception as cb_err:
                        logger.debug(f"Progress callback error: {cb_err}")
                if term_state == "COMPLETED":
                    end_utc = datetime.now(timezone.utc).isoformat()
                    logger.info(f"Job {job_id} completed successfully (elapsed: {elapsed:.2f}s, finished at UTC: {end_utc}). Downloading output...")
                    _flush_log_handlers()
                    out_data = self.download_json_output(job_id)
                    if out_data:
                        logger.info(f"Downloaded output for job {job_id} ({len(out_data)} bytes).")
                        _flush_log_handlers()
                        return True, out_data
                    else:
                        logger.error(f"Failed to download output for job {job_id}.")
                        _flush_log_handlers()
                        return False, None
                else:
                    return False, None

            elapsed = _time.time() - start
            if elapsed > timeout:
                end_utc = datetime.now(timezone.utc).isoformat()
                logger.error(f"Job {job_id} timed out after {timeout}s (elapsed: {elapsed:.2f}s, finished at UTC: {end_utc}).")
                return False, None

            info = self.get_job_status(job_id)
            if info is None:
                consecutive_none += 1
                logger.info(f"Could not get job status for {job_id} (attempt {consecutive_none}/15)")
                _flush_log_handlers()
                if consecutive_none >= 15:
                    logger.info(f"Checking if job {job_id} output is already available before failing...")
                    out_data = self.download_json_output(job_id)
                    if out_data:
                        logger.info(f"Output for {job_id} downloaded successfully ({len(out_data)} bytes).")
                        return True, out_data
                    end_utc = datetime.now(timezone.utc).isoformat()
                    logger.error(f"Could not get job status for {job_id} after {consecutive_none} attempts (elapsed: {elapsed:.2f}s, finished at UTC: {end_utc}).")
                    _flush_log_handlers()
                    return False, None
                _time.sleep(poll_interval)
                continue
            consecutive_none = 0

            state = info.get("state", "UNKNOWN")
            logger.info(f"  Job {job_id} state: {state} ({elapsed:.0f}s elapsed)")
            _flush_log_handlers()

            if progress_callback:
                try:
                    progress_callback(state, elapsed, info)
                except Exception as cb_err:
                    logger.debug(f"Progress callback exception: {cb_err}")

            if state in ("COMPLETED", "FINISHED", "DONE"):
                end_utc = datetime.now(timezone.utc).isoformat()
                logger.info(f"Job {job_id} completed successfully (elapsed: {elapsed:.2f}s, finished at UTC: {end_utc}). Downloading output...")
                _flush_log_handlers()
                out_data = self.download_json_output(job_id)
                if out_data:
                    logger.info(f"Downloaded output for job {job_id} ({len(out_data)} bytes).")
                    _flush_log_handlers()
                    return True, out_data
                else:
                    logger.error(f"Failed to download output for job {job_id}.")
                    _flush_log_handlers()
                    return False, None
            elif state in ("TERMINATED", "CANCELLED", "TIMED_OUT", "INVALID", "ERROR"):
                end_utc = datetime.now(timezone.utc).isoformat()
                logger.error(f"Job {job_id} ended with state: {state} (elapsed: {elapsed:.2f}s, finished at UTC: {end_utc}).")
                _flush_log_handlers()
                return False, None

            _time.sleep(poll_interval)