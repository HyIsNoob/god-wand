import sys,os
from PIL import Image
# usage: sheet.py <dir> <out.png> <first> <last> <step> [prefix f-|b-]
d,out,a,b,step=sys.argv[1],sys.argv[2],int(sys.argv[3]),int(sys.argv[4]),int(sys.argv[5])
pre=sys.argv[6] if len(sys.argv)>6 else 'f-'
fs=[os.path.join(d,pre+'%04d.png'%i if pre=='f-' else pre+'%03d.png'%i) for i in range(a,b+1,step)]
fs=[f for f in fs if os.path.exists(f)]
ims=[Image.open(f).convert('RGB') for f in fs]; w,h=ims[0].size; tw=480; th=int(h*tw/w); cols=4
S=Image.new('RGB',(tw*cols,th*((len(ims)+cols-1)//cols)))
for i,im in enumerate(ims): S.paste(im.resize((tw,th)),((i%cols)*tw,(i//cols)*th))
S.save(out); print(out,len(ims))
