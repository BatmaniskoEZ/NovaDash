import socket, time, sys
HOST, PORT = "192.168.1.254", 23
cmd = sys.argv[1]
TMO = int(sys.argv[2]) if len(sys.argv) > 2 else 90

def neg(sock, data):
    out=bytearray(); resp=bytearray(); i=0
    while i < len(data):
        b=data[i]
        if b==255 and i+2<len(data):
            c,opt=data[i+1],data[i+2]
            if c==253: resp+=bytes([255,252,opt])
            elif c==251: resp+=bytes([255,254,opt])
            i+=3; continue
        if b==255 and i+2>=len(data): break
        out.append(b); i+=1
    if resp: sock.sendall(bytes(resp))
    return bytes(out)

def ru(sock, pats, t=90):
    sock.settimeout(0.5); acc=bytearray(); end=time.time()+t
    while time.time()<end:
        try:
            d=sock.recv(4096)
            if not d: break
        except socket.timeout: continue
        acc+=neg(sock,d)
        if any(p in bytes(acc) for p in pats): break
    return bytes(acc)

s=socket.create_connection((HOST,PORT),timeout=8)
ru(s,[b"login:"],8)
s.sendall(b"root\r\n")
r=ru(s,[b"assword",b"$ ",b"# "],6)
if b"assword" in r:
    s.sendall(b"\r\n"); ru(s,[b"$ ",b"# "],6)
# split marker so the command echo can't match the real output marker
s.sendall((cmd + '; echo "__C""AMEND__"\r\n').encode())
out=ru(s,[b"__CAMEND__"], TMO).decode("latin1")
s.close()
k=out.find("__CAMEND__")
body=out[:k] if k!=-1 else out
nl=body.find("\n")
print(body[nl+1:] if nl!=-1 else body)
