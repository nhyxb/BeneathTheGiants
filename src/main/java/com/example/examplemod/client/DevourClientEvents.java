package com.example.examplemod.client;

import com.example.examplemod.devour.DevourPlayerAccess;
import net.minecraft.client.player.Input;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

public final class DevourClientEvents {
    private DevourClientEvents() {}

    public static void onMovementInputUpdate(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        if (player instanceof DevourPlayerAccess access && access.examplemod$getCaptorId() != -1) {
            Input input = event.getInput();
            if (input != null) {
                input.leftImpulse = 0.0F;
                input.forwardImpulse = 0.0F;
                input.up = false;
                input.down = false;
                input.left = false;
                input.right = false;
                input.jumping = false;
                input.shiftKeyDown = false;
            }
        }
    }
}
