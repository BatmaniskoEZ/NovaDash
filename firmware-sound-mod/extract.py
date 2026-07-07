import struct, wave, os
p=os.path.join(os.path.dirname(os.path.abspath(__file__)),'cardv.bin')
d=open(p,"rb").read()
out=os.path.join(os.path.dirname(os.path.abspath(__file__)),'sounds_extracted')
os.makedirs(out, exist_ok=True)
start=0x101a7c0; STRIDE=20; n=27
for idx in range(n):
    off=start+idx*STRIDE
    v0,v1,v2,v3,v4=struct.unpack_from("<5I",d,off)
    foff=v0-0x10000
    pcm=d[foff:foff+v1]
    fn=os.path.join(out,f"sound_{idx:02d}.wav")
    w=wave.open(fn,"wb"); w.setnchannels(1); w.setsampwidth(2); w.setframerate(v2); w.writeframes(pcm); w.close()
    print(f"id={idx:2} -> sound_{idx:02d}.wav  ({v1} bytes, {v1/(v2*2):.2f}s)")
print("\nSaved to:", out)
