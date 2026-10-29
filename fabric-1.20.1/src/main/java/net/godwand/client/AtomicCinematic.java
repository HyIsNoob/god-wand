package net.godwand.client;

import net.godwand.config.GodWandConfig;
import net.godwand.skill.AtomicSequence;
import net.minecraft.client.Minecraft;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Camera changes last one frame; player rotation and perspective settings are never rewritten. */
public final class AtomicCinematic {
    private static final net.minecraft.resources.ResourceLocation TITLE_FONT = new net.minecraft.resources.ResourceLocation("godwand", "atomic_title");
    public record Pose(Vec3 position, float yaw, float pitch) {}
    private static boolean skipped, movementReleased, movementReleaseSent;
    private static long wideStarted=Long.MIN_VALUE;
    private static float wideAngle;
    private static double wideHeight;
    private static volatile float environmentGain = 1;

    public static boolean movementLocked() {
        var client=Minecraft.getInstance();
        var frame=AtomicRenderer.localFrame(0);
        return frame!=null && client.player.isAlive() && !client.player.isSpectator() && !movementReleased && AtomicSequence.movementLocked(frame.age());
    }
    public static boolean active() {
        var client = Minecraft.getInstance();
        var frame = AtomicRenderer.localFrame(0);
        return frame != null && GodWandConfig.CLIENT.atomicCutscene && client.player.isAlive()
                && !client.player.isSpectator() && !skipped && client.screen==null && client.isWindowActive();
    }
    public static void tick(Minecraft client) {
        var nearest = AtomicRenderer.nearestFrame(0);
        environmentGain = 1;
        if (nearest != null && nearest.age() >= AtomicSequence.CHARGE_TICKS + 8 && nearest.age() < AtomicSequence.DETONATION) {
            float distance = AtomicRenderer.distanceGain(client.player.position().distanceTo(nearest.center()));
            environmentGain = 1 - .98f * distance * AtomicSequence.phase(nearest.age(), 392, 402);
        }
        var local=AtomicRenderer.localFrame(0);
        if (local == null) return;
        if(movementReleased&&!movementReleaseSent) {
            net.godwand.platform.Services.NETWORK.sendToServer(new net.godwand.network.AtomicSkipPayload(local.startedAt()));
            movementReleaseSent=true;
        }
        if(!client.isWindowActive()||client.screen!=null)return;
        long window = client.getWindow().getWindow();
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F5) == GLFW.GLFW_PRESS) skip();
    }
    public static void reset() { skipped=false; movementReleased=false; movementReleaseSent=false; wideStarted=Long.MIN_VALUE; environmentGain = 1; }
    public static void onCastStart() { skipped=false; movementReleased=false; movementReleaseSent=false; wideStarted=Long.MIN_VALUE; }
    public static void skip() {
        skipped=true;
        var frame=AtomicRenderer.localFrame(0);
        if(frame!=null&&frame.age()>=AtomicSequence.DETONATION)movementReleased=true;
    }

    public static Pose camera(float delta, Vec3 vanillaPosition, float vanillaYaw, float vanillaPitch) {
        if (!active()) return null;
        var client = Minecraft.getInstance();
        var frame = AtomicRenderer.localFrame(delta);
        var shot = AtomicSequence.shot(frame.age(), frame.yaw());
        boolean wide=frame.age()>=466&&frame.age()<614;
        Vec3 target = frame.center().add(shot.focus());
        Vec3 offset = shot.offset();
        float idle=AtomicSequence.holdIdle(frame.waitTicks())*(1-AtomicSequence.phase(frame.age(),AtomicSequence.WAIT_TICK,AtomicSequence.WAIT_TICK+4));
        offset=offset.add(idle*2,idle*.5,0);
        double budget = AtomicSequence.cameraBudget(client.options.getEffectiveRenderDistance(),frame.age());
        if (offset.horizontalDistance() > budget) {
            double scale=budget/offset.horizontalDistance();
            offset=new Vec3(offset.x*scale,offset.y,offset.z*scale);
        }
        Vec3 desired = frame.center().add(offset);
        for (int step = 0; step < 12 && !loadedNeighborhood(client, desired); step++) {
            offset = new Vec3(offset.x*.8,offset.y,offset.z*.8);
            desired = frame.center().add(offset);
        }
        if(wide) {
            double radius=offset.horizontalDistance();
            if(wideStarted!=frame.startedAt()) {
                wideStarted=frame.startedAt(); wideAngle=0; wideHeight=Double.POSITIVE_INFINITY;
                for(int angle:new int[]{0,-30,30,-60,60,180}) {
                    double height=wideClearance(client,frame,radius,angle);
                    if(height<wideHeight) { wideHeight=height; wideAngle=angle; }
                    if(height<=Math.max(6,(radius-AtomicSequence.MAX_RADIUS)*.6))break;
                }
                // shortcut: enclosed terrain may still obscure the base; avoid turning the wide shot into another overhead shot.
                wideHeight=Math.min(60,wideHeight);
            }
            offset=offset.yRot((float)Math.toRadians(wideAngle));
            desired=frame.center().add(offset.x,wideHeight,offset.z);
            double lens=GodWandConfig.CLIENT.cameraFov?shot.fov():client.options.fov().get();
            target=frame.center().add(AtomicSequence.wideFocus(desired.subtract(frame.center()),lens));
        }
        Vec3 direction = target.subtract(desired);
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
        float blend = AtomicSequence.returnBlend(frame.age());
        return new Pose(desired.lerp(vanillaPosition, blend), Mth.rotLerp(blend, yaw, vanillaYaw), Mth.lerp(blend, pitch, vanillaPitch));
    }
    private static double wideClearance(Minecraft client,AtomicRenderer.Frame frame,double radius,int angle) {
        double height=Math.min(60,Math.max(6,(radius-AtomicSequence.MAX_RADIUS)*.6));
        Vec3 arc=AtomicSequence.shot(466,frame.yaw()).offset();
        for(int turn=0;turn>=-6;turn--) {
            Vec3 direction=arc.yRot((float)Math.toRadians(angle+turn)).scale(1/arc.horizontalDistance());
            if(!loadedNeighborhood(client,frame.center().add(direction.scale(radius))))return Double.POSITIVE_INFINITY;
            for(int step=1;step<=8;step++) {
                double t=step/8.0, r=Math.min(AtomicSequence.MAX_RADIUS,radius)+Math.max(0,radius-AtomicSequence.MAX_RADIUS)*t;
                Vec3 sample=frame.center().add(direction.scale(r));
                int x=Mth.floor(sample.x),z=Mth.floor(sample.z);
                if(!client.level.getChunkSource().hasChunk(x>>4,z>>4))return Double.POSITIVE_INFINITY;
                double ground=client.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,x,z)+.75-frame.center().y;
                height=Math.max(height,ground/t);
            }
        }
        return height;
    }
    private static boolean loadedNeighborhood(Minecraft client, Vec3 point) {
        for (int x : new int[]{-8, 8}) for (int z : new int[]{-8, 8})
            if (!client.level.getChunkSource().hasChunk(Mth.floor(point.x+x)>>4, Mth.floor(point.z+z)>>4)) return false;
        return true;
    }
    public static double fov(double vanilla, float delta) {
        if (!active() || !GodWandConfig.CLIENT.cameraFov) return vanilla;
        var frame = AtomicRenderer.localFrame(delta);
        return Mth.lerp(AtomicSequence.returnBlend(frame.age()), AtomicSequence.shot(frame.age(), frame.yaw()).fov(), vanilla);
    }
    public static float environmentGain() {
        return environmentGain;
    }
    public static void applyShake(com.mojang.blaze3d.vertex.PoseStack matrices, float delta) {
        var frame=AtomicRenderer.nearestFrame(delta);
        var client=Minecraft.getInstance();
        if(frame==null||client.player==null)return;
        float gain=AtomicRenderer.distanceGain(client.player.position().distanceTo(frame.center()));
        float strength=AtomicSequence.shake(frame.age());
        if(active()&&frame.age()>=466&&frame.age()<614)
            strength+=AtomicSequence.pressureShake(frame.age(),client.gameRenderer.getMainCamera().getPosition().subtract(frame.center()).horizontalDistance());
        strength*=gain*GodWandConfig.CLIENT.cameraShake;
        double t=frame.age();
        if(active()&&t>=466&&t<614)
            matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float)(5*Math.sin((t-466)/148*Math.PI))));
        matrices.translate(Math.sin(t*1.71)*.055*strength,Math.cos(t*2.13)*.09*strength,0);
        matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees((float)(Math.sin(t*1.67)*1.7*strength)));
        matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float)(Math.sin(t*2.23)*1.1*strength)));
    }
    public static void render(GuiGraphics graphics, float delta) {
        var frame = AtomicRenderer.localFrame(delta);
        if(frame==null)return;
        if(!active()) { releaseHint(graphics,frame); return; }
        float age = frame.age(), envelope = AtomicSequence.letterbox(age);
        int width=graphics.guiWidth(), height=graphics.guiHeight();
        int transition=Math.round(255*AtomicSequence.transitionOpacity(age));
        if(transition>0)graphics.fill(0,0,width,height,(transition<<24)|0x140923);
        int bars=Math.round(height*.105f*envelope);
        graphics.fill(0,0,width,bars,0xFF030109);
        graphics.fill(0,height-bars,width,height,0xFF030109);
        if (age < 100) graphics.drawString(Minecraft.getInstance().font,Component.translatable("gui.godwand.atomic_skip"),
                width-Minecraft.getInstance().font.width(Component.translatable("gui.godwand.atomic_skip"))-10,height-16,0xFFB6A7C9,false);
        int darkness=Math.round(255*AtomicSequence.blackout(age)*(AtomicPostFx.available()?.15f:.95f));
        if(darkness>0)graphics.fill(0,bars,width,height-bars,darkness<<24);
        if(frame.waiting()&&GodWandConfig.CLIENT.impactFrames) {
            int pulse=Math.round(18*AtomicSequence.heartbeat(frame.waitTicks()));
            if(pulse>0)graphics.fill(0,bars,width,height-bars,(pulse<<24)|0xB39BE8);
        }
        if(!AtomicPostFx.available()&&GodWandConfig.CLIENT.impactFrames) {
            int flash=Math.round(255*AtomicSequence.releaseFlash(age));
            if(flash>0)graphics.fill(0,bars,width,height-bars,(flash<<24)|0xF3DEFF);
            if(age>=AtomicSequence.WAIT_TICK+4&&age<AtomicSequence.WAIT_TICK+6)
                graphics.fill(0,bars,width,height-bars,((int)age%2==0?0xFFF1DDFF:0xFF0B0314));
        }
        releaseHint(graphics,frame);
        if (age >= 298 && age < 384) {
            float alpha=AtomicSequence.phase(age,298,312)*(1-AtomicSequence.phase(age,372,384));
            title(graphics,age<320?"I":"I AM",width*.5f,height*.82f,Math.min(1.5f,width/220f),alpha,false);
        }
        if (age >= AtomicSequence.ATOMIC_WORD && age < AtomicSequence.WAIT_TICK) {
            float alpha=AtomicSequence.phase(age,AtomicSequence.ATOMIC_WORD,AtomicSequence.ATOMIC_WORD+2)*(1-AtomicSequence.phase(age,AtomicSequence.WAIT_TICK-4,AtomicSequence.WAIT_TICK));
            float scale=Math.min(width*.36f/Minecraft.getInstance().font.width(lettering("ATOMIC")),6f);
            scale*=1+.1f*(1-AtomicSequence.phase(age,AtomicSequence.ATOMIC_WORD,AtomicSequence.ATOMIC_WORD+8));
            title(graphics,"ATOMIC",width*.5f,height*.30f,scale,alpha,true);
        }
    }
    private static void releaseHint(GuiGraphics graphics,AtomicRenderer.Frame frame) {
        if(!frame.waiting()||frame.waitTicks()<60)return;
        var client=Minecraft.getInstance();
        var text=Component.translatable("gui.godwand.atomic_release");
        int alpha=Math.round(190*AtomicSequence.phase(frame.waitTicks(),60,70));
        if(alpha>3)graphics.drawString(client.font,text,(graphics.guiWidth()-client.font.width(text))/2,
                Math.round(graphics.guiHeight()*.84f),(alpha<<24)|0xC5B8D8,false);
    }
    private static void title(GuiGraphics graphics,String text,float x,float y,float scale,float alpha,boolean impact) {
        if(alpha<.01)return;
        var font=Minecraft.getInstance().font;
        Component letters=lettering(text);
        int a=net.minecraft.util.Mth.clamp(Math.round(alpha*255),0,255);
        graphics.pose().pushPose();
        graphics.pose().translate(x,y,0); graphics.pose().scale(scale,scale,1);
        float left=-font.width(letters)/2f;
        graphics.pose().translate(left,-4,0);
        if(impact) {
            graphics.drawString(font,letters,-1,0,(Math.max(4,a/3)<<24)|0x9D32FF,false);
            graphics.drawString(font,letters,1,0,(Math.max(4,a/3)<<24)|0xDA61FF,false);
        }
        graphics.drawString(font,letters,0,0,(a<<24)|0xFFF0FF,false);
        graphics.pose().popPose();
    }
    private static Component lettering(String text) { return Component.literal(text).withStyle(style -> style.withFont(TITLE_FONT)); }
}
