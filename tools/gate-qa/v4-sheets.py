import os, shutil, subprocess, sys
# usage: v3-sheets.py <screenshots dir gate-<run>> <out dir>
# Script v4 QA: contact sheets (build step 4, barrage step 3, ending step 3) and one full-size middle frame per shot.
src, out = sys.argv[1], sys.argv[2]
here = os.path.dirname(os.path.abspath(__file__))
os.makedirs(out, exist_ok=True)
sheet = os.path.join(here, 'sheet.py')
def near(prefix, t):
    # nearest captured frame (the first ticks of a cast may be missing: the client sees the cast a few ticks late)
    have = sorted(int(f[2:5]) for f in os.listdir(src) if f.startswith(prefix) and f[2:5].isdigit())
    return os.path.join(src, '%s%03d.png' % (prefix, min(have, key=lambda h: abs(h - t))))
def run(*a): subprocess.run([sys.executable, sheet, src, os.path.join(out, a[0]), *map(str, a[1:])], check=True)
run('build-0-480.png', 0, 480, 4, 'b-')
run('barrage.png', 0, 212, 3, 'c-')
run('ending.png', 0, 160, 3, 'e-')
build_mid = [7, 30, 50, 58, 77, 108, 142, 175, 225, 247, 277, 300, 320, 345, 380, 403, 430, 465, 478]
for n, t in enumerate(build_mid, 1): shutil.copy(near('b-', t), os.path.join(out, 'shot-%02d.png' % n))
for n, t in enumerate([7, 30, 70, 100, 110, 143, 187], 1): shutil.copy(near('c-', t), os.path.join(out, 'B%d.png' % n))
for n, t in enumerate([20, 60, 95, 120, 150], 1): shutil.copy(near('e-', t), os.path.join(out, 'E%d.png' % n))
print('done', out)
