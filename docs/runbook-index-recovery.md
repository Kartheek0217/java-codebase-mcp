# Runbook: Index Recovery

This runbook defines operational steps for recovering from index corruption or mismatched metadata. It is written for local usage.

## Symptoms

- Search returns empty results for known files.
- Index writer throws exceptions on startup.
- Index directory cannot be opened due to corruption.

## Data Locations (Default)

- Lucene index: `./data/index` (planned)
- Metadata DB (H2): `./data/metadata` (planned)
- Sprint 0 reports: `./target/sprint0/*.json`

## Recovery Strategy (Planned)

1. Stop the process to ensure a single-writer policy.
2. Backup the current index directory and metadata database.
3. Validate whether metadata is consistent and complete enough to rebuild.
4. If the index is corrupted:
   - Rebuild Lucene from metadata.
5. If metadata is corrupted:
   - Re-run a full scan to reconstruct metadata, then rebuild the index.

## Safety Notes

- Always preserve a backup of the last known-good snapshot before deleting anything.
- Recovery must avoid partially written indexes; prefer rebuild into a new directory and then atomic rename.

