# Build Genius

**Intelligent build doctor for Android Code Studio (ACSIDE)**

English only • Built for serious productivity

---

## What it does

Build Genius is an **AI-like error fixing system** for ACSIDE:

1. **Static analysis** of your project (AGP, Kotlin, compileSdk, AndroidX, heap, structure…)
2. **Real error parsing** – understands Gradle / compiler logs:
   - Missing dependencies (`Could not find …`, `Failed to resolve …`)
   - Unresolved references → suggests the correct library (ViewModel, Retrofit, Compose, Room, Hilt…)
   - Duplicate classes / Support vs AndroidX conflicts
   - OOM / heap issues
   - AGP / Kotlin / JDK mismatches
   - Manifest merger failures
   - Missing plugins & SDK platforms
3. **One-click safe fixes** that actually edit your `build.gradle(.kts)` and `gradle.properties`
4. **Health Score** dashboard (0–100)

---

## Features

| Feature | Description |
|--------|-------------|
| Project Health Score | 0–100 based on real issues |
| Smart Diagnosis | AGP, Kotlin, SDK, Properties, Structure |
| **Error Log Parser** | Understands real Gradle/compiler output |
| **Dependency Catalog** | 50+ common symbols → correct Maven coordinates |
| One-Click Fix | High-confidence automatic repairs |
| Add Missing Library | Literally inserts `implementation("…")` |
| Fix All Safe | Applies every safe fix in priority order |
| Quick Fix action | Silent auto-fixes from editor menu |
| Material 3 UI | Modern overlay dashboard |

---

## How the "AI-like" fixing works

```
Build log / static scan
        ↓
   ErrorParser (patterns + DependencyCatalog)
        ↓
   List of BuildIssue (severity + confidence + fixActionId)
        ↓
   BuildFixer (writes real changes to build files)
        ↓
   Re-sync project → problem gone
```

No external AI API required. Everything runs offline on-device.

---

## How to build

```bash
./gradlew :main:assembleRelease
```

Or let **GitHub Actions** build it (push to `main` or run the workflow manually). The `.acp` is uploaded as an artifact.

---

## Installation in ACSIDE

1. Get `BuildGenius.acp` (from Actions artifact or local build)
2. ACSIDE → Plugins → Install from file

---

## Safety

- Only high-confidence fixes are auto-applied
- Every change is tracked
- Offline, no telemetry

---

## Author

Canadian developer – made for people who ship apps on-device.
