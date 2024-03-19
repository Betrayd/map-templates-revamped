package net.betrayd.map_templates_test.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.betrayd.map_templates.MapTemplateCreator;
import net.betrayd.map_templates.MapTemplateSerializer;
import net.betrayd.map_templates_test.MapTemplatesTest;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager.RegistrationEnvironment;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.server.command.ServerCommandSource;

import static net.minecraft.server.command.CommandManager.*;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;

public class ExportTemplateCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess,
            RegistrationEnvironment environment) {
        
        dispatcher.register(literal("export_template").then(
            argument("pos1", BlockPosArgumentType.blockPos()).then(
                argument("pos2", BlockPosArgumentType.blockPos()).then(
                    argument("name", IdentifierArgumentType.identifier()).executes(ExportTemplateCommand::execute)
                )
            )
        ));
    }

    private static int execute(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {

        BlockPos pos1 = BlockPosArgumentType.getBlockPos(context, "pos1");
        BlockPos pos2 = BlockPosArgumentType.getBlockPos(context, "pos2");
        
        Identifier name = IdentifierArgumentType.getIdentifier(context, "name");

        context.getSource().sendFeedback(() -> Text.literal("Capturing world with name ").append(Text.of(name)), false);

        ChunkSectionPos chunkPos1 = ChunkSectionPos.from(pos1);
        ChunkSectionPos chunkPos2 = ChunkSectionPos.from(pos2);

        MapTemplateCreator.compileWorld(context.getSource().getWorld(), chunkPos1, chunkPos2, e -> true, null)
                .thenAccept(template -> {
                    Path filename = MapTemplatesTest.getExportedTemplatePath(name);
                    context.getSource().sendFeedback(() -> Text.literal("Saving captured map template to " + filename), true);

                    try {
                        Files.createDirectories(filename.getParent());
                    } catch (IOException e1) {
                        throw new CompletionException(e1);
                    }
                    try(BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(filename))) {
                        MapTemplateSerializer.saveTo(template, out);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }

                }).exceptionally(e -> {
                    context.getSource().sendFeedback(() -> Text.literal("Unable to save map. See console for details."), false);
                    MapTemplatesTest.LOGGER.error("Unable to save map.", e);

                    return null;
                });

        return 1;
    }
    
}
