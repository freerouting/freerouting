# ---------------------------------------------------------------------------
# api_client.py — Freerouting REST API client
# ---------------------------------------------------------------------------
# ``FreeroutingApiClient`` wraps the Freerouting v1 REST API using only
# the Python standard library (``urllib``).  It handles:
#   * HTTP requests with JSON bodies and default headers.
#   * Session and job lifecycle (create, enqueue, upload, start, poll).
#   * Downloading routing results as KiCad JSON.
# ---------------------------------------------------------------------------

import urllib.error
import urllib.parse
import urllib.request
import uuid

from .config import (
    API_JOB_TIMEOUT,
    API_POLL_INTERVAL,
    API_REQUEST_TIMEOUT,
    DEFAULT_FR_API_BASE_URL,
)


class FreeroutingApiClient:
    """Minimal client for the Freerouting REST API (v1).

    Designed for the IPC bridge workflow where the KiCad plugin acts as
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
        """Build a urllib opener with default headers."""
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "Freerouting-Environment-Host": "KiCad/10",
            "Freerouting-Profile-ID": self.profile_id,
        }
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"
        opener = urllib.request.build_opener(urllib.request.HTTPHandler())
        opener.addheaders = list(headers.items())
        return opener

    def _request(self, method, path, data=None, timeout=API_REQUEST_TIMEOUT):
        """Make an HTTP request and return ``(status_code, body)``."""
        url = f"{self.base_url}{path}"
        req = urllib.request.Request(url, method=method)
        for key, value in self._opener.addheaders:
            req.add_header(key, value)
        if data is not None:
            req.data = (
                data.encode("utf-8")
                if isinstance(data, (str, bytes))
                else __import__("json").dumps(data).encode("utf-8")
            )
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return resp.status, resp.read().decode("utf-8")
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8") if e.fp else ""
            return e.code, body
        except urllib.error.URLError as e:
            raise ConnectionError(f"Could not connect to {url}: {e}")

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
        print(f"Failed to create session: HTTP {status} — {body}")
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

    def enqueue_job(self, session_id, job_name="KiCad IPC Job"):
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
        print(f"Failed to enqueue job: HTTP {status} — {body}")
        return None

    def upload_json_input(self, job_id, json_str):
        """Upload KiCad JSON board data to a job.

        Returns:
            ``True`` on success.
        """
        status, body = self._request("POST", f"/v1/jobs/{job_id}/input/json", data=json_str)
        if status == 200:
            return True
        print(f"Failed to upload JSON input: HTTP {status} — {body}")
        return False

    def start_job(self, job_id):
        """Start a queued job.

        Returns:
            ``True`` on success.
        """
        status, body = self._request("PUT", f"/v1/jobs/{job_id}/start")
        if status == 200:
            return True
        print(f"Failed to start job: HTTP {status} — {body}")
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
        status, body = self._request("GET", f"/v1/jobs/{job_id}")
        if status == 200:
            try:
                return __import__("json").loads(body)
            except __import__("json").JSONDecodeError:
                return None
        return None

    def download_json_output(self, job_id):
        """Download the routing result as KiCad JSON.

        Returns:
            JSON string, or ``None`` on failure.
        """
        status, body = self._request(
            "GET", f"/v1/jobs/{job_id}/output/json", timeout=60
        )
        if status in (200, 202):
            return body
        print(f"Failed to download JSON output: HTTP {status} — {body}")
        return None

    # ------------------------------------------------------------------
    # Blocking wait
    # ------------------------------------------------------------------

    def wait_for_job_completion(self, job_id, poll_interval=None, timeout=None):
        """Poll the job until it completes or times out.

        Args:
            poll_interval: Seconds between polls (default ``API_POLL_INTERVAL``).
            timeout: Maximum seconds to wait (default ``API_JOB_TIMEOUT``).

        Returns:
            ``(success, output_json)`` tuple.
        """
        import time as _time

        poll_interval = poll_interval or API_POLL_INTERVAL
        timeout = timeout or API_JOB_TIMEOUT
        start = _time.time()

        while True:
            elapsed = _time.time() - start
            if elapsed > timeout:
                print(f"Job {job_id} timed out after {timeout}s.")
                return False, None

            info = self.get_job_status(job_id)
            if info is None:
                print(f"Could not get job status for {job_id}.")
                return False, None

            state = info.get("state", "UNKNOWN")
            print(f"  Job {job_id} state: {state} ({elapsed:.0f}s elapsed)")

            if state in ("COMPLETED", "FINISHED", "DONE"):
                return True, self.download_json_output(job_id)
            elif state in ("TERMINATED", "CANCELLED", "TIMED_OUT", "INVALID", "ERROR"):
                print(f"Job {job_id} ended with state: {state}")
                return False, None

            _time.sleep(poll_interval)