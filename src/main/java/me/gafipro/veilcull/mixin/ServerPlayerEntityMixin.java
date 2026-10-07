package me.gafipro.veilcull.mixin;

import me.gafipro.veilcull.VeilCull;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SentMessage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Inject(method = "sendMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void veilcull$interceptSystemMessage(Text message, CallbackInfo ci) {
        VeilCull.interceptMessage(
                (ServerPlayerEntity) (Object) this,
                message
        );
    }

    @Inject(
            method = "sendChatMessage(Lnet/minecraft/network/message/SentMessage;ZLnet/minecraft/network/message/MessageType$Parameters;)V",
            at = @At("HEAD")
    )
    private void veilcull$interceptChatMessage(
            SentMessage message,
            boolean filterMaskEnabled,
            MessageType.Parameters params,
            CallbackInfo ci
    ) {
        Text decoratedMessage = params.applyChatDecoration(message.content());

        VeilCull.interceptChatMessage(
                (ServerPlayerEntity) (Object) this,
                message,
                decoratedMessage
        );
    }
}
