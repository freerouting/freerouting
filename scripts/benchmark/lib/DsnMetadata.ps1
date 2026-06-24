function Get-DsnMetadata {
    param([string]$DsnPath)

    $content = [System.IO.File]::ReadAllText($DsnPath)

    # 1. Counts
    $layers = [regex]::Matches($content, '\(layer\b').Count
    $nets = [regex]::Matches($content, '\(net\b').Count
    $components = [regex]::Matches($content, '\(component\b').Count

    # 2. Host CAD & Host Version
    $hostCad = "Unknown"
    $hostVersion = "Unknown"
    if ($content -match '\(host_cad\s+"?([^"\)]+)"?\)') {
        $hostCad = $matches[1].Trim()
    }
    if ($content -match '\(host_version\s+"?([^"\)]+)"?\)') {
        $hostVersion = $matches[1].Trim()
    }

    # 3. Resolution and units
    $unit = "inch"
    $res = 100.0
    if ($content -match '\(resolution\s+(\w+)\s+(\d+)\)') {
        $unit = $matches[1].ToLower()
        $res = [double]$matches[2]
    }

    # 4. Dimensions parsing
    $widthMM = 0.0
    $heightMM = 0.0
    $areaCm2 = 0.0

    # Match rect boundary
    if ($content -match '\(boundary\s*\(\s*rect\s+\S+\s+([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)\s*\)') {
        $x1 = [double]$matches[1]
        $y1 = [double]$matches[2]
        $x2 = [double]$matches[3]
        $y2 = [double]$matches[4]

        $wVal = [math]::Abs($x2 - $x1)
        $hVal = [math]::Abs($y2 - $y1)

        $widthMM = Convert-ToMM $wVal $unit $res
        $heightMM = Convert-ToMM $hVal $unit $res
    } elseif ($content -match '\(boundary\s*\(\s*path\s+\S+\s+\d+\s+((?:[-\d.]+\s*)+)\)') {
        # Parse polyline boundary coordinates to find bounding box
        $coords = $matches[1] -split '\s+' | Where-Object { $_ -ne "" } | ForEach-Object { [double]$_ }
        if ($coords.Count -ge 4) {
            $xCoords = @()
            $yCoords = @()
            for ($i = 0; $i -lt $coords.Count; $i += 2) {
                if ($i+1 -lt $coords.Count) {
                    $xCoords += $coords[$i]
                    $yCoords += $coords[$i+1]
                }
            }
            if ($xCoords.Count -gt 0) {
                $wVal = ($xCoords | Measure-Object -Maximum).Maximum - ($xCoords | Measure-Object -Minimum).Minimum
                $hVal = ($yCoords | Measure-Object -Maximum).Maximum - ($yCoords | Measure-Object -Minimum).Minimum
                $widthMM = Convert-ToMM $wVal $unit $res
                $heightMM = Convert-ToMM $hVal $unit $res
            }
        }
    }

    # Format values
    $widthMM = [math]::Round($widthMM, 1)
    $heightMM = [math]::Round($heightMM, 1)
    $areaCm2 = [math]::Round(($widthMM * $heightMM) / 100.0, 1)

    return [PSCustomObject]@{
        layer_count     = $layers
        net_count       = $nets
        component_count = $components
        host_cad        = $hostCad
        host_version    = $hostVersion
        board_width_mm  = $widthMM
        board_height_mm = $heightMM
        board_area_cm2  = $areaCm2
    }
}

function Convert-ToMM {
    param([double]$val, [string]$unit, [double]$res)
    if ($res -le 0) { $res = 1.0 }
    
    switch ($unit) {
        "inch" { return ($val / $res) * 25.4 }
        "mil"  { return (($val / $res) / 1000.0) * 25.4 }
        "um"   { return $val / 1000.0 }
        "microns" { return $val / 1000.0 }
        "mm"   { return $val }
        "cm"   { return $val * 10.0 }
        default { return $val / $res }
    }
}
