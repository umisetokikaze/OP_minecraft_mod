package io.github.umisetokikaze.foundation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.umisetokikaze.Config;
import io.github.umisetokikaze.foundation.cache.CacheModuleDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.ModList;

public final class PackFingerprintService {
    private final ProfilingFoundation foundation;
    private final StageProfiler profiler;
    private final Path fingerprintDirectory;

    PackFingerprintService(ProfilingFoundation foundation, StageProfiler profiler, Path fingerprintDirectory) {
        this.foundation = foundation;
        this.profiler = profiler;
        this.fingerprintDirectory = fingerprintDirectory;
    }

    public PackFingerprintSnapshot capture() {
        return capture(null);
    }

    public PackFingerprintSnapshot capture(ResourceManager resourceManager) {
        Map<String, String> configInputs = Config.fingerprintInputs();
        Map<String, String> relevantFileHashes;
        String minecraftVersion;
        List<JsonObject> mods;
        List<JsonObject> packs;
        String neoForgeVersion;

        try (StageProfiler.StageScope ignored = profiler.begin("foundation.fingerprint.minecraft_version")) {
            Object currentVersion = SharedConstants.getCurrentVersion();
            minecraftVersion = invokeNoArgString(currentVersion, "getName")
                    .or(() -> invokeNoArgString(currentVersion, "getId"))
                    .orElseGet(currentVersion::toString);
        }

        try (StageProfiler.StageScope ignored = profiler.begin("foundation.fingerprint.loaded_mods")) {
            mods = collectMods();
        }

        neoForgeVersion = mods.stream()
                .filter(json -> "neoforge".equals(json.get("modId").getAsString()))
                .map(json -> json.get("version").getAsString())
                .findFirst()
                .orElse("unknown");

        try (StageProfiler.StageScope ignored = profiler.begin("foundation.fingerprint.resource_packs")) {
            packs = collectResourcePacks();
        }

        try (StageProfiler.StageScope ignored = profiler.begin("foundation.fingerprint.relevant_files")) {
            relevantFileHashes = collectRelevantFileHashes(resourceManager);
        }

        try (StageProfiler.StageScope ignored = profiler.begin("foundation.fingerprint.digest")) {
            String canonical = buildCanonicalInput(minecraftVersion, neoForgeVersion, mods, packs, relevantFileHashes, configInputs);
            String fingerprint = sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
            String configInputsDigest = sha256Hex(configInputs.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("\n"))
                    .getBytes(StandardCharsets.UTF_8));
            boolean warm = Files.exists(fingerprintDirectory.resolve(fingerprint + ".json"));
            return new PackFingerprintSnapshot(
                    fingerprint,
                    warm ? "warm" : "cold",
                    minecraftVersion,
                    neoForgeVersion,
                    mods,
                    packs,
                    relevantFileHashes,
                    configInputs,
                    CacheModuleDescriptor.schemaVersionsByModuleId(),
                    configInputsDigest);
        }
    }

    public void persistMarker(PackFingerprintSnapshot snapshot) {
        try {
            Files.createDirectories(fingerprintDirectory);
            Files.writeString(
                    fingerprintDirectory.resolve(snapshot.fingerprint() + ".json"),
                    snapshot.toJson().toString(),
                    StandardCharsets.UTF_8);
            Files.writeString(
                    fingerprintDirectory.resolve("latest.json"),
                    snapshot.toJson().toString(),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            foundation.recordInvalidation(snapshot, "foundation.pack_fingerprint", "MARKER_WRITE_FAILED", exception.getClass().getSimpleName());
            foundation.quarantine(snapshot, "foundation.pack_fingerprint", "IO_FAILURE", "marker-write-failed");
        }
    }

    public Optional<PackFingerprintSnapshot> loadLatestSnapshot() {
        Path latestPath = fingerprintDirectory.resolve("latest.json");
        if (!Files.exists(latestPath)) {
            return Optional.empty();
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(latestPath, StandardCharsets.UTF_8)).getAsJsonObject();
            return Optional.of(PackFingerprintSnapshot.fromJson(json));
        } catch (IOException | RuntimeException exception) {
            foundation.recordInvalidation("foundation.pack_fingerprint", "MARKER_READ_FAILED", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private List<JsonObject> collectMods() {
        List<JsonObject> mods = new ArrayList<>();
        for (Object modInfo : ModList.get().getMods()) {
            JsonObject json = new JsonObject();
            String modId = invokeNoArgString(modInfo, "getModId").orElse("unknown");
            String version = invokeNoArg(modInfo, "getVersion")
                    .map(Objects::toString)
                    .orElse("unknown");
            json.addProperty("modId", modId);
            json.addProperty("version", version);

            Path filePath = resolveModFilePath(modInfo).orElse(null);
            if (filePath != null) {
                json.addProperty("file", filePath.toString());
                json.addProperty("fileHash", safeHash(filePath));
            } else {
                json.addProperty("file", "unavailable");
                json.addProperty("fileHash", "unavailable");
            }
            mods.add(json);
        }
        mods.sort(Comparator.comparing(item -> item.get("modId").getAsString()));
        return mods;
    }

    private List<JsonObject> collectResourcePacks() {
        List<JsonObject> packs = new ArrayList<>();
        Object repository = getFieldValue(Minecraft.getInstance(), "packRepository").orElse(null);
        if (repository == null) {
            foundation.recordInvalidation("foundation.pack_fingerprint.resource_packs", "PACK_REPOSITORY_UNAVAILABLE", "");
            foundation.quarantine("foundation.pack_fingerprint.resource_packs", "INTEGRATION_FAILURE", "pack-repository-unavailable");
            return packs;
        }

        Object selected = invokeNoArg(repository, "getSelectedPacks").orElse(List.of());
        if (!(selected instanceof Iterable<?> iterable)) {
            return packs;
        }

        int order = 0;
        for (Object pack : iterable) {
            JsonObject json = new JsonObject();
            json.addProperty("order", order++);
            json.addProperty("id", invokeNoArgString(pack, "getId").orElse("unknown"));
            json.addProperty("title", invokeNoArg(pack, "getTitle").map(Objects::toString).orElse("unknown"));
            json.addProperty("source", invokeNoArg(pack, "getPackSource").map(Objects::toString).orElse("unknown"));
            json.addProperty("required", invokeNoArg(pack, "isRequired").map(Objects::toString).orElse("false"));
            packs.add(json);
        }
        return packs;
    }

    private Optional<Path> resolveModFilePath(Object modInfo) {
        return invokeNoArg(modInfo, "getOwningFile")
                .flatMap(this::unwrapOwningFile);
    }

    private Optional<Path> unwrapOwningFile(Object owningFile) {
        return invokeNoArg(owningFile, "getFile")
                .flatMap(fileObject -> invokeNoArg(fileObject, "getFilePath"))
                .filter(Path.class::isInstance)
                .map(Path.class::cast);
    }

    private Optional<Object> invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return Optional.ofNullable(method.invoke(target));
        } catch (ReflectiveOperationException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> invokeNoArgString(Object target, String methodName) {
        return invokeNoArg(target, methodName).map(Objects::toString);
    }

    private Optional<Object> getFieldValue(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return Optional.ofNullable(field.get(target));
        } catch (ReflectiveOperationException exception) {
            return Optional.empty();
        }
    }

    private String buildCanonicalInput(
            String minecraftVersion,
            String neoForgeVersion,
            List<JsonObject> mods,
            List<JsonObject> packs,
            Map<String, String> relevantFileHashes,
            Map<String, String> configInputs) {
        Map<String, String> canonical = new LinkedHashMap<>();
        canonical.put("minecraftVersion", minecraftVersion);
        canonical.put("neoForgeVersion", neoForgeVersion);
        canonical.put("mods", mods.stream().map(JsonObject::toString).collect(Collectors.joining("|")));
        canonical.put("resourcePacks", packs.stream().map(JsonObject::toString).collect(Collectors.joining("|")));
        canonical.put("relevantFileHashes", relevantFileHashes.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("|")));
        configInputs.forEach(canonical::put);
        return canonical.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    private Map<String, String> collectRelevantFileHashes(ResourceManager resourceManager) {
        if (resourceManager == null) {
            return Map.of();
        }

        List<PathMatcher> matchers = Config.RELEVANT_FINGERPRINT_PATHS.get().stream()
                .map(String::valueOf)
                .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern.replace('\\', '/')))
                .toList();
        Map<String, String> hashes = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Resource> entry : resourceManager.listResources("", path -> true).entrySet()) {
            String candidatePath = "assets/" + entry.getKey().getNamespace() + "/" + entry.getKey().getPath();
            if (!matchesRelevantPath(candidatePath, matchers)) {
                continue;
            }
            try (InputStream stream = entry.getValue().open()) {
                hashes.put(candidatePath, sha256Hex(stream.readAllBytes()));
            } catch (IOException exception) {
                hashes.put(candidatePath, "unavailable");
            }
        }
        return Map.copyOf(hashes);
    }

    private boolean matchesRelevantPath(String candidatePath, List<PathMatcher> matchers) {
        Path path = Path.of(candidatePath);
        for (PathMatcher matcher : matchers) {
            if (matcher.matches(path)) {
                return true;
            }
        }
        return false;
    }

    private String safeHash(Path filePath) {
        if (!Files.isRegularFile(filePath)) {
            return "unavailable";
        }
        try (InputStream stream = Files.newInputStream(filePath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            return "unavailable";
        }
    }

    private String sha256Hex(byte[] input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Missing SHA-256 support", exception);
        }
    }
}
