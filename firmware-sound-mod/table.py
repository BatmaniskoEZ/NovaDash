import struct, os
p=os.path.join(os.path.dirname(os.path.abspath(__file__)),'cardv.bin')
d=open(p,"rb").read()
RO_LO, RO_HI = 0x360440, 0x360440+0xcb3f4c
def rop(v): return RO_LO<=v<RO_HI
STRIDE=20
def valid(off):
    if off<0 or off+STRIDE>len(d): return False
    v0,v1,v2,v3,v4=struct.unpack_from("<5I",d,off)
    return rop(v0) and 32<=v1<=3_000_000
# find start: walk backward from a known-good entry
start=0x101a7c0
while valid(start-STRIDE): start-=STRIDE
# find end
end=start
while valid(end): end+=STRIDE
n=(end-start)//STRIDE
print(f"table start file=0x{start:06x} vaddr=0x{start+0x10000:06x}  entries={n}")
for idx in range(n):
    off=start+idx*STRIDE
    v0,v1,v2,v3,v4=struct.unpack_from("<5I",d,off)
    dur=v1/(v2*2) if v2 else 0
    print(f"  id={idx:3} foff=0x{v0-0x10000:06x} len={v1:>7} sr={v2:>6} dur={dur:4.2f}s w3=0x{v3:x} w4=0x{v4:x}")
