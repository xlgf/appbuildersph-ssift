# Generates ~80 fake phone screenshots for the Screenshot Butler demo.
# All names, numbers and codes are fake. Card 4111 1111 1111 1111 is the standard public test number.
# Usage:  powershell -ExecutionPolicy Bypass -File tools\make-demo-screenshots.ps1
param([string]$Out = (Join-Path $PSScriptRoot '..\demo-screenshots'))

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

New-Item -ItemType Directory -Force -Path $Out | Out-Null
Get-ChildItem $Out -Filter *.png | Remove-Item -Force

$W = 1080; $H = 2340
$rand = New-Object System.Random 42
$script:clock = [datetime]'2026-10-08 21:30'
$script:count = 0

# ---------- drawing helpers ----------

function C([int]$r, [int]$g, [int]$b) { [System.Drawing.Color]::FromArgb($r, $g, $b) }
function NewBrush($c) { New-Object System.Drawing.SolidBrush $c }
function NewFont([float]$size, [bool]$bold = $false, [string]$family = 'Segoe UI') {
    $style = [System.Drawing.FontStyle]::Regular
    if ($bold) { $style = [System.Drawing.FontStyle]::Bold }
    New-Object System.Drawing.Font($family, $size, $style, [System.Drawing.GraphicsUnit]::Pixel)
}

$Ink = C 22 22 26
$Grey = C 128 128 136
$Blue = C 0 122 255

# Draws wrapped text; returns the height used.
function Text($g, [string]$s, [float]$x, [float]$y, [float]$size, $color, [bool]$bold = $false,
              [float]$maxW = 0, [string]$family = 'Segoe UI', [string]$align = 'Near') {
    if ($maxW -le 0) { $maxW = $W - $x - 48 }
    $f = NewFont $size $bold $family
    $b = NewBrush $color
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = [System.Drawing.StringAlignment]::$align
    $sz = $g.MeasureString($s, $f, [int]$maxW, $fmt)
    $g.DrawString($s, $f, $b, (New-Object System.Drawing.RectangleF($x, $y, $maxW, 2000)), $fmt)
    $f.Dispose(); $b.Dispose()
    return $sz.Height
}

function RoundRect($g, $color, [float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = [math]::Min($r * 2, [math]::Min($w, $h))
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    $b = NewBrush $color
    $g.FillPath($b, $p)
    $b.Dispose(); $p.Dispose()
}

function Rect($g, $color, [float]$x, [float]$y, [float]$w, [float]$h) {
    $b = NewBrush $color; $g.FillRectangle($b, $x, $y, $w, $h); $b.Dispose()
}

function NewShot($bg) {
    $bmp = New-Object System.Drawing.Bitmap $W, $H
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.Clear($bg)
    $fg = $Ink
    if ($bg.GetBrightness() -lt 0.5) { $fg = C 255 255 255 }
    $null = Text $g '9:41' 56 36 38 $fg $true
    $null = Text $g '5G  87%' 820 36 36 $fg $true 210 'Segoe UI' 'Far'
    return @{ Bmp = $bmp; G = $g }
}

function Save($shot, [string]$app) {
    $script:clock = $script:clock.AddMinutes(-1 * $rand.Next(45, 1100))
    $name = 'Screenshot_{0}_{1}.png' -f $script:clock.ToString('yyyyMMdd-HHmmss'), $app
    $path = Join-Path $Out $name
    $shot.Bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    (Get-Item $path).LastWriteTime = $script:clock
    $shot.G.Dispose(); $shot.Bmp.Dispose()
    $script:count++
}

# ---------- templates ----------

# $msgs: array of @('me'|'them', 'text', 'time')
function Chat([string]$contact, [object[]]$msgs, [string]$app = 'Messages') {
    $s = NewShot (C 255 255 255); $g = $s.G
    Rect $g (C 247 247 247) 0 110 $W 190
    $null = Text $g '<' 44 150 64 $Blue
    RoundRect $g (C 190 196 206) (($W / 2) - 44) 128 88 88 44
    $null = Text $g $contact.Substring(0, 1) 0 146 44 (C 255 255 255) $true $W 'Segoe UI' 'Center'
    $null = Text $g $contact 0 226 34 $Ink $true $W 'Segoe UI' 'Center'

    $y = 360
    foreach ($m in $msgs) {
        $mine = $m[0] -eq 'me'
        $f = NewFont 42
        $size = $s.G.MeasureString($m[1], $f, 700)
        $f.Dispose()
        $bw = [math]::Max($size.Width, 80) + 64
        $bh = $size.Height + 44
        $x = 48
        $bubble = C 233 233 235; $tc = $Ink
        if ($mine) { $x = $W - 48 - $bw; $bubble = $Blue; $tc = C 255 255 255 }
        RoundRect $g $bubble $x $y $bw $bh 42
        $null = Text $g $m[1] ($x + 32) ($y + 22) 42 $tc $false 700
        $y += $bh + 10
        if ($m[2]) {
            $align = 'Near'; if ($mine) { $align = 'Far' }
            $null = Text $g $m[2] 60 $y 28 $Grey $false ($W - 120) 'Segoe UI' $align
            $y += 44
        }
        $y += 22
    }
    RoundRect $g (C 242 242 244) 48 ($H - 190) ($W - 96) 100 50
    $null = Text $g 'Message' 96 ($H - 162) 38 $Grey
    Save $s $app
}

# A generic app card: coloured header, title, then label/value rows.
function Card([string]$app, $accent, [string]$title, [object[]]$rows, [string]$footer = '') {
    $s = NewShot (C 250 250 250); $g = $s.G
    Rect $g $accent 0 110 $W 200
    $null = Text $g $app 64 180 48 (C 255 255 255) $true
    $y = 380
    $y += Text $g $title 64 $y 64 $Ink $true ($W - 128)
    $y += 40
    foreach ($r in $rows) {
        if ($r.Count -eq 1) {
            $y += Text $g $r[0] 64 $y 40 $Ink $false ($W - 128)
            $y += 24
            continue
        }
        $null = Text $g $r[0] 64 $y 34 $Grey
        $y += 50
        $y += Text $g $r[1] 64 $y 44 $Ink $true ($W - 128)
        $y += 30
        Rect $g (C 228 228 232) 64 $y ($W - 128) 2
        $y += 30
    }
    if ($footer) { $null = Text $g $footer 64 ($y + 20) 32 $Grey $false ($W - 128) }
    Save $s ($app -replace '[^A-Za-z]', '')
}

function Note([string]$title, [string[]]$lines) {
    $s = NewShot (C 255 252 240); $g = $s.G
    $null = Text $g '< Notes' 48 140 40 (C 214 160 32)
    $y = 260
    $y += Text $g $title 64 $y 64 $Ink $true ($W - 128)
    $y += 40
    foreach ($l in $lines) { $y += Text $g $l 64 $y 44 $Ink $false ($W - 128); $y += 18 }
    Save $s 'Notes'
}

function Meme([string]$top, [string]$bottom, $bg) {
    $s = NewShot (C 0 0 0); $g = $s.G
    Rect $g $bg 60 500 ($W - 120) 1100
    # simple "photo": a sun and two hills, so it reads as an image, not a block
    $sun = NewBrush (C 255 230 140); $g.FillEllipse($sun, 640, 600, 240, 240); $sun.Dispose()
    $hill = NewBrush ([System.Drawing.Color]::FromArgb(90, 0, 0, 0))
    $g.FillEllipse($hill, -100, 1150, 800, 700); $g.FillEllipse($hill, 400, 1250, 900, 700); $hill.Dispose()
    Rect $g (C 0 0 0) 0 1600 $W 60
    $null = Text $g $top 60 300 76 (C 255 255 255) $true ($W - 120) 'Impact' 'Center'
    $null = Text $g $bottom 60 1660 76 (C 255 255 255) $true ($W - 120) 'Impact' 'Center'
    $null = Text $g 'Liked by friends and others' 60 2050 32 (C 200 200 200)
    Save $s 'Social'
}

function Article([string]$site, [string]$title, [string[]]$paras) {
    $s = NewShot (C 255 255 255); $g = $s.G
    $null = Text $g $site 64 140 36 $Grey $true
    Rect $g (C 230 230 230) 0 210 $W 2
    $y = 260
    $y += Text $g $title 64 $y 60 $Ink $true ($W - 128)
    $y += 40
    foreach ($p in $paras) { $y += Text $g $p 64 $y 40 (C 60 60 66) $false ($W - 128); $y += 36 }
    Save $s 'Browser'
}

function Qr($g, [float]$x, [float]$y, [float]$size) {
    $n = 25; $m = $size / $n
    Rect $g (C 255 255 255) ($x - 20) ($y - 20) ($size + 40) ($size + 40)
    $black = C 0 0 0
    for ($i = 0; $i -lt $n; $i++) { for ($j = 0; $j -lt $n; $j++) {
        if ($rand.Next(2) -eq 1) { Rect $g $black ($x + $i * $m) ($y + $j * $m) $m $m }
    } }
    foreach ($p in @(@(0, 0), @(18, 0), @(0, 18))) {
        Rect $g $black ($x + $p[0] * $m) ($y + $p[1] * $m) (7 * $m) (7 * $m)
        Rect $g (C 255 255 255) ($x + ($p[0] + 1) * $m) ($y + ($p[1] + 1) * $m) (5 * $m) (5 * $m)
        Rect $g $black ($x + ($p[0] + 2) * $m) ($y + ($p[1] + 2) * $m) (3 * $m) (3 * $m)
    }
}

function Ticket {
    $s = NewShot (C 18 18 32); $g = $s.G
    $null = Text $g 'Your tickets' 64 150 40 (C 200 200 220) $true
    RoundRect $g (C 245 241 230) 64 260 ($W - 128) 1800 48
    $y = 330
    $y += Text $g 'PHOENIX LIVE' 120 $y 84 $Ink $true
    $y += Text $g 'World Tour 2026' 120 $y 44 $Grey
    $y += 40
    foreach ($row in @(@('DATE', 'Fri, Oct 24, 2026'), @('DOORS', '7:30 PM'), @('VENUE', 'Riverside Arena'), @('ENTRANCE', 'Gate 14 - Row F - Seat 22'))) {
        $null = Text $g $row[0] 120 $y 32 $Grey $true
        $y += 46
        $y += Text $g $row[1] 120 $y 48 $Ink $true
        $y += 26
    }
    Qr $g 290 ($y + 40) 500
    $null = Text $g 'ADMIT ONE   -   Order #A-77341' 64 ($y + 620) 36 $Ink $true ($W - 128) 'Segoe UI' 'Center'
    Save $s 'Tickets'
}

function Bank {
    $s = NewShot (C 255 255 255); $g = $s.G
    $null = Text $g 'Northway Bank' 64 150 40 (C 20 110 70) $true
    RoundRect $g (C 20 150 90) (($W / 2) - 90) 300 180 180 90
    $null = Text $g 'OK' 0 350 64 (C 255 255 255) $true $W 'Segoe UI' 'Center'
    $null = Text $g 'Transfer sent' 0 540 60 $Ink $true $W 'Segoe UI' 'Center'
    $null = Text $g '$85.00' 0 640 120 $Ink $true $W 'Segoe UI' 'Center'
    $null = Text $g 'to Alex Morgan' 0 800 48 $Grey $false $W 'Segoe UI' 'Center'
    $y = 960
    foreach ($row in @(@('From', 'Everyday Checking'), @('Reference', 'Concert tickets'), @('Date', 'Oct 6, 2026 at 8:14 PM'), @('Transaction ID', 'TX-55812-NW'))) {
        $null = Text $g $row[0] 64 $y 38 $Grey
        $null = Text $g $row[1] 64 $y 38 $Ink $true ($W - 128) 'Segoe UI' 'Far'
        $y += 90
        Rect $g (C 230 230 230) 64 ($y - 24) ($W - 128) 2
    }
    Save $s 'Bank'
}

# Photo screenshots use real CC0 photos from tools/photos (see SOURCES.md). They carry almost no
# text on purpose: under 40 letters, the app runs on-device image labeling ("dog", "food", "beach").
$Photos = Join-Path $PSScriptRoot 'photos'

# Phone gallery: black background, the photo fit to width, no text besides the status bar.
function PhotoGallery([string]$file) {
    $s = NewShot (C 0 0 0); $g = $s.G
    $img = [System.Drawing.Image]::FromFile((Join-Path $Photos $file))
    $photoH = $W * $img.Height / $img.Width   # not $h: PowerShell names are case-insensitive and $H is the screen height
    $g.DrawImage($img, 0, [float](($H - $photoH) / 2), [float]$W, [float]$photoH)
    $img.Dispose()
    $null = Text $g '<' 44 140 64 (C 255 255 255)
    $white = NewBrush (C 230 230 230)
    foreach ($x in @(180, 420, 660, 900)) { $g.FillEllipse($white, $x - 22, $H - 170, 44, 44) }
    $white.Dispose()
    Save $s 'Gallery'
}

# Social post: avatar + handle, square-cropped photo, a like count. About 15 letters in total.
function PhotoPost([string]$file, [string]$handle) {
    $s = NewShot (C 255 255 255); $g = $s.G
    RoundRect $g (C 214 160 32) 40 150 90 90 45
    $null = Text $g $handle 150 168 40 $Ink $true
    $img = [System.Drawing.Image]::FromFile((Join-Path $Photos $file))
    $side = [math]::Min($img.Width, $img.Height)
    $src = New-Object System.Drawing.Rectangle ([int](($img.Width - $side) / 2)), ([int](($img.Height - $side) / 2)), $side, $side
    $dst = New-Object System.Drawing.Rectangle 0, 280, $W, $W
    $g.DrawImage($img, $dst, $src, [System.Drawing.GraphicsUnit]::Pixel)
    $img.Dispose()
    $pen = New-Object System.Drawing.Pen $Ink, 6
    foreach ($x in @(48, 148, 248)) { $g.DrawEllipse($pen, $x, 1400, 64, 64) }
    $pen.Dispose()
    $null = Text $g ('{0} likes' -f $rand.Next(40, 900)) 48 1500 40 $Ink $true
    Save $s 'Social'
}

# ---------- 1. key demo screenshots (most recent) ----------

Chat 'Mom' @(
    @('them', 'Hi sweetie! For when you visit this weekend', '6:02 PM'),
    @('them', 'The wifi network is Peggy_Guest_5G and the pw is Sunset2024', '6:03 PM'),
    @('me', 'Thanks mom! See you Saturday', '6:10 PM'),
    @('them', 'Drive safe, love you', '6:11 PM')
)
Ticket
Bank
Chat 'Sam' @(
    @('them', 'Party is at 221 Market Street, apt 4B', '4:15 PM'),
    @('them', 'Saturday 8 PM, bring snacks!', '4:15 PM'),
    @('me', 'I will bring pizza', '4:20 PM'),
    @('them', 'Legend', '4:21 PM')
)
Card 'Tony''s Pizza' (C 190 40 40) 'Order receipt' @(
    @('Margherita pizza (large)', '$14.50'),
    @('Garlic knots', '$6.20'),
    @('Delivery + tip', '$10.50'),
    @('Total paid', '$31.20'),
    @('Payment', 'Visa ending 4242')
) 'Thanks for ordering! Order #5521'
Card 'SkyLink Air' (C 0 80 160) 'Amsterdam to Lisbon' @(
    @('Flight', 'KL 1023'),
    @('Departs', 'Nov 3, 2026, 09:40'),
    @('Booking code', 'X7K2PQ'),
    @('Gate / Seat', 'D7 / 14A')
) 'Boarding closes 20 minutes before departure.'
Card 'Recipes' (C 200 120 40) 'Grandma''s banana bread' @(
    @('Ingredients'),
    @('3 ripe bananas, 2 cups flour, 1 tsp baking soda, 1/2 cup sugar, 1 egg, 1/3 cup melted butter'),
    @('Method'),
    @('Preheat the oven to 350F. Mash bananas, mix in butter, sugar and egg. Fold in flour and baking soda. Bake 60 minutes.')
)
Chat 'NorthwayBank' @(
    @('them', 'Northway: your verification code is 482911. Never share it with anyone.', '11:02 AM'),
    @('them', 'Northway: a new device signed in at 11:03 AM', '11:03 AM'),
    @('them', 'Northway: your statement is ready', '11:30 AM')
)
Card 'City Dental' (C 60 140 160) 'Appointment reminder' @(
    @('Patient', 'Maya Chen'),
    @('When', 'Thu, Oct 30, 2026, 3:15 PM'),
    @('With', 'Dr. Kim, cleaning'),
    @('Address', '48 Oak Avenue, Suite 2')
) 'Reply C to confirm.'

# ---------- 1a. everyday keepers ----------
# What decluttering guides say people actually screenshot and keep: recipes, receipts, reminders,
# inspiration, and "random things that felt important at the time". All stores and names are fake.

# Reminders
Card 'Parking' (C 40 90 150) 'Your car is parked' @(
    @('Garage', 'Westfield Central, Level 3'),
    @('Spot', 'C14'),
    @('Paid until', '6:30 PM')
) 'Take the blue elevators to get back to Level 3.'
Card 'Library' (C 120 70 140) 'Items due soon' @(
    @('Title', 'The Overstory'),
    @('Due date', 'Oct 21, 2026'),
    @('Branch', 'Eastside Library')
) 'Renew online or at any branch.'
Note 'Dry cleaning' @('Ticket #4471', 'Pick up Thursday after 4pm', '2 shirts, 1 coat')
Card 'Pharmacy' (C 30 140 120) 'Prescription ready' @(
    @('Pickup code', 'RX 5520'),
    @('Store', 'CareWell Pharmacy, Main St'),
    @('Ready until', 'Oct 15')
)

# Receipts and orders
Card 'Northline' (C 30 30 30) 'Order confirmed' @(
    @('Item', 'Linen blazer, sand, size M'),
    @('Total', '$89.00'),
    @('Order number', 'NL-208841'),
    @('Arrives', 'Oct 14-16')
) 'Returns are free within 30 days.'
Card 'Parcel Tracking' (C 210 120 30) 'Out for delivery' @(
    @('Tracking number', '1Z 84F 93R 0412 6650'),
    @('From', 'Northline'),
    @('Expected', 'Today by 8 PM')
)
Card 'Bean There Cafe' (C 110 70 40) 'Receipt' @(
    @('Oat latte', '$5.20'),
    @('Almond croissant', '$4.10'),
    @('Total paid', '$9.30')
) 'Thank you! Show this receipt for 10% off next time.'

# Recipes
Card 'Recipes' (C 90 140 60) '15-minute pesto pasta' @(
    @('Ingredients'),
    @('200 g pasta, 3 tbsp pesto, 1 cup cherry tomatoes, parmesan, olive oil'),
    @('Method'),
    @('Boil pasta. Toss with pesto and halved tomatoes. Top with parmesan.')
)

# Inspiration
Card 'Shop' (C 200 90 110) 'Ceramic table lamp' @(
    @('Price', '$64.00'),
    @('Store', 'Homeward'),
    @('Color', 'Warm white')
) 'Saved to your wishlist.'
Note 'Workout plan' @('Mon: 30 min run', 'Tue: upper body', 'Thu: yoga', 'Sat: long walk 10k')
Meme 'DO SMALL THINGS' 'WITH GREAT LOVE' (C 200 160 120)

# Random things that felt important
Card 'Gift Card' (C 70 70 160) 'You received a gift card' @(
    @('Amount', '$50.00'),
    @('Store', 'Homeward'),
    @('Expires', 'Dec 31, 2027')
) 'Redeem in store or online.'

# ---------- 1b. photo screenshots (exercise image labeling) ----------

PhotoGallery 'dog.jpg'
PhotoPost 'pizza.jpg' 'sam.eats'
PhotoGallery 'beach.jpg'
PhotoPost 'cat.jpg' 'nora'
PhotoGallery 'lake.jpg'

# ---------- 2. filler, part 1 ----------

$friends = 'Jordan', 'Priya', 'Leo', 'Aisha', 'Ben', 'Chloe', 'Diego', 'Emma', 'Kenji', 'Nora'
$lines = @(
    'Did you see the game last night?', 'Running 10 min late sorry', 'lol that is so true',
    'Can you send me the slides?', 'Happy birthday!!', 'We should get coffee this week',
    'Pizza friday?', 'Just landed', 'Who is driving tomorrow?', 'That movie was way too long',
    'On my way', 'Did you feed the cat', 'Ok sounds good', 'Haha no way', 'Call me when free',
    'The meeting moved to Thursday', 'Can you pick up milk?', 'Brunch on Sunday?', 'Good luck today!',
    'I found your charger', 'Send pics!', 'Traffic is terrible', 'Let me know when you get home'
)
function RandomChat {
    $msgs = @()
    $hour = $rand.Next(8, 12); $min = $rand.Next(10, 50)
    for ($i = 0; $i -lt $rand.Next(4, 7); $i++) {
        $who = 'them'; if ($rand.Next(2) -eq 1) { $who = 'me' }
        $msgs += , @($who, $lines[$rand.Next($lines.Count)], ('{0}:{1:00} AM' -f $hour, ($min + $i)))
    }
    Chat $friends[$rand.Next($friends.Count)] $msgs
}

$memes = @(
    @('ME: I WILL SLEEP EARLY', 'ALSO ME AT 3 AM'),
    @('WHEN THE CODE WORKS', 'ON THE FIRST TRY'),
    @('MONDAY MORNING', 'LOADING...'),
    @('NOBODY:', 'MY CAT AT 4 AM'),
    @('WHEN YOU SAY ONE MORE EPISODE', 'AND THE SUN COMES UP'),
    @('ME OPENING THE FRIDGE', 'FOR THE 10TH TIME'),
    @('GROUP PROJECT', 'ME DOING EVERYTHING'),
    @('WHEN THE WIFI DROPS', 'FOR ONE SECOND'),
    @('ME AFTER ONE GYM SESSION', 'WHERE ARE MY ABS'),
    @('WEEKEND PLANS', 'STAY IN BED')
)
$memeColors = @((C 230 120 80), (C 90 140 200), (C 120 180 120), (C 200 160 60), (C 150 110 190))

for ($i = 0; $i -lt 15; $i++) { RandomChat }
foreach ($m in $memes) { Meme $m[0] $m[1] $memeColors[$rand.Next($memeColors.Count)] }

# ---------- 3. the planted sensitive screenshots ----------

Note 'Card for online orders' @('4111 1111 1111 1111', 'Exp 09/28', 'CVV 123', 'Use for groceries only')
Note 'Streaming login' @('email: maya.demo@example.com', 'password: Tr0ub4dor&3', 'shared with the family plan')
Card 'Account Security' (C 70 70 90) 'Save your backup codes' @(
    @('Each code can be used once if you lose your phone.'),
    @('4829 1173'), @('5521 0098'), @('7730 4412'), @('1094 6638'), @('3317 9025'), @('8846 2201')
) 'Keep these backup codes somewhere safe.'
Note 'Travel documents' @('Passport number: X12345678', 'Expires: 2031', 'Renewal reminder in March')

# ---------- 4. filler, part 2 ----------

for ($i = 0; $i -lt 17; $i++) { RandomChat }

Note 'Groceries' @('eggs', 'oat milk', 'spinach', 'pizza dough', 'coffee beans', 'bananas')
Note 'To do' @('Book dentist', 'Return library books', 'Call landlord about the heater', 'Pay phone bill')
Note 'Gift ideas' @('Mom: scarf or plants', 'Sam: vinyl record', 'Jordan: climbing gym pass')
Note 'Book list' @('Project Hail Mary', 'Tomorrow, and Tomorrow, and Tomorrow', 'The Overstory')
Note 'Workout' @('Mon: run 5k', 'Wed: legs', 'Fri: swim', 'Sun: rest')
Note 'Packing' @('chargers', 'passport case', 'sunscreen', 'adapter plug', 'headphones')

Article 'citynews.example' 'New bike lanes open downtown next month' @(
    'The city council approved three new protected bike lanes connecting the river path to the central station.',
    'Construction is expected to finish before winter, officials said.'
)
Article 'techdaily.example' 'Phones are getting better at running AI without the cloud' @(
    'New chips make it possible to run small language models directly on a phone.',
    'Researchers say on-device models improve privacy and work without a connection.'
)
Article 'weather.example' 'Weekend forecast: sunny with a chance of rain on Sunday' @(
    'Saturday: 21 degrees and clear skies. Sunday: clouds move in by the afternoon.',
    'Pack an umbrella if you are heading out in the evening.'
)
Article 'foodblog.example' 'The five best ramen spots in town' @(
    'We tried twenty bowls so you do not have to. Here are our favourites.',
    'Number one has a broth that simmers for eighteen hours.'
)
Card 'Fitness' (C 230 80 60) 'Morning run' @(@('Distance', '5.2 km'), @('Time', '27 min 40 s'), @('Pace', '5:19 / km'))
Card 'Weather' (C 70 130 200) 'Tomorrow' @(@('Morning', '14 degrees, cloudy'), @('Afternoon', '19 degrees, sunny'), @('Evening', '15 degrees, light rain'))
Card 'Maps' (C 60 160 90) 'Riverside Arena' @(@('Drive', '24 min via Ring Road'), @('Transit', '38 min, line 4'), @('Parking', 'Lot B, open until midnight'))

Write-Host "Created $($script:count) screenshots in $((Resolve-Path $Out).Path)"
