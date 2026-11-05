package net.godwand.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.godwand.network.FugaFxPayload;
import net.godwand.skill.FugaSequence;
import net.godwand.item.GodWandItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
import java.util.Map;

/** Solid flame geometry shared by every viewer; no particle entities or world writes. */
public final class FugaRenderer {
    public record Frame(int casterId, long startedAt, int phase, double age, Vec3 origin, Vec3 target) {
        public Vec3 center() { return origin; }
        public Vec3 arrowPosition() {
            Vec3 travel = target.subtract(origin);
            double distance = travel.length();
            return distance < .0001 ? origin : origin.add(travel.scale(Math.min(distance, FugaSequence.flightDistance(age)) / distance));
        }
    }
    private static final Map<Integer, FugaFxPayload> ACTIVE = new LinkedHashMap<>();
    private static final Map<Integer, Long> RECEIVED = new LinkedHashMap<>();
    // When this client first saw the current phase; packets can arrive several ticks after the server's phaseAt.
    private static final Map<Integer, Long> PHASE_SEEN = new LinkedHashMap<>();
    // Keep finished tokens as well, so late charge/fire packets cannot resurrect a cancelled cast.
    private static final Map<Integer, FugaFxPayload> LATEST = new LinkedHashMap<>();
    private static ClientLevel world;
    /** Detonations keep scarring the sky and smoking after their packet ends: centre and client time of the blast. */
    record Aftermath(Vec3 center,long at,long cast) {}
    private static final java.util.List<Aftermath> AFTERMATH = new java.util.ArrayList<>();
    static final int AFTERMATH_TICKS = 900;
    private static final double TAU = Math.PI * 2;
    private FugaRenderer() {}

    static boolean newer(FugaFxPayload next, FugaFxPayload previous) {
        return previous == null || next.startedAt() > previous.startedAt()
                || next.startedAt() == previous.startedAt() && previous.phase() < 3
                && next.phase() >= previous.phase() && next.phaseAt() >= previous.phaseAt();
    }
    public static void receive(FugaFxPayload p) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != world) reset();
        world = client.level;
        if (world == null || p.phase() < 0 || p.phase() > 4 || !finite(p.x(),p.y(),p.z(),p.tx(),p.ty(),p.tz())
                || !newer(p,LATEST.get(p.casterId()))) return;
        LATEST.remove(p.casterId()); LATEST.put(p.casterId(),p);
        if (LATEST.size() > 64) LATEST.remove(LATEST.keySet().iterator().next());
        if (p.phase() >= 3) { ACTIVE.remove(p.casterId()); RECEIVED.remove(p.casterId()); return; }
        var previous = ACTIVE.get(p.casterId());
        if (previous == null || previous.startedAt() != p.startedAt()) RECEIVED.put(p.casterId(),world.getGameTime());
        if (previous == null || previous.startedAt() != p.startedAt() || previous.phase() != p.phase()) PHASE_SEEN.put(p.casterId(),clientTicks);
        // Where the arrow left the bow, so the hit-stop can freeze it exactly on the path it flew.
        if (previous != null && previous.phase() == 1 && p.phase() == 2 && previous.startedAt() == p.startedAt())
            LAUNCH.put(p.casterId(), new Vec3(previous.x(), previous.y(), previous.z()));
        ACTIVE.put(p.casterId(), p);
        if (ACTIVE.size() > 16) ACTIVE.remove(ACTIVE.keySet().iterator().next());
        RECEIVED.keySet().retainAll(ACTIVE.keySet()); PHASE_SEEN.keySet().retainAll(ACTIVE.keySet());
    }
    public static void reset() { LAUNCH.clear(); ACTIVE.clear(); RECEIVED.clear(); PHASE_SEEN.clear(); LATEST.clear(); AFTERMATH.clear(); glow = null; world = null; }
    /** The local caster's last impact, so its smouldering ground can keep cooling after the cast has ended. */
    private record Glow(int caster,long token,long phaseAt,Vec3 center) {}
    private static Glow glow;
    static final int GLOW_TICKS = 340;
    /** 0..1 strength of the lingering scorched sky for a viewer at {@code eye}. */
    public static float aftermath(Vec3 eye) {
        if (world == null || eye == null) return 0;
        float best = 0;
        for (var a : AFTERMATH) {
            double t = (world.getGameTime() - a.at()) / (double)AFTERMATH_TICKS;
            if (t < 0 || t >= 1) continue;
            double near = 1 - smooth((eye.distanceTo(a.center()) - 220) / 260);
            best = Math.max(best, (float)(smooth(t * 40) * (1 - smooth(t)) * near));
        }
        return best;
    }
    /** Client ticks since this caster's current phase was first seen here. */
    public static double phaseSeen(int casterId, float partial) {
        Long seen = PHASE_SEEN.get(casterId);
        return world == null || seen == null ? Double.MAX_VALUE : clientTicks + partial - seen;
    }
    // Local tick count: server time packets rewind the game time after a server stall, which held the impact frame on screen.
    private static long clientTicks;
    public static void tick(Minecraft client) {
        clientTicks++;
        if (client.level != world || client.player == null) { reset(); return; }
        ACTIVE.values().removeIf(p -> {
            var caster = world.getEntity(p.casterId());
            long age = world.getGameTime() - p.phaseAt();
            return caster != null && !caster.isAlive() || age < -20
                    || p.phase() == 0 && world.getGameTime()-RECEIVED.getOrDefault(p.casterId(),0L)>20
                    && (!(caster instanceof LivingEntity living) || !living.isUsingItem()
                    || !(living.getUseItem().getItem() instanceof GodWandItem)
                    || GodWandItem.getSkillMode(living.getUseItem()) != GodWandItem.MODE_FUGA)
                    || p.phase() == 1 && age > 80
                    || p.phase() == 2 && age >= net.godwand.skill.FugaSequence.END_TICK;
        });
        RECEIVED.keySet().retainAll(ACTIVE.keySet());
        long now = world.getGameTime();
        for (var p : ACTIVE.values()) {
            // A caster standing still keeps its body facing wherever the last cast left it (vanilla only turns it
            // past ~50 degrees), while the flame and hands follow the aim: keep the body on the head while casting.
            if (p.phase() <= 1 && world.getEntity(p.casterId()) instanceof LivingEntity living) {
                living.yBodyRot = living.yBodyRotO = living.getYHeadRot();
            }
            if (p.phase() == 2 && p.casterId() == client.player.getId())
                glow = new Glow(p.casterId(), p.startedAt(), p.phaseAt(), new Vec3(p.x(), p.y(), p.z()));
        }
        AFTERMATH.removeIf(a -> now - a.at() >= AFTERMATH_TICKS);
        for (var p : ACTIVE.values()) {
            // The hellish sky starts rolling in as the meteor appears, not after the blast.
            if (p.phase() == 2
                    && AFTERMATH.stream().noneMatch(a -> a.cast() == p.startedAt())) {
                AFTERMATH.add(new Aftermath(new Vec3(p.x(), p.y(), p.z()), now, p.startedAt()));
                if (AFTERMATH.size() > 8) AFTERMATH.remove(0);
            }
        }
        if (client.options != null && client.options.particles().get() != net.minecraft.client.ParticleStatus.MINIMAL) particles(client);
    }
    /** Vanilla fire/smoke particles on top of the mesh: embers rise, heat blows out, the crater keeps smoking. */
    private static void particles(Minecraft client) {
        var random = world.getRandom();
        Vec3 eye = client.player.getEyePosition();
        for (int caster : ACTIVE.keySet()) {
            Frame f = frameFor(caster, 0);
            if (f == null || f.origin().distanceToSqr(eye) > 96 * 96 && f.phase() != 1) continue;
            Vec3 o = f.origin();
            // The charge uses only its own mesh embers and cinders; vanilla flame sprites read as campfires.
            if (f.phase() == 1) {
                Vec3 head = f.arrowPosition(), d = f.target().subtract(o).normalize();
                for (int i = 0; i < 14; i++) {
                    Vec3 p = head.subtract(d.scale(random.nextDouble() * 10)).add(random.nextGaussian() * .9, random.nextGaussian() * .9, random.nextGaussian() * .9);
                    world.addAlwaysVisibleParticle(i % 4 == 0 ? net.minecraft.core.particles.ParticleTypes.SMALL_FLAME : net.minecraft.core.particles.ParticleTypes.FLAME, true,
                            p.x, p.y, p.z, random.nextGaussian() * .15, .05 + random.nextDouble() * .15, random.nextGaussian() * .15);
                }
            }
        }
        for (var a : AFTERMATH) {
            double t = (world.getGameTime() - a.at()) / (double)AFTERMATH_TICKS;
            // The crater only smokes once the blast (80 ticks after the meteor appears) has made it.
            if (t < 0 || t >= 1 || world.getGameTime() - a.at() < FugaSequence.ORB_TICKS || a.center().distanceToSqr(eye) > 400 * 400) continue;
            double strength = 1 - t;
            for (int i = 0; i < 1 + 3 * strength; i++) {
                double ang = random.nextDouble() * TAU, r = Math.sqrt(random.nextDouble()) * FugaSequence.RADIUS * 1.2;
                Vec3 p = a.center().add(Math.cos(ang) * r, random.nextDouble() * 2, Math.sin(ang) * r);
                world.addAlwaysVisibleParticle(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true,
                        p.x, p.y, p.z, 0, .06 + .08 * strength, 0);
                if (random.nextFloat() < strength) world.addAlwaysVisibleParticle(net.minecraft.core.particles.ParticleTypes.LAVA, true,
                        p.x, p.y, p.z, 0, 0, 0);
                if (random.nextFloat() < .7 * strength) world.addAlwaysVisibleParticle(net.minecraft.core.particles.ParticleTypes.FLAME, true,
                        p.x, p.y + .2, p.z, random.nextGaussian() * .02, .04 + random.nextDouble() * .1, random.nextGaussian() * .02);
            }
        }
    }
    public static Frame frameFor(int casterId, float partial) {
        if (world == null || world != Minecraft.getInstance().level) return null;
        FugaFxPayload p = ACTIVE.get(casterId);
        if (p == null) return null;
        Vec3 origin = new Vec3(p.x(),p.y(),p.z()), target = new Vec3(p.tx(),p.ty(),p.tz());
        if (p.phase() == 0) {
            var caster = world.getEntity(casterId);
            if (caster != null) {
                origin = caster.getPosition(partial);
                target = origin.add(caster.getViewVector(partial).scale(160));
            }
        }
        if (p.phase() == 2 && phaseSeen(casterId, partial) < HIT_STOP) {
            // Hit-stop: for a few ticks the arrow hangs frozen in its impact before the meteor shot, on the path it flew.
            Vec3 launch = LAUNCH.get(casterId);
            if (launch == null) {
                Vec3 dir = target.subtract(origin);
                dir = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : dir.normalize();
                launch = origin.subtract(dir.scale(FugaSequence.flightDistance(FREEZE_AGE)));
            }
            return new Frame(casterId, p.startedAt(), 1, flightTime(launch.distanceTo(origin)), launch, origin);
        }
        return new Frame(casterId,p.startedAt(),p.phase(),Math.max(0,world.getGameTime()+partial-p.phaseAt()),origin,target);
    }
    /** Client ticks of hit-stop after the arrow lands, then one tick per impact-frame beat before the sphere shot. */
    static final double IMPACT_BEATS = 4, HIT_STOP = net.godwand.skill.FugaSequence.TRANSITION_TICKS - IMPACT_BEATS, FREEZE_AGE = 30;
    /** 0..1 through the hit-stop of this caster's landing; -1 outside it. */
    static double hitStop(int casterId) { return hitStop(casterId, renderPartial); }
    static double hitStop(int casterId, float partial) {
        FugaFxPayload p = ACTIVE.get(casterId);
        if (p == null || p.phase() != 2) return -1;
        double seen = phaseSeen(casterId, partial);
        return seen < HIT_STOP ? seen / HIT_STOP : -1;
    }
    /** Impact-frame beat for the post shader: the release flash, then four distinct beats after the hit-stop. */
    public static float impactBeat(int casterId, float partial) {
        FugaFxPayload p = ACTIVE.get(casterId);
        if (p == null) return 0;
        double seen = phaseSeen(casterId, partial);
        if (p.phase() == 1) return seen < 3 ? 1 : 0;
        // The hit itself lands on one plain beat; after the hold the four beats lead into the sphere.
        if (p.phase() == 2 && seen < 1) return 1;
        if (p.phase() == 2 && seen >= HIT_STOP && seen < HIT_STOP + IMPACT_BEATS) return 1 + (int)(seen - HIT_STOP);
        return 0;
    }
    private static float renderPartial;
    private static final Map<Integer, Vec3> LAUNCH = new java.util.HashMap<>();
    /** Ticks the arrow takes to fly this far (inverse of FugaSequence.flightDistance). */
    static double flightTime(double distance) {
        double lo = 0, hi = 400;
        for (int i = 0; i < 40; i++) { double mid = (lo + hi) / 2; if (FugaSequence.flightDistance(mid) < distance) lo = mid; else hi = mid; }
        return hi;
    }
    public static Frame localFrame(float partial) {
        var player = Minecraft.getInstance().player;
        if (player == null) return null;
        var frame = frameFor(player.getId(),partial);
        return frame != null ? frame : afterglow(player.getId(), partial);
    }
    /** The cast the post shader draws: your own, otherwise the nearest one in view range, so observers see the same
     *  volumetric fire instead of the mesh stand-ins. */
    public static Frame shaderFrame(float partial) {
        Frame own = localFrame(partial);
        var player = Minecraft.getInstance().player;
        if (own != null || player == null) return own;
        Frame best = null; double bestDistance = 512 * 512;
        for (int caster : ACTIVE.keySet()) {
            Frame f = frameFor(caster, partial);
            if (f == null || f.phase() > 2) continue;
            double d = (f.phase() == 2 ? f.center() : f.origin()).distanceToSqr(player.position());
            if (d < bestDistance) { best = f; bestDistance = d; }
        }
        return best;
    }
    /** Caster id the post shader draws this frame; its mesh stand-ins step back for the volume. */
    private static int shaderCaster = Integer.MIN_VALUE;
    /** After the local cast ends, a frame past END_TICK that only drives the post shader's cooling ground glow;
     *  the director, mesh and HUD all ignore frames that old. */
    private static Frame afterglow(int caster, float partial) {
        if (glow == null || world == null || glow.caster() != caster || ACTIVE.containsKey(caster)) return null;
        double age = world.getGameTime() + partial - glow.phaseAt();
        return age >= FugaSequence.END_TICK && age < FugaSequence.END_TICK + GLOW_TICKS
                ? new Frame(caster, glow.token(), 2, age, glow.center(), glow.center()) : null;
    }
    /** -1 emits both layers (tests); 0 is the alpha-blended flame body, 1 the additive hot core. */
    private static int pass = -1;
    /** Flame-fibre texture: rows 0-55 hold fibres that tile along U, rows 56-63 are solid for untextured fills. */
    private static final ResourceLocation FLAME_TEXTURE = ResourceLocation.fromNamespaceAndPath("godwand","fuga_flame");
    private static boolean textureReady;
    // Edges sample texel centres of rows 0/55: V=0 would wrap into the solid rows and leak an opaque rim.
    static final float SOLID_U = .5f, SOLID_V = 60/64f, EDGE_V = .5f/64, SPINE_V = 28/64f, FAR_V = 55.5f/64;
    /** World clock that scrolls the fibres; 0 in headless tests. */
    private static double flow;
    private static void ensureFlameTexture() {
        if (textureReady) return;
        var pixels = new com.mojang.blaze3d.platform.NativeImage(256,64,false);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 256; x++) pixels.setPixelRGBA(x,y,(flameAlpha(x,y)<<24)|0xFFFFFF);
        var texture = new net.minecraft.client.renderer.texture.DynamicTexture(pixels);
        texture.setFilter(true,false);
        Minecraft.getInstance().getTextureManager().register(FLAME_TEXTURE,texture);
        textureReady = true;
    }
    /** Cel-style ribbon, periodic along U so strands scroll forever: a hard, torn rim that reaches in and out
     *  along the strand, with long slits cut through the body like the dark gaps between the reference's ribbons. */
    static int flameAlpha(int x,int y) {
        if (y >= 56) return 255;
        double u = x/256.0, w = Math.abs(y-27.5)/27.5;
        int edge = y < 28 ? 0 : 1;
        double rim = .38+.5*fibre(u*6,edge*5+1.3,6)+.2*fibre(u*19,edge*7+4.1,19);
        double slit = fibre(u*6,w*4.5+8,6)*(1-w*.5);
        // A soft-edged rim: crisp edges made the fire read as glowing polygons and blades.
        double f = Math.max(0,Math.min(1,Math.min((rim-w)*6,(.64-slit)*5)));
        return (int)Math.round(255*f);
    }
    private static double fibre(double x,double y,int period) {
        int ix=(int)Math.floor(x),iy=(int)Math.floor(y);double fx=x-ix,fy=y-iy;fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);
        int x0=Math.floorMod(ix,period),x1=Math.floorMod(ix+1,period);
        double a=hash(x0,iy),b=hash(x1,iy),c=hash(x0,iy+1),d=hash(x1,iy+1);
        return a+(b-a)*fx+(c-a)*fy+(a-b-c+d)*fx*fy;
    }
    public static void render(PoseStack pose, Camera camera, float partial) {
        if (world == null || world != Minecraft.getInstance().level || ACTIVE.isEmpty() && AFTERMATH.isEmpty()) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
        ensureFlameTexture();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0,FLAME_TEXTURE);
        flow = world.getGameTime() + partial;
        renderPartial = partial;
        try {
            Frame local = localFrame(partial);
            Frame shaded = shaderFrame(partial);
            shaderCaster = shaded == null ? Integer.MIN_VALUE : shaded.casterId();
            double darkness = local!=null ? 1-FugaCinematic.environmentGain() : 0;
            if (local != null && local.phase() == 0 && local.age() < 176 && darkness > 0) {
                BufferBuilder backdrop = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
                emitBackdrop(backdrop,pose.last().pose(),local,camera.getPosition(),darkness);
                var backdropMesh = backdrop.build();
                if (backdropMesh != null) BufferUploader.drawWithShader(backdropMesh);
            }
            // MIN_VALUE stands for the lingering storms, which outlive every cast packet.
            java.util.List<Integer> casters = new java.util.ArrayList<>(ACTIVE.keySet());
            if (!AFTERMATH.isEmpty()) casters.add(Integer.MIN_VALUE);
            for (pass = 0; pass < 2; pass++) {
            if (pass == 1) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            else RenderSystem.defaultBlendFunc();
            for (int caster : casters) {
                Frame frame = caster == Integer.MIN_VALUE ? null : frameFor(caster,partial);
                if (frame == null && caster != Integer.MIN_VALUE) continue;
                BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
                if(frame==null) storms(buffer,pose.last().pose(),camera.getPosition());
                else if(frame.phase()!=0 && AtomicPostFx.available() && shaderCaster==caster && AtomicRenderer.nearestFrame(partial)==null) accents(buffer,pose.last().pose(),frame,camera.getPosition());
                else emit(buffer,pose.last().pose(),frame,camera.getPosition(),world.getMinBuildHeight()-frame.origin().y);
                var mesh = buffer.build();
                if (mesh != null) BufferUploader.drawWithShader(mesh);
            }
            }
        } finally {
            pass = -1;
            RenderSystem.depthMask(true); RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
        }
    }
    private static boolean valid(Frame f, Vec3 eye) {
        return Double.isFinite(f.age()) && finite(f.origin().x,f.origin().y,f.origin().z,f.target().x,f.target().y,f.target().z,eye.x,eye.y,eye.z)
                && f.age() >= 0 && f.phase() >= 0 && f.phase() <= 2 && !(f.phase() == 2 && f.age() >= FugaSequence.END_TICK);
    }
    /** Extras drawn over the post-processed volume: pressure rings, sparks, rays, debris. */
    private static void accents(VertexConsumer v,Matrix4f m,Frame f,Vec3 eye) {
        if(!valid(f,eye))return;
        Vec3 base=f.origin().subtract(eye);
        if(f.phase()==1) { launch(v,m,f,base); streamers(v,m,f,base); lingeringTrail(v,m,f,base); impactPoint(v,m,f,base); }
        else if(f.phase()==2) impactAccents(v,m,FugaSequence.visualAge(f.age()),base);
    }
    /** The plane stays behind the subject, leaving their existing depth buffer visible. */
    public static void emitBackdrop(VertexConsumer v, Matrix4f m, Frame frame, Vec3 eye, double alpha) {
        if (frame.phase() != 0 || frame.age() < 0 || frame.age() >= 176 || alpha <= 0) return;
        Vec3 aim=frame.target().subtract(frame.origin());
        Vec3 view=eye.subtract(frame.origin());
        Vec3 facing=direction(new Vec3(view.x,0,view.z).lengthSqr()<.0001?new Vec3(aim.x,0,aim.z):new Vec3(view.x,0,view.z));
        // Close behind the subject for the opening; during the silence (screen fully black) it moves back so the
        // whole vortex turns in front of it, and a black floor hides the ground: the same fire is seen on black,
        // then in the world as both fade.
        double back=frame.age()<FugaPose.CLAP+2?.4:3.4;
        Vec3 center=frame.origin().subtract(facing.scale(back)).add(0,1.5,0).subtract(eye);
        Vec3 right=side(facing).scale(64),up=new Vec3(0,64,0);
        quad(v,m,center.subtract(right).subtract(up),center.add(right).subtract(up),
                center.add(right).add(up),center.subtract(right).add(up),0,alpha);
        // High enough off the grass not to z-fight with it (a flickering background under camera shake).
        Vec3 floor=frame.origin().add(0,.09,0).subtract(eye),across=side(facing).scale(48),depth=facing.scale(48);
        quad(v,m,floor.subtract(across).subtract(depth),floor.add(across).subtract(depth),
                floor.add(across).add(depth),floor.subtract(across).add(depth),0,alpha);

    }
    /** Camera-relative QUADS, usable without a Minecraft instance or GPU. */
    public static void emit(VertexConsumer v, Matrix4f matrix, Frame f, Vec3 eye) {
        emit(v,matrix,f,eye,-384);
    }
    /** Floor is relative to the impact anchor; only the column extends below that anchor. */
    public static void emit(VertexConsumer v, Matrix4f matrix, Frame f, Vec3 eye, double floor) {
        if (!valid(f,eye) || !Double.isFinite(floor)) return;
        Vec3 base = f.origin().subtract(eye);
        if (f.phase() == 0) ritualRibbon(v,matrix,f,base);
        else if (f.phase() == 1) { flight(v,matrix,f,base); launch(v,matrix,f,base); streamers(v,matrix,f,base); lingeringTrail(v,matrix,f,base); impactPoint(v,matrix,f,base); }
        else {
            double age=FugaSequence.visualAge(f.age());
            impactVolume(v,matrix,age,base,Math.min(0,floor)); impactAccents(v,matrix,age,base);
        }
    }
    /** Pull look drawn by the shader: a burning ball between the palms (true) or one stretched band (false).
     *  Must match PULL_BALL in godwand_atomic.fsh. */
    static final boolean PULL_BALL=true;
    /** 0..1 how strongly the post shader draws the pulled flame between the palms as a volume (until the clap). */
    public static double ribbonVolume(double age) { return FugaCinematic.smooth(age,24,40)*(1-FugaCinematic.smooth(age,FugaPose.CLAP-2,FugaPose.CLAP+1)); }
    /** Axis the shader works in: the aim, or while the flame is pulled, the raw palm-to-palm span (its length is the span). */
    public static Vec3 shaderAxis(Frame f) {
        if(f.phase()==0 && ribbonVolume(f.age())>0) {
            Vec3 span=chargeHand(f,f.origin(),false).subtract(chargeHand(f,f.origin(),true));
            if(span.lengthSqr()>1e-4)return span;
        }
        return f.target().subtract(f.origin()).normalize();
    }
    static Vec3 chargeHand(Frame f,Vec3 base,boolean rightHand) {
        Vec3 d=direction(f.target().subtract(f.origin()));
        Vec3 forward=direction(new Vec3(d.x,0,d.z)),right=side(forward);
        var hand=FugaPose.hand(f.age(),(float)-Math.asin(d.y),rightHand,FugaAimRig.availableFor.test(f.casterId()));
        // The flame grips the upper hand surface rather than disappearing inside the arm cuboid.
        double surface=.13*FugaCinematic.smooth(f.age(),144,190);
        return base.add(right.scale(hand.x)).add(0,hand.y+surface,0).add(forward.scale(hand.z));
    }
    /** Ritual: a living flame stretched between the palms; drawing tightens it into a nocked arrow. */
    /** Ritual in three beats: gather (24-96), compress (96-144), tension into the drawn shot (144+). */
    private static void ritualRibbon(VertexConsumer v,Matrix4f m,Frame f,Vec3 base) {
        Vec3 a=chargeHand(f,base,true),b=chargeHand(f,base,false);
        double age=f.age(),gain=FugaCinematic.smooth(age,24,40),draw=FugaCinematic.smooth(age,144,190);
        if(gain<=0)return;
        Vec3 aim=direction(f.target().subtract(f.origin()));
        double span=a.distanceTo(b),gather=FugaCinematic.smooth(age,24,96),press=FugaCinematic.smooth(age,96,144);
        // Side-on stance: nock at the rear hand by the chin, head out past the bow hand along the aim.
        Vec3 tip=b.lerp(b.add(aim.scale(1.6)),draw);
        Vec3 axis=direction(tip.subtract(a)),end=tip.subtract(axis.scale(.35*draw));
        // Gathering fattens the living flame; compressing squeezes it thinner and hotter; the draw makes it a shaft.
        double width=Mth.lerp(draw,(.06+.08*Math.min(1,span/.8))*(1+.35*gather-.3*press),.07);
        double tremble=.035*press*(1-draw)*Math.sin(age*2.3);
        // For the local caster with the post shader the pulled flame is a real volume; its mesh body steps back.
        boolean volume=pass>=0&&AtomicPostFx.enabled()&&shaderCaster==f.casterId()&&AtomicRenderer.nearestFrame(0)==null;
        // With the ball the old pulled-ribbon mesh never shows (it came back between the palms at the clap); it only returns as the drawn shaft.
        double mesh=gain*(volume?PULL_BALL?Math.max(draw,crackle(age)):1-ribbonVolume(age):1);
        fireBody(v,m,a,end,width,(.06+tremble)*(1-draw),age*(1+press),mesh,0);
        fireBody(v,m,a,end.subtract(axis.scale(.1*draw)),width*.62,(.09-tremble)*(1-draw),age*(1.4+press),mesh*.6,5);
        // White-hot core runs the full length, hotter as the energy is pressed in.
        line(v,m,a,end,width*(.16+.14*press),width*(.1+.1*press),0xFFF6E0,true,mesh*(.3+.35*press));
        streaks(v,m,a,end,width*1.1,age,mesh*.5,5);
        // Soft flames wrap and roll around the ribbon as they stream along it, instead of tongues standing straight up.
        Vec3 ru=side(axis),rw=axis.cross(ru).normalize();
        for(int i=0;i<40;i++) {
            double t=(hash(i,31)+age*(.016+.01*hash(i,32)))%1,turn=hash(i,33)*TAU+age*(.3+.15*press)+t*7;
            double rad=width*(.35+.55*Math.sin(Math.PI*t))*(.7+.5*hash(i,34));
            Vec3 at=a.lerp(end,t).add(ru.scale(Math.cos(turn)*rad)).add(rw.scale(Math.sin(turn)*rad));
            // Small enough that the ribbon stays readable: they wrap it, they do not swallow it.
            double size=width*(.45+.55*Math.sin(Math.PI*t))*(.7+.6*hash(i,35)),fade=mesh*Math.sin(Math.PI*t);
            int body=t<.35?0xFFA838:t<.7?0xF0580C:0xC82008;
            flame(v,m,at,size,body,fade*.45,false);
            if(i%2==0)flame(v,m,at,size*.45,0xFFE08A,fade*.4,true);
        }
        // No halo behind the small ball (its faceted glow read as a flickering background); the ball glows by itself.
        boolean ballOn=volume&&PULL_BALL&&ribbonVolume(age)>0;
        light(v,m,a.lerp(end,.5),tip,aim,age,ballOn?gain*(1-ribbonVolume(age)):gain,press,draw);
        // The pale inflow/wind streak field read as scratches; only its warm ground glow stays under the vortex.
        glowDisc(v,m,base.add(0,.03,0),1+2*gather-.4*press,0xFF6A1A,gain*(.12+.2*press+.1*draw));
        if(draw>0) {
            // The held shot: energy keeps pouring in while fire draws the arrow.
            charging(v,m,tip,axis,age,gain*draw);
            // The arrow is flowing fire shaped by its own flow; no solid arrow body is drawn.
            flowArrow(v,m,a,tip,axis,age,gain*draw,draw);
        }
        // Embers curl along the ribbon itself, spiralling around it toward the far hand, instead of floating up.
        Vec3 u=side(axis),w=axis.cross(u).normalize();
        if(volume&&PULL_BALL) {
            // Fire sparks (plain hot points, no trails) are pulled in from all around and whirl chaotically round the
            // small ball as they fall into it, feeding it: each orbits faster the closer it gets, jittering as it goes.
            Vec3 core=a.lerp(b,.5);
            // An accretion disk: sparks spiral into the ball in one plane tilted toward the viewer, orbiting faster as
            // they near it (like matter falling into a black hole), with only a thin scatter off the disk.
            // The disk turns about the palm-to-palm axis so the hands sit off its plane, tilted toward the viewer.
            Vec3 fwd=direction(new Vec3(aim.x,0,aim.z)),spanAxis=direction(a.subtract(b));
            Vec3 toward=direction(fwd.scale(-1).subtract(spanAxis.scale(spanAxis.dot(fwd.scale(-1)))));
            Vec3 normal=direction(spanAxis.scale(Math.cos(.7)).add(toward.scale(Math.sin(.7))));
            Vec3 e1=direction(normal.cross(new Vec3(0,1,0))),e2=direction(normal.cross(e1));
            for(int i=0;i<140;i++) {
                double life=(hash(i,131)+age*(.045+.035*hash(i,132)))%1;
                double r0=.25+.5*hash(i,135),rad=r0*Math.pow(1-life,.75)+.012;
                double theta=hash(i,133)*TAU+.22*(1/(rad+.05)-1/(r0+.05))+age*.04;
                double off=(hash(i,134)-.5)*.07*rad/r0;
                Vec3 at=core.add(e1.scale(Math.cos(theta)*rad)).add(e2.scale(Math.sin(theta)*rad)).add(normal.scale(off));
                // Full strength from the moment they appear; they only vanish as they are swallowed by the ball.
                double fade=(gain>0?1:0)*(1-smooth((life-.85)/.15))*(1-FugaCinematic.smooth(age,FugaPose.CLAP-1,FugaPose.CLAP+1));
                // Never through the palms: sparks fade out where they would pass a hand.
                fade*=smooth((Math.min(at.distanceTo(a),at.distanceTo(b))-.09)/.08);
                dot(v,m,at,.011+.01*hash(i,138),i%3==0?0xFFF4D0:0xFFB040,fade,true);
            }
        } else for(int i=0;i<90;i++) {
            // Tight to the ribbon: the curl hugs the flame instead of flying off it.
            // Hashed per ember: a regular spacing lined them up into a bead necklace.
            double t=(hash(i,1)+age*(.012+.01*press)*(.7+.6*hash(i,2)))%1,turn=hash(i,3)*TAU+t*TAU*2*(.6+.8*hash(i,4))+age*.22;
            double rad=width*(.3+.6*hash(i,5))*(.7+.4*Math.sin(Math.PI*t))+.01;
            Vec3 p=a.lerp(end,t).add(u.scale(Math.cos(turn)*rad)).add(w.scale(Math.sin(turn)*rad));
            Vec3 trail=axis.scale(-.05).add(u.scale(Math.sin(turn)*rad*.6)).subtract(w.scale(Math.cos(turn)*rad*.6));
            ember(v,m,p,trail,.016+.012*Math.sin(Math.PI*t),i%4==0?0xFFF4D0:0xFFB040,gain*Math.sin(Math.PI*t)*.9);
        }
        clap(v,m,a.lerp(b,.5),age);
        double ignite=FugaCinematic.smooth(age,FugaPose.CLAP+4,FugaPose.CLAP+14);
        // Everything is laid out from the aim heading, so the whole charge turns with the aim.
        double heading=Math.atan2(aim.z,aim.x);
        // A little lighter in the hold so the arrow and the aim stay readable.
        vortex(v,m,base,heading,age,ignite*(1-.35*draw),1);
        ground(v,m,base,heading,age,ignite*(.45+.55*draw));
        cinders(v,m,base,heading,age,ignite);
        // Heat aura: soft billows of hot air keep rising off the caster.
        for(int i=0;i<18;i++) {
            double life=(i*.618034+age*.018)%1,ang=heading+i*2.399963+age*.05;
            puff(v,m,base.add(Math.cos(ang)*(.5+.7*life),.3+life*3.2,Math.sin(ang)*(.5+.7*life)),.35+.7*life,i%3==0?0xFFA040:0xFF5014,ignite*.09*Math.max(.001,Math.sin(Math.PI*life)),true);
        }
    }
    /** Electric crackle of the pulled flame around the ball: the old ribbon flashes for a single tick now and then,
     *  more often as the clap nears, and twice right before the palms meet. 0 between flashes. */
    static double crackle(double age) {
        if (age < 40 || age >= FugaPose.CLAP) return 0;
        int tick = (int)Math.floor(age);
        if (tick == (int)FugaPose.CLAP - 2 || tick == (int)FugaPose.CLAP - 4) return 1;
        double chance = .07 + .4 * smooth((age - (FugaPose.CLAP - 22)) / 20);
        return hash(tick, 211) < chance ? .55 + .45 * hash(tick, 212) : 0;
    }
    /** The palms slam shut on the flame: it bursts out between them for a few ticks. */
    private static void clap(VertexConsumer v,Matrix4f m,Vec3 c,double age) {
        double t=age-FugaPose.CLAP;
        if(t<-1||t>=10)return;
        double k=Math.max(0,t)/10,fade=(1-k)*(1-k)*FugaCinematic.smooth(t,-1,0);
        puff(v,m,c,.12+.5*k,0xFFF2D0,fade*.45,true);
        puff(v,m,c,.25+.9*k,0xFF6A18,fade*.25,true);
        for(int i=0;i<14;i++) {
            double a=i*TAU/14+.3;
            Vec3 out=new Vec3(Math.cos(a),.35*Math.sin(i*1.7),Math.sin(a));
            ember(v,m,c.add(out.scale(.15+1.6*k)),out.scale(-.25),.03*(1-k)+.012,i%3==0?0xFFFFFF:0xFFC060,fade);
        }
    }
    static final int STREAMS=10;
    /** One shared flow field for the whirlpool: stream {@code k} at {@code t} (0 outer rim .. 1 centre, rising).
     *  Outer streams roll one way, inner ones faster the other way, so flames, body and embers all move together. */
    static Vec3 stream(Vec3 base,double heading,double age,int k,double t,double spread) {
        boolean inner=k>=6;
        // Hugs the ground: a rolling ring of fire, not flames standing up.
        double phase=heading+k*TAU/(inner?4:6)+(inner?-.13:.075)*age,outer=inner?1.6:3.4,lift=inner?.8:.35;
        double a=phase+(inner?-1:1)*t*2.8,rr=spread*(.45+outer*(1-t));
        double wave=.18*Math.sin(t*7+age*.23+k)+.1*Math.sin(t*13-age*.37+k*2.1);
        return base.add(Math.cos(a)*(rr+wave),.06+lift*t*t*(1+.15*Math.sin(age*.19+k))/spread+.12*Math.sin(t*9-age*.3+k),Math.sin(a)*(rr+wave));
    }
    /** Hellfire whirlpool at the caster's feet: soft, elongated flame sprites ride the shared flow, hot at birth and
     *  cooling to red as they curl in and rise, over a soft glowing bed of the same streams, so the fire blends into one
     *  churning mass instead of hard glowing lines. {@code spread} > 1 blows it out on release. */
    static void vortex(VertexConsumer v,Matrix4f m,Vec3 base,double heading,double age,double gain,double spread) {
        if(gain<=0)return;
        int n=14;Vec3[] p=new Vec3[n];double[] r=new double[n],al=new double[n];
        for(int k=0;k<STREAMS;k++) {
            double thick=k>=6?.55:.8;
            for(int i=0;i<n;i++) {
                double t=i/(n-1.0),swell=Math.sin(Math.PI*Math.min(1,t*1.15));
                p[i]=stream(base,heading,age,k,t,spread);
                r[i]=thick*swell*(.6+.4*noise(t*6-age*.012*6,k*3.1));
                al[i]=gain*.32*Math.pow(swell,.7)*smooth((p[i].length()-.9)/1.4);
            }
            strand(v,m,p,r,al,n,0xC8280A,true,false);
        }
        for(int i=0;i<80;i++) {
            int k=i%STREAMS;
            double life=(hash(i,21)+age*(.014+.012*hash(i,22)))%1,t=Math.min(1,life*1.1);
            Vec3 at=stream(base,heading,age,k,t,spread).add((hash(i,23)-.5)*.5,(hash(i,24)-.5)*.15+life*life*.3,(hash(i,25)-.5)*.5);
            Vec3 flowDir=stream(base,heading,age,k,Math.min(1,t+.04),spread).subtract(stream(base,heading,age,k,Math.max(0,t-.04),spread));
            double size=(.28+.42*Math.sin(Math.PI*life))*(.7+.6*hash(i,26))*(k>=6?.85:1);
            double fade=gain*Math.sin(Math.PI*Math.min(1,life*1.2))*smooth((at.length()-.9)/1.4);
            // Colour over life: yellow-white at birth, orange, then deep red as it burns out.
            int body=life<.3?0xFFB040:life<.65?0xF0600E:0xC02008;
            flame(v,m,at,size,body,fade*.55,false,flowDir);
            if(i%2==0)flame(v,m,at.add(0,-size*.15,0),size*.5,0xFFD070,fade*(1-life)*.7,true,flowDir);
        }
    }
    /** Soft, upward-stretched flame sprite: a camera-facing blob, taller than wide, ragged at the rim. */
    private static void flame(VertexConsumer v,Matrix4f m,Vec3 c,double size,int color,double alpha,boolean glow) {
        flame(v,m,c,size,color,alpha,glow,new Vec3(0,1,0));
    }
    /** Same sprite stretched along {@code along} (e.g. a flow), lying in the camera plane: rolling fire, not standing. */
    private static void flame(VertexConsumer v,Matrix4f m,Vec3 c,double size,int color,double alpha,boolean glow,Vec3 along) {
        if(!layer(glow)||size<=0)return;
        Vec3 axis=c.cross(along).cross(c);
        if(axis.lengthSqr()<1e-10)axis=new Vec3(0,1,0).cross(c).cross(c);
        if(axis.lengthSqr()<1e-10)axis=side(c);
        Vec3 u=direction(axis).scale(size*1.3),r=direction(c.cross(u)).scale(size*.75);
        for(int i=0;i<12;i++) {
            double a0=i*TAU/12,a1=(i+1)*TAU/12,w0=1+.15*Math.sin(a0*3+size*9),w1=1+.15*Math.sin(a1*3+size*9);
            vertex(v,m,c,color,alpha);
            vertex(v,m,c.add(r.scale(Math.cos(a0)*w0)).add(u.scale(Math.sin(a0)*w0*(Math.sin(a0)>0?1:.55))),color,0);
            vertex(v,m,c.add(r.scale(Math.cos(a1)*w1)).add(u.scale(Math.sin(a1)*w1*(Math.sin(a1)>0?1:.55))),color,0);
            vertex(v,m,c,color,alpha);
        }
    }
    /** Ground under the caster: rings expanding out around the flame whirlpool (radial strokes read as vertical spikes from a low lens). */
    private static void ground(VertexConsumer v,Matrix4f m,Vec3 base,double heading,double age,double gain) {
        if(gain<=0)return;
        Vec3 feet=base.add(0,.04,0);
        for(int i=0;i<2;i++) {
            double life=(age*.022+i/2.0)%1;
            ring(v,m,feet,.6+life*7,.2+.3*(1-life),0xFF7A20,gain*(1-life)*.55,true,heading,32);
        }
        // Quick energy rings snapping out from the feet, some tight and fast, some wide.
        for(int i=0;i<2;i++) {
            double life=(age*(i==0?.07:.045)+i*.37)%1;
            ring(v,m,feet.add(0,.05,0),.3+life*(i==0?2.6:4.8),.08+.12*(1-life),i==0?0xFFC060:0xFF8A30,gain*(1-life)*(1-life)*.8,true,heading+i,32);
        }
        // A steady train of pressure pulses pushed out under the aim; the dark smoky one keeps them from reading alike.
        for(int i=0;i<3;i++) {
            double life=(age*.06+i/3.0)%1;boolean dark=i==1;
            ring(v,m,feet.add(0,.03+.01*i,0),.4+life*(3.5+1.5*i),.1+.18*(1-life),dark?0x4A0C02:i==0?0xD8400A:0xFF6A18,gain*(1-life)*(dark?.7:.6),!dark,heading+2*i,32);
        }
    }
    /** Sparks, embers and dark cinders: some ride the vortex flow, others rise and swirl round the caster. */
    private static void cinders(VertexConsumer v,Matrix4f m,Vec3 base,double heading,double age,double gain) {
        if(gain<=0)return;
        // Embers ride the same flow as the fire, curling in along the streams, then lift off the centre in one
        // spiralling updraft, so they read as one current of energy rather than scattered sparkles.
        for(int i=0;i<70;i++) {
            int k=i%STREAMS;
            double life=(hash(i,7)+age*(.011+.008*hash(i,8)))%1,t=Math.min(1,life*1.6);
            Vec3 p=stream(base,heading,age,k,t,1),q=stream(base,heading,age,k,Math.max(0,t-.04),1);
            double up=Math.max(0,life-.6)/.4,a=heading+hash(i,9)*TAU+age*.16+up*4;
            p=p.add(Math.cos(a)*up*(.3+.9*hash(i,12)),up*up*3.4,Math.sin(a)*up*(.3+.9*hash(i,12))).add((hash(i,13)-.5)*.25,0,(hash(i,14)-.5)*.25);
            Vec3 tail=q.subtract(stream(base,heading,age,k,t,1)).scale(.8).add(0,-.03-.05*up,0);
            double fade=gain*Math.sin(Math.PI*life);
            if(i%5==4) dot(v,m,p,.035,0x5A0E04,fade*.8,false);
            else ember(v,m,p,tail,.025+.025*(1-life),i%3==0?0xFFF2C8:0xFF9A30,fade);
        }
        // Sparks bursting up from the ground in every direction, chaotic and quick.
        for(int i=0;i<70;i++) {
            double life=(hash(i,161)+age*(.035+.03*hash(i,162)))%1,a=hash(i,163)*TAU+Math.floor(hash(i,161)+age*(.035+.03*hash(i,162)))*1.7,rr=.4+2.6*hash(i,164);
            double drift=life*(.8+1.6*hash(i,165));
            Vec3 p=base.add(Math.cos(a)*(rr+drift*.4)+Math.sin(age*.7+i)*.08,.05+drift*(1.6+1.6*hash(i,166))-life*life*.6,Math.sin(a)*(rr+drift*.4));
            dot(v,m,p,.02+.02*(1-life),i%3==0?0xFFF4D0:0xFFB040,gain*Math.sin(Math.PI*life),true);
        }
        // Embers lifting off and circling the caster as they climb.
        for(int i=0;i<70;i++) {
            double life=(hash(i,151)+age*(.012+.006*hash(i,152)))%1,a=heading+hash(i,153)*TAU+age*.13+life*5,rr=.6+1.8*life*(.4+.6*hash(i,154));
            Vec3 p=base.add(Math.cos(a)*rr,.2+life*4.4,Math.sin(a)*rr);
            dot(v,m,p,.022+.024*(1-life),i%3==0?0xFFF2C8:0xFFA040,gain*Math.sin(Math.PI*life),true);
        }
    }
    private static void storms(VertexConsumer v,Matrix4f m,Vec3 eye) {
        for(var a:AFTERMATH)storm(v,m,a.center().subtract(eye),world.getGameTime()-a.at());
    }
    /** Aftermath sky: a smoke column feeds a rolling black ceiling lit orange from below, with lightning inside it. */
    static void storm(VertexConsumer v,Matrix4f m,Vec3 base,double ticks) {
        // Counted from the meteor's appearance: the ceiling spreads while it falls, the smoke column only rises after the blast.
        double grow=easeOut((ticks-10)/200.0),fade=smooth((ticks-5)/45.0)*(1-smooth((ticks-AFTERMATH_TICKS*.55)/(AFTERMATH_TICKS*.45)));
        if(fade<=0||ticks<0)return;
        double ceiling=105,spread=50+250*grow;
        for(int i=0;i<36;i++) {
            double h=4+i/36.0*(ceiling-4)*grow,a=h*.045+ticks*.012+i*.7,w=7+h*.09;
            Vec3 c=base.add(Math.cos(a)*w*.5,h,Math.sin(a)*w*.5);
            double column=fade*smooth((ticks-FugaSequence.ORB_TICKS)/40.0);
            puff(v,m,c,w+6,0x1C1410,.5*column,false);
            puff(v,m,c,w*.6,0xFF6A1C,.08*column*(1-h/ceiling),true);
        }
        for(int i=0;i<160;i++) {
            double s=(i+.5)/160,r=spread*Math.sqrt(s),a=i*2.399963+ticks*.0015*(1.2-s);
            double y=ceiling+12*Math.sin(i*1.7)+18*(1-s)+6*Math.sin(ticks*.01+i);
            // Big overlapping billows merge into one rolling ceiling instead of separate blobs.
            double size=(38+26*noise(i*.31,ticks*.008))*(.7+.6*grow),edge=1-smooth((s-.8)/.2);
            Vec3 c=base.add(Math.cos(a)*r,y,Math.sin(a)*r);
            puff(v,m,c,size,i%5==0?0x2A1810:0x120A08,.42*fade*edge,false);
            puff(v,m,c.subtract(0,size*.5,0),size*.85,0xFF5010,(.07+.06*(1-s))*fade*edge*(.75+.25*Math.sin(ticks*.05+i)),true);
        }
        // Lightning: brief jagged bolts tearing through the cloud.
        for(int k=0;k<5;k++) {
            double clock=ticks*.05+k*.29,cycle=Math.floor(clock),life=clock-cycle;
            if(life>.12)continue;
            double flash=(1-life/.12)*fade,a=noise(cycle*3.7,k)*TAU*3,r=spread*.8*noise(k*5.1,cycle*1.9);
            Vec3 at=base.add(Math.cos(a)*r,ceiling-5,Math.sin(a)*r);
            Vec3 heading=new Vec3(noise(cycle,k+9)-.5,-.8,noise(cycle+4,k)-.5).normalize();
            int n=12;Vec3[] p=new Vec3[n];double[] w=new double[n],al=new double[n];
            for(int i=0;i<n;i++) {
                p[i]=at;w[i]=1.6*(1-i/(double)n)+.4;al[i]=flash;
                at=at.add(heading.scale(9)).add((noise(cycle*7+i,k)-.5)*12,(noise(i*3.3,cycle)-.5)*4,(noise(k+i*1.9,cycle*2)-.5)*12);
            }
            strand(v,m,p,w,al,n,0xE8ECFF,true,false);
            puff(v,m,p[0],80,0xFFD8B0,flash*.35,true);
        }
    }
    /** Soft camera-facing billow: dense centre fading to a ragged transparent rim. */
    private static void puff(VertexConsumer v,Matrix4f m,Vec3 c,double size,int color,double alpha,boolean glow) {
        if(!layer(glow)||alpha<=0||size<=0)return;
        Vec3 r=side(c).scale(size),u=direction(c.cross(side(c))).scale(size);
        // 14 segments: soft radial gradients read just as round, and puffs are the most numerous primitive here.
        for(int i=0;i<14;i++) {
            double a0=i*TAU/14,a1=(i+1)*TAU/14,w0=1+.12*Math.sin(a0*3+size)+.06*Math.sin(a0*7+size*2),w1=1+.12*Math.sin(a1*3+size)+.06*Math.sin(a1*7+size*2);
            vertex(v,m,c,color,alpha);
            vertex(v,m,c.add(r.scale(Math.cos(a0)*w0)).add(u.scale(Math.sin(a0)*w0)),color,0);
            vertex(v,m,c.add(r.scale(Math.cos(a1)*w1)).add(u.scale(Math.sin(a1)*w1)),color,0);
            vertex(v,m,c,color,alpha);
        }
    }
    /** Fakes the light the fire throws: layered bloom halos that breathe, hotter as the pressure builds and the head forms. */
    private static void light(VertexConsumer v,Matrix4f m,Vec3 hands,Vec3 tip,Vec3 aim,double age,double gain,double press,double draw) {
        double breathe=.85+.15*Math.sin(age*.37)+.08*Math.sin(age*1.3);
        // Halos stay low: stacked additive light washed the flame out to a white blob.
        puff(v,m,hands,(.8+.6*press)*breathe,0xFF5A14,gain*(.14+.12*press)*(1-.5*draw),true);
        puff(v,m,hands,(.35+.2*press)*breathe,0xFFC870,gain*(.14+.14*press)*(1-.5*draw),true);
        if(draw>0) {
            puff(v,m,tip,2.2*breathe,0xFF4A0C,gain*draw*.22,true);
            puff(v,m,tip,1.1*breathe,0xFFA040,gain*draw*.25,true);
            puff(v,m,tip,.45*breathe,0xFFFFFF,gain*draw*.45,true);
        }
    }
    /** Ring around an axis (pressure rings travelling along the arrow). */
    private static void loop(VertexConsumer v,Matrix4f m,Vec3 c,Vec3 axis,double radius,double width,int color,double alpha) {
        Vec3 u=side(axis),w=axis.cross(u).normalize();
        int n=25;Vec3[] p=new Vec3[n];double[] r=new double[n],a=new double[n];
        for(int i=0;i<n;i++) {
            double t=i*TAU/(n-1);p[i]=c.add(u.scale(Math.cos(t)*radius)).add(w.scale(Math.sin(t)*radius));
            // Torn, uneven band rather than a clean hoop.
            double torn=noise(t*3+radius*2.3,7.7);r[i]=width*(.4+.9*noise(t*5+radius*3,2.2));a[i]=alpha*Math.min(1,.15+1.3*torn*torn);
        }
        strand(v,m,p,r,a,n,color,true);
    }
    /** Soft additive pool of light on the ground. */
    private static void glowDisc(VertexConsumer v,Matrix4f m,Vec3 c,double radius,int color,double alpha) {
        if(!layer(true)||alpha<=0||radius<=0)return;
        for(int i=0;i<32;i++) {
            double a0=i*TAU/32,a1=(i+1)*TAU/32;
            vertex(v,m,c,color,alpha);vertex(v,m,c.add(Math.cos(a0)*radius,0,Math.sin(a0)*radius),color,0);
            vertex(v,m,c.add(Math.cos(a1)*radius,0,Math.sin(a1)*radius),color,0);vertex(v,m,c,color,alpha);
        }
    }
    /** Thin bright streaks braided along the flame, like the line work in the reference sheet of fire. */
    private static void streaks(VertexConsumer v,Matrix4f m,Vec3 a,Vec3 b,double width,double age,double gain,int count) {
        Vec3 along=direction(b.subtract(a)),u=side(along),w=along.cross(u).normalize();
        int n=14;Vec3[] p=new Vec3[n];double[] r=new double[n],al=new double[n];
        for(int k=0;k<count;k++) {
            double lane=(k+.5)/count*2-1;
            for(int i=0;i<n;i++) {
                double t=i/(n-1.0),env=Math.pow(Math.sin(Math.PI*t),.5);
                double drift=Math.sin(t*(4+k%3)-age*(.13+.02*k)+k*1.9);
                p[i]=a.lerp(b,t).add(u.scale((lane*.75+.3*drift)*width*env)).add(w.scale(Math.cos(t*3+k-age*.1)*width*.35*env));
                r[i]=width*(.08+.06*noise(t*5-age*.2,k))*env;al[i]=gain*env*(.7+.3*Math.sin(t*9-age*.3+k));
            }
            strand(v,m,p,r,al,n,k%3==0?0xFFF6C0:0xFFD05A,true);
        }
    }
    /** Four-point glint like the sparkles in the reference. */
    /** A glowing ember: a hot dot with a faint tail along its motion; thin lines alone read as spikes. */
    private static int mixRgb(int a,int b,double t) {
        int r=(int)Math.round(((a>>16)&255)*(1-t)+((b>>16)&255)*t),g=(int)Math.round(((a>>8)&255)*(1-t)+((b>>8)&255)*t),bl=(int)Math.round((a&255)*(1-t)+(b&255)*t);
        return r<<16|g<<8|bl;
    }
    private static void ember(VertexConsumer v,Matrix4f m,Vec3 p,Vec3 tail,double size,int color,double alpha) {
        dot(v,m,p,size,hot(color),alpha,true);
        line(v,m,p,p.add(tail),size*.4,0,color,true,alpha*.55);
    }
    /** Cheap round glow for small particles: a six-sided fan, always emitted so mesh size never depends on age. */
    private static void dot(VertexConsumer v,Matrix4f m,Vec3 c,double size,int color,double alpha,boolean glow) {
        if(!layer(glow))return;
        Vec3 r=side(c).scale(size),u=direction(c.cross(side(c))).scale(size);
        for(int i=0;i<6;i++) {
            double a0=i*TAU/6,a1=(i+1)*TAU/6;
            vertex(v,m,c,color,alpha);
            vertex(v,m,c.add(r.scale(Math.cos(a0))).add(u.scale(Math.sin(a0))),color,0);
            vertex(v,m,c.add(r.scale(Math.cos(a1))).add(u.scale(Math.sin(a1))),color,0);
            vertex(v,m,c,color,alpha);
        }
    }
    private static void glint(VertexConsumer v,Matrix4f m,Vec3 p,double size,double alpha,int color) {
        if(size<=0||alpha<=0)return;
        puff(v,m,p,size*.3,color,alpha*.3,true);
        puff(v,m,p,size*.09,0xFFFFFF,alpha,true);
        Vec3 across=side(p).scale(size*2.2);
        line(v,m,p.subtract(across),p.add(across),size*.03,size*.03,color,true,alpha*.45);
    }
    /** Held shot: a clearly arrow-shaped flame that keeps pulsing like a 2D drawing, never a rigid prop.
     *  Its trail streams far behind; fire sprays off it while energy streaks are sucked back in to sustain it. */
    /** The held shot as flowing fire: flame is born at the point, runs out along four blade edges and then streams
     *  backward as four fins, while more pours down the shaft. Every sprite lives, cools and dies, so the arrow is only
     *  the shape the flow keeps drawing: white at the point, yellow and orange through the head, red at the tails. */
    private static void flowArrow(VertexConsumer v,Matrix4f m,Vec3 nock,Vec3 tip,Vec3 axis,double age,double gain,double draw) {
        if(gain<=0)return;
        Vec3 u=side(axis),w=axis.cross(u).normalize();
        // It grows out of the hand as the draw goes on; each flame joins the flow at its own moment.
        double form=easeOut(draw);
        double L=Math.max(.5,tip.distanceTo(nock)),h=1.1*form,H=.95*form,fin=L*.9*form,spin=age*.03;
        for(int i=0;i<112;i++) {
            int k=i%4;
            double s=(hash(i,121)+age*(.03+.015*hash(i,122)))%1,q=k*Math.PI/2+spin+(hash(i,123)-.5)*.25;
            Vec3 out=u.scale(Math.cos(q)).add(w.scale(Math.sin(q)));
            // Out along the blade edge to the barb, then back as a fin that slowly narrows and curls.
            double head=Math.min(1,s/.3),back=Math.max(0,(s-.3)/.7);
            double x=h*head+fin*back,r=H*head*(1-.55*back)+.06*Math.sin(age*.4+i);
            Vec3 curl=out.cross(axis).scale(.12*back*Math.sin(age*.3+i*1.3));
            Vec3 at=tip.subtract(axis.scale(x)).add(out.scale(r)).add(curl).add(0,back*back*.2,0);
            double size=(.16+.46*Math.sqrt(s))*(.7+.6*hash(i,124)),fade=gain*Math.pow(Math.sin(Math.PI*s),.6);
            int color=s<.12?0xFFF0C0:s<.35?0xFFC040:s<.7?0xF0600E:0xC02008;
            flame(v,m,at,size*(.4+.6*form),color,fade*.6*smooth((draw*1.3-hash(i,128))/.3),s<.35,axis.scale(-1).add(out.scale(head<1?.6:.2)));
        }
        // The shaft: a hot stream pouring from the point back past the nock.
        for(int i=0;i<26;i++) {
            double s=(hash(i,125)+age*.05)%1;
            Vec3 at=tip.subtract(axis.scale(s*(L+1.2))).add(u.scale((hash(i,126)-.5)*.12)).add(w.scale((hash(i,127)-.5)*.12));
            flame(v,m,at,.14+.24*s,s<.25?0xFFF4D0:s<.6?0xFFA030:0xD83010,gain*Math.sin(Math.PI*s)*.7,s<.4,axis.scale(-1));
        }
        // Trails: one big sinuous ribbon of fire and a few thin ones swim far behind the arrow, curling as they go.
        int n=18;Vec3[] p=new Vec3[n];double[] r=new double[n],al=new double[n],rc=new double[n],ac=new double[n];
        for(int k=0;k<4;k++) {
            boolean big=k==0;
            double reach=big?5.2:3.4+.6*k,amp=big?.55:.3,width=big?.32:.09,ph=k*2.1;
            for(int i=0;i<n;i++) {
                double t=i/(n-1.0);
                double sway=Math.sin(t*5.5-age*.32+ph)*amp*t,lift=Math.cos(t*4.2-age*.27+ph*1.3)*amp*.7*t;
                p[i]=nock.subtract(axis.scale(.2+t*reach)).add(u.scale(sway+(big?0:Math.cos(ph)*.25))).add(w.scale(lift+(big?0:Math.sin(ph)*.25)));
                r[i]=width*(1-.75*t)*(.8+.3*Math.sin(t*9-age*.5+k));al[i]=gain*Math.pow(1-t,.9)*(big?.85:.75);
                rc[i]=r[i]*.38;ac[i]=al[i];
            }
            strand(v,m,p,r,al,n,big?0xE8400A:0xF06010,!big,!big?false:true);
            strand(v,m,p,rc,ac,n,0xFFD070,true,false);
        }
        // Fire coils around the shaft, two ribbons racing forward to the point, and pressure rings keep bursting out
        // ahead of it: the shot looks like it can barely be held.
        for(int i=0;i<44;i++) {
            int k=i%2;double s=(hash(i,141)+age*.045)%1,turn=k*Math.PI+s*TAU*2.2+age*.18,rr=(.22+.3*(1-s))*form;
            Vec3 at=nock.lerp(tip,s).add(u.scale(Math.cos(turn)*rr)).add(w.scale(Math.sin(turn)*rr));
            flame(v,m,at,(.1+.18*Math.sin(Math.PI*s))*form,s>.7?0xFFE08A:0xF0600E,gain*Math.sin(Math.PI*s)*.65,s>.7,axis);
        }
        for(int i=0;i<2;i++) {
            double life=(age/26.0+i*.5)%1;
            loop(v,m,tip.add(axis.scale(.2+life*2.2)),axis,(.3+life*2.4)*form,.08*(1-life)+.02,0xFFC060,gain*(1-life)*(1-life)*.9);
        }
        // The point itself: a small white-hot heart.
        dot(v,m,tip,.22,0xFFF8E6,gain*.9,true);
        dot(v,m,tip.subtract(axis.scale(.25)),.35,0xFFB040,gain*.5,true);
    }
    /** Energy pouring into the held shot: embers sucked in from the air, fire drawn onto the point and tightening
     *  rings, all harder the longer it is held. (The arrow itself is {@link #flowArrow}.) */
    private static void charging(VertexConsumer v,Matrix4f m,Vec3 tip,Vec3 axis,double age,double gain) {
        Vec3 flat=tip.cross(axis);flat=flat.lengthSqr()<1e-8?side(axis):flat.normalize();
        Vec3 up=axis.cross(flat).normalize();
        // Energy is sucked in from the air and squeezed onto the head: converging embers and tightening rings.
        // The longer it is held, the wider the pull and the harder the squeeze.
        double held=smooth((age-190)/110);
        for(int i=0;i<100;i++) {
            double life=(i*.618034+age*(.05+.03*held))%1,a=i*2.399963+age*(.15+.12*held),rr=(2.4+1.6*held)*(1-life)+.1;
            Vec3 at=tip.subtract(axis.scale(.4)).add(flat.scale(Math.cos(a)*rr)).add(up.scale(Math.sin(a)*rr));
            ember(v,m,at,tip.subtract(at).scale(.15),.02+.02*life,i%3==0?0xFFF4D0:0xFF9A30,gain*Math.sin(Math.PI*life));
        }
        for(int i=0;i<4;i++) {
            double life=(age*(.045+.04*held)+i/4.0)%1;
            loop(v,m,tip.subtract(axis.scale(.6+.5*life)),axis,(1.4+.8*held)*(1-life)+.15,.06+.06*life,0xFFC060,gain*life*(.6+.4*held));
        }
        for(int i=0;i<16;i++) {
            double life=(hash(i,96)+age*(.04+.03*held))%1,a=hash(i,97)*TAU+age*.2,rr=(1.6+1.4*held)*(1-life);
            Vec3 at=tip.subtract(axis.scale(.5)).add(flat.scale(Math.cos(a)*rr)).add(up.scale(Math.sin(a)*rr));
            flame(v,m,at,.1+.16*life,0xF0600E,gain*Math.sin(Math.PI*life)*(.3+.4*held),false);
        }
    }
    private static void flight(VertexConsumer v, Matrix4f m, Frame f, Vec3 base) {
        Vec3 d=direction(f.target().subtract(f.origin()));
        Vec3 head=base.add(f.arrowPosition().subtract(f.origin()));
        double age=f.age(),length=Math.min(100,age*4+8);
        // A chaotic comet of fire, not a tidy arrow: fat braided body, torn tongues bursting back from the head.
        fireBody(v,m,head.subtract(d.scale(14)),head,1.7,0,age*1.6,1,3);
        fireBody(v,m,head.subtract(d.scale(9)),head.subtract(d.scale(.8)),1.1,.6,age*2.1,.85,7);
        licks(v,m,head.subtract(d.scale(2.5)),head.subtract(d.scale(.3)),1.5,age*1.8,1,10,1,d.scale(-1).add(0,.5,0));
        line(v,m,head.subtract(d.scale(4)),head,1.3,0,0xFFF4DA,true,1);
        for(int k=0;k<10;k++) {
            double a=k*TAU/10+age*.2;Vec3 out=side(d).scale(Math.cos(a)).add(d.cross(side(d)).normalize().scale(Math.sin(a)));
            line(v,m,head,head.subtract(d.scale(7)).add(out.scale(3.2)),.12,.02,0xFFF0D8,true,.45);
        }
        // Long multi-lane wake: lanes spiral out, widen, rise and burn out toward the tail.
        Vec3 u=side(d),w=d.cross(u).normalize();
        int n=24;Vec3[] p=new Vec3[n];double[] r=new double[n],al=new double[n],rc=new double[n],ac=new double[n];
        for(int lane=0;lane<4;lane++) {
            for(int i=0;i<n;i++) {
                double t=i/(n-1.0),angle=lane*TAU/4+t*7-age*.35,radius=(.15+2.2*t)*(.8+.3*Math.sin(lane*1.7));
                p[i]=head.subtract(d.scale(2+length*t)).add(u.scale(Math.cos(angle)*radius)).add(w.scale(Math.sin(angle)*radius*.8)).add(0,t*t*3,0);
                r[i]=(.8+2.4*t)*(.5+.5*noise(t*8-age*.5,lane*3.7));
                al[i]=Math.pow(1-t,1.5)*(lane<2?.75:.55);
                rc[i]=r[i]*.32;ac[i]=al[i]*.9;
            }
            strand(v,m,p,r,al,n,lane%2==0?0xE8360A:0xFF6A12,false);
            if(lane<2)strand(v,m,p,rc,ac,n,0xFFE2A0,true);
        }
        licks(v,m,head.subtract(d.scale(3)),head.subtract(d.scale(Math.min(length,60))),2.2,age,1,14,0,d.scale(-1).add(0,.6,0));
        for(int i=0;i<24;i++) {
            double life=(i*.618034+age*.06)%1;
            Vec3 q=head.subtract(d.scale(3+life*length*.8)).add(u.scale(Math.cos(i*2.4)*(.6+life*3))).add(w.scale(Math.sin(i*2.4)*(.6+life*3))).add(0,life*2.5,0);
            spark(v,m,q,.05+.07*(1-life),1-life);
        }
    }
    /** Speed streaks and flung flame: drawn with or without the post shader, which only paints the bolt itself. */
    private static void streamers(VertexConsumer v,Matrix4f m,Frame f,Vec3 base) {
        Vec3 d=direction(f.target().subtract(f.origin())),head=base.add(f.arrowPosition().subtract(f.origin()));
        double age=f.age();
        for(int i=0;i<16;i++) {
            double life=(i*.618034+age*.09)%1,a=i*2.399963;
            // Tongues hug the shaft and stream backward, like flame blown off a burning arrow.
            Vec3 out=side(d).scale(Math.cos(a)).add(d.cross(side(d)).normalize().scale(Math.sin(a))).add(0,.25,0);
            Vec3 root=head.subtract(d.scale(1.5+life*11)).add(out.scale(.25+life*1.1));
            licks(v,m,root,root.subtract(d.scale(2.2+life*2)).add(out.scale(.3)),.7*(1-life)+.25,age*2+i,(1-life),1,i*1.7,d.scale(-1));
        }
    }
    /** Release: a ring of compressed air bursts forward from the muzzle, with radial shock streaks. */
    /** During the hit-stop the point of impact swells white-hot and a beam shoots up to where the meteor will form. */
    private static void impactPoint(VertexConsumer v,Matrix4f m,Frame f,Vec3 base) {
        double stop=hitStop(f.casterId());
        if(stop<0)return;
        Vec3 head=base.add(f.arrowPosition().subtract(f.origin()));
        double k=smooth(stop);
        puff(v,m,head,.6+3.4*k,0xFFFFFF,.9,true);
        puff(v,m,head,2+7*k,0xFFA040,.45,true);
        skyBeam(v,m,head,k,1);
    }
    /** A white-hot column punching straight up from the impact; `rise` 0..1 grows it, `gain` fades it. */
    static void skyBeam(VertexConsumer v,Matrix4f m,Vec3 foot,double rise,double gain) {
        if(gain<=0)return;
        double top=6+110*rise;
        line(v,m,foot,foot.add(0,top,0),.35+.9*rise,.2+.5*rise,0xFFF6E0,true,gain*.95);
        line(v,m,foot,foot.add(0,top*.8,0),1.2+2.4*rise,.6,0xFF8A30,true,gain*.4);
        for(int i=0;i<6;i++) puff(v,m,foot.add(0,top*(i+.5)/6,0),.8+1.6*rise,0xFFD080,gain*.25,true);
    }
    /** The arrow leaves a burning streak hanging in the air: each point it passed swells, rises and cools to dark red. */
    private static void lingeringTrail(VertexConsumer v,Matrix4f m,Frame f,Vec3 base) {
        Vec3 d=direction(f.target().subtract(f.origin()));
        double span=f.origin().distanceTo(f.target()),age=f.age();
        for(int k=0;k<28;k++) {
            double since=k+(age-Math.floor(age)),at=age-since;
            if(at<0)break;
            double dist=Math.min(span,FugaSequence.flightDistance(at)),life=since/28.0;
            Vec3 p=base.add(d.scale(dist)).add(Math.sin(k*1.7+at)*.15*since*.05,.04*since,Math.cos(k*2.3+at)*.15*since*.05);
            double fade=Math.exp(-since/9)*smooth(since/1.5);
            puff(v,m,p,.45+.09*since,mixRgb(0xFFB040,0x8A1A06,Math.min(1,life*1.6)),fade*.32,true);
            puff(v,m,p.add(0,.1*since,0),.6+.12*since,0x2A120C,fade*.18*life,false);
        }
    }
    private static void launch(VertexConsumer v,Matrix4f m,Frame f,Vec3 base) {
        double age=f.age();if(age>=18)return;
        Vec3 d=direction(f.target().subtract(f.origin())),u=side(d),w=d.cross(u).normalize();
        double k=age/18,fade=(1-k)*(1-k);
        Vec3 muzzle=base.add(d.scale(1.2));
        for(int i=0;i<12;i++) {
            double angle=i*TAU/12+.2;Vec3 out=u.scale(Math.cos(angle)).add(w.scale(Math.sin(angle)));
            Vec3 from=muzzle.add(out.scale(.4+age*.5)).subtract(d.scale(age*.2)),to=from.add(out.scale(1+age*.6)).subtract(d.scale(.8+age*.3));
            line(v,m,from,to,.05*(1-k)+.015,0,0xFFE8C0,true,fade*.45);
        }
        Vec3 feet=base.subtract(0,1.6,0);
        // The release blows the wrapping vortex outward and it burns out.
        vortex(v,m,feet,Math.atan2(d.z,d.x),age*2,1-smooth(age/10),1+age*.4);
        glowDisc(v,m,feet,2.5+age*.6,0xFF7A20,fade*.5);
        if(age<4)for(int i=0;i<10;i++) spark(v,m,muzzle.add(u.scale(Math.cos(i*2.4)*.5)).add(w.scale(Math.sin(i*2.4)*.5)).add(d.scale(i*.15)),.25*(1-age/4),1-age/4);
    }
    /** Visual beats shared with the post shader: descent, squash, implosion, detonation at 40. */
    // Huge and there at full size at once, so that from the ground it covers nearly the whole sky above its landing.
    static double orbRadius(double age) {return 44;}
    /** Swells slightly as it settles and again into the white flash of the blast; it never collapses (mirrors the shader). */
    static double orbScale(double age) {return 1+.05*smooth((age-32)/5)+.05*smooth((age-37)/2.6);}
    /** Gap under the energy mass: it falls from 28 blocks and slows right down near the ground (mirrors the shader). */
    static double orbGap(double age) { return 28*Math.pow(1-Math.max(0,Math.min(1,age/32)),2.2); }
    /** 0..1 crush against the ground after contact: flatter and wider. */
    static double orbSquash(double age) { return smooth((age-32)/5); }
    static double orbCenter(double age) { return orbGap(age)+orbRadius(age)*orbScale(age)*(1-.22*orbSquash(age)); }
    private static void impactVolume(VertexConsumer v, Matrix4f m, double age, Vec3 base, double floor) {
        if(age<40) {
            double k=orbScale(age),radius=orbRadius(age)*k,sy=1-.22*orbSquash(age),sxz=1+.12*orbSquash(age);
            Vec3 center=base.add(0,orbCenter(age),0);
            // Crimson crust, orange mantle, a smaller white heart: a molten mass rather than a white blob.
            ellipsoid(v,m,center,radius*sxz,radius*sy,age,0xB81A06,.7,false);
            ellipsoid(v,m,center,radius*.9*sxz,radius*.9*sy,age+3,0xFF6A18,.5,false);
            ellipsoid(v,m,center,radius*.55*sxz,radius*.55*sy,age+5,0xFFF8EE,.5,true);
            ring(v,m,center.add(0,-radius*sy*.62,0),radius*sxz*.95,.9,0x8A54FF,.4,true);
            for(int i=0;i<20;i++) {
                double a=i*TAU/20+age*.05,lift=Math.sin(i*1.7);
                Vec3 root=center.add(Math.cos(a)*radius*sxz*.9,lift*radius*sy*.7,Math.sin(a)*radius*sxz*.9);
                Vec3 out=new Vec3(Math.cos(a),.6+lift*.3,Math.sin(a)).normalize();
                licks(v,m,root,root.add(out.scale(radius*.25)),radius*.12,age*2,.8,2,i,out.add(0,.8,0));
            }
            return;
        }
        double retract=1-smooth((age-120)/40),height=384*easeOut((age-40)/5)*(1-.85*smooth((age-116)/42)),core=FugaSequence.RADIUS*.75*Math.sqrt(retract);
        if(core<=0)return;
        // Swirling ascending streams with a flared base, instead of a uniform beam.
        int n=26;Vec3[] p=new Vec3[n];double[] r=new double[n],al=new double[n];
        for(int s=0;s<18;s++) {
            for(int i=0;i<n;i++) {
                double t=i/(n-1.0),h=floor+(height-floor)*t;
                double angle=s*TAU/18-h*.028+age*.14;
                double flare=1+.75*Math.exp(-Math.max(h,0)/13),top=1+.35*smooth((h-160)/224);
                double radius=core*flare*top*(.45+.45*noise(s*1.3,h*.02-age*.12));
                p[i]=base.add(Math.cos(angle)*radius,h,Math.sin(angle)*radius);
                r[i]=core*.28*(.6+.4*noise(s*2.1+7,h*.03-age*.08))*taper(h,floor,height);
                al[i]=(s%3==2?.55:.5)*retract*Math.min(1,(height-h)/30+.2);
            }
            strand(v,m,p,r,al,n,s%3==0?0xC81E06:s%3==1?0xF04A0A:0xFFB040,s%3==2);
        }
        for(int i=0;i<n;i++) {
            double t=i/(n-1.0),h=floor+(height-floor)*t;
            p[i]=base.add(Math.sin(h*.05+age*.2)*1.5,h,Math.cos(h*.04-age*.17)*1.5);
            r[i]=core*.42*(1+.9*Math.exp(-Math.max(h,0)/15))*taper(h,floor,height);al[i]=.85*retract;
        }
        strand(v,m,p,r,al,n,0xFFF1D0,true);
        for(int s=0;s<4;s++) {
            for(int i=0;i<n;i++) {
                double t=i/(n-1.0),h=Math.max(0,floor)+(height-Math.max(0,floor))*t,angle=s*TAU/4+1-h*.03+age*.09;
                double radius=core*(1.1+.5*Math.exp(-h/20)+.35*noise(s*4.3,h*.025-age*.04));
                p[i]=base.add(Math.cos(angle)*radius,h,Math.sin(angle)*radius);
                r[i]=core*.08*taper(h,floor,height);al[i]=.4*retract*smooth((h-20)/60);
            }
            strand(v,m,p,r,al,n,s%2==0?0x8A54FF:0x4C8CFF,true);
        }
    }
    private static double taper(double h,double floor,double height) {return Math.min(1,Math.min(h-floor,height-h)/30);}
    private static void impactAccents(VertexConsumer v,Matrix4f m,double age,Vec3 base) {
        if(age<40) {
            double radius=orbRadius(age),bottom=orbCenter(age)-radius*orbScale(age);
            double pressure=1-smooth(bottom/48);
            // Air is driven out from under the falling sphere: dust, lifted debris and heat rings.
            for(int i=0;i<16;i++) {
                // Pressure escalates along the ground as the mass nears it: the dust is driven out faster and thicker.
                double near=1-smooth(orbGap(age)/28),a=i*TAU/16+age*(.02+.01*(i%3)),rr=radius*(1+.08*i)+age*.4+near*24;
                Vec3 from=base.add(Math.cos(a)*rr,.6+(i%4)*.7,Math.sin(a)*rr),to=base.add(Math.cos(a+.5)*(rr+6),1.5+(i%3),Math.sin(a+.5)*(rr+6));
                strand(v,m,new Vec3[]{from,from.lerp(to,.5),to},new double[]{2.4+pressure*3,1.8+pressure*1.5,1.2},new double[]{.3*pressure*(1+1.4*near),.3*pressure*(1+1.4*near),.18*pressure},3,0x6E5E50,false,false);
            }
            for(int i=0;i<24;i++) {
                double life=(i*.618034+age*.03)%1,a=i*2.399963;
                Vec3 p=base.add(Math.cos(a)*radius*(1.3-life*.4),life*bottom*.6,Math.sin(a)*radius*(1.3-life*.4));
                line(v,m,p,p.add(0,.6,0),.35,.35,0x2E241E,false,pressure*(1-life));
            }
            // Energy radiates like the Atomic blast's pressure aura: soft billows blown off the surface,
            // heat rings climbing over the sphere and widening, thin shock strokes and rising shards.
            Vec3 c=base.add(0,orbCenter(age),0);double R=radius*orbScale(age),live=1-smooth((age-37)/2);
            for(int i=0;i<72;i++) {
                double life=(i*.618034+age*.05)%1,y=1-2*((i+.5)/72),band=Math.sqrt(1-y*y),a=i*2.399963+age*.01;
                Vec3 dir=new Vec3(Math.cos(a)*band,y,Math.sin(a)*band);
                puff(v,m,c.add(dir.scale(R*(1+.55*life))),R*(.12+.2*life),i%3==0?0xFF9A40:0xFF4A14,Math.sin(Math.PI*life)*live*.2,true);
            }
            for(int i=0;i<48;i++) {
                double life=(i*.618034+age*.11)%1,a=i*2.399963,e=Math.asin(((i*.37)%1)*1.6-.6);
                Vec3 dir=new Vec3(Math.cos(a)*Math.cos(e),Math.sin(e),Math.sin(a)*Math.cos(e));
                Vec3 from=c.add(dir.scale(R*(1.05+life*.9)));
                line(v,m,from,from.add(dir.scale(5+life*14)),.07*(1-life)+.03,0,i%3==0?0xFFE0A0:0xFF8A30,true,(1-life)*.6*live);
            }
            // Air torn by the fall: speed streaks race up past the meteor's flanks while it is still falling.
            double falling=1-smooth((age-28)/6);
            for(int i=0;i<40;i++) {
                double life=(i*.618034+age*.16)%1,a=i*2.399963,rr=R*(.75+.4*((i*.37)%1));
                Vec3 from=c.add(Math.cos(a)*rr,-R*.3+life*R*2.2,Math.sin(a)*rr);
                line(v,m,from,from.add(0,14+22*((i*.53)%1),0),.12*(1-life)+.05,.02,i%4==0?0xFFF4D8:0xFFA850,true,Math.sin(Math.PI*life)*falling*.55);
            }
            // Molten lumps drip off the underside: they fall glowing, splash on the ground and cool from yellow to a dark crust.
            double under=orbCenter(age)-R*(1-.22*orbSquash(age));
            for(int i=0;i<60;i++) {
                double life=(hash(i,51)+age*(.035+.03*hash(i,52)))%1,a=hash(i,53)*TAU,rr=R*Math.sqrt(hash(i,54))*.85;
                double drop=Math.min(1,life*1.8),landed=Math.max(0,life-1/1.8)/(1-1/1.8),size=.25+.35*hash(i,56);
                Vec3 p=base.add(Math.cos(a)*rr,Math.max(.25,(under+1)*(1-drop*drop)),Math.sin(a)*rr);
                double fade=smooth(life/.06)*(1-smooth((life-.9)/.1))*live;
                dot(v,m,p,size*(1+.3*landed),0x1A0804,fade*.9,false);
                dot(v,m,p.add(0,.02,0),size*(.75-.35*landed),mixRgb(0xFFC048,0x9A1A04,Math.min(1,landed*1.6)),fade*(1-.6*landed),true);
                line(v,m,p,p.add(0,1.2+2*hash(i,55),0),size*.5,0,0xFF8A2A,true,landed>0?0:fade*.5);
                dot(v,m,p.add(0,.03,0),size*2.2,0xFF5010,landed>0?fade*.3*(1-landed):0,true);
            }
            for(int i=0;i<70;i++) {
                double life=(hash(i,61)+age*(.04+.03*hash(i,62)))%1,a=hash(i,63)*TAU,rr=R*(.2+.9*hash(i,64));
                Vec3 p=base.add(Math.cos(a)*(rr+life*4),.3+life*(3+9*hash(i,65))*pressure,Math.sin(a)*(rr+life*4));
                dot(v,m,p,.25+.45*hash(i,66),i%4==0?0x5A2410:0x1E1612,Math.sin(Math.PI*life)*pressure*live,false);
            }
            // The beam from the arrow's impact still stands through the impact-frame beats, then burns out under the mass.
            double beam=1-smooth(age/2.5);
            if(beam>0)skyBeam(v,m,base,1,beam);
            // No collapse: the blast announces itself as a white flare swelling out of the underside.
            double flare=smooth((age-37)/3);
            if(flare>0) {
                puff(v,m,c.subtract(0,R*.6,0),R*(.8+.8*flare),0xFFF4D8,flare*.55,true);
                puff(v,m,c,R*(1.2+flare),0xFFB060,flare*.3,true);
            }
            return;
        }
        double t=age-40,retract=1-smooth((age-120)/40);
        // Energy rays and a horizontal flare tear outward from the detonation.
        // Detonation: a white-blue burst swallows the view for a beat, with a blue shock ring.
        if(t<8) {
            puff(v,m,base.add(0,20,0),60+t*20,0xFFFFFF,(1-t/8)*.9,true);
            // Warm outer veil: a blue one over the white burst desaturated the whole frame to grey.
            puff(v,m,base.add(0,20,0),100+t*30,0xFFA048,(1-t/8)*.3,true);
        }
        if(t<16)for(int i=0;i<36;i++) {
            double a=i*TAU/18+Math.sin(i*3.1)*.2,elev=.15+.5*Math.abs(Math.sin(i*1.9)),len=25+t*4.5;
            Vec3 dir=new Vec3(Math.cos(a)*Math.cos(elev),Math.sin(elev),Math.sin(a)*Math.cos(elev));
            line(v,m,base.add(dir.scale(4+t*1.5)),base.add(dir.scale(4+t*1.5+len)),1.3*(1-t/16)+.2,0,i%3==0?0xB070FF:0xFFE6B0,true,(1-t/16));
        }
        // Flame tongues burst out of the blast in every direction: chaotic, not a tidy dome.
        if(t<60)for(int i=0;i<36;i++) {
            double a=i*2.399963,rr=6+(i*5.3)%30+t*1.2;
            Vec3 out=new Vec3(Math.cos(a),1.2+.6*Math.sin(i*1.7),Math.sin(a));
            Vec3 root=base.add(Math.cos(a)*rr,2+(i%5)*3,Math.sin(a)*rr);
            licks(v,m,root,root.add(out.scale(6)),6+4*(i%3),age*2+i,1-t/60,2,i,out);
        }
        // The shock front races out over the land as a wall of dust and fire, far past the crater.
        if(t<70)for(int k=0;k<2;k++) {
            double front=20+t*(k==0?6.5:4.5),fade=1-t/70;
            for(int i=0;i<24;i++) {
                double a=i*TAU/24+k*.13+Math.sin(i*2.7)*.1;
                Vec3 root=base.add(Math.cos(a)*front,1,Math.sin(a)*front);
                line(v,m,root,root.add(Math.cos(a)*10,6+4*Math.sin(i*1.3),Math.sin(a)*10),3*fade+1,.5,k==0?0x5A4436:0xFF9040,k==1,fade*.5);
            }
        }
        if(t<20)for(int side=-1;side<=1;side+=2)
            line(v,m,base.add(0,6,0),base.add(side*(30+t*12),6,0),3*(1-t/20)+.5,0,0xFFD8A0,true,(1-t/20)*.9);
        // Debris flung out ballistically.
        if(t<70)for(int i=0;i<90;i++) {
            double a=i*2.399963,speed=1+(i%7)*.35,up=1.2+(i%5)*.5;
            double x=Math.cos(a)*speed*t,z=Math.sin(a)*speed*t,y=up*t-.045*t*t;
            if(y<-2)continue;
            Vec3 p=base.add(x,y+2,z);
            line(v,m,p,p.add(Math.cos(a)*1.2,.8,Math.sin(a)*1.2),.7+(i%3)*.4,.4,i%4==0?0xFF7A30:0x2A211C,i%4==0,(1-t/70));
        }
        // Rolling ground fire around the base of the column.
        for(int i=0;i<16;i++) {
            double a=i*TAU/16+age*.01,rr=FugaSequence.RADIUS*(.9+.25*Math.sin(i*2.3));
            Vec3 root=base.add(Math.cos(a)*rr,.5,Math.sin(a)*rr);
            licks(v,m,root,root.add(Math.cos(a+1.57)*8,0,Math.sin(a+1.57)*8),3*retract+.6,age*1.5,.75*retract+.15*smooth((age-110)/10),2,i,new Vec3(Math.cos(a)*.3,1,Math.sin(a)*.3));
        }
        if(retract>0)for(int arm=0;arm<8;arm++) {
            int n=16;Vec3[] p=new Vec3[n];double[] r=new double[n],al=new double[n];
            for(int i=0;i<n;i++) {
                double q=i/(n-1.0),a=arm*TAU/8+age*.06-q*2.2,rr=FugaSequence.RADIUS*(1.1+q*1.2);
                p[i]=base.add(Math.cos(a)*rr,1+q*14,Math.sin(a)*rr);r[i]=2+4*q;al[i]=retract*.35*Math.sin(Math.PI*q);
            }
            strand(v,m,p,r,al,n,arm%2==0?0x7A6A60:0xFF9050,arm%2==1);
        }
        if(t<60)for(int i=0;i<12;i++) {
            double life=(i*.618034+age*.05)%1,a=i*2.399963;
            glint(v,m,base.add(Math.cos(a)*(20+i*5%40),6+(i*13)%60,Math.sin(a)*(20+i*5%40)),8*Math.sin(Math.PI*life),Math.sin(Math.PI*life)*(1-t/60),i%3==0?0xFFFFFF:0xFFB848);
        }
        // Heat pours off the pillar: thick billows of hot air keep rising around it while it stands.
        double pillar=384*easeOut((age-40)/5)*(1-.85*smooth((age-116)/42));
        for(int i=0;i<48;i++) {
            double life=(i*.618034+age*.02)%1,a=i*2.399963+age*.03,rr=FugaSequence.RADIUS*(.75+.35*life);
            puff(v,m,base.add(Math.cos(a)*rr,4+life*pillar*.55,Math.sin(a)*rr),6+10*life,i%3==0?0xFFA040:0xFF5A18,retract*.13*Math.sin(Math.PI*life),true);
        }
        // Plumes of fire and smoke keep rolling out over the ground from the pillar's foot, fed until it fades.
        if(retract>0)for(int i=0;i<96;i++) {
            double clock=t-i*(30.0/96);
            if(clock<=0)continue;
            double cycle=Math.floor(clock/30),life=clock-cycle*30,seed=hash(i,(int)cycle+40),ang=i*2.399963+cycle*.7;
            double rr=FugaSequence.RADIUS*.9+life*4.5,size=5+seed*7+life*.4;
            Vec3 c=base.add(Math.cos(ang)*rr,size*.6+seed*8,Math.sin(ang)*rr);
            double al=retract*smooth(life/3)*(1-smooth((life-22)/8));
            puff(v,m,c,size*1.2,0x2A1410,al*.12,false);
            puff(v,m,c,size,seed<.5?0xFF6A20:0xFFA040,al*.1,true);
        }
        // Embers keep drifting up through the aftermath.
        for(int i=0;i<160;i++) {
            double life=(i*.618034+age*.012)%1,a=i*2.399963,rr=6+(i*7.3)%46;
            Vec3 p=base.add(Math.cos(a+life)*rr,life*life*120,Math.sin(a+life)*rr);
            spark(v,m,p,.25+.35*(1-life),(1-life)*(.4+.6*retract));
        }
    }
    // --- Primitives -------------------------------------------------------------------------------------------
    private static boolean layer(boolean glow) { return pass < 0 || (pass == 1) == glow; }
    /** Camera-facing soft strip through camera-relative points: transparent edges, solid spine. */
    static void strand(VertexConsumer v,Matrix4f m,Vec3[] p,double[] w,double[] a,int n,int color,boolean glow) {
        strand(v,m,p,w,a,n,color,glow,true);
    }
    /** {@code torn} false keeps even a wide strand soft (lightning, dust), where the cel slits read as a dotted chain. */
    static void strand(VertexConsumer v,Matrix4f m,Vec3[] p,double[] w,double[] a,int n,int color,boolean glow,boolean torn) {
        if(!layer(glow)||n<2)return;
        Vec3[] s=new Vec3[n];
        for(int i=0;i<n;i++) {
            Vec3 tangent=p[Math.min(n-1,i+1)].subtract(p[Math.max(0,i-1)]),c=tangent.cross(p[i]);
            s[i]=(c.lengthSqr()<1e-12?side(tangent):c.normalize()).scale(w[i]);
        }
        // U follows arc length so fibres keep their size; the clock streams them toward the strand's end.
        float[] u=new float[n];double run=-flow*.09,widest=0;
        for(int i=0;i<n;i++) {if(i>0)run+=p[i].distanceTo(p[i-1])*.45;u[i]=(float)run;widest=Math.max(widest,w[i]);}
        // Only broad ribbons get the torn cel texture; on thin strands and tongues the rim ate the body, leaving pale spikes.
        boolean soft=!torn||widest<.22;
        float edge=soft?SOLID_V:EDGE_V,spine=soft?SOLID_V:SPINE_V,far=soft?SOLID_V:FAR_V;
        // Wide strands keep their alpha to the rim so the texture cuts a hard, torn silhouette; hairlines stay soft.
        double rim=soft?0:.9;
        for(int i=0;i+1<n;i++) {
            vertex(v,m,p[i].add(s[i]),color,a[i]*rim,u[i],edge);vertex(v,m,p[i],color,a[i],u[i],spine);vertex(v,m,p[i+1],color,a[i+1],u[i+1],spine);vertex(v,m,p[i+1].add(s[i+1]),color,a[i+1]*rim,u[i+1],edge);
            vertex(v,m,p[i],color,a[i],u[i],spine);vertex(v,m,p[i].subtract(s[i]),color,a[i]*rim,u[i],far);vertex(v,m,p[i+1].subtract(s[i+1]),color,a[i+1]*rim,u[i+1],far);vertex(v,m,p[i+1],color,a[i+1],u[i+1],spine);
        }
    }
    private static void line(VertexConsumer v,Matrix4f m,Vec3 a,Vec3 b,double wa,double wb,int color,boolean glow,double alpha) {
        // Accent strokes stay soft: the torn cel texture is for flame ribbons, on rays and streaks it reads as shards.
        strand(v,m,new Vec3[]{a,a.lerp(b,.5),b},new double[]{wa,(wa+wb)*.5,wb},new double[]{alpha,alpha,alpha*.6},3,color,glow,false);
    }
    private static void spark(VertexConsumer v,Matrix4f m,Vec3 p,double size,double alpha) {
        // A glowing dot, not a vertical stroke.
        dot(v,m,p,size*1.2,0xFFE6B0,alpha,true);
    }
    /** Braided flame between two points: red/orange body streaks around a yellow-white core, uneven width. */
    private static void fireBody(VertexConsumer v,Matrix4f m,Vec3 a,Vec3 b,double width,double bend,double age,double gain,double seed) {
        Vec3 along=direction(b.subtract(a)),u=side(along),w=along.cross(u).normalize();
        int n=18;Vec3[] p=new Vec3[n];double[] r=new double[n],al=new double[n];
        int[] colors={0xB81A03,0xE23A06,0xFF6A12,0xFF9A2E,0xFFC65A,0xFFE7A8,0xFFF8E6};
        for(int k=0;k<colors.length;k++) {
            double phase=seed+k*1.7,spread=(k<3?.55:k<5?.32:.1)*width,thick=(k<3?.75:k<5?.38:.16)*width;
            for(int i=0;i<n;i++) {
                double t=i/(n-1.0),envelope=Math.sin(Math.PI*t),soft=Math.pow(envelope,.55);
                // Each streak drifts along and around the spine at its own rate; fire rises, so it bows upward.
                double wobble=Math.sin(t*(5+k*.7)-age*(.11+k*.013)+phase),twist=Math.cos(t*(4+k*.5)-age*(.09+k*.011)+phase*1.3);
                p[i]=a.lerp(b,t).add(u.scale(wobble*spread*soft)).add(w.scale(twist*spread*.7*soft))
                        .add(0,envelope*bend*(1+.3*Math.sin(age*.07+phase)),0);
                r[i]=thick*(.3+.7*soft)*(.55+.45*noise(t*6-age*.16,k*3.1+seed));
                al[i]=gain*(k<3?.85:k<5?.7:.8)*Math.min(1,soft*1.6);
            }
            // Only the two hottest layers add light; the rest are opaque bands, so the flame keeps its form.
            strand(v,m,p,r,al,n,colors[k],k>=5);
        }
    }
    /** Short-lived tongues that peel from a flame and burn out while drifting. */
    private static void licks(VertexConsumer v,Matrix4f m,Vec3 a,Vec3 b,double size,double age,double gain,int count,double seed,Vec3 drift) {
        licks(v,m,a,b,size,age,gain,count,seed,drift,0xF04A0A,0xFFCF72,false);
    }
    private static void licks(VertexConsumer v,Matrix4f m,Vec3 a,Vec3 b,double size,double age,double gain,int count,double seed,Vec3 drift,int body,int core,boolean glowBody) {
        if(size<=0||gain<=0)return;
        Vec3 u=side(direction(b.subtract(a))),flow=direction(drift);
        int n=8;Vec3[] p=new Vec3[n];double[] r=new double[n],al=new double[n],rc=new double[n],ac=new double[n];
        for(int j=0;j<count;j++) {
            double clock=j*.6180339+age*(.028+.006*(j%3))+seed,life=clock-Math.floor(clock),cycle=Math.floor(clock);
            double at=(j*.37+cycle*.618034)%1,turn=((j+(int)cycle)&1)==0?1:-1,swell=Math.sin(Math.PI*Math.min(1,life*1.3));
            double length=size*(1.5+1.4*noise(cycle,j+seed))*swell;
            Vec3 root=a.lerp(b,.1+.8*at);
            for(int i=0;i<n;i++) {
                double q=i/(n-1.0);
                p[i]=root.add(flow.scale(q*length)).add(u.scale(turn*Math.sin(q*3.4+age*.25+j)*size*.8*q)).add(0,q*q*size*.3,0);
                r[i]=size*.85*Math.pow(1-q,.7)*(.45+.55*swell);al[i]=gain*(1-life)*(1-q*.5);
                rc[i]=r[i]*.42;ac[i]=al[i]*.85;
            }
            // Tongues stay soft: at large sizes the torn texture shows as spotted panels.
            strand(v,m,p,r,al,n,body,glowBody,false);
            strand(v,m,p,rc,ac,n,core,true,false);
        }
    }
    private static void ellipsoid(VertexConsumer v,Matrix4f m,Vec3 center,double horizontal,double vertical,double age,int color,double alpha,boolean glow) {
        if(!layer(glow))return;
        for(int band=0;band<12;band++) for(int s=0;s<24;s++) {
            Vec3[] p=new Vec3[4];
            for(int c=0;c<4;c++) {
                double theta=(band+(c>=2?1:0))*Math.PI/12-Math.PI/2,a=(s+(c==1||c==2?1:0))*TAU/24;
                double r=1+.07*Math.sin(a*7+theta*9+age*.15)+.04*Math.sin(a*13-theta*5-age*.23);
                p[c]=center.add(Math.cos(a)*Math.cos(theta)*horizontal*r,Math.sin(theta)*vertical*r,Math.sin(a)*Math.cos(theta)*horizontal*r);
            }
            quad(v,m,p[0],p[1],p[2],p[3],color,alpha);
        }
    }
    /** Textured ground ring: soft edges, width and brightness broken up by noise into torn arcs and gaps,
     *  with thin hot filaments running through it; the pattern drifts as the radius changes. */
    private static void ring(VertexConsumer v,Matrix4f m,Vec3 base,double radius,double width,int color,double alpha,boolean glow) {
        ring(v,m,base,radius,width,color,alpha,glow,0);
    }
    /** {@code turn} rotates the torn pattern with the ring. */
    private static void ring(VertexConsumer v,Matrix4f m,Vec3 base,double radius,double width,int color,double alpha,boolean glow,double turn) {
        ring(v,m,base,radius,width,color,alpha,glow,turn,64);
    }
    /** {@code n} segments: small rings round out with fewer. */
    private static void ring(VertexConsumer v,Matrix4f m,Vec3 base,double radius,double width,int color,double alpha,boolean glow,double turn,int n) {
        if(!layer(glow)||alpha<=0)return;
        double drift=radius*.17;
        Vec3[] in=new Vec3[n+1],mid=new Vec3[n+1],out=new Vec3[n+1];double[] k=new double[n+1];
        for(int i=0;i<=n;i++) {
            double a=i*TAU/n,c=Math.cos(a+turn),s=Math.sin(a+turn);
            double w=width*(.45+.9*noise(a*7+drift,3.1)),torn=noise(a*4-drift*.6,radius*.05+1.7);
            k[i]=alpha*Math.min(1,.15+1.4*torn*torn);
            in[i]=base.add(c*(radius-w),0,s*(radius-w));mid[i]=base.add(c*radius,0,s*radius);out[i]=base.add(c*(radius+w*1.3),0,s*(radius+w*1.3));
        }
        // Solid texels: the ribbon slits would punch the ring into a chain of holes; its noise already tears it.
        for(int i=0;i<n;i++) {
            float ua=(float)(i*8.0/n+drift*.3),ub=(float)((i+1)*8.0/n+drift*.3);
            vertex(v,m,in[i],color,0,ua,SOLID_V);vertex(v,m,in[i+1],color,0,ub,SOLID_V);vertex(v,m,mid[i+1],color,k[i+1],ub,SOLID_V);vertex(v,m,mid[i],color,k[i],ua,SOLID_V);
            vertex(v,m,mid[i],color,k[i],ua,SOLID_V);vertex(v,m,mid[i+1],color,k[i+1],ub,SOLID_V);vertex(v,m,out[i+1],color,0,ub,SOLID_V);vertex(v,m,out[i],color,0,ua,SOLID_V);
        }
        int hot=hot(color);
        for(int f=0;f<2;f++) {
            double off=(f==0?-.35:.45)*width,thin=width*.08+.02;
            for(int i=0;i<n;i++) {
                double a=i*TAU/n,b=(i+1)*TAU/n;
                double ka=k[i]*noise(a*11+drift*1.3,f*5+9),kb=k[i+1]*noise(b*11+drift*1.3,f*5+9);
                quad2(v,m,base,radius+off,thin,a+turn,b+turn,hot,ka,kb);
            }
        }
    }
    private static void quad2(VertexConsumer v,Matrix4f m,Vec3 base,double r,double w,double a,double b,int color,double ka,double kb) {
        vertex(v,m,base.add(Math.cos(a)*(r-w),0,Math.sin(a)*(r-w)),color,ka);vertex(v,m,base.add(Math.cos(b)*(r-w),0,Math.sin(b)*(r-w)),color,kb);
        vertex(v,m,base.add(Math.cos(b)*(r+w),0,Math.sin(b)*(r+w)),color,kb);vertex(v,m,base.add(Math.cos(a)*(r+w),0,Math.sin(a)*(r+w)),color,ka);
    }
    /** Halfway to white: the hot core colour of a ring or band. */
    private static int hot(int c) { return (((c>>16&255)+255)/2)<<16|(((c>>8&255)+255)/2)<<8|((c&255)+255)/2; }
    private static void quad(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, double alpha) {
        vertex(v,m,a,color,alpha); vertex(v,m,b,color,alpha); vertex(v,m,c,color,alpha); vertex(v,m,d,color,alpha);
    }
    private static void vertex(VertexConsumer v, Matrix4f m, Vec3 p, int color, double alpha) {
        vertex(v,m,p,color,alpha,SOLID_U,SOLID_V);
    }
    private static void vertex(VertexConsumer v, Matrix4f m, Vec3 p, int color, double alpha, float u, float tv) {
        int opacity=(int)(Math.max(0,Math.min(1,alpha))*255);
        v.addVertex(m,(float)p.x,(float)p.y,(float)p.z).setColor((color>>16)&255,(color>>8)&255,color&255,opacity).setUv(u,tv);
    }
    private static double hash(int x,int y) { int h=x*374761393+y*668265263;h=(h^(h>>>13))*1274126177;return ((h^(h>>>16))&0xFFFF)/65535.0; }
    static double noise(double x,double y) {
        int ix=(int)Math.floor(x),iy=(int)Math.floor(y);double fx=x-ix,fy=y-iy;fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);
        double a=hash(ix,iy),b=hash(ix+1,iy),c=hash(ix,iy+1),d=hash(ix+1,iy+1);
        return a+(b-a)*fx+(c-a)*fy+(a-b-c+d)*fx*fy;
    }
    private static double easeOut(double t) { t=Math.max(0,Math.min(1,t)); return 1-(1-t)*(1-t)*(1-t); }
    private static Vec3 direction(Vec3 d) { return d.lengthSqr()<1e-8?new Vec3(0,0,1):d.normalize(); }
    private static Vec3 side(Vec3 d) { return direction(d.cross(Math.abs(d.y)>.98?new Vec3(1,0,0):new Vec3(0,1,0))); }
    private static double smooth(double t) { t=Math.max(0,Math.min(1,t)); return t*t*(3-2*t); }
    private static boolean finite(double... values) { for(double v:values)if(!Double.isFinite(v))return false; return true; }
}
