# MyFinance — Backup format and recovery

MyFinance has no server. Backups are encrypted files written to a folder the user chooses; a folder-sync app (Google Drive, Dropbox, Syncthing…) can carry that folder to the cloud. MyFinance never talks to a cloud provider.

## File

Name: `myfinance-backup-YYYYMMDD-HHmmss.myfinance` (local time).

```
"MYFINBAK" | u16 version | u8 kdf | u32 iterations | 16 B salt | 16 B key-check | u64 createdAtMs    (55 bytes, big-endian)
streaming AES-256-GCM-HKDF (Tink, 1 MiB segments, the whole header as associated data)
  └─ zip: manifest.json   formatVersion, app version, createdAt, db schema version, counts, SHA-256 + size per entry
          data.json       BackupDataV1 (independent of the Room schema)
          settings.json   AppSettings
          images/assets/<uuid>.jpg   (stored, not recompressed)
```

- `version` = 1, `kdf` = 1 (PBKDF2-HMAC-SHA256), `iterations` = 600,000 by default.
- Changing any header byte (including `createdAtMs`) makes decryption fail.
- JSON entries are deflated; images are stored as-is.

### `data.json` (BackupDataV1)

Flat lists mirroring the tables: `categories`, `templates`, `occurrences`, `adjustments`, `assetCategories`, `assets`, `assetFlows`, `valuations`, `exchangeRates`. IDs are kept; money is `Long` minor units plus a currency code (flows and valuations use their asset's currency); calendar dates are epoch days (`Long`); instants are epoch milliseconds (`Long`); exchange rates are `rateMicros` (`Long`, rate × 10⁶); quantities are decimal strings; enums are stored by name; predefined categories that were never renamed keep a `null` name.

Field names are part of the file format: they are never renamed. New fields are added as optional with defaults, so older backups keep restoring.

## When backups run

- **Daily**, when battery and storage are not low.
- **10 minutes after a change** (debounced), so a burst of edits produces one file.
- **Back up now** in *Settings → Backup*.
- A change counter records what the last backup covered; a change made during a backup stays pending for the next one.

Before the first backup the user must set a recovery password. Until a folder and a password are set, automatic runs are skipped silently: that is "not configured", not a failure.

## Writing to the folder

1. Write `<name>.partial` through the Storage Access Framework (persistable folder permission).
2. Rename it to `<name>` (or copy and delete if the provider cannot rename).
3. Re-open the new file and **fully verify it**: decrypt the whole stream and validate the archive (manifest, hashes, counts).
4. Only then delete older backups beyond the 7 most recent, never the file just written. Files that don't match the name pattern are ignored.

Failures become a visible state (folder unavailable, no password, no space, unknown) and a notification.

## Restoring

From the folder list or from any file (*Restore from a file*):

1. The app reads only the header and shows the backup date; the user confirms that local data will be replaced.
2. The device key is used if the file's salt and iterations match; otherwise the app asks for the recovery password. The key-check value distinguishes "wrong password" from "corrupted file".
3. The whole file is decrypted to the app cache (truncation and tampering are only detected at the end) and the archive is validated: entry limits, no zip-slip, exact entry set, size and SHA-256 per entry, format version, counts, image paths.
4. New images are copied to `images/` (UUID names, so current ones stay in place).
5. All tables are replaced in **one database transaction**, settings are imported, then images no longer referenced are deleted.
6. Counts and images are re-validated and the occurrence horizon is regenerated.

If the app dies at any point, the database is either the old one or the new one, with all its images; leftovers are cleaned on the next start.

## Recovering on a new phone

1. Install MyFinance.
2. Make the backup folder available on the phone (sync app) or copy a `.myfinance` file to it.
3. *Settings → Backup → Choose folder* (then tap a backup) or *Restore from a file*.
4. Confirm the date and type the recovery password.
5. Set the folder again for future backups if needed.

## CSV export

*Settings → Export CSV* writes a zip with `transactions.csv`, `assets.csv`, `asset_flows.csv`, `valuations.csv` and `exchange_rates.csv`:

- UTF-8, comma separated, header row, RFC 4180 quoting.
- Dates in ISO-8601 (`2026-11-12`), amounts as plain decimals with a dot (`2500.00`) and currency columns (`transactions.csv` has `currency` for the expected amount and `actual_currency` for the actual one).
- Text cells starting with `=`, `+`, `-` or `@` are prefixed with `'` to prevent formula injection in spreadsheets.
- **The export is not encrypted**; the app warns before writing it.

CSV is for reading the data elsewhere. Restoring the app always uses the encrypted backup.
