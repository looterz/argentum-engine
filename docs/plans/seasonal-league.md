# Seasonal Draft League — Implementation Plan

## Concept

A **seasonal league** where a host defines a sequence of draft rounds, each using a different set. Players join the
league and accumulate standings across all rounds. Each round is a self-contained draft tournament (fresh pool, fresh
deck) — no card carryover between rounds.

The host controls when each round begins. Rounds have a label (e.g., "Week 1 — Portal") rather than a fixed date.

## Data Model

### PlayerAccount

Persistent player identity, linked via magic link email auth.

```kotlin
data class PlayerAccount(
    val id: UUID,
    val email: String,
    val displayName: String,
    val createdAt: Instant,
)
```

### LeagueSeason

Top-level entity representing a full season.

```kotlin
data class LeagueSeason(
    val id: UUID,
    val name: String,                        // "Spring 2026"
    val hostAccountId: UUID,                 // references PlayerAccount
    val rounds: List<LeagueRound>,           // ordered round definitions
    val status: LeagueStatus,                // ENROLLMENT, ACTIVE, COMPLETE
    val defaultFormat: DraftFormat,           // DRAFT, SEALED, etc.
    val createdAt: Instant,
)

enum class LeagueStatus { ENROLLMENT, ACTIVE, COMPLETE }
```

### LeagueRound

A single round within a season. Maps to one or more tournament lobbies (pods) once started.

```kotlin
data class LeagueRound(
    val roundNumber: Int,                    // 1-based
    val label: String,                       // "Week 1 — Portal" (free text)
    val setCode: String,                     // "POR", "ONS", etc.
    val format: DraftFormat?,                // per-round override (null = use season default)
    val status: RoundStatus,                 // PENDING, ACTIVE, COMPLETE
    val podIds: List<String>,                // lobby IDs when split into multiple pods
)

enum class RoundStatus { PENDING, ACTIVE, COMPLETE }
```

### LeagueStandings

Aggregated across all completed rounds.

```kotlin
data class LeaguePlayerStanding(
    val accountId: UUID,
    val displayName: String,
    val totalPoints: Int,                    // sum across rounds
    val roundResults: List<RoundResult>,     // per-round breakdown
    val matchWins: Int,
    val matchLosses: Int,
    val matchDraws: Int,
)

data class RoundResult(
    val roundNumber: Int,
    val participated: Boolean,               // false if player missed this round
    val points: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
)
```

## Lifecycle

```
1. Host creates season → status = ENROLLMENT
   - Defines name, round sequence (label + set per round), default format
   
2. Players join season (must be logged in via magic link)
   - Can join at any point before the season completes
   
3. Host starts a round → status = ACTIVE, round status = ACTIVE
   - Splits players into pods if >8 enrolled
   - Creates a TournamentLobby per pod for that round's set
   - All enrolled players are auto-added to their pod's lobby
   - Players who aren't online join when they connect (reconnection)
   
4. Round plays out through existing tournament flow
   - Draft/sealed → deck building → Swiss matches
   - Uses existing TournamentLobby + TournamentManager
   
5. Round completes → round status = COMPLETE
   - Points from TournamentManager.standings written to PostgreSQL
   - League standings updated and broadcast
   
6. Host starts next round (repeat 3-5)

7. All rounds complete → status = COMPLETE
   - Final standings displayed
```

## Implementation Phases

### Phase 1: Database & Auth Foundation

This phase establishes PostgreSQL and magic link authentication — prerequisites for everything else.

**Dependencies:**

- `spring-boot-starter-data-jpa`
- `org.postgresql:postgresql`
- `org.flywaydb:flyway-core`
- Email sending library (e.g., `spring-boot-starter-mail` or Resend SDK)

**Schema:**

```sql
-- Flyway: V1__create_accounts_and_auth.sql

CREATE TABLE player_account (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT NOT NULL UNIQUE,
    display_name    TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE magic_link_token (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT NOT NULL,
    token           TEXT NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    used            BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE player_session (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID NOT NULL REFERENCES player_account(id),
    session_token   TEXT NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Magic link auth flow:**

```
1. Player enters email on login page
2. POST /api/auth/login { email }
   → Server generates token (UUID), stores in magic_link_token with 15-min expiry
   → Server sends email: "Click to log in: https://argentum.app/auth?token=xyz"
   → Response: 200 OK (always, to prevent email enumeration)
   
3. Player clicks link
4. GET /api/auth/verify?token=xyz
   → Server validates: token exists, not expired, not used
   → Marks token as used
   → Creates or fetches PlayerAccount for that email
   → Creates player_session with long-lived session_token (30 days)
   → Response: redirect to app with session_token as cookie or query param
   
5. Client stores session_token in localStorage
6. All subsequent requests include session_token
   → WebSocket: sent in Connect message
   → REST: sent as Authorization header or cookie
   
7. Server resolves session_token → PlayerAccount on each request
```

**Bridge to existing PlayerIdentity:**

The current `PlayerIdentity` system stays for in-game use. When a logged-in player connects via WebSocket:

1. `Connect` message includes `sessionToken` (in addition to existing `token`)
2. Server resolves `sessionToken` → `PlayerAccount`
3. `PlayerIdentity.accountId` is set (new nullable field)
4. League operations require `accountId != null`
5. Anonymous play (casual games) still works without login

**New files:**

| File | Purpose |
|------|---------|
| `auth/PlayerAccount.kt` | JPA entity |
| `auth/MagicLinkToken.kt` | JPA entity |
| `auth/PlayerSessionEntity.kt` | JPA entity |
| `auth/AuthService.kt` | Login, verify, session management |
| `auth/EmailService.kt` | Send magic link emails |
| `auth/PlayerAccountRepository.kt` | Spring Data repository |
| `controller/AuthController.kt` | REST endpoints for auth flow |

**Configuration** in `application.yml`:

```yaml
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/argentum}
    username: ${DATABASE_USER:argentum}
    password: ${DATABASE_PASSWORD:argentum}
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway manages schema
  flyway:
    enabled: true
    locations: classpath:db/migration
  mail:
    host: ${SMTP_HOST:smtp.resend.com}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
```

### Phase 2: Backend — League Season Management

**Schema (continued):**

```sql
-- Flyway: V2__create_league_tables.sql

CREATE TABLE league_season (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    host_account_id UUID NOT NULL REFERENCES player_account(id),
    status          TEXT NOT NULL DEFAULT 'ENROLLMENT',
    default_format  TEXT NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE league_enrollment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_id       UUID NOT NULL REFERENCES league_season(id),
    account_id      UUID NOT NULL REFERENCES player_account(id),
    enrolled_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (season_id, account_id)
);

CREATE TABLE league_round (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_id       UUID NOT NULL REFERENCES league_season(id),
    round_number    INT NOT NULL,
    label           TEXT NOT NULL,
    set_code        TEXT NOT NULL,
    format          TEXT,                    -- per-round override, null = season default
    status          TEXT NOT NULL DEFAULT 'PENDING',
    UNIQUE (season_id, round_number)
);

CREATE TABLE league_pod (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    round_id        UUID NOT NULL REFERENCES league_round(id),
    pod_number      INT NOT NULL,
    lobby_id        TEXT,                    -- links to TournamentLobby
    UNIQUE (round_id, pod_number)
);

CREATE TABLE league_match_result (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    round_id        UUID NOT NULL REFERENCES league_round(id),
    pod_id          UUID NOT NULL REFERENCES league_pod(id),
    account_id      UUID NOT NULL REFERENCES player_account(id),
    opponent_id     UUID REFERENCES player_account(id),  -- null = BYE
    result          TEXT NOT NULL,           -- WIN, LOSS, DRAW, BYE
    points          INT NOT NULL,            -- 3, 0, 1, or 3 (BYE)
    UNIQUE (round_id, account_id, opponent_id)
);

CREATE VIEW league_standings AS
SELECT
    e.season_id,
    e.account_id,
    pa.display_name,
    COALESCE(SUM(mr.points), 0) AS total_points,
    COUNT(mr.id) FILTER (WHERE mr.result = 'WIN') AS match_wins,
    COUNT(mr.id) FILTER (WHERE mr.result = 'LOSS') AS match_losses,
    COUNT(mr.id) FILTER (WHERE mr.result = 'DRAW') AS match_draws,
    COUNT(DISTINCT mr.round_id) AS rounds_played
FROM league_enrollment e
JOIN player_account pa ON pa.id = e.account_id
LEFT JOIN league_match_result mr ON mr.account_id = e.account_id
    AND mr.round_id IN (SELECT id FROM league_round WHERE season_id = e.season_id)
GROUP BY e.season_id, e.account_id, pa.display_name;
```

**New files:**

| File | Purpose |
|------|---------|
| `league/entity/LeagueSeasonEntity.kt` | JPA entity |
| `league/entity/LeagueEnrollmentEntity.kt` | JPA entity |
| `league/entity/LeagueRoundEntity.kt` | JPA entity |
| `league/entity/LeaguePodEntity.kt` | JPA entity |
| `league/entity/LeagueMatchResultEntity.kt` | JPA entity |
| `league/repository/*Repository.kt` | Spring Data repositories (one per entity) |
| `league/LeagueManager.kt` | Business logic: create, join, start round, aggregate standings |

**LeagueManager responsibilities:**

- `createSeason(name, rounds, format, hostAccount)` → creates season + round rows
- `joinSeason(seasonId, account)` — insert enrollment
- `leaveSeason(seasonId, account)` — remove enrollment (only before season starts)
- `startNextRound(seasonId, hostAccount)` — host-only, splits pods, creates lobbies
- `onRoundComplete(lobbyId, standings)` — callback: writes match results to DB
- `getStandings(seasonId)` → query standings view
- `transferHost(seasonId, currentHost, newHostAccount)` — reassign host

### Phase 3: Backend — Protocol & API

**REST endpoints:**

```
POST /api/auth/login                 — request magic link
GET  /api/auth/verify?token=...      — verify magic link, issue session
GET  /api/auth/me                    — current account info

GET  /api/leagues                    — list leagues (filterable by status)
POST /api/leagues                    — create season (authenticated)
GET  /api/leagues/{id}               — season details + standings
POST /api/leagues/{id}/join          — join season (authenticated)
POST /api/leagues/{id}/leave         — leave season
POST /api/leagues/{id}/start-round   — start next round (host only)
POST /api/leagues/{id}/transfer-host — transfer host role
```

**WebSocket messages:**

```kotlin
// Client → Server
ClientMessage.StartLeagueRound(leagueId: String)      // host only

// Server → Client
ServerMessage.LeagueRoundStarting(leagueId, roundNumber, lobbyId)
ServerMessage.LeagueRoundComplete(leagueId, roundNumber, standings)
ServerMessage.LeagueComplete(leagueId, finalStandings)
```

Most league management goes through REST (it's not real-time). WebSocket messages are only for
in-the-moment events that need to push to connected clients (round starting, results coming in).

**Integration with existing tournament flow:**

- `LeagueManager.startNextRound()` creates `TournamentLobby` via existing `LobbyHandler` logic
- The lobby's `setCodes` are set to the current round's set
- When tournament completes (`TournamentComplete`), `LeagueManager.onRoundComplete()` is called
- Hook into `TournamentMatchHandler` to detect tournament completion and propagate results

### Phase 4: Frontend — Auth & League UI

**Auth components:**

| Component | Purpose |
|-----------|---------|
| `pages/LoginPage.tsx` | Email input, "Send magic link" button |
| `pages/AuthVerifyPage.tsx` | Landing page for magic link, exchanges token for session |
| `store/slices/authSlice.ts` | Auth state: account, session token, login/logout |
| `components/auth/AccountMenu.tsx` | Logged-in user display, logout |

**League components:**

| Component | Purpose |
|-----------|---------|
| `pages/LeagueListPage.tsx` | Browse leagues, create new |
| `pages/LeaguePage.tsx` | Season detail: rounds, standings, host controls |
| `components/league/LeagueStandings.tsx` | Standings table with per-round breakdown |
| `components/league/RoundList.tsx` | Round sequence with status indicators |
| `components/league/CreateLeagueForm.tsx` | Form: name, add rounds (label + set picker), default format |

**Standings table design:**

```
┌──────────────┬───────┬─────┬─────┬─────┬─────┬─────┐
│ Player       │ Total │ R1  │ R2  │ R3  │ R4  │ W-L │
├──────────────┼───────┼─────┼─────┼─────┼─────┼─────┤
│ Alice        │  15   │  6  │  9  │  -  │  -  │ 5-1 │
│ Bob          │  12   │  9  │  3  │  -  │  -  │ 4-2 │
│ Charlie      │   9   │  -  │  9  │  -  │  -  │ 3-0 │
└──────────────┴───────┴─────┴─────┴─────┴─────┴─────┘

- = did not participate
```

**UX flow:**

1. **Login:** Player enters email → checks inbox → clicks magic link → logged in
2. **Create:** Host fills in season name, adds rounds (each with label + set dropdown), picks format, submits
3. **Join:** Player sees league list, clicks join, sees season page with upcoming rounds
4. **Start round:** Host clicks "Start Round N" → players get redirected into their pod's draft lobby
5. **During round:** Standard tournament UX (draft → build → play) — no changes needed
6. **After round:** Players return to league page, see updated standings
7. **Season end:** Final standings page with winner highlighted

### Phase 5: Polish & Edge Cases

- **Reconnection:** Player reconnects → server checks `accountId` → if in active league round → rejoin pod lobby
- **Late join:** Player joins mid-season, gets 0 points for missed rounds — standings reflect this
- **Host transfer:** Any enrolled player can be promoted to host via REST endpoint
- **Pod splitting:** >8 players split into even pods (e.g., 12 → two 6-player pods). Random assignment each round.
- **Cleanup:** Periodic job to expire old magic link tokens and sessions

## What We Reuse vs. Build New

| Concern | Approach |
|---------|----------|
| Draft/sealed mechanics | **Reuse** — TournamentLobby handles all draft formats |
| Match play | **Reuse** — GameSession, existing game flow |
| Round-robin scheduling | **Reuse** — TournamentManager |
| Per-round standings | **Reuse** — TournamentManager.standings → written to PostgreSQL |
| Booster generation | **Reuse** — BoosterGenerator with set configs |
| Player identity (in-game) | **Reuse** — PlayerIdentity, extended with optional `accountId` |
| Player accounts | **Build** — PostgreSQL + magic link auth |
| Cross-round standings | **Build** — SQL view + LeagueManager |
| Season lifecycle | **Build** — LeagueManager state machine |
| League persistence | **Build** — PostgreSQL via Spring Data JPA + Flyway |
| League UI | **Build** — new pages + components |
| League REST API | **Build** — AuthController + LeagueController |

## Decisions

1. **Scoring**: 3 pts/win, 1 pt/draw, 0 pts/loss (matches existing `PlayerStanding.points`)
2. **Tiebreakers**: TBD — total points as primary, tiebreaker method to be decided later
3. **Pod splitting**: When >8 players, split into even pods. Random assignment each round.
4. **Format per round**: Configurable — each round specifies its own format independently.
5. **Host transfer**: Any enrolled player can be promoted to host.
6. **Auth**: Magic link (passwordless email login). Accounts required for league participation, anonymous play
   unchanged for casual games.
7. **Persistence**: PostgreSQL for accounts + league data, Redis stays for ephemeral lobby/draft state.

## Open Questions

1. **Pod assignment**: Random pods each round? Or seeded (e.g., top players spread across pods)?
2. **Cross-pod fairness**: With separate pods, a 3-0 in a weak pod = same points as 3-0 in a strong pod. Acceptable?
3. **Email provider**: Resend, AWS SES, or SMTP relay?
