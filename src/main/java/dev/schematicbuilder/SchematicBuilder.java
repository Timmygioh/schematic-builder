package dev.schematicbuilder;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SchematicBuilder implements ModInitializer {
    public static final String MOD_ID = "schematicbuilder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Ordner, aus dem .schem-Dateien geladen werden: <spielordner>/schematics */
    public static Path schematicDir() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("schematics");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("Konnte Schematics-Ordner nicht anlegen", e);
        }
        return dir;
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> SchemCommand.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(server -> PasteManager.tick());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PasteManager.clear());
        LOGGER.info("Schematic Builder geladen. Ordner: {}", schematicDir());
    }
}
