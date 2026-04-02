## What changed

This PR implements cache invalidation and the Cache Resolver flow for the client reload pipeline.

The main changes are:
- added a `CacheResolver` flow that compares the previous and current pack fingerprint snapshots and resolves cache reuse on a per-module basis
- introduced module-specific cache resolution types and descriptors so cache dependencies can be tracked independently for `resource_index` and `negative_lookup`
- extended pack fingerprint snapshots to include relevant file hashes, cache schema versions, and persisted `latest` snapshot metadata
- updated the reload listener to load the previous snapshot, resolve cache invalidation before reload, and pass the resolution into the resource index cache controller
- changed the persistent cache store and safe cache layer to use module dependency digests instead of a single global fingerprint/config digest key
- expanded diagnostics so cache resolution state, changed inputs, and per-module reuse decisions are visible in runtime output
- updated and added tests for the new snapshot shape, resolver behavior, and dependency-digest-based cache store usage

## Why these changes were made

The task for this branch was to detect cache-impacting changes and invalidate only the affected cache scope when:
- mod jars are updated
- resource packs are added, removed, or reordered
- relevant file contents change
- cache schema versions change
- cache-related settings change

Previously, cache reuse was effectively tied to a single fingerprint. That made it hard to express why invalidation happened and prevented unaffected cache modules from being reused when only unrelated inputs changed.

This PR introduces the Cache Resolver so reload can make explicit, module-level decisions about whether a cache can be reused or must be rebuilt.

## Important implementation details

- `PackFingerprintService` now captures relevant resource file hashes from the reload `ResourceManager`, not just pack-level metadata.
- the latest fingerprint snapshot is persisted and loaded back so the resolver can compare `previous -> current` state during reload
- `ResourceIndexCacheController` now accepts the resolver result and only attempts warm reuse when the resolved module dependencies match
- `VersionedCacheStore` metadata now keys entries by dependency digest, which allows module-level reuse even when the overall runtime fingerprint changes
- new invalidation reasons were added for first load, mod changes, pack changes, pack order changes, relevant file changes, settings changes, and schema changes
- diagnostics now include both top-level changed inputs and per-module resolution output for easier debugging

This PR was written using [Vibe Kanban](https://vibekanban.com)
