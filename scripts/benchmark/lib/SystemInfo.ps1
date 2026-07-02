function Get-SystemInfo {
    $cpuName = "Unknown"
    $physicalCores = 0
    $logicalCores = [System.Environment]::ProcessorCount
    $totalRamGB = 0.0

    try {
        $cpu = Get-CimInstance Win32_Processor -ErrorAction SilentlyContinue
        if ($cpu) {
            $cpuName = $cpu.Name.Trim()
            $physicalCores = [int]$cpu.NumberOfCores
        } else {
            $cpu = Get-WmiObject Win32_Processor -ErrorAction SilentlyContinue
            if ($cpu) {
                $cpuName = $cpu.Name.Trim()
                $physicalCores = [int]$cpu.NumberOfCores
            }
        }
    } catch {}

    try {
        $cs = Get-CimInstance Win32_ComputerSystem -ErrorAction SilentlyContinue
        if ($cs) {
            $totalRamGB = [math]::Round($cs.TotalPhysicalMemory / 1GB, 1)
        } else {
            $cs = Get-WmiObject Win32_ComputerSystem -ErrorAction SilentlyContinue
            if ($cs) {
                $totalRamGB = [math]::Round($cs.TotalPhysicalMemory / 1GB, 1)
            }
        }
    } catch {}

    return [PSCustomObject]@{
        cpu_name           = $cpuName
        cpu_physical_cores = $physicalCores
        cpu_logical_cores  = $logicalCores
        total_ram_gb       = $totalRamGB
    }
}
