#version 150
uniform sampler2D DiffuseSampler;
uniform sampler2D MainDepthSampler;
uniform mat4 InvProjMat;
uniform mat4 InvViewMat;
uniform vec3 CameraPos;
uniform vec3 BlastCenter;
uniform vec2 OutSize;
uniform float Age;
uniform float SequenceEnd;
uniform float DetonationTick;
uniform float ChargeProgress;
uniform float Compression;
uniform float ReleaseFlash;
uniform float FxFade;
uniform float Cut;
uniform float HoloRadius;
uniform float BeamRadius;
uniform float BeamHeight;
uniform float BeamDepth;
uniform float BlastPulse;
uniform float ShockRadius;
uniform float EmissionOnly;
uniform float ObserverGain;
uniform float ImpactFrames;
uniform float ImpactPulse;
uniform float Darken;
uniform float CenterX;
uniform float CenterY;
in vec2 texCoord;
out vec4 fragColor;

float phase(float age,float a,float b) { return smoothstep(a,b,age); }
float hash(vec3 p) { return fract(sin(dot(p,vec3(127.1,311.7,74.7)))*43758.5453); }
float noise(vec3 p) {
    vec3 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
vec2 cylinderHit(vec3 o,vec3 d,float radius,float height) {
    float a=dot(d.xz,d.xz), b=dot(o.xz,d.xz), c=dot(o.xz,o.xz)-radius*radius;
    float nearT=0.0, farT=1200.0;
    if(a<0.000001) { if(c>0.0)return vec2(1,-1); }
    else {
        float discriminant=b*b-a*c;
        if(discriminant<0.0)return vec2(1,-1);
        float root=sqrt(discriminant);
        nearT=max(0.0,(-b-root)/a); farT=(-b+root)/a;
    }
    if(abs(d.y)<0.000001) { if(o.y<0.0||o.y>height)return vec2(1,-1); }
    else {
        float lo=-o.y/d.y, hi=(height-o.y)/d.y;
        nearT=max(nearT,min(lo,hi)); farT=min(farT,max(lo,hi));
    }
    return vec2(nearT,farT);
}
void main() {
    vec2 uv=texCoord;
    vec3 original=texture(DiffuseSampler,uv).rgb;
    if(Age<0.0||Age>=SequenceEnd) { fragColor=vec4(EmissionOnly>.5?vec3(0):original,1); return; }
    vec2 ndc=uv*2.0-1.0;
    vec4 farView=InvProjMat*vec4(ndc,1,1);
    vec3 direction=normalize((InvViewMat*vec4(farView.xyz/farView.w,0)).xyz);
    float depth=texture(MainDepthSampler,uv).r;
    vec4 sceneView=InvProjMat*vec4(ndc,depth*2.0-1.0,1);
    float sceneDistance=depth>.99999?1200.0:length(sceneView.xyz/sceneView.w);
    vec3 origin=CameraPos-BlastCenter;
    vec3 surface=origin+direction*sceneDistance;
    float charge=ChargeProgress, collapse=Compression, fade=FxFade;
    vec3 emission=vec3(0);
    float extinction=0.0;
    float dark=1.0;
    if(Age<DetonationTick) {
        dark=1.0-Darken*ObserverGain*(.26*charge+.55*collapse);
        float spread=max(.0001,1.0-collapse);
        vec3 shift=vec3(0);
        vec2 gasHit=cylinderHit(origin-shift,direction,36.0*spread,54.0*spread);
        gasHit.y=min(gasHit.y,sceneDistance);
        if(gasHit.y>gasHit.x) {
            float stepSize=(gasHit.y-gasHit.x)/20.0, transmit=1.0;
            float jitter=hash(vec3(gl_FragCoord.xy,0));
            for(int i=0;i<20;i++) {
                vec3 p=origin+direction*(gasHit.x+(float(i)+jitter)*stepSize);
                vec3 local=(p-shift)/spread;
                float r=length(local.xz), angle=atan(local.z,local.x);
                float turbulence=noise(local*vec3(.23,.16,.23)-vec3(0,Age*.038,0));
                float curl=noise(local*.47+vec3(Age*.012,-Age*.06,0));
                float skirt=exp(-local.y*.38)*smoothstep(1.6,4.5,r)*(1.0-smoothstep(18.0,26.0,r));
                float jets=pow(.5+.5*sin(angle*11.0+local.y*.24-Age*.055+turbulence*3.0),4.0);
                float polygonRadius=26.0*.9238795/cos(mod(angle+6.2831853,.7853982)-.3926991);
                float curtain=exp(-pow((r-polygonRadius)/1.7,2.0))*(1.0-phase(local.y,26.0,31.0));
                float sector=floor((angle+.3926991)/.7853982)*.7853982;
                vec2 tower=abs(local.xz-vec2(cos(sector),sin(sector))*32.0);
                float bastion=exp(-pow((max(tower.x,tower.y)-2.3)/1.1,2.0))*(1.0-phase(local.y,36.0,45.0));
                float wall=(curtain*.75+bastion)*(.3+.7*jets);
                float holoDistance=length(vec2(length(local.xz)-HoloRadius,(local.y-3.2)*1.3));
                float holo=exp(-holoDistance*12.0)*phase(Age,290,350)*3.0;
                float density=((skirt*.32*(1.0-phase(Age,285.0,320.0)*.88)+wall*.18)*smoothstep(.25,.8,turbulence*.7+curl*.3)
                        *phase(Age,48,218)+holo)*(1.0-Cut);
                float release=phase(Age,396,398)*(1.0-phase(Age,402,406));
                float pressure=exp(-pow((r-(2.0+34.0*phase(Age,396,404)))/1.4,2.0))*exp(-local.y*.085);
                density+=pressure*release*.22;
                float roofView=phase(Age,115,125)*(1.0-phase(Age,196,205));
                density*=1.0-roofView*.8;
                float opacity=1.0-exp(-density*stepSize);
                vec3 color=mix(vec3(.16,.018,.48),vec3(1.2,.35,2.5),turbulence*.55+jets*.45);
                emission+=transmit*color*opacity;
                transmit*=1.0-opacity;
                if(transmit<.003)break;
            }
            extinction=max(extinction,(1.0-transmit)*.8);
        }
        if(depth<.99999) {
            float bounce=exp(-length(surface-vec3(0,1,0))*.1)*charge*.38;
            emission+=vec3(.42,.035,.8)*bounce;
        }
    } else {
        float t=Age-DetonationTick, beamRadius=BeamRadius;
        vec2 hit=cylinderHit(origin+vec3(0,BeamDepth,0),direction,beamRadius+8.0,BeamHeight+BeamDepth);
        hit.y=min(hit.y,sceneDistance);
        if(hit.y>hit.x&&(beamRadius>.001)) {
            float stepSize=(hit.y-hit.x)/32.0;
            float transmittance=1.0;
            float jitter=hash(vec3(gl_FragCoord.xy,0));
            for(int i=0;i<32;i++) {
                vec3 p=origin+direction*(hit.x+(float(i)+jitter)*stepSize);
                float radial=length(p.xz), interior=1.0-smoothstep(beamRadius*.6,beamRadius,radial);
                float corona=exp(-abs(radial-beamRadius)*.35);
                float turbulence=noise(p*vec3(.18,.065,.18)-vec3(0,t*.09,0));
                float threads=pow(.5+.5*sin(atan(p.z,p.x)*23.0+p.y*.14-t*.3+turbulence*4.0),10.0);
                float gates=pow(.5+.5*sin(p.y*.42-t*.32+turbulence*2.0),5.0);
                float column=(interior*.48+corona*(.055+.2*threads+.12*gates))*(1.0-step(BeamHeight,p.y))*step(-BeamDepth,p.y);
                float density=column*fade*BlastPulse;
                float opacity=1.0-exp(-density*stepSize);
                vec3 color=mix(vec3(.5,.02,2.1)*(1.0+turbulence),vec3(3.8,3.1,4.4),interior*.85+threads*.15);
                emission+=transmittance*color*opacity;
                transmittance*=1.0-opacity;
                if(transmittance<.003)break;
            }
            extinction=1.0-transmittance;
        }
        float wave=ShockRadius;
        if(depth<.99999) {
            float r=length(surface.xz);
            float groundMask=exp(-abs(surface.y)*.10);
            float rim=exp(-pow((r-wave)/max(.6,1.2+t*.03),2.0));
            float inner=exp(-pow((r-wave*.91)/max(.8,2.0+t*.02),2.0));
            float spokes=pow(.5+.5*sin(atan(surface.z,surface.x)*48.0+noise(surface*.1)*3.0),18.0);
            float cycle=fract(t/30.0), pressure=phase(Age,422.0,442.0)*(1.0-phase(Age,614.0,644.0));
            float peeling=exp(-pow((r-BeamRadius-cycle*32.0)/1.0,2.0))*(1.0-cycle)*pressure;
            emission+=vec3(.9,.35,1.5)*peeling*groundMask*fade;
            emission+=(vec3(1.4,.65,1.8)*rim+vec3(.5,.02,1.2)*inner+vec3(.3,.0,.7)*spokes*rim)*groundMask*fade*BlastPulse*(1.0-phase(Age,614.0,704.0));
        }
        // A thin continuously fed outward flow; only the separate shockwaves have repeating fronts.
        float auraLife=phase(Age,DetonationTick,DetonationTick+5.0)*(1.0-phase(Age,574.0,734.0));
        vec2 auraHit=cylinderHit(origin,direction,310.0,72.0);
        auraHit.y=min(auraHit.y,sceneDistance);
        vec2 solidColumn=cylinderHit(origin+vec3(0,BeamDepth,0),direction,beamRadius,BeamHeight+BeamDepth);
        if(solidColumn.y>solidColumn.x) auraHit.y=min(auraHit.y,solidColumn.x);
        if(auraLife>0.0&&auraHit.y>auraHit.x) {
            float stepSize=(auraHit.y-auraHit.x)/24.0, transmit=1.0;
            vec3 auraColor=vec3(0);
            for(int i=0;i<24;i++) {
                vec3 p=origin+direction*(auraHit.x+(float(i)+.5)*stepSize);
                float r=length(p.xz), a=atan(p.z,p.x);
                float travel=r-beamRadius-t*6.0;
                float flow=noise(vec3(a*3.0,p.y*.08,travel*.055));
                float curl=noise(vec3(a*7.0,p.y*.12,travel*.09));
                float fed=1.0-phase(r,beamRadius+t*6.0,beamRadius+t*6.0+18.0);
                float heightMask=exp(-pow((p.y-22.0)/26.0,2.0));
                float outside=smoothstep(beamRadius-1.0,beamRadius+4.0,r)*(1.0-smoothstep(290.0,310.0,r));
                float density=heightMask*outside*fed*(.18+.82*smoothstep(.18,.75,flow*.7+curl*.3))*auraLife*.008;
                float opacity=1.0-exp(-density*stepSize);
                auraColor+=transmit*mix(vec3(.35,.015,1.05),vec3(1.45,.24,2.4),flow)*opacity;
                transmit*=1.0-opacity;
                if(transmit<.003)break;
            }
            emission=emission*transmit+auraColor;
            extinction=1.0-(1.0-extinction)*transmit;
        }
        float scar=phase(Age,644.0,734.0)*(1.0-phase(Age,814.0,SequenceEnd));
        if(depth<.99999) {
            float r=length(surface.xz), a=atan(surface.z,surface.x);
            float paths=exp(-pow(sin(a*12.0)*r/12.0/.45,2.0));
            float bands=max(exp(-pow((r-74.48)/.45,2.0)),exp(-pow((r-98.0)/.45,2.0)))*step(.1,sin(a*8.0));
            emission+=vec3(1.0,.09,2.1)*max(paths,bands)*step(3.0,r)*(1.0-step(98.0,r))*scar;
        }
        dark=1.0-Darken*ObserverGain*.28*fade*(1.0-scar);
        float afterglow=ImpactFrames*ObserverGain*(1.0-phase(t,5.0,16.0))*.18;
        emission+=vec3(afterglow);
    }
    emission*=ObserverGain*(1.0-Cut*ImpactFrames);
    if(EmissionOnly>.5) { fragColor=vec4(max(vec3(0),emission),1); return; }
    vec2 fromCenter=uv-vec2(CenterX,CenterY);
    if(Age>=DetonationTick&&ImpactFrames>.5) {
        float t=Age-DetonationTick, r=length(fromCenter);
        float pressure=exp(-pow((r-phase(t,0.0,25.0)*.9)/.055,2.0))*(1.0-phase(t,22.0,35.0));
        vec2 bend=fromCenter/max(r,.001)*pressure*.009*ObserverGain;
        original=vec3(texture(DiffuseSampler,uv+bend).r,texture(DiffuseSampler,uv).g,texture(DiffuseSampler,uv-bend).b);
    }
    float vignette=1.0-.18*charge*(1.0-phase(Age,DetonationTick,DetonationTick+40.0))*smoothstep(.18,.8,length(fromCenter));
    float grade=(Age<DetonationTick?phase(Age,48,234):fade)*ObserverGain*Darken;
    // Preserve the effects hue instead of progressively replacing red with blue.
    vec3 violet=original*vec3(.8,.45,1.0);
    vec3 base=mix(original,violet,grade*.72)*dark*vignette*(1.0-Cut)*(1.0-extinction*ObserverGain);
    vec3 result=1.0-(1.0-base)*exp(-emission);
    result=mix(result,vec3(.96,.88,1.0),ReleaseFlash*ImpactFrames*ObserverGain);
    float t=Age-DetonationTick;
    bool impact=ImpactPulse>.5;
    if(ImpactFrames>.5 && impact) {
        vec2 pixel=1.5/OutSize;
        vec3 horizontal=texture(DiffuseSampler,uv+vec2(pixel.x,0)).rgb-texture(DiffuseSampler,uv-vec2(pixel.x,0)).rgb;
        vec3 vertical=texture(DiffuseSampler,uv+vec2(0,pixel.y)).rgb-texture(DiffuseSampler,uv-vec2(0,pixel.y)).rgb;
        float edge=smoothstep(.06,.24,length(horizontal)+length(vertical));
        float light=dot(original+emission*.35,vec3(.2126,.7152,.0722));
        float ink=step(.33,light)*(1.0-edge);
        if(mod(floor(max(0.0,t)/2.0),2.0)<1.0)ink=1.0-ink;
        vec3 impactColor=mix(vec3(.012,.003,.025),vec3(.96,.84,1.0),ink);
        result=mix(result,impactColor,ObserverGain);
    }
    fragColor=vec4(clamp(result,0.0,1.0),1);
}
