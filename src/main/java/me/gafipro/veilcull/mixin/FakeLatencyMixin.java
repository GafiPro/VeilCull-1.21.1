package me.gafipro.veilcull.mixin;

import com.mojang.authlib.GameProfile;
import me.gafipro.veilcull.VeilCull;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerCommonNetworkHandler.class)
public abstract class FakeLatencyMixin {
    @Shadow
    protected abstract GameProfile getProfile();

    @Inject(method = "getLatency", at = @At("HEAD"), cancellable = true)
    private void veilcull$overrideLatency(CallbackInfoReturnable<Integer> cir) {
        Integer fakePing = VeilCull.getFakePing(getProfile().getId());

        if (fakePing != null) {
            cir.setReturnValue(fakePing);
        }
    }
}
