$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$jar = Join-Path $PSScriptRoot 'target\trackmyboardgames-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw 'Build the executable JAR with .\mvnw.cmd clean package before packaging.'
}

$destination = Join-Path $PSScriptRoot 'target\trackmyboardgames-beanstalk.zip'
$stream = [System.IO.File]::Open($destination, [System.IO.FileMode]::Create)
try {
    $archive = New-Object System.IO.Compression.ZipArchive(
        $stream, [System.IO.Compression.ZipArchiveMode]::Create, $true
    )
    try {
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $archive, $jar, 'application.jar'
        ) | Out-Null
        $files = @((Get-Item -LiteralPath (Join-Path $PSScriptRoot 'Procfile')))
        foreach ($directory in @('.platform', '.ebextensions')) {
            $files += Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot $directory) -File -Recurse -Force
        }
        foreach ($file in $files) {
            $relative = $file.FullName.Substring($PSScriptRoot.Length + 1).Replace('\', '/')
            # Beanstalk normalizes hook files, but not the helper scripts they call.
            $text = [System.IO.File]::ReadAllText($file.FullName).Replace("`r`n", "`n").Replace("`r", "`n")
            $entry = $archive.CreateEntry($relative)
            $entryStream = $entry.Open()
            try {
                $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($text)
                $entryStream.Write($bytes, 0, $bytes.Length)
            }
            finally {
                $entryStream.Dispose()
            }
        }
    }
    finally {
        $archive.Dispose()
    }
}
finally {
    $stream.Dispose()
}
Write-Output "Beanstalk bundle: $destination"
