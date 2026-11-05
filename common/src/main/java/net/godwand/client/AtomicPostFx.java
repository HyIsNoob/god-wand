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
        pending = GodWandConfig.CLIENT.postEffects && (AtomicRenderer.nearestFrame(delta) != null || FugaRenderer.shaderFrame(delta) != null)
                ? new Frame(new Matrix4f(view), new Matrix4f(projection), delta) : null;
    }
    public static boolean available() { return GodWandConfig.CLIENT.postEffects&&chain!=null&&!failed; }
    public static void reset() { reload(); pending = null; }
    public static void reload() {
        if (chain != null) chain.close();
        chain = null; failed = false; width = height = downsample = 0;
    }
    /** Whether the post shader is meant to draw this frame (configured and not failed), regardless of whether the chain
     *  has been built yet: mesh stand-ins keyed to it must not flicker on and off between frames. */
    public static boolean enabled() { return GodWandConfig.CLIENT.postEffects&&!failed; }
    private static java.lang.reflect.Method irisInUse;
    private static Object iris;
    private static boolean irisChecked;
    /** Iris/Oculus with a shaderpack loaded may leave the main depth buffer empty; probed through its public API. */
    static boolean shaderPackActive() {
        try {
            if (!irisChecked) {
                irisChecked = true;
                var api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                iris = api.getMethod("getInstance").invoke(null);
                irisInUse = api.getMethod("isShaderPackInUse");
            }
            return irisInUse != null && (boolean) irisInUse.invoke(iris);
        } catch (ReflectiveOperationException | LinkageError absent) { irisInUse = null; return false; }
    }
    public static void renderAfterWorld() {
        Frame context = pending; pending = null;
        if (context == null || failed) return;
        var fx = AtomicRenderer.nearestFrame(context.delta);
        var fuga=FugaRenderer.shaderFrame(context.delta);
        // Under a shaderpack the fire mesh is drawn here, after the level pass, so the pack cannot repaint it.
        FugaRenderer.flushDeferred();
        var client = Minecraft.getInstance();
        if ((fx == null && fuga == null) || client.level == null) return;
        // The chain is built behind the static opening shot, so the release frame never stalls on shader linking;
        // during the charge it only adds screen-space light (bloom, shafts, haze) around the mesh flame.
        if(fx==null && fuga.phase()==0 && fuga.age()<4)return;
        var center=fx!=null?fx.center():fuga.phase()==1?fuga.arrowPosition():fuga.center();
        // During the hit-stop the fire phoenix leads the skyward beam: same head, pointed straight up at its top.
        double rise=fx==null&&fuga.phase()==1?FugaRenderer.hitStop(fuga.casterId(),context.delta):-1;
        if(rise>=0) center=center.add(0,6+110*FugaCinematic.smooth(rise,0,1)-3,0);
        if(fx==null && fuga.phase()==0) {
            // Light source: the hands, sliding out to the arrowhead as it forms (projected at center.y+2 below).
            var d=fuga.target().subtract(fuga.origin()).normalize();
            center=FugaRenderer.chargeHand(fuga,fuga.origin(),false).add(d.scale(1.6*FugaCinematic.smooth(fuga.age(),144,190))).subtract(0,2,0);
            // While the palms pull the flame apart the shader draws it as a volume: anchor it between both hands.
            if(FugaRenderer.ribbonVolume(fuga.age())>0)
                center=FugaRenderer.chargeHand(fuga,fuga.origin(),true).lerp(FugaRenderer.chargeHand(fuga,fuga.origin(),false),.5).subtract(0,2,0);
        }
        int w=client.getWindow().getWidth(), h=client.getWindow().getHeight();
        if(w<=0||h<=0)return;
        try {
            if(chain==null) chain=new PostChain(client.getTextureManager(),client.getResourceManager(),client.getMainRenderTarget(),
                    ResourceLocation.withDefaultNamespace("shaders/post/godwand_atomic.json"));
            if(w!=width||h!=height||downsample!=GodWandConfig.CLIENT.bloomDownsample) {
                chain.resize(w,h);
                for(String name:new String[]{"atomic_emit","atomic_blur_x","atomic_blur_y"})
                    chain.getTempTarget(name).resize(GodWandPostFxManager.bloomDimension(w),GodWandPostFxManager.bloomDimension(h),false);
                chain.getTempTarget("fuga_half_volume").resize((w+1)/2,(h+1)/2,false);
                for(var pass:((PostEffectProcessorAccessor)chain).getPasses())
                    pass.setOrthoMatrix(new Matrix4f().setOrtho(0,pass.outTarget.width,0,pass.outTarget.height,.1f,1000));
                width=w; height=h; downsample=GodWandConfig.CLIENT.bloomDownsample;
            }
            var camera=client.gameRenderer.getMainCamera().getPosition();
            var clip=new Vector4f((float)(center.x-camera.x),(float)(center.y+2-camera.y),(float)(center.z-camera.z),1)
                    .mul(context.view).mul(context.projection);
            float centerX=clip.w>.001?clip.x/clip.w*.5f+.5f:.5f, centerY=clip.w>.001?clip.y/clip.w*.5f+.5f:.5f;
            var inverseProjection=new Matrix4f(context.projection).invert();
            var inverseView=new Matrix4f(context.view).invert();
            int index=0;
            for(var pass:((PostEffectProcessorAccessor)chain).getPasses()) {
                var effect=pass.getEffect();
                effect.safeGetUniform("EmissionOnly").set(index++==2?1f:0f);
                effect.safeGetUniform("InvProjMat").set(inverseProjection);
                effect.safeGetUniform("InvViewMat").set(inverseView);
                effect.safeGetUniform("CameraPos").set((float)camera.x,(float)camera.y,(float)camera.z);
                effect.safeGetUniform("BlastCenter").set((float)center.x,(float)center.y,(float)center.z);
            }
            chain.setUniform("FugaPhase",fx==null?fuga.phase():-1);
            chain.setUniform("DepthTrust",shaderPackActive()?0:1);
            if(fx==null) {
                var aim=FugaRenderer.shaderAxis(fuga);
                // Rising, it leans a little away from the camera behind it, so its wings spread across the view
                // instead of being seen edge-on.
                if(rise>=0) aim=new net.minecraft.world.phys.Vec3(aim.x,0,aim.z).normalize().scale(.3).add(0,1,0).normalize();
                chain.setUniform("FugaDirX",(float)aim.x);chain.setUniform("FugaDirY",(float)aim.y);chain.setUniform("FugaDirZ",(float)aim.z);
                // The frozen frame would freeze its fire too: rising, it burns on, opening its wings as it climbs.
                chain.setUniform("Age",(float)(rise>=0?12+8*rise:fuga.age()));
                chain.setUniform("BloomStrength",GodWandConfig.CLIENT.bloomStrength*.8f);
                chain.setUniform("BloomBurst",0);
                chain.setUniform("ImpactFrames",GodWandConfig.CLIENT.impactFrames?1:0);
                // Impact frame on release and on the sphere's birth, timed from local receipt of that phase.
                // Screen-wide beats (impact frames, flashes, vignette) belong to the caster; observers see the world effect.
                boolean own=client.player!=null&&fuga.casterId()==client.player.getId();
                chain.setUniform("ObserverGain",own?1:0);
                chain.setUniform("Cut",own?FugaRenderer.impactBeat(fuga.casterId(),context.delta):0);
                // Off-screen detonations move the screen flare out of view instead of centring it.
                chain.setUniform("CenterX",clip.w>.001?centerX:-10); chain.setUniform("CenterY",centerY);
            } else {
            chain.setUniform("Age",fx.age());
            chain.setUniform("SequenceEnd",net.godwand.skill.AtomicSequence.END);
            chain.setUniform("DetonationTick",net.godwand.skill.AtomicSequence.DETONATION);
            chain.setUniform("ChargeProgress",net.godwand.skill.AtomicSequence.charge(fx.age()));
            chain.setUniform("ReleaseFlash",net.godwand.skill.AtomicSequence.releaseFlash(fx.age()));
            chain.setUniform("Compression",net.godwand.skill.AtomicSequence.compression(fx.age()));
            chain.setUniform("FxFade",net.godwand.skill.AtomicSequence.visibility(fx.age()));
            chain.setUniform("Cut",net.godwand.skill.AtomicSequence.blackout(fx.age()));
            chain.setUniform("HoloRadius",net.godwand.skill.AtomicSequence.holoRadius(fx.age()));
            chain.setUniform("BeamRadius",net.godwand.skill.AtomicSequence.beamRadius(fx.age()));
            chain.setUniform("BeamHeight",net.godwand.skill.AtomicSequence.beamHeight(fx.age()));
            chain.setUniform("BeamDepth",(float)net.godwand.skill.AtomicSequence.BEAM_DEPTH);
            chain.setUniform("BlastPulse",GodWandConfig.CLIENT.impactFrames?net.godwand.skill.AtomicSequence.blastPulse(fx.age()):1);
            chain.setUniform("ShockRadius",net.godwand.skill.AtomicSequence.shockRadius(fx.age()));
            chain.setUniform("ObserverGain",AtomicRenderer.distanceGain(client.player.position().distanceTo(fx.center())));
            chain.setUniform("ImpactPulse",net.godwand.skill.AtomicSequence.impact(fx.age())?1:0);
            chain.setUniform("ImpactFrames",GodWandConfig.CLIENT.impactFrames?1:0);
            chain.setUniform("Darken",GodWandConfig.CLIENT.skyBlackout?1:0);
            chain.setUniform("CenterX",centerX); chain.setUniform("CenterY",centerY);
            chain.setUniform("BloomStrength",GodWandConfig.CLIENT.bloomStrength);
            chain.setUniform("BloomBurst",fx.age()>=net.godwand.skill.AtomicSequence.DETONATION&&clip.w>0
                    ?Math.max(0,1-(fx.age()-net.godwand.skill.AtomicSequence.DETONATION)/24):0);
            }
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
