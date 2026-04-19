package net.omega.minecraftpanorama;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@EventBusSubscriber
public class PanoramaHandler {
    private static List<PanoramaCommand.Pos> queue;
    private static int currentPosIndex = -1;
    private static int currentFacing = 0;
    private static int tickCounter = 0;
    private static boolean readyToCapture = false;

    private static final int CHUNK_LOAD_WAIT = 100; // 5 seconds for world gen/loading
    private static final int ROTATION_WAIT = 15;   // 0.75 seconds for renderer to stabilize

    public static void startSequence(List<PanoramaCommand.Pos> positions) {
        queue = positions;
        currentPosIndex = 0;
        currentFacing = 0;
        readyToCapture = false;
        Minecraft.getInstance().options.fov().set(90);
        Minecraft.getInstance().options.hideGui = true;

        // Start by preparing the first position
        prepareNextState();
    }

    private static void prepareNextState() {
        if (queue == null || currentPosIndex >= queue.size()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        PanoramaCommand.Pos pos = queue.get(currentPosIndex);

        // 1. Teleport and Rotate FIRST
        float yaw = switch(currentFacing) {
            case 1 -> 90f;  // Right
            case 2 -> 180f; // Back
            case 3 -> 270f; // Left
            default -> 0f;  // Front / Up / Down
        };
        float pitch = (currentFacing == 4) ? -90f : (currentFacing == 5 ? 90f : 0f);

        player.setPos(pos.x(), pos.y(), pos.z());
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);

        // 2. Set the timer. We will wait this many ticks BEFORE capturing.
        tickCounter = (currentFacing == 0) ? CHUNK_LOAD_WAIT : ROTATION_WAIT;
        readyToCapture = true;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (currentPosIndex == -1 || !readyToCapture) return;

        // Count down...
        if (tickCounter > 0) {
            tickCounter--;
            return;
        }

        // Timer hit zero! Now we capture.
        captureAndAdvance();
    }

    private static void captureAndAdvance() {
        Minecraft mc = Minecraft.getInstance();
        PanoramaCommand.Pos pos = queue.get(currentPosIndex);

        // 1. Create the folder path strings
        String folderName = String.format("panorama_%d_%d_%d", (int)pos.x(), (int)pos.y(), (int)pos.z());
        String fileName = folderName + "/side_" + currentFacing + ".png";

        // 2. Ensure the directory exists
        try {
            Path screenshotFolder = mc.gameDirectory.toPath().resolve("screenshots").resolve(folderName);
            if (!Files.exists(screenshotFolder)) {
                Files.createDirectories(screenshotFolder);
            }
        } catch (Exception e) {
            // Log the error or notify the player if the folder creation fails
            e.printStackTrace();
        }

        // 3. Now grab the screenshot
        Screenshot.grab(mc.gameDirectory, fileName, mc.getMainRenderTarget(), (msg) -> {});

        // Logic to move to the next side or next coordinate
        readyToCapture = false; // Stop capturing until next state is prepared
        currentFacing++;

        if (currentFacing > 5) {
            currentFacing = 0;
            currentPosIndex++;
        }

        if (currentPosIndex < queue.size()) {
            prepareNextState();
        } else {
            currentPosIndex = -1;
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§aPanorama sequence finished!"));
                mc.options.hideGui = false;
            }
        }
    }


}