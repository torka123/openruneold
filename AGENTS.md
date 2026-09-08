# OpenRune Persistence / Source of Truth Rules

- This project root is the canonical OpenRune development tree. Before changing gameplay, run `scripts/openrune-doctor.ps1` and confirm the running JVM reports this working directory.
- Never create a duplicate implementation when an authoritative implementation already exists. Search all handlers, plugins, commands, and modules first.
- Never edit `build/`, `out/`, `.gradle/`, `.kotlin/`, or other generated output. Change the source or the intentional generator instead.
- Never overwrite source from a startup or build task. Do not automatically restore source from Git.
- Never change the cache revision or cache path without explicitly reporting it. The active local cache is `.data/cache/SERVER`; `.data/cache/LIVE` supplies JS5 groups.
- Treat `.data/postgres` as the local embedded PostgreSQL authority for player inventories, banks, and realm spawn/respawn values. It is intentionally ignored and belongs to this project root only.
- Always inspect `git status` and `git diff` before and after major modifications. Preserve unrelated uncommitted work.
- Always verify the active runtime build after gameplay changes using the `OPENRUNE CUSTOM BUILD ACTIVE` startup marker and `scripts/openrune-doctor.ps1`.
- Changes must survive a controlled rebuild and two server restarts before they are considered verified.
- Keep one authoritative implementation for each gameplay feature. Duplicate command registration must be treated as an error, not a fallback mechanism.

# Standing Project Authorization

- The user explicitly grants standing approval for all commands and actions needed to carry out requested work in this OpenRune project, including future sessions. Proceed without repeatedly asking for conversational confirmation.
- This includes project inspection, implementation, dependency installation, testing, builds, debugging, cache maintenance, and controlled server restarts/deployment needed for the requested work. Continue independently when the user is away.
- Preserve the source-of-truth and persistence rules above and unrelated work. This authorization is scoped to work on this project.
- Use approved tool permissions and command rules wherever available. This document records user authorization; it does not override enforced sandbox, managed security, or tool approval requirements. Never claim a blocked action ran or approve an enforced prompt on the user's behalf.
