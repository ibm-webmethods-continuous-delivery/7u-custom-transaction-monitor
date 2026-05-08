<#
.SYNOPSIS
    Adds transaction classification flags to a unified service metrics CSV.

.DESCRIPTION
    This script reads a unified metrics CSV and adds three columns:

    - TxnBaseFlag:
        0 for services starting with internal namespaces:
        wm., pub., com., ws., or /ws/
        1 for all other services

    - TxnExceptionFlag:
        1 for exact matches in the known transaction-exception list
        0 otherwise

    - TxnEffectiveFlag:
        TxnBaseFlag + TxnExceptionFlag

    Intended usage:
        ActualTransactionIntervals = TransactionIntervalsCount * TxnEffectiveFlag

.PARAMETER InputFile
    Path to the input unified_metrics.csv file.

.PARAMETER OutputFile
    Path to the output CSV file with added transaction flag columns.

.EXAMPLE
    .\Add-TransactionFlags.ps1 `
        -InputFile "C:\data\unified_metrics.csv" `
        -OutputFile "C:\data\unified_metrics_with_flags.csv"
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$InputFile,

    [Parameter(Mandatory=$true)]
    [string]$OutputFile
)

function Write-Log {
    param([string]$Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$timestamp] $Message"
}

function Test-InternalNamespace {
    param([string]$ServiceNS)

    if ([string]::IsNullOrWhiteSpace($ServiceNS)) {
        return $false
    }

    return (
        $ServiceNS.StartsWith("wm.") -or
        $ServiceNS.StartsWith("pub.") -or
        $ServiceNS.StartsWith("com.") -or
        $ServiceNS.StartsWith("ws.") -or
        $ServiceNS.StartsWith("/ws/")
    )
}

Write-Log "Starting transaction flag enrichment..."
Write-Log "Input file: $InputFile"
Write-Log "Output file: $OutputFile"

if (-not (Test-Path $InputFile)) {
    Write-Error "Input file not found: $InputFile"
    exit 1
}

$exceptionServices = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
@(
    "wm.tn:receive",
    "wm.tn.doc.xml:routeXml",
    "wm.EDIINT:receive",
    "wm.prt.dispatch:handlePublishedInput",
    "wm.prt.dispatch:handleSubprocessStart",
    "wm.prt.dispatch:handleCallActivityStart",
    "wm.prt.dispatch:handleEDAEvent",
    "wm.prt.dispatch:handleRequestReply",
    "wm.prt.dispatch:invokeCallActivityStart",
    "wm.prt.dispatch:invokeSubprocessStart",
    "pub.prt.tn:handleBizDoc",
    "pub.sap.client:invokeTransaction",
    "pub.sap.client:sendIDoc",
    "pub.sap.bapi:commit",
    "pub.sap.transport.ALE:OutboundProcess",
    "wm.ach.tn.trp:receive",
    "wm.ach.trp:receive",
    "wm.ach.trp:receiveStream",
    "wm.ip.hl7.tn.service:receive",
    "pub.estd.hipaa:receive",
    "pub.estd.rosettaNet:receive",
    "/ws/msh/receive",
    "wm.ip.rn:receive",
    "wm.ip.ebxml.MSH:receive",
    "pub.estd.chem:receive",
    "wm.b2b.io.core:submit",
    "wm.b2b.cxml:receiveCXML",
    "wm.channels.services:gateway",
    "wm.cloudstreams.listener.event:invokeService",
    "wm.oftp.gateway.Gw:fetchAndUpdateStatus",
    "wm.oftp.tn:receiveOftp",
    "wm.oftp.tn:deliverOftp",
    "wm.x400.tn:receiveX400",
    "wm.x400.gateway.Gw:sendAndFetch",
    "wm.prt.dispatch:handleTransition"
) | ForEach-Object {
    [void]$exceptionServices.Add($_)
}

Write-Log "Loaded $($exceptionServices.Count) transaction exception services"

try {
    $records = Import-Csv -Path $InputFile
} catch {
    Write-Error "Failed to read input CSV: $_"
    exit 1
}

if ($null -eq $records -or $records.Count -eq 0) {
    Write-Error "Input CSV is empty or contains only header: $InputFile"
    exit 1
}

$totalRecords = 0
$baseTransactionalCount = 0
$exceptionCount = 0
$effectiveTransactionalCount = 0

$enrichedRecords = foreach ($record in $records) {
    $totalRecords++

    $serviceNS = [string]$record.ServiceNS
    $isInternal = Test-InternalNamespace -ServiceNS $serviceNS
    $txnBaseFlag = if ($isInternal) { 0 } else { 1 }
    $txnExceptionFlag = if ($exceptionServices.Contains($serviceNS)) { 1 } else { 0 }
    $txnEffectiveFlag = $txnBaseFlag + $txnExceptionFlag

    if ($txnBaseFlag -eq 1) {
        $baseTransactionalCount++
    }

    if ($txnExceptionFlag -eq 1) {
        $exceptionCount++
    }

    if ($txnEffectiveFlag -gt 0) {
        $effectiveTransactionalCount++
    }

    $actualTransactionIntervals = 0
    if ($record.PSObject.Properties.Name -contains "TransactionIntervalsCount") {
        try {
            $actualTransactionIntervals = [int]$record.TransactionIntervalsCount * $txnEffectiveFlag
        } catch {
            $actualTransactionIntervals = 0
        }
    }

    $ordered = [ordered]@{}
    foreach ($property in $record.PSObject.Properties) {
        $ordered[$property.Name] = $property.Value
    }

    $ordered["TxnBaseFlag"] = $txnBaseFlag
    $ordered["TxnExceptionFlag"] = $txnExceptionFlag
    $ordered["TxnEffectiveFlag"] = $txnEffectiveFlag
    $ordered["ActualTransactionIntervals"] = $actualTransactionIntervals

    [PSCustomObject]$ordered
}

$outputFolder = Split-Path -Path $OutputFile -Parent
if (-not [string]::IsNullOrWhiteSpace($outputFolder) -and -not (Test-Path $outputFolder)) {
    try {
        New-Item -Path $outputFolder -ItemType Directory -Force | Out-Null
    } catch {
        Write-Error "Failed to create output folder: $_"
        exit 1
    }
}

try {
    $enrichedRecords | Export-Csv -Path $OutputFile -NoTypeInformation -Encoding UTF8
} catch {
    Write-Error "Failed to write output CSV: $_"
    exit 1
}

Write-Log "Enrichment completed successfully"
Write-Log "  Total records: $totalRecords"
Write-Log "  TxnBaseFlag = 1 records: $baseTransactionalCount"
Write-Log "  TxnExceptionFlag = 1 records: $exceptionCount"
Write-Log "  TxnEffectiveFlag > 0 records: $effectiveTransactionalCount"
Write-Log "Output written to: $OutputFile"

# Made with Bob