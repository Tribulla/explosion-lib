package com.example.explosionlib.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ExploderConfigScreen extends Screen {
    private static final float MIN_YIELD = 0.5f, MAX_YIELD = 1024.0f;
    private final Screen parent;

    public ExploderConfigScreen(Screen parent) {
        super(Component.literal("ExplosionLib"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int w = 220, x = cx - w / 2;
        int y = this.height / 4;

        addRenderableWidget(new YieldSlider(x, y, w, 20));
        y += 24;
        addRenderableWidget(toggle(x, y, w, "Shockwave", () -> ClientConfig.shockwave, v -> ClientConfig.shockwave = v));
        y += 24;
        addRenderableWidget(toggle(x, y, w, "Gravity collapse", () -> ClientConfig.gravity, v -> ClientConfig.gravity = v));
        y += 24;
        addRenderableWidget(toggle(x, y, w, "Debris", () -> ClientConfig.debris, v -> ClientConfig.debris = v));
        y += 24;
        addRenderableWidget(toggle(x, y, w, "Scorch", () -> ClientConfig.scorch, v -> ClientConfig.scorch = v));
        y += 24;
        addRenderableWidget(toggle(x, y, w, "Entity damage", () -> ClientConfig.entityDamage, v -> ClientConfig.entityDamage = v));
        y += 32;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
            .bounds(x, y, w, 20).build());
    }

    private interface BoolGetter { boolean get(); }
    private interface BoolSetter { void set(boolean v); }

    private Button toggle(int x, int y, int w, String name, BoolGetter getter, BoolSetter setter) {
        return Button.builder(label(name, getter.get()), b -> {
            setter.set(!getter.get());
            b.setMessage(label(name, getter.get()));
        }).bounds(x, y, w, 20).build();
    }

    private static Component label(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, this.height / 4 - 24, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.literal("left-click = look  |  shift+left-click = feet"),
            this.width / 2, this.height / 4 - 12, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    private static final class YieldSlider extends AbstractSliderButton {
        YieldSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), Math.log(ClientConfig.yield / MIN_YIELD) / Math.log(MAX_YIELD / MIN_YIELD));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.format("Yield: %.1f", ClientConfig.yield)));
        }

        @Override
        protected void applyValue() {
            ClientConfig.yield = (float) (MIN_YIELD * Math.pow(MAX_YIELD / MIN_YIELD, this.value));
        }
    }
}
