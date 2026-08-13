package com.tlmstatueanimation;

import com.tlmstatueanimation.network.NetworkHandler;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TlmStatueAnimation.MOD_ID)
public final class TlmStatueAnimation {
    public static final String MOD_ID = "tlm_statue_animation";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public TlmStatueAnimation() {
        NetworkHandler.init();
    }
}
