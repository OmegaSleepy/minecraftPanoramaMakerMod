package net.omega.minecraftpanorama;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import java.io.FileReader;
import java.io.File;
import java.util.List;

public class PanoramaCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("panorama").executes(context -> {
            File file = new File(Minecraft.getInstance().gameDirectory, "config/panorama_coords.json");

            if (!file.exists()) {
                context.getSource().sendFailure(Component.literal("JSON not found in config/panorama_coords.json"));
                return 0;
            }

            try (FileReader reader = new FileReader(file)) {
                List<Pos> positions = new Gson().fromJson(reader, new TypeToken<List<Pos>>(){}.getType());
                PanoramaHandler.startSequence(positions);
                context.getSource().sendSuccess(() -> Component.literal("Starting panorama capture..."), true);
            } catch (Exception e) {
                context.getSource().sendFailure(Component.literal("Error reading JSON."));
            }
            return 1;
        }));
    }

    public record Pos(double x, double y, double z) {}
}