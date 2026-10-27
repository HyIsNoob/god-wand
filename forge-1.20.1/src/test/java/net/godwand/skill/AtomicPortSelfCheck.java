package net.godwand.skill;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.netty.buffer.Unpooled;
import net.godwand.client.AtomicRenderer;
import net.godwand.config.GodWandConfig;
import net.godwand.config.WandSkin;
import net.godwand.network.AtomicFxPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Runs without a display, a world or extra test dependencies. */
public final class AtomicPortSelfCheck {
    private static int checks;
    private static void check(boolean value,String message) {
        checks++;
        if(!value)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        net.godwand.skill.AtomicVertexOrderCheck.verify();
        check(AtomicSequence.CHARGE_TICKS==380 && AtomicSequence.ATOMIC_WORD==396 && AtomicSequence.WAIT_TICK==406 && AtomicSequence.DETONATION==414 && AtomicSequence.END==834,"Accepted timing changed");
        long started=1000, released=AtomicSequence.releaseTime(50000,started);
        check(AtomicSequence.castAge(49999,started,-1)==406,"Hold must freeze scene clock");
        check(AtomicSequence.beamRadius(AtomicSequence.castAge(49999,started,-1))==0,"Hold detonated");
        check(AtomicSequence.protectedDuringCast(406) && AtomicSequence.movementLocked(406),"Hold protection lost");
        check(released==50000 && AtomicSequence.castAge(released+8,started,released)==414,"Delayed release skipped blast");
        check(AtomicSequence.castAge(released+428,started,released)==834,"Delayed cutscene ended early");
        check(AtomicSequence.releaseTime(1380,started)==1409,"Early release missed the word and wait");
        check(AtomicSequence.releaseFlash(408)>.9 && AtomicSequence.impact(410),"Release flash and impact order");
        check(AtomicSequence.scarOpacity(AtomicSequence.END+599)>0 && AtomicSequence.scarOpacity(AtomicSequence.SCAR_END)==0,"Scar lifetime changed");
        check(AtomicManager.damage(0)==9999 && AtomicManager.damage(49)==4999.5f && AtomicManager.damage(98)==0,"Damage falloff changed");
        check(GodWandConfig.SERVER.atomicShockwaveDamage==15,"Pressure damage default changed");
        var limits=new GodWandConfig.Server();
        limits.atomicDamage=Float.NaN;limits.atomicShockwaveDamage=-1;limits.atomicTerrainRadius=999;limits.validate();
        check(limits.atomicDamage==9999&&limits.atomicShockwaveDamage==0&&limits.atomicTerrainRadius==98,"Config boundary validation");
        var config=new GodWandConfig.Client();
        check(config.getActiveSkin(2)==WandSkin.AQUA_HEARTIA && config.getActiveSkin(4)==WandSkin.AQUA_HEARTIA && config.getActiveSkin(5)==WandSkin.SHADOW_BLADE,"AGY AUTO skin mapping changed");
        for(int phase=0;phase<=4;phase++) {
            var packet=new AtomicFxPayload(17,1000,1.5,-4,9,phase,phase<2?-1:50000);
            var buf=new FriendlyByteBuf(Unpooled.buffer());
            try {packet.write(buf);check(packet.equals(AtomicFxPayload.read(buf))&&buf.readableBytes()==0,"Packet roundtrip "+phase);}
            finally {buf.release();}
        }
        for(int age:new int[]{0,36,72,114,150,174,199,209,240,296,344,370,385,396,398,400,402,404,406,408,410,412,413,414,416,434,474,534,594,674,754,834}) {
            var mesh=new Mesh();AtomicRenderer.emit(mesh,new Matrix4f(),age,new Vec3(0,2,160));
            check(mesh.vertices%4==0&&mesh.vertices<=60000,"Unbounded mesh "+age);
            if(age==0||age>=734||age>=406&&age<414)check(mesh.vertices==0,"Unexpected geometry "+age);
            else check(mesh.vertices>0,"Missing phase "+age);
        }
        for(int age=0;age<=834;age++) {
            var shot=AtomicSequence.shot(age,237);
            check(Double.isFinite(shot.offset().length())&&shot.fov()>=30&&shot.fov()<=128,"Invalid camera "+age);
        }
        System.out.println("Atomic Forge 1.20.1 self-check: "+checks+" checks passed");
    }
    private static final class Mesh implements VertexConsumer {
        int vertices;
        public VertexConsumer vertex(double x,double y,double z) {
            check(Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Math.abs(x)<=330&&Math.abs(z)<=330&&y>=-AtomicSequence.BEAM_DEPTH-1&&y<=1100,"Invalid vertex");
            return this;
        }
        public VertexConsumer color(int r,int g,int b,int a) {check(a>=0&&a<=255,"Invalid opacity");return this;}
        public VertexConsumer uv(float u,float v) {return this;}
        public VertexConsumer overlayCoords(int u,int v){return this;}
        public VertexConsumer uv2(int u,int v){return this;}
        public VertexConsumer normal(float x,float y,float z){return this;}
        public void endVertex(){vertices++;}
        public void defaultColor(int r,int g,int b,int a){}
        public void unsetDefaultColor(){}
    }
}
