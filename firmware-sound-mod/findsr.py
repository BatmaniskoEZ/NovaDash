import struct, os
p=os.path.join(os.path.dirname(os.path.abspath(__file__)),'cardv.bin')
d=open(p,"rb").read()
RO_LO, RO_HI = 0x360440, 0x360440+0xcb3f4c
SR={8000,11025,12000,16000,22050,24000,32000,44100,48000}
def rop(v): return RO_LO<=v<RO_HI
def foff(v): return v-0x10000
# find any u32 in file that is a known sample rate, and check if preceded by ptr+len pattern
hits=[]
for i in range(0,len(d)-12,4):
    v0,v1,v2=struct.unpack_from("<III",d,i)
    if v2 in SR and rop(v0) and 64<=v1<=2_000_000:
        # plausible {ptr,len,sr}
        hits.append((i,v0,v1,v2))
print("candidate {ptr,len,sr} entries:",len(hits))
for i,v0,v1,v2 in hits[:40]:
    print(f"  @0x{i:06x}  ptr=0x{v0:08x}(foff 0x{foff(v0):06x}) len={v1:>8} sr={v2}")
