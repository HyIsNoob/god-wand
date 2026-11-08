import sys,os
from PIL import Image
d,out,a,b,step=sys.argv[1],sys.argv[2],int(sys.argv[3]),int(sys.argv[4]),int(sys.argv[5])
fs=[os.path.join(d,'f-%04d.png'%i) for i in range(a,b+1,step) if os.path.exists(os.path.join(d,'f-%04d.png'%i))]
ims=[Image.open(f).convert('RGB') for f in fs]; w,h=ims[0].size; tw=480; th=int(h*tw/w); cols=4
S=Image.new('RGB',(tw*cols,th*((len(ims)+cols-1)//cols)))
for i,im in enumerate(ims): S.paste(im.resize((tw,th)),((i%cols)*tw,(i//cols)*th))
S.save(out); print(out,len(ims))
