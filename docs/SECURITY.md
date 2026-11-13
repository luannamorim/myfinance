# MyFinance — Security model

MyFinance stores highly sensitive information. This document states exactly what is protected, how, and what is not.

## Threat model

| Threat | Protection |
|---|---|
| Lost or stolen phone, locked | Android file-based encryption (credential-encrypted storage) keeps the app's files unreadable until the device is unlocked. |
| Another app reading MyFinance data | Android app sandbox: the database, settings and images live in private app storage. |
| Cloud copies of app data | Android auto backup and device-to-device transfer are disabled (`allowBackup="false"`, extraction rules exclude everything). |
| Someone obtaining a backup file (cloud folder, shared drive) | Backups are encrypted and authenticated with a key derived from the user's recovery password. |
| Tampered or corrupted backup | Authenticated encryption, SHA-256 per entry, strict validation before restore; local data is untouched on failure. |
| Network observers | The only request is an HTTPS GET for public exchange rates; it carries a date range and nothing about the user. Cleartext traffic is disabled. |
| Data leaking through logs | Amounts, names and notes are never logged; R8 strips `android.util.Log` calls from release builds. |

Out of scope: a compromised or rooted device, malware with accessibility or root privileges, someone with the unlocked phone in hand. MyFinance does not add an app lock or an encrypted local database (see "Decisions").

## What is encrypted

| Data | Encrypted by MyFinance? | Protection |
|---|---|---|
| Backup files (`.myfinance`) | **Yes** | AES-256-GCM streaming (Tink), key from PBKDF2-HMAC-SHA256 (600,000 iterations) of the recovery password |
| Backup key on the device | **Yes** | Wrapped by a non-exportable AES-256 key in the Android Keystore |
| Local database (Room) | No | App sandbox + Android file-based encryption |
| Settings (DataStore) | No | App sandbox + Android file-based encryption |
| Asset images | No | App sandbox + Android file-based encryption |
| CSV exports | **No** | Written where the user chooses; the app warns before exporting |

The recovery password itself is never stored. Losing both the device and the password makes backups unrecoverable, by design.

## Keys

- The recovery password is normalized to NFKC and fed to PBKDF2-HMAC-SHA256 with a random 16-byte salt and 600,000 iterations, producing a 256-bit key.
- A 16-byte key-check value (HKDF of the key with the info string `myfinance-kcv`) is stored in the backup header so a wrong password is told apart from a corrupted file. The key itself never appears in the file.
- On the device, the derived key is wrapped with AES-GCM by the Keystore key `myfinance_backup_wrap`, which does not require user authentication so scheduled backups can run while the phone is locked. If the Keystore key disappears (reinstall, cleared data), the state becomes *locked* and the app asks for the password again.
- Restoring a file created with weaker parameters (fewer iterations) re-derives a fresh key instead of adopting them.

## Network

- Permissions declared by the app: `INTERNET` (exchange rates) and `POST_NOTIFICATIONS` (backup failure). Merged from WorkManager: `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`; merged from AndroidX: the app-private `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. The release checklist verifies this exact list.
- Destination: `https://api.bcb.gov.br/dados/serie/bcdata.sgs.1/dados` only. The request contains `formato=json`, `dataInicial` and `dataFinal`, a generic `User-Agent: MyFinance` and no identifiers, cookies or financial data.
- `network_security_config.xml` disables cleartext traffic. Responses are size-limited (1 MiB), parsed strictly and range-checked; invalid data is ignored and never replaces manual rates.
- Automatic fetching can be turned off in *Settings → Currency*; the app then makes no request on its own, and fetches only when the user taps *Update*. Manual rates always work.

## Imported files

- Backup files are validated before any local data changes: header, key-check value, authenticated decryption of the whole stream, zip entry count and size limits, no duplicate or unexpected entries, SHA-256 and size per entry, format version, record counts, and image paths restricted to `images/<dir>/<file>` (no `..`, no absolute paths).
- Restore replaces every table in a single database transaction; if anything fails the previous data stays intact.

## Logging and diagnostics

- No analytics, crash reporting or telemetry SDKs.
- Code never logs amounts, names, notes, categories, file names of backups or exchange of financial data. Errors are logged by type only.
- Release builds strip `android.util.Log` via `-assumenosideeffects`.

## Secrets and repository hygiene

- No API keys are needed (the BCB API is keyless).
- Release signing credentials are read from `~/.gradle/gradle.properties` (`MYFINANCE_*`) and never committed; `*.jks`, `*.keystore`, backup archives (`*.myfinance`), databases and exports are git-ignored.
- Screenshots and fixtures use synthetic data only.

## Decisions

- **No encrypted local database (SQLCipher):** Android's file-based encryption already protects data at rest on a locked device. SQLCipher would add ~7–10 MB of native libraries, slower queries and a more complex restore and migration path for protection that matters mainly on compromised devices.
- **No app lock or screenshot blocking:** chosen by the user; the phone's own lock screen is the access control.

## Reporting a vulnerability

Please open a private security advisory on GitHub (*Security → Report a vulnerability*) instead of a public issue.
