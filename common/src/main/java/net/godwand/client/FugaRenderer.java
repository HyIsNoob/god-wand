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
            return distance < .0001 ? origin : origin.add(travel.scale(Math.min(distance, Math.max(0, age) * 4) / distance));
        }
    }
    private static final Map<Integer, FugaFxPayload> ACTIVE = new LinkedHashMap<>();
    private static final Map<Integer, Long> RECEIVED = new LinkedHashMap<>();
    // Keep finished tokens as well, so late charge/fire packets cannot resurrect a cancelled cast.
    private static final Map<Integer, FugaFxPayload> LATEST = new LinkedHashMap<>();
    private static ClientLevel world;
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
        ACTIVE.put(p.casterId(), p);
        if (ACTIVE.size() > 16) ACTIVE.remove(ACTIVE.keySet().iterator().next());
        RECEIVED.keySet().retainAll(ACTIVE.keySet());
    }
    public static void reset() { ACTIVE.clear(); RECEIVED.clear(); LATEST.clear(); world = null; }
    public static void tick(Minecraft client) {
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
                    || p.phase() == 2 && age >= 160;
        });
        RECEIVED.keySet().retainAll(ACTIVE.keySet());
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
        return new Frame(casterId,p.startedAt(),p.phase(),Math.max(0,world.getGameTime()+partial-p.phaseAt()),origin,target);
    }
    public static Frame localFrame(float partial) {
        var player = Minecraft.getInstance().player;
        return player == null ? null : frameFor(player.getId(),partial);
    }
    public static void render(PoseStack pose, Camera camera, float partial) {
        if (world == null || world != Minecraft.getInstance().level || ACTIVE.isEmpty()) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            Frame local = localFrame(partial);
            double darkness = FugaCinematic.environmentGain()<1 && local!=null ? 1-smooth((local.age()-26)/10) : 0;
            if (local != null && local.phase() == 0 && local.age() < 36 && darkness > 0) {
                BufferBuilder backdrop = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
                emitBackdrop(backdrop,pose.last().pose(),local,camera.getPosition(),darkness);
                var backdropMesh = backdrop.build();
                if (backdropMesh != null) BufferUploader.drawWithShader(backdropMesh);
            }
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            for (int caster : ACTIVE.keySet()) {
                Frame frame = frameFor(caster,partial);
                if (frame == null) continue;
                BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
                emit(buffer,pose.last().pose(),frame,camera.getPosition(),world.getMinBuildHeight()-frame.origin().y);
                var mesh = buffer.build();
                if (mesh != null) BufferUploader.drawWithShader(mesh);
            }
        } finally {
            RenderSystem.depthMask(true); RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
        }
    }
    /** The plane stays behind the subject, leaving their existing depth buffer visible. */
    public static void emitBackdrop(VertexConsumer v, Matrix4f m, Frame frame, Vec3 eye, double alpha) {
        if (frame.phase() != 0 || frame.age() < 0 || frame.age() >= 36 || alpha <= 0) return;
        Vec3 aim = frame.target().subtract(frame.origin());
        Vec3 facing = direction(new Vec3(aim.x,0,aim.z));
        Vec3 center = frame.origin().subtract(facing.scale(.4)).add(0,1.5,0).subtract(eye);
        Vec3 right = side(facing).scale(24), up = new Vec3(0,24,0);
        quad(v,m,center.subtract(right).subtract(up),center.add(right).subtract(up),
                center.add(right).add(up),center.subtract(right).add(up),0,alpha);
    }
    /** Camera-relative QUADS, usable without a Minecraft instance or GPU. */
    public static void emit(VertexConsumer v, Matrix4f matrix, Frame f, Vec3 eye) {
        emit(v,matrix,f,eye,-384);
    }
    /** Floor is relative to the impact anchor; only the column extends below that anchor. */
    public static void emit(VertexConsumer v, Matrix4f matrix, Frame f, Vec3 eye, double floor) {
        if (!Double.isFinite(f.age()) || !Double.isFinite(floor) || !finite(f.origin().x,f.origin().y,f.origin().z,f.target().x,f.target().y,f.target().z,eye.x,eye.y,eye.z)
                || f.age() < 0 || f.phase() < 0 || f.phase() > 2 || f.phase() == 2 && f.age() >= 160) return;
        Vec3 base = f.origin().subtract(eye);
        if (f.phase() == 0) charge(v,matrix,f,base);
        else if (f.phase() == 1) flight(v,matrix,f,base);
        else impact(v,matrix,f.age(),base,Math.min(0,floor));
    }
    private static void charge(VertexConsumer v, Matrix4f m, Frame f, Vec3 base) {
        double age = f.age(), build = smooth((age-32)/70), draw = smooth((age-78)/42);
        Vec3 forward = direction(f.target().subtract(f.origin()));
        Vec3 right = side(forward), up = forward.cross(right).normalize();
        Vec3 hand = base.add(0,1.23,0).add(forward.scale(.8)).add(right.scale(-.34));
        Vec3 pulling = base.add(0,1.25,0).add(forward.scale(.65-draw*.75)).add(right.scale(.30));
        flame(v,m,hand.add(0,-.12,0),hand.add(up.scale(.52)),.17+.08*Math.sin(age*.16),age,0,1);
        flame(v,m,pulling.add(0,-.12,0),pulling.add(up.scale(.5)),.19,age,2,1);
        if (build <= 0) return;
        Vec3 last = null;
        for (int i=0;i<=28;i++) {
            double t = i/28.0*2-1;
            Vec3 point = hand.add(up.scale(t*1.65*build)).add(forward.scale((1-t*t)*.52*build));
            if(last != null) flame(v,m,last,point,(.12+.10*(1-Math.abs(t)))*build,age,i*.2,1);
            last=point;
        }
        Vec3 upper=hand.add(up.scale(1.65*build)), lower=hand.add(up.scale(-1.65*build));
        tube(v,m,upper,pulling,.018,0xFFF5B2,.8,age,0);
        tube(v,m,pulling,lower,.018,0xFFF5B2,.8,age,0);
        arrow(v,m,hand.add(forward.scale(2.25*build)),forward,2.8*build,.16*build,age,1);
        for(int i=0;i<12;i++) {
            double t=i/11.0*2-1, lick=.3+.25*(.5+.5*Math.sin(age*.17+i*2.4));
            Vec3 root=hand.add(up.scale(t*1.55*build)).add(forward.scale((1-t*t)*.52*build));
            Vec3 tip=root.add(up.scale(lick*build)).add(right.scale(Math.sin(i*2.4+age*.1)*.22*build));
            flame(v,m,root,tip,.11*build,age,i,1);
        }
        for(int i=0;i<14;i++) {
            double a=i*2.399+age*.018,r=.7+(i%4)*.26;
            Vec3 p=hand.add(right.scale(Math.cos(a)*r)).add(up.scale(Math.sin(a)*r));
            star(v,m,p,right,up,.025+.025*(.5+.5*Math.sin(age*.13+i)),.7);
        }
    }
    private static void flight(VertexConsumer v, Matrix4f m, Frame f, Vec3 base) {
        Vec3 direction=direction(f.target().subtract(f.origin()));
        Vec3 head=base.add(f.arrowPosition().subtract(f.origin()));
        arrow(v,m,head,direction,4.5,.32,f.age(),1);
        double length=Math.min(64,Math.min(160,f.age()*4));
        for(int lane=0;lane<5;lane++) {
            Vec3 right=side(direction),up=direction.cross(right).normalize(),last=head;
            for(int j=1;j<=32;j++) {
                double t=j/32.0, spread=t*.9, a=lane*TAU/5+f.age()*.12-t*5;
                Vec3 p=head.subtract(direction.scale(length*t)).add(right.scale(Math.cos(a)*spread)).add(up.scale(Math.sin(a)*spread));
                double width=(1-t)*(.19-lane*.022);
                tube(v,m,last,p,width,lane<2?0xFFF2B0:0xFF5A08,(1-t)*.65,f.age(),lane);
                last=p;
            }
        }
        Vec3 right=side(direction),up=direction.cross(right).normalize();
        for(int i=0;i<28;i++) {
            double t=(i*.618033+f.age()*.023)%1,a=i*2.399;
            Vec3 p=head.subtract(direction.scale(length*t)).add(right.scale(Math.cos(a)*(1+t*2))).add(up.scale(Math.sin(a)*(1+t*2)));
            star(v,m,p,right,up,.08*(1-t)+.02,1-t);
        }
    }
    private static void arrow(VertexConsumer v, Matrix4f m, Vec3 head, Vec3 d, double length, double radius, double age, double alpha) {
        Vec3 tail=head.subtract(d.scale(length));
        flame(v,m,tail,head,radius,age,2,alpha);
        Vec3 right=side(d),up=d.cross(right).normalize(),neck=head.subtract(d.scale(.95));
        for(int i=0;i<4;i++) {
            double a=i*TAU/4;
            Vec3 wing=neck.add(right.scale(Math.cos(a)*radius*2.8)).add(up.scale(Math.sin(a)*radius*2.8));
            flame(v,m,wing,head,radius*.7,age,i,alpha);
            flame(v,m,tail,tail.add(right.scale(Math.cos(a)*radius*2)).add(up.scale(Math.sin(a)*radius*2)).add(d.scale(.7)),radius*.4,age,i,alpha);
        }
    }
    private static void impact(VertexConsumer v, Matrix4f m, double age, Vec3 base, double floor) {
        if(age<40) {
            double descent=smooth(age/40),implode=1-smooth((age-33)/7);
            Vec3 center=base.add(0,38*(1-descent)+12,0);
            double radius=(10+6*smooth(age/18))*Math.max(.04,implode);
            sphere(v,m,center,radius,age,0xF52B06,.55);
            sphere(v,m,center,radius*.87,age+2,0xFF8C1D,.85);
            sphere(v,m,center,radius*.65,age+4,0xFFF4C5,.95);
            for(int i=0;i<24;i++) {
                double a=i*TAU/24;
                Vec3 p=center.add(Math.cos(a)*radius*.78,-radius*.6,Math.sin(a)*radius*.78);
                flame(v,m,p,p.add(Math.cos(a)*2,8+4*Math.sin(i+age*.09),Math.sin(a)*2),2,age,i,.8);
            }
            ring(v,m,center.add(0,-radius*.65,0),radius*1.2,.35,0x8A54FF,.35);
            ring(v,m,center.add(0,-radius*.7,0),radius*1.32,.20,0x479CFF,.3);
            aura(v,m,base,age,.5);
            return;
        }
        double fade=1-smooth((age-120)/40),rise=smooth((age-40)/6);
        double radius=FugaSequence.RADIUS/1.17*fade;
        if(age<45) sphere(v,m,base.add(0,5,0),(age-39)*8,age,0xFFF9DE,(45-age)/5);
        column(v,m,base,radius,floor,384*rise,age,fade,0xF42B06);
        column(v,m,base,radius*.78,floor,384*rise,age+3,fade*.9,0xFF941F);
        column(v,m,base,radius*.47,floor,384*rise,age+5,fade,0xFFF4D6);
        for(int i=0;i<28;i++) {
            double a=i*2.399+age*.023,r=radius*(.85+.28*Math.sin(i));
            Vec3 p=base.add(Math.cos(a)*r,0,Math.sin(a)*r);
            flame(v,m,p,p.add(Math.cos(a)*6,14+(i%6)*7,Math.sin(a)*6),2.6*fade,age,i,fade);
        }
        aura(v,m,base,age,fade);
        for(int i=0;i<3;i++) {
            double travel=((age-40+i*9)%28)/28;
            ring(v,m,base.add(0,.3+i*.5,0),12+travel*100,.3+travel,0xFF9A2B,(1-travel)*fade*.65);
        }
    }
    /** All intervening radii are populated every frame, independently of the ring pulses. */
    static void aura(VertexConsumer v, Matrix4f m, Vec3 base, double age, double fade) {
        for(int lane=0;lane<12;lane++) for(int step=0;step<12;step++) {
            double a=lane*TAU/12+step*.14+age*.004;
            double r=10+step*7+(age*.65%7),b=a+.045;
            Vec3 p=base.add(Math.cos(a)*r,.4+Math.sin(step+lane)*.25,Math.sin(a)*r);
            Vec3 q=base.add(Math.cos(b)*(r+6),.5,Math.sin(b)*(r+6));
            tube(v,m,p,q,.14+.08*Math.sin(step+age*.08),0xFF6215,fade*.19,age,lane);
        }
    }
    private static void column(VertexConsumer v, Matrix4f m, Vec3 base, double radius, double floor, double height, double age, double alpha, int color) {
        for(int band=0;band<24;band++) for(int s=0;s<32;s++) {
            Vec3[] p=new Vec3[4];
            for(int c=0;c<4;c++) {
                double y=(band+(c>=2?1:0))/24.0,a=(s+(c==1||c==2?1:0))*TAU/32;
                double r=radius*(1+.10*Math.sin(a*5-y*18+age*.14)+.07*Math.sin(a*11+y*28-age*.21));
                p[c]=base.add(Math.cos(a)*r,floor+(height-floor)*y,Math.sin(a)*r);
            }
            quad(v,m,p[0],p[1],p[2],p[3],color,alpha*(.75+.25*Math.sin(band+s)));
        }
    }
    private static void sphere(VertexConsumer v, Matrix4f m, Vec3 center, double radius, double age, int color, double alpha) {
        for(int band=0;band<12;band++) for(int s=0;s<24;s++) {
            Vec3[] p=new Vec3[4];
            for(int c=0;c<4;c++) {
                double theta=(band+(c>=2?1:0))*Math.PI/12-Math.PI/2;
                double a=(s+(c==1||c==2?1:0))*TAU/24;
                double r=radius*(1+.055*Math.sin(a*7+theta*9+age*.15));
                p[c]=center.add(Math.cos(a)*Math.cos(theta)*r,Math.sin(theta)*r,Math.sin(a)*Math.cos(theta)*r);
            }
            quad(v,m,p[0],p[1],p[2],p[3],color,alpha);
        }
    }
    private static void flame(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, double width, double age, double seed, double alpha) {
        tube(v,m,a,b,width,0xF02A05,alpha*.65,age,seed);
        tube(v,m,a,b,width*.71,0xFF9018,alpha*.8,age+1,seed);
        tube(v,m,a,b,width*.36,0xFFF1BA,alpha,age+2,seed);
    }
    /** Six-sided tapered volumes create a solid sculpted silhouette from every camera. */
    private static void tube(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, double width, int color, double alpha, double age, double seed) {
        Vec3 d=direction(b.subtract(a)),right=side(d),up=d.cross(right).normalize();
        for(int s=0;s<6;s++) {
            double angle=s*TAU/6,next=(s+1)*TAU/6;
            Vec3 ra=right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            Vec3 rb=right.scale(Math.cos(next)).add(up.scale(Math.sin(next)));
            Vec3 bend=right.scale(Math.sin(age*.19+seed)*width*.3).add(up.scale(Math.cos(age*.13+seed)*width*.3));
            Vec3 mid=a.lerp(b,.57).add(bend);
            quad(v,m,a.add(ra.scale(width*.65)),a.add(rb.scale(width*.65)),mid.add(rb.scale(width)),mid.add(ra.scale(width)),color,alpha);
            quad(v,m,mid.add(ra.scale(width)),mid.add(rb.scale(width)),b,b,color,alpha);
        }
    }
    private static void ring(VertexConsumer v, Matrix4f m, Vec3 base, double radius, double width, int color, double alpha) {
        for(int i=0;i<64;i++) {
            double a=i*TAU/64,b=(i+1)*TAU/64;
            quad(v,m,base.add(Math.cos(a)*(radius-width),0,Math.sin(a)*(radius-width)),
                    base.add(Math.cos(b)*(radius-width),0,Math.sin(b)*(radius-width)),
                    base.add(Math.cos(b)*(radius+width),0,Math.sin(b)*(radius+width)),
                    base.add(Math.cos(a)*(radius+width),0,Math.sin(a)*(radius+width)),color,alpha);
        }
    }
    private static void star(VertexConsumer v, Matrix4f m, Vec3 p, Vec3 right, Vec3 up, double size, double alpha) {
        quad(v,m,p.subtract(right.scale(size*3)),p.add(up.scale(size*.22)),p.add(right.scale(size*3)),p.subtract(up.scale(size*.22)),0xFFF1B5,alpha);
        quad(v,m,p.subtract(up.scale(size*3)),p.add(right.scale(size*.22)),p.add(up.scale(size*3)),p.subtract(right.scale(size*.22)),0xFFF8DD,alpha);
    }
    private static void quad(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, double alpha) {
        vertex(v,m,a,color,alpha); vertex(v,m,b,color,alpha); vertex(v,m,c,color,alpha); vertex(v,m,d,color,alpha);
    }
    private static void vertex(VertexConsumer v, Matrix4f m, Vec3 p, int color, double alpha) {
        int opacity=(int)(Math.max(0,Math.min(1,alpha))*255);
        v.addVertex(m,(float)p.x,(float)p.y,(float)p.z).setColor((color>>16)&255,(color>>8)&255,color&255,opacity);
    }
    private static Vec3 direction(Vec3 d) { return d.lengthSqr()<1e-8?new Vec3(0,0,1):d.normalize(); }
    private static Vec3 side(Vec3 d) { return direction(d.cross(Math.abs(d.y)>.98?new Vec3(1,0,0):new Vec3(0,1,0))); }
    private static double smooth(double t) { t=Math.max(0,Math.min(1,t)); return t*t*(3-2*t); }
    private static boolean finite(double... values) { for(double v:values)if(!Double.isFinite(v))return false; return true; }
}
