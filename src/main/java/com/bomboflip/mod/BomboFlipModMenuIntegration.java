package com.bomboflip.mod;

import com.bomboflip.mod.moul.MoulConfigIntegrator;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Lets ModMenu's "config" button on Bombo Flip's entry open the MoulConfig
 * screen, same as the in-game command.
 */
public class BomboFlipModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            MoulConfigIntegrator.openConfigScreen();
            return null;
        };
    }
}