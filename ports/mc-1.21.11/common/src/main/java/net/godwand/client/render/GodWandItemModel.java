package net.godwand.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.*;
import java.util.function.Consumer;

public final class GodWandItemModel implements ItemModel {
    private final java.util.Map<ItemDisplayContext,Renderer> renderers = new java.util.EnumMap<>(ItemDisplayContext.class);
    @Override public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
            ItemDisplayContext context, ClientLevel level, ItemOwner owner, int seed) {
        state.setAnimated();
        var layer = state.newLayer();
        layer.setUsesBlockLight(false);

        var special = renderers.computeIfAbsent(context,Renderer::new);
        layer.setupSpecialModel(special,stack);
        layer.setExtents(() -> new Vector3fc[]{new Vector3f(-.5f,-.5f,-.5f),new Vector3f(.5f,.5f,.5f)});
    }
    private static final class Renderer implements SpecialModelRenderer<ItemStack> {
        private final ItemDisplayContext context;
        private final GodWandItemRenderer renderer = new GodWandItemRenderer();
        Renderer(ItemDisplayContext context) { this.context=context; }
        public void submit(ItemStack stack,ItemDisplayContext displayContext,PoseStack pose,SubmitNodeCollector collector,int light,int overlay,boolean foil,int outline) {
            renderer.submit(stack,context,pose,collector,light,overlay);
        }
        public void getExtents(Consumer<Vector3fc> out) { out.accept(new Vector3f(-.5f));out.accept(new Vector3f(.5f)); }
        public ItemStack extractArgument(ItemStack stack) { return stack; }
    }
    public record Unbaked() implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> CODEC = MapCodec.unit(new Unbaked());
        public MapCodec<Unbaked> type() { return CODEC; }
        public ItemModel bake(ItemModel.BakingContext context) { return new GodWandItemModel(); }
        public void resolveDependencies(ResolvableModel.Resolver resolver) { }
    }
}
