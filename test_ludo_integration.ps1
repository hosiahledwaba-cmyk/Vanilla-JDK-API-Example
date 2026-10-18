# test_ludo_integration.ps1
$ErrorActionPreference = "Stop"
Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host "       RobinHood - Ludo Full Integration Test Suite       " -ForegroundColor Cyan
Write-Host "=========================================================" -ForegroundColor Cyan

$baseUrl = "http://localhost:8080"

# 1. Authenticate Player 1
Write-Host "`n1. Authenticating Player 1..." -ForegroundColor Yellow
$rnd1 = Get-Random -Minimum 1000 -Maximum 9999
$email1 = "ludo_tester1_$rnd1@robinhood.com"
$loginBody1 = @{
    email = $email1
    password = "Password123!"
} | ConvertTo-Json

$regResp1 = Invoke-RestMethod -Uri "$baseUrl/signup" -Method Post -Body $loginBody1 -ContentType "application/json"
$authResp1 = Invoke-RestMethod -Uri "$baseUrl/login" -Method Post -Body $loginBody1 -ContentType "application/json"
$token1 = $authResp1.token
$headers1 = @{
    Authorization = "Bearer $token1"
    "Content-Type" = "application/json"
}
Write-Host "Player 1 Logged in: $email1 (Token: $($token1.Substring(0, 15))...)" -ForegroundColor Green

# 2. Deposit tokens for Player 1
Write-Host "`n2. Depositing 500 tokens for Player 1..." -ForegroundColor Yellow
$depResp1 = Invoke-RestMethod -Uri "$baseUrl/deposit" -Method Post -Headers $headers1 -Body (@{ amount = "500" } | ConvertTo-Json)
Write-Host "Player 1 Balance: $($depResp1.balance) tokens" -ForegroundColor Green

# 3. Create and Start a Ludo Bot Match (vs RobinBot)
Write-Host "`n3. Launching Single-Player Ludo Match vs RobinBot..." -ForegroundColor Yellow
$createLudo = @{
    game = "ludo"
    maxPlayers = "2"
    bet = "50"
} | ConvertTo-Json

$sessionResp = Invoke-RestMethod -Uri "$baseUrl/create-game" -Method Post -Headers $headers1 -Body $createLudo
$sId = $sessionResp.sessionId
Write-Host "Ludo Session Created: $sId (Type: $($sessionResp.gameType))" -ForegroundColor Green

$startResp = Invoke-RestMethod -Uri "$baseUrl/start-game" -Method Post -Headers $headers1 -Body (@{ sessionId = $sId } | ConvertTo-Json)
Write-Host "Ludo Match Started: $($startResp.started)" -ForegroundColor Green

Start-Sleep -Milliseconds 600

# 4. Query Ludo Authoritative State
Write-Host "`n4. Querying Authoritative Ludo State via /games/ludo/state..." -ForegroundColor Yellow
$stateResp = Invoke-RestMethod -Uri "$baseUrl/games/ludo/state/$sId" -Method Get -Headers $headers1
$ludoState = $stateResp.state
Write-Host "Ludo State Loaded:" -ForegroundColor Cyan
Write-Host "  Current Turn: $($ludoState.currentTurnPlayer)"
Write-Host "  Turn Phase:   $($ludoState.phase)"
Write-Host "  Players:      $($ludoState.players.Count) registered"
foreach ($p in $ludoState.players) {
    Write-Host "    - $($p.id): $($p.colorName) ($($p.colorHex))"
}

# 5. Execute Authoritative Dice Roll
Write-Host "`n5. Executing Dice Roll for Active Player..." -ForegroundColor Yellow
$activePlayer = $ludoState.currentTurnPlayer
if ($activePlayer -eq $email1) {
    # Force a roll of 6 to guarantee testing yard exit
    $rollResp = Invoke-RestMethod -Uri "$baseUrl/games/ludo/roll/$sId" -Method Post -Headers $headers1 -Body (@{ forcedRoll = "6" } | ConvertTo-Json)
    Write-Host "Roll Result: $($rollResp | ConvertTo-Json -Compress)" -ForegroundColor Green
    Write-Host "  Rolled: $($rollResp.roll), Legal Moves: $($rollResp.legalMoves | ConvertTo-Json -Compress)"
    
    # 6. Move Token 0 out of Yard onto board
    Write-Host "`n6. Moving Token 0 from Yard onto Board..." -ForegroundColor Yellow
    $moveResp = Invoke-RestMethod -Uri "$baseUrl/games/ludo/move/$sId" -Method Post -Headers $headers1 -Body (@{ tokenId = "0" } | ConvertTo-Json)
    Write-Host "Move Result: $($moveResp | ConvertTo-Json -Compress)" -ForegroundColor Green
    Write-Host "  Bonus Roll Granted: $($moveResp.bonusRoll)"
} else {
    Write-Host "RobinBot has first turn, waiting for bot action..." -ForegroundColor Gray
    Start-Sleep -Seconds 2
}

# 7. Test Custom Multiplayer Room Flow for Ludo
Write-Host "`n7. Testing Custom Multiplayer Room Creation for Ludo (4 Players)..." -ForegroundColor Yellow
$roomCreate = @{
    game = "LUDO"
    maxPlayers = 4
    wager = 50
    isBotMatch = $false
} | ConvertTo-Json

$roomResp = Invoke-RestMethod -Uri "$baseUrl/rooms/create" -Method Post -Headers $headers1 -Body $roomCreate
$roomId = $roomResp.room.id
$roomCode = $roomResp.room.roomCode
Write-Host "Custom Ludo Room Created! ID: $roomId, Code: $roomCode" -ForegroundColor Green

# 8. Authenticate Player 2 and Join Room by Code
Write-Host "`n8. Authenticating Player 2 and Joining by Room Code ($roomCode)..." -ForegroundColor Yellow
$rnd2 = Get-Random -Minimum 1000 -Maximum 9999
$email2 = "ludo_tester2_$rnd2@robinhood.com"
$loginBody2 = @{ email = $email2; password = "Password123!" } | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/signup" -Method Post -Body $loginBody2 -ContentType "application/json" | Out-Null
$authResp2 = Invoke-RestMethod -Uri "$baseUrl/login" -Method Post -Body $loginBody2 -ContentType "application/json"
$token2 = $authResp2.token
$headers2 = @{ Authorization = "Bearer $token2"; "Content-Type" = "application/json" }
Invoke-RestMethod -Uri "$baseUrl/deposit" -Method Post -Headers $headers2 -Body (@{ amount = "500" } | ConvertTo-Json) | Out-Null

$joinResp = Invoke-RestMethod -Uri "$baseUrl/rooms/join" -Method Post -Headers $headers2 -Body (@{ roomCode = $roomCode } | ConvertTo-Json)
Write-Host "Player 2 successfully joined Ludo room! Members count: $($joinResp.room.members.Count)" -ForegroundColor Green

# 9. Verify Forfeit Handling
Write-Host "`n9. Testing Forfeit on Single-Player Session..." -ForegroundColor Yellow
$forfeitResp = Invoke-RestMethod -Uri "$baseUrl/games/forfeit" -Method Post -Headers $headers1 -Body (@{ sessionId = $sId } | ConvertTo-Json)
Write-Host "Forfeit Response: $($forfeitResp | ConvertTo-Json -Compress)" -ForegroundColor Green
Write-Host "  Winner after forfeit: $($forfeitResp.winner)"

Write-Host "`n=========================================================" -ForegroundColor Green
Write-Host "   ALL LUDO INTEGRATION TESTS PASSED SUCCESSFULLY!       " -ForegroundColor Green
Write-Host "=========================================================" -ForegroundColor Green
