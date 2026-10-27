package dev.schematicbuilder;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public final class SchemCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("schem")
                // Nur OPs / Cheats. Falls das nicht kompiliert: siehe Hinweis in der Antwort.
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list").executes(SchemCommand::list))
                .then(Commands.literal("load")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(fileNames(), b))
                                .executes(SchemCommand::load)))
                .then(Commands.literal("paste")
                        .executes(ctx -> paste(ctx, false))
                        .then(Commands.argument("ignoreAir", BoolArgumentType.bool())
                                .executes(ctx -> paste(ctx, BoolArgumentType.getBool(ctx, "ignoreAir")))))
                .then(Commands.literal("undo").executes(SchemCommand::undo))
                .then(Commands.literal("cancel").executes(SchemCommand::cancel)));
    }

    private static List<String> fileNames() {
        try (Stream<Path> s = Files.list(SchematicBuilder.schematicDir())) {
            return s.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".schem"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        List<String> names = fileNames();
        if (names.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    "Keine .schem-Dateien in " + SchematicBuilder.schematicDir()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Schematics: " + String.join(", ", names)), false);
        return names.size();
    }

    private static int load(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        if (!name.endsWith(".schem")) name += ".schem";

        Path dir = SchematicBuilder.schematicDir().toAbsolutePath().normalize();
        Path file = dir.resolve(name).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
            src.sendFailure(Component.literal("Datei nicht gefunden: " + name));
            return 0;
        }
        try {
            Schematic s = Schematic.load(file, src.getServer().registryAccess().lookupOrThrow(Registries.BLOCK));
            PasteManager.setLoaded(player.getUUID(), s);
            src.sendSuccess(() -> Component.literal(
                    "Geladen: " + s.width + "x" + s.height + "x" + s.length + " (" + s.volume() + " Bloecke)"), false);
            return 1;
        } catch (IOException e) {
            SchematicBuilder.LOGGER.error("Laden fehlgeschlagen", e);
            src.sendFailure(Component.literal("Laden fehlgeschlagen: " + e.getMessage()));
            return 0;
        }
    }

    private static int paste(CommandContext<CommandSourceStack> ctx, boolean ignoreAir) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        UUID id = player.getUUID();
        Schematic s = PasteManager.getLoaded(id);
        if (s == null) {
            src.sendFailure(Component.literal("Erst /schem load <name> ausfuehren."));
            return 0;
        }
        if (PasteManager.isBusy(id)) {
            src.sendFailure(Component.literal("Es laeuft schon ein Job. /schem cancel zum Abbrechen."));
            return 0;
        }
        BlockPos origin = player.blockPosition();
        PasteManager.startPaste(id, src.getLevel(), origin, s, ignoreAir);
        src.sendSuccess(() -> Component.literal("Starte Paste bei " + origin.toShortString() + " ..."), false);
        return 1;
    }

    private static int undo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        UUID id = src.getPlayerOrException().getUUID();
        if (PasteManager.isBusy(id)) {
            src.sendFailure(Component.literal("Es laeuft noch ein Job."));
            return 0;
        }
        if (!PasteManager.startUndo(id)) {
            src.sendFailure(Component.literal("Nichts zum Rueckgaengigmachen."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Mache letzten Paste rueckgaengig ..."), false);
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        UUID id = ctx.getSource().getPlayerOrException().getUUID();
        int n = PasteManager.cancel(id);
        ctx.getSource().sendSuccess(() -> Component.literal(n + " Job(s) abgebrochen."), false);
        return n;
    }

    private SchemCommand() {}
}
