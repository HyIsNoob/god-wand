package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.godwand.GodWandMod;
import net.godwand.config.GodWandConfig;
import net.godwand.mixin.PostEffectProcessorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Separate chain so Atomic cannot replace an in-progress Supernova effect. */
public final class AtomicPostFx {
    private record Frame(Matrix4f view, Matrix4f projection, float delta) {}
    private static Frame pending;
    private static PostChain chain;
    private static int width, height, downsample;
    private static boolean failed;

    public static void capture(Matrix4f view, Matrix4f projection, float delta) {
        pending = GodWandConfig.CLIENT.postEffects && AtomicRenderer.nearestFrame(delta) != null
                ? new Frame(new Matrix4f(view), new Matrix4f(projection), delta) : null;
    }
    public static boolean available() { return GodWandConfig.CLIENT.postEffects&&chain!=null&&!failed; }
    public static void reset() { reload(); pending = null; }
    public static void reload() {
        if (chain != null) chain.close();
        chain = null; failed = false; width = height = downsample = 0;
    }
    private static void setUniform(PostChain chain,String name,float value) {
        for(var pass:((PostEffectProcessorAccessor)chain).getPasses()) pass.getEffect().safeGetUniform(name).set(value);
    }
    public static void renderAfterWorld() {
        Frame context = pending; pending = null;
        if (context == null || failed) return;
        var fx = AtomicRenderer.nearestFrame(context.delta);
        var client = Minecraft.getInstance();
        if (fx == null || client.level == null) return;
        int w=client.getWindow().getWidth(), h=client.getWindow().getHeight();
        if(w<=0||h<=0)return;
        try {
            if(chain==null) chain=new PostChain(client.getTextureManager(),client.getResourceManager(),client.getMainRenderTarget(),
                    new ResourceLocation("minecraft","shaders/post/godwand_atomic.json"));
            if(w!=width||h!=height||downsample!=GodWandConfig.CLIENT.bloomDownsample) {
                chain.resize(w,h);
                for(String name:new String[]{"atomic_emit","atomic_blur_x","atomic_blur_y"})
                    chain.getTempTarget(name).resize(GodWandPostFxManager.bloomDimension(w),GodWandPostFxManager.bloomDimension(h),false);
                for(var pass:((PostEffectProcessorAccessor)chain).getPasses())
                    pass.setOrthoMatrix(new Matrix4f().setOrtho(0,pass.outTarget.width,0,pass.outTarget.height,.1f,1000));
                width=w; height=h; downsample=GodWandConfig.CLIENT.bloomDownsample;
            }
            var camera=client.gameRenderer.getMainCamera().getPosition();
            var clip=new Vector4f((float)(fx.center().x-camera.x),(float)(fx.center().y+2-camera.y),(float)(fx.center().z-camera.z),1)
                    .mul(context.view).mul(context.projection);
            float centerX=clip.w>.001?clip.x/clip.w*.5f+.5f:.5f, centerY=clip.w>.001?clip.y/clip.w*.5f+.5f:.5f;
            var inverseProjection=new Matrix4f(context.projection).invert();
            var inverseView=new Matrix4f(context.view).invert();
            int index=0;
            for(var pass:((PostEffectProcessorAccessor)chain).getPasses()) {
                var effect=pass.getEffect();
                effect.safeGetUniform("EmissionOnly").set(index++==1?1f:0f);
                effect.safeGetUniform("InvProjMat").set(inverseProjection);
                effect.safeGetUniform("InvViewMat").set(inverseView);
                effect.safeGetUniform("CameraPos").set((float)camera.x,(float)camera.y,(float)camera.z);
                effect.safeGetUniform("BlastCenter").set((float)fx.center().x,(float)fx.center().y,(float)fx.center().z);
            }
            setUniform(chain,"Age",fx.age());
            setUniform(chain,"SequenceEnd",net.godwand.skill.AtomicSequence.END);
            setUniform(chain,"DetonationTick",net.godwand.skill.AtomicSequence.DETONATION);
            setUniform(chain,"ChargeProgress",net.godwand.skill.AtomicSequence.charge(fx.age()));
            setUniform(chain,"ReleaseFlash",net.godwand.skill.AtomicSequence.releaseFlash(fx.age()));
            setUniform(chain,"Compression",net.godwand.skill.AtomicSequence.compression(fx.age()));
            setUniform(chain,"FxFade",net.godwand.skill.AtomicSequence.visibility(fx.age()));
            setUniform(chain,"Cut",net.godwand.skill.AtomicSequence.blackout(fx.age()));
            setUniform(chain,"HoloRadius",net.godwand.skill.AtomicSequence.holoRadius(fx.age()));
            setUniform(chain,"BeamRadius",net.godwand.skill.AtomicSequence.beamRadius(fx.age()));
            setUniform(chain,"BeamHeight",net.godwand.skill.AtomicSequence.beamHeight(fx.age()));
            setUniform(chain,"BeamDepth",(float)net.godwand.skill.AtomicSequence.BEAM_DEPTH);
            setUniform(chain,"BlastPulse",GodWandConfig.CLIENT.impactFrames?net.godwand.skill.AtomicSequence.blastPulse(fx.age()):1);
            setUniform(chain,"ShockRadius",net.godwand.skill.AtomicSequence.shockRadius(fx.age()));
            setUniform(chain,"ObserverGain",AtomicRenderer.distanceGain(client.player.position().distanceTo(fx.center())));
            setUniform(chain,"ImpactPulse",net.godwand.skill.AtomicSequence.impact(fx.age())?1:0);
            setUniform(chain,"ImpactFrames",GodWandConfig.CLIENT.impactFrames?1:0);
            setUniform(chain,"Darken",GodWandConfig.CLIENT.skyBlackout?1:0);
            setUniform(chain,"CenterX",centerX); setUniform(chain,"CenterY",centerY);
            setUniform(chain,"BloomStrength",GodWandConfig.CLIENT.bloomStrength);
            setUniform(chain,"BloomBurst",fx.age()>=net.godwand.skill.AtomicSequence.DETONATION&&clip.w>0
                    ?Math.max(0,1-(fx.age()-net.godwand.skill.AtomicSequence.DETONATION)/24):0);
            RenderSystem.disableBlend(); RenderSystem.disableDepthTest(); RenderSystem.resetTextureMatrix();
            chain.process(context.delta);
        } catch(Exception error) {
            failed=true;
            GodWandMod.LOGGER.error("Atomic post effect disabled until resource reload; world geometry remains available",error);
            if(chain!=null) { chain.close(); chain=null; }
        } finally {
            client.getMainRenderTarget().bindWrite(true);
            RenderSystem.enableDepthTest(); RenderSystem.depthMask(true);
        }
    }
}
