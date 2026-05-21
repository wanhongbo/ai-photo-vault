# LumaNox iOS Data Model Layer

## Goal

The encrypted files under `Documents/vault_albums/` remain the source of truth. The iOS data model layer adds a local metadata index so vault search, storage usage, export, AI scans, and future album operations can share one consistent read model without changing the encrypted file or backup package format.

## Storage

- Location: `Application Support/LumaNox/vault_metadata_v1.json`
- Format: versioned JSON, written atomically through a temporary file
- Rebuild strategy: reconcile by scanning `vault_albums/` and `vault_trash/`
- Compatibility: metadata is local-only and can be regenerated from encrypted files

## Core Models

| Model | Purpose |
|---|---|
| `VaultMetadataSnapshot` | Versioned root object containing albums and media records |
| `VaultAlbumRecord` | Album identity, item count, and timestamps |
| `VaultMediaRecord` | Stable media metadata for active and trashed files |
| `VaultAiMetadata` | Placeholder-ready AI scan metadata for sensitive score, cleanup score, category, and tags |
| `VaultStorageSummary` | Aggregated storage counts and encrypted byte size |

## Current Integrations

- `VaultStore.loadSnapshot()` reconciles metadata before publishing UI state.
- `VaultStore.photos(in:)` reads album media from metadata.
- `VaultStore.searchPhotos(query:)` searches all indexed active media, not only recent items.
- Trash listing and purge refresh the metadata index.
- Storage usage now reads real metadata counts and encrypted file size.

## Next Consumers

- AI cleanup/classification should update `VaultAiMetadata` by `VaultMediaRecord.id`.
- Batch export should query selected `VaultMediaRecord`s, decrypt their `storagePath`, and write/share plaintext copies.
- Legal language and app settings can later move to the same data-layer pattern if they need persisted user preferences.
