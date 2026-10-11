package com.nonid.affinity.integration;

import com.nonid.affinity.client.AffinityConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;

/**
 * ModMenu entry point: adds a "Config" button on this mod's ModMenu page.
 *
 * <p>Only referenced from {@code fabric.mod.json}, so the mod still loads fine when ModMenu
 * is not installed.
 */
@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return (ConfigScreenFactory<Screen>) AffinityConfigScreen::new;
    }
}
