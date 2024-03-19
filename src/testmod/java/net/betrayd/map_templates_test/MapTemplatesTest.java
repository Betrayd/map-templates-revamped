package net.betrayd.map_templates_test;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.betrayd.map_templates_test.commands.ExportTemplateCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

public class MapTemplatesTest implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("map-templates-test");

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(ExportTemplateCommand::register);
    }

    public static Path getExportedTemplatePath(Identifier id) {
        return FabricLoader.getInstance().getGameDir()
                .resolve("templates").resolve(id.getNamespace()).resolve(id.getPath() + ".nbt");
    }
    
}
