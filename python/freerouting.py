import requests
import base64
import json
import time
import uuid
from typing import Dict, Any, Optional, List, Union


class FreeroutingAPI:
    """Client library for the Freerouting API."""

    def __init__(self, api_key: str, base_url: str = "https://api.freerouting.app", version: str = "v1",
                 profile_id: Optional[str] = None, host_name: str = "FreeroutingPythonLib/1.0.0"):
        """
        Initialize the Freerouting API client.
        
        Args:
            api_key: Your Freerouting API key
            base_url: The base URL for the API
            version: API version to use (v1, dev, etc.)
            profile_id: Optional profile ID (GUID)
            host_name: Name and version of the software making API calls
        """
        self.api_key = api_key
        self.base_url = f"{base_url}/{version}"
        self.profile_id = profile_id or str(uuid.uuid4())
        self.host_name = host_name
        self.session_id = None
        
    def _get_headers(self) -> Dict[str, str]:
        """Get the headers for API requests."""
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Freerouting-Profile-ID": self.profile_id,
            "Freerouting-Environment-Host": self.host_name,
            "Content-Type": "application/json",
            "Accept": "application/json"
        }
        return headers
    
    def _make_request(self, method: str, endpoint: str, data: Optional[Dict] = None) -> Dict:
        """Make an API request and handle response."""
        url = f"{self.base_url}{endpoint}"
        headers = self._get_headers()
        
        if method.upper() == "GET":
            response = requests.get(url, headers=headers)
        elif method.upper() == "POST":
            response = requests.post(url, headers=headers, data=json.dumps(data) if data else None)
        elif method.upper() == "PUT":
            response = requests.put(url, headers=headers, data=json.dumps(data) if data else None)
        else:
            raise ValueError(f"Unsupported HTTP method: {method}")
            
        if response.status_code not in (200, 201, 202):
            raise Exception(f"API request failed: {response.status_code} - {response.text}")
            
        return response.json()
    
    # System Endpoints
    def get_system_status(self) -> Dict:
        """Get the current status of the Freerouting service."""
        return self._make_request("GET", "/system/status")
        
    def get_environment(self) -> Dict:
        """Get information about the system environment."""
        return self._make_request("GET", "/system/environment")
    
    # Session Endpoints
    def create_session(self) -> Dict:
        """Create a new session and store the session ID."""
        result = self._make_request("POST", "/sessions/create")
        self.session_id = result.get("id")
        return result
    
    def list_sessions(self) -> List[Dict]:
        """List all available sessions."""
        return self._make_request("GET", "/sessions/list")
    
    def get_session(self, session_id: Optional[str] = None) -> Dict:
        """Get details for a specific session."""
        session_id = session_id or self.session_id
        if not session_id:
            raise ValueError("No session ID provided or stored")
        return self._make_request("GET", f"/sessions/{session_id}")
    
    def get_session_logs(self, session_id: Optional[str] = None) -> List[Dict]:
        """Get logs for a specific session."""
        session_id = session_id or self.session_id
        if not session_id:
            raise ValueError("No session ID provided or stored")
        return self._make_request("GET", f"/sessions/{session_id}/logs")
    
    # Job Endpoints
    def enqueue_job(self, name: str, priority: str = "NORMAL", session_id: Optional[str] = None) -> Dict:
        """Enqueue a new routing job."""
        session_id = session_id or self.session_id
        if not session_id:
            raise ValueError("No session ID provided or stored")
            
        data = {
            "session_id": session_id,
            "name": name,
            "priority": priority
        }
        return self._make_request("POST", "/jobs/enqueue", data)
    
    def list_jobs(self, session_id: Optional[str] = None) -> List[Dict]:
        """List all jobs for a specific session."""
        session_id = session_id or self.session_id
        if not session_id:
            raise ValueError("No session ID provided or stored")
        return self._make_request("GET", f"/jobs/list/{session_id}")
    
    def get_job(self, job_id: str) -> Dict:
        """Get details for a specific job."""
        return self._make_request("GET", f"/jobs/{job_id}")
    
    def update_job_settings(self, job_id: str, settings: Dict[str, Any]) -> Dict:
        """Update settings for a specific job."""
        return self._make_request("POST", f"/jobs/{job_id}/settings", settings)
    
    def start_job(self, job_id: str) -> Dict:
        """Start processing a job."""
        return self._make_request("PUT", f"/jobs/{job_id}/start")
    
    def cancel_job(self, job_id: str) -> Dict:
        """Cancel a job."""
        return self._make_request("PUT", f"/jobs/{job_id}/cancel")
    
    def upload_input(self, job_id: str, filename: str, file_path: str) -> Dict:
        """Upload input file for a job."""
        with open(file_path, "rb") as f:
            file_data = f.read()
        
        data = {
            "filename": filename,
            "data": base64.b64encode(file_data).decode("utf-8")
        }
        return self._make_request("POST", f"/jobs/{job_id}/input", data)
    
    def download_output(self, job_id: str, output_path: Optional[str] = None) -> Dict:
        """Download output from a completed job."""
        result = self._make_request("GET", f"/jobs/{job_id}/output")
        
        if output_path and "data" in result:
            with open(output_path, "wb") as f:
                f.write(base64.b64decode(result["data"]))
                
        return result
    
    def get_job_logs(self, job_id: str) -> List[Dict]:
        """Get logs for a specific job."""
        return self._make_request("GET", f"/jobs/{job_id}/logs")
    
    # Workflow helpers
    def run_routing_job(self, name: str, dsn_file_path: str, settings: Optional[Dict] = None,
                        poll_interval: int = 5, timeout: int = 3600) -> Dict:
        """
        Run a complete routing job workflow from start to finish.
        
        Args:
            name: Name for the routing job
            dsn_file_path: Path to the DSN input file
            settings: Optional router settings
            poll_interval: Time in seconds between progress checks
            timeout: Maximum time in seconds to wait for job completion
            
        Returns:
            The output result dictionary
        """
        # Create session if needed
        if not self.session_id:
            self.create_session()
            
        # Create job
        job = self.enqueue_job(name)
        job_id = job["id"]
        
        # Upload input file
        filename = dsn_file_path.split("/")[-1]
        self.upload_input(job_id, filename, dsn_file_path)
        
        # Update settings if provided
        if settings:
            self.update_job_settings(job_id, settings)
        
        # Start the job
        self.start_job(job_id)
        
        # Poll for completion
        start_time = time.time()
        while time.time() - start_time < timeout:
            job_status = self.get_job(job_id)
            if job_status["state"] == "COMPLETED":
                return self.download_output(job_id)
            elif job_status["state"] in ("CANCELLED", "FAILED"):
                raise Exception(f"Job failed with state: {job_status['state']}")
            
            time.sleep(poll_interval)
            
        raise TimeoutError(f"Job did not complete within {timeout} seconds")