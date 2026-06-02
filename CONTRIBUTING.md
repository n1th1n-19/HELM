<p align="center">
  <img src="assets/helm.png" alt="HELM" width="140"/>
</p>

# Contributing to HELM

## Project Structure

```
helm/
├── android/     # Android app (Kotlin, Jetpack Compose, Material 3)
├── agent/       # Desktop agent (Rust, Tokio, WebSocket)
├── protocol/    # WebSocket message schema (JSON Schema)
├── docs/        # Architecture and setup docs
├── assets/      # Logo and brand assets
└── install.sh   # One-liner installer
```

## Development Setup

### Desktop Agent

Requirements: Rust 1.75+, Linux

```bash
cd agent
cargo build
cargo test
cargo clippy
cargo fmt
```

### Android App

Requirements: JDK 17+, Android Studio or `sdkmanager`, Android SDK 35

Open `android/` in Android Studio, or:

```bash
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Protocol

The WebSocket protocol is in `protocol/schema/`. Changing the protocol requires updating schema files, the agent (`protocol.rs`), and the Android app (`HelmModels.kt`) in sync.

See [docs/architecture.md](docs/architecture.md) for the full message flow.

## Pull Requests

- Keep PRs focused on a single concern
- Add tests for new functionality
- Follow existing code style
- Update docs for user-facing changes
- Run `cargo fmt` and `cargo clippy` before submitting

## Code Style

- **Rust:** `cargo fmt` + `cargo clippy --all-targets -- -D warnings`
- **Kotlin:** standard Android Studio formatter

## License

By contributing you agree your contributions will be licensed under MIT.
