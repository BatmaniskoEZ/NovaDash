import struct, shutil, os, hashlib
base=os.path.dirname(os.path.abspath(__file__))
src=os.path.join(base,"cardv.bin")            # PRISTINE
dst=os.path.join(base,"cardv.patched_all")
btn=open(os.path.join(base,"btn.raw"),"rb").read()
boot=open(os.path.join(base,"boot.raw"),"rb").read()
emg=open(os.path.join(base,"emerg.raw"),"rb").read()
shutil.copyfile(src,dst)
d=bytearray(open(dst,"rb").read()); orig=len(d)
TBL=0x101a7c0; STR=20
def E(i): return struct.unpack_from("<5I",d,TBL+i*STR)
def setE(i,ptr,ln):
    o=E(i); struct.pack_into("<5I",d,TBL+i*STR, ptr, ln, o[2], o[3], o[4])
ptrs=[E(i)[0] for i in range(27)]
POOL_LO,POOL_HI=0xe3f8e0,0xf6cd20+80000
def foff(v): return v-0x10000

# targets: (sacrifice slot id, data bytes, list of entry-ids to point here)
jobs=[
  (26, btn,  [0, 26]),   # button click
  (14, boot, [2, 20, 14]),# boot (id2==id20 share; id14 sacrificed)
  (12, emg,  [12]),       # emergency (in-place, its own slot)
]
writes=[]
for sac, data, ids in jobs:
    e=E(sac); fo=foff(e[0])
    assert ptrs.count(e[0])==1, f"slot {sac} data shared!"
    nxt=min([foff(p) for p in ptrs if foff(p)>fo], default=fo+e[1])
    assert fo+len(data)<=nxt, f"slot {sac} write overlaps next sound"
    writes.append((fo,fo+len(data)))
    d[fo:fo+len(data)]=data
    for i in ids: setE(i, e[0], len(data))
# no write region may hit the shared boot data 0xebe4c8 (we relocated it, keep original intact)
boot_orig=foff(0xebe4c8)
for a,b in writes: assert not (a<=boot_orig<b), "clobbered original boot data"
# writes disjoint
ws=sorted(writes)
for (a,b),(c,e2) in zip(ws,ws[1:]): assert b<=c, "write regions overlap"
open(dst,"wb").write(d)
print("size ok:", len(d)==orig)
for lbl,i in [("button",0),("boot",2),("boot20",20),("emerg",12),("sac14",14),("sac26",26)]:
    print(f"  {lbl:7} id{i:2}: {[hex(x) for x in E(i)]}")
print("md5:", hashlib.md5(bytes(d)).hexdigest())
