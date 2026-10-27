# Build Genius

**Intelligent build doctor for Android Code Studio (ACSIDE)**

English only • Built for serious productivity

**This repository is the main workspace.**

---

## What it does

1. **Static analysis** – AGP, Kotlin, compileSdk, AndroidX, heap, structure
2. **Real error parsing** – missing deps, unresolved refs, OOM, conflicts
3. **Environment alignment** – matches project to installed ACSIDE SDK/JDK/Flutter
4. **TURBO MODE** – max on-device Gradle speed (Bionic / Termux packages aware)
5. **One-click fixes** that edit real build files

---

## TURBO MODE

One button applies:

- parallel + workers + configuration-cache
- build cache + daemon + vfs.watch
- nonTransitiveRClass + nonFinalResIds
- Kotlin incremental + in-process compiler
- heap tuned for phones (ACSIDE heat-aware defaults)

---

## Build

```bash
./gradlew :main:assembleRelease
```

Or use **Actions** → artifact `BuildGenius-acp`.

---

## Install in ACSIDE

Plugins → install `BuildGenius.acp`

---

## Author

Canadian developer – ship on-device.
