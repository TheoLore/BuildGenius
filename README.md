# Build Genius

**Intelligent build doctor for Android Code Studio (ACSIDE)**

English only • Built for serious productivity

---

## What it does

Build Genius deeply analyzes your open project and:

- Detects outdated AGP / Kotlin / compileSdk
- Finds missing AndroidX flags and dangerous memory settings
- Surfaces structural problems (missing modules, incomplete Flutter projects…)
- Gives every issue a **severity** + **confidence** score
- Offers **one-click safe fixes** with clear explanations
- Shows a live **Health Score** dashboard

All changes are written directly to your build files. Re-sync the project afterwards to see the effect.

---

## Features

| Feature                    | Description                                      |
|---------------------------|--------------------------------------------------|
| Project Health Score      | 0–100 score based on real issues                 |
| Smart Diagnosis           | AGP, Kotlin, SDK, Properties, Structure          |
| One-Click Fix             | High-confidence automatic repairs                |
| Fix All Safe              | Applies every safe fix in priority order         |
| Diff-friendly             | Every change is tracked (original → new)         |
| Quick Fix action          | Run silent auto-fixes from the editor menu       |
| Beautiful Compose UI      | Modern Material 3 overlay                        |

---

## How to build

1. Open the project in Android Studio / ACSIDE
2. Make sure you have the ACSIDE Plugin API available (JitPack)
3. Run:

```bash
./gradlew :main:assembleRelease
```

The `.acp` file will be generated at `main/build/BuildGenius.acp` and also copied to the root.

---

## Installation in ACSIDE

1. Copy `BuildGenius.acp` to your device
2. Open ACSIDE → Plugins
3. Install from local file (or put it in the official plugins repo structure)

---

## Actions exposed

- **Build Genius** – Opens the full dashboard (editor overflow / toolbar)
- **Quick Fix Build** – Runs high-confidence automatic fixes immediately

---

## Safety

- Only high-confidence fixes are marked auto-fixable
- Every change can be inspected
- No network calls, no telemetry
- Works completely offline

---

## Author

Canadian developer – made for people who actually ship apps on-device.

Enjoy.
