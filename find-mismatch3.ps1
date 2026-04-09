$ErrorActionPreference = "Stop"
For ($i = 14; $i -le 200; $i++) {
    Write-Host "Running iteration $i..."
    .\scripts\tests\compare-versions.ps1 -max_items $i -max_passes 1 -NoBuild -NoClean | Out-Null
    rm C:\Work\freerouting\divergence.log -ErrorAction Ignore
    .\gradlew test --tests "*CompareLogsTest*" --quiet --rerun-tasks | Out-Null
    $result = Get-Content C:\Work\freerouting\divergence.log
    if ($result -notmatch "MATCH!") {
        Write-Host "MISMATCH FOUND at $i!"
        $result | Out-File "C:\Work\freerouting\found_mismatch.txt"
        break
    }
}
