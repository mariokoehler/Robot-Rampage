# Packs a set of same-shape square PNGs (any sizes, with alpha) into a single multi-resolution
# Windows .ico, using classic uncompressed 32bpp DIB entries (not PNG-compressed ICONDIR
# entries) for every size - this is deliberate, not a simplification: a PNG-compressed entry at
# 64/128px round-tripped fine through Windows Explorer but threw "requested range exceeds array
# end" out of .NET's own System.Drawing.Icon(path, w, h) constructor when this was first tried
# (assets-raw/icon.ico, 2026-09-23) - only 16/32/48 worked as PNG, not 64/128/256. Switching every
# size to plain DIB (the format Windows has supported natively since 3.1) fixed all six sizes at
# once and needs no external tool (no ImageMagick/similar assumed installed).
#
# Usage (PowerShell, comma-separated PNG paths - NOT a PowerShell array: this script is meant to
# be invoked as its own process, e.g. from Bash via `powershell -File build-ico.ps1 ...`, and a
# real PowerShell array does not survive that process boundary - it gets silently mis-bound):
#   powershell -File build-ico.ps1 -PngPaths "icon-16.png,icon-32.png,icon-48.png,icon-256.png" -OutPath icon.ico
#
# Every input PNG must already be square (width == height) - this only reads the width to label
# each ICONDIRENTRY, it does not resize anything.
param(
    [Parameter(Mandatory = $true)][string]$PngPaths,
    [Parameter(Mandatory = $true)][string]$OutPath
)

Add-Type -AssemblyName System.Drawing

function Get-DibBytes {
    param([System.Drawing.Bitmap]$bmp)
    $w = $bmp.Width
    $h = $bmp.Height
    $ms = New-Object System.IO.MemoryStream
    $bw = New-Object System.IO.BinaryWriter($ms)
    # BITMAPINFOHEADER - biHeight is XOR-mask height + AND-mask height, per the ICO format.
    $bw.Write([UInt32]40)
    $bw.Write([Int32]$w)
    $bw.Write([Int32]($h * 2))
    $bw.Write([UInt16]1)
    $bw.Write([UInt16]32)
    $bw.Write([UInt32]0)
    $bw.Write([UInt32]($w * $h * 4))
    $bw.Write([Int32]0)
    $bw.Write([Int32]0)
    $bw.Write([UInt32]0)
    $bw.Write([UInt32]0)
    # XOR mask: bottom-up rows, BGRA per pixel (real alpha lives here).
    for ($y = $h - 1; $y -ge 0; $y--) {
        for ($x = 0; $x -lt $w; $x++) {
            $px = $bmp.GetPixel($x, $y)
            $bw.Write([byte]$px.B)
            $bw.Write([byte]$px.G)
            $bw.Write([byte]$px.R)
            $bw.Write([byte]$px.A)
        }
    }
    # AND mask: 1bpp, bottom-up, rows padded to a 4-byte boundary - left all-zero since the XOR
    # mask's own alpha channel already carries real transparency (the modern, universally
    # supported convention for a 32bpp icon).
    $rowBytes = [Math]::Ceiling($w / 32.0) * 4
    $andRow = New-Object byte[] $rowBytes
    for ($y = 0; $y -lt $h; $y++) {
        $bw.Write($andRow)
    }
    $bw.Flush()
    return , $ms.ToArray()
}

$paths = $PngPaths -split ','
$entries = @()
foreach ($p in $paths) {
    $bmp = [System.Drawing.Bitmap]::FromFile($p.Trim())
    if ($bmp.Width -ne $bmp.Height) {
        throw "Not square: $p ($($bmp.Width)x$($bmp.Height))"
    }
    $dib = Get-DibBytes -bmp $bmp
    $entries += [PSCustomObject]@{ Size = $bmp.Width; Bytes = $dib }
    $bmp.Dispose()
}
$entries = $entries | Sort-Object Size

$stream = New-Object System.IO.MemoryStream
$writer = New-Object System.IO.BinaryWriter($stream)
$writer.Write([UInt16]0)   # reserved
$writer.Write([UInt16]1)   # type = icon
$writer.Write([UInt16]$entries.Count)
$offset = 6 + 16 * $entries.Count
foreach ($e in $entries) {
    $sideByte = if ($e.Size -ge 256) { 0 } else { [byte]$e.Size }  # 0 means 256, per the ICO format
    $writer.Write([byte]$sideByte)
    $writer.Write([byte]$sideByte)
    $writer.Write([byte]0)     # palette (0 = true color)
    $writer.Write([byte]0)     # reserved
    $writer.Write([UInt16]1)   # color planes
    $writer.Write([UInt16]32)  # bits per pixel
    $writer.Write([UInt32]$e.Bytes.Length)
    $writer.Write([UInt32]$offset)
    $offset += $e.Bytes.Length
}
foreach ($e in $entries) {
    $writer.Write($e.Bytes)
}
$writer.Flush()
[System.IO.File]::WriteAllBytes($OutPath, $stream.ToArray())
Write-Output ("Wrote {0}: {1} sizes ({2})" -f $OutPath, $entries.Count, (($entries | ForEach-Object { $_.Size }) -join ', '))
