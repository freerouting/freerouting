function Get-BenchmarkCacheKey {
    param(
        [System.IO.FileInfo]$Binary,
        [System.IO.FileInfo]$Fixture,
        $Settings
    )
    # Calculate file hashes
    $binaryHash = (Get-FileHash $Binary.FullName -Algorithm SHA256).Hash
    $fixtureHash = (Get-FileHash $Fixture.FullName -Algorithm SHA256).Hash

    # Form settings string
    $settingsStr = "$($Settings.max_passes)|$($Settings.max_time)|$($Settings.max_threads)|$($Settings.heap_max)|$($Settings.log_level)|$($Settings.fanout_enabled)|$($Settings.router_enabled)|$($Settings.optimizer_enabled)"
    
    # Compute hash of settings string
    $hasher = [System.Security.Cryptography.SHA256]::Create()
    $settingsBytes = [System.Text.Encoding]::UTF8.GetBytes($settingsStr)
    $settingsHashBytes = $hasher.ComputeHash($settingsBytes)
    $settingsHash = [System.BitConverter]::ToString($settingsHashBytes).Replace("-","")
    $hasher.Dispose()

    # Composite key parts
    $binPart = $binaryHash.Substring(0, 16)
    $fixPart = $fixtureHash.Substring(0, 16)
    $setPart = $settingsHash.Substring(0, 8)
    return "$binPart-$fixPart-$setPart"
}
