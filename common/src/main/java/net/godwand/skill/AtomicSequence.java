package net.godwand.skill;

import net.minecraft.world.phys.Vec3;

/** One clock for the server blast, energy geometry, camera cuts and typography. */
public final class AtomicSequence {
    public static final int BEAM_RETRACTION=574;
    public static final int CHARGE_TICKS=380, WAIT_TICK=406, DETONATION=414, END=834;
    public static final int MAX_RADIUS=98, CAGE_RADIUS=26, ATOMIC_WORD=396, BEAM_HEIGHT=1024, BEAM_DEPTH=30, CRATER_DEPTH=28, SCAR_FADE_START=END+600, SCAR_END=END+660;
    public record Shot(Vec3 offset,Vec3 focus,float fov) {}
    public static float phase(double age,double start,double end) {
        float t=(float)Math.clamp((age-start)/(end-start),0,1);
        return t*t*(3-2*t);
    }
    public static float castAge(double now,long started,long releasedAt) {
        return (float)(releasedAt>=0&&now>=releasedAt?WAIT_TICK+now-releasedAt:Math.min(WAIT_TICK,Math.max(0,now-started)));
    }
    public static long releaseTime(long now,long started) { return Math.max(now,started+WAIT_TICK+3); }
    public static float heartbeat(double ticks) {
        if(ticks<0)return 0;
        double p=ticks%28, first=Math.min(p,28-p), second=p-5;
        return (float)Math.max(Math.exp(-first*first/2),.6*Math.exp(-second*second/2));
    }
    public static float holdIdle(double ticks) { return (float)(Math.sin(ticks*.075)*.018+Math.sin(ticks*.023)*.006); }
    public static float releaseFlash(double age) { return phase(age,WAIT_TICK,WAIT_TICK+2)*(1-phase(age,WAIT_TICK+2,WAIT_TICK+4)); }
    public static float charge(double age) { return phase(age,0,CHARGE_TICKS); }
    public static float visibility(double age) { return age<0?0:1-phase(age,814,END); }
    public static float compression(double age) {
        double remaining=1-Math.clamp((age-ATOMIC_WORD)/(WAIT_TICK-ATOMIC_WORD),0,1);
        return (float)(1-remaining*remaining*remaining);
    }
    public static float blackout(double age) { return .64f*phase(age,WAIT_TICK-6,WAIT_TICK)*(1-phase(age,WAIT_TICK,WAIT_TICK+2)); }
    public static float circuitProgress(double age,double seed) { return phase(age,115+seed*12,180); }
    public static float junctionWaveRadius(double age) { return age<=180||age>=203?0:(float)(age-180)*1.55f; }
    public static float armRaise(double age) { return phase(age,294,344); }
    public static float armSpread(double age) { return phase(age,ATOMIC_WORD,402); }
    public static float armWindup(double age) { return phase(age,382,394)*(1-armSpread(age)); }
    public static float facing(double age) { return phase(age,374,394)*(1-phase(age,774,802)); }
    public static float blendHeadYaw(float current, float target, float amount) {
        float from = net.minecraft.util.Mth.wrapDegrees(current * net.minecraft.util.Mth.RAD_TO_DEG);
        float to = net.minecraft.util.Mth.wrapDegrees(target * net.minecraft.util.Mth.RAD_TO_DEG);
        // Discard complete turns before fading the head pose toward the caster's front.
        return net.minecraft.util.Mth.wrapDegrees(net.minecraft.util.Mth.rotLerp(amount, from, to)) * net.minecraft.util.Mth.DEG_TO_RAD;
    }
    public static Vec3 headLook(double age,double seed) {
        double beat=Math.floor(age/58), t=phase(age,beat*58,(beat+1)*58);
        double[] look=new double[3];
        for(int axis=0;axis<3;axis++)
            look[axis]=moteNoise(beat+seed*17,axis+5)*(1-t)+moteNoise(beat+1+seed*17,axis+5)*t;
        return new Vec3(-.18+look[0]*.1,(look[1]-.5)*.58,(look[2]-.5)*.035);
    }
    public static Vec3 wideFocus(Vec3 camera,double fov) {
        double distance=camera.horizontalDistance();
        // Reserve ground framing room for the distant shot's five-degree roll.
        double half=Math.atan(Math.tan(Math.toRadians(Math.clamp(fov,20,128)/2))*.68);
        double foot=Math.atan2(-14-camera.y,Math.max(.1,distance-MAX_RADIUS));
        double elevation=Math.min(Math.atan2(104-camera.y,distance),foot+half);
        return new Vec3(0,camera.y+Math.tan(elevation)*distance,0);
    }
    public static float transitionOpacity(double age) {
        float opacity=0;
        for(int cut:new int[]{115,205,285})
            opacity=Math.max(opacity,.48f*(1-phase(Math.abs(age-cut),0,6)));
        return opacity;
    }
    public static float armPitch(double age) {
        float start=-.12f-1.3f*activation(age);
        float up=start+(-2.2f-start)*armRaise(age);
        up+=(-1.15f-up)*armWindup(age);
        return up+( -.12f-up)*armSpread(age);
    }
    public static float holoRadius(double age) {
        return .25f+2.1f*phase(age,290,370);
    }
    public static float shockRadius(double age) { return age<DETONATION?0:MAX_RADIUS*phase(age,DETONATION,534); }
    public static float beamRadius(double age) { return shockRadius(age)*(1-phase(age,BEAM_RETRACTION,734)); }
    public static float beamHeight(double age) { return age<DETONATION?0:BEAM_HEIGHT*phase(age,DETONATION,DETONATION+16); }
    public static float blastPulse(double age) { return 1; }
    public static float scarOpacity(double age) { return phase(age,644,734)*(.42f+.33f*(1-phase(age,814,END)))*(1-phase(age,SCAR_FADE_START,SCAR_END)); }
    public static boolean protectedDuringCast(double age) { return age>=0 && age<END; }
    public static boolean columnOccludes(Vec3 eye,Vec3 point,double age) {
        double radius=beamRadius(age), height=beamHeight(age);
        if(radius<=0||height<=0)return false;
        Vec3 d=point.subtract(eye);
        double a=d.x*d.x+d.z*d.z, b=eye.x*d.x+eye.z*d.z, c=eye.x*eye.x+eye.z*eye.z-radius*radius;
        double near=0,far=1;
        if(a<1e-8) { if(c>0)return false; }
        else {
            double discriminant=b*b-a*c;
            if(discriminant<0)return false;
            double root=Math.sqrt(discriminant);
            near=Math.max(near,(-b-root)/a); far=Math.min(far,(-b+root)/a);
        }
        if(Math.abs(d.y)<1e-8) { if(eye.y<-BEAM_DEPTH||eye.y>height)return false; }
        else {
            double low=(-BEAM_DEPTH-eye.y)/d.y, high=(height-eye.y)/d.y;
            near=Math.max(near,Math.min(low,high)); far=Math.min(far,Math.max(low,high));
        }
        return far>near && near<1-1e-6;
    }
    public static float letterbox(double age) { return age>=ATOMIC_WORD&&age<DETONATION?1:0; }
    public static float returnBlend(double age) { return phase(age,802,826); }
    public static boolean movementLocked(double age) { return age>=0 && age<826; }
    public static float activation(double age) { return phase(age,0,8)*(1-phase(age,22,40)); }
    public static boolean impact(double age) { return age>=10 && age<14 || age>=ATOMIC_WORD && age<ATOMIC_WORD+4 || age>=WAIT_TICK+4&&age<WAIT_TICK+6; }
    public static Vec3 groundTracePoint(double age,double seed) {
        double a=seed*Math.PI*2;
        Vec3[] path={polar(2,.08,a),polar(6,.08,a+.07),polar(11,.08,a+.07),polar(15,.08,a),polar(26,.08,a)};
        double t=phase(age,25+seed*55,100+seed*45)*4;
        int i=Math.min(3,(int)t);
        return path[i].lerp(path[i+1],t-i);
    }
    private static Vec3 polar(double r,double h,double angle) { return new Vec3(Math.cos(angle)*r,h,Math.sin(angle)*r); }
    public static Vec3 convergencePoint(double age,double seed,double progress,Vec3 target) {
        double t=Math.clamp(progress,0,1),u=1-t,a=seed*Math.PI*2+age*.006;
        boolean overhead=seed>=.5;
        double r=overhead?4+(seed-.5)*8:1.8+seed*.8;
        Vec3 source=polar(r,overhead?14+seed*5:.12,a);
        Vec3 bend=polar(r*.8,overhead?9:1.2+seed,a+.35);
        Vec3 approach=target.add(polar(.7,overhead?2.5:-.65,a+.5));
        return source.scale(u*u*u).add(bend.scale(3*u*u*t))
                .add(approach.scale(3*u*t*t)).add(target.scale(t*t*t));
    }
    private static double moteNoise(double seed,double salt) {
        double value=Math.sin(seed*1437.7+salt*311.7)*43758.5453;
        return value-Math.floor(value);
    }
    public static Vec3 risingMote(double rise,double seed) {
        double a=moteNoise(seed,1)*Math.PI*2, r=Math.sqrt(9+moteNoise(seed,0)*(24*24-9));
        return polar(r,.12+rise*(14+moteNoise(seed,2)*26),a);
    }
    public static double moteClock(double age) { return Math.min(age,390); }
    public static Vec3 releaseMote(Vec3 point,double age) {
        float push=phase(age,ATOMIC_WORD,ATOMIC_WORD+6);
        double r=Math.max(.001,point.horizontalDistance());
        return point.add(point.x/r*push*12,push*(.3+point.y*.06),point.z/r*push*12);
    }
    public static float fortressWaveHeight(double age) {
        if(age<170||age>=350)return -1;
        double cycle=(age-170)%60;
        return cycle<8||cycle>=50?-1:54*phase(cycle,8,50);
    }
    public static float releaseRadius(double age) {
        return age<ATOMIC_WORD||age>=DETONATION?0:1+34*phase(age,ATOMIC_WORD,ATOMIC_WORD+8);
    }
    public static float cameraBudget(int chunks) { return (float)Math.clamp((Math.max(2,chunks)-1)*16,8,112); }
    public static float cameraBudget(int chunks,double age) { return cameraBudget(chunks)+(age>=466&&age<614?16:0); }
    public static float pressure(double age) { return phase(age,DETONATION+8,DETONATION+28)*(1-phase(age,614,644)); }
    public static float pressureWaveRadius(double age,int wave) {
        if(pressure(age)<=0)return 0;
        double cycle=(age-DETONATION)/30+wave*.25;
        return beamRadius(age)+(float)(cycle-Math.floor(cycle))*32;
    }
    public static boolean pressureCrosses(double age,int wave,double distance) {
        if(distance<=MAX_RADIUS||distance>MAX_RADIUS+32||pressure(age)<=.15f||pressure(age-1)<=.15f)return false;
        float before=pressureWaveRadius(age-1,wave), now=pressureWaveRadius(age,wave);
        return now>=before && distance>=before-1.25 && distance<=now+1.25;
    }
    public static float pressureShake(double age,double cameraRadius) {
        float impulse=0;
        // Lead the luminous front by two ticks so the jolt lands on its leading edge.
        for(int wave=0;wave<4;wave++) {
            float radius=pressureWaveRadius(age+2,wave);
            if(radius>0)impulse+=(float)Math.exp(-Math.pow((radius-cameraRadius)/2.2,2))*.32f;
        }
        return impulse*pressure(age);
    }
    public static float shake(double age) {
        if(age<DETONATION) {
            float activation=age<10||age>=28?0:(float)(.32*Math.exp(-(age-10)/4));
            float release=age<ATOMIC_WORD||age>=ATOMIC_WORD+12?0:(float)(.22*Math.exp(-(age-ATOMIC_WORD)/3));
            float gathering=.10f*phase(age,294,310)*(1-phase(age,378,384));
            return activation+release+gathering+.55f*phase(age,DETONATION-2,DETONATION);
        }
        double t=age-DETONATION;
        return (float)(1.3*Math.exp(-t/18)+.34*Math.exp(-Math.pow((t-26)/7,2))
                +.20*Math.exp(-Math.pow((t-120)/12,2))+.12*pressure(age))*(1-returnBlend(age));
    }
    public static Shot shot(double age,float yaw) {
        double radius,height,focus,angle; float fov;
        if(age<40) {
            float t=phase(age,0,40);
            radius=5.2+1.6*t; height=.6; focus=.8; angle=70-18*t; fov=58;
        } else if(age<115) {
            double t=(age-40)/75;
            Vec3 head=groundTracePoint(16+(age-40)*.52,0);
            return new Shot(head.add(-5,.6+17*t*t,3+6*t),head.add(8,0,0),84);
        } else if(age<205) {
            // Keep the optical axis vertical; the closing roof and its expanding wave stay visible before the castle cut.
            radius=0; height=age<190?96-(age-115)*35/75:61-(age-190)*.2;
            focus=46; angle=0; fov=64;
        } else if(age<285) {
            double t=(age-205)/80;
            radius=30.5-2*t; height=53-46*t; focus=30-12*t; angle=-65+130*t; fov=100;
        } else if(age<DETONATION) {
            double t=Math.clamp((age-285)/95,0,1), out=phase(age,370,396), push=phase(age,396,402);
            radius=4.8-.6*t+.35*out+2.5*push+.008*Math.max(0,age-396)+.2*phase(age,402,DETONATION);
            height=3.5-.4*t-1.5*out; focus=2.15-.8*out;
            angle=(23-(age-285)*.22)*(1-phase(age,370,396)); fov=(float)(56-14*out+2*push);
        } else if(age<466) {
            radius=beamRadius(age)+20; height=2.8; focus=110; angle=-28; fov=96;
        } else if(age<614) {
            double t=(age-466)/148;
            radius=210+10*t; height=60; focus=104; angle=-46-6*t; fov=128;
        } else if(age<734) {
            radius=beamRadius(age)+18; height=8; focus=70; angle=-52; fov=96;
        } else if(age>=774) {
            double a=Math.toRadians(yaw-98), back=Math.toRadians(yaw+196), front=Math.toRadians(yaw);
            Vec3 wide=new Vec3(-Math.sin(a)*60,52,Math.cos(a)*60);
            Vec3 shoulder=new Vec3(-Math.sin(back)*2,1.55,Math.cos(back)*2);
            Vec3 ahead=new Vec3(-Math.sin(front)*8,1.5,Math.cos(front)*8);
            return new Shot(wide.lerp(shoulder,phase(age,774,802)),new Vec3(0,-.5,0).lerp(ahead,phase(age,774,798)),76);
        } else {
            radius=60; height=52; focus=-.5; angle=-84-(age-734)*.35; fov=76;
        }
        double radians=Math.toRadians(yaw+angle);
        return new Shot(new Vec3(-Math.sin(radians)*radius,height,Math.cos(radians)*radius),new Vec3(0,focus,0),fov);
    }
}
