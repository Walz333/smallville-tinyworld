[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [string]$RunId,

  [Parameter(Mandatory = $true)]
  [int]$TickObserved,

  [Parameter(Mandatory = $true)]
  [string]$OutputPath,

  [object]$Proposals,

  [string[]]$ReviewNotes = @(),

  [int]$ApprovedCount = 0,

  [int]$RejectedCount = 0,

  [int]$InvalidTargetCount = 0
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-ProposalList {
  param(
    [object]$ProposalInput
  )

  if ($null -eq $ProposalInput) {
    return @()
  }

  # Re-wrap after filtering so 1-item results stay arrays instead of collapsing to a scalar PSCustomObject.
  return @(@($ProposalInput) | Where-Object { $null -ne $_ })
}

function Get-ProposalValue {
  param(
    [object]$Proposal,
    [string]$Name
  )

  if ($null -eq $Proposal) {
    return ''
  }

  $property = $Proposal.PSObject.Properties[$Name]
  if ($null -eq $property -or $null -eq $property.Value) {
    return ''
  }

  return [string]$property.Value
}

$proposalList = @(Get-ProposalList -ProposalInput $Proposals)
$proposalCount = $proposalList.Count

$lines = @(
  "run_id: $RunId"
  "tick_observed: $TickObserved"
  "proposal_count: $proposalCount"
  "approved_count: $ApprovedCount"
  "rejected_count: $RejectedCount"
  "invalid_target_count: $InvalidTargetCount"
  'review_notes:'
)

if (@($ReviewNotes).Count -gt 0) {
  $lines += $ReviewNotes
} elseif ($proposalCount -eq 0) {
  $lines += '- none observed during review'
} else {
  foreach ($proposal in $proposalList) {
    $lines += (
      '- pending: [{0}] {1} parent=[{2}] name=[{3}] state=[{4}] reason=[{5}]' -f `
        (Get-ProposalValue -Proposal $proposal -Name 'agent'), `
        (Get-ProposalValue -Proposal $proposal -Name 'type'), `
        (Get-ProposalValue -Proposal $proposal -Name 'parentLocation'), `
        (Get-ProposalValue -Proposal $proposal -Name 'name'), `
        (Get-ProposalValue -Proposal $proposal -Name 'proposedState'), `
        (Get-ProposalValue -Proposal $proposal -Name 'reason')
    )
  }
}

$directory = Split-Path -Path $OutputPath -Parent
if ($directory -and -not (Test-Path $directory)) {
  New-Item -ItemType Directory -Path $directory -Force | Out-Null
}

$lines | Set-Content -Path $OutputPath
