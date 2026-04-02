package io.github.umisetokikaze.foundation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.umisetokikaze.Config;
import io.github.umisetokikaze.foundation.cache.CacheModuleId;
import io.github.umisetokikaze.foundation.cache.CacheResolution;
import io.github.umisetokikaze.foundation.cache.CacheStatusSnapshot;
import io.github.umisetokikaze.foundation.cache.ModuleCacheResolution;
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
        if (!Config.CACHE_COMMANDS_ENABLED.get()) {
            return;
        }
        dispatcher.register(Commands.literal("momooptimizer")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("cache")
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource(), foundation, Optional.empty()))
                                .then(Commands.argument("module", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            Arrays.stream(CacheModuleId.values()).map(CacheModuleId::id).forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> status(
                                                context.getSource(),
                                                foundation,
                                                Optional.of(parseModule(StringArgumentType.getString(context, "module")))))))
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

    private static int status(CommandSourceStack source, ProfilingFoundation foundation, Optional<CacheModuleId> moduleFilter) {
        SafeCacheLayer cacheLayer = foundation.getSafeCacheLayer();
        Map<CacheModuleId, CacheStatusSnapshot> statuses = cacheLayer.statusSnapshot();
        source.sendSuccess(() -> Component.literal("Safe cache layer status:"), false);
        statuses.values().stream()
                .filter(status -> moduleFilter.map(module -> module == status.module()).orElse(true))
                .forEach(status -> source.sendSuccess(
                () -> Component.literal(status.module().id()
                        + " enabled=" + status.enabled()
                        + " quarantined=" + status.quarantined()
                        + " rebuildRequested=" + status.rebuildRequested()
                        + " bytes=" + status.bytesUsed()
                        + " entries=" + status.entryCount()
                        + " budgetMiB=" + status.budgetMiB()
                        + " eviction=" + status.evictionPolicy()
                        + " compatibility=" + status.compatibilityMode()
                        + " debug=" + status.debugLogging()
                        + " overBudget=" + status.overBudget()
                        + " integrityState=" + status.lastIntegrityState()
                        + " integrityReason=" + status.lastIntegrityReasonCode()
                        + " integrityFailures=" + status.integrityFailureCount()
                        + " lastReason=" + status.lastReasonCode()
                        + " detail=" + status.lastDetail()),
                false));
        moduleFilter.ifPresent(module -> cacheLayer.usageByDependencyDigest(module).forEach((digest, usage) -> source.sendSuccess(
                () -> Component.literal("  digest=" + digest + " bytes=" + usage.bytesUsed() + " entries=" + usage.entryCount()),
                false)));
        return (int) statuses.values().stream()
                .filter(status -> moduleFilter.map(module -> module == status.module()).orElse(true))
                .count();
    }

    private static int purge(CommandSourceStack source, ProfilingFoundation foundation, Optional<CacheModuleId> module) {
        foundation.getSafeCacheLayer().purge(Optional.ofNullable(foundation.currentFingerprint()), module);
        source.sendSuccess(() -> Component.literal("Purged cache " + module.map(CacheModuleId::id).orElse("all")), true);
        return 1;
    }

    private static int rebuild(CommandSourceStack source, ProfilingFoundation foundation, Optional<CacheModuleId> module) {
        SafeCacheLayer cacheLayer = foundation.getSafeCacheLayer();
        if (module.isPresent()) {
            if (!cacheLayer.markRebuildRequested(module.get())) {
                source.sendFailure(Component.literal("Rebuild is suppressed by safe compatibility mode for " + module.get().id()));
                return 0;
            }
        } else {
            long scheduled = Arrays.stream(CacheModuleId.values())
                    .filter(cacheLayer::markRebuildRequested)
                    .count();
            if (scheduled == 0L) {
                source.sendFailure(Component.literal("Rebuild is suppressed by safe compatibility mode for all cache modules."));
                return 0;
            }
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
        CacheResolution resolution = foundation.currentCacheResolution();
        source.sendSuccess(() -> Component.literal(
                "fingerprint=" + snapshot.fingerprint()
                        + " warmCold=" + snapshot.executionTemperature()
                        + " configDigest=" + snapshot.configInputsDigest()),
                false);
        if (resolution != null) {
            Arrays.stream(CacheModuleId.values()).forEach(module -> {
                ModuleCacheResolution moduleResolution = resolution.resolutionFor(module);
                source.sendSuccess(() -> Component.literal(
                        module.id()
                                + " dependencyDigest=" + moduleResolution.dependencyDigest()
                                + " reuseAllowed=" + moduleResolution.reuseAllowed()
                                + " reasons=" + moduleResolution.reasonDetail()),
                        false);
            });
        }
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
