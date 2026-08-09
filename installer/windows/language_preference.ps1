param(
  [Parameter(Mandatory = $true)]
  [ValidateSet('Read', 'Write')]
  [string] $Mode,

  [ValidateSet('zh-Hans', 'zh-Hant', 'en')]
  [string] $Language = 'zh-Hans'
)

$ErrorActionPreference = 'Stop'

$LanguageKey = 'flutter.gang.language'
$AllowedLanguages = @('zh-Hans', 'zh-Hant', 'en')
$PreferencesPath = Join-Path $env:APPDATA 'com.gangchat\Gang Chat\shared_preferences.json'
$LegacyPreferencesPath = Join-Path $env:APPDATA 'com.gangchat\client\shared_preferences.json'

function Normalize-Language([string] $Value) {
  if ($AllowedLanguages -contains $Value) {
    return $Value
  }
  return 'zh-Hans'
}

function New-PreferenceObject {
  return New-Object psobject
}

function Read-Preferences([string] $Path = $PreferencesPath) {
  if (!(Test-Path -LiteralPath $Path)) {
    return New-PreferenceObject
  }

  $raw = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
  if ([string]::IsNullOrWhiteSpace($raw)) {
    return New-PreferenceObject
  }

  try {
    $parsed = $raw | ConvertFrom-Json
    if ($parsed -is [psobject]) {
      return $parsed
    }
  } catch {}

  return New-PreferenceObject
}

function Read-Language {
  $sourcePath = $PreferencesPath
  if (!(Test-Path -LiteralPath $sourcePath) -and
      (Test-Path -LiteralPath $LegacyPreferencesPath)) {
    $sourcePath = $LegacyPreferencesPath
  }
  $prefs = Read-Preferences $sourcePath
  $property = $prefs.PSObject.Properties[$LanguageKey]
  if ($null -eq $property) {
    return ''
  }
  return Normalize-Language ([string] $property.Value)
}

function Write-Language([string] $Value) {
  $language = Normalize-Language $Value
  $directory = Split-Path -Parent $PreferencesPath
  if (!(Test-Path -LiteralPath $directory)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
  }

  $prefs = Read-Preferences
  $property = $prefs.PSObject.Properties[$LanguageKey]
  if ($null -eq $property) {
    Add-Member `
      -InputObject $prefs `
      -NotePropertyName $LanguageKey `
      -NotePropertyValue $language
  } else {
    $property.Value = $language
  }

  $json = $prefs | ConvertTo-Json -Compress -Depth 10
  $encoding = New-Object System.Text.UTF8Encoding -ArgumentList $false
  [System.IO.File]::WriteAllText($PreferencesPath, $json, $encoding)
}

if ($Mode -eq 'Read') {
  [Console]::Out.Write((Read-Language))
  exit 0
}

Write-Language $Language
