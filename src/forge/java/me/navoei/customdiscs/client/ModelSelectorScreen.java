package me.navoei.customdiscs.client;

import cpw.mods.fml.client.FMLClientHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;
import me.navoei.customdiscs.CustomDiscsMod;
import me.navoei.customdiscs.ModelSelector;

import java.util.ArrayList;
import java.util.List;

public final class ModelSelectorScreen extends GuiScreen {
    private static final int PAGE_SIZE = 8;
    private final int[] values;
    private final String[] names;
    private int page;
    private final List<GuiButton> modelButtons = new ArrayList<GuiButton>();

    public ModelSelectorScreen(int[] values, String[] names) {
        this.values = values;
        this.names = names;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        modelButtons.clear();
        int pages = Math.max(1, (values.length + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pages - 1);
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, values.length);
        int left = width / 2 - 100;
        for (int index = start; index < end; index++) {
            GuiButton button = new GuiButton(index, left, height / 2 - 72 + (index - start) * 20,
                    200, 18, names[index] + " (" + values[index] + ")");
            buttonList.add(button);
            modelButtons.add(button);
        }
        if (page > 0) {
            buttonList.add(new GuiButton(300, left, height - 28, 90, 20,
                    StatCollector.translateToLocal("gui.customdiscs.models.previous")));
        }
        if (page < pages - 1) {
            buttonList.add(new GuiButton(301, left + 110, height - 28, 90, 20,
                    StatCollector.translateToLocal("gui.customdiscs.models.next")));
        }
        buttonList.add(new GuiButton(302, left, height - 52, 200, 20,
                StatCollector.translateToLocal("gui.customdiscs.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 300) {
            page--;
            initGui();
        } else if (button.id == 301) {
            page++;
            initGui();
        } else if (button.id == 302) {
            mc.displayGuiScreen(null);
        } else if (button.id >= 0 && button.id < values.length) {
            CustomDiscsMod.NETWORK.sendToServer(new ModelSelector.SelectMessage(values[button.id]));
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, StatCollector.translateToLocal(
                "gui.customdiscs.models.title"), width / 2, 24, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
