import socket,struct,time,sys
from pathlib import Path
folder=Path(sys.argv[1]); props=dict(line.split('=',1) for line in (folder/'server.properties').read_text().splitlines() if '=' in line)
log=folder/'launch.log'; start=time.monotonic()
while time.monotonic()-start<240:
    text=log.read_text(errors='replace') if log.exists() else ''
    if 'Done (' in text:break
    if 'BUILD FAILED' in text or 'Exception in thread' in text:raise SystemExit('Dedicated boot failed; inspect launch.log')
    time.sleep(1)
else:raise SystemExit('Dedicated boot timed out; inspect launch.log')
def receive(s):
    def read(n):
        data=b''
        while len(data)<n:
            part=s.recv(n-len(data))
            if not part:raise RuntimeError('RCON connection closed')
            data+=part
        return data
    length=struct.unpack('<i',read(4))[0]
    data=read(length); return struct.unpack('<ii',data[:8]),data[8:-2].decode(errors='replace')
def send(s,rid,kind,message):
    data=struct.pack('<ii',rid,kind)+message.encode()+b'\x00\x00';s.sendall(struct.pack('<i',len(data))+data)
with socket.create_connection(('127.0.0.1',int(props['rcon.port'])),timeout=10) as s:
    send(s,1,3,props['rcon.password'])
    while True:
        (rid,kind),body=receive(s)
        if rid==-1:raise RuntimeError('RCON auth failed')
        if kind==2:break
    send(s,2,2,'list'); result=receive(s)[1];print('DEDICATED_QA READY:',result,flush=True)
    time.sleep(5)
    send(s,3,2,'stop');print('DEDICATED_QA STOP:',receive(s)[1],flush=True)
