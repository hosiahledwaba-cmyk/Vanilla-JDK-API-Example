# test_khasino_api.ps1
$baseUrl = "http://localhost:8080"
$testUsername = "khasino_tester_" + (Get-Random -Minimum 1000 -Maximum 9999)
$testPass = "Password123!"

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  SOUTH AFRICAN KHASINO API VALIDATION TEST  " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# 1. Signup / Register
Write-Host "`n1. Creating test user: $testUsername..."
$regBody = @{ username = $testUsername; password = $testPass } | ConvertTo-Json
$regRes = Invoke-RestMethod -Uri "$baseUrl/auth/signup" -Method Post -Body $regBody -ContentType "application/json"
Write-Host "Signup response: $($regRes | ConvertTo-Json -Compress)"

# 2. Login
Write-Host "`n2. Logging in to obtain JWT token..."
$loginRes = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $regBody -ContentType "application/json"
$token = $loginRes.token
if (-not $token) {
    Write-Host "FAILED: Unable to login/get token" -ForegroundColor Red
    exit 1
}
Write-Host "SUCCESS: Logged in, token: $($token.Substring(0, 15))..." -ForegroundColor Green

# 3. Check / Deposit tokens
$headers = @{ Authorization = "Bearer $token" }
Write-Host "`n3. Depositing 500 tokens into wallet..."
$depBody = @{ amount = "500" } | ConvertTo-Json
try {
    $depRes = Invoke-RestMethod -Uri "$baseUrl/deposit" -Method Post -Body $depBody -Headers $headers -ContentType "application/json"
    Write-Host "Wallet deposit response: $($depRes | ConvertTo-Json -Compress)" -ForegroundColor Green
} catch {
    Write-Host "Notice: $($_.Exception.Message)"
}

# 4. Create 2-player Khasino Game
Write-Host "`n4. Creating 2-Player Khasino Match..."
$createBody = @{
    game = "khasino"
    maxPlayers = "2"
    bet = "50"
    khasinoMode = "TWO_PLAYER"
    difficulty = "HARD"
} | ConvertTo-Json
$createRes = Invoke-RestMethod -Uri "$baseUrl/create-game" -Method Post -Body $createBody -Headers $headers -ContentType "application/json"
$sessionId = $createRes.sessionId
Write-Host "SUCCESS: Game created with Session ID: $sessionId, Type: $($createRes.gameType)" -ForegroundColor Green

# 5. Start Game
Write-Host "`n5. Starting Match (Auto-filling with RobinBot CPU opponent)..."
$startBody = @{ sessionId = $sessionId } | ConvertTo-Json
$startRes = Invoke-RestMethod -Uri "$baseUrl/start-game" -Method Post -Body $startBody -Headers $headers -ContentType "application/json"
Write-Host "SUCCESS: Match started: $($startRes.started)" -ForegroundColor Green

# 6. Query Session Meta
Write-Host "`n6. Querying Session Info..."
$sessionInfo = Invoke-RestMethod -Uri "$baseUrl/games/session/$sessionId" -Method Get -Headers $headers
Write-Host "Game Type: $($sessionInfo.gameType)"
Write-Host "Khasino Mode: $($sessionInfo.khasinoMode)"
Write-Host "AI Difficulty: $($sessionInfo.aiDifficulty)"
Write-Host "Current Turn: $($sessionInfo.currentTurn)"
if ($sessionInfo.gameType -ne "KHASINO") {
    Write-Host "FAILED: Expected gameType KHASINO" -ForegroundColor Red
    exit 1
}
Write-Host "SUCCESS: Session metadata verified" -ForegroundColor Green

# 7. Query Khasino State
Write-Host "`n7. Querying Khasino Game State..."
$stateRes = Invoke-RestMethod -Uri "$baseUrl/games/khasino/state?sessionId=$sessionId" -Method Get -Headers $headers
$state = $stateRes.state
Write-Host "Mode: $($state.mode)"
Write-Host "Deal Number: $($state.dealNumber)"
Write-Host "My Hand Size: $($state.hand.Count)"
Write-Host "Opponent Counts: $($state.opponentCounts | ConvertTo-Json -Compress)"
Write-Host "Floor Cards Count: $($state.floorCards.Count)"
Write-Host "Builds Count: $($state.builds.Count)"
if ($state.hand.Count -ne 10) {
    Write-Host "FAILED: Expected 10 cards in hand for 2-player deal 1" -ForegroundColor Red
    exit 1
}
Write-Host "SUCCESS: Authentic 2-player Khasino state verified (10 cards in hand)" -ForegroundColor Green

# 8. Query Legal Moves
Write-Host "`n8. Querying Legal Moves for active player..."
$movesRes = Invoke-RestMethod -Uri "$baseUrl/games/khasino/legal-moves?sessionId=$sessionId" -Method Get -Headers $headers
Write-Host "Total Legal Moves Generated: $($movesRes.actions.Count)"
if ($movesRes.actions.Count -gt 0) {
    Write-Host "First 3 Legal Moves:"
    for ($i = 0; $i -lt [Math]::Min(3, $movesRes.actions.Count); $i++) {
        $a = $movesRes.actions[$i]
        Write-Host "  - [$($a.type)] $($a.description)" -ForegroundColor Yellow
    }
}

# 9. Test Action Execution (if my turn, play first legal move)
if ($state.currentTurn -eq $loginRes.email -or $state.currentTurn -eq $testUsername) {
    if ($movesRes.actions.Count -gt 0) {
        $move = $movesRes.actions[0]
        Write-Host "`n9. Executing Legal Move: [$($move.type)] $($move.description)..."
        $actionBody = @{
            type = $move.type
            playedCardId = $move.playedCardId
            targetFloorCardIds = $move.targetFloorCardIds
            targetBuildIds = $move.targetBuildIds
            targetBuildId = $move.targetBuildId
            declaredValue = $move.declaredValue
            stolenCardId = $move.stolenCardId
            stolenOpponentId = $move.stolenOpponentId
        } | ConvertTo-Json
        $actionRes = Invoke-RestMethod -Uri "$baseUrl/games/khasino/action?sessionId=$sessionId" -Method Post -Body $actionBody -Headers $headers -ContentType "application/json"
        Write-Host "SUCCESS: Action executed: $($actionRes.success), Next Player: $($actionRes.nextPlayer)" -ForegroundColor Green
    }
} else {
    Write-Host "`n9. Opponent's turn ($($state.currentTurn)). Waiting 2s for CPU Bot move..."
    Start-Sleep -Seconds 2
    $stateRes2 = Invoke-RestMethod -Uri "$baseUrl/games/khasino/state?sessionId=$sessionId" -Method Get -Headers $headers
    Write-Host "Updated State after CPU: Last Action: '$($stateRes2.state.lastAction)', Next Turn: '$($stateRes2.state.currentTurn)'" -ForegroundColor Green
}

# 10. Test 4-Player 2v2 Partnerships Setup
Write-Host "`n10. Testing 4-Player 2v2 Partnerships Game Creation..."
$pBody = @{
    game = "khasino"
    maxPlayers = "4"
    bet = "50"
    khasinoMode = "FOUR_PLAYER_PARTNERSHIPS"
    difficulty = "MEDIUM"
} | ConvertTo-Json
$pCreate = Invoke-RestMethod -Uri "$baseUrl/create-game" -Method Post -Body $pBody -Headers $headers -ContentType "application/json"
$pSId = $pCreate.sessionId
$pStart = Invoke-RestMethod -Uri "$baseUrl/start-game" -Method Post -Body (@{ sessionId = $pSId } | ConvertTo-Json) -Headers $headers -ContentType "application/json"
$pState = (Invoke-RestMethod -Uri "$baseUrl/games/khasino/state?sessionId=$pSId" -Method Get -Headers $headers).state
Write-Host "Partnership Mode: $($pState.mode)"
Write-Host "Teams Assigned: $($pState.playerTeams | ConvertTo-Json -Compress)"
Write-Host "Captured Piles: $($pState.capturedPiles.PSObject.Properties.Name -join ', ')"
if ($pState.mode -ne "FOUR_PLAYER_PARTNERSHIPS" -or ($null -eq $pState.capturedPiles.PSObject.Properties['Team 1'])) {
    Write-Host "FAILED: Expected FOUR_PLAYER_PARTNERSHIPS with Team 1 and Team 2 captured piles" -ForegroundColor Red
    exit 1
}
Write-Host "SUCCESS: 4-Player 2v2 Partnership game correctly initialized!" -ForegroundColor Green

# 11. Test 3-Player 7-Point Setup
Write-Host "`n11. Testing 3-Player 7-Point Game Creation..."
$threeBody = @{
    game = "khasino"
    maxPlayers = "3"
    bet = "50"
    khasinoMode = "THREE_PLAYER"
} | ConvertTo-Json
$threeCreate = Invoke-RestMethod -Uri "$baseUrl/create-game" -Method Post -Body $threeBody -Headers $headers -ContentType "application/json"
$threeSId = $threeCreate.sessionId
$threeStart = Invoke-RestMethod -Uri "$baseUrl/start-game" -Method Post -Body (@{ sessionId = $threeSId } | ConvertTo-Json) -Headers $headers -ContentType "application/json"
$threeState = (Invoke-RestMethod -Uri "$baseUrl/games/khasino/state?sessionId=$threeSId" -Method Get -Headers $headers).state
Write-Host "3-Player Mode: $($threeState.mode)"
Write-Host "3-Player Floor Cards: $($threeState.floorCards.Count) (Expected: 1)"
Write-Host "3-Player Hand Size: $($threeState.hand.Count) (Expected: 13)"
Write-Host "3-Player Scoring Mode: $(if ($threeState.isElevenPointMode) { '11-pt' } else { '7-pt' }) (Expected: 7-pt)"
if ($threeState.floorCards.Count -ne 1 -or $threeState.hand.Count -ne 13 -or $threeState.isElevenPointMode) {
    Write-Host "FAILED: 3-Player game setup mismatch" -ForegroundColor Red
    exit 1
}
Write-Host "SUCCESS: 3-Player 7-Point game correctly initialized (13 cards + 1 floor card)!" -ForegroundColor Green

Write-Host "`n=============================================" -ForegroundColor Cyan
Write-Host "  ALL KHASINO API TESTS PASSED SUCCESSFULLY! " -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Cyan
