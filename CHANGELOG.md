# Changelog

## 0.2.0

- Added the Android `PROCESS_TEXT` action labeled `RUN CODE ON TERMUX` for selected text in compatible apps. Commands are previewed and confirmed before execution.
- Added a full-screen Tools Center for arbitrary multi-line Shell commands, custom working directories, `pkg`, Python `pip`, Node `npm`, Rust `cargo`, Ruby `gem`, Go tools, runtime inventory, system diagnostics, and archive operations.
- Added automatic launch commands for more common programming-language extensions plus a custom-runner fallback.
- Added terminal-editor action (`nano` / `vi`) for larger files, and increased the built-in UTF-8 editor limit to 256 KiB.
- Improved directory/search transitions and ignored stale results from earlier requests to reduce navigation glitches.
