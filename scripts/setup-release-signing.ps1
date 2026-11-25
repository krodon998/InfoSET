[CmdletBinding()]
param(
    [string]$Repository = "krodon998/InfoSET",
    [string]$OutputDirectory = (Join-Path $env:USERPROFILE "Documents\InfoSET-signing"),
    [string]$Alias = "infoset-release",
    [switch]$UseExisting
)

$ErrorActionPreference = "Stop"

function Require-Command {
    param([Parameter(Mandatory)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found."
    }
}

function Read-ConfirmedPassword {
    $first = Read-Host "Create the permanent InfoSET signing password" -AsSecureString
    $second = Read-Host "Enter it again" -AsSecureString

    $firstPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($first)
    $secondPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($second)
    try {
        $firstText = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($firstPtr)
        $secondText = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secondPtr)
        if ($firstText -cne $secondText) {
            throw "The two passwords do not match."
        }
        if ($firstText.Length -lt 16) {
            throw "Use at least 16 characters for the permanent signing password."
        }
        return $firstText
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($firstPtr)
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secondPtr)
    }
}

function Set-RepositorySecret {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Value
    )

    & gh secret set $Name --repo $Repository --body $Value
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to set GitHub Actions secret '$Name'."
    }
}

Require-Command "keytool"
Require-Command "gh"
gh auth status | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "Sign in to GitHub CLI with 'gh auth login' first."
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$keystorePath = Join-Path $OutputDirectory "infoset-release.p12"
if ((Test-Path $keystorePath) -and -not $UseExisting) {
    throw "Refusing to overwrite the existing signing key: $keystorePath"
}
if (-not (Test-Path $keystorePath) -and $UseExisting) {
    throw "The existing signing key was not found: $keystorePath"
}

$password = Read-ConfirmedPassword
try {
    if (-not $UseExisting) {
        $keytoolArgs = @(
            "-genkeypair",
            "-v",
            "-storetype", "PKCS12",
            "-keystore", $keystorePath,
            "-alias", $Alias,
            "-keyalg", "RSA",
            "-keysize", "4096",
            "-validity", "10000",
            "-dname", "CN=InfoSET, OU=Release, O=InfoSET, C=CN",
            "-storepass", $password,
            "-keypass", $password
        )
        & keytool @keytoolArgs
        if ($LASTEXITCODE -ne 0) {
            throw "keytool failed to create the InfoSET signing key."
        }
    }

    $certificateInfo = (& keytool -list -v -keystore $keystorePath -alias $Alias -storepass $password | Out-String)
    if ($LASTEXITCODE -ne 0) {
        throw "The signing key, alias, or password could not be verified."
    }

    $keystoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystorePath))

    Set-RepositorySecret "INFOSET_KEYSTORE_BASE64" $keystoreBase64
    Set-RepositorySecret "INFOSET_STORE_PASSWORD" $password
    Set-RepositorySecret "INFOSET_KEY_ALIAS" $Alias
    Set-RepositorySecret "INFOSET_KEY_PASSWORD" $password

    Write-Host ""
    Write-Host "InfoSET release signing is ready." -ForegroundColor Green
    Write-Host "Keystore backup: $keystorePath"
    Write-Host "Back up this file and its password in two separate safe places."
    Write-Host ""
    Write-Host $certificateInfo
}
finally {
    $password = $null
    $keystoreBase64 = $null
    $certificateInfo = $null
}
