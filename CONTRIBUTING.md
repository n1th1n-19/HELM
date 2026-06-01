# Contributing to HELM

Thank you for your interest in contributing!

## Project Structure

- `android/` — Android app (Kotlin, Jetpack Compose, Material 3)
- `agent/` — Desktop agent (Rust, Tokio, WebSocket server)
- `protocol/` — WebSocket message schema definitions
- `docs/` — Documentation
- `plugins/` — Plugin system (V2, coming soon)

## Development Setup

### Desktop Agent

Requirements: Rust 1.75+, Linux

```bash
cd agent
cargo build
cargo test
```

### Android App

Requirements: Android Studio, JDK 17+, Android SDK 34

Open `android/` in Android Studio.

## Protocol

The WebSocket protocol is defined in `protocol/schema/`. If you change the protocol, update the schema files and both the agent and Android app.

## Pull Requests

- Keep PRs focused on a single concern
- Add tests for new functionality
- Follow existing code style
- Update documentation for user-facing changes

## Code Style

- Rust: `cargo fmt` + `cargo clippy`
- Kotlin: `ktlint`

## License

By contributing you agree your contributions will be licensed under MIT.
