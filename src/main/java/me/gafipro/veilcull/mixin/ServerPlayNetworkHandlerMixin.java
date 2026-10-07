package me.gafipro.veilcull.mixin;

import me.gafipro.veilcull.VeilCull;
import net.minecraft.network.message.MessageType;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Inject(
            method = "sendProfilelessChatMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageType$Parameters;)V",
            at = @At("HEAD")
    )
    private void veilcull$interceptProfilelessChat(
            Text message,
            MessageType.Parameters params,
            CallbackInfo ci
    ) {
        ServerPlayNetworkHandler handler =
                (ServerPlayNetworkHandler) (Object) this;

        VeilCull.interceptProfilelessChatMessage(
                handler.player,
                message,
                params
        );
    }
}
