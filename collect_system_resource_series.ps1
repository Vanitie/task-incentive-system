param(
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,

    [Parameter(Mandatory = $true)]
    [string]$StopFilePath,

    [int]$SampleIntervalSec = 5,
    [int]$BackendPid = 0,
    [string]$BackendCommandLinePattern = 'task-incentive-system',
    [string]$BackendProcessNamePattern = '^java(|w)?(\.exe)?$'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-BackendProcessInfo {
    param(
        [int]$ProcessId,
        [string]$CommandLinePattern,
        [string]$ProcessNamePattern
    )

    if ($ProcessId -gt 0) {
        try {
            $process = Get-Process -Id $ProcessId -ErrorAction Stop
            $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
            return [pscustomobject]@{
                ProcessId = $process.Id
                Process = $process
                CommandLine = if ($null -ne $processInfo) { $processInfo.CommandLine } else { $null }
            }
        } catch {
            return $null
        }
    }

    $candidates = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object {
            $_.Name -match $ProcessNamePattern -and
            ([string]::IsNullOrWhiteSpace($CommandLinePattern) -or ($_.CommandLine -match $CommandLinePattern))
        } |
        Sort-Object ProcessId -Descending

    $selected = $candidates | Select-Object -First 1
    if ($null -eq $selected) {
        return $null
    }

    try {
        $process = Get-Process -Id $selected.ProcessId -ErrorAction Stop
        return [pscustomobject]@{
            ProcessId = $process.Id
            Process = $process
            CommandLine = $selected.CommandLine
        }
    } catch {
        return $null
    }
}

function New-StatsObject {
    param(
        [object[]]$Samples,
        [string]$PropertyName
    )

    $values = @($Samples |
        ForEach-Object { $_.$PropertyName } |
        Where-Object { $null -ne $_ })

    if ($values.Count -eq 0) {
        return [pscustomobject]@{
            avg = $null
            max = $null
        }
    }

    $sum = 0.0
    $max = [double]$values[0]
    foreach ($value in $values) {
        $numeric = [double]$value
        $sum += $numeric
        if ($numeric -gt $max) {
            $max = $numeric
        }
    }

    return [pscustomobject]@{
        avg = [math]::Round($sum / $values.Count, 2)
        max = [math]::Round($max, 2)
    }
}

function New-ResourceOutput {
    param(
        [object[]]$Samples,
        [datetime]$StartedAt,
        [int]$SampleInterval,
        [string]$ProcessCommandLinePattern,
        [string]$ProcessNamePattern
    )

    $resourceSummary = [pscustomobject]@{
        system_cpu_percent = New-StatsObject -Samples $Samples -PropertyName 'system_cpu_percent'
        system_memory_percent = New-StatsObject -Samples $Samples -PropertyName 'system_memory_percent'
        disk_active_percent = New-StatsObject -Samples $Samples -PropertyName 'disk_active_percent'
        disk_read_bytes_per_sec = New-StatsObject -Samples $Samples -PropertyName 'disk_read_bytes_per_sec'
        disk_write_bytes_per_sec = New-StatsObject -Samples $Samples -PropertyName 'disk_write_bytes_per_sec'
        backend_cpu_percent = New-StatsObject -Samples $Samples -PropertyName 'backend_cpu_percent'
        backend_working_set_mb = New-StatsObject -Samples $Samples -PropertyName 'backend_working_set_mb'
        backend_private_memory_mb = New-StatsObject -Samples $Samples -PropertyName 'backend_private_memory_mb'
    }

    $xAxis = @($Samples | ForEach-Object { $_.elapsed_sec })

    return [pscustomobject]@{
        analysis_version = 'resource-v1'
        source = 'powershell_os_sampler'
        metadata = [pscustomobject]@{
            host = $env:COMPUTERNAME
            started_at = $StartedAt.ToString('yyyy-MM-dd HH:mm:ss')
            finished_at = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
            sample_interval_sec = $SampleInterval
            sample_count = $Samples.Count
            backend_commandline_pattern = $ProcessCommandLinePattern
            backend_process_name_pattern = $ProcessNamePattern
        }
        summary = $resourceSummary
        samples = $Samples
        charts = [pscustomobject]@{
            resource_system_line = [pscustomobject]@{
                chart_type = 'line'
                available = ($Samples.Count -gt 0)
                title = 'System resource usage time series'
                x_axis = $xAxis
                series = @(
                    [pscustomobject]@{
                        name = 'system_cpu_percent'
                        unit = '%'
                        data = @($Samples | ForEach-Object { $_.system_cpu_percent })
                    },
                    [pscustomobject]@{
                        name = 'system_memory_percent'
                        unit = '%'
                        data = @($Samples | ForEach-Object { $_.system_memory_percent })
                    },
                    [pscustomobject]@{
                        name = 'disk_active_percent'
                        unit = '%'
                        data = @($Samples | ForEach-Object { $_.disk_active_percent })
                    }
                )
            }
            resource_process_line = [pscustomobject]@{
                chart_type = 'line'
                available = ($Samples.Count -gt 0)
                title = 'Backend process resource time series'
                x_axis = $xAxis
                series = @(
                    [pscustomobject]@{
                        name = 'backend_cpu_percent'
                        unit = '%'
                        data = @($Samples | ForEach-Object { $_.backend_cpu_percent })
                    },
                    [pscustomobject]@{
                        name = 'backend_working_set_mb'
                        unit = 'MB'
                        data = @($Samples | ForEach-Object { $_.backend_working_set_mb })
                    },
                    [pscustomobject]@{
                        name = 'backend_private_memory_mb'
                        unit = 'MB'
                        data = @($Samples | ForEach-Object { $_.backend_private_memory_mb })
                    }
                )
            }
            disk_io_line = [pscustomobject]@{
                chart_type = 'line'
                available = ($Samples.Count -gt 0)
                title = 'Disk IO time series'
                x_axis = $xAxis
                series = @(
                    [pscustomobject]@{
                        name = 'disk_read_bytes_per_sec'
                        unit = 'B/s'
                        data = @($Samples | ForEach-Object { $_.disk_read_bytes_per_sec })
                    },
                    [pscustomobject]@{
                        name = 'disk_write_bytes_per_sec'
                        unit = 'B/s'
                        data = @($Samples | ForEach-Object { $_.disk_write_bytes_per_sec })
                    }
                )
            }
            resource_summary_bar = [pscustomobject]@{
                chart_type = 'bar'
                available = ($Samples.Count -gt 0)
                title = 'Resource average and peak values'
                x_axis = @('system_cpu', 'system_memory', 'disk_active', 'backend_cpu')
                series = @(
                    [pscustomobject]@{
                        name = 'avg'
                        data = @(
                            $resourceSummary.system_cpu_percent.avg,
                            $resourceSummary.system_memory_percent.avg,
                            $resourceSummary.disk_active_percent.avg,
                            $resourceSummary.backend_cpu_percent.avg
                        )
                    },
                    [pscustomobject]@{
                        name = 'max'
                        data = @(
                            $resourceSummary.system_cpu_percent.max,
                            $resourceSummary.system_memory_percent.max,
                            $resourceSummary.disk_active_percent.max,
                            $resourceSummary.backend_cpu_percent.max
                        )
                    }
                )
            }
        }
    }
}

function Write-OutputSnapshot {
    param(
        [string]$Path,
        [object[]]$Samples,
        [datetime]$StartedAt,
        [int]$SampleInterval,
        [string]$ProcessCommandLinePattern,
        [string]$ProcessNamePattern
    )

    $parentDir = Split-Path -Path $Path -Parent
    if (-not [string]::IsNullOrWhiteSpace($parentDir) -and -not (Test-Path $parentDir)) {
        New-Item -ItemType Directory -Path $parentDir -Force | Out-Null
    }

    $output = New-ResourceOutput -Samples $Samples -StartedAt $StartedAt -SampleInterval $SampleInterval -ProcessCommandLinePattern $ProcessCommandLinePattern -ProcessNamePattern $ProcessNamePattern
    $output | ConvertTo-Json -Depth 10 | Set-Content -Path $Path -Encoding UTF8
}

$startedAt = Get-Date
$samples = New-Object System.Collections.Generic.List[object]
$logicalProcessorCount = [math]::Max([Environment]::ProcessorCount, 1)
$previousProcessCpuSeconds = $null
$previousProcessTimestamp = $null
$previousProcessId = $null

while ($true) {
    $timestamp = Get-Date
    $elapsedSec = [math]::Round(($timestamp - $startedAt).TotalSeconds, 2)

    $processorPerf = Get-CimInstance Win32_PerfFormattedData_PerfOS_Processor -Filter "Name = '_Total'" -ErrorAction SilentlyContinue
    $diskPerf = Get-CimInstance Win32_PerfFormattedData_PerfDisk_PhysicalDisk -Filter "Name = '_Total'" -ErrorAction SilentlyContinue

    $os = Get-CimInstance Win32_OperatingSystem -ErrorAction SilentlyContinue
    $memoryUsagePercent = $null
    if ($null -ne $os -and [double]$os.TotalVisibleMemorySize -gt 0) {
        $totalMemoryKb = [double]$os.TotalVisibleMemorySize
        $freeMemoryKb = [double]$os.FreePhysicalMemory
        $usedMemoryKb = $totalMemoryKb - $freeMemoryKb
        $memoryUsagePercent = [math]::Round(($usedMemoryKb / $totalMemoryKb) * 100, 2)
    }

    $backendInfo = Resolve-BackendProcessInfo -ProcessId $BackendPid -CommandLinePattern $BackendCommandLinePattern -ProcessNamePattern $BackendProcessNamePattern
    $backendCpuPercent = $null
    $backendWorkingSetMb = $null
    $backendPrivateMemoryMb = $null
    $backendThreads = $null
    $backendHandles = $null
    $backendProcessId = $null

    if ($null -ne $backendInfo) {
        $backendProcess = $backendInfo.Process
        $backendProcessId = $backendInfo.ProcessId
        $backendWorkingSetMb = [math]::Round($backendProcess.WorkingSet64 / 1MB, 2)
        $backendPrivateMemoryMb = [math]::Round($backendProcess.PrivateMemorySize64 / 1MB, 2)
        $backendThreads = $backendProcess.Threads.Count
        $backendHandles = $backendProcess.Handles

        if ($null -ne $previousProcessTimestamp -and $null -ne $previousProcessCpuSeconds -and $previousProcessId -eq $backendProcessId) {
            $intervalSec = ($timestamp - $previousProcessTimestamp).TotalSeconds
            $cpuDelta = [double]$backendProcess.CPU - [double]$previousProcessCpuSeconds
            if ($intervalSec -gt 0 -and $cpuDelta -ge 0) {
                $backendCpuPercent = [math]::Round(($cpuDelta / ($intervalSec * $logicalProcessorCount)) * 100, 2)
            }
        }

        $previousProcessCpuSeconds = [double]$backendProcess.CPU
        $previousProcessTimestamp = $timestamp
        $previousProcessId = $backendProcessId
    } else {
        $previousProcessCpuSeconds = $null
        $previousProcessTimestamp = $null
        $previousProcessId = $null
    }

    $sample = [pscustomobject][ordered]@{
        timestamp = $timestamp.ToString('yyyy-MM-dd HH:mm:ss')
        elapsed_sec = $elapsedSec
        system_cpu_percent = if ($null -ne $processorPerf) { [math]::Round([double]$processorPerf.PercentProcessorTime, 2) } else { $null }
        system_memory_percent = $memoryUsagePercent
        disk_active_percent = if ($null -ne $diskPerf) { [math]::Round([double]$diskPerf.PercentDiskTime, 2) } else { $null }
        disk_read_bytes_per_sec = if ($null -ne $diskPerf) { [math]::Round([double]$diskPerf.DiskReadBytesPersec, 2) } else { $null }
        disk_write_bytes_per_sec = if ($null -ne $diskPerf) { [math]::Round([double]$diskPerf.DiskWriteBytesPersec, 2) } else { $null }
        backend_pid = $backendProcessId
        backend_cpu_percent = $backendCpuPercent
        backend_working_set_mb = $backendWorkingSetMb
        backend_private_memory_mb = $backendPrivateMemoryMb
        backend_threads = $backendThreads
        backend_handles = $backendHandles
    }

    [void]$samples.Add($sample)

    Write-OutputSnapshot -Path $OutputPath -Samples $samples.ToArray() -StartedAt $startedAt -SampleInterval $SampleIntervalSec -ProcessCommandLinePattern $BackendCommandLinePattern -ProcessNamePattern $BackendProcessNamePattern

    if (Test-Path $StopFilePath) {
        break
    }

    Start-Sleep -Seconds $SampleIntervalSec
}