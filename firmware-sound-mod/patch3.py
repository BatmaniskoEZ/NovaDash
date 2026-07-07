import struct, shutil, os, hashlib
base=os.path.dirname(os.path.abspath(__file__))
src=os.path.join(base,"cardv.bin")            # PRISTINE original
dst=os.path.join(base,"cardv.patched3")
pcm=open(os.path.join(base,"btn.raw"),"rb").read()
shutil.copyfile(src,dst)
d=bytearray(open(dst,"rb").read()); orig=len(d)
TBL=0x101a7c0; STR=20
def entry(i): return struct.unpack_from("<5I",d,TBL+i*STR)
# sacrifice sound 26 (unique, expendable "formatting reminder")
SAC=26
s=entry(SAC); foff=s[0]-0x10000
# verify uniqueness of this data ptr and that first len(pcm) bytes don't overlap a neighbor
ptrs=[entry(i)[0] for i in range(27)]
assert ptrs.count(s[0])==1, "sacrifice slot data is shared!"
nxt=min([p-0x10000 for p in ptrs if (p-0x10000)>foff], default=foff+s[1])
assert foff+len(pcm)<=nxt, "chime would overlap next sound"
# boot sound (id2/id20 shared) must NOT be touched
boot=entry(2)[0]-0x10000
assert not (foff <= boot < foff+len(pcm)), "would hit boot sound"
# 1) write chime into sound 26's region (in-pool, valid range)
d[foff:foff+len(pcm)] = pcm
# 2) button id0 -> here, chime length ; 3) sound26 -> here too (plays chime, harmless)
b=entry(0)
struct.pack_into("<5I",d,TBL+0*STR,   s[0], len(pcm), b[2], b[3], b[4])
struct.pack_into("<5I",d,TBL+SAC*STR, s[0], len(pcm), s[2], s[3], s[4])
open(dst,"wb").write(d)
print("size ok:", len(d)==orig)
print("chime at foff 0x%x vaddr 0x%x len %d"%(foff, s[0], len(pcm)))
print("boot(id2) ptr still:", hex(entry(2)[0]), "len", entry(2)[1], "(untouched)")
print("button id0:", [hex(x) for x in entry(0)])
print("md5:", hashlib.md5(bytes(d)).hexdigest())
