package me.navoei.customdiscs.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import me.navoei.customdiscs.AudioCategory;
import net.minecraft.util.StatCollector;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public final class VolumeSettingsScreen extends GuiScreen {
    private final GuiScreen parent;
    private final List<GuiButton> volumeButtons = new ArrayList<GuiButton>();

    public VolumeSettingsScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        volumeButtons.clear();
        int left = width / 2 - 100;
        AudioCategory[] categories = AudioCategory.values();
        for (int i = 0; i < categories.length; i++) {
            int y = height / 2 - 36 + i * 28;
            buttonList.add(new GuiButton(10 + i * 2, left, y, 35, 20, "-"));
            GuiButton value = new GuiButton(11 + i * 2, left + 40, y, 120, 20, label(categories[i]));
            value.enabled = false;
            buttonList.add(value);
            buttonList.add(new GuiButton(20 + i, left + 165, y, 35, 20, "+"));
            volumeButtons.add(value);
        }
        buttonList.add(new GuiButton(30, left, height / 2 + 60, 200, 20,
                StatCollector.translateToLocal("gui.customdiscs.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 30) {
            mc.displayGuiScreen(parent);
            return;
        }
        int categoryIndex;
        float step;
        if (button.id >= 10 && button.id <= 14 && button.id % 2 == 0) {
            categoryIndex = (button.id - 10) / 2;
            step = -0.05F;
        } else if (button.id >= 20 && button.id <= 22) {
            categoryIndex = button.id - 20;
            step = 0.05F;
        } else {
            return;
        }
        ClientAudioSettings.adjust(AudioCategory.values()[categoryIndex], step);
        initGui();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, StatCollector.translateToLocal(
                "gui.customdiscs.volume.title"), width / 2, height / 2 - 62, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return parent != null && parent.doesGuiPauseGame();
    }

    private String label(AudioCategory category) {
        int percentage = Math.round(ClientAudioSettings.get(category) * 100.0F);
        String key = category == AudioCategory.MUSIC_DISC ? "gui.customdiscs.volume.discs"
                : category == AudioCategory.HORN ? "gui.customdiscs.volume.horns"
                : "gui.customdiscs.volume.heads";
        return StatCollector.translateToLocalFormatted(key, percentage);
    }
}
