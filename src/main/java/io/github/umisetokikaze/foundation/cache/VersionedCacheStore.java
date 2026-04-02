package io.github.umisetokikaze.foundation.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class VersionedCacheStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path rootDirectory;
    private final int schemaVersion;

    public VersionedCacheStore(Path rootDirectory, int schemaVersion) {
        this.rootDirectory = rootDirectory;
        this.schemaVersion = schemaVersion;
    }

    public <T> CacheLookupResult<T> read(
            CacheModuleId module,
            String dependencyDigest,
            String entryKey,
            CachePayloadCodec<T> codec) {
        Path metaPath = metaPath(module, dependencyDigest, entryKey);
        Path dataPath = dataPath(module, dependencyDigest, entryKey);
        if (!Files.exists(metaPath) || !Files.exists(dataPath)) {
            return CacheLookupResult.miss(InvalidationReason.ENTRY_MISSING, entryKey);
        }

        try {
            JsonObject metadataJson = JsonParser.parseString(Files.readString(metaPath, StandardCharsets.UTF_8)).getAsJsonObject();
            CacheEntryMetadata metadata = metadataFromJson(metadataJson);
            if (metadata.schemaVersion() != schemaVersion) {
                discardEntry(metaPath, dataPath);
                return CacheLookupResult.miss(InvalidationReason.SCHEMA_MISMATCH, entryKey, withIntegrityState(metadata, IntegrityState.INVALIDATED));
            }
            if (!metadata.dependencyDigest().equals(dependencyDigest)) {
                discardEntry(metaPath, dataPath);
                return CacheLookupResult.miss(InvalidationReason.FINGERPRINT_CHANGED, entryKey, withIntegrityState(metadata, IntegrityState.INVALIDATED));
            }
            if (!metadata.entryType().equals(codec.entryType())) {
                discardEntry(metaPath, dataPath);
                return CacheLookupResult.miss(InvalidationReason.ENTRY_TYPE_MISMATCH, entryKey, withIntegrityState(metadata, IntegrityState.INVALIDATED));
            }

            String payload = Files.readString(dataPath, StandardCharsets.UTF_8);
            String checksum = sha256Hex(payload);
            if (!checksum.equals(metadata.checksum())) {
                discardEntry(metaPath, dataPath);
                return CacheLookupResult.miss(InvalidationReason.CHECKSUM_MISMATCH, entryKey, withIntegrityState(metadata, IntegrityState.CORRUPT));
            }

            T decoded = codec.decode(JsonParser.parseString(payload));
            long now = Instant.now().toEpochMilli();
            CacheEntryMetadata refreshedMetadata = new CacheEntryMetadata(
                    metadata.schemaVersion(),
                    metadata.dependencyDigest(),
                    metadata.entryType(),
                    metadata.checksum(),
                    metadata.createdAtEpochMillis(),
                    now,
                    metadata.sizeBytes(),
                    IntegrityState.VALID);
            writeMetadata(metaPath, refreshedMetadata);
            return CacheLookupResult.hit(decoded, refreshedMetadata);
        } catch (RuntimeException | IOException exception) {
            CacheEntryMetadata metadata = readMetadataQuietly(metaPath);
            discardEntry(metaPath, dataPath);
            return CacheLookupResult.miss(
                    InvalidationReason.DESERIALIZE_FAILED,
                    exception.getClass().getSimpleName(),
                    metadata == null ? null : withIntegrityState(metadata, IntegrityState.CORRUPT));
        }
    }

    public <T> CacheEntryMetadata write(
            CacheModuleId module,
            String dependencyDigest,
            String entryKey,
            CachePayloadCodec<T> codec,
            T value) throws IOException {
        Path metaPath = metaPath(module, dependencyDigest, entryKey);
        Path dataPath = dataPath(module, dependencyDigest, entryKey);
        Files.createDirectories(dataPath.getParent());

        String payload = GSON.toJson(codec.encode(value));
        String checksum = sha256Hex(payload);
        long now = Instant.now().toEpochMilli();
        long sizeBytes = payload.getBytes(StandardCharsets.UTF_8).length;
        CacheEntryMetadata metadata = new CacheEntryMetadata(
                schemaVersion,
                dependencyDigest,
                codec.entryType(),
                checksum,
                now,
                now,
                sizeBytes,
                IntegrityState.VALID);

        atomicWriteString(dataPath, payload);
        writeMetadata(metaPath, metadata);
        return metadata;
    }

    public void purge(CacheModuleId module) throws IOException {
        Path modulePath = modulePath(module);
        if (Files.exists(modulePath)) {
            deleteRecursively(modulePath);
        }
    }

    public void purgeFingerprint(CacheModuleId module, String fingerprint) throws IOException {
        Path fingerprintPath = fingerprintPath(module, fingerprint);
        if (Files.exists(fingerprintPath)) {
            deleteRecursively(fingerprintPath);
        }
    }

    public UsageStats usage(CacheModuleId module) {
        Path modulePath = modulePath(module);
        if (!Files.exists(modulePath)) {
            return new UsageStats(0L, 0L);
        }
        try (Stream<Path> stream = Files.walk(modulePath)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();
            long bytes = 0L;
            for (Path file : files) {
                bytes += Files.size(file);
            }
            return new UsageStats(bytes, files.size() / 2L);
        } catch (IOException exception) {
            return new UsageStats(0L, 0L);
        }
    }

    public List<String> evictLeastRecentlyUsed(long maxBytes, Optional<CacheModuleId> moduleFilter) throws IOException {
        List<EntryOnDisk> entries = listEntries(moduleFilter);
        long totalBytes = entries.stream().mapToLong(EntryOnDisk::sizeBytes).sum();
        List<String> evicted = new ArrayList<>();
        if (totalBytes <= maxBytes) {
            return evicted;
        }

        entries.sort(Comparator.comparingLong(EntryOnDisk::lastUsedAtEpochMillis));
        for (EntryOnDisk entry : entries) {
            if (totalBytes <= maxBytes) {
                break;
            }
            deleteQuietly(entry.metaPath());
            deleteQuietly(entry.dataPath());
            totalBytes -= entry.sizeBytes();
            evicted.add(entry.module().id() + ":" + entry.entryKey());
        }
        return evicted;
    }

    private List<EntryOnDisk> listEntries(Optional<CacheModuleId> moduleFilter) throws IOException {
        List<EntryOnDisk> entries = new ArrayList<>();
        List<CacheModuleId> modules = moduleFilter.map(List::of).orElse(List.of(CacheModuleId.values()));
        for (CacheModuleId module : modules) {
            Path modulePath = modulePath(module);
            if (!Files.exists(modulePath)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(modulePath)) {
                for (Path metaPath : stream.filter(path -> path.getFileName().toString().endsWith(".meta.json")).toList()) {
                    JsonObject metadataJson = JsonParser.parseString(Files.readString(metaPath, StandardCharsets.UTF_8)).getAsJsonObject();
                    CacheEntryMetadata metadata = metadataFromJson(metadataJson);
                    String fileName = metaPath.getFileName().toString();
                    String entryKey = fileName.substring(0, fileName.length() - ".meta.json".length());
                    entries.add(new EntryOnDisk(
                            module,
                            entryKey,
                            metadata.lastUsedAtEpochMillis(),
                            metadata.sizeBytes(),
                            metaPath,
                            metaPath.resolveSibling(entryKey + ".data.json")));
                }
            }
        }
        return entries;
    }

    private void writeMetadata(Path path, CacheEntryMetadata metadata) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", metadata.schemaVersion());
        json.addProperty("dependencyDigest", metadata.dependencyDigest());
        json.addProperty("entryType", metadata.entryType());
        json.addProperty("checksum", metadata.checksum());
        json.addProperty("createdAtEpochMillis", metadata.createdAtEpochMillis());
        json.addProperty("lastUsedAtEpochMillis", metadata.lastUsedAtEpochMillis());
        json.addProperty("sizeBytes", metadata.sizeBytes());
        json.addProperty("integrityState", metadata.integrityState().name());
        atomicWriteString(path, GSON.toJson(json));
    }

    private CacheEntryMetadata metadataFromJson(JsonObject json) {
        return new CacheEntryMetadata(
                json.get("schemaVersion").getAsInt(),
                json.has("dependencyDigest") ? json.get("dependencyDigest").getAsString() : json.get("fingerprint").getAsString(),
                json.get("entryType").getAsString(),
                json.get("checksum").getAsString(),
                json.get("createdAtEpochMillis").getAsLong(),
                json.get("lastUsedAtEpochMillis").getAsLong(),
                json.get("sizeBytes").getAsLong(),
                IntegrityState.valueOf(json.get("integrityState").getAsString()));
    }

    private CacheEntryMetadata readMetadataQuietly(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            JsonObject metadataJson = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            return metadataFromJson(metadataJson);
        } catch (RuntimeException | IOException exception) {
            return null;
        }
    }

    private CacheEntryMetadata withIntegrityState(CacheEntryMetadata metadata, IntegrityState integrityState) {
        return new CacheEntryMetadata(
                metadata.schemaVersion(),
                metadata.dependencyDigest(),
                metadata.entryType(),
                metadata.checksum(),
                metadata.createdAtEpochMillis(),
                metadata.lastUsedAtEpochMillis(),
                metadata.sizeBytes(),
                integrityState);
    }

    private void discardEntry(Path metaPath, Path dataPath) {
        deleteQuietly(metaPath);
        deleteQuietly(dataPath);
    }

    private void atomicWriteString(Path path, String content) throws IOException {
        Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tempPath, content, StandardCharsets.UTF_8);
        try {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path modulePath(CacheModuleId module) {
        return schemaRoot().resolve(module.id());
    }

    private Path fingerprintPath(CacheModuleId module, String dependencyDigest) {
        return modulePath(module).resolve(dependencyDigest);
    }

    private Path metaPath(CacheModuleId module, String dependencyDigest, String entryKey) {
        return fingerprintPath(module, dependencyDigest).resolve(entryKey + ".meta.json");
    }

    private Path dataPath(CacheModuleId module, String dependencyDigest, String entryKey) {
        return fingerprintPath(module, dependencyDigest).resolve(entryKey + ".data.json");
    }

    private Path schemaRoot() {
        return rootDirectory.resolve("schema-v" + schemaVersion);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    private String sha256Hex(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Missing SHA-256 support", exception);
        }
    }

    public record UsageStats(long bytesUsed, long entryCount) {
    }

    private record EntryOnDisk(
            CacheModuleId module,
            String entryKey,
            long lastUsedAtEpochMillis,
            long sizeBytes,
            Path metaPath,
            Path dataPath) {
    }
}
