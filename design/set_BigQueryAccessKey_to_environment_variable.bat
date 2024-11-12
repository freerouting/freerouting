# Read the JSON file content
$jsonContent = Get-Content -Path "C:\Users\andra\Downloads\freerouting-analytics-7fcef69f7590.json" -Raw

# Set the JSON content as a system environment variable
[System.Environment]::SetEnvironmentVariable(
    "FREEROUTING__USAGE_AND_DIAGNOSTIC_DATA__BIGQUERY_SERVICE_ACCOUNT_KEY", 
    $jsonContent, 
    [System.EnvironmentVariableTarget]::Machine
)
