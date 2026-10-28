package net.godwand.client.gui;

import net.godwand.config.GodWandConfig;
import net.godwand.config.WandSkin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class GodWandConfigScreen extends Screen {
    private final Screen parent;

    public GodWandConfigScreen(Screen parent) {
        super(Component.translatable("gui.godwand.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 30;
        int contentWidth = Math.min(310, this.width - 24);
        int left = cx - contentWidth / 2;
        int columnWidth = (contentWidth - 10) / 2;
        int right = left + columnWidth + 10;
        int gap = Math.max(2, Math.min(6, (this.height - 160) / 4));

        addRenderableWidget(CycleButton.<WandSkin>builder(WandSkin::getDisplayName, GodWandConfig.CLIENT.wandSkin)
                .withValues(WandSkin.values())
                .create(left, y, contentWidth, 20,
                        Component.translatable("gui.godwand.config.wand_skin"),
                        (button, value) -> GodWandConfig.CLIENT.wandSkin = value));

        y += 20 + gap;

        addRenderableWidget(CycleButton.onOffBuilder(GodWandConfig.CLIENT.postEffects)
                .create(left, y, columnWidth, 20,
                        Component.translatable("gui.godwand.config.post_effects"),
                        (btn, val) -> GodWandConfig.CLIENT.postEffects = val));

        addRenderableWidget(CycleButton.onOffBuilder(GodWandConfig.CLIENT.impactFrames)
                .create(right, y, columnWidth, 20,
                        Component.translatable("gui.godwand.config.impact_frames"),
                        (btn, val) -> GodWandConfig.CLIENT.impactFrames = val));

        y += 20 + gap;

        addRenderableWidget(CycleButton.onOffBuilder(GodWandConfig.CLIENT.skyBlackout)
                .create(left, y, columnWidth, 20,
                        Component.translatable("gui.godwand.config.sky_blackout"),
                        (btn, val) -> GodWandConfig.CLIENT.skyBlackout = val));

        addRenderableWidget(CycleButton.onOffBuilder(GodWandConfig.CLIENT.cameraFov)
                .create(right, y, columnWidth, 20,
                        Component.translatable("gui.godwand.config.camera_fov"),
                        (btn, val) -> GodWandConfig.CLIENT.cameraFov = val));

        y += 20 + gap;

        addRenderableWidget(CycleButton.onOffBuilder(GodWandConfig.CLIENT.targetMarker)
                .create(left, y, columnWidth, 20,
                        Component.translatable("gui.godwand.config.target_marker"),
                        (btn, val) -> GodWandConfig.CLIENT.targetMarker = val));

        addRenderableWidget(CycleButton.onOffBuilder(GodWandConfig.CLIENT.atomicCutscene)
                .create(right,y,columnWidth,20,Component.translatable("gui.godwand.config.atomic_cutscene"),
                        (button,value)->GodWandConfig.CLIENT.atomicCutscene=value));
        y += 20 + gap;

        addRenderableWidget(new AbstractSliderButton(left, y, columnWidth, 20,
                Component.translatable("gui.godwand.config.bloom", (int) (GodWandConfig.CLIENT.bloomStrength * 100)),
                GodWandConfig.CLIENT.bloomStrength / 1.5) {
            @Override
            protected void updateMessage() {
                setMessage(Component.translatable("gui.godwand.config.bloom", (int) (this.value * 150)));
            }

            @Override
            protected void applyValue() {
                GodWandConfig.CLIENT.bloomStrength = (float) (this.value * 1.5);
            }
        });

        addRenderableWidget(new AbstractSliderButton(right, y, columnWidth, 20,
                Component.translatable("gui.godwand.config.camera_shake", (int) (GodWandConfig.CLIENT.cameraShake * 100)),
                GodWandConfig.CLIENT.cameraShake) {
            @Override
            protected void updateMessage() {
                setMessage(Component.translatable("gui.godwand.config.camera_shake", (int) (this.value * 100)));
            }

            @Override
            protected void applyValue() {
                GodWandConfig.CLIENT.cameraShake = (float) this.value;
            }
        });

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            onClose();
        }).bounds(cx - Math.min(200,contentWidth)/2, this.height - 30, Math.min(200,contentWidth), 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractBackground(context, mouseX, mouseY, delta);
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 18, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        GodWandConfig.CLIENT.validate();
        GodWandConfig.saveClient();
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
