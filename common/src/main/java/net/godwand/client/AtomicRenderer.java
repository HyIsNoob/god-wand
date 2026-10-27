package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.godwand.network.AtomicFxPayload;
import net.godwand.skill.AtomicSequence;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.util.*;

/** Energy is client geometry; buildup never removes or duplicates terrain. */
public final class AtomicRenderer {
    public record Frame(int casterId, long startedAt, Vec3 center, float age, float yaw, float waitTicks, boolean waiting) {}
    private static final Map<Integer, State> ACTIVE = new LinkedHashMap<>();
    private static final List<State> AUDIO_TAILS = new ArrayList<>();
    private record Scar(Vec3 center,long started) {}
    private static final Map<Integer,Scar> SCARS=new LinkedHashMap<>();
    private static final ResourceLocation ENERGY_TEXTURE=ResourceLocation.fromNamespaceAndPath("godwand","atomic_energy");
    private static boolean textureReady;
    private static ClientLevel world;
    private static final class State {
        AtomicFxPayload payload;
        final List<TipSample> swordTrail=new ArrayList<>();
        org.joml.Vector3f pendingTip,pendingBladeBase;
        Vec3 bladeTip,bladeBase;
        double bladeTime;
        double pendingTipTime;
        final float yaw;
        SimpleSoundInstance charge, blast, heartbeatSound;
        long lastHeartbeat = -1;
        State(AtomicFxPayload payload, float yaw) {
            this.payload = payload; this.yaw = yaw;
        }
    }

    public static void receive(AtomicFxPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != world) reset();
        world = client.level;
        if (world == null || payload.phase() < 0 || payload.phase() > AtomicFxPayload.RELEASE) return;
        if(!Double.isFinite(payload.x())||!Double.isFinite(payload.y())||!Double.isFinite(payload.z()))return;
        if(payload.phase()==AtomicFxPayload.CHARGE)SCARS.remove(payload.casterId());
        if(payload.phase()==AtomicFxPayload.CHARGE&&client.player!=null&&payload.casterId()==client.player.getId())
            AtomicCinematic.onCastStart();
        State state = ACTIVE.get(payload.casterId());
        if (payload.phase() == AtomicFxPayload.CANCEL) {
            // The server also sends CANCEL for normal completion; keep the authored audio tail.
            if(payload.releasedAt()>=0&&AtomicSequence.castAge(world.getGameTime(),payload.startedAt(),payload.releasedAt())>=AtomicSequence.END) {
                if(state!=null) { AUDIO_TAILS.add(state); ACTIVE.remove(payload.casterId()); }
                return;
            }
            if (state != null) { stop(state); ACTIVE.remove(payload.casterId()); }
            AUDIO_TAILS.removeIf(tail -> {
                if(tail.payload.casterId()!=payload.casterId())return false;
                stop(tail); return true;
            });
            return;
        }
        boolean newBurst = payload.phase() == AtomicFxPayload.BURST && (state == null || state.payload.phase() != AtomicFxPayload.BURST);
        if (state == null) {
            if (ACTIVE.size() >= 8) { var first = ACTIVE.values().iterator(); stop(first.next()); first.remove(); }
            var entity = world.getEntity(payload.casterId());
            state = new State(payload, entity == null ? 0 : entity.getYRot());
            ACTIVE.put(payload.casterId(), state);
            if (payload.phase() == AtomicFxPayload.CHARGE) state.charge = sound("atomic_charge", payload, .8f);
        }
        state.payload = payload;
        if ((payload.phase() == AtomicFxPayload.RELEASE || newBurst) && state.blast == null)
            state.blast = sound("atomic_blast", payload, 1);
        if (newBurst) {
            SCARS.put(payload.casterId(),new Scar(new Vec3(payload.x(),payload.y(),payload.z()),payload.releasedAt()-AtomicSequence.WAIT_TICK));
            if(SCARS.size()>64)SCARS.remove(SCARS.keySet().iterator().next());
            float gain = distanceGain(client.player == null ? 256 : client.player.position().distanceTo(new Vec3(payload.x(), payload.y(), payload.z())));
            GodWandCinematicEngine.addTrauma(.94f * gain, 18);
        }
    }
    private static SimpleSoundInstance sound(String name, AtomicFxPayload p, float volume) {
        var client = Minecraft.getInstance();
        float gain = distanceGain(client.player == null ? 256 : client.player.position().distanceTo(new Vec3(p.x(), p.y(), p.z())));
        var sound = new SimpleSoundInstance(ResourceLocation.fromNamespaceAndPath("godwand", name), SoundSource.PLAYERS,
                volume * gain, 1, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        client.getSoundManager().play(sound);
        return sound;
    }
    public static float distanceGain(double distance) { return (float) Math.clamp(1 - Math.max(0, distance - 48) / 208, 0, 1); }
    private static void stop(State state) {
        var sounds = Minecraft.getInstance().getSoundManager();
        if (state.charge != null) sounds.stop(state.charge);
        if (state.blast != null) sounds.stop(state.blast);
        if (state.heartbeatSound != null) sounds.stop(state.heartbeatSound);
    }
    public static void tick(Minecraft client) {
        if (client.level != world || client.player == null) { reset(); return; }
        AUDIO_TAILS.removeIf(tail -> (tail.charge==null||!client.getSoundManager().isActive(tail.charge))
                &&(tail.blast==null||!client.getSoundManager().isActive(tail.blast)));
        SCARS.values().removeIf(s -> world.getGameTime()-s.started>=AtomicSequence.SCAR_END);
        for (var iterator = ACTIVE.values().iterator(); iterator.hasNext();) {
            State state = iterator.next();
            var view=frame(state,0);
            float age=view.age();
            boolean waiting=view.waiting()&&state.payload.releasedAt()<0;
            long heartbeat=(long)(view.waitTicks()/28);
            if(waiting&&heartbeat!=state.lastHeartbeat) {
                state.lastHeartbeat=heartbeat;
                float gain=distanceGain(client.player.position().distanceTo(view.center()));
                state.heartbeatSound=new SimpleSoundInstance(ResourceLocation.fromNamespaceAndPath("minecraft","entity.warden.heartbeat"),SoundSource.PLAYERS,
                        gain*.30f,.8f,RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,0,0,0,true);
                client.getSoundManager().play(state.heartbeatSound);
            }
            if(!waiting&&state.heartbeatSound!=null) {
                client.getSoundManager().stop(state.heartbeatSound); state.heartbeatSound=null;
            }
            if (age >= AtomicSequence.END) {
                // Let authored audio finish after the camera returns; cancellation/reset still owns it.
                AUDIO_TAILS.add(state); iterator.remove();
            } else if (age < -20 || (state.payload.phase() == AtomicFxPayload.CHARGE && world.getGameTime()-state.payload.startedAt() > AtomicSequence.CHARGE_TICKS + 40)) {
                stop(state); iterator.remove();
            }
        }
        AtomicCinematic.tick(client);
    }
    private static Frame frame(State state, float delta) {
        var p = state.payload;
        Vec3 center = new Vec3(p.x(), p.y(), p.z());
        if (p.phase() == AtomicFxPayload.CHARGE) {
            var entity = world.getEntity(p.casterId());
            if (entity != null) center = entity.getPosition(delta);
        }
        double now=world.getGameTime()+delta;
        float age=p.phase()==AtomicFxPayload.CHARGE?(float)Math.min(Math.max(0,now-p.startedAt()),AtomicSequence.CHARGE_TICKS)
                :AtomicSequence.castAge(now,p.startedAt(),p.releasedAt());
        float waited=age>=AtomicSequence.WAIT_TICK?(float)Math.max(0,(p.releasedAt()<0?now:Math.min(now,p.releasedAt()))-p.startedAt()-AtomicSequence.WAIT_TICK):0;
        boolean waiting=p.phase()!=AtomicFxPayload.CHARGE&&now>=p.startedAt()+AtomicSequence.WAIT_TICK&&(p.releasedAt()<0||now<p.releasedAt());
        return new Frame(p.casterId(), p.startedAt(), center, age, state.yaw,waited,waiting);
    }
    public static Frame localFrame(float delta) {
        var client = Minecraft.getInstance();
        if (world == null || world != client.level || client.player == null) return null;
        return frameFor(client.player.getId(), delta);
    }
    public static Frame frameFor(int casterId, float delta) {
        if (world == null || world != Minecraft.getInstance().level) return null;
        State state = ACTIVE.get(casterId);
        return state == null ? null : frame(state, delta);
    }
    public static Frame heldFrame(net.minecraft.world.item.ItemStack stack, float delta) {
        if (world == null || world != Minecraft.getInstance().level) return null;
        for (State state : ACTIVE.values()) {
            var entity = world.getEntity(state.payload.casterId());
            if (entity instanceof net.minecraft.world.entity.LivingEntity living
                    && (living.getMainHandItem() == stack || living.getOffhandItem() == stack)) return frame(state, delta);
        }
        return null;
    }
    record TipSample(Vec3 position,double time) {}
    public static void captureSwordTip(Frame frame,Matrix4f itemPose,float delta) {
        State state=ACTIVE.get(frame.casterId());
        if(state==null||frame.age()<294||frame.age()>406)return;
        state.pendingTip=itemPose.transformPosition(new org.joml.Vector3f(0,47f/16+.5f,0));
        state.pendingBladeBase=itemPose.transformPosition(new org.joml.Vector3f(0,.35f,0));
        state.pendingTipTime=world.getGameTime()+delta;
    }
    static void sampleSwordTip(List<TipSample> trail,Vec3 point,double now) {
        trail.removeIf(p -> now-p.time()>16||now<p.time());
        if(!Double.isFinite(point.lengthSqr()))return;
        if(!trail.isEmpty()) {
            TipSample last=trail.get(trail.size()-1);
            if(now-last.time()<.2)return;
            if(last.position().distanceToSqr(point)>16)trail.clear();
        }
        if(trail.size()>=81)trail.remove(0);
        trail.add(new TipSample(point,now));
    }
    private static void swordTrail(VertexConsumer v,Matrix4f m,List<TipSample> trail,Vec3 center,double now) {
        trail.removeIf(p -> now-p.time()>16||now<p.time());
        for(int i=1;i<trail.size();i++) {
            TipSample a=trail.get(i-1),b=trail.get(i);
            float light=(float)Math.pow(Math.max(0,1-(now-a.time())/16),2);
            stroke(v,m,a.position().subtract(center),b.position().subtract(center),.028f,light);
        }
        if(!trail.isEmpty()) {
            TipSample last=trail.get(trail.size()-1);
            float light=(float)Math.max(0,1-(now-last.time())/6);
            shard(v,m,last.position().subtract(center),.11f,light);
        }
    }
    public static Frame nearestFrame(float delta) {
        var client = Minecraft.getInstance();
        if (world == null || world != client.level || client.player == null) return null;
        Frame own = localFrame(delta);
        if (own != null) return own;
        // shortcut: one volumetric Atomic per view; add compositing only if simultaneous casts need full volumes.
        Frame nearest = null;
        double distance = Double.MAX_VALUE;
        for (State state : ACTIVE.values()) {
            Frame candidate = frame(state, delta);
            double d = candidate.center.distanceToSqr(client.gameRenderer.getMainCamera().getPosition());
            if (d < distance) { distance = d; nearest = candidate; }
        }
        return nearest;
    }
    public static void reset() {
        for (State state : ACTIVE.values()) stop(state);
        for (State state : AUDIO_TAILS) stop(state);
        AUDIO_TAILS.clear(); ACTIVE.clear(); SCARS.clear(); world = null;
        AtomicCinematic.reset(); AtomicPostFx.reset();
    }

    public static void render(PoseStack matrices, Camera camera, float delta) {
        if (world == null || world != Minecraft.getInstance().level || ACTIVE.isEmpty()&&SCARS.isEmpty()) return;
        RenderSystem.enableBlend(); RenderSystem.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
        ensureEnergyTexture();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0,ENERGY_TEXTURE);
        try {
            for (State state : ACTIVE.values()) {
                Frame frame = frame(state, delta);
                if (camera.getPosition().distanceToSqr(frame.center) > 320 * 320) continue;
                if(state.pendingTip!=null) {
                    var inverse=new Matrix4f(matrices.last().pose()).invert();
                    var tip=inverse.transformPosition(state.pendingTip);
                    var base=inverse.transformPosition(state.pendingBladeBase);
                    Vec3 point=new Vec3(tip.x,tip.y,tip.z).add(camera.getPosition());
                    if(point.distanceToSqr(frame.center)<64) {
                        state.bladeTip=point;state.bladeBase=new Vec3(base.x,base.y,base.z).add(camera.getPosition());
                        state.bladeTime=state.pendingTipTime;
                        if(frame.age()>=382&&frame.age()<=402)
                            sampleSwordTip(state.swordTrail,point,state.pendingTipTime);
                    }
                    state.pendingTip=null;
                }
                matrices.pushPose();
                matrices.translate(frame.center.x - camera.getPosition().x, frame.center.y - camera.getPosition().y, frame.center.z - camera.getPosition().z);
                try {
                    var mesh = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                    emit(mesh, matrices.last().pose(), frame.age,camera.getPosition().subtract(frame.center));
                    swordTrail(mesh,matrices.last().pose(),state.swordTrail,frame.center,world.getGameTime()+delta);
                    if(state.bladeTip!=null&&world.getGameTime()+delta-state.bladeTime<1)
                        bladeConvergence(mesh,matrices.last().pose(),frame.age,state.bladeBase.subtract(frame.center),
                                state.bladeTip.subtract(frame.center),camera.getPosition().subtract(frame.center));

                    var built = mesh.build();
                    if (built != null) BufferUploader.drawWithShader(built);
                } finally { matrices.popPose(); }
            }
            var visible=SCARS.values().stream().filter(s -> s.center.distanceToSqr(camera.getPosition())<256*256)
                    .sorted(Comparator.comparingDouble(s -> s.center.distanceToSqr(camera.getPosition()))).limit(8).toList();
            for(var scar:visible) {
                matrices.pushPose();
                matrices.translate(scar.center.x-camera.getPosition().x,scar.center.y-camera.getPosition().y,scar.center.z-camera.getPosition().z);
                try {
                    var mesh=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
                    scars(mesh,matrices.last().pose(),scar);
                    var built=mesh.build(); if(built!=null)BufferUploader.drawWithShader(built);
                } finally { matrices.popPose(); }
            }
        } finally { RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend(); }
    }

    public static void emit(VertexConsumer v, Matrix4f m, double age,Vec3 eye) {
        float fade = AtomicSequence.visibility(age);
        if (fade <= 0) return;
        if (age < AtomicSequence.DETONATION) {
            if(age>0) {
                float compression=AtomicSequence.compression(age), scale=1-compression;
                if(scale<=0)return;
                Matrix4f local=new Matrix4f(m).scale(scale);
                Vec3 localEye=new Vec3(eye.x/scale,eye.y/scale,eye.z/scale);
                charge(v,local,age,localEye);
                float particleCollapse=AtomicSequence.phase(age,AtomicSequence.ATOMIC_WORD+4,AtomicSequence.WAIT_TICK);
                float particleScale=1-particleCollapse;
                if(particleScale>0) {
                    Matrix4f particles=new Matrix4f(m).scale(particleScale);
                    Vec3 particleEye=new Vec3(eye.x/particleScale,eye.y/particleScale,eye.z/particleScale);
                    motes(v,particles,age,particleEye);
                }
                float releaseCollapse=AtomicSequence.phase(age,AtomicSequence.ATOMIC_WORD+8,AtomicSequence.WAIT_TICK);
                float releaseScale=1-releaseCollapse;
                if(releaseScale>0)release(v,new Matrix4f(m).scale(releaseScale),age);
                for(int i=0;i<3;i++) {
                    double start=AtomicSequence.ATOMIC_WORD+i*2;
                    if(age<=start)continue;
                    float inward=AtomicSequence.phase(age,start,AtomicSequence.WAIT_TICK);
                    float light=AtomicSequence.phase(age,start,start+2)*(1-AtomicSequence.phase(age,AtomicSequence.WAIT_TICK-2,AtomicSequence.WAIT_TICK));
                    ring(v,m,.08f+i*2*(1-inward),(32+i*3)*(1-inward),.08f,light*.8f);
                }
            }
        }
        else blast(v, m, age, fade,eye);
    }
    private static void charge(VertexConsumer v, Matrix4f m, double age,Vec3 eye) {
        float q=AtomicSequence.charge(age), compression=AtomicSequence.compression(age);
        float spread=1, radius=AtomicSequence.CAGE_RADIUS;
        float alpha=chargeAlpha(age);
        float kick=AtomicSequence.activation(age);
        float kickRadius=1+15*AtomicSequence.phase(age,6,36);
        ring(v,m,.12f,kickRadius,.12f,kick*alpha*3);
        ring(v,m,.32f,kickRadius*.85f,.055f,kick*alpha*2);
        float sourceScale=AtomicSequence.phase(age,8,36);
        float source=AtomicSequence.phase(age,8,16)*(.65f+q*.35f)*(1-AtomicSequence.blackout(age));
        ringProgress(v,m,.065f,2*sourceScale,.055f,source,1);
        ringProgress(v,m,.085f,1.55f*sourceScale,.025f,source*.65f,1);
        for(int i=0;i<24;i++) {
            double a=i*Math.PI/12;
            stroke(v,m,polar(1.75*sourceScale,.07,a),polar(2.12*sourceScale,.07,a),.022f,source*.7f);
            shard(v,m,polar(2*sourceScale,.09,a),.04f*sourceScale,source*.6f);
        }
        float ground=AtomicSequence.phase(age,20,160);
        ringProgress(v,m,.07f,radius,.055f,alpha,ground);
        ringProgress(v,m,.1f,radius*.76f,.035f,alpha*.7f,AtomicSequence.phase(age,48,178));
        float traceAlpha=(.42f+q*.55f)*(1-AtomicSequence.blackout(age))*AtomicSequence.phase(age,20,38);
        for (int i=0;i<24;i++) {
            double a=i*Math.PI/12, seed=fract(i*.61803398875);
            float trace=AtomicSequence.phase(age,25+seed*55,100+seed*45);
            Vec3[] path={polar(2*spread*sourceScale,.08,a),polar(6*spread,.08,a+.07),polar(11*spread,.08,a+.07),polar(15*spread,.08,a),polar(radius,.08,a)};
            trace(v,m,path,trace,.036f,traceAlpha);
            float fork=(float)Math.clamp((trace-.25)/.75,0,1);
            for(int side:new int[]{-1,1}) {
                Vec3 joint=polar(17*spread,.08,a+side*.14);
                Vec3[] branch={path[1],polar(11*spread,.08,a+side*.09),joint,polar(radius,.08,a+side*.22)};
                trace(v,m,branch,fork,.028f,traceAlpha*.8f);
                for(int twig:new int[]{-1,1}) {
                    Vec3[] tips={joint,polar(21*spread,.08,a+side*.17+twig*.035),polar(radius,.08,a+side*.22+twig*.055)};
                    trace(v,m,tips,(float)Math.clamp((trace-.62)/.38,0,1),.018f,traceAlpha*.65f);
                }
            }
        }
        fortress(v,m,age,alpha);
        float roofWave=AtomicSequence.junctionWaveRadius(age);
        ring(v,m,46.1f,roofWave,.12f,alpha*AtomicSequence.phase(age,180,181)*(1-AtomicSequence.phase(age,196,203))*1.2f);
        if(age>=170&&age<350&&(age-170)%60<8)
            ring(v,m,.14f,2+30*AtomicSequence.phase((age-170)%60,0,8),.09f,alpha*.85f);
        ring(v,m,46*spread,(.2f+2*AtomicSequence.phase(age,178,198))*spread,.07f,alpha*AtomicSequence.phase(age,175,182));
        float holo=AtomicSequence.holoRadius(age), light=AtomicSequence.phase(age,290,350)*alpha*(1-AtomicSequence.phase(age,392,396));
        ring(v,m,3.2f,holo,.065f,light);
        // Broad rising ribbons retain a pressure field when post effects are disabled.
        for(int i=0;i<20;i++) {
            float reveal=AtomicSequence.phase(age,66+i%5*12,156+i%5*12);
            double seed=fract(i*.61803398875), angle=i*2.39996+age*.008;
            Vec3 last=null;
            for(int j=0;j<=12;j++) {
                double t=j/12.0, h=fract(t+age*.004+seed);
                double r=(3+seed*11)*(1-h*.48)*spread;
                Vec3 p=polar(r,.15+h*(8+q*16)*spread,angle+h*.8+Math.sin(h*7+age*.025)*.12);
                if(last!=null&&p.y>last.y) stroke(v,m,AtomicSequence.releaseMote(last,age),AtomicSequence.releaseMote(p,age),(float)((.1+.7*q)*Math.sin(Math.PI*h))*spread,alpha*reveal*.065f);
                last=p;
            }
        }
    }

    private static void bladeConvergence(VertexConsumer v,Matrix4f m,double age,Vec3 base,Vec3 tip,Vec3 eye) {
        float gather=AtomicSequence.phase(age,294,344)*(1-AtomicSequence.phase(age,378,384));
        if(gather<=0)return;
        for(int i=0;i<8;i++) {
            double seed=i/8.0,packet=fract(age*.024+seed);
            Vec3 target=base.lerp(tip,.45+seed*.5);
            Vec3 previous=AtomicSequence.convergencePoint(age,seed,0,target);
            for(int j=1;j<=20;j++) {
                double t=j/20.0;
                Vec3 next=AtomicSequence.convergencePoint(age,seed,t,target);
                float flow=(float)(.35+.65*Math.pow(.5+.5*Math.sin((t-packet)*Math.PI*6),2));
                stroke(v,m,previous,next,.025f+(float)Math.sin(t*Math.PI)*.025f,gather*flow);
                previous=next;
            }
            mote(v,m,AtomicSequence.convergencePoint(age,seed,packet,target),eye,.08f,gather*1.6f);
            mote(v,m,target,eye,.045f,gather);
        }
        mote(v,m,tip,eye,.09f,gather*1.8f);
    }
    private static float chargeAlpha(double age) {
        return (.12f+AtomicSequence.charge(age)*.65f)*(1-AtomicSequence.blackout(age))*AtomicSequence.phase(age,0,12);
    }
    private static void motes(VertexConsumer v,Matrix4f m,double age,Vec3 eye) {
        float q=AtomicSequence.charge(age), alpha=chargeAlpha(age), spread=1;
        double moteAge=AtomicSequence.moteClock(age);
        for(int i=0;i<1536;i++) {
            double seed=fract(i*.61803398875), birth=20+i*.06;
            if(age<birth)continue;
            double life=(moteAge-birth)/(90+seed*70), rise=fract(life), emitter=seed+Math.floor(life)*.754877666;
            float reveal=AtomicSequence.phase(age,birth,birth+12);
            float glow=reveal*AtomicSequence.phase(rise,0,.08)*(1-AtomicSequence.phase(rise,.72,1));
            Vec3 tip=AtomicSequence.releaseMote(AtomicSequence.risingMote(rise,emitter),age);
            float size=(.03f+.028f*(float)seed)*(1+.2f*q)*spread;
            mote(v,m,tip,eye,size,glow*alpha*2f);
            if(i%12==0&&rise>=.05)
                stroke(v,m,AtomicSequence.releaseMote(AtomicSequence.risingMote(rise-.05,emitter),age-1.2),tip,.01f*spread,glow*alpha*.45f);
            if(i%48==0&&rise<.12) {
                Vec3 sourcePoint=AtomicSequence.risingMote(0,emitter);
                Matrix4f local=new Matrix4f(m).translate((float)sourcePoint.x,0,(float)sourcePoint.z);
                ring(v,local,.075f,(float)(.15+rise*7)*spread,.022f,alpha*reveal*(float)(1-rise/.12)*.55f);
            }
        }
        for(int i=0;i<96;i++) {
            double seed=fract(i*.61803398875), life=moteAge*(.005+seed*.005)+seed, rise=fract(life);
            float sparkLight=(float)(Math.sin(rise*Math.PI)*alpha*AtomicSequence.phase(age,32,100)*.6);
            Vec3 tip=AtomicSequence.releaseMote(AtomicSequence.risingMote(rise,seed+Math.floor(life)*.754877666+17),age);
            if(i%2==0)stroke(v,m,tip.add(0,-.35-rise*.65,0),tip,.012f,sparkLight);
            mote(v,m,tip,eye,.022f,sparkLight);
        }
    }
    private static void release(VertexConsumer v,Matrix4f m,double age) {
        float alpha=chargeAlpha(age);
        float release=AtomicSequence.phase(age,AtomicSequence.ATOMIC_WORD,AtomicSequence.ATOMIC_WORD+2)
                *(1-AtomicSequence.phase(age,AtomicSequence.DETONATION-4,AtomicSequence.DETONATION));
        if(release>0) {
            float travel=AtomicSequence.phase(age,AtomicSequence.ATOMIC_WORD,AtomicSequence.ATOMIC_WORD+8);
            ring(v,m,.12f,AtomicSequence.releaseRadius(age),.16f,release*alpha*1.3f);
            ring(v,m,3.2f,2.3f+.4f*travel,.09f,release*alpha*1.5f);
        }

    }
    private static void fortress(VertexConsumer v,Matrix4f m,double age,float alpha) {
        float wave=AtomicSequence.fortressWaveHeight(age), power=alpha*1.5f;
        for(int i=0;i<8;i++) {
            double angle=i*Math.PI/4;
            float grow=AtomicSequence.phase(age,65+i*4,155+i*4);
            if(grow<=0)continue;
            Vec3 tower=polar(32,0,angle);
            float height=(i%2==0?42:36)*grow;
            Vec3[] corners={tower.add(-2.3,0,-2.3),tower.add(2.3,0,-2.3),tower.add(2.3,0,2.3),tower.add(-2.3,0,2.3)};
            Vec3 peak=tower.add(0,height+8*grow,0);
            for(int side=0;side<4;side++) {
                Vec3 a=corners[side], b=corners[(side+1)%4], topA=a.add(0,height,0), topB=b.add(0,height,0);
                quad(v,m,a,b,topB,topA,0x6421C2,alpha*.035f);
                circuitPanel(v,m,a,b,topB,topA,alpha*.65f);
                stroke(v,m,a,topA,.055f,alpha); stroke(v,m,topA,topB,.05f,alpha);
                Vec3 roofA=tower.add(a.subtract(tower).scale(.65)).add(0,height+2*grow,0);
                Vec3 roofB=tower.add(b.subtract(tower).scale(.65)).add(0,height+2*grow,0);
                quad(v,m,roofA,roofB,peak,peak,0x9F46EE,alpha*.055f);
                stroke(v,m,roofA,peak,.045f,alpha*.85f);
                if(wave>=0&&wave<=height)
                    fortressBand(v,m,a.add(0,wave,0),b.add(0,wave,0),age,power);
                else if(wave>=height+2*grow&&wave<peak.y) {
                    double taper=.65*(peak.y-wave)/(6*grow);
                    fortressBand(v,m,tower.add(a.subtract(tower).scale(taper)).add(0,wave,0),
                            tower.add(b.subtract(tower).scale(taper)).add(0,wave,0),age,power);
                }
                for(int merlon=0;merlon<2;merlon++) {
                    Vec3 left=topA.lerp(topB,.08+merlon*.5), right=topA.lerp(topB,.36+merlon*.5);
                    Vec3 up=new Vec3(0,2.2*grow,0);
                    stroke(v,m,left,left.add(up),.04f,alpha); stroke(v,m,left.add(up),right.add(up),.04f,alpha);
                    stroke(v,m,right.add(up),right,.04f,alpha);
                    if(wave>height&&wave<=height+2.2f*grow)
                        fortressBand(v,m,left.add(0,wave-height,0),right.add(0,wave-height,0),age,power);
                }
            }
            float wallGrow=AtomicSequence.phase(age,80+i*3,170+i*3);
            Vec3 wallA=polar(26,.1,angle), wallB=polar(26,.1,angle+Math.PI/4), edge=wallB.subtract(wallA);
            float wallHeight=28*wallGrow;
            if(wave>=0&&wave<=wallHeight)
                fortressBand(v,m,wallA.add(0,wave-.1,0),wallB.add(0,wave-.1,0),age,power);
            quad(v,m,wallA,wallB,wallB.add(0,wallHeight,0),wallA.add(0,wallHeight,0),0x7B24EF,alpha*wallGrow*.025f);
            for(int panel=0;panel<2;panel++) {
                Vec3 a=wallA.lerp(wallB,panel*.5),b=wallA.lerp(wallB,(panel+1)*.5);
                circuitPanel(v,m,a,b,b.add(0,wallHeight,0),a.add(0,wallHeight,0),alpha*wallGrow*.75f);
            }
            stroke(v,m,wallA.add(0,wallHeight,0),wallB.add(0,wallHeight,0),.045f,alpha*wallGrow);
            for(int tooth=0;tooth<3;tooth++) {
                Vec3 left=wallA.lerp(wallB,.04+tooth/3.0).add(0,wallHeight,0);
                Vec3 right=wallA.lerp(wallB,.22+tooth/3.0).add(0,wallHeight,0), up=new Vec3(0,1.7*wallGrow,0);
                stroke(v,m,left,left.add(up),.035f,alpha*wallGrow);
                stroke(v,m,left.add(up),right.add(up),.035f,alpha*wallGrow);
                stroke(v,m,right.add(up),right,.035f,alpha*wallGrow);
                if(wave>wallHeight&&wave<=wallHeight+1.7f*wallGrow)
                    fortressBand(v,m,left.add(0,wave-wallHeight-.1,0),right.add(0,wave-wallHeight-.1,0),age,power);
            }
            for(int window=0;window<2;window++) {
                Vec3 center=wallA.lerp(wallB,.28+window*.44), across=edge.normalize().scale(1.2);
                Vec3[] arch={center.subtract(across).add(0,5*wallGrow,0),center.subtract(across).add(0,19*wallGrow,0),
                        center.add(0,23*wallGrow,0),center.add(across).add(0,19*wallGrow,0),center.add(across).add(0,5*wallGrow,0)};
                trace(v,m,arch,wallGrow,.035f,alpha*wallGrow*.75f);
            }
            stroke(v,m,polar(26,14*grow,angle),tower.add(0,22*grow,0),.05f,alpha*grow*.8f);
            Vec3[] circuit={peak,polar(24,46,angle),polar(12,46,angle+Math.PI/8),new Vec3(0,46,0)};
            float roofLight=(.85f+.15f*AtomicSequence.charge(age))*(1-AtomicSequence.blackout(age));
            trace(v,m,circuit,AtomicSequence.circuitProgress(age,i/8.0),.14f,roofLight);
        }
    }
    private static void fortressBand(VertexConsumer v,Matrix4f m,Vec3 a,Vec3 b,double age,float alpha) {
        Vec3 middle=a.lerp(b,.5).add(0,Math.sin(age*1.7+a.x*.4+a.z*.7)*.12,0);
        stroke(v,m,a,middle,.065f,alpha);
        stroke(v,m,middle,b,.065f,alpha);
    }
    private static void trace(VertexConsumer v,Matrix4f m,Vec3[] path,float progress,float width,float alpha) {
        for(int j=1;j<path.length;j++) {
            double part=Math.clamp(progress*(path.length-1)-(j-1),0,1);
            if(part<=0)break;
            Vec3 end=path[j-1].lerp(path[j],part);
            stroke(v,m,path[j-1],end,width,alpha);
            if(part<1) { shard(v,m,end,width*3,alpha); break; }
        }
    }
    private static void blast(VertexConsumer v, Matrix4f m, double age, float fade,Vec3 eye) {
        double t=age-AtomicSequence.DETONATION;
        fade*=net.godwand.config.GodWandConfig.CLIENT.impactFrames?AtomicSequence.blastPulse(age):1;
        float radius=AtomicSequence.beamRadius(age);
        float height=AtomicSequence.beamHeight(age);
        if(radius>.001f) {
            // White interior, violet corona and broken vertical facets: a column, not a fireball.
            cylinder(v,m,radius*.78f,height,.7f*fade,0xF5DFFF,age);
            cylinder(v,m,radius,height,.22f*fade,0x932BFF,age+10);
            for(int i=0;i<40;i++) {
                double a=i*Math.PI/20 + .004*age;
                Vec3 last=polar(radius*(.88+.06*Math.sin(i)),-AtomicSequence.BEAM_DEPTH,a);
                for(int j=1;j<=5;j++) {
                    double y=-AtomicSequence.BEAM_DEPTH+j*(height+AtomicSequence.BEAM_DEPTH)/5, jitter=Math.sin(i*18.23+j*8.3+age*.11)*.035;
                    Vec3 next=polar(radius*(.93+jitter),y,a+jitter);
                    stroke(v,m,last,next,.045f+(i%4)*.016f,fade*(i%3==0?.85f:.35f)); last=next;
                }
            }
        }
        for(int i=0;i<3;i++) {
            float r=AtomicSequence.shockRadius(age-i*7);
            if(r>.01) {
                float opacity=fade*(1-.22f*i)*(1-AtomicSequence.phase(age,614,704));
                ring(v,m,.2f+i*.18f,r,.18f+i*.05f,opacity);
                ring(v,m,6+i*8,r*.94f,.12f,opacity*.45f);
            }
        }
        float pressure=AtomicSequence.pressure(age);
        for(int i=0;i<4;i++) {
            float waveRadius=AtomicSequence.pressureWaveRadius(age,i);
            float cycle=(waveRadius-radius)/32;
            ring(v,m,.2f+i*.1f,waveRadius,.25f,fade*pressure*(1-cycle)*.7f);
            ring(v,m,height*cycle,radius*(1+.06f*cycle),.18f,fade*pressure*(1-cycle)*.45f);
        }
        float wave=AtomicSequence.shockRadius(age);
        for(int i=0;i<48;i++) {
            double a=i*Math.PI/24;
            double r=wave*(.82+.1*Math.sin(i*18.7));
            stroke(v,m,polar(r,.15,a),polar(Math.min(144,r+3+wave*.13),.18,a+.012),.035f,fade*.65f*(1-AtomicSequence.phase(age,614,704)));
        }
        float dust=AtomicSequence.phase(age,420,444)*(1-AtomicSequence.phase(age,538,574));
        if(dust>0)for(int i=0;i<72;i++) {
            double a=i*Math.PI/36, seed=fract(i*.61803398875);
            double r=wave+2+seed*7, h=2+seed*9;
            if(AtomicSequence.columnOccludes(eye,polar(r,h*.5,a),age))continue;
            for(int j=0;j<4;j++) {
                double curl=age*.05+seed*6+j*.7;
                Vec3 lo=polar(r+Math.sin(curl)*(1+j),.2+j*h/4,a);
                Vec3 hi=polar(r+Math.sin(curl+.7)*(2+j),.2+(j+1)*h/4,a+.014);
                Vec3 side=polar(1.2+seed*2,0,a+Math.PI/2);
                quad(v,m,lo.subtract(side),lo.add(side),hi.add(side),hi.subtract(side),0x9E84B8,dust*fade*.07f);
            }
        }
        if(!AtomicPostFx.available())pressureAura(v,m,age,fade,eye);
        for(int i=0;i<144;i++) {
            double seed=fract(i*.61803398875), life=t-i%24;
            if(life<0||life>90)continue;
            double a=i*2.39996, r=4+seed*48+(life*.3);
            double y=1+life*(.7+seed*.8)-life*life*.005;
            shard(v,m,polar(r,Math.max(.1,y),a),.06f+.12f*(float)seed,fade*(float)Math.sin(Math.PI*life/90));
        }
    }
    private static void pressureAura(VertexConsumer v,Matrix4f m,double age,float fade,Vec3 eye) {
        if(age<AtomicSequence.DETONATION||age>=734)return;
        fade*=1-AtomicSequence.phase(age,574,734);
        double elapsed=age-AtomicSequence.DETONATION;
        for(int plume=0;plume<192;plume++) {
            double clock=elapsed-plume*(36.0/192);
            if(clock<=0)continue;
            double cycle=Math.floor(clock/36),life=clock-cycle*36;
            double seed=fract(plume*.61803398875+cycle*.173),angle=plume*2.39996;
            double size=5+seed*6;
            Vec3 center=polar(AtomicSequence.beamRadius(age)+life*6,size+2+seed*16,angle);
            if(AtomicSequence.columnOccludes(eye,center,age))continue;
            float alpha=fade*AtomicSequence.phase(life,0,3)*(1-AtomicSequence.phase(life,28,36));
            Vec3 normal=eye.subtract(center).normalize();
            Vec3 right=new Vec3(normal.z,0,-normal.x);
            if(right.lengthSqr()<.0001)right=new Vec3(1,0,0);
            right=right.normalize();
            Vec3 up=normal.cross(right).normalize();
            double turn=seed*6+life*.06;
            Vec3 x=right.scale(Math.cos(turn)).add(up.scale(Math.sin(turn))).scale(size);
            Vec3 y=up.scale(Math.cos(turn)).subtract(right.scale(Math.sin(turn))).scale(size*.85);
            disc(v,m,center.subtract(x).subtract(y),center.add(x).subtract(y),center.add(x).add(y),center.subtract(x).add(y),
                    0x7222D8,alpha*.085f);
            x=x.scale(.7);y=y.scale(.7);
            disc(v,m,center.subtract(x).subtract(y),center.add(x).subtract(y),center.add(x).add(y),center.subtract(x).add(y),
                    0xAD52F2,alpha*.055f);
        }
    }

    private static void scars(VertexConsumer v,Matrix4f m,Scar scar) {
        float alpha=AtomicSequence.scarOpacity(world.getGameTime()-scar.started);
        for(int ray=0;ray<26;ray++) {
            Vec3 previous=null;
            int steps=ray<24?28:128;
            for(int step=0;step<=steps;step++) {
                double r=ray<24?3+step*(AtomicSequence.MAX_RADIUS-3)/steps:ray==24?AtomicSequence.MAX_RADIUS*.76:AtomicSequence.MAX_RADIUS;
                double angle=ray<24?ray*Math.PI/12:step*Math.PI*2/steps;
                if(r>AtomicSequence.MAX_RADIUS || ray>=24&&Math.sin(angle*8)<=.1) { previous=null; continue; }
                Vec3 p=polar(r,0,angle);
                int x=(int)Math.floor(scar.center.x+p.x),z=(int)Math.floor(scar.center.z+p.z);
                if(!world.getChunkSource().hasChunk(x>>4,z>>4)) { previous=null; continue; }
                double y=world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,x,z)+.06-scar.center.y;
                Vec3 next=new Vec3(p.x,y,p.z);
                if(previous!=null&&Math.abs(next.y-previous.y)<4)stroke(v,m,previous,next,.055f,alpha);
                previous=next;
            }
        }
    }
    private static Vec3 polar(double radius,double y,double angle) { return new Vec3(Math.cos(angle)*radius,y,Math.sin(angle)*radius); }
    private static double fract(double n) { return n-Math.floor(n); }
    private static void ring(VertexConsumer v,Matrix4f m,float y,float r,float width,float alpha) {
        ringProgress(v,m,y,r,width,alpha,1);
    }
    private static void ringProgress(VertexConsumer v,Matrix4f m,float y,float r,float width,float alpha,float progress) {
        if(r<=0||alpha<=0)return;
        int resolution=r<4?32:r<12?64:96;
        int segments=(int)Math.ceil(resolution*Math.clamp(progress,0,1));
        for(int layer=2;layer>=0;layer--) {
            float w=width*(layer==2?7:layer==1?3:1), a=alpha*(layer==2?.07f:layer==1?.23f:.85f);
            int color=layer==0?0xA550FF:0x8420FF;
            for(int i=0;i<segments;i++) {
                double p=i*Math.PI*2/resolution,q=Math.min((i+1)/(double)resolution,progress)*Math.PI*2;
                quad(v,m,polar(Math.max(0,r-w),y,p),polar(r+w,y,p),polar(r+w,y,q),polar(Math.max(0,r-w),y,q),color,a);
            }
        }
    }
    private static void stroke(VertexConsumer v,Matrix4f m,Vec3 a,Vec3 b,float width,float alpha) {
        if(alpha<=0)return;
        Vec3 direction=b.subtract(a).normalize(), side=direction.cross(Math.abs(direction.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        Vec3 other=direction.cross(side).normalize();
        for(int layer=2;layer>=0;layer--) {
            double w=width*(layer==2?5:layer==1?2.4:1);
            float opacity=alpha*(layer==2?.055f:layer==1?.22f:.85f);
            int color=layer==0?0xA550FF:0x7C1AFF;
            for(Vec3 axis:new Vec3[]{side,other}) {
                Vec3 n=axis.scale(w);
                quad(v,m,a.subtract(n),a.add(n),b.add(n),b.subtract(n),color,opacity);
            }
        }
    }
    private static void cylinder(VertexConsumer v,Matrix4f m,float r,float height,float alpha,int color,double age) {
        for(int i=0;i<64;i++) {
            double a=i*Math.PI/32,b=(i+1)*Math.PI/32;
            for(int j=0;j<6;j++) {
                double low=-AtomicSequence.BEAM_DEPTH+(height+AtomicSequence.BEAM_DEPTH)*j/6, high=-AtomicSequence.BEAM_DEPTH+(height+AtomicSequence.BEAM_DEPTH)*(j+1)/6;
                double ra=r*(.94+.06*Math.sin(i*2.7+j*1.8+age*.13));
                quad(v,m,polar(ra,low,a),polar(ra,low,b),polar(ra,high,b),polar(ra,high,a),color,alpha);
            }
        }
    }
    private static void shard(VertexConsumer v,Matrix4f m,Vec3 p,float size,float alpha) {
        disc(v,m,p.add(-size,-size,0),p.add(size,-size,0),p.add(size,size,0),p.add(-size,size,0),0xC66AFF,alpha);
        disc(v,m,p.add(0,-size,-size),p.add(0,-size,size),p.add(0,size,size),p.add(0,size,-size),0xF0CDFF,alpha);
    }
    private static void mote(VertexConsumer v,Matrix4f m,Vec3 p,Vec3 eye,float size,float alpha) {
        if(size<=0||alpha<=0)return;
        Vec3 facing=eye.subtract(p).normalize();
        if(facing.lengthSqr()<1e-8)facing=new Vec3(0,0,1);
        Vec3 right=facing.cross(Math.abs(facing.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        Vec3 up=right.cross(facing).normalize();
        for(int layer=2;layer>=1;layer--) {
            double radius=size*(layer==2?3.2:layer==1?1.3:.4);
            Vec3 x=right.scale(radius), y=up.scale(radius);
            disc(v,m,p.subtract(x).subtract(y),p.add(x).subtract(y),p.add(x).add(y),p.subtract(x).add(y),
                    layer==2?0x9F32FF:layer==1?0xC660FF:0xF0CDFF,alpha*(layer==2?.13f:layer==1?.65f:.95f));
        }

    }
    public static int energyAlpha(int x,int y) {
        double r=Math.hypot((x+.5-16)/16,(y+.5-16)/16);
        return r>=1?0:(int)Math.round(255*Math.exp(-r*r*3)*Math.clamp((1-r)*8,0,1));
    }
    public static int circuitAlpha(int x,int y) {
        if(x<3||x>60||y<2||y>125)return 0;
        double light=0;
        for(int lane=0;lane<6;lane++) {
            double bend=22+lane*5, direction=lane%2==0?1:-1;
            double path=6+lane*10+direction*3*(Math.clamp((y-bend)/3,0,1)-Math.clamp((y-(84-lane*3))/3.0,0,1));
            double d=x-path;
            light=Math.max(light,220*Math.exp(-d*d/.4)+35*Math.exp(-d*d/2.5));
            double node=Math.hypot(d,y-bend-3);
            light=Math.max(light,220*Math.max(0,1-node/2.4));
        }
        return Math.clamp((int)Math.round(light),0,255);
    }
    private static void circuitPanel(VertexConsumer v,Matrix4f m,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float alpha) {
        int opacity=Math.clamp(Math.round(alpha*255),0,255),i=0;
        for(Vec3 p:new Vec3[]{a,b,c,d}) {
            v.addVertex(m,(float)p.x,(float)p.y,(float)p.z).setColor(165,80,255,opacity)
                    .setUv(i==0||i==3?.513671875f:.736328125f,i<2?.98046875f:.01953125f);
            i++;
        }
    }
    private static void ensureEnergyTexture() {
        if(textureReady)return;
        var pixels=new com.mojang.blaze3d.platform.NativeImage(256,128,false);
        for(int y=0;y<128;y++)for(int x=0;x<256;x++)
            pixels.setPixelRGBA(x,y,((x<128?energyAlpha(x/4,y/4):x>=192?255:circuitAlpha(x-128,y))<<24)|0xFFFFFF);
        var texture=new net.minecraft.client.renderer.texture.DynamicTexture(pixels);
        texture.setFilter(true,false);
        Minecraft.getInstance().getTextureManager().register(ENERGY_TEXTURE,texture);
        textureReady=true;
    }
    private static void disc(VertexConsumer v,Matrix4f m,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float alpha) {
        int opacity=Math.clamp(Math.round(alpha*255),0,255), i=0;
        for(Vec3 p:new Vec3[]{a,b,c,d}) {
            v.addVertex(m,(float)p.x,(float)p.y,(float)p.z).setColor((color>>16)&255,(color>>8)&255,color&255,opacity)
                    .setUv(i==0||i==3?.0078125f:.4921875f,i<2?.984375f:.015625f);
            i++;
        }
    }
    private static void quad(VertexConsumer v,Matrix4f m,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float alpha) {
        int opacity=Math.clamp(Math.round(alpha*255),0,255);
        for(Vec3 p:new Vec3[]{a,b,c,d}) v.addVertex(m,(float)p.x,(float)p.y,(float)p.z).setColor((color>>16)&255,(color>>8)&255,color&255,opacity).setUv(.875f,.5f);
    }
}
