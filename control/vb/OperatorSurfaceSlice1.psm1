Set-StrictMode -Version Latest

function New-OperatorWarning {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Code,

        [Parameter(Mandatory = $true)]
        [string]$Message,

        [Parameter(Mandatory = $true)]
        [string]$Owner,

        [string[]]$Paths = @()
    )

    [pscustomobject][ordered]@{
        code = $Code
        severity = "warning"
        owner = $Owner
        message = $Message
        paths = @($Paths)
    }
}

function ConvertTo-NullableInt {
    param([AllowNull()][string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $null
    }

    $parsed = 0
    if ([int]::TryParse($Value.Trim(), [ref]$parsed)) {
        return $parsed
    }

    return $null
}

function ConvertTo-NullableBool {
    param([AllowNull()][string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $null
    }

    $parsed = $false
    if ([bool]::TryParse($Value.Trim(), [ref]$parsed)) {
        return $parsed
    }

    return $null
}

function Get-PropertyValue {
    param(
        [AllowNull()]$Object,
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    if ($null -eq $Object) {
        return $null
    }

    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }

    return $property.Value
}

function ConvertFrom-JsonCompat {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Text
    )

    $command = Get-Command ConvertFrom-Json
    if ($command.Parameters.ContainsKey("Depth")) {
        return ($Text | ConvertFrom-Json -Depth 64)
    }

    return ($Text | ConvertFrom-Json)
}

function Read-OrderedKeyValueDocument {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string[]]$OrderedKeys
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }

    $raw = Get-Content -LiteralPath $Path -Raw
    $data = [ordered]@{
        __path = (Resolve-Path -LiteralPath $Path).Path
        __raw = $raw
    }

    $pattern = "(?<key>" + (($OrderedKeys | ForEach-Object { [regex]::Escape($_) }) -join "|") + "):"
    $matches = [regex]::Matches($raw, $pattern)
    for ($i = 0; $i -lt $matches.Count; $i++) {
        $match = $matches[$i]
        $key = $match.Groups["key"].Value
        $start = $match.Index + $match.Length
        $end = if ($i + 1 -lt $matches.Count) { $matches[$i + 1].Index } else { $raw.Length }
        $value = $raw.Substring($start, $end - $start).Trim()
        $data[$key] = $value
    }

    return [pscustomobject]$data
}

function Read-ProposalReviewDocument {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }

    $lines = Get-Content -LiteralPath $Path
    $data = [ordered]@{
        __path = (Resolve-Path -LiteralPath $Path).Path
        review_notes = @()
    }
    $currentList = $null

    foreach ($line in $lines) {
        if ($line -match '^([A-Za-z0-9_]+):\s*(.*)$') {
            $key = $Matches[1]
            $value = $Matches[2]
            if ($value -eq "") {
                if (-not $data.Contains($key)) {
                    $data[$key] = @()
                }
                $currentList = $key
            }
            else {
                $data[$key] = $value.Trim()
                $currentList = $null
            }
            continue
        }

        if ($currentList -and $line -match '^\s*-\s*(.*)$') {
            $data[$currentList] = @($data[$currentList]) + $Matches[1]
        }
    }

    return [pscustomobject]$data
}

function Read-EndpointCapture {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }

    $raw = Get-Content -LiteralPath $Path -Raw
    $doc = ConvertFrom-JsonCompat -Text $raw
    $body = Get-PropertyValue -Object $doc -Name "body"
    if ($body -is [string]) {
        $trimmed = $body.Trim()
        if ($trimmed.StartsWith("{") -or $trimmed.StartsWith("[")) {
            try {
                $body = ConvertFrom-JsonCompat -Text $trimmed
            }
            catch {
                $body = Get-PropertyValue -Object $doc -Name "body"
            }
        }
    }

    return [pscustomobject][ordered]@{
        __path = (Resolve-Path -LiteralPath $Path).Path
        run_id = Get-PropertyValue -Object $doc -Name "run_id"
        captured_at_utc = Get-PropertyValue -Object $doc -Name "captured_at_utc"
        tick_index = Get-PropertyValue -Object $doc -Name "tick_index"
        method = Get-PropertyValue -Object $doc -Name "method"
        path = Get-PropertyValue -Object $doc -Name "path"
        http_status = Get-PropertyValue -Object $doc -Name "http_status"
        body = $body
    }
}

function Get-BundleArtifacts {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BundlePath
    )

    $resolved = (Resolve-Path -LiteralPath $BundlePath).Path
    $worldTickPaths = @(Get-ChildItem -LiteralPath $resolved -Filter "endpoint_world_tick_*.json" -File -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -ExpandProperty FullName)
    $stateTickPaths = @(Get-ChildItem -LiteralPath $resolved -Filter "endpoint_state_tick_*.json" -File -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -ExpandProperty FullName)
    $statePostTickPaths = @(Get-ChildItem -LiteralPath $resolved -Filter "endpoint_state_post_tick_*.json" -File -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -ExpandProperty FullName)

    [pscustomobject][ordered]@{
        bundle_path = $resolved
        manifest = Join-Path $resolved "manifest.yaml"
        proposal_review = Join-Path $resolved "proposal_review.md"
        operator_notes = Join-Path $resolved "operator_notes.md"
        endpoint_world_cold = Join-Path $resolved "endpoint_world_cold.json"
        endpoint_world_proposals_cold = Join-Path $resolved "endpoint_world_proposals_cold.json"
        endpoint_world_restart = Join-Path $resolved "endpoint_world_restart_cold.json"
        endpoint_state_restart = Join-Path $resolved "endpoint_state_restart_cold.json"
        log_startup = Join-Path $resolved "log_excerpt_startup.txt"
        log_restart = Join-Path $resolved "log_excerpt_restart.txt"
        world_tick_paths = $worldTickPaths
        state_tick_paths = $stateTickPaths
        state_post_tick_paths = $statePostTickPaths
    }
}

function Get-RunIdsFromBundleData {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$BundleData
    )

    $sources = @()
    $manifestRunId = Get-PropertyValue -Object $BundleData.manifest -Name "run_id"
    if (-not [string]::IsNullOrWhiteSpace([string]$manifestRunId)) {
        $sources += [pscustomobject]@{ source = "manifest.yaml"; run_id = $manifestRunId; path = $BundleData.artifacts.manifest }
    }
    $proposalRunId = Get-PropertyValue -Object $BundleData.proposal_review -Name "run_id"
    if (-not [string]::IsNullOrWhiteSpace([string]$proposalRunId)) {
        $sources += [pscustomobject]@{ source = "proposal_review.md"; run_id = $proposalRunId; path = $BundleData.artifacts.proposal_review }
    }
    $operatorRunId = Get-PropertyValue -Object $BundleData.operator_notes -Name "run_id"
    if (-not [string]::IsNullOrWhiteSpace([string]$operatorRunId)) {
        $sources += [pscustomobject]@{ source = "operator_notes.md"; run_id = $operatorRunId; path = $BundleData.artifacts.operator_notes }
    }
    foreach ($endpoint in $BundleData.endpoints) {
        if ($endpoint.run_id) {
            $sources += [pscustomobject]@{ source = [System.IO.Path]::GetFileName($endpoint.__path); run_id = $endpoint.run_id; path = $endpoint.__path }
        }
    }

    return @($sources)
}

function Get-RunBundleWarnings {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$BundleData
    )

    $warnings = [System.Collections.Generic.List[object]]::new()
    $artifacts = $BundleData.artifacts

    if (-not (Test-Path -LiteralPath $artifacts.manifest)) {
        $warnings.Add((New-OperatorWarning -Code "missing_manifest" -Message "manifest.yaml is missing from the run bundle." -Owner "runtime-harness" -Paths @($artifacts.manifest)))
    }

    if (-not (Test-Path -LiteralPath $artifacts.operator_notes)) {
        $warnings.Add((New-OperatorWarning -Code "missing_operator_notes" -Message "operator_notes.md is missing from the run bundle." -Owner "runtime-harness" -Paths @($artifacts.operator_notes)))
    }

    if (-not (Test-Path -LiteralPath $artifacts.proposal_review)) {
        $warnings.Add((New-OperatorWarning -Code "missing_proposal_review" -Message "proposal_review.md is missing from the run bundle." -Owner "runtime-harness" -Paths @($artifacts.proposal_review)))
    }

    if ($BundleData.proposal_review) {
        $proposalCountRaw = [string](Get-PropertyValue -Object $BundleData.proposal_review -Name "proposal_count")
        if ([string]::IsNullOrWhiteSpace($proposalCountRaw)) {
            $warnings.Add((New-OperatorWarning -Code "blank_proposal_count" -Message "proposal_review.md contains a blank proposal_count value." -Owner "run-bundle-writer" -Paths @($artifacts.proposal_review)))
        }
        else {
            $proposalCount = ConvertTo-NullableInt -Value $proposalCountRaw
            $reviewNotes = @(Get-PropertyValue -Object $BundleData.proposal_review -Name "review_notes")
            $pendingNoteCount = @($reviewNotes | Where-Object { $_ -match "^pending:" }).Count
            if ($null -ne $proposalCount -and $pendingNoteCount -gt 0 -and $proposalCount -ne $pendingNoteCount) {
                $warnings.Add((New-OperatorWarning -Code "proposal_count_note_mismatch" -Message "proposal_count does not match the number of pending proposal note lines." -Owner "run-bundle-writer" -Paths @($artifacts.proposal_review)))
            }
        }
    }

    $restartMissing = @()
    foreach ($restartPath in @($artifacts.endpoint_world_restart, $artifacts.endpoint_state_restart, $artifacts.log_restart)) {
        if (-not (Test-Path -LiteralPath $restartPath)) {
            $restartMissing += $restartPath
        }
    }
    if ($restartMissing.Count -gt 0) {
        $warnings.Add((New-OperatorWarning -Code "missing_restart_evidence" -Message "Restart evidence is incomplete for this run bundle." -Owner "runtime-harness" -Paths $restartMissing))
    }

    $runIds = @(Get-RunIdsFromBundleData -BundleData $BundleData)
    $uniqueRunIds = @($runIds.run_id | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Sort-Object -Unique)
    if ($uniqueRunIds.Count -gt 1) {
        $warnings.Add((New-OperatorWarning -Code "inconsistent_run_id" -Message "Run identifiers are inconsistent across bundle files." -Owner "runtime-harness" -Paths @($runIds.path)))
    }

    return @($warnings)
}

function Get-RunBundleData {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BundlePath
    )

    $artifacts = Get-BundleArtifacts -BundlePath $BundlePath
    $manifest = Read-OrderedKeyValueDocument -Path $artifacts.manifest -OrderedKeys @(
        "run_id",
        "scenario_name",
        "launch_mode",
        "smallville_port",
        "frozen_model",
        "launch_command",
        "jar_path",
        "startup_started_at_utc",
        "startup_healthy_at_utc",
        "startup_elapsed_seconds"
    )
    $proposalReview = Read-ProposalReviewDocument -Path $artifacts.proposal_review
    $operatorNotes = Read-OrderedKeyValueDocument -Path $artifacts.operator_notes -OrderedKeys @(
        "run_id",
        "scenario_name",
        "frozen_model",
        "startup_result",
        "control_stability",
        "seed_integrity",
        "plan_integrity",
        "action_log_quality",
        "restart_integrity",
        "shutdown_port_closed",
        "shutdown_process_gone",
        "next_decision"
    )

    $endpoints = @()
    foreach ($path in @(
            $artifacts.endpoint_world_cold,
            $artifacts.endpoint_world_proposals_cold,
            $artifacts.endpoint_world_restart,
            $artifacts.endpoint_state_restart
        ) + $artifacts.world_tick_paths + $artifacts.state_tick_paths + $artifacts.state_post_tick_paths) {
        $endpoint = Read-EndpointCapture -Path $path
        if ($endpoint) {
            $endpoints += $endpoint
        }
    }

    $bundleData = [pscustomobject][ordered]@{
        artifacts = $artifacts
        manifest = $manifest
        proposal_review = $proposalReview
        operator_notes = $operatorNotes
        endpoints = @($endpoints)
    }
    $bundleData | Add-Member -NotePropertyName warnings -NotePropertyValue @(Get-RunBundleWarnings -BundleData $bundleData)

    return $bundleData
}

function Get-ResolvedRunId {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$BundleData
    )

    foreach ($candidate in @(
            (Get-PropertyValue -Object $BundleData.manifest -Name "run_id"),
            (Get-PropertyValue -Object $BundleData.proposal_review -Name "run_id"),
            (Get-PropertyValue -Object $BundleData.operator_notes -Name "run_id")
        )) {
        if (-not [string]::IsNullOrWhiteSpace([string]$candidate)) {
            return [string]$candidate
        }
    }

    foreach ($endpoint in $BundleData.endpoints) {
        if (-not [string]::IsNullOrWhiteSpace([string]$endpoint.run_id)) {
            return [string]$endpoint.run_id
        }
    }

    return $null
}

function Get-HighestTickFromPaths {
    param([string[]]$Paths)

    $values = @()
    foreach ($path in @($Paths)) {
        $fileName = [System.IO.Path]::GetFileNameWithoutExtension($path)
        if ($fileName -match '_(\d+)$') {
            $values += [int]$Matches[1]
        }
    }

    if ($values.Count -eq 0) {
        return $null
    }

    return ($values | Measure-Object -Maximum).Maximum
}

function Get-LatestWorldTickEndpoint {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$BundleData
    )

    $latest = $null
    foreach ($endpoint in $BundleData.endpoints | Where-Object { $_.path -eq "/world" -and $null -ne $_.tick_index -and [int]$_.tick_index -gt 0 }) {
        if (-not $latest -or [int]$endpoint.tick_index -gt [int]$latest.tick_index) {
            $latest = $endpoint
        }
    }

    return $latest
}

function ConvertTo-ParsedProposalEntry {
    param([AllowNull()][string]$Note)

    if ([string]::IsNullOrWhiteSpace($Note)) {
        return $null
    }

    $pattern = '^pending:\s*\[(?<agent>[^\]]+)\]\s+(?<type>\S+)\s+parent=\[(?<parent>[^\]]*)\]\s+name=\[(?<name>[^\]]*)\]\s+state=\[(?<state>[^\]]*)\]\s+reason=\[(?<reason>.*)\]$'
    if ($Note -notmatch $pattern) {
        return $null
    }

    return [pscustomobject][ordered]@{
        agent = $Matches.agent
        type = $Matches.type
        parent = $Matches.parent
        name = $Matches.name
        proposed_state = $Matches.state
        reason = $Matches.reason
        source = "proposal_review.md"
    }
}

function Get-RunBundleSummaryView {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string]$BundlePath
    )

    $bundleData = Get-RunBundleData -BundlePath $BundlePath
    $highestWorldTick = Get-HighestTickFromPaths -Paths $bundleData.artifacts.world_tick_paths
    $highestPostTick = Get-HighestTickFromPaths -Paths $bundleData.artifacts.state_post_tick_paths
    $tickCountCaptured = @($bundleData.artifacts.world_tick_paths | ForEach-Object {
            if ([System.IO.Path]::GetFileNameWithoutExtension($_) -match '_(\d+)$') { [int]$Matches[1] }
        } | Sort-Object -Unique).Count
    $restartCaptured = (Test-Path -LiteralPath $bundleData.artifacts.endpoint_world_restart) -and
        (Test-Path -LiteralPath $bundleData.artifacts.endpoint_state_restart) -and
        (Test-Path -LiteralPath $bundleData.artifacts.log_restart)
    $proposalCountRaw = $null
    if ($bundleData.proposal_review) {
        $proposalCountRaw = [string](Get-PropertyValue -Object $bundleData.proposal_review -Name "proposal_count")
    }

    return [pscustomobject][ordered]@{
        bundle_path = $bundleData.artifacts.bundle_path
        run_id = Get-ResolvedRunId -BundleData $bundleData
        scenario_name = Get-PropertyValue -Object $bundleData.manifest -Name "scenario_name"
        launch_mode = Get-PropertyValue -Object $bundleData.manifest -Name "launch_mode"
        frozen_model = Get-PropertyValue -Object $bundleData.manifest -Name "frozen_model"
        smallville_port = ConvertTo-NullableInt -Value (Get-PropertyValue -Object $bundleData.manifest -Name "smallville_port")
        launch_command = Get-PropertyValue -Object $bundleData.manifest -Name "launch_command"
        startup_result = Get-PropertyValue -Object $bundleData.operator_notes -Name "startup_result"
        startup_healthy_at_utc = Get-PropertyValue -Object $bundleData.manifest -Name "startup_healthy_at_utc"
        tick_count_captured = $tickCountCaptured
        highest_world_tick = $highestWorldTick
        highest_state_post_tick = $highestPostTick
        restart_captured = $restartCaptured
        shutdown_port_closed = ConvertTo-NullableBool -Value (Get-PropertyValue -Object $bundleData.operator_notes -Name "shutdown_port_closed")
        shutdown_process_gone = ConvertTo-NullableBool -Value (Get-PropertyValue -Object $bundleData.operator_notes -Name "shutdown_process_gone")
        proposal_count_raw = $proposalCountRaw
        proposal_count = ConvertTo-NullableInt -Value $proposalCountRaw
        artifact_paths = [pscustomobject][ordered]@{
            manifest = $bundleData.artifacts.manifest
            proposal_review = $bundleData.artifacts.proposal_review
            operator_notes = $bundleData.artifacts.operator_notes
            endpoint_world_cold = $bundleData.artifacts.endpoint_world_cold
            latest_world_tick = if ($highestWorldTick) { $bundleData.artifacts.world_tick_paths[-1] } else { $null }
            endpoint_world_restart = $bundleData.artifacts.endpoint_world_restart
        }
        warnings = @($bundleData.warnings)
    }
}

function Get-ProposalReviewView {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string]$BundlePath
    )

    $bundleData = Get-RunBundleData -BundlePath $BundlePath
    $proposalCountRaw = if ($bundleData.proposal_review) { [string](Get-PropertyValue -Object $bundleData.proposal_review -Name "proposal_count") } else { $null }
    $latestWorldTick = Get-LatestWorldTickEndpoint -BundleData $bundleData
    $latestPendingProposals = @()
    $latestWorldTickBody = Get-PropertyValue -Object $latestWorldTick -Name "body"
    if ($latestWorldTick -and $latestWorldTickBody -and (Get-PropertyValue -Object $latestWorldTickBody -Name "pendingProposals")) {
        $latestPendingProposals = @((Get-PropertyValue -Object $latestWorldTickBody -Name "pendingProposals"))
    }
    else {
        $proposalEndpoints = @($bundleData.endpoints | Where-Object { $_.path -eq "/world/proposals" })
        if ($proposalEndpoints.Count -gt 0) {
            $proposalEndpoint = $proposalEndpoints[0]
            $proposalEndpointBody = Get-PropertyValue -Object $proposalEndpoint -Name "body"
            if ($proposalEndpointBody -and (Get-PropertyValue -Object $proposalEndpointBody -Name "proposals")) {
                $latestPendingProposals = @((Get-PropertyValue -Object $proposalEndpointBody -Name "proposals"))
            }
        }
    }

    $parsedEntries = @()
    foreach ($note in @(Get-PropertyValue -Object $bundleData.proposal_review -Name "review_notes")) {
        $entry = ConvertTo-ParsedProposalEntry -Note $note
        if ($entry) {
            $parsedEntries += $entry
        }
    }

    return [pscustomobject][ordered]@{
        bundle_path = $bundleData.artifacts.bundle_path
        run_id = Get-ResolvedRunId -BundleData $bundleData
        scenario_name = Get-PropertyValue -Object $bundleData.manifest -Name "scenario_name"
        tick_observed = ConvertTo-NullableInt -Value (Get-PropertyValue -Object $bundleData.proposal_review -Name "tick_observed")
        proposal_count_raw = $proposalCountRaw
        proposal_count = ConvertTo-NullableInt -Value $proposalCountRaw
        approved_count = ConvertTo-NullableInt -Value (Get-PropertyValue -Object $bundleData.proposal_review -Name "approved_count")
        rejected_count = ConvertTo-NullableInt -Value (Get-PropertyValue -Object $bundleData.proposal_review -Name "rejected_count")
        invalid_target_count = ConvertTo-NullableInt -Value (Get-PropertyValue -Object $bundleData.proposal_review -Name "invalid_target_count")
        review_notes = @(Get-PropertyValue -Object $bundleData.proposal_review -Name "review_notes")
        parsed_entries = @($parsedEntries)
        latest_pending_proposals = @($latestPendingProposals)
        source_paths = [pscustomobject][ordered]@{
            proposal_review = $bundleData.artifacts.proposal_review
            endpoint_world_proposals_cold = $bundleData.artifacts.endpoint_world_proposals_cold
            latest_world_tick = if ($latestWorldTick) { $latestWorldTick.__path } else { $null }
        }
        warnings = @($bundleData.warnings)
    }
}

Export-ModuleMember -Function Get-RunBundleSummaryView, Get-ProposalReviewView
