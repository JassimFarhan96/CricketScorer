# Cricket Scorer — Android App (Java)

A fully functional cricket match tracking app for Android, written in Java.

---

## How to open in Android Studio

1. Unzip `CricketScorer.zip`
2. Open Android Studio → **File → Open** → select the `CricketScorer` folder
3. Wait for Gradle sync to finish (it will download dependencies automatically)
4. Connect a device or launch an emulator (API 24+)
5. Click ▶ **Run**

---

## Project structure

```
CricketScorer/
├── app/
│   ├── build.gradle                  ← Dependencies (RecyclerView, CardView, FlexBox, Material)
│   └── src/main/
│       ├── AndroidManifest.xml       ← Activity declarations + CricketApp registration
│       ├── java/com/cricket/scorer/
│       │   ├── activities/
│       │   │   ├── CricketApp.java           ← Application singleton (holds Match + Engine)
│       │   │   ├── HomeActivity.java          ← Home screen (3 menu options)
│       │   │   ├── SetupActivity.java         ← Match setup form
│       │   │   ├── InningsActivity.java       ← Live match tracking (both innings)
│       │   │   ├── InningsBreakActivity.java  ← Between-innings break screen
│       │   │   └── StatsActivity.java         ← Final scorecard + WhatsApp share
│       │   ├── models/
│       │   │   ├── Match.java        ← Top-level match data model
│       │   │   ├── Innings.java      ← Per-innings runs, wickets, overs, batsmen
│       │   │   ├── Over.java         ← Single over (up to 6 valid balls + extras)
│       │   │   ├── Ball.java         ← Single delivery (NORMAL/WIDE/NO_BALL/WICKET)
│       │   │   └── Player.java       ← Batsman stats (runs, balls, 4s, 6s, SR)
│       │   ├── adapters/
│       │   │   ├── BallAdapter.java          ← Current over ball circles (RecyclerView)
│       │   │   └── OverHistoryAdapter.java   ← Completed overs list (RecyclerView)
│       │   └── utils/
│       │       ├── MatchEngine.java  ← Core cricket rules + state machine
│       │       └── ShareUtils.java   ← Scorecard text builder + WhatsApp/Share intents
│       └── res/
│           ├── layout/
│           │   ├── activity_home.xml
│           │   ├── activity_setup.xml
│           │   ├── activity_innings.xml       ← Two-panel: batting top, over tracker bottom
│           │   ├── activity_innings_break.xml
│           │   └── activity_stats.xml
│           ├── drawable/              ← Button shapes, ball colors, card backgrounds
│           └── values/
│               ├── colors.xml
│               ├── strings.xml
│               └── themes.xml
├── build.gradle                      ← Project-level
├── settings.gradle
└── gradle.properties
```

---

## Key classes to read for debugging

| Class | What it does |
|---|---|
| `MatchEngine.java` | All cricket rules live here. `deliverNormalBall()`, `deliverWide()`, `deliverNoBall()`, `deliverWicket()`, `undoLastBall()` — each returns a `MatchState` enum |
| `Innings.java` | Holds the live state: totalRuns, totalWickets, totalValidBalls, strikerIndex, overs list. `recordNormalBall()` and `undoLastBall()` mutate this |
| `InningsActivity.java` | UI controller for live tracking. `handleBall()` → `engine.deliver*()` → `handleMatchState()` → `refreshUI()` |
| `Ball.java` | Factory methods: `Ball.normal(runs)`, `Ball.wide()`, `Ball.noBall()`, `Ball.wicket()`. `isValid()` determines if it counts toward the 6-ball over |
| `Over.java` | `getValidBallCount()` counts only legal deliveries. `isComplete()` = 6 valid balls |

---

## Valid balls rule

A valid ball is any delivery that is **not** a Wide or No Ball.
- Wide → `Ball.isValid() = false`, +1 extra, over does NOT advance
- No Ball → `Ball.isValid() = false`, +1 extra, over does NOT advance
- Wicket → `Ball.isValid() = true`, counts as one of the 6 balls
- Normal (0–6 runs) → `Ball.isValid() = true`

An over ends exactly when `Over.getValidBallCount() == 6`.

---

## WhatsApp sharing

`ShareUtils.shareViaWhatsApp(context, phoneNumber, match)` opens a WhatsApp chat with a pre-filled plain-text scorecard. Internet permission is declared in the manifest. The phone number must be in international format (e.g. `+919876543210`).

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| `androidx.appcompat` | 1.7.0 | AppCompatActivity base |
| `com.google.android.material` | 1.12.0 | Buttons, dialogs, CardView |
| `androidx.recyclerview` | 1.3.2 | Ball circles + over history list |
| `androidx.cardview` | 1.0.0 | Menu cards, stats cards |
| `com.google.android.flexbox` | 3.0.0 | Wrapping ball-input button row |

---

## Minimum requirements

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK API 34 (compileSdk)
- Minimum device API 24 (Android 7.0)
- Java 8 source compatibility
