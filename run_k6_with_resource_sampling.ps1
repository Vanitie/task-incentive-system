param(
    [string]$K6ScriptPath = '.\engine_process_event_k6.js',
    [string]$K6Executable = 'k6',
    [string]$ResourceSamplerScriptPath = '.\collect_system_resource_series.ps1',
    [string]$RemoteSamplerHost = '',
    [System.Management.Automation.PSCredential]$RemoteCredential = $null,
    [string]$RemoteSamplerWorkingDirectory = '',
    [int]$RemoteSamplerWaitTimeoutSec = 60,
    [string]$TestRound = '',
    [int]$SampleIntervalSec = 5,
    [int]$BackendPid = 0,
    [string]$BackendCommandLinePattern = 'task-incentive-system',
    [string]$BackendProcessNamePattern = '^java(|w)?(\.exe)?$',
    [string[]]$K6Arguments = @('run')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-NormalizedRound {
    param(
        [string]$RoundValue
    )

    if ([string]::IsNullOrWhiteSpace($RoundValue)) {
        return ''
    }

    return ($RoundValue.Trim().ToLowerInvariant() -replace '[^a-z0-9_-]+', '_')
}

function Get-RunLabel {
    param(
        [string]$RoundValue = ''
    )

    $parts = @(
        $env:DATA_SCALE,
        $env:TEST_MODE,
        $env:TARGET_MODE
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

    $normalizedRound = Get-NormalizedRound -RoundValue $RoundValue
    if (-not [string]::IsNullOrWhiteSpace($normalizedRound)) {
        if ($normalizedRound.StartsWith('round-') -or $normalizedRound.StartsWith('round_')) {
            $parts += $normalizedRound
        }
        else {
            $parts += "round-$normalizedRound"
        }
    }

    if ($parts.Count -eq 0) {
        return 'default'
    }

    return ($parts -join '-').ToLowerInvariant()
}

function Ensure-Argument {
    param(
        [string[]]$Arguments,
        [string]$RequiredValue
    )

    if ($Arguments -contains $RequiredValue) {
        return $Arguments
    }

    return @($Arguments + $RequiredValue)
}

function Merge-ObservedSummary {
    param(
        [string]$K6ExtendedSummaryPath,
        [string]$ResourceSummaryPath,
        [string]$ObservedSummaryPath,
        [int]$SampleInterval,
        [int]$BackendProcessId,
        [string]$BackendPattern,
        [string]$SamplerMode = 'local',
        [string]$RemoteResourceSummaryPath = ''
    )

    if (-not (Test-Path $K6ExtendedSummaryPath)) {
        throw "Extended k6 summary not found: $K6ExtendedSummaryPath"
    }
    if (-not (Test-Path $ResourceSummaryPath)) {
        throw "Resource summary not found: $ResourceSummaryPath"
    }

    $k6Summary = Get-Content -Path $K6ExtendedSummaryPath -Raw | ConvertFrom-Json
    $resourceSummary = Get-Content -Path $ResourceSummaryPath -Raw | ConvertFrom-Json

    $resourceSampling = [pscustomobject]@{
        source = 'powershell_os_sampler'
        sampler_mode = $SamplerMode
        sample_interval_sec = $SampleInterval
        resource_summary_file = (Resolve-Path $ResourceSummaryPath).Path
        sampler_host = if ($resourceSummary.metadata.host) { $resourceSummary.metadata.host } else { $env:COMPUTERNAME }
        remote_resource_summary_file = if ([string]::IsNullOrWhiteSpace($RemoteResourceSummaryPath)) { $null } else { $RemoteResourceSummaryPath }
        backend_pid = if ($BackendProcessId -gt 0) { $BackendProcessId } else { $null }
        backend_commandline_pattern = $BackendPattern
    }

    Add-Member -InputObject $k6Summary -MemberType NoteProperty -Name 'resource_sampling' -Value $resourceSampling -Force
    Add-Member -InputObject $k6Summary -MemberType NoteProperty -Name 'resource_summary' -Value $resourceSummary.summary -Force
    Add-Member -InputObject $k6Summary -MemberType NoteProperty -Name 'resource_time_series' -Value $resourceSummary.samples -Force

    if ($null -eq $k6Summary.charts) {
        Add-Member -InputObject $k6Summary -MemberType NoteProperty -Name 'charts' -Value ([pscustomobject]@{}) -Force
    }

    Add-Member -InputObject $k6Summary.charts -MemberType NoteProperty -Name 'resource_system_line' -Value $resourceSummary.charts.resource_system_line -Force
    Add-Member -InputObject $k6Summary.charts -MemberType NoteProperty -Name 'resource_process_line' -Value $resourceSummary.charts.resource_process_line -Force
    Add-Member -InputObject $k6Summary.charts -MemberType NoteProperty -Name 'disk_io_line' -Value $resourceSummary.charts.disk_io_line -Force
    Add-Member -InputObject $k6Summary.charts -MemberType NoteProperty -Name 'resource_summary_bar' -Value $resourceSummary.charts.resource_summary_bar -Force

    $k6Summary | ConvertTo-Json -Depth 12 | Set-Content -Path $ObservedSummaryPath -Encoding UTF8
}

function New-RemoteSessionParameters {
    param(
        [string]$ComputerName,
        [System.Management.Automation.PSCredential]$Credential
    )

    $sessionParams = @{ ComputerName = $ComputerName }
    if ($null -ne $Credential) {
        $sessionParams.Credential = $Credential
    }
    return $sessionParams
}

function Start-LocalSampler {
    param(
        [string]$SamplerScript,
        [string]$OutputFile,
        [string]$StopFile,
        [int]$IntervalSec,
        [int]$BackendProcessId,
        [string]$Pattern,
        [string]$ProcessNamePattern
    )

    if (Test-Path $StopFile) {
        Remove-Item -Path $StopFile -Force
    }

    $job = Start-Job -ScriptBlock {
        param(
            [string]$InnerSamplerScript,
            [string]$InnerOutputFile,
            [string]$InnerStopFile,
            [int]$InnerIntervalSec,
            [int]$InnerBackendProcessId,
            [string]$InnerPattern,
            [string]$InnerProcessNamePattern
        )

        & $InnerSamplerScript -OutputPath $InnerOutputFile -StopFilePath $InnerStopFile -SampleIntervalSec $InnerIntervalSec -BackendPid $InnerBackendProcessId -BackendCommandLinePattern $InnerPattern -BackendProcessNamePattern $InnerProcessNamePattern
    } -ArgumentList $SamplerScript, $OutputFile, $StopFile, $IntervalSec, $BackendProcessId, $Pattern, $ProcessNamePattern

    return [pscustomobject]@{
        mode = 'local'
        job = $job
        stop_file = $StopFile
        output_file = $OutputFile
        remote_output_file = $null
        session = $null
        remote_directory = $null
    }
}

function Start-RemoteSampler {
    param(
        [string]$ComputerName,
        [System.Management.Automation.PSCredential]$Credential,
        [string]$LocalSamplerScript,
        [string]$RequestedRemoteDirectory,
        [string]$RunLabel,
        [string]$Timestamp,
        [int]$IntervalSec,
        [int]$BackendProcessId,
        [string]$Pattern,
        [string]$ProcessNamePattern,
        [string]$LocalOutputFile
    )

    $sessionParams = New-RemoteSessionParameters -ComputerName $ComputerName -Credential $Credential
    $session = New-PSSession @sessionParams

    try {
        $remoteDirectory = Invoke-Command -Session $session -ScriptBlock {
            param(
                [string]$PreferredDirectory,
                [string]$InnerRunLabel,
                [string]$InnerTimestamp
            )

            $targetDirectory = $PreferredDirectory
            if ([string]::IsNullOrWhiteSpace($targetDirectory)) {
                $targetDirectory = Join-Path $env:TEMP "task-incentive-resource-$InnerRunLabel-$InnerTimestamp"
            }
            New-Item -ItemType Directory -Path $targetDirectory -Force | Out-Null
            return (Resolve-Path $targetDirectory).Path
        } -ArgumentList $RequestedRemoteDirectory, $RunLabel, $Timestamp

        $remoteScriptPath = Join-Path $remoteDirectory 'collect_system_resource_series.ps1'
        $remoteOutputFile = Join-Path $remoteDirectory "k6-resource-series-$RunLabel-$Timestamp.json"
        $remoteStopFile = Join-Path $remoteDirectory ".resource-sampler-$RunLabel-$Timestamp.stop"

        Copy-Item -Path $LocalSamplerScript -Destination $remoteScriptPath -ToSession $session -Force

        $remoteProcessId = Invoke-Command -Session $session -ScriptBlock {
            param(
                [string]$InnerScriptPath,
                [string]$InnerOutputFile,
                [string]$InnerStopFile,
                [int]$InnerIntervalSec,
                [int]$InnerBackendProcessId,
                [string]$InnerPattern,
                [string]$InnerProcessNamePattern
            )

            $argumentList = @(
                '-ExecutionPolicy', 'Bypass',
                '-File', $InnerScriptPath,
                '-OutputPath', $InnerOutputFile,
                '-StopFilePath', $InnerStopFile,
                '-SampleIntervalSec', $InnerIntervalSec,
                '-BackendCommandLinePattern', $InnerPattern,
                '-BackendProcessNamePattern', $InnerProcessNamePattern
            )
            if ($InnerBackendProcessId -gt 0) {
                $argumentList += @('-BackendPid', $InnerBackendProcessId)
            }

            $process = Start-Process -FilePath 'powershell.exe' -ArgumentList $argumentList -WindowStyle Hidden -PassThru
            return $process.Id
        } -ArgumentList $remoteScriptPath, $remoteOutputFile, $remoteStopFile, $IntervalSec, $BackendProcessId, $Pattern, $ProcessNamePattern

        return [pscustomobject]@{
            mode = 'remote'
            host = $ComputerName
            session = $session
            process_id = $remoteProcessId
            stop_file = $remoteStopFile
            output_file = $LocalOutputFile
            remote_output_file = $remoteOutputFile
            remote_directory = $remoteDirectory
        }
    } catch {
        if ($null -ne $session) {
            Remove-PSSession -Session $session -ErrorAction SilentlyContinue
        }
        throw
    }
}

function Stop-Sampler {
    param(
        [pscustomobject]$Context,
        [int]$WaitTimeoutSec,
        [string]$LocalOutputFile
    )

    if ($null -eq $Context) {
        return
    }

    if ($Context.mode -eq 'remote') {
        try {
            Invoke-Command -Session $Context.session -ScriptBlock {
                param(
                    [string]$InnerStopFile
                )

                New-Item -ItemType File -Path $InnerStopFile -Force | Out-Null
            } -ArgumentList $Context.stop_file | Out-Null

            $deadline = (Get-Date).AddSeconds([math]::Max($WaitTimeoutSec, 20))
            do {
                $status = Invoke-Command -Session $Context.session -ScriptBlock {
                    param(
                        [string]$InnerOutputFile,
                        [int]$InnerProcessId
                    )

                    $processRunning = $false
                    try {
                        $null = Get-Process -Id $InnerProcessId -ErrorAction Stop
                        $processRunning = $true
                    } catch {
                        $processRunning = $false
                    }

                    return [pscustomobject]@{
                        file_exists = Test-Path $InnerOutputFile
                        process_running = $processRunning
                    }
                } -ArgumentList $Context.remote_output_file, $Context.process_id

                if ($status.file_exists -and -not $status.process_running) {
                    break
                }

                Start-Sleep -Seconds 2
            } while ((Get-Date) -lt $deadline)

            $remoteFileExists = Invoke-Command -Session $Context.session -ScriptBlock {
                param(
                    [string]$InnerOutputFile
                )

                return (Test-Path $InnerOutputFile)
            } -ArgumentList $Context.remote_output_file

            if (-not $remoteFileExists) {
                throw "Remote resource summary not found: $($Context.remote_output_file)"
            }

            Copy-Item -FromSession $Context.session -Path $Context.remote_output_file -Destination $LocalOutputFile -Force
        } finally {
            if ($null -ne $Context.session) {
                Invoke-Command -Session $Context.session -ScriptBlock {
                    param(
                        [string]$InnerDirectory
                    )

                    if (-not [string]::IsNullOrWhiteSpace($InnerDirectory) -and (Test-Path $InnerDirectory)) {
                        Remove-Item -Path $InnerDirectory -Recurse -Force -ErrorAction SilentlyContinue
                    }
                } -ArgumentList $Context.remote_directory -ErrorAction SilentlyContinue | Out-Null
                Remove-PSSession -Session $Context.session -ErrorAction SilentlyContinue
            }
        }
        return
    }

    New-Item -ItemType File -Path $Context.stop_file -Force | Out-Null
    Wait-Job -Job $Context.job -Timeout ([math]::Max($WaitTimeoutSec, 20)) | Out-Null
    $jobOutput = Receive-Job -Job $Context.job -Keep -ErrorAction SilentlyContinue
    if ($null -ne $jobOutput -and $jobOutput.Count -gt 0) {
        $jobOutput | ForEach-Object { Write-Host $_ }
    }
    if ($Context.job.State -eq 'Failed') {
        throw 'Resource sampler job failed'
    }
    Remove-Job -Job $Context.job -Force | Out-Null
    Remove-Item -Path $Context.stop_file -Force -ErrorAction SilentlyContinue
}

$workspaceRoot = Split-Path -Path $MyInvocation.MyCommand.Path -Parent
$resolvedTestRound = if (-not [string]::IsNullOrWhiteSpace($TestRound)) { $TestRound } else { $env:TEST_ROUND }
$originalTestRound = $env:TEST_ROUND
if (-not [string]::IsNullOrWhiteSpace($resolvedTestRound)) {
    $env:TEST_ROUND = $resolvedTestRound
}
$runLabel = Get-RunLabel -RoundValue $resolvedTestRound
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'

$k6ScriptFullPath = Resolve-Path -Path $K6ScriptPath
$resourceSamplerFullPath = Resolve-Path -Path $ResourceSamplerScriptPath
$resourceSummaryPath = Join-Path $workspaceRoot "k6-resource-series-$runLabel-$timestamp.json"
$observedSummaryPath = Join-Path $workspaceRoot "k6-summary-observed-$runLabel-$timestamp.json"
$stopFilePath = Join-Path $workspaceRoot ".resource-sampler-$runLabel-$timestamp.stop"

$resolvedK6Arguments = Ensure-Argument -Arguments $K6Arguments -RequiredValue $k6ScriptFullPath.Path

$samplerContext = if ([string]::IsNullOrWhiteSpace($RemoteSamplerHost)) {
    Start-LocalSampler -SamplerScript $resourceSamplerFullPath.Path -OutputFile $resourceSummaryPath -StopFile $stopFilePath -IntervalSec $SampleIntervalSec -BackendProcessId $BackendPid -Pattern $BackendCommandLinePattern -ProcessNamePattern $BackendProcessNamePattern
} else {
    Start-RemoteSampler -ComputerName $RemoteSamplerHost -Credential $RemoteCredential -LocalSamplerScript $resourceSamplerFullPath.Path -RequestedRemoteDirectory $RemoteSamplerWorkingDirectory -RunLabel $runLabel -Timestamp $timestamp -IntervalSec $SampleIntervalSec -BackendProcessId $BackendPid -Pattern $BackendCommandLinePattern -ProcessNamePattern $BackendProcessNamePattern -LocalOutputFile $resourceSummaryPath
}

try {
    if ($samplerContext.mode -eq 'remote') {
        Write-Host "[resource-sampler] remote sampler started on $RemoteSamplerHost, target file $($samplerContext.remote_output_file)"
    } else {
        Write-Host "[resource-sampler] writing to $resourceSummaryPath"
    }
    & $K6Executable @resolvedK6Arguments
    $k6ExitCode = $LASTEXITCODE
} finally {
    Stop-Sampler -Context $samplerContext -WaitTimeoutSec ([math]::Max($RemoteSamplerWaitTimeoutSec, ($SampleIntervalSec * 6))) -LocalOutputFile $resourceSummaryPath
    if ([string]::IsNullOrWhiteSpace($originalTestRound)) {
        Remove-Item Env:TEST_ROUND -ErrorAction SilentlyContinue
    }
    else {
        $env:TEST_ROUND = $originalTestRound
    }
}

if ($k6ExitCode -ne 0) {
    throw "k6 exited with code $k6ExitCode"
}

$runLabelFromEnv = Get-RunLabel -RoundValue $resolvedTestRound
$k6ExtendedSummaryPath = Join-Path $workspaceRoot "k6-summary-extended-$runLabelFromEnv.json"
if (-not (Test-Path $k6ExtendedSummaryPath)) {
    $fallbackPath = Join-Path $workspaceRoot 'k6-summary-extended.json'
    if (Test-Path $fallbackPath) {
        $k6ExtendedSummaryPath = $fallbackPath
    }
}

Merge-ObservedSummary -K6ExtendedSummaryPath $k6ExtendedSummaryPath -ResourceSummaryPath $resourceSummaryPath -ObservedSummaryPath $observedSummaryPath -SampleInterval $SampleIntervalSec -BackendProcessId $BackendPid -BackendPattern $BackendCommandLinePattern -SamplerMode $samplerContext.mode -RemoteResourceSummaryPath $samplerContext.remote_output_file

Write-Host "[k6] extended summary: $k6ExtendedSummaryPath"
Write-Host "[resource-sampler] series summary: $resourceSummaryPath"
Write-Host "[observed-summary] merged output: $observedSummaryPath"