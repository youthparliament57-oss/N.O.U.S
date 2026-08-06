# NOUS Android AI Assistant

<p align="center">
  <strong>The mind that perceives.</strong><br>
  <em>Advanced AI-powered personal assistant for Android</em>
</p>

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Building the Project](#building-the-project)
- [Project Structure](#project-structure)
- [Modules](#modules)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)

---

## 🎯 Overview

NOUS is a sophisticated Android AI assistant that combines:
- **Local & Cloud AI Processing** (LLM integration, speech recognition)
- **Advanced Memory System** (episodic, semantic, procedural memories)
- **Multi-modal Capabilities** (voice, vision, text)
- **Privacy-first Design** (local processing, encrypted storage)
- **Modular Architecture** (37+ independent modules)

### Version Information
- **Version:** 0.1.0-production
- **Min SDK:** 29 (Android 10)
- **Target SDK:** 36
- **Build Tools:** 34.0.0

---

## ✨ Features

### Core AI Capabilities
| Feature | Description | Module |
|---------|-------------|--------|
| **Brain Engine** | Multi-layer cognitive processing | `:feature:brain` |
| **LLM Integration** | OpenAI, Anthropic, Gemini support | `:feature:llm` |
| **Voice Recognition** | Whisper.cpp local STT | `:native:whisper` |
| **Vision Processing** | Image understanding & OCR | `:feature:vision` |
| **Memory System** | Episodic + Semantic memory | `:feature:memory` |

### Advanced Features
| Feature | Description |
|---------|-------------|
| **Persona System** | Customizable AI personality |
| **Automation** | Rule-based task automation |
| **Agent Mode** | Proactive assistance |
| **Security** | Encrypted database (SQLCipher) |
| **Connectivity** | Network state management |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        :app                                 │
│                   (Application Entry)                       │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐         │
│  │ :feature│ │ :feature│ │ :feature│ │ :feature│         │
│  │ :brain  │ │  :llm   │ │  :voice │ │ :vision │    ...    │
│  └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘         │
│       └────────────┴───────────┴───────────┘               │
│  ┌─────────────────────────────────────────────────┐       │
│  │                  :core:*                         │       │
│  │  common │ di │ database │ network │ security    │       │
│  └─────────────────────────────────────────────────┘       │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐         │
│  │:native  │ │:native  │ │:native  │ │:native  │         │
│  │:whisper │ │:llamacpp│ │ :ecapa  │ │:wakeword│         │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘         │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔧 Prerequisites

### Required Software
1. **JDK 21+** ([Eclipse Temurin](https://adoptium.net/))
2. **Android SDK** (API level 36+)
3. **Android NDK** (27.1.12297006)
4. **CMake** (3.22.1+) - for native modules
5. **Gradle 8.10.2+** (included via wrapper)

### Hardware Requirements
- **Minimum:** 8GB RAM for full build with native code
- **Recommended:** 16GB RAM
- **Disk Space:** ~10GB (including SDK, dependencies, build cache)

### For GitHub Actions Builds
- No local setup required!
- Automatic builds on push/PR to main branch
- Artifacts available for download

---

## 🚀 Quick Start

### Option 1: GitHub Actions (Recommended - No Local Setup)

1. **Fork this repository**
2. **Enable GitHub Actions**
3. **Push your changes** or go to **Actions tab → Run workflow**
4. **Download APK** from Artifacts section

```bash
# Clone and push
git clone https://github.com/YOUR_USERNAME/nous-android.git
cd nous-android
git checkout -b your-feature
# Make changes...
git add .
git commit -m "Your feature"
git push origin your-feature
```

### Option 2: Local Build

```bash
# 1. Clone repository
git clone --recursive https://github.com/YOUR_USERNAME/nous-android.git
cd nous-android

# 2. Set JAVA_HOME (adjust path as needed)
export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || echo $JAVA_HOME)

# 3. Set ANDROID_HOME
export ANDROID_HOME=$HOME/Android/Sdk

# 4. Build Debug APK
./gradlew assembleDevDebug

# 5. Find APK
ls app/build/outputs/apk/dev/debug/
```

---

## 📦 Building the Project

### Build Variants

| Command | Output | Description |
|---------|--------|-------------|
| `./gradlew assembleDevDebug` | `app-dev-debug.apk` | Development build |
| `./gradlew assembleProdDebug` | `app-prod-debug.apk` | Production debug |
| `./gradlew assembleRelease` | `app-release.apk` | Release build (signed) |

### Build Flavors

| Flavor | Description |
|--------|-------------|
| `dev` | Development (logging enabled, mock data) |
| `prod` | Production (optimized, release mode) |

### Running Tests

```bash
# Unit tests only
./gradlew testDevDebugUnitTest

# Instrumented tests (requires device/emulator)
./gradlew connectedDevDebugAndroidTest

# All tests
./gradlew test
```

---

## 📁 Project Structure

```
nous-android/
├── .github/workflows/     # CI/CD configurations
├── app/                    # Main application module
│   ├── src/main/
│   │   ├── java/...        # Application code
│   │   ├── res/           # Resources
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build-logic/            # Convention plugins
├── core/                   # Core modules (12)
│   ├── common/             # Shared utilities
│   ├── di/                 # Dependency injection
│   ├── database/           # Room database + migrations
│   ├── datastore/          # DataStore preferences
│   ├── designsystem/       # UI components & theming
│   ├── navigation/         # Compose Navigation
│   ├── network/            # Retrofit + OkHttp
│   ├── permissions/        # Runtime permissions
│   ├── security/           # Encryption, auth
│   ├── telemetry/          # Analytics, logging
│   └── testing/            # Test utilities
├── feature/                # Feature modules (15)
│   ├── agent/              # Accessibility service
│   ├── automation/         # Task automation
│   ├── brain/              # Cognitive engine
│   ├── cognitive/          # Reasoning module
│   ├── connectivity/       # Network monitoring
│   ├── llm/                # LLM API integration
│   ├── memory/             # Memory system
│   ├── persona/            # Personality config
│   ├── productivity/       # Timers, reminders
│   ├── security/           # App security
│   ├── selfmodify/         # Self-improvement
│   ├── system/             # System integration
│   ├── ui/                 # Main UI shell
│   ├── vision/             # Camera/OCR
│   └── voice/              # Speech I/O
├── native/                 # Native C/C++ modules (4)
│   ├── ecapa/              # Speaker verification
│   ├── llamacpp/           # LLM inference
│   ├── wakeword/           # Wake word detection
│   └── whisper/            # Speech-to-text
├── dynamicfeature/         # On-demand features (4)
│   ├── ar/                 # AR experiences
│   ├── drone/              # Drone control
│   ├── hacker/             # Security tools
│   └── voiceclone/         # Voice cloning
├── gradle/                 # Wrapper files
├── libs.versions.toml      # Version catalog
├── build.gradle.kts        # Root build script
├── settings.gradle.kts     # Module includes
├── gradle.properties       # Build configuration
└── README.md               # This file
```

---

## 🧩 Modules Detailed

### Core Modules (`core:*`)

| Module | Purpose | Key Classes |
|--------|---------|------------|
| `common` | Utilities, extensions | `Extensions.kt`, `ResultWrapper.kt` |
| `di` | Hilt setup | `AppInitializer`, `Module.kt` |
| `database` | Room DB | `NousDatabase`, `Migrations.kt` |
| `datastore` | Preferences | `PreferencesDataStore.kt` |
| `designsystem` | UI Kit | `Theme.kt`, `Components.kt` |
| `navigation` | Nav graph | `NavGraph.kt`, `Destinations.kt` |
| `network` | HTTP client | `ApiService.kt`, `AuthInterceptor.kt` |
| `permissions` | Runtime perms | `PermissionHandler.kt` |
| `security` | Crypto | `EncryptionManager.kt` |
| `telemetry` | Logging/analytics | `TimberSetup.kt`, `Analytics.kt` |
| `testing` | Test helpers | `TestRule.kt`, `MockData.kt` |

### Feature Modules (`feature:*`)

| Module | Status | Notes |
|--------|--------|-------|
| `brain` | ✅ Working | Multi-layer cognitive processing |
| `memory` | ✅ Working | Episodic + semantic memory |
| `llm` | ✅ Working | OpenAI, Anthropic, Gemini |
| `voice` | ✅ Working | Speech I/O, whisper bridge |
| `vision` | ✅ Working | Camera, image analysis |
| `persona` | ✅ Working | Personality system |
| `cognitive` | ✅ Working | Reasoning engine |
| `automation` | ✅ Working | Task automation rules |
| `agent` | ✅ Working | Accessibility agent |
| `system` | ✅ Working | System integration |
| `connectivity` | ✅ Working | Network monitoring |
| `security` | ✅ Working | App security features |
| `productivity` | ✅ Working | Timers, reminders |
| `selfmodify` | ✅ Working | Self-improvement logic |
| `ui` | ✅ Working | Main UI shell |

### Native Modules (`native:*`)

| Module | Language | Purpose | Status |
|--------|----------|---------|--------|
| `whisper` | C++/Kotlin | Local STT | ✅ Graceful fallback |
| `llamacpp` | C++/Kotlin | Local LLM | ✅ Optional |
| `ecapa` | C++/Kotlin | Speaker verification | ✅ Optional |
| `wakeword` | C++/Kotlin | Wake word detection | ✅ Optional |

> **Note:** Native modules are optional. If not built, the app uses cloud alternatives gracefully.

---

## ⚙️ Configuration

### Environment Variables

Create `secrets.gradle` (gitignored) or set in CI:

```properties
# LLM API Keys (at least one required)
NOUS_OPENAI_API_KEY=sk-...
NOUS_ANTHROPIC_API_KEY=sk-ant-...
NOUS_GEMINI_API_KEY=AIza...
NOUS_GROQ_API_KEY=gsk_...
NOUS_OPENROUTER_API_KEY=sk-or-...

# Firebase (optional, for analytics/crashlytics)
# Add google-services.json to app/src/
```

### Build Customization

Edit `gradle.properties`:

```properties
# Change application ID
nous.applicationId=com.your.company.nous

# Change version
nous.versionCode=2026080601
nous.versionName="0.2.0"

# Min/target SDK
nous.minSdk=29
nous.targetSdk=36
```

---

## 🔧 Troubleshooting

### Common Build Issues

#### 1. "OutOfMemoryError" during build

**Solution:** Increase Gradle memory in `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m
```

Or use GitHub Actions (7GB+ RAM available).

#### 2. "Native code compilation failed"

**Solution:** Ensure NDK and CMake are installed:
```bash
sdkmanager "ndk;27.1.12297006"
sdkmanager "cmake;3.22.1"
```

#### 3. "Database migration failed"

**Solution:** The app handles this automatically. If issues persist:
```bash
# Clear app data on device/emulator
adb pm clear com.roshan.persona
```

#### 4. "Whisper/LLM not working"

**Solution:** Native modules require pre-built `.so` files:
- Build with NDK, OR
- Download pre-built libraries, OR
- App will use cloud APIs as fallback

### Runtime Issues

#### App crashes on open

Check these fixes already applied:
1. ✅ `@Singleton` annotation fixed in `AppInitializersImpl.kt`
2. ✅ Database `Migrations.kt` rewritten properly
3. ✅ Global exception handler in `NousApplication.kt`
4. ✅ Safe mode fallback UI in `MainActivity.kt`

If still crashing:
```bash
# Get crash log
adb logcat -s NOUS.* *:E > crash.log
# Share crash.log for debugging
```

---

## 🤝 Contributing

We welcome contributions! Please follow these steps:

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. Open a **Pull Request**

### Code Style
- Kotlin official style guide
- meaningful variable/function names
- KDoc comments for public APIs
- Unit tests for new features

### Commit Messages
Follow Conventional Commits:
- `feat:` New feature
- `fix:` Bug fix
- `docs:` Documentation
- `style:` Formatting
- `refactor:` Code restructuring
- `test:` Adding tests
- `chore:` Maintenance

---

## 📄 License

Proprietary License — see [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- [whisper.cpp](https://github.com/ggerganov/whisper.cpp) - Speech recognition
- [llama.cpp](https://github.com/ggml-org/llama.cpp) - LLM inference
- [Hilt](https://dagger.dev/hilt/) - Dependency injection
- [Compose](https://developer.android.com/jetpack/compose) - UI toolkit
- [Room](https://developer.android.com/jetpack/room) - Database

---

## 📞 Support

- **Issues:** [GitHub Issues](../../issues)
- **Discussions:** [GitHub Discussions](../../discussions)
- **Email:** roshan@example.com

---

<p align="center">
  <strong>NOUS — The Mind That Perceives</strong><br>
  <em>Built with ❤️ using Kotlin, Jetpack Compose, and modern Android architecture</em>
</p>
