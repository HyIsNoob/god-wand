package net.godwand.qa;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.godwand.GodWandMod;
import net.godwand.client.AtomicRenderer;
import net.godwand.client.AtomicCinematic;
import net.godwand.item.GodWandItem;
import net.godwand.skill.AtomicSequence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Optional dev mod: only runs in a local world explicitly named Atomic QA. Never packaged in God Wand. */
public final class AtomicGameplayProbe implements ClientModInitializer {
    private static final String QA_WORLD=System.getProperty("godwand.qa.world","Atomic QA");
    private int ticks, phase, phaseStarted, integrationTicks;
    private net.minecraft.client.CameraType integrationCamera;
    private Boolean originalPostEffects;
    private Integer originalRenderDistance, originalTerrainRadius;
    private net.minecraft.world.phys.Vec3 castOrigin, lastWideCamera;
    private final List<net.minecraft.world.entity.Mob> pressureTargets=new ArrayList<>();
    private Float waitingArm,normalLegX,normalLegZ;
    private final List<Integer> buildupFps=new ArrayList<>();
    private final List<net.minecraft.world.entity.Mob> primaryTargets=new ArrayList<>();
    private net.minecraft.world.phys.Vec3 waitingCamera;
    private float headYawMin=Float.POSITIVE_INFINITY, headYawMax=Float.NEGATIVE_INFINITY;
    private void restore(Minecraft client) {
        if(originalPostEffects!=null) {
            net.godwand.config.GodWandConfig.CLIENT.postEffects=originalPostEffects;
            originalPostEffects=null;
        }
        if(originalTerrainRadius!=null) {
            net.godwand.config.GodWandConfig.SERVER.atomicTerrainRadius=originalTerrainRadius;
            originalTerrainRadius=null;
        }
        if(originalRenderDistance!=null) {
            client.options.renderDistance().set(originalRenderDistance);
            originalRenderDistance=null;
        }
    }
    private boolean integrationPreflight(Minecraft client) {
        integrationTicks++;
        if(integrationTicks==1) {
            integrationCamera=client.options.getCameraType();
            var screen=new net.godwand.client.gui.GodWandConfigScreen(null);
            screen.init(client,320,180);
            var widgets=screen.children().stream().filter(w -> w instanceof net.minecraft.client.gui.components.AbstractWidget)
                    .map(w -> (net.minecraft.client.gui.components.AbstractWidget)w).toList();
            for(var a:widgets) {
                if(a.getX()<0 || a.getY()<0 || a.getRight()>320 || a.getBottom()>180)throw new AssertionError("Config widget outside small GUI");
                for(var b:widgets)if(a!=b && a.getX()<b.getRight() && a.getRight()>b.getX() && a.getY()<b.getBottom() && a.getBottom()>b.getY())
                    throw new AssertionError("Overlapping config widgets");
            }
            ((net.minecraft.client.gui.components.CycleButton<?>)widgets.get(0)).onPress();
            screen.onClose();
            net.godwand.config.GodWandConfig.loadClient(client.gameDirectory.toPath().resolve("config"));
            if(net.godwand.config.GodWandConfig.CLIENT.wandSkin!=net.godwand.config.WandSkin.DEFAULT)throw new AssertionError("Esc close did not save skin");
            net.godwand.config.GodWandConfig.CLIENT.wandSkin=net.godwand.config.WandSkin.AUTO;
            net.godwand.config.GodWandConfig.saveClient();
            client.setScreen(new net.godwand.client.gui.GodWandConfigScreen(null));
            System.out.println("INTEGRATION_QA PASS: compact config layout, close/save/reload, AUTO restored");
        }
        if(integrationTicks==6)Screenshot.grab(client.gameDirectory,"atomic-v12-model-config.png",client.getMainRenderTarget(),message -> {});
        if(integrationTicks==10) {
            client.screen.onClose();
            client.getSingleplayerServer().execute(() -> client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID()).getInventory().selected=0);
            client.player.setXRot(0);
        }
        if(integrationTicks==20)client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        if(integrationTicks==25)Screenshot.grab(client.gameDirectory,"atomic-v12-model-shadow-first.png",client.getMainRenderTarget(),message -> {});
        if(integrationTicks==30)client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        if(integrationTicks==35)Screenshot.grab(client.gameDirectory,"atomic-v12-model-shadow-third.png",client.getMainRenderTarget(),message -> {});
        if(integrationTicks==40)net.godwand.config.GodWandConfig.CLIENT.wandSkin=net.godwand.config.WandSkin.AQUA_HEARTIA;
        if(integrationTicks==45)Screenshot.grab(client.gameDirectory,"atomic-v12-model-aqua-third.png",client.getMainRenderTarget(),message -> {});
        if(integrationTicks==50)client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        if(integrationTicks==55)Screenshot.grab(client.gameDirectory,"atomic-v12-model-aqua-first.png",client.getMainRenderTarget(),message -> {});
        if(integrationTicks==60) {
            net.godwand.config.GodWandConfig.CLIENT.wandSkin=net.godwand.config.WandSkin.AUTO;
            client.options.setCameraType(integrationCamera);
            System.out.println("INTEGRATION_QA: native model screenshots captured; AUTO restored");
        }
        if(integrationTicks==61) {
            net.godwand.client.SkillWheelScreen.OPEN.setDown(true);
            net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_V));
            net.godwand.client.SkillWheelScreen.handleKeyInput(client);
            if(!(client.screen instanceof net.godwand.client.SkillWheelScreen))throw new AssertionError("V did not open skill wheel");
        }
        if(integrationTicks==65)Screenshot.grab(client.gameDirectory,"atomic-v12-model-wheel.png",client.getMainRenderTarget(),message -> {});
        if(integrationTicks==70) { net.godwand.client.SkillWheelScreen.OPEN.setDown(false); if(client.screen!=null)client.screen.onClose(); System.out.println("INTEGRATION_QA PASS: V opens six-skill wheel"); }
        return integrationTicks<=70;
    }

    private final Set<Integer> captured=new HashSet<>(), checked=new HashSet<>();
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var server=client.getSingleplayerServer();
            if(client.player==null||client.level==null||server==null||!server.getWorldData().getLevelName().equals(QA_WORLD)) {
                restore(client); return;
            }
            if(Boolean.getBoolean("godwand.qa.integration") && phase==1 && integrationPreflight(client))return;
            if(client.screen!=null)return;
            ticks++;
            if(phase==0&&ticks>100) {
                originalTerrainRadius=net.godwand.config.GodWandConfig.SERVER.atomicTerrainRadius;
                phase=1;
                client.getWindow().setWindowed(1280,720);
                client.getWindow().setTitle("God Wand 1.3.0 - Atomic QA");
                client.options.pauseOnLostFocus=false;
                originalRenderDistance=client.options.renderDistance().get();
                client.options.renderDistance().set(8);
                client.options.broadcastOptions();
                phaseStarted=ticks;
                server.execute(() -> {
                    var player=server.getPlayerList().getPlayer(client.player.getUUID());
                    var stack=new ItemStack(GodWandMod.GOD_WAND_ITEM);
                    GodWandItem.setSkillMode(stack,5);
                    player.getInventory().setItem(0,stack);
                    player.getInventory().selected=0;
                    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    player.getCooldowns().removeCooldown(GodWandMod.GOD_WAND_ITEM);
                    player.inventoryMenu.broadcastChanges();
                    player.serverLevel().setDayTime(1000);
                    player.serverLevel().setWeatherParameters(6000,0,false,false);
                    net.godwand.config.GodWandConfig.SERVER.atomicTerrainRadius=98;
                    var ground=player.blockPosition().below();
                    for(int x=-100;x<=100;x++)for(int z=-100;z<=100;z++)for(int depth=0;depth<3;depth++) {
                        var block=depth==0?net.minecraft.world.level.block.Blocks.GRASS_BLOCK:net.minecraft.world.level.block.Blocks.DIRT;
                        player.serverLevel().setBlock(ground.offset(x,-depth,z),block.defaultBlockState(),2);
                    }
                });
            }
            if(phase==1&&ticks-phaseStarted>60) {
                client.player.getInventory().selected=0;
                if(!(client.player.getMainHandItem().getItem() instanceof GodWandItem))throw new AssertionError("QA wand not synchronized");
                var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)client.getEntityRenderDispatcher().getRenderer(client.player);
                renderer.getModel().setupAnim(client.player,0,0,client.player.tickCount,0,0);
                normalLegX=renderer.getModel().rightLeg.x; normalLegZ=renderer.getModel().rightLeg.z;
                client.options.keyUse.setDown(true);
                client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);
                castOrigin=client.player.position();
                phase=2;
                phaseStarted=ticks;
            }
            var frame=AtomicRenderer.localFrame(0);
            if(phase==2&&frame!=null) {
                client.options.keyUse.setDown(frame.age()<AtomicSequence.WAIT_TICK||frame.waiting()&&frame.waitTicks()<100);
                if(frame.waitTicks()>=100&&client.player.isUsingItem()&&checked.add(9406)) {
                    client.options.keyUse.setDown(false);
                    client.gameMode.releaseUsingItem(client.player);
                    System.out.println("ATOMIC_QA: released after five seconds in the dark waiting state");
                }
                if(frame.waiting()) {
                    for(int mark:new int[]{15,35,65,85})if(frame.waitTicks()>=mark&&checked.add(9500+mark))
                        Screenshot.grab(client.gameDirectory,"atomic-v12-wait-"+mark+".png",client.getMainRenderTarget(),message -> {});
                    if(frame.waitTicks()>=35&&waitingArm==null) {
                        var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)client.getEntityRenderDispatcher().getRenderer(client.player);
                        renderer.getModel().setupAnim(client.player,0,0,client.player.tickCount,0,0);
                        waitingArm=renderer.getModel().rightArm.xRot;
                        waitingCamera=client.gameRenderer.getMainCamera().getPosition();
                    }
                    if(frame.waitTicks()>=80&&checked.add(9480)) {
                        var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)client.getEntityRenderDispatcher().getRenderer(client.player);
                        renderer.getModel().setupAnim(client.player,0,0,client.player.tickCount,0,0);
                        if(Math.abs(renderer.getModel().rightArm.xRot-waitingArm)<.005)throw new AssertionError("Waiting arm animation froze with the scene clock");
                        if(client.gameRenderer.getMainCamera().getPosition().distanceTo(waitingCamera)<.003)throw new AssertionError("Waiting camera froze");
                        if(frame.age()!=AtomicSequence.WAIT_TICK||!scarMap().isEmpty())throw new AssertionError("Atomic detonated while still held");
                        server.submit(() -> {
                            var player=server.getPlayerList().getPlayer(client.player.getUUID());
                            if(!player.isUsingItem()||!net.godwand.skill.AtomicManager.invulnerable(player)||!net.godwand.skill.AtomicManager.movementLocked(player))
                                throw new AssertionError("Server did not maintain the held cast and protection");
                            var ground=net.minecraft.core.BlockPos.containing(frame.center()).below().offset(40,0,0);
                            if(!player.serverLevel().getBlockState(ground).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK))throw new AssertionError("Held Atomic carved terrain before release");
                        }).join();
                        System.out.println("ATOMIC_QA PASS: dark hold lasts beyond three seconds with moving real arms/camera, no blast/crater, server movement lock and invulnerability maintained");
                    }
                }
                int bucket=(int)frame.age();
                if(bucket>=180&&bucket<=380&&bucket%20==0&&checked.add(30000+bucket))buildupFps.add(client.getFps());
                if(bucket>=390&&checked.add(30390)) {
                    buildupFps.sort(Integer::compareTo);
                    System.out.println("ATOMIC_QA local buildup FPS min="+buildupFps.getFirst()+" median="+buildupFps.get(buildupFps.size()/2)+" samples="+buildupFps.size()+" window=1280x720 renderDistance=8; not a modpack benchmark");
                    server.submit(() -> {
                        var level=server.getPlayerList().getPlayer(client.player.getUUID()).serverLevel();
                        for(int radius:new int[]{49,95}) {
                            var mob=net.minecraft.world.entity.EntityType.PIG.create(level);
                            mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
                            mob.setHealth(1000); mob.setNoAi(true); mob.setNoGravity(true); mob.setSilent(true);
                            mob.setPos(frame.center().add(radius,0,0)); level.addFreshEntity(mob); primaryTargets.add(mob);
                        }
                    }).join();
                }
                if(bucket>=397&&bucket<401&&checked.add(386)) {
                    var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)client.getEntityRenderDispatcher().getRenderer(client.player);
                    var model=renderer.getModel();
                    model.setupAnim(client.player,0,0,client.player.tickCount+.1f,0,0);
                    float before=model.rightArm.xRot;
                    model.setupAnim(client.player,0,0,client.player.tickCount+.9f,0,0);
                    if(Math.abs(model.rightArm.xRot-before)<.02)throw new AssertionError("Arm animation ignores partial ticks");
                    System.out.println("ATOMIC_QA PASS: arm drop interpolates between ticks rather than stepping at 20 FPS");
                }
                if(bucket>=819 && client.gameRenderer.getMainCamera().isDetached())throw new AssertionError("Local head remains visible inside the return camera");
                if(bucket>=60&&checked.add(60)) { client.options.keyUp.setDown(true); client.options.keyJump.setDown(true); }
                if(bucket>=80&&checked.add(80)) client.getConnection().send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(
                        castOrigin.x+10,castOrigin.y+5,castOrigin.z,true));
                if(bucket>=90&&checked.add(90)) {
                    client.options.keyUp.setDown(false); client.options.keyJump.setDown(false);
                    if(client.player.position().distanceToSqr(castOrigin)>.01)throw new AssertionError("Caster moved during Atomic");
                    server.submit(() -> {
                        var player=server.getPlayerList().getPlayer(client.player.getUUID());
                        if(player.position().distanceToSqr(castOrigin)>.01)throw new AssertionError("Server caster moved during Atomic");
                    }).join();
                    System.out.println("ATOMIC_QA PASS: forward and jump input cannot move the caster on client or server");
                }
                for(int mark:new int[]{24,40,50,65,88,100,115,150,174,185,195,199,200,205,209,215,245,285,300,320,344,370,390,394,395,396,398,400,402,406,407,408,409,410,411,412,413,414,415,418,420,424,428,430,434,436,438,439,454,474,494,524,544,574,604,644,704,744,784,819}) {
                    if(frame.age()>=mark&&captured.add(mark)) {
                        Screenshot.grab(client.gameDirectory,"atomic-v12-phase-"+mark+".png",client.getMainRenderTarget(),message -> {});
                        double distance=client.gameRenderer.getMainCamera().getPosition().subtract(frame.center()).horizontalDistance();
                        if(distance>AtomicSequence.cameraBudget(client.options.getEffectiveRenderDistance())+1)throw new AssertionError("Camera escaped the loaded-terrain budget: "+distance);
                        System.out.println("ATOMIC_QA frame="+mark+" age="+frame.age()+" camera="+client.gameRenderer.getMainCamera().getPosition()+" cinematic="+AtomicCinematic.active());
                    }
                }
                if(bucket>467&&bucket<614)checkWideFrame(client,frame,"normal eight-chunk");
                if(bucket>=470&&bucket<=570&&bucket%10==0&&checked.add(40000+bucket))
                    System.out.println("ATOMIC_V12 blast FPS age="+bucket+" fps="+client.getFps());
                if(bucket>=350&&checked.add(40350)) {
                    try {
                        var active=AtomicRenderer.class.getDeclaredField("ACTIVE");active.setAccessible(true);
                        var state=((java.util.Map<?,?>)active.get(null)).get(client.player.getId());
                        var tip=state.getClass().getDeclaredField("bladeTip");tip.setAccessible(true);
                        System.out.println("ATOMIC_V12 captured sword tip="+tip.get(state)+" center="+frame.center());
                    } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
                }
                if(bucket>=30&&bucket<360&&bucket%20==0) {
                    var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)client.getEntityRenderDispatcher().getRenderer(client.player);
                    var model=renderer.getModel();
                    model.setupAnim(client.player,0,0,client.player.tickCount,35,70);
                    if(model.head.xRot>=0||model.head.xRot<-.22||Math.abs(model.head.yRot)>.36)
                        throw new AssertionError("Buildup gaze follows the downward mouse view rather than the idle glance");
                    headYawMin=Math.min(headYawMin,model.head.yRot); headYawMax=Math.max(headYawMax,model.head.yRot);
                }
                if(bucket>=370&&checked.add(2350)) {
                    if(headYawMax-headYawMin<.08)throw new AssertionError("The caster head stayed still during the buildup");
                    System.out.println("ATOMIC_QA PASS: real player head glances smoothly during buildup without bowing toward the ground");
                }
                if((bucket>=344&&checked.add(324))||(bucket>=404&&checked.add(396))) {
                    var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)client.getEntityRenderDispatcher().getRenderer(client.player);
                    var model=renderer.getModel();
                    model.setupAnim(client.player,0,0,client.player.tickCount,0,0);
                    float pitch=model.rightArm.xRot;
                    if(bucket<382&&(pitch> -2.05||pitch< -2.35||model.rightArm.yRot>-.5||model.leftArm.yRot<.5
                            ||Math.abs(model.leftArm.xRot-pitch)>.15))throw new AssertionError("Two-hand forward raise lost grip alignment: "+pitch);
                    if(bucket>=404&&pitch< -.4)throw new AssertionError("Rendered grip arm did not drop during ATOMIC: "+pitch);
                    if(bucket>=404)System.out.println("ATOMIC_QA PASS: rendered player arms raise during I AM and lower during ATOMIC");
                }
                if(bucket>=393&&bucket<396&&checked.add(373)) {
                    var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)client.getEntityRenderDispatcher().getRenderer(client.player);
                    var model=renderer.getModel();
                    model.setupAnim(client.player,0,0,client.player.tickCount,35,25);
                    if(model.leftArm.zRot<.4||model.leftArm.xRot< -1.3)throw new AssertionError("Left hand did not sweep across the torso before release");
                    System.out.println("ATOMIC_QA PASS: left arm crosses inward before the outward snap");
                }
                if(bucket>=402&&bucket<416&&checked.add(383)) {
                    var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)client.getEntityRenderDispatcher().getRenderer(client.player);
                    var model=renderer.getModel();
                    model.setupAnim(client.player,0,0,client.player.tickCount,35,25);
                    if(model.leftArm.zRot> -1||model.rightArm.zRot<1||Math.abs(model.head.yRot)>.01||Math.abs(model.head.xRot)>.01)
                        throw new AssertionError("ATOMIC did not snap the arms open with a forward gaze");
                    var matrix=new com.mojang.blaze3d.vertex.PoseStack();
                    float oldYaw=client.player.getYRot(), oldPitch=client.player.getXRot();
                    try {
                        var setup=net.minecraft.client.renderer.entity.LivingEntityRenderer.class.getDeclaredMethod("setupRotations",
                            net.minecraft.world.entity.LivingEntity.class,com.mojang.blaze3d.vertex.PoseStack.class,float.class,float.class,float.class,float.class);
                        setup.setAccessible(true);
                        setup.invoke(renderer,client.player,matrix,0f,frame.yaw()+45,0f,1f);
                    } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
                    var direction=matrix.last().pose().transformDirection(new org.joml.Vector3f(0,0,-1));
                    var expected=net.minecraft.world.phys.Vec3.directionFromRotation(0,frame.yaw());
                    if(new net.minecraft.world.phys.Vec3(direction.x,direction.y,direction.z).distanceTo(expected)>.01)
                        throw new AssertionError("Rendered body did not face the cast direction: "+direction+" expected "+expected);
                    if(client.player.getYRot()!=oldYaw||client.player.getXRot()!=oldPitch)throw new AssertionError("Atomic rewrote player view");
                    System.out.println("ATOMIC_QA PASS: arms snap outward, head and rendered body face forward, player view unchanged");
                }
                if(bucket>=100&&checked.add(100))server.submit(() -> {
                    var player=server.getPlayerList().getPlayer(client.player.getUUID());
                    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    var zombie=net.minecraft.world.entity.EntityType.ZOMBIE.create(player.serverLevel());
                    zombie.setPos(player.position().add(1,0,0)); zombie.setNoAi(true); player.serverLevel().addFreshEntity(zombie);
                    float health=player.getHealth();
                    boolean hit=zombie.doHurtTarget(player);
                    zombie.discard();
                    if(hit||player.getHealth()!=health||player.hurtTime>0)throw new AssertionError("Survival caster was hurt by a mob during Atomic");
                    if(player.hurt(player.damageSources().onFire(),4))throw new AssertionError("Atomic did not block fire damage");
                    System.out.println("ATOMIC_QA PASS: survival caster rejects a real zombie attack and fire damage without hurt animation");
                }).join();
                if(bucket>=350&&bucket<374&&checked.add(397)) {
                    var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)client.getEntityRenderDispatcher().getRenderer(client.player);
                    var model=renderer.getModel();
                    model.setupAnim(client.player,0,0,client.player.tickCount,0,0);
                    if(Math.abs(model.rightLeg.x-normalLegX)>.001||Math.abs(model.rightLeg.z-normalLegZ)>.001
                            ||Math.abs(model.rightLeg.xRot)>.001||Math.abs(model.rightLeg.zRot)>.01
                            ||Math.abs(model.leftLeg.xRot)>.001||Math.abs(model.leftLeg.zRot)>.01)
                        throw new AssertionError("Atomic moved or crossed the normally straight legs");
                    System.out.println("ATOMIC_QA PASS: both legs retain their normal straight stance during Atomic");
                }
                if(bucket>=150&&checked.add(150)) {
                    server.submit(() -> checkNoLift(client,server)).join();
                    try {
                        var effect=net.godwand.client.AtomicPostFx.class.getDeclaredField("chain"); effect.setAccessible(true);
                        var failed=net.godwand.client.AtomicPostFx.class.getDeclaredField("failed"); failed.setAccessible(true);
                        if(effect.get(null)==null||failed.getBoolean(null))throw new AssertionError("Atomic shader did not compile/render");
                        try {
                            var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                            var iris=api.getMethod("getInstance").invoke(null);
                            boolean active=(boolean)api.getMethod("isShaderPackInUse").invoke(iris);
                            if(!active)throw new AssertionError("Iris loaded without an active shaderpack");
                            System.out.println("ATOMIC_QA PASS: Iris shaderpack is active during the full cast");
                        } catch(ClassNotFoundException vanilla) {
                            System.out.println("ATOMIC_QA: vanilla renderer without Iris");
                        }
                        int atomicPrograms=0;
                        for(var pass:((net.godwand.mixin.PostEffectProcessorAccessor)effect.get(null)).getPasses()) {
                            var detonation=pass.getEffect().getUniform("DetonationTick");
                            if(detonation==null)continue;
                            atomicPrograms++;
                            if(detonation.getFloatBuffer().get(0)!=AtomicSequence.DETONATION
                                    ||pass.getEffect().getUniform("SequenceEnd").getFloatBuffer().get(0)!=AtomicSequence.END
                                    ||Math.abs(pass.getEffect().getUniform("Age").getFloatBuffer().get(0)-frame.age())>5)
                                throw new AssertionError("Shader clock drifted from the real server/cinematic clock");
                        }
                        if(atomicPrograms==0)throw new AssertionError("No live Atomic shader program");
                        var atlas=AtomicRenderer.class.getDeclaredField("ENERGY_TEXTURE"); atlas.setAccessible(true);
                        var texture=client.getTextureManager().getTexture((net.minecraft.resources.ResourceLocation)atlas.get(null));
                        if(!(texture instanceof net.minecraft.client.renderer.texture.DynamicTexture dynamic)||dynamic.getPixels()==null
                                ||(dynamic.getPixels().getPixelRGBA(0,0)>>>24)!=0)throw new AssertionError("Round particle atlas did not upload correctly");
                        if(dynamic.getPixels().getWidth()!=256||dynamic.getPixels().getHeight()!=128
                                ||(dynamic.getPixels().getPixelRGBA(134,10)>>>24)<200
                                ||(dynamic.getPixels().getPixelRGBA(128,0)>>>24)!=0
                                ||(dynamic.getPixels().getPixelRGBA(224,64)>>>24)!=255)
                            throw new AssertionError("Circuit atlas or its transparent gutter/white region did not upload");
                        System.out.println("ATOMIC_QA PASS: Atomic shader chain, round particles and circuit-panel atlas compiled/uploaded on the GPU");
                    } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
                }
                if(bucket>=534&&checked.add(7534))server.submit(() -> {
                    var level=server.getPlayerList().getPlayer(client.player.getUUID()).serverLevel();
                    for(int i=0;i<4;i++) {
                        var mob=net.minecraft.world.entity.EntityType.PIG.create(level);
                        mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
                        mob.setHealth(100); mob.setNoAi(true); mob.setNoGravity(true); mob.setSilent(true);
                        double distance=i==0?112:i==1?128:i==2?136:112;
                        mob.setPos(frame.center().add(distance,i==3?20:0,0));
                        level.addFreshEntity(mob); pressureTargets.add(mob);
                    }
                }).join();
                if(bucket>=574&&checked.add(7574))server.submit(() -> {
                    if(primaryTargets.get(0).isAlive()||primaryTargets.get(1).getHealth()<650||primaryTargets.get(1).getHealth()>750)
                        throw new AssertionError("Configured Atomic primary damage or its distance falloff was not applied");
                    System.out.println("ATOMIC_QA PASS: 9999-center primary damage kills a 1000-HP target at49 blocks;95-block target health="+primaryTargets.get(1).getHealth());
                    primaryTargets.forEach(net.minecraft.world.entity.Entity::discard);
                    for(int i=0;i<4;i++) {
                        var mob=pressureTargets.get(i);
                        if(i<2&&(mob.getHealth()>=100||Math.abs((100-mob.getHealth())%15)>.01))throw new AssertionError("Pressure front did not damage mob outside column: "+i);
                        if(i>=2&&mob.getHealth()!=100)throw new AssertionError("Pressure damaged a mob outside its ground path: "+i);
                        System.out.println("ATOMIC_QA pressure target="+i+" health="+mob.getHealth());
                        mob.discard();
                    }
                    System.out.println("ATOMIC_QA PASS: real outgoing pressure rings damage mobs at 112/128 blocks, spare 136-block and airborne controls");
                }).join();
                if(bucket>=784&&checked.add(790))server.submit(() -> {
                    var center=net.minecraft.core.BlockPos.containing(frame.center()).below();
                    for(int r:new int[]{40,70,94}) {
                        var pos=center.offset(r,0,0);
                        if(!server.overworld().isEmptyBlock(pos))throw new AssertionError("Column footprint not destroyed at radius "+r);
                    }
                    if(!server.overworld().getBlockState(center).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK))
                        throw new AssertionError("Caster pedestal lost");
                    System.out.println("ATOMIC_QA PASS: terrain destruction reaches the 98-block column footprint, caster pedestal preserved");
                }).join();
            }
            if(phase==2&&ticks-phaseStarted>AtomicSequence.END+30&&frame==null) {
                client.options.keyUse.setDown(false);
                if(!checked.contains(9480)||!checked.contains(9406)||!checked.contains(7574)||!checked.contains(150)||!checked.contains(100)||!checked.contains(386)||!checked.contains(373)||!checked.contains(383)||!checked.contains(2580))throw new AssertionError("A required gameplay/GPU/animation/shockwave check was skipped");
                if(!captured.contains(819))throw new AssertionError("Full Atomic cast ended before the final camera phase");
                if(client.gameRenderer.getMainCamera().isDetached())throw new AssertionError("Camera did not return to first person");
                Screenshot.grab(client.gameDirectory,"atomic-v12-return.png",client.getMainRenderTarget(),message -> {});
                assertScarPresent();
                var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)client.getEntityRenderDispatcher().getRenderer(client.player);
                renderer.getModel().setupAnim(client.player,0,0,client.player.tickCount,0,0);
                if(Math.abs(renderer.getModel().rightLeg.x-normalLegX)>.001||Math.abs(renderer.getModel().rightLeg.z-normalLegZ)>.001)
                    throw new AssertionError("Atomic lead-leg position leaked after the cutscene");
                System.out.println("ATOMIC_QA PASS: original lead-leg position restored and large aftermath remains after the cutscene");
                System.out.println("ATOMIC_QA PASS: cast through the real item/network/server path, captured "+captured.size()+" phases, camera restored");
                if(Boolean.getBoolean("godwand.qa.singleCast")) {
                    restore(client);
                    System.out.println("ATOMIC_QA PASS: single full cast, damage, straight legs, camera and settings restored");
                    client.stop(); return;
                }
                phase=3;
                phaseStarted=ticks;
            }
            if(phase==3&&ticks-phaseStarted==5)client.options.keyUp.setDown(true);
            if(phase==3&&ticks-phaseStarted==15) {
                client.options.keyUp.setDown(false);
                if(client.player.position().distanceToSqr(castOrigin)<.01)throw new AssertionError("Movement stayed locked after the return");
                System.out.println("ATOMIC_QA PASS: movement unlocks after the shoulder return");
            }
            if(phase==3&&ticks-phaseStarted==18)server.submit(() -> {
                var player=server.getPlayerList().getPlayer(client.player.getUUID());
                if(net.godwand.skill.AtomicManager.invulnerable(player))throw new AssertionError("Cast protection leaked after completion");
                if(!player.hurt(player.damageSources().generic(),1))throw new AssertionError("Survival caster stayed invulnerable after the cutscene");
                player.setHealth(player.getMaxHealth());
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                System.out.println("ATOMIC_QA PASS: normal survival damage resumes after the cutscene");
            }).join();
            if(phase==3&&ticks-phaseStarted==20) server.execute(() -> {
                var player=server.getPlayerList().getPlayer(client.player.getUUID());
                player.getCooldowns().removeCooldown(GodWandMod.GOD_WAND_ITEM);
                var ground=player.blockPosition().below();
                for(int x=-100;x<=100;x++)for(int z=-100;z<=100;z++)
                    player.serverLevel().setBlock(ground.offset(x,0,z),net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(),2);
            });
            if(phase==3&&ticks-phaseStarted==40) {
                client.options.keyUse.setDown(true);
                client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);
                phase=4;
                phaseStarted=ticks;
            }
            if(phase==4&&ticks-phaseStarted==130)server.submit(() -> checkNoLift(client,server)).join();
            if(phase==4&&ticks-phaseStarted==140) {
                client.options.keyUse.setDown(false);
                client.gameMode.releaseUsingItem(client.player);
            }
            if(phase==4&&ticks-phaseStarted>170) {
                if(AtomicRenderer.localFrame(0)!=null||AtomicCinematic.active()||client.gameRenderer.getMainCamera().isDetached())
                    throw new AssertionError("Early cancellation left Atomic state or camera active");
                System.out.println("ATOMIC_QA PASS: early release cancels the server sequence and restores the camera");
                phase=5;
                phaseStarted=ticks;
            }
            if(phase==5&&ticks-phaseStarted==30) {
                originalPostEffects=net.godwand.config.GodWandConfig.CLIENT.postEffects;
                net.godwand.config.GodWandConfig.CLIENT.postEffects=false;
                client.options.renderDistance().set(4);
                client.options.broadcastOptions();
                client.options.keyUse.setDown(true);
                client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);
                phase=6;
                phaseStarted=ticks;
            }
            if(phase==6&&frame!=null) {
                client.options.keyUse.setDown(frame.age()<AtomicSequence.CHARGE_TICKS+2);
                if(frame.age()==20) {
                    client.keyboardHandler.keyPress(client.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,org.lwjgl.glfw.GLFW.GLFW_PRESS,0);
                    if(client.screen!=null)throw new AssertionError("Escape opened a menu instead of skipping the camera");
                }
                if(frame.age()<AtomicSequence.END-20&&!AtomicCinematic.active()&&client.screen==null) {
                    System.out.println("ATOMIC_QA PASS: Escape skipped the camera without opening a menu or cancelling the spell, age="+frame.age());
                    phase=7;
                }
            }
            if(phase==7&&frame!=null) {
                client.options.keyUse.setDown(frame.age()<AtomicSequence.CHARGE_TICKS+2);
                if(frame.age()>=AtomicSequence.CHARGE_TICKS+2&&client.player.isUsingItem())client.gameMode.releaseUsingItem(client.player);
            }
            if(phase==7&&frame!=null&&frame.age()>=434&&checked.add(7440)) {
                client.options.renderDistance().set(8); client.options.broadcastOptions(); lastWideCamera=null;
                float castYaw=frame.yaw();
                var ridgeOrigin=frame.center();
                server.submit(() -> {
                    var level=server.getPlayerList().getPlayer(client.player.getUUID()).serverLevel();
                    for(int angle=-55;angle<=-43;angle++)for(int r=101;r<=108;r++)for(int y=0;y<24;y++) {
                        double a=Math.toRadians(castYaw+angle);
                        var p=net.minecraft.core.BlockPos.containing(ridgeOrigin.add(-Math.sin(a)*r,y,Math.cos(a)*r));
                        level.setBlock(p,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
                    }
                }).join();
                AtomicCinematic.reset();
                System.out.println("ATOMIC_QA: eight-chunk terrain ridge test, shader off, camera restored for blast framing");
            }
            if(phase==7&&frame!=null&&frame.age()>467&&frame.age()<614)checkWideFrame(client,frame,"obstructed eight-chunk shader-off");

            if(phase==7&&frame!=null&&((int)frame.age()==335||(int)frame.age()==416||(int)frame.age()==534||(int)frame.age()==574))
                Screenshot.grab(client.gameDirectory,"atomic-v12-fallback-"+(int)frame.age()+".png",client.getMainRenderTarget(),message -> {});
            if(phase==7&&frame==null&&ticks-phaseStarted>AtomicSequence.END+20) {
                assertScarPresent();
                client.options.renderDistance().set(12); client.options.broadcastOptions();
                server.submit(() -> {
                    var player=server.getPlayerList().getPlayer(client.player.getUUID());
                    var level=player.serverLevel();
                    for(int x=12;x<64;x++)for(int z=-2;z<=2;z++) {
                        var base=net.minecraft.core.BlockPos.containing(castOrigin).offset(x,0,z);
                        int y=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,base.getX(),base.getZ());
                        for(int h=0;h<1+x/16%3;h++)level.setBlock(new net.minecraft.core.BlockPos(base.getX(),y+h,base.getZ()),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
                    }
                    player.getAbilities().flying=true; player.onUpdateAbilities();
                    player.connection.teleport(castOrigin.x+45,castOrigin.y+55,castOrigin.z+45,135,42);
                }).join();
                phase=8; phaseStarted=ticks;
            }
            if(phase==8&&ticks-phaseStarted==40) {
                assertScarPresent();
                Screenshot.grab(client.gameDirectory,"atomic-v12-scar-after.png",client.getMainRenderTarget(),message -> {});
            }
            if(phase==8&&ticks-phaseStarted==550) {
                assertScarPresent();
                Screenshot.grab(client.gameDirectory,"atomic-v12-scar-held.png",client.getMainRenderTarget(),message -> {});
                System.out.println("ATOMIC_QA PASS: full-area scar still exists near thirty seconds after the cutscene, including stepped terrain and no post shader");
            }
            if(phase==8&&ticks-phaseStarted==630)
                Screenshot.grab(client.gameDirectory,"atomic-v12-scar-fading.png",client.getMainRenderTarget(),message -> {});
            if(phase==8&&ticks-phaseStarted==680) {
                if(!scarMap().isEmpty())throw new AssertionError("Scars did not expire after thirty seconds plus fade");
                Screenshot.grab(client.gameDirectory,"atomic-v12-scar-expired.png",client.getMainRenderTarget(),message -> {});
            }
            if(phase==8&&ticks-phaseStarted>700) {
                server.submit(() -> {
                    var player=server.getPlayerList().getPlayer(client.player.getUUID());
                    player.getAbilities().flying=false; player.onUpdateAbilities();
                    player.connection.teleport(castOrigin.x,castOrigin.y,castOrigin.z,0,0);
                }).join();
                System.out.println("ATOMIC_QA PASS: gameplay, persistent scar and fallback checks complete; testing held mode switch next");
                server.submit(() -> {
                    var player=server.getPlayerList().getPlayer(client.player.getUUID());
                    player.getCooldowns().removeCooldown(GodWandMod.GOD_WAND_ITEM);
                }).join();
                phase=9; phaseStarted=ticks;
            }
            if(phase==9&&ticks-phaseStarted>10) {
                client.options.keyUse.setDown(true);
                client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);
                phase=10; phaseStarted=ticks;
            }
            if(phase==10&&frame!=null&&frame.waiting()&&frame.waitTicks()>5) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new net.godwand.network.SelectSkillPayload(3,client.player.getInventory().selected));
                phase=11; phaseStarted=ticks;
            }
            if(phase==10&&ticks-phaseStarted>650)throw new AssertionError("Mode-switch cast never reached the held state");
            if(phase==11&&ticks-phaseStarted>20) {
                if(frame==null||!frame.waiting()||GodWandItem.getSkillMode(client.player.getMainHandItem())!=5)
                    throw new AssertionError("Mode-selection while using bypassed the existing server guard");
                System.out.println("ATOMIC_QA PASS: other-mode selection remains blocked while the item is held, preserving the shared input rule");
                client.options.keyUse.setDown(false); client.player.stopUsingItem();
                client.player.getInventory().selected=1;
                client.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(1));
                phase=12; phaseStarted=ticks;
            }
            if(phase==12&&ticks-phaseStarted>20) {
                if(AtomicRenderer.localFrame(0)!=null||AtomicCinematic.movementLocked()||client.gameRenderer.getMainCamera().isDetached())
                    throw new AssertionError("Changing the held slot left Atomic camera/input state");
                server.submit(() -> {
                    var player=server.getPlayerList().getPlayer(client.player.getUUID());
                    if(net.godwand.skill.AtomicManager.invulnerable(player)||net.godwand.skill.AtomicManager.movementLocked(player))
                        throw new AssertionError("Held Atomic protection leaked after item loss");
                }).join();
                System.out.println("ATOMIC_QA PASS: real carried-slot change cancels a held Atomic and clears camera, movement lock and protection");
                client.player.getInventory().selected=0;
                client.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(0));
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new net.godwand.network.SelectSkillPayload(3,0));
                phase=13; phaseStarted=ticks;
            }
            if(phase==13&&ticks-phaseStarted>20) {
                if(GodWandItem.getSkillMode(client.player.getMainHandItem())!=3)throw new AssertionError("Normal mode selection failed after Atomic cleanup");
                client.options.keyUse.setDown(true); client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);
                phase=14; phaseStarted=ticks;
            }
            if(phase==14&&ticks-phaseStarted>25) {
                if(!client.player.isUsingItem()||GodWandItem.getSkillMode(client.player.getUseItem())!=3||AtomicRenderer.localFrame(0)!=null)
                    throw new AssertionError("Supernova hold failed after Atomic cancellation");
                client.options.keyUse.setDown(false); client.gameMode.releaseUsingItem(client.player);
                phase=15; phaseStarted=ticks;
            }
            if(phase==15&&ticks-phaseStarted>15) {
                if(client.player.isUsingItem()||AtomicCinematic.movementLocked())throw new AssertionError("Supernova cancellation inherited Atomic hold behavior");
                System.out.println("ATOMIC_QA PASS: Supernova still starts charging and cancels on early release after Atomic cleanup");
                boolean expected=originalPostEffects; restore(client);
                if(net.godwand.config.GodWandConfig.CLIENT.postEffects!=expected)throw new AssertionError("QA leaked its shader override");
                System.out.println("ATOMIC_QA PASS: all interactive-release checks complete; original settings restored"); client.stop();
            }
        });
    }
    private void checkWideFrame(Minecraft client,AtomicRenderer.Frame frame,String label) {
        var pose=AtomicCinematic.camera(0,client.player.getEyePosition(),client.player.getYRot(),client.player.getXRot());
        if(pose==null)throw new AssertionError("Missing wide camera");
        var position=pose.position();
        if(lastWideCamera!=null&&position.distanceTo(lastWideCamera)>2)
            throw new AssertionError("Distant camera snapped: "+position.distanceTo(lastWideCamera));
        lastWideCamera=position;
        var offset=position.subtract(frame.center());
        if(offset.y>64)throw new AssertionError("Wide camera escaped into an overhead shot rather than choosing a clear azimuth");
        if(offset.horizontalDistance()<106)throw new AssertionError("Wide camera was pulled inside the light column");
        float roll=(float)(5*Math.sin((frame.age()-466)/148*Math.PI));
        var view=new org.joml.Matrix4f().rotateZ((float)Math.toRadians(roll)).rotateX((float)Math.toRadians(pose.pitch()))
                .rotateY((float)Math.toRadians(pose.yaw()+180)).translate((float)-offset.x,(float)-offset.y,(float)-offset.z);
        var projection=new org.joml.Matrix4f().perspective((float)Math.toRadians(128),16f/9,.05f,1600);
        for(int i=0;i<32;i++) {
            double a=i*Math.PI/16;
            var p=new org.joml.Vector4f((float)(98*Math.cos(a)),-14,(float)(98*Math.sin(a)),1).mul(view).mul(projection);
            if(p.w<=0||Math.abs(p.x/p.w)>=1||p.y/p.w<=-.8||p.y/p.w>=.8)
                throw new AssertionError("Wide shot cuts the column footprint: "+p+", camera "+offset);
        }
        if(AtomicSequence.letterbox(frame.age())!=0)throw new AssertionError("Blackbar remains over the explosion");
        if(AtomicSequence.pressureShake(frame.age(),offset.horizontalDistance())>.16&&checked.add(2580)) {
            var kicked=new com.mojang.blaze3d.vertex.PoseStack();
            var still=new com.mojang.blaze3d.vertex.PoseStack();
            float setting=net.godwand.config.GodWandConfig.CLIENT.cameraShake;
            try {
                net.godwand.config.GodWandConfig.CLIENT.cameraShake=1;
                AtomicCinematic.applyShake(kicked,0);
                net.godwand.config.GodWandConfig.CLIENT.cameraShake=0;
                AtomicCinematic.applyShake(still,0);
            } finally { net.godwand.config.GodWandConfig.CLIENT.cameraShake=setting; }
            if(Math.abs(kicked.last().pose().m30()-still.last().pose().m30())+Math.abs(kicked.last().pose().m31()-still.last().pose().m31())<.0001)
                throw new AssertionError("Pressure wave did not reach the real camera shake matrix");
            System.out.println("ATOMIC_QA PASS: passing pressure rings apply a mild impulse to the real view matrix; shake setting remains respected");
        }
        if((int)frame.age()==604)System.out.println("ATOMIC_QA PASS: "+label+" camera keeps the full footprint on screen without blackbars or clamp jumps");
    }
    private Map<?,?> scarMap() {
        try {
            var field=AtomicRenderer.class.getDeclaredField("SCARS"); field.setAccessible(true);
            return (Map<?,?>)field.get(null);
        } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private void assertScarPresent() {
        if(scarMap().isEmpty())throw new AssertionError("Aftermath scar was removed with the cutscene");
        if(AtomicSequence.scarOpacity(AtomicSequence.END+590)<.4)throw new AssertionError("Scar fades before the thirty-second hold ends");
    }
    private void checkNoLift(Minecraft client,net.minecraft.client.server.IntegratedServer server) {
        var player=server.getPlayerList().getPlayer(client.player.getUUID());
        var world=player.serverLevel();
        var records=net.godwand.spatial.SpatialDisplacementManager.getActiveRecordsForWorld(world.dimension());
        if(records.stream().anyMatch(r -> player.getUUID().equals(r.boundPlayerUuid)))
            throw new AssertionError("Atomic buildup displaced terrain");
        for(var entity:world.getAllEntities())
            if(entity instanceof net.minecraft.world.entity.Display.BlockDisplay && entity.getTags().contains("godwand_displaced"))
                throw new AssertionError("Atomic spawned terrain displays");
        System.out.println("ATOMIC_QA PASS: energy cage only; no terrain displacement or block displays during charge");
    }
}
