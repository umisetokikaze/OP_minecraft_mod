package io.github.umisetokikaze.foundation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.umisetokikaze.Config;
import io.github.umisetokikaze.foundation.cache.CacheModuleId;
import io.github.umisetokikaze.foundation.cache.CacheStatusSnapshot;
import io.github.umisetokikaze.foundation.cache.SafeCacheLayer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class CacheCommands {
    private CacheCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ProfilingFoundation foundation) {
        dispatcher.register(Commands.literal("momooptimizer")
                .then(Commands.literal("cache")
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource(), foundation)))
                        .then(Commands.literal("purge")
                                .executes(context -> purge(context.getSource(), foundation, Optional.empty()))
                                .then(Commands.argument("module", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            Arrays.stream(CacheModuleId.values()).map(CacheModuleId::id).forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> purge(
                                                context.getSource(),
                                                foundation,
                                                Optional.of(parseModule(StringArgumentType.getString(context, "module")))))))
                        .then(Commands.literal("rebuild")
                                .executes(context -> rebuild(context.getSource(), foundation, Optional.empty()))
                                .then(Commands.argument("module", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            Arrays.stream(CacheModuleId.values()).map(CacheModuleId::id).forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> rebuild(
                                                context.getSource(),
                                                foundation,
                                                Optional.of(parseModule(StringArgumentType.getString(context, "module")))))))
                        .then(Commands.literal("fingerprint")
                                .executes(context -> fingerprint(context.getSource(), foundation)))
                        .then(Commands.literal("benchmark-log")
                                .executes(context -> benchmarkLog(context.getSource(), foundation)))));
    }

    private static int status(CommandSourceStack source, ProfilingFoundation foundation) {
        if (!Config.CACHE_COMMANDS_ENABLED.get()) {
            source.sendFailure(Component.literal("Cache commands are disabled by config."));
            return 0;
        }
        SafeCacheLayer cacheLayer = foundation.getSafeCacheLayer();
        Map<CacheModuleId, CacheStatusSnapshot> statuses = cacheLayer.statusSnapshot();
        source.sendSuccess(() -> Component.literal("Safe cache layer status:"), false);
        statuses.values().forEach(status -> source.sendSuccess(
                () -> Component.literal(status.module().id()
                        + " enabled=" + status.enabled()
                        + " quarantined=" + status.quarantined()
                        + " rebuildRequested=" + status.rebuildRequested()
                        + " bytes=" + status.bytesUsed()
                        + " entries=" + status.entryCount()
                        + " integrityState=" + status.lastIntegrityState()
                        + " integrityReason=" + status.lastIntegrityReasonCode()
                        + " integrityFailures=" + status.integrityFailureCount()
                        + " lastReason=" + status.lastReasonCode()
                        + " detail=" + status.lastDetail()),
                false));
        return statuses.size();
    }

    private static int purge(CommandSourceStack source, ProfilingFoundation foundation, Optional<CacheModuleId> module) {
        foundation.getSafeCacheLayer().purge(Optional.ofNullable(foundation.currentFingerprint()), module);
        source.sendSuccess(() -> Component.literal("Purged cache " + module.map(CacheModuleId::id).orElse("all")), true);
        return 1;
    }

    private static int rebuild(CommandSourceStack source, ProfilingFoundation foundation, Optional<CacheModuleId> module) {
        if (module.isPresent()) {
            foundation.getSafeCacheLayer().markRebuildRequested(module.get());
        } else {
            Arrays.stream(CacheModuleId.values()).forEach(foundation.getSafeCacheLayer()::markRebuildRequested);
        }
        source.sendSuccess(() -> Component.literal("Rebuild scheduled for " + module.map(CacheModuleId::id).orElse("all")), true);
        return 1;
    }

    private static int fingerprint(CommandSourceStack source, ProfilingFoundation foundation) {
        PackFingerprintSnapshot snapshot = foundation.currentFingerprint();
        if (snapshot == null) {
            source.sendFailure(Component.literal("Fingerprint is not available on this runtime."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "fingerprint=" + snapshot.fingerprint()
                        + " warmCold=" + snapshot.executionTemperature()
                        + " configDigest=" + snapshot.configInputsDigest()),
                false);
        return 1;
    }

    private static int benchmarkLog(CommandSourceStack source, ProfilingFoundation foundation) {
        source.sendSuccess(() -> Component.literal("benchmark=" + foundation.benchmarkDirectory()
                + " diagnostics=" + foundation.diagnosticsFile()), false);
        return 1;
    }

    private static CacheModuleId parseModule(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT);
        return Arrays.stream(CacheModuleId.values())
                .filter(module -> module.id().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown cache module: " + raw));
    }
}
