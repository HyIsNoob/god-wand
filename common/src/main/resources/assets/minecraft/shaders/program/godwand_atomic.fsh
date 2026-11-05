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
uniform float FugaPhase;
uniform float FugaDirX;
uniform float FugaDirY;
uniform float FugaDirZ;
// 1 only in the half-resolution pass that raymarches Fuga's impact volumes; the full passes composite its result.
uniform float VolumePass;
// 0 while a shaderpack owns the depth buffer: depth is estimated from the ground plane under the effect.
uniform float DepthTrust;
uniform sampler2D FugaVolumeSampler;
// Depth conventions live only here, so version adapters never need to copy the effect itself.
float fugaNdcDepth(float depth) { return depth*2.0-1.0; }
bool fugaSky(float depth) { return depth>.99999; }
const float FUGA_FAR_DEPTH=1.0;
vec2 fireBox(vec3 o,vec3 d,vec3 lo,vec3 hi) {
    vec3 safe=sign(d)*max(abs(d),vec3(.00001));
    safe+=vec3(equal(safe,vec3(0)))*.00001;
    vec3 a=(lo-o)/safe,b=(hi-o)/safe;
    vec3 n=min(a,b),f=max(a,b);
    return vec2(max(0.0,max(n.x,max(n.y,n.z))),min(f.x,min(f.y,f.z)));
}
vec2 fireSphere(vec3 o,vec3 d,vec3 c,float r) {
    vec3 w=o-c;float b=dot(w,d),q=dot(w,w)-r*r,h=b*b-q;
    if(h<0.0)return vec2(1,-1);
    h=sqrt(h);return vec2(max(0.0,-b-h),-b+h);
}
// Closest visible point on a finite flame strand; thin fire does not disappear between volume samples.
vec3 fireStrand(vec3 o,vec3 d,vec3 a,vec3 b,float width,float limit,float gain) {
    vec3 u=b-a,w=o-a;float c=max(dot(u,u),.00001),h=dot(d,u),v=dot(d,w);
    float along=clamp((dot(u,w)-h*v)/max(.00001,c-h*h),0.0,1.0);
    float ray=clamp(h*along-v,0.0,limit);
    along=clamp(dot(o+d*ray-a,u)/c,0.0,1.0);
    ray=dot(a+u*along-o,d);
    if(ray<0.0||ray>limit)return vec3(0);
    float r=length(o+d*ray-a-u*along);
    float shell=1.0-smoothstep(width*.45,width,r);
    float core=1.0-smoothstep(width*.08,width*.3,r);
    float glow=exp(-r*r/max(.00001,width*width*5.0));
    return (vec3(2.6,.42,.02)*shell+vec3(4.6,3.6,2.2)*core+vec3(1.0,.08,.004)*glow*.3)*gain;
}
// Soft flame along a segment: a glow coloured by temperature, without fireStrand's hard white core.
vec3 fireColor(float temp);
vec3 softStrand(vec3 o,vec3 d,vec3 a,vec3 b,float width,float limit,float gain,float temp) {
    vec3 u=b-a,w=o-a;float c=max(dot(u,u),.00001),h=dot(d,u),v=dot(d,w);
    float along=clamp((dot(u,w)-h*v)/max(.00001,c-h*h),0.0,1.0);
    float ray=clamp(h*along-v,0.0,limit);
    along=clamp(dot(o+d*ray-a,u)/c,0.0,1.0);
    ray=dot(a+u*along-o,d);
    if(ray<0.0||ray>limit)return vec3(0);
    float r=length(o+d*ray-a-u*along)/width;
    return fireColor(temp)*(exp(-r*r*2.2)*.55+exp(-r*r*9.0)*.45)*gain;
}
float fbm(vec3 p) { return noise(p)*.6+noise(p*2.13+vec3(17.1,3.2,8.4))*.28+noise(p*4.31+vec3(2.7,11.9,5.3))*.12; }
float easeOut(float t) { t=clamp(t,0.0,1.0); return 1.0-(1.0-t)*(1.0-t)*(1.0-t); }
// Hot core to white, mantle orange, outer red; rim adds the violet/blue accent of the reference column.
vec3 fireColor(float temp) {
    vec3 c=mix(vec3(1.2,.05,.008),vec3(2.7,.55,.03),smoothstep(0.0,.4,temp));
    c=mix(c,vec3(3.6,2.0,.35),smoothstep(.38,.72,temp));
    return mix(c,vec3(5.2,5.0,4.6),smoothstep(.7,1.0,temp));
}
// Warm, bright pixels: the mesh fire. Sky and white blocks stay out because they are not saturated warm.
vec3 fugaHot(vec2 p) {
    vec3 c=texture(DiffuseSampler,clamp(p,vec2(.001),vec2(.999))).rgb;
    return c*smoothstep(.1,.42,c.r-c.b)*smoothstep(.5,.95,max(c.r,c.g));
}
// Radial light shafts: hot pixels smeared toward the light source, fading with distance along the shaft.
vec3 fugaRays(vec2 uv,vec2 c,float reach) {
    vec3 acc=vec3(0);float w=1.0;vec2 p=uv,stepv=(c-uv)*reach/24.0;
    for(int i=0;i<24;i++) {p+=stepv;acc+=fugaHot(p)*w;w*=.92;}
    return acc/9.0;
}
void fugaFire() {
    vec2 uv=texCoord,ndc=uv*2.0-1.0;
    vec4 farView=InvProjMat*vec4(ndc,fugaNdcDepth(FUGA_FAR_DEPTH),1);
    vec3 ray=normalize((InvViewMat*vec4(farView.xyz/farView.w,0)).xyz);
    float depth=texture(MainDepthSampler,uv).r;
    vec4 sceneView=InvProjMat*vec4(ndc,fugaNdcDepth(depth),1);
    bool sky=fugaSky(depth);
    float sceneDistance=sky?1600.0:length(sceneView.xyz/sceneView.w);
    if(DepthTrust<.5) {
        float down=-ray.y,ground=(CameraPos.y-BlastCenter.y)/max(down,.0001);
        sky=down<=.0001||ground<0.0;sceneDistance=sky?1600.0:min(ground,1600.0);
    }
    vec3 o=CameraPos-BlastCenter,d=normalize(vec3(FugaDirX,FugaDirY,FugaDirZ));
    vec3 right=normalize(cross(d,abs(d.y)>.98?vec3(1,0,0):vec3(0,1,0))),up=normalize(cross(right,d));
    vec3 surface=o+ray*sceneDistance;
    float age=Age<80.0?Age*.5:Age<105.0?40.0+(Age-80.0)*.8:60.0+(Age-105.0)*.5;
    bool charge=FugaPhase<.5,arrow=FugaPhase<1.5&&!charge;
    vec2 light=vec2(CenterX,CenterY);
    float aspect=OutSize.x/max(OutSize.y,1.0),toLight=length((uv-light)*vec2(aspect,1.0));
    vec3 emission=vec3(0);float transmit=1.0,heat=0.0,dust=0.0,suck=0.0,bounce=0.0;
    vec2 ballLens=vec2(0);
    // Fixed spatial dither hides march banding without a crawling temporal grain.
    float dither=fract(52.9829189*fract(dot(gl_FragCoord.xy,vec2(.06711056,.00583715))));
    if(arrow) {
        float trail=min(100.0,Age*4.0+10.0);
        vec3 localO=vec3(dot(o,right),dot(o,up),dot(o,d)),localD=vec3(dot(ray,right),dot(ray,up),dot(ray,d));
        vec2 hit=fireBox(localO,localD,vec3(-8,-6,-36.0),vec3(8,6,5));
        if(hit.y>hit.x) {
            float limit=sceneDistance;
            // Still Fuga, an arrow made of fire: a white-hot point and core with a long tapering wake.
            emission+=fireStrand(localO,localD,vec3(0,0,-.6),vec3(0,0,3.2),.55,limit,1.25);
            emission+=softStrand(localO,localD,vec3(0,0,-2.5),vec3(0,0,3.4),1.3,limit,1.0,.88);
            emission+=softStrand(localO,localD,vec3(0,0,-5.0),vec3(0,0,3.0),3.2,limit,.38,.55);
            float shaft=min(32.0,Age*4.0+8.0);
            for(int i=0;i<6;i++) {
                float t0=float(i)/6.0,t1=float(i+1)/6.0;
                emission+=softStrand(localO,localD,vec3(0,0,-1.0-shaft*.95*t0),vec3(0,0,-1.0-shaft*.95*t1),.85-.45*t0,limit,.55*(1.0-t0*.8),.86-.45*t0);
            }
            // Four flame ribbons: at the release they coil tight round the shaft (an arrow); over its first ticks they
            // drift apart into a gliding phoenix (two wings swept back, no beat, and two long tail plumes) that is
            // only suggested by the flowing fire. Each ribbon splits into two branches that part and rejoin.
            float morph=smoothstep(3.0,16.0,Age);
            // A while into the glide the wings fold in and the whole flame body barrel-rolls round the flight line,
            // then (on a long shot) opens out again.
            float fold=smoothstep(22.0,30.0,Age)*(1.0-smoothstep(40.0,48.0,Age));
            float spread=morph*(1.0-.5*fold),roll=fold*fold*(Age-22.0)*.55;
            float rc=cos(roll),rs=sin(roll);
            for(int lane=0;lane<4;lane++) {
                bool wingLane=lane<2;
                float sgn=lane==0||lane==2?1.0:-1.0,seed=float(lane)*3.17,base=float(lane)*1.5708+.7854;
                float len=wingLane?13.0:shaft*.9;
                vec3 lastA=vec3(0,0,1.2),lastB=lastA;
                for(int i=1;i<=14;i++) {
                    float t=float(i)/14.0,z=1.2-len*t;
                    float drift=noise(vec3(t*3.0-Age*1.1,seed,0))-.5;
                    float a=base+z*.55+Age*1.2;
                    vec3 coil=vec3(cos(a),sin(a),0)*(.35+.35*t)+vec3(0,0,z);
                    vec3 shape=wingLane
                        ? vec3(sgn*(4.0*sin(min(t*1.9,1.0)*1.5708)*(1.0-.3*smoothstep(.55,1.0,t))+drift*.7*t),
                               .55*sin(t*3.1416)+drift*.5*t,1.2-(1.5+11.0*t)*1.0)
                        : vec3(sgn*(.3+.9*t)+sin(z*.3+Age*.8+seed)*.5*t,-.35-.5*t+drift*.4*t,z);
                    vec3 c=mix(coil,shape,spread);
                    c.xy=vec2(c.x*rc-c.y*rs,c.x*rs+c.y*rc);
                    vec3 across=normalize(cross(vec3(0,0,1),vec3(c.xy,0)+vec3(.001,0,0)));
                    float part=.5*max(0.0,sin(t*7.0-Age*.9+seed))*t;
                    vec3 nextA=c+across*part,nextB=c-across*part;
                    float burn=(.7+.3*noise(vec3(t*6.0-Age*2.1,seed+5.0,0)))*pow(1.0-t,1.15)*(wingLane?.7:.6);
                    float w=(wingLane?.42:.32)+(wingLane?.65:.5)*t,temp=.82-.55*t;
                    // A wide, dim red mantle round each ribbon gives the fire body instead of a thin line.
                    emission+=softStrand(localO,localD,lastA,nextA,w*2.6,limit,burn*.3,.4-.2*t);
                    emission+=softStrand(localO,localD,lastA,nextA,w,limit,burn,temp);
                    emission+=softStrand(localO,localD,lastB,nextB,w*.75,limit,burn*.65,temp-.06);
                    if(i%4==2) {
                        float lick=fract(Age*.35+float(i)*.37+seed);
                        vec3 out2=normalize(vec3(c.xy,0)+vec3(.001,.3,0));
                        emission+=softStrand(localO,localD,c,c+out2*(.4+.8*lick)-vec3(0,0,1.0+1.8*lick),.25+.2*t,limit,burn*(1.0-lick),temp-.1*lick);
                    }
                    lastA=nextA;lastB=nextB;
                }
            }            // Wisps peel off the shaft and curl away as they burn out.
            for(int k=0;k<6;k++) {
                float life=fract(float(k)*.618+Age*.08),a0=float(k)*2.39996;
                float z0=-2.0-life*shaft*.8;
                vec3 last=vec3(cos(a0),sin(a0),0)*.8+vec3(0,0,z0);
                for(int i=1;i<=6;i++) {
                    float t=float(i)/6.0,a=a0+t*2.6,r=.8+3.2*t*(.5+.5*life);
                    vec3 next=vec3(cos(a)*r,sin(a)*r,z0-2.5*t);
                    emission+=softStrand(localO,localD,last,next,.2+.2*(1.0-t),limit,(1.0-life)*(1.0-t*.6)*.4,.7-.4*t);
                    last=next;
                }
            }            heat=clamp(length(emission)*.1,0.0,1.0);transmit=exp(-length(emission)*.06);
        }
        // Ground and walls along the flight path catch orange light.
        if(!sky) {
            vec3 s=surface,a=vec3(0),b=-d*trail*.55;
            float along=clamp(dot(s-a,b-a)/max(.001,dot(b-a,b-a)),0.0,1.0);
            float dist=length(s-a-(b-a)*along);
            bounce=exp(-dist/6.0)*(1.0-along*.6)*1.4;
        }
    } else if(!charge) {
        // Visual beats (mirrored by FugaRenderer.orb*): the energy mass falls from 10 blocks up to the
        // ground 0-39 (never resting, swelling from 33), contact at 39, a last swell 38.5-39.6, dark beat, detonation at 40. Air resistance slows it near the ground.
        float fall=clamp(age/39.0,0.0,1.0);
        // The compressed air below resists harder the closer it gets: it slows right down before contact.
        float gap=10.0*pow(1.0-fall,1.4);
        float implode=smoothstep(38.5,39.6,age);
        // Full size from its first frame; at the end it swells a little and flashes white instead of collapsing.
        float k=1.0+.1*smoothstep(33.0,39.0,age)+.12*implode;
        float orbR=44.0;
        // Stays round on contact: it swells (k) instead of flattening.
        float sy=1.0,sxz=1.0;
        float cy=gap+orbR*sy*k;
        // White-hot where the underside meets the compressed air and then the ground.
        float contact=1.0-smoothstep(0.0,12.0,gap);
        suck=0.0;
        float blast=age>=40.0?1.0:0.0;
        // The column punches up in a few ticks instead of growing.
        float rise=easeOut((age-40.0)/5.0),retract=1.0-smoothstep(120.0,160.0,age);
        // Retracting keeps a body and fades out rather than thinning into a beacon-like line.
        float R0=46.0*sqrt(retract)*(.7+.3*smoothstep(40.0,52.0,age)),H=384.0*rise*(1.0-.85*smoothstep(116.0,158.0,age));
        float fireball=70.0*easeOut((age-40.0)/9.0),fireballFade=1.0-smoothstep(44.0,62.0,age);
        float smoke=smoothstep(56.0,96.0,age)*(1.0-smoothstep(135.0,160.0,age));
        // Sphere, column, halos and outflow are raymarched once at half resolution (VolumePass) and read back here:
        // raymarching them in both full passes cost most of the frame.
        float nearGround=1.0-smoothstep(0.0,10.0,gap);
        float auraLife=age<40.0?nearGround*nearGround*(1.0-smoothstep(38.0,40.0,age)):smoothstep(40.0,45.0,age)*smoothstep(0.0,.35,retract);
        if(VolumePass<.5) {
            vec4 vol=texture(FugaVolumeSampler,uv);
            emission=vol.rgb/max(vec3(.003),1.0-vol.rgb);transmit=vol.a;heat=max(heat,(1.0-vol.a)*1.5);
        } else {
        vec2 hit;
        if(age<40.0)hit=fireSphere(o,ray,vec3(0,cy,0),orbR*max(sxz,sy)*k*1.55+.5);
        else hit=cylinderHit(o+vec3(0,40,0),ray,max(fireball*fireballFade,R0*2.4+smoke*30.0)+4.0,H+60.0);
        hit.y=min(hit.y,sceneDistance);
        if(hit.y>hit.x && (age<40.0 || retract>0.0)) {
            const int STEPS=32;
            float stepSize=(hit.y-hit.x)/float(STEPS);
            for(int i=0;i<STEPS;i++) {
                vec3 p=o+ray*(hit.x+(float(i)+dither)*stepSize);
                float density=0.0,temp=0.0,accent=0.0,soot=0.0;
                if(age<40.0) {
                    vec3 q=p-vec3(0,cy,0);q.y/=sy;q.xz/=sxz;
                    float ang=age*.05;vec3 w=q;w.xz=mat2(cos(ang),-sin(ang),sin(ang),cos(ang))*w.xz;
                    float r=length(q)/k;
                    float n=fbm(w*.085/k+vec3(0,-age*.09,0)),c2=noise(w*.24/k+vec3(age*.06,age*.11,0));
                    float shape=orbR*(.84+.18*n);
                    float core=1.0-smoothstep(shape*.1,shape*.55,r);
                    // A crisp molten limb: the sphere reads as a dense mass, not a soft white cloud.
                    float body=1.0-smoothstep(shape*.94,shape,r);
                    float tongue=exp(-abs(r-shape)*.3)*smoothstep(.45,.85,c2);
                    // Convection cells: bright rising plumes separated by darker, cooler lanes.
                    float cells=smoothstep(.3,.75,n*.65+c2*.35);
                    // Constant aura gusts blowing out of the surface, streaked radially.
                    vec3 dir=q/max(length(q),.001);
                    float gust=smoothstep(.55,.9,noise(dir*5.0+vec3(0,0,r*.12-age*.3)))*exp(-max(0.0,r-shape)/(orbR*(.14+.12*smoothstep(8.0,36.0,age))))*step(shape,r);
                    // Heavy energy keeps pouring off the surface as dense gusts.
                    density=(body*(.2+.2*cells)+core*.28+tongue*.14+gust*.1)/max(k,.12);
                    // Glowing fissures through a dark-red crust; rays that reach deeper see the white-hot heart,
                    // so the centre burns bright while the limb stays crimson.
                    // Two scales of ridged noise make a lightning-like network of burning veins over the dome.
                    // Ridges of fbm (not raw value noise, whose lattice showed as square cells).
                    float crack=pow(1.0-abs(fbm(w*.13/k+vec3(age*.03,-age*.05,0))*2.0-1.0),7.0)
                            +.6*pow(1.0-abs(fbm(w*.33/k+vec3(-age*.05,age*.08,3.7))*2.0-1.0),9.0);
                    float depthIn=.2*(1.0-smoothstep(shape*.35,shape,r));
                    // Only the very bottom, where it presses on the ground, burns white.
                    float belly=contact*smoothstep(.8,1.0,-q.y/max(length(q),.001))*.7;
                    density+=belly*body*.2;
                    // Saturated orange crust; yellow-white is kept for the veins, the heart and the contact.
                    // A glowing limb around the dome's edge, as in the reference underside.
                    float limb=body*smoothstep(shape*.84,shape*.99,r);
                    // Dark crust so the vein network carries the light.
                    temp=clamp(core*.2+body*(.05+.1*cells+2.4*crack+depthIn*.5)+limb*.6+belly+tongue*.3+gust*.3+implode*1.2,0.0,1.0);
                    soot=0.0;
                    // The violet/blue energy gathers underneath, where the air is compressed, as in the reference.
                    float under=1.0-smoothstep(-.75,.1,q.y/shape);
                    float bands=smoothstep(.55,.85,noise(vec3(atan(w.z,w.x)*2.0+q.y*.05,q.y*.07-age*.12,age*.05)));
                    accent=(body*bands*(.08+.5*under)+(tongue*.55+gust*.3)*(.25+.75*under))*(1.0-implode);
                } else {
                    float h=p.y,r=length(p.xz);
                    if(fireballFade>0.0) {
                        float n=fbm(p*.06+vec3(0,-age*.2,0));
                        float shell=exp(-pow((length(p)-fireball*(.7+.6*n))/(fireball*.22+1.0),2.0));
                        density+=shell*.07*fireballFade*step(-6.0,h);temp+=shell*fireballFade*.9;
                        // Violet/blue energy dome torn by streaks, as in the reference blast.
                        float streak=smoothstep(.55,.85,noise(vec3(atan(p.z,p.x)*6.0,h*.05,age*.2)));
                        accent+=shell*fireballFade*(.5+1.2*streak)*smoothstep(-4.0,20.0,h);
                    }
                    if(h<H && R0>0.0) {
                        float flare=1.0+.75*exp(-max(h,0.0)/13.0),top=1.0+.35*smoothstep(160.0,384.0,h);
                        // Fast upward flow: the slowed clock otherwise made the column read as sluggish.
                        float tw=atan(p.z,p.x)-h*.028+age*.14;
                        float streams=smoothstep(.42,.78,noise(vec3(cos(tw)*2.6,sin(tw)*2.6,h*.035-age*.75)));
                        float tear=fbm(p*vec3(.07,.035,.07)+vec3(0,-age*.95,0));
                        float R=R0*flare*top*(.62+.66*tear);
                        float body=1.0-smoothstep(R*.55,R,r),core=1.0-smoothstep(R*.1,R*.42,r);
                        float cap=smoothstep(H,H-28.0,h)*smoothstep(-40.0,-10.0,h)*smoothstep(0.0,.7,retract);
                        // Tongues tear out past the body edge, so the silhouette never reads as a clean cylinder.
                        float tongue=smoothstep(.6,.9,noise(vec3(cos(tw)*4.0,sin(tw)*4.0,h*.02-age*1.1)))*exp(-max(0.0,r-R)/14.0)*step(R*.8,r);
                        density+=(body*(.08+.17*streams)+core*.24+tongue*.06)*cap;
                        temp+=tongue*.45*cap;
                        temp+=(core*.95+body*streams*.6+body*.14)*cap;
                        float rim=body*(1.0-body)*4.0*smoothstep(R*.75,R*.95,r);
                        accent+=rim*streams*smoothstep(40.0,140.0,h)*cap*.45;
                        soot+=smoke*smoothstep(R*.85,R*1.25,r)*(1.0-smoothstep(R*1.4,R*2.2,r))*smoothstep(.45,.75,tear)*(1.0-smoothstep(150.0,300.0,h));
                    }
                    density+=soot*.06;
                }
                if(age<40.0)soot*=.25;
                float opacity=1.0-exp(-density*stepSize);
                // The meteor is a dense molten mass seen up close: its crust sits in deep red-orange and only the
                // veins, the heart and the contact climb into yellow, so the vein network reads instead of a white glare.
                vec3 color=age<40.0?fireColor(clamp(temp*.72,0.0,1.0))*.8:fireColor(clamp(temp,0.0,1.0));
                if(age<40.0)accent*=.35;
                float violet=noise(p*.04+vec3(age*.03,0,0));
                color+=mix(vec3(.75,.22,2.5),vec3(.3,.65,2.7),violet)*accent;
                color=mix(color,vec3(.035,.025,.02),clamp(soot*3.0,0.0,1.0)*(1.0-temp));
                emission+=transmit*color*opacity;transmit*=1.0-opacity;
                heat+=transmit*opacity;
                if(transmit<.004)break;
            }
        }
        // Pressure halos on the face that meets the ground, drawn crisp on the surface itself: bright rings run out from
        // the contact point across the underside with a spiky burning fringe trailing outward, faster the closer it
        // gets, lightning veins crawl between them and the contact burns white.
        if(age<40.0) {
            vec3 sc=vec3(sxz,sy,sxz),oo=(o-vec3(0,cy,0))/sc,dd=ray/sc;float dl=length(dd);dd/=dl;
            vec2 sh=fireSphere(oo,dd,vec3(0),orbR*k*.96);
            if(sh.y>sh.x&&sh.x/dl<sceneDistance) {
                vec3 n=normalize(oo+dd*sh.x);
                float theta=acos(clamp(-n.y,-1.0,1.0)),az=atan(n.z,n.x);
                float nearG=1.0-smoothstep(0.0,10.0,gap),flow=.02*age+.0016*age*age,halo=0.0;
                for(int b=0;b<3;b++) {
                    float life=fract(flow+float(b)/3.0),dk=theta-life*1.3;
                    float spike=pow(noise(vec3(az*60.0,float(b)*7.3,age*.3)),3.0);
                    float rim=exp(-pow(dk/.012,2.0))+step(0.0,dk)*exp(-dk/(.008+.09*spike))*.85;
                    float torn=.35+.65*smoothstep(.25,.6,noise(vec3(az*4.0+float(b)*2.1,life*5.0,0)));
                    halo+=rim*torn*smoothstep(0.0,.06,life)*(1.0-life);
                }
                float veins=pow(1.0-abs(fbm(n*7.0+vec3(age*.06,-age*.04,0))*2.0-1.0),14.0)*(1.0-smoothstep(.3,1.3,theta));
                float heart=exp(-theta*theta/.006)*(.5+1.5*contact);
                float sF=smoothstep(4.0,10.0,age)*(.35+.65*nearG)*(1.0-implode);
                emission+=(vec3(3.8,2.3,1.2)*(halo+heart)+vec3(3.4,1.5,.5)*veins)*sF;
            }
        }
        // Pressure aura (after Atomic's fed outflow, but turbulent and burning): hot air and flame keep pouring out of
        // the pillar along the ground and up its flanks, fed continuously, until the column has faded away.
        // Under the descending mass the same outflow hugs the ground, swelling as the gap closes (pressure escalation).
        if(auraLife>0.0) {
            // Bounded by how far the outflow has actually spread, so the 18 samples are not spent on empty air.
            float reach=age<40.0?orbR*.6+nearGround*110.0:R0+10.0+(age-40.0)*7.0;
            vec2 ah=cylinderHit(o+vec3(0,6,0),ray,min(240.0,reach+2.0),130.0);
            ah.y=min(ah.y,sceneDistance);
            if(ah.y>ah.x) {
                float st=(ah.y-ah.x)/18.0,at=1.0;vec3 ac=vec3(0);
                for(int i=0;i<18;i++) {
                    vec3 p=o+ray*(ah.x+(float(i)+dither)*st);
                    float r=length(p.xz),a=atan(p.z,p.x),travel=r-age*(3.4+5.0*nearGround*step(age,40.0));
                    // Two turbulent layers rolling outward and up; the curl twists them around the pillar.
                    float flow=fbm(vec3(cos(a)*2.6+travel*.035,p.y*.045-age*.2,sin(a)*2.6-travel*.02));
                    float curl=noise(vec3(a*6.0+age*.06+p.y*.02,p.y*.1,travel*.08));
                    float near=exp(-max(0.0,r-R0)/60.0);
                    float rise=exp(-max(0.0,p.y)/(14.0+60.0*near))*smoothstep(-6.0,0.0,p.y);
                    float fed=1.0-smoothstep(reach-30.0,reach,r),outside=smoothstep(R0*.6,R0*1.05,r);
                    float dens=rise*outside*fed*(.12+.88*smoothstep(.35,.8,flow*.7+curl*.3))*auraLife*.022*(.35+.65*near);
                    float op=1.0-exp(-dens*st);
                    float hot=near*smoothstep(.45,.9,flow);
                    ac+=at*mix(vec3(.55,.06,.01),vec3(3.0,1.05,.18),hot)*op;
                    heat+=at*op*2.0;
                    at*=1.0-op;
                    if(at<.01)break;
                }
                emission=emission*at+ac;transmit*=at;
            }
        }
        }
        if(VolumePass>.5) {fragColor=vec4(emission/(1.0+emission),transmit);return;}
        // Ground plane: pressure rings and dust under the falling sphere, shockwave after detonation.
        if(ray.y<-.0001) {
            float tg=(.3-o.y)/ray.y;
            if(tg>0.0&&tg<sceneDistance+1.5) {
                vec3 g=o+ray*tg;float rg=length(g.xz);
                float bottom=cy-orbR*sy*k;
                float pressure=age<40.0?(1.0-smoothstep(0.0,48.0,bottom))*(1.0-suck*.5):0.0;
                for(int i=0;i<3;i++) {
                    // Same torn, buckled pressure rings as the pillar's, squeezed out from under the falling mass.
                    float f=fract(age*(.06+.16*nearGround)+float(i)/3.0),ringR=orbR*(.55+f*(1.9+1.6*nearGround));
                    float ga=atan(g.z,g.x),buckle=(noise(vec3(ga*4.0,f*5.0+float(i)*3.1,age*.05))-.5)*(6.0+26.0*f);
                    float torn=smoothstep(.25,.65,noise(vec3(ga*7.0+float(i),rg*.03,age*.08)));
                    emission+=vec3(3.0,1.1,.3)*exp(-pow((rg+buckle-ringR)/(3.0+f*9.0),2.0))*(1.0-f)*pressure*torn*(.9+1.6*nearGround);
                }
                emission+=vec3(1.6,.4,.06)*exp(-rg/(orbR*.7))*pressure*.7;
                float churn=fbm(vec3(g.xz*.045,age*.06)+vec3(0,0,atan(g.z,g.x)));
                dust=smoothstep(orbR*.4,orbR*1.2,rg)*(1.0-smoothstep(orbR*1.5,orbR*3.0+pressure*30.0,rg))*smoothstep(.35,.7,churn)*pressure*.75;
                float wave=4.0+230.0*easeOut((age-40.0)/30.0),waveFade=blast*(1.0-smoothstep(42.0,78.0,age));
                emission+=vec3(3.2,1.7,.8)*exp(-pow((rg-wave)/(1.6+wave*.02),2.0))*waveFade*.9;
                dust=max(dust,smoothstep(wave-30.0,wave-2.0,rg)*(1.0-smoothstep(wave,wave+6.0,rg))*churn*waveFade*.8);
                // Pressure keeps peeling off the pillar as torn, buckled hot rings rolling out over the ground.
                for(int i=0;i<3;i++) {
                    float cyc=fract((age-40.0)/10.0+float(i)/3.0);
                    float ga=atan(g.z,g.x),buckle=(noise(vec3(ga*4.0,cyc*5.0+float(i)*3.1,age*.05))-.5)*(6.0+26.0*cyc);
                    float torn=smoothstep(.25,.65,noise(vec3(ga*7.0+float(i),rg*.03,age*.08)));
                    float peel=exp(-pow((rg+buckle-(R0+4.0+cyc*110.0))/(3.0+cyc*9.0),2.0))*(1.0-cyc)*auraLife*torn;
                    emission+=vec3(3.0,1.1,.3)*peel*.9;
                    dust=max(dust,peel*.45);
                }
            }
        }
        // Smouldering crater floor after the column.
        if(!sky&&age>=40.0) {
            float rs=length(surface.xz),glow=pow(noise(surface*.9+vec3(0,age*.03,0)),5.0)*.6;
            bounce=exp(-length(surface-vec3(0,8,0))/60.0)*retract*1.6;
            // The red-hot patches outlive the column and cool slowly (the client keeps an afterglow frame alive).
            emission+=vec3(2.2,.45,.04)*glow*(1.0-smoothstep(40.0,52.0,rs))*smoothstep(-80.0,-1.0,-abs(surface.y))*smoothstep(60.0,90.0,age)
                    *(1.0-smoothstep(175.0,330.0,age))*1.2;
        } else if(!sky) bounce=exp(-length(surface-vec3(0,cy,0))/(orbR*1.6))*1.5*(1.0-suck);
        // Anamorphic flare across the detonation.
        float flare=smoothstep(39.7,40.1,age)*(1.0-smoothstep(40.0,58.0,age))+(1.0-smoothstep(60.0,120.0,age))*blast*.18;
        emission+=vec3(2.6,1.3,.55)*exp(-abs(uv.y-CenterY)*80.0)*exp(-abs(uv.x-CenterX)*1.6)*flare;
    } else {
        // Charge. FugaDir carries the palm-to-palm span while the flame is pulled (BlastCenter sits 2 below the palms'
        // midpoint), then the aim once the arrow forms (BlastCenter 2 below its point). The matching mesh steps back.
        vec3 span=vec3(FugaDirX,FugaDirY,FugaDirZ);
        float len=length(span),ribbon=smoothstep(24.0,40.0,Age)*(1.0-smoothstep(93.0,96.0,Age));
        // Pull look. true: the fire gathers into a burning ball between the palms (FugaRenderer.PULL_BALL must match);
        // false: one dense band stretched between them.
        const bool PULL_BALL=true;
        if(PULL_BALL&&ribbon>0.0&&len>.05&&len<4.0) {
            vec3 mid=vec3(0,2,0);
            // A small ball that forms between the palms and spins ever faster (FugaRenderer.ballRadius/ballSpin match).
            // Spin speeds up but stays readable (capped near .35 rad/tick); a fast-rotating noise reads as flicker.
            // A bright point until the arms spread, then it swells slowly to full size right as the palms clap.
            float rB=(.008+.082*smoothstep(48.0,93.0,Age))*(1.0+.015*sin(Age*.5));
            float spinB=.1*max(0.0,Age-24.0)+.0018*pow(max(0.0,Age-24.0),2.0);
            // Heat lens hugging the rim: the air bends around the ball. Slow and noise-free, so it never flickers.
            vec3 toB=mid-o;float dB=length(toB);
            if(dB>.05) {
                float angB=acos(clamp(dot(ray,toB/dB),-1.0,1.0)),angR=asin(clamp(rB/dB,0.0,.99));
                float lens=exp(-pow((angB-angR*1.4)/(angR*.5+.002),2.0))*smoothstep(30.0,50.0,Age)*(1.0-smoothstep(92.0,95.0,Age));
                vec2 rad=(uv-light)*vec2(aspect,1.0);float rl=max(length(rad),.0001);
                vec2 rd=rad/rl,tg=vec2(-rd.y,rd.x)*sin(atan(rad.y,rad.x)*3.0+Age*.12);
                ballLens=(rd*.45+tg*.2)*lens*angR*vec2(1.0/aspect,1.0);
            }
            vec2 bh=fireSphere(o,ray,mid,rB*1.4);
            bh.y=min(bh.y,sceneDistance);
            if(bh.y>bh.x) {
                const int BS=24;
                float st=(bh.y-bh.x)/float(BS);
                for(int i=0;i<BS;i++) {
                    vec3 p=o+ray*(bh.x+(float(i)+dither)*st)-mid;
                    float r=length(p),ang=spinB;
                    vec3 w=p;w.xz=mat2(cos(ang),-sin(ang),sin(ang),cos(ang))*w.xz;
                    // A churning surface: it boils and breaks into small flames at its edge.
                    float n=fbm(w/rB*1.6+vec3(0,-Age*.12,0));
                    float body=1.0-smoothstep(rB*.8,rB*1.08,r+(n-.5)*rB*.4);
                    // Red swirling marks through the yellow-white ball, as in the reference.
                    float swirl=smoothstep(.55,.72,noise(vec3(atan(w.z,w.x)*2.0+w.y/rB*2.0,w.y/rB*1.5,r/rB*2.0)));
                    float density=body*(.7+.6*n)*30.0*step(.001,ribbon)*(1.0-smoothstep(93.0,96.0,Age));
                    float temp=clamp(.46+.2*(1.0-r/rB)+.12*n-.42*swirl,0.0,1.0);
                    float op=1.0-exp(-density*st);
                    emission+=transmit*fireColor(temp)*op;
                    transmit*=1.0-op;
                    if(transmit<.01)break;
                }
            }
        } else if(ribbon>0.0&&len>.05&&len<4.0) {
            vec3 ax=span/len,a0=vec3(0,2,0)-span*.5;
            vec3 sd=normalize(cross(ax,abs(ax.y)>.95?vec3(1,0,0):vec3(0,1,0))),upv=cross(sd,ax);
            if(upv.y<0.0){upv=-upv;sd=-sd;}
            vec3 rel=o-a0;
            vec3 lO=vec3(dot(rel,ax),dot(rel,sd),dot(rel,upv)),lD=vec3(dot(ray,ax),dot(ray,sd),dot(ray,upv));
            float W=.11+.06*smoothstep(24.0,90.0,Age);
            vec2 hb=fireBox(lO,lD,vec3(-.15,-W*3.2,-W*2.4),vec3(len+.15,W*3.2,W*4.2));
            hb.y=min(hb.y,sceneDistance);
            if(hb.y>hb.x) {
                const int RS=28;
                float st=(hb.y-hb.x)/float(RS);
                for(int i=0;i<RS;i++) {
                    vec3 q=lO+lD*(hb.x+(float(i)+dither)*st);
                    float t=clamp(q.x/len,0.0,1.0);
                    // One dense band of fire stretched taut between the palms: it grips both hands broadly, thins a little
                    // where it is pulled hardest, twists slowly along its length and rises as it burns.
                    float grip=1.0-.3*sin(3.14159*t);
                    float tw=t*2.2+Age*.04+.4*sin(t*3.0-Age*.11);
                    vec2 c=q.yz-vec2(.03*sin(t*5.0-Age*.2),.08*sin(3.14159*t));
                    c=mat2(cos(tw),-sin(tw),sin(tw),cos(tw))*c;
                    // Streaky turbulence stretched along the pull: the fire is being drawn out.
                    float flow=fbm(vec3(q.x*1.6-Age*.5,c.x/W*1.4,c.y/W*2.6+Age*.15));
                    float edge=(fbm(vec3(q.x*4.0-Age*.7,c.x/W*3.0,c.y/W*3.0-Age*.4))-.5)*.55;
                    float d=length(c/vec2(W*2.1*grip,W*.75*grip))+edge;
                    float body=1.0-smoothstep(.7,1.0,d);
                    // Hot streaks running along the band, and the odd small tongue splitting off its edge.
                    float streak=smoothstep(.5,.78,noise(vec3(q.x*1.2-Age*.55,c.x/W*3.5,c.y/W*1.5)));
                    float tongue=smoothstep(.62,.9,fbm(vec3(q.x*6.0-Age*.6,c.x/W*2.0,c.y/W*2.0-Age*.8)))
                            *(1.0-smoothstep(1.0,1.7,d))*smoothstep(.9,1.05,d);
                    // Nothing past the palms: a clamped end would otherwise leave a thin glowing rod.
                    float inside=smoothstep(-.04,.06,q.x)*(1.0-smoothstep(len-.06,len+.04,q.x));
                    float density=(body*(.9+1.2*flow)+tongue*.8)*34.0*ribbon*inside;
                    float temp=clamp(body*(.3+.35*flow)+streak*body*.55+(1.0-smoothstep(.0,.55,d))*.35+tongue*.25,0.0,1.0);
                    float op=1.0-exp(-density*st);
                    emission+=transmit*fireColor(temp)*op;
                    transmit*=1.0-op;
                    if(transmit<.01)break;
                }
            }
        }
    }
    // Anime impact frames: release, sphere birth, and the implosion turning into the blast.
    // Cut marks release/sphere birth as seen by this client; the implosion beat follows the shared clock.
    float impact=ImpactFrames>.5?max(Cut,FugaPhase>1.5?step(39.7,age)*step(age,40.5)*step(.5,ObserverGain):0.0):0.0;
    // The mesh fire feeds the bloom too, so every strand, ring and spark glows instead of reading as flat geometry.
    if(EmissionOnly>.5) {fragColor=vec4((emission+fugaHot(uv)*1.8)*(1.0-impact),1);return;}
    if(impact>.5) {
        vec3 base=texture(DiffuseSampler,uv).rgb;
        float lum=dot(1.0-(1.0-base)*exp(-emission*.85),vec3(.3,.59,.11));
        // Cut carries the beat after the arrow lands: 1 white on black, 2 inverted, 3 blood red, 4 white with speed
        // lines bursting from the impact. The release and the detonation use the plain beat.
        float beat=max(Cut,1.0);
        bool flip=beat<1.5?fract(Age*.5)>.5:beat<2.5;
        vec3 ink=vec3(.06,.0,.0),paper=vec3(1.0,.95,.88);
        if(beat>2.5&&beat<3.5){ink=vec3(.3,.01,0);paper=vec3(1.0,.36,.1);}
        bool lit=(lum>.42)!=flip;
        vec3 frame=lit?paper:ink;
        if(beat>3.5) {
            vec2 dv=(uv-light)*vec2(aspect,1.0);
            float spoke=step(.8,fract(sin(floor(atan(dv.y,dv.x)*30.0)*91.7)*437.3));
            frame=mix(frame,lit?ink:paper,spoke*smoothstep(.12,.45,length(dv)));
        }
        fragColor=vec4(frame,1);return;
    }
    // Sample the scene through animated heat refraction, attenuated by the visible fire volume.
    // Charge: heat haze wobbles the air around the fire, stronger as the pressure builds.
    float gather=smoothstep(24.0,96.0,Age),press=smoothstep(96.0,190.0,Age);
    // The haze eases off once the arrow is drawn, so the aim stays readable.
    // No shimmer while the ball is pulled (it made the body behind it flicker); it rises with the vortex after the clap.
    if(charge && CenterX>-1.0) heat=exp(-toLight*5.0)*gather*(.7+.7*press)*smoothstep(96.0,110.0,Age)*(1.0-.8*smoothstep(144.0,190.0,Age));
    // Strong heat shimmer: the air itself wobbles around the fire.
    vec2 bend=vec2(sin(uv.y*170.0-Age*.19)+.5*sin(uv.y*61.0+Age*.31),cos(uv.x*140.0+Age*.13)+.5*cos(uv.x*53.0-Age*.27))*.0028*clamp(heat+dust,0.0,2.5);
    bend+=ballLens;
    // Shockwave: a refractive ring tears outward from the palms at the clap (fast, before the black beat) and from the
    // impact at the detonation (wide and slower). Pure screen-space bend, so it costs a few instructions.
    float shockT=charge?(Age-94.4)/2.2:(!arrow?(age-40.3)/9.0:-1.0),shockGlow=0.0;
    if(shockT>0.0&&shockT<1.0&&CenterX>-1.0) {
        vec2 dv=(uv-light)*vec2(aspect,1.0);float dist=length(dv);
        float radius=shockT*(charge?.55:1.4),width=(charge?.035:.06)+.05*shockT;
        float ring=exp(-pow((dist-radius)/width,2.0))*(1.0-shockT)*(1.0-shockT);
        bend+=dv/max(dist,.0001)*vec2(1.0/aspect,1.0)*ring*(charge?.035:.05);
        // A faint hot rim, so the wave still reads against the black beat of the clap.
        shockGlow=ring*(charge?.35:.12);
    }
    vec3 scene=texture(DiffuseSampler,clamp(uv+bend,vec2(.001),vec2(.999))).rgb;
    // Chromatic split: the release kick and the detonation tear the image apart for a few frames.
    float split=arrow?1.0-smoothstep(0.0,7.0,Age):charge?0.0:smoothstep(39.7,40.1,age)*(1.0-smoothstep(40.1,47.0,age));
    if(split>0.0) {
        vec2 dir=(uv-(CenterX>-1.0?light:vec2(.5)))*.018*split;
        scene.r=texture(DiffuseSampler,clamp(uv+bend+dir,vec2(.001),vec2(.999))).r;
        scene.b=texture(DiffuseSampler,clamp(uv+bend-dir,vec2(.001),vec2(.999))).b;
    }
    scene*=1.0-.7*suck;
    // Chunks past the caster's view distance never reach this client and show as sky below the horizon; after the
    // blast that is exactly where the burning haze would be, so fill those holes with it.
    if(!charge&&!arrow&&age>=40.0&&sky&&ray.y<.02&&DepthTrust>.5) scene=mix(scene,vec3(.32,.11,.04),(1.0-smoothstep(-.04,.02,ray.y))*.92);
    // Smoke and heat haze fill the air around the blast evenly, so loaded terrain and missing chunks read alike.
    if(!charge&&!arrow&&age>=40.0) {
        // By view distance alone: a bounded smoke volume showed its edge as an arc across the frame.
        float L=sky?400.0:sceneDistance;
        // Only near the blast: a distant observer must not see the whole world fogged.
        scene=mix(scene,vec3(.32,.11,.04),(1.0-smoothstep(180.0,360.0,length(o)))*(1.0-exp(-L/22.0))*smoothstep(40.0,44.0,age)*(1.0-smoothstep(145.0,180.0,age))*.86);
    }
    scene=mix(scene,vec3(.2,.15,.12),clamp(dust,0.0,.85));
    float warmth=clamp(length(emission)*.08+bounce*.35,0.0,.45);
    scene=mix(scene,scene*vec3(1.1,.62,.34),warmth)+vec3(.16,.045,.006)*bounce;
    vec3 result=1.0-(1.0-scene*transmit)*exp(-emission*.85);
    // Short light shafts around the charging fire only; longer smears read as motion blur.
    // Radial smear shafts read as straight vertical lines once fire is everywhere; the charge relies on haze instead.
    float shafts=0.0;
    if(shafts>0.0 && CenterX>-1.0) result+=fugaRays(uv,light,.22)*shafts*vec3(1.0,.72,.42)*.6;
    // The shot: a soft warm flash as the arrow tears off the bow.
    if(arrow) result+=vec3(1.0,.7,.42)*.22*exp(-Age/3.0)*ObserverGain;
    // The mass's arrival: a soft warm flash as it appears overhead.
    if(!charge&&!arrow&&age<12.0) result+=vec3(1.0,.62,.32)*.4*exp(-age/1.8)*ObserverGain;
    result+=vec3(1.0,.55,.25)*shockGlow;
    // Charge vignette: the world closes in around the fire.
    if(charge) result*=1.0-.42*gather*ObserverGain*smoothstep(.25,.95,length((uv-.5)*vec2(aspect*.8,1.0)));
    fragColor=vec4(clamp(result,0.0,1.0),1);
}
void main() {
    // The volume pass only serves Fuga's impact phase.
    if(VolumePass>.5&&FugaPhase<1.5) {fragColor=vec4(0,0,0,1);return;}
    if(FugaPhase>=0.0) {fugaFire();return;}
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
