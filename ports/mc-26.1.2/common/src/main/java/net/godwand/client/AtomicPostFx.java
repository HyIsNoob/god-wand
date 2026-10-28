package net.godwand.client;

import net.godwand.GodWandMod;
import net.godwand.config.GodWandConfig;
import net.minecraft.client.Minecraft;
import net.godwand.client.postfx.GodWandPostChain;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Separate chain so Atomic cannot replace an in-progress Supernova effect. */
public final class AtomicPostFx {
    private record Frame(Matrix4f view, Matrix4f projection, float delta) {}
    private static Frame pending;
    private static GodWandPostChain chain;
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
    public static void renderAfterWorld() {
        Frame context = pending; pending = null;
        if (context == null || failed) return;
        var fx = AtomicRenderer.nearestFrame(context.delta);
        var client = Minecraft.getInstance();
        if (fx == null || client.level == null) return;
        int w=client.getWindow().getWidth(), h=client.getWindow().getHeight();
        if(w<=0||h<=0)return;
        try {
            if(chain==null) chain=new GodWandPostChain("godwand_atomic");
            if(w!=width||h!=height||downsample!=GodWandConfig.CLIENT.bloomDownsample) {
                chain.resize(w,h);
                width=w; height=h; downsample=GodWandConfig.CLIENT.bloomDownsample;
            }
            var camera=client.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.pos;
            var clip=new Vector4f((float)(fx.center().x-camera.x),(float)(fx.center().y+2-camera.y),(float)(fx.center().z-camera.z),1)
                    .mul(context.view).mul(context.projection);
            float centerX=clip.w>.001?clip.x/clip.w*.5f+.5f:.5f, centerY=clip.w>.001?clip.y/clip.w*.5f+.5f:.5f;
            var inverseProjection=new Matrix4f(context.projection).invert();
            var inverseView=new Matrix4f(context.view).invert();
            chain.setUniform("InvProjMat", inverseProjection);
            chain.setUniform("InvViewMat", inverseView);
            chain.setUniform("CameraPos", (float)camera.x, (float)camera.y, (float)camera.z);
            chain.setUniform("BlastCenter", (float)fx.center().x, (float)fx.center().y, (float)fx.center().z);
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
            chain.process();
        } catch(Exception error) {
            failed=true;
            GodWandMod.LOGGER.error("Atomic post effect disabled until resource reload; world geometry remains available",error);
            if(chain!=null) { chain.close(); chain=null; }
        }
    }
}

