# Contributing

MyFinance is a personal project, but issues and pull requests are welcome.

## Before you start

- Read [`SPEC.md`](SPEC.md) and [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). Financial rules (recurrence, balances, returns, conversion) are defined there; changes to them need a discussion first.
- Security issues: please use a private security advisory, not a public issue (see [`docs/SECURITY.md`](docs/SECURITY.md)).

## Development

Requirements: JDK 21 and the Android SDK (platform 37, build-tools 37.0.0).

```bash
export JAVA_HOME=/path/to/jdk-21 ANDROID_HOME=/path/to/Android/Sdk
./gradlew testDebugUnitTest      # all tests (JVM + Robolectric)
./gradlew recordRoborazziDebug   # screenshots in app/build/outputs/roborazzi/
./gradlew lintDebug
```

## Rules

- Money is `Long` minor units plus a currency. Never use `Double` for money.
- Financial calculations live in `domain/` (pure Kotlin) and come with tests that use hand-computed values.
- Every user-visible string goes in `res/values/strings.xml` and `res/values-pt-rBR/strings.xml`.
- Never log amounts, names, notes or categories. Never commit real financial data, databases, backups or signing keys.
- Any Room schema change needs a migration and a `MigrationTest` case.
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/) in English.
- Screenshots and fixtures use synthetic data only.
