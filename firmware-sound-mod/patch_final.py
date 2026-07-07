import struct, shutil, os, hashlib
base=os.path.dirname(os.path.abspath(__file__))
src=os.path.join(base,"cardv.bin")            # PRISTINE
dst=os.path.join(base,"cardv.final")
btn=open(os.path.join(base,"btn.raw"),"rb").read()
song=open(os.path.join(base,"boot.raw"),"rb").read()   # music clip for id23
emg=open(os.path.join(base,"emerg.raw"),"rb").read()
shutil.copyfile(src,dst)
d=bytearray(open(dst,"rb").read()); orig=len(d)
TBL=0x101a7c0; STR=20
def E(i): return struct.unpack_from("<5I",d,TBL+i*STR)
def setE(i,ptr,ln):
    o=E(i); struct.pack_into("<5I",d,TBL+i*STR, ptr, ln, o[2], o[3], o[4])
ptrs=[E(i)[0] for i in range(27)]
def foff(v): return v-0x10000
# (host slot id, data, entry-ids to point there)
jobs=[
  (26, btn,  [0, 26]),    # button click
  (12, emg,  [12]),       # emergency video (in-place own slot)
  (14, song, [23, 14]),   # id23 ("recording started") = music clip; id14 host. id2/id20 untouched.
]
writes=[]
for sac,data,ids in jobs:
    e=E(sac); fo=foff(e[0])
    assert ptrs.count(e[0])==1, f"slot {sac} shared"
    nxt=min([foff(p) for p in ptrs if foff(p)>fo], default=fo+e[1])
    assert fo+len(data)<=nxt, f"slot {sac} overlaps next"
    writes.append((fo,fo+len(data))); d[fo:fo+len(data)]=data
    for i in ids: setE(i, e[0], len(data))
# original boot data (id2/id20 -> 0xebe4c8) must stay intact
bo=foff(0xebe4c8)
for a,b in writes: assert not(a<=bo<b), "hit boot data"
open(dst,"wb").write(d)
print("size ok:", len(d)==orig)
for lbl,i in [("button",0),("emerg",12),("recStart",23),("boot2",2),("boot20",20),("sac14",14),("sac26",26)]:
    print(f"  {lbl:9} id{i:2}: {[hex(x) for x in E(i)]}")
print("md5:", hashlib.md5(bytes(d)).hexdigest())
