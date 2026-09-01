<#
.SYNOPSIS
    Fills a running SIGNA network with member banks and enrolled customers.

.DESCRIPTION
    Signs in as the development super administrator, registers a handful of
    member banks, and enrols a population of customers through the real
    privacy layer: each identifier is blinded locally, evaluated by the
    producer service, checked against its DLEQ proof and unblinded. Nothing
    here takes a shortcut the console does not also take.

    Some people are deliberately enrolled at more than one bank, under the
    same identifier and a different display name. That is the interesting
    part to look at afterwards: the records link on the pseudonym alone, so
    blocking one of them suspends the others across the network.

    Start the system first, with an in-memory database:

        .\start.ps1 -InMemory

    Development data for a development database. The identifiers below are
    invented, and so are the banks.

.PARAMETER BaseUrl
    Producer service to populate. Defaults to the local one.

.PARAMETER Banks
    How many member banks to register.

.PARAMETER Customers
    How many distinct people to invent. Each is enrolled at one to three
    banks, so the number of customer records comes out higher than this.

.PARAMETER Seed
    Fixes the population. The same seed invents the same people with the same
    identifiers, so re-running adds nothing rather than piling up duplicates.
    Change it to add a second, unrelated population.

.PARAMETER NoPause
    Do not wait for a keypress at the end. For CI, where nobody is watching.

.EXAMPLE
    .\simulate.ps1

.EXAMPLE
    .\simulate.ps1 -Banks 8 -Customers 60

.EXAMPLE
    .\simulate.ps1 -Seed 7 -Customers 10
#>

[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:9090',
    [int]$Banks = 6,
    [int]$Customers = 24,
    [string]$Email = 'admin@signa.az',
    [string]$Password = 'signa-dev-password',
    [int]$Seed = 20260901,
    [switch]$NoPause
)

$ErrorActionPreference = 'Stop'

# Invoke-RestMethod draws a progress bar for every call. Sixty of them turn a
# fast run into a flicker show.
$ProgressPreference = 'SilentlyContinue'

try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$root = $PSScriptRoot
$BaseUrl = $BaseUrl.TrimEnd('/')

$script:failed = $false
$script:stoppedDeliberately = $false

# --------------------------------------------------------------- output ----

function Write-Step  ($message) { Write-Host "  $message" -ForegroundColor White }
function Write-Info  ($message) { Write-Host "  $message" -ForegroundColor DarkGray }
function Write-Good  ($message) { Write-Host "  $message" -ForegroundColor Green }
function Write-Warn  ($message) { Write-Host "  $message" -ForegroundColor Yellow }
function Write-Bad   ($message) { Write-Host "  $message" -ForegroundColor Red }

function Wait-BeforeClosing {
    if ($NoPause) { return }
    try {
        Write-Host '  Press any key to close...' -ForegroundColor DarkGray
        $null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')
    } catch {
        Start-Sleep -Seconds 5
    }
}

<#
    Ends the run with a message the reader can actually see.

    Everything below runs inside one try/finally so that a failure anywhere,
    prerequisites included, still reaches Wait-BeforeClosing. A script that
    exits early takes its own error message off the screen with it.
#>
function Stop-Here ($message, $remedy) {
    # Marks the failure as one this script diagnosed, so the handler prints the
    # remedy rather than a line number pointing at this throw.
    $script:stoppedDeliberately = $true

    $lines = @($message)
    foreach ($line in $remedy) { $lines += "  $line" }
    throw ($lines -join [Environment]::NewLine)
}

# ------------------------------------------------------------------ http ----

<#
    Turns a failed request into the message the service actually sent.

    The producer answers errors with { message, status, errors }, and the
    per-field map is where validation failures live. Without this, every
    failure here would read "The remote server returned an error: (400)".
#>
function Read-ApiError ($errorRecord) {
    $response = $errorRecord.Exception.Response
    if (-not $response) { return $errorRecord.Exception.Message }

    $status = 0
    try { $status = [int]$response.StatusCode } catch { }

    $text = ''
    try {
        $stream = $response.GetResponseStream()
        $reader = New-Object IO.StreamReader($stream, [Text.Encoding]::UTF8)
        $text = $reader.ReadToEnd()
        $reader.Close()
    } catch { }

    $detail = $text
    try {
        $parsed = $text | ConvertFrom-Json
        if ($parsed.errors -and $parsed.errors.PSObject.Properties.Count -gt 0) {
            $detail = ($parsed.errors.PSObject.Properties | ForEach-Object { $_.Value }) -join ' '
        } elseif ($parsed.message) {
            $detail = $parsed.message
        }
    } catch { }

    if (-not $detail) { $detail = $errorRecord.Exception.Message }
    return "HTTP $status - $detail"
}

<#
    One JSON request, with UTF-8 forced in both directions.

    Neither direction is UTF-8 by default here. Windows PowerShell 5.1 sends a
    string body in the machine's single-byte code page, and it decodes a
    response whose Content-Type carries no charset -- which is what this
    service sends -- the same way. Left alone, a bank called
    "Xəzər Sahil Bankı" is stored mangled and, worse, read back as something
    that no longer equals what was sent, so the second run of this script
    would register every bank a second time.

    Invoke-WebRequest rather than Invoke-RestMethod because only the former
    hands back the raw bytes to decode.
#>
function Invoke-Api {
    param(
        [string]$Method = 'GET',
        [Parameter(Mandatory = $true)][string]$Path,
        $Body,
        [hashtable]$Headers
    )

    if (-not $Headers) { $Headers = @{} }

    $parameters = @{
        Method          = $Method
        Uri             = "$BaseUrl$Path"
        Headers         = $Headers
        UseBasicParsing = $true
    }

    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json; charset=utf-8'
        $parameters.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 6 -Compress))
    }

    try {
        $response = Invoke-WebRequest @parameters
    } catch {
        throw (Read-ApiError $_)
    }

    $text = [Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    if (-not $text.Trim()) { return $null }

    # Write-Output, not a bare ConvertFrom-Json: on Windows PowerShell 5.1
    # ConvertFrom-Json hands an array to the caller as a single object, so
    # @(...) around the call wraps it one level deeper than it looks and
    # $bank.id silently becomes every id at once. Write-Output enumerates,
    # which is what the @() call sites below expect.
    Write-Output ($text | ConvertFrom-Json)
}

# ------------------------------------------------------------ population ----

# Fictional institutions. None of these is a real bank, and the whole point of
# the exercise is that the network never learns who its customers are anyway.
$bankNames = @(
    'Alov Bank',
    'Xəzər Sahil Bankı',
    'Nizami Kapital',
    'Qobustan Kredit Bankı',
    'Şirvan İnvestisiya Bankı',
    'Abşeron Ticarət Bankı',
    'Kür Bank',
    'Göygöl Bank'
)

$womenNames = @('Aysel', 'Nigar', 'Günel', 'Leyla', 'Sevinc', 'Nərmin', 'Aynur',
                'Zeynəb', 'Lalə', 'Gülnar', 'Xəyalə', 'Aytən', 'Səbinə', 'Mehriban')
$menNames   = @('Rəşad', 'Elvin', 'Murad', 'Kamran', 'Orxan', 'Tural', 'Ramin',
                'Fərid', 'Emin', 'Samir', 'Vüqar', 'Elçin', 'Rauf', 'Kənan')
$surnames   = @('Məmməd', 'Əli', 'Hüseyn', 'Quli', 'İsmayıl', 'Rəhim',
                'Səfər', 'Abbas', 'Nəbi', 'Cəfər', 'Kərim', 'Vəli')

# An Azerbaijani FIN is seven characters. I and O are left out, as they are on
# the real document, because they are unreadable next to 1 and 0.
$finAlphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ0123456789'

function New-Population ($count, $bankCount, $rng) {
    $people = @()
    $seenFin = @{}

    for ($i = 0; $i -lt $count; $i++) {
        $female = ($rng.Next(2) -eq 0)
        if ($female) {
            $first = $womenNames[$rng.Next($womenNames.Count)]
            $last  = $surnames[$rng.Next($surnames.Count)] + 'ova'
        } else {
            $first = $menNames[$rng.Next($menNames.Count)]
            $last  = $surnames[$rng.Next($surnames.Count)] + 'ov'
        }

        do {
            $fin = -join (1..7 | ForEach-Object { $finAlphabet[$rng.Next($finAlphabet.Length)] })
        } while ($seenFin.ContainsKey($fin))
        $seenFin[$fin] = $true

        # Most people bank in one place. A meaningful minority do not, and they
        # are the ones the linkage view exists for.
        $roll = $rng.Next(100)
        if ($roll -lt 55) { $atBanks = 1 } elseif ($roll -lt 85) { $atBanks = 2 } else { $atBanks = 3 }
        if ($atBanks -gt $bankCount) { $atBanks = $bankCount }

        $chosen = @(0..($bankCount - 1) | Sort-Object { $rng.Next() } | Select-Object -First $atBanks)

        $people += [pscustomobject]@{
            First      = $first
            Last       = $last
            Name       = "$first $last"
            Fin        = $fin
            BankIndex  = $chosen
        }
    }

    return $people
}

# ------------------------------------------------------------------ oprf ----

<#
    The bank's half of the protocol, run by Node against the console's own
    client.

    Reusing frontend/src/lib/oprf.js rather than reimplementing P-256 in
    PowerShell is not only shorter: it means this script exercises the same
    code path the operator does, DLEQ verification included. A pseudonym that
    this script can produce is one the console could have produced.
#>
$deriveScript = @'
import { webcrypto } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';

// Node exposes Web Crypto globally from 19 onward; older releases need help.
if (!globalThis.crypto) globalThis.crypto = webcrypto;

const [, , oprfModuleUrl, jobPath, outPath] = process.argv;

const {
  canonicalize, blind, finalize, serializeElement, deserializeElement,
  deserializeProof, bytesToHex, hexToBytes,
} = await import(oprfModuleUrl);

const job = JSON.parse(readFileSync(jobPath, 'utf8'));
const items = Array.isArray(job.items) ? job.items : [job.items];
const results = [];

for (const item of items) {
  try {
    const input = canonicalize(item.identifierType, item.identifier);

    const { blind: blindScalar, blindedElement } = await blind(input);
    const blindedHex = bytesToHex(serializeElement(blindedElement));

    const response = await fetch(`${job.baseUrl}/api/v1/oprf/evaluate`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Signa-Client-Id': item.clientId,
        'X-Signa-Api-Key': item.apiKey,
      },
      body: JSON.stringify({ blindedElements: [blindedHex] }),
    });
    if (!response.ok) {
      throw new Error(`evaluate returned ${response.status}: ${(await response.text()).slice(0, 200)}`);
    }

    const evaluation = await response.json();
    const publicKey = deserializeElement(hexToBytes(evaluation.publicKey));
    const evaluated = deserializeElement(hexToBytes(evaluation.evaluatedElements[0]));
    const proof = deserializeProof(evaluation.proof);

    // finalize verifies the proof before it unblinds; an unproved evaluation
    // throws here rather than becoming a pseudonym nobody can account for.
    const pseudonym = await finalize(input, blindScalar, evaluated, blindedElement, publicKey, proof);

    results.push({ ref: item.ref, pseudonym: bytesToHex(pseudonym), keyId: evaluation.keyId });
  } catch (error) {
    results.push({ ref: item.ref, error: String((error && error.message) || error) });
  }
}

writeFileSync(outPath, JSON.stringify(results), 'utf8');
'@

function Get-Pseudonyms ($items) {
    $workspace = Join-Path ([IO.Path]::GetTempPath()) ("signa-simulate-" + [Guid]::NewGuid().ToString('n'))
    $null = New-Item -ItemType Directory -Path $workspace

    try {
        $helperPath = Join-Path $workspace 'derive.mjs'
        $jobPath    = Join-Path $workspace 'job.json'
        $outPath    = Join-Path $workspace 'out.json'

        $utf8 = New-Object Text.UTF8Encoding($false)
        [IO.File]::WriteAllText($helperPath, $deriveScript, $utf8)

        $job = @{ baseUrl = $BaseUrl; items = @($items) }
        [IO.File]::WriteAllText($jobPath, ($job | ConvertTo-Json -Depth 6 -Compress), $utf8)

        $oprfModule = Join-Path $root 'frontend\src\lib\oprf.js'
        if (-not (Test-Path $oprfModule)) {
            Stop-Here "The console's OPRF client is missing at $oprfModule" @(
                'Run this script from the folder that contains producer-main and frontend.'
            )
        }
        $oprfModuleUrl = ([Uri]$oprfModule).AbsoluteUri

        # stderr is folded into the output on purpose: if Node fails to start,
        # its reason is the only useful thing on screen.
        $priorPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $noise = & node $helperPath $oprfModuleUrl $jobPath $outPath 2>&1
        } finally {
            $ErrorActionPreference = $priorPreference
        }

        if (-not (Test-Path $outPath)) {
            Stop-Here 'The pseudonym helper did not produce a result.' @(
                'Node reported:',
                (($noise | ForEach-Object { "$_" }) -join ' ')
            )
        }

        return @([IO.File]::ReadAllText($outPath, [Text.Encoding]::UTF8) | ConvertFrom-Json)
    } finally {
        Remove-Item $workspace -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# ------------------------------------------------------------------ main ----

Write-Host ''
Write-Host '  SIGNA' -ForegroundColor White -NoNewline
Write-Host '  simulation' -ForegroundColor DarkGray
Write-Host '  ---------------------------------------------------------'
Write-Host ''

try {
    # -- prerequisites ------------------------------------------------------

    Write-Step 'Checking the service'

    if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
        Stop-Here 'node is not on PATH.' @(
            'The privacy layer runs in the console''s JavaScript client, so this',
            'script needs Node 20 or newer. Install it and reopen this terminal.'
        )
    }

    try {
        $parameters = Invoke-Api -Path '/api/v1/oprf/public-key'
    } catch {
        Stop-Here "The producer service is not answering on $BaseUrl" @(
            'Start it first, in another terminal:',
            '    .\start.ps1 -InMemory',
            '',
            "Reason: $($_.Exception.Message)"
        )
    }
    Write-Info "  producer $BaseUrl"
    Write-Info "  oprf key $($parameters.activeKeyId) ($($parameters.ciphersuite))"

    # -- sign in ------------------------------------------------------------

    Write-Step 'Signing in'
    try {
        $session = Invoke-Api -Method POST -Path '/api/v1/auth/login' -Body @{ email = $Email; password = $Password }
    } catch {
        Stop-Here "Could not sign in as $Email" @(
            'These are the development bootstrap credentials seeded by start.ps1.',
            'If you changed BOOTSTRAP_ADMIN_EMAIL or BOOTSTRAP_ADMIN_PASSWORD, pass',
            'them here:   .\simulate.ps1 -Email you@example.com -Password ...',
            '',
            "Reason: $($_.Exception.Message)"
        )
    }
    $operator = @{ Authorization = "Bearer $($session.token)" }
    Write-Info "  $Email"

    # -- banks --------------------------------------------------------------

    Write-Step 'Member banks'

    $existing = @(Invoke-Api -Path '/api/v1/banks' -Headers $operator)
    $members = @()

    for ($i = 0; $i -lt $Banks; $i++) {
        if ($i -lt $bankNames.Count) {
            $name = $bankNames[$i]
        } else {
            $name = "Demo Bank " + ($i + 1)
        }

        $match = $existing | Where-Object { $_.name -eq $name } | Select-Object -First 1

        if ($match) {
            # The API key is readable once, at issue. Rotating is the supported
            # way to get a usable one for a bank that already exists, and it
            # keeps a second run from creating a second copy of every bank.
            $credentials = Invoke-Api -Method POST -Path "/api/v1/banks/$($match.id)/rotate-api-key" -Headers $operator
            Write-Host '    ~ ' -ForegroundColor DarkGray -NoNewline
            Write-Host $name.PadRight(28) -NoNewline
            Write-Info 'already registered, key rotated'
        } else {
            $credentials = Invoke-Api -Method POST -Path '/api/v1/banks' -Headers $operator -Body @{ name = $name }
            Write-Host '    + ' -ForegroundColor Green -NoNewline
            Write-Host $name.PadRight(28) -NoNewline
            Write-Info $credentials.clientId
        }

        $members += [pscustomobject]@{
            Id       = $credentials.bankId
            Name     = $name
            ClientId = $credentials.clientId
            ApiKey   = $credentials.apiKey
        }
    }

    # -- population ---------------------------------------------------------

    Write-Step 'Population'

    $rng = New-Object Random $Seed
    $people = New-Population $Customers $members.Count $rng

    $enrolments = @()
    foreach ($person in $people) {
        $atBank = 0
        foreach ($bankIndex in $person.BankIndex) {
            # The same person, filed under a different label at the second bank.
            # Nothing links these two records except the pseudonym, which is the
            # claim the demo is making.
            if ($atBank -eq 1) {
                $displayName = "$($person.First.Substring(0, 1)). $($person.Last)"
            } else {
                $displayName = $person.Name
            }

            $enrolments += [pscustomobject]@{
                Ref         = $enrolments.Count
                Person      = $person
                Bank        = $members[$bankIndex]
                DisplayName = $displayName
            }
            $atBank++
        }
    }

    $linked = @($people | Where-Object { $_.BankIndex.Count -gt 1 })
    Write-Info "  $($people.Count) people, $($enrolments.Count) enrolments, $($linked.Count) of them at more than one bank"

    # -- pseudonyms ---------------------------------------------------------

    Write-Step 'Deriving pseudonyms'
    Write-Info '  blind, evaluate, verify the proof, unblind (one round trip each)'

    $items = foreach ($enrolment in $enrolments) {
        @{
            ref            = $enrolment.Ref
            identifierType = 'az-fin'
            identifier     = $enrolment.Person.Fin
            clientId       = $enrolment.Bank.ClientId
            apiKey         = $enrolment.Bank.ApiKey
        }
    }

    $derived = Get-Pseudonyms $items

    $byRef = @{}
    foreach ($result in $derived) { $byRef[[int]$result.ref] = $result }

    $failedDerivations = @($derived | Where-Object { $_.error })
    if ($failedDerivations.Count -gt 0) {
        Write-Warn "  $($failedDerivations.Count) could not be derived"
        Write-Info "  first reason: $($failedDerivations[0].error)"
    }
    Write-Good "  $($derived.Count - $failedDerivations.Count) pseudonyms derived"

    # -- enrolment ----------------------------------------------------------

    Write-Step 'Enrolling'

    $enrolled = 0
    $alreadyThere = 0
    $failed = 0
    $firstFailure = $null

    foreach ($enrolment in $enrolments) {
        $result = $byRef[$enrolment.Ref]
        if (-not $result -or $result.error) { $failed++; continue }

        try {
            Invoke-Api -Method POST -Path '/api/v1/customers' -Headers $operator -Body @{
                name      = $enrolment.DisplayName
                pseudonym = $result.pseudonym
                oprfKeyId = $result.keyId
                bankId    = $enrolment.Bank.Id
            } | Out-Null
            $enrolled++
        } catch {
            if ("$($_.Exception.Message)" -match 'ALREADY ENROLLED') {
                $alreadyThere++
            } else {
                $failed++
                if (-not $firstFailure) { $firstFailure = $_.Exception.Message }
            }
        }
    }

    Write-Good "  $enrolled enrolled"
    if ($alreadyThere -gt 0) { Write-Info "  $alreadyThere already on file from an earlier run" }
    if ($failed -gt 0) {
        Write-Bad "  $failed failed"
        if ($firstFailure) { Write-Info "  first reason: $firstFailure" }
    }

    # -- summary ------------------------------------------------------------

    $bankTotal = @(Invoke-Api -Path '/api/v1/banks' -Headers $operator).Count

    # Spring Boot 4 nests the page metadata; older ones put it at the root.
    $customerPage = Invoke-Api -Path '/api/v1/customers?page=0&size=1' -Headers $operator
    $customerTotal = $customerPage.page.totalElements
    if ($null -eq $customerTotal) { $customerTotal = $customerPage.totalElements }

    Write-Host ''
    Write-Host '  ---------------------------------------------------------'
    Write-Host '  Banks      ' -NoNewline; Write-Host $bankTotal -ForegroundColor Cyan
    Write-Host '  Customers  ' -NoNewline; Write-Host $customerTotal -ForegroundColor Cyan
    Write-Host ''

    if ($linked.Count -gt 0) {
        $example = $linked[0]
        Write-Host '  Enrolled at more than one bank' -ForegroundColor White
        foreach ($person in ($linked | Select-Object -First 3)) {
            $where = ($person.BankIndex | ForEach-Object { $members[$_].Name }) -join ', '
            Write-Info "  $($person.Name.PadRight(22)) $where"
        }
        Write-Host ''
        Write-Host "  Block $($example.Name) in Customers and watch the other" -ForegroundColor DarkGray
        Write-Host '  record suspend itself. The two are linked by pseudonym only.' -ForegroundColor DarkGray
        Write-Host ''
    }

    Write-Host '  Console    ' -NoNewline; Write-Host 'http://localhost:5173' -ForegroundColor Cyan
    Write-Host "  Sign in with $Email" -ForegroundColor DarkGray
    Write-Host '  ---------------------------------------------------------'
    Write-Host ''
} catch {
    $script:failed = $true

    Write-Host ''
    $headline = $true
    foreach ($line in ("$($_.Exception.Message)" -split "`r?`n")) {
        if ($headline) { Write-Bad "  $line"; $headline = $false }
        else { Write-Info "  $line" }
    }
    if (-not $script:stoppedDeliberately -and $_.InvocationInfo -and $_.InvocationInfo.ScriptLineNumber) {
        Write-Info "  at line $($_.InvocationInfo.ScriptLineNumber)"
    }
    Write-Host ''
} finally {
    Wait-BeforeClosing
}

# After the pause, not inside the catch: exiting from within the try would be
# the one way to leave without the reader having seen why.
if ($script:failed) { exit 1 }
