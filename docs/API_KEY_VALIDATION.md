# API Key Validation System Documentation

## Overview

The Freerouting API implements a modular API key validation system to protect sensitive endpoints
from unauthorized access. The system uses a provider-based architecture that allows for multiple API
key storage backends while maintaining a consistent validation interface.

### Key Features

- **Modular Provider Architecture**: Extensible design supports multiple API key storage mechanisms
- **Google Sheets Integration**: Initial provider reads API keys from a publicly accessible Google
  Sheet
- **Intelligent Caching**: 5-minute cache refresh interval minimizes API calls
- **GUID Validation**: Ensures API keys follow the RFC 4122 GUID format
- **Selective Endpoint Protection**: Public endpoints remain accessible without authentication
- **Graceful Degradation**: Maintains cached data during temporary outages

---

## Architecture

### Components

1. **ApiKeyProvider Interface** (`app.freerouting.api.security.ApiKeyProvider`)
    - Defines the contract for API key validation
    - Supports multiple implementations (Google Sheets, database, file-based, etc.)
    - Includes health check and refresh capabilities

2. **GoogleSheetsApiKeyProvider** (`app.freerouting.api.security.GoogleSheetsApiKeyProvider`)
    - Reads API keys from a Google Sheet
    - Implements caching with automatic refresh
    - Validates GUID format and access permissions

3. **ApiKeyValidationFilter** (`app.freerouting.api.security.ApiKeyValidationFilter`)
    - JAX-RS request filter that intercepts all API requests
    - Validates API keys from the `Authorization: Bearer` header
    - Excludes specific public endpoints from validation

---

## Provider Interface

### Implementing a Custom Provider

To create a new API key provider, implement the `ApiKeyProvider` interface:

```java
package app.freerouting.api.security;

public class MyCustomApiKeyProvider implements ApiKeyProvider {

  @Override
  public boolean validateApiKey(String apiKey) {
    // Implement your validation logic
    // 1. Validate key format
    // 2. Check against your data source
    // 3. Verify access permissions
    return false;
  }

  @Override
  public void refresh() {
    // Implement cache refresh logic
    // Should handle errors gracefully
  }

  @Override
  public String getProviderName() {
    return "My Custom Provider";
  }

  @Override
  public boolean isHealthy() {
    // Return true if provider can validate keys
    return true;
  }
}
```

### Integration Points

To use your custom provider, modify `ApiKeyValidationFilter.initializeProvider()`:

```java
private static synchronized void initializeProvider() {
  if (isInitialized) {
    return;
  }

  // Add your provider initialization logic
  String customConfig = Freerouting.globalSettings.apiServerSettings.myCustomConfig;

  if (customConfig != null && !customConfig.trim().isEmpty()) {
    try {
      apiKeyProvider = new MyCustomApiKeyProvider(customConfig);
      FRLogger.info("API key validation enabled with Custom provider");
    } catch (Exception e) {
      FRLogger.error("Failed to initialize Custom API key provider", null, e);
      apiKeyProvider = null;
    }
  }

  isInitialized = true;
}
```

---

## Google Sheets Provider

### Configuration

The Google Sheets provider requires:

1. A publicly accessible Google Sheet with API keys
2. A Google API key for authentication

#### Google Sheet Structure

The sheet must have the following columns (order doesn't matter):

| API Key                              | Access granted? | (other columns...) |
|--------------------------------------|-----------------|--------------------|
| 550e8400-e29b-41d4-a716-446655440000 | Yes             | ...                |
| 660e8400-e29b-41d4-a716-446655440001 | No              | ...                |

**Required Columns**:

- **API Key**: Must contain valid GUID strings (RFC 4122 format)
- **Access granted?**: Must contain "Yes" (case-insensitive) to grant access

#### Google API Key Setup

To access the Google Sheets API, you need a Google API key:

1. **Create API Key**:
    - Go to [Google Cloud Console - Credentials](https://console.cloud.google.com/apis/credentials)
    - Click "Create Credentials" → "API Key"
    - Copy the generated API key

2. **Restrict API Key** (Recommended):
    - Click "Restrict Key" on your new API key
    - Under "API restrictions", select "Restrict key"
    - Choose "Google Sheets API" from the dropdown
    - Click "Save"

3. **Enable Google Sheets API**:
    - Go to [Google Cloud Console - APIs](https://console.cloud.google.com/apis/library)
    - Search for "Google Sheets API"
    - Click "Enable" if not already enabled

### Environment Variables

Set both required environment variables:

**Linux/Mac:**

```bash
export FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_SHEETS="https://docs.google.com/spreadsheets/d/YOUR_SHEET_ID/edit"
export FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_API_KEY="YOUR_GOOGLE_API_KEY"
```

**Windows PowerShell:**

```powershell
$env:FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_SHEETS="https://docs.google.com/spreadsheets/d/YOUR_SHEET_ID/edit"
$env:FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_API_KEY="YOUR_GOOGLE_API_KEY"
```

### Deployment Steps

1. **Create Google Sheet**:
    - Create a new Google Sheet or use an existing one
    - Add columns: "API Key" and "Access granted?"
    - Add your API keys (must be valid GUIDs)
    - Set "Access granted?" to "Yes" for authorized keys

2. **Make Sheet Public**:
    - Click "Share" button
    - Change to "Anyone with the link" can view
    - Copy the sheet URL

3. **Configure Freerouting**:
    - Set the `FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_SHEETS` environment variable
    - Set the `FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_API_KEY` environment variable
    - Restart the Freerouting API server

4. **Verify**:
    - Check logs for: "API key validation enabled with Google Sheets provider"
    - Test with a valid API key:
      `curl -H "Authorization: Bearer YOUR_KEY" http://localhost:37864/v1/sessions/list`

### Caching Behavior

- **Initial Load**: Synchronous during provider initialization
- **Refresh Interval**: Every 5 minutes automatically
- **Failure Handling**: Maintains cached data if refresh fails
- **Thread Safety**: Uses `ConcurrentHashMap` for thread-safe access

---

## Endpoint Exclusions

The following endpoints are **publicly accessible** without API key validation:

| Endpoint Pattern  | Description                         |
|-------------------|-------------------------------------|
| `/v1/system/*`    | System monitoring and health checks |
| `/v1/analytics/*` | Analytics tracking endpoints        |
| `/dev/*`          | Development and testing endpoints   |
| `/openapi/*`      | OpenAPI specification (JSON/YAML)   |
| `/swagger-ui`     | Swagger UI documentation interface  |

All other endpoints require a valid API key in the `Authorization: Bearer <API_KEY>` header.

---

## API Key Format

API keys must follow the **RFC 4122 GUID format**:

```
xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
```

Where `x` is a hexadecimal digit (0-9, a-f, A-F).

**Valid Examples**:

- `550e8400-e29b-41d4-a716-446655440000`
- `A1B2C3D4-E5F6-7890-ABCD-EF1234567890`

**Invalid Examples**:

- `not-a-guid`
- `12345678-1234-1234-1234-12345678901` (too short)
- `550e8400e29b41d4a716446655440000` (missing hyphens)

---

## Error Responses

### 401 Unauthorized - Missing API Key

```bash
curl http://localhost:37864/v1/sessions/list
```

Response:

```json
{
  "error": "Missing API key. Please provide a valid API key in the Authorization header using Bearer scheme (Authorization: Bearer <API_KEY>)."
}
```

### 401 Unauthorized - Invalid API Key

```bash
curl -H "Authorization: Bearer invalid-key" http://localhost:37864/v1/sessions/list
```

Response:

```json
{
  "error": "Invalid or unauthorized API key."
}
```

### 401 Unauthorized - Provider Not Configured

Response:

```json
{
  "error": "API key validation is not properly configured."
}
```

---

## Troubleshooting

### Provider Not Initializing

**Symptom**: All protected endpoints return 401 with "API key validation is not properly configured"

**Solutions**:

1. Check both environment variables are set:
    - `echo $FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_SHEETS`
    - `echo $FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_API_KEY`
2. Verify Google Sheet URL is correct and accessible
3. Verify Google API key is valid and has Sheets API enabled
4. Check logs for initialization errors
5. Ensure Google Sheet is publicly readable

### Valid Keys Being Rejected

**Symptom**: Known valid API keys return 401 "Invalid or unauthorized API key"

**Solutions**:

1. Verify API key is a valid GUID format
2. Check "Access granted?" column contains exactly "Yes" (case-insensitive)
3. Wait up to 5 minutes for cache refresh if recently added
4. Check logs for refresh errors

### Cache Not Refreshing

**Symptom**: New API keys not working after being added to Google Sheet

**Solutions**:

1. Wait up to 5 minutes for automatic refresh
2. Check Google Sheets API connectivity
3. Verify sheet permissions haven't changed
4. Restart API server to force refresh

---

## Security Considerations

### Best Practices

1. **GUID Generation**: Use cryptographically secure random GUID generators
2. **Key Rotation**: Regularly rotate API keys and remove old ones
3. **Access Logging**: Monitor API key usage via application logs
4. **Sheet Permissions**: Keep Google Sheet read-only for public access
5. **HTTPS Only**: Always use HTTPS in production to protect API keys in transit

### Limitations

- **Public Sheet**: Google Sheet must be publicly readable (anyone with link)
- **No Rate Limiting**: Provider does not implement rate limiting (add separately if needed)
- **Cache Delay**: Up to 5 minutes delay for new keys to become active
- **No Key Expiration**: Provider does not support automatic key expiration

---

## Configuration Examples

### Docker Deployment

```dockerfile
FROM freerouting:latest
ENV FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_SHEETS="https://docs.google.com/spreadsheets/d/YOUR_SHEET_ID/edit"
ENV FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_API_KEY="YOUR_GOOGLE_API_KEY"
```

### Kubernetes Deployment

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: freerouting-config
data:
  FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_SHEETS: "https://docs.google.com/spreadsheets/d/YOUR_SHEET_ID/edit"
  FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_API_KEY: "YOUR_GOOGLE_API_KEY"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: freerouting-api
spec:
  template:
    spec:
      containers:
        - name: freerouting
          envFrom:
            - configMapRef:
                name: freerouting-config
```

### Systemd Service

```ini
[Unit]
Description=Freerouting API Server
After=network.target

[Service]
Type=simple
Environment="FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_SHEETS=https://docs.google.com/spreadsheets/d/YOUR_SHEET_ID/edit"
Environment="FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_API_KEY=YOUR_GOOGLE_API_KEY"
ExecStart=/usr/local/bin/freerouting
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

---

## Code Examples

### Making Authenticated Requests

#### cURL

```bash
curl -H "Authorization: Bearer 550e8400-e29b-41d4-a716-446655440000" \
     http://localhost:37864/v1/sessions/list
```

#### Python

```python
import requests

headers = {
    'Authorization': 'Bearer 550e8400-e29b-41d4-a716-446655440000'
}

response = requests.get(
    'http://localhost:37864/v1/sessions/list',
    headers=headers
)

print(response.json())
```

#### JavaScript (Node.js)

```javascript
const axios = require('axios');

const config = {
  headers: {
    'Authorization': 'Bearer 550e8400-e29b-41d4-a716-446655440000'
  }
};

axios.get('http://localhost:37864/v1/sessions/list', config)
  .then(response => console.log(response.data))
  .catch(error => console.error(error));
```

#### Java

```java
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("http://localhost:37864/v1/sessions/list"))
    .header("Authorization", "Bearer 550e8400-e29b-41d4-a716-446655440000")
    .GET()
    .build();

HttpResponse<String> response = client.send(request,
    HttpResponse.BodyHandlers.ofString());
System.out.

println(response.body());
```

---

## Monitoring and Logging

### Log Messages

The API key validation system logs the following events:

**INFO Level**:

- `"API key validation enabled with Google Sheets provider"` - Provider initialized successfully
- `"Successfully refreshed X valid API keys from Google Sheets"` - Cache refresh completed

**WARN Level**:

- `"Google Sheets URL not configured"` - Environment variable not set
- `"API key validation failed: missing API key for path X"` - Request without API key
- `"API key validation failed: invalid or unauthorized API key for path X"` - Invalid key used

**ERROR Level**:

- `"Failed to initialize Google Sheets API key provider"` - Provider initialization failed
- `"Failed to refresh API keys from Google Sheets"` - Cache refresh failed
- `"Required columns not found in Google Sheet"` - Sheet structure invalid

**DEBUG Level**:

- `"API key validation skipped for excluded path: X"` - Public endpoint accessed
- `"API key validation successful for path: X"` - Valid key used
- `"Skipping invalid GUID in row X"` - Invalid GUID in sheet

---

## Future Enhancements

Potential improvements for the API key validation system:

1. **Database Provider**: Store API keys in PostgreSQL/MySQL for better performance
2. **Redis Caching**: Use Redis for distributed caching across multiple instances
3. **Key Expiration**: Support automatic key expiration dates
4. **Rate Limiting**: Implement per-key rate limiting
5. **Scoped Permissions**: Allow different keys to access different endpoint sets
6. **Audit Logging**: Detailed audit trail of all API key usage
7. **Key Management API**: Endpoints to manage API keys programmatically
8. **Multiple Providers**: Support multiple providers simultaneously with fallback