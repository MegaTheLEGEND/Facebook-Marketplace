import struct, sys, glob, os, re

def uleb(b, o):
    r = 0; s = 0
    while True:
        x = b[o]; o += 1
        r |= (x & 0x7f) << s
        if not x & 0x80: return r, o
        s += 7

def mutf8(b, o):
    e = b.index(0, o)
    return b[o:e].decode('utf-8', 'replace')

# instruction widths
W = [1]*256
def setw(lo, hi, w):
    for i in range(lo, hi+1): W[i] = w
setw(0x02,0x02,2); setw(0x03,0x03,3); setw(0x05,0x05,2); setw(0x06,0x06,3)
setw(0x08,0x08,2); setw(0x09,0x09,3); setw(0x13,0x13,2); setw(0x14,0x14,3)
setw(0x15,0x16,2); setw(0x17,0x17,3); setw(0x18,0x18,5); setw(0x19,0x19,2)
setw(0x1a,0x1a,2); setw(0x1b,0x1b,3); setw(0x1c,0x1c,2); setw(0x1f,0x20,2)
setw(0x22,0x23,2); setw(0x24,0x26,3); setw(0x29,0x29,2); setw(0x2a,0x2c,3)
setw(0x2d,0x3d,2); setw(0x44,0x6d,2); setw(0x6e,0x72,3); setw(0x74,0x78,3)
setw(0x90,0xaf,2); setw(0xd0,0xe2,2); setw(0xfa,0xfb,4); setw(0xfc,0xfd,3); setw(0xfe,0xff,2)

class Dex:
    def __init__(self, path):
        self.path = path
        b = self.b = open(path, 'rb').read()
        (self.ssz, self.soff, self.tsz, self.toff, self.psz, self.poff,
         self.fsz, self.foff, self.msz, self.moff, self.csz, self.coff) = struct.unpack_from('<12I', b, 56)
        self._strs = {}
    def string(self, i):
        if i in self._strs: return self._strs[i]
        off = struct.unpack_from('<I', self.b, self.soff + 4*i)[0]
        _, o = uleb(self.b, off)
        s = mutf8(self.b, o); self._strs[i] = s; return s
    def type(self, i):
        return self.string(struct.unpack_from('<I', self.b, self.toff + 4*i)[0])
    def proto(self, i):
        sh, rt, po = struct.unpack_from('<3I', self.b, self.poff + 12*i)
        params = []
        if po:
            n = struct.unpack_from('<I', self.b, po)[0]
            params = [self.type(struct.unpack_from('<H', self.b, po+4+2*k)[0]) for k in range(n)]
        return '(' + ''.join(params) + ')' + self.type(rt)
    def field(self, i):
        c, t, n = struct.unpack_from('<HHI', self.b, self.foff + 8*i)
        return self.type(c), self.string(n), self.type(t)
    def method(self, i):
        c, p, n = struct.unpack_from('<HHI', self.b, self.moff + 8*i)
        return self.type(c), self.string(n), self.proto(p)
    def classes(self):
        for k in range(self.csz):
            ci, af, sup, ifs, sf, ann, cd, sv = struct.unpack_from('<8I', self.b, self.coff + 32*k)
            yield k, self.type(ci), af, self.type(sup) if sup != 0xffffffff else None, ifs, cd
    def interfaces(self, off):
        if not off: return []
        n = struct.unpack_from('<I', self.b, off)[0]
        return [self.type(struct.unpack_from('<H', self.b, off+4+2*i)[0]) for i in range(n)]
    def class_data(self, cd):
        """returns (static_fields, inst_fields, direct, virtual); methods: (midx, access, code_off)"""
        if not cd: return [], [], [], []
        b = self.b; o = cd
        sfn, o = uleb(b, o); ifn, o = uleb(b, o); dmn, o = uleb(b, o); vmn, o = uleb(b, o)
        res = []
        for cnt, kind in ((sfn, 'f'), (ifn, 'f')):
            lst = []; idx = 0
            for _ in range(cnt):
                d, o = uleb(b, o); af, o = uleb(b, o); idx += d; lst.append((idx, af))
            res.append(lst)
        for cnt in (dmn, vmn):
            lst = []; idx = 0
            for _ in range(cnt):
                d, o = uleb(b, o); af, o = uleb(b, o); co, o = uleb(b, o)
                idx += d; lst.append((idx, af, co))
            res.append(lst)
        return res
    def insns(self, code_off):
        """yield (unit_offset, opcode, refs) for a code item"""
        b = self.b
        regs, ins, outs, tries, dbg, size = struct.unpack_from('<4HIi'[:0] or '<HHHHII', b, code_off)
        base = code_off + 16
        u = 0
        out = []
        while u < size:
            w0 = struct.unpack_from('<H', b, base + 2*u)[0]
            op = w0 & 0xff
            if op == 0 and w0 != 0:
                if w0 == 0x0100:
                    n = struct.unpack_from('<H', b, base+2*u+2)[0]; u += 4 + n*2
                elif w0 == 0x0200:
                    n = struct.unpack_from('<H', b, base+2*u+2)[0]; u += 2 + n*4
                else:
                    ew, n = struct.unpack_from('<HI', b, base+2*u+2); u += 4 + (n*ew + 1)//2
                continue
            idx = None; kind = None
            if op == 0x1a: idx = struct.unpack_from('<H', b, base+2*u+2)[0]; kind = 's'
            elif op == 0x1b: idx = struct.unpack_from('<I', b, base+2*u+2)[0]; kind = 's'
            elif op in (0x1c, 0x1f, 0x20, 0x22, 0x23): idx = struct.unpack_from('<H', b, base+2*u+2)[0]; kind = 't'
            elif 0x52 <= op <= 0x6d: idx = struct.unpack_from('<H', b, base+2*u+2)[0]; kind = 'f'
            elif 0x6e <= op <= 0x72 or 0x74 <= op <= 0x78: idx = struct.unpack_from('<H', b, base+2*u+2)[0]; kind = 'm'
            out.append((u, op, kind, idx, w0))
            u += W[op]
        return out

def load_all(d):
    return [Dex(p) for p in sorted(glob.glob(d + '/classes*.dex'), key=lambda x: (len(x), x))]


# ----------------------------------------------------------------------------------------------
# CLI
# ----------------------------------------------------------------------------------------------
def _fmt(d, kind, idx):
    if kind == 's': return repr(d.string(idx))
    if kind == 't': return d.type(idx)
    if kind == 'f': return '%s.%s:%s' % d.field(idx)
    if kind == 'm': return '%s.%s%s' % d.method(idx)
    return ''

def _index(dexes):
    cls = {}
    for di, d in enumerate(dexes):
        for k, name, af, sup, ifs, cd in d.classes():
            cls[name] = (di, sup, d.interfaces(ifs), cd)
    return cls

def _methods(d, cd):
    sf, inf, dm, vm = d.class_data(cd)
    return sf, inf, dm + vm

def main(argv):
    import argparse, pickle
    ap = argparse.ArgumentParser(description='Poke around the classes*.dex files of an unpacked APK.')
    ap.add_argument('dexdir', help='directory containing classes*.dex (unzip them from the APK)')
    sub = ap.add_subparsers(dest='cmd', required=True)
    p = sub.add_parser('strings', help='regex over all dex strings'); p.add_argument('regex')
    p = sub.add_parser('strref', help='methods that const-string the given exact strings'); p.add_argument('strs', nargs='+')
    p = sub.add_parser('callers', help='who calls Class.method'); p.add_argument('cls'); p.add_argument('method')
    p = sub.add_parser('class', help='fields + methods of a class'); p.add_argument('cls')
    p = sub.add_parser('dis', help='dump a class: per method register counts and referenced strings/types/fields/calls')
    p.add_argument('cls'); p.add_argument('methods', nargs='*')
    p = sub.add_parser('subclasses', help='classes extending / implementing a type'); p.add_argument('cls')
    a = ap.parse_args(argv)
    dexes = load_all(a.dexdir)

    if a.cmd == 'strings':
        import re
        pat = re.compile(a.regex)
        for di, d in enumerate(dexes):
            for i in range(d.ssz):
                s = d.string(i)
                if pat.search(s): print(di + 1, i, repr(s))
        return
    if a.cmd == 'strref':
        targets = set(a.strs)
        for di, d in enumerate(dexes):
            want = {i: d.string(i) for i in range(d.ssz) if d.string(i) in targets}
            if not want: continue
            for k, name, af, sup, ifs, cd in d.classes():
                for mi, acc, co in _methods(d, cd)[2]:
                    if not co: continue
                    for u, op, kind, idx, w0 in d.insns(co):
                        if kind == 's' and idx in want:
                            print(want[idx], '| dex', di + 1, name, *d.method(mi)[1:], '@%04x' % u)
        return
    if a.cmd == 'callers':
        for di, d in enumerate(dexes):
            ids = {i for i in range(d.msz) if d.method(i)[:2] == (a.cls, a.method)}
            if not ids: continue
            for k, name, af, sup, ifs, cd in d.classes():
                for mi, acc, co in _methods(d, cd)[2]:
                    if not co: continue
                    if any(kind == 'm' and idx in ids for u, op, kind, idx, w0 in d.insns(co)):
                        print('from', name, *d.method(mi)[1:], '| dex', di + 1)
        return
    cls = _index(dexes)
    if a.cmd == 'subclasses':
        for n, (di, sup, ifs, cd) in cls.items():
            if sup == a.cls or a.cls in ifs: print(n, 'extends', sup, ifs)
        return
    di, sup, ifs, cd = cls[a.cls]; d = dexes[di]
    sf, inf, ms = _methods(d, cd)
    print(a.cls, '| dex', di + 1, '| super', sup, '| implements', ifs)
    if a.cmd == 'class':
        for i, acc in sf + inf: print('  F', *d.field(i)[1:], hex(acc))
        for i, acc, co in ms: print('  M', *d.method(i)[1:], hex(acc))
        return
    if a.cmd == 'dis':
        import struct
        for i, acc, co in ms:
            m = d.method(i)
            if a.methods and m[1] not in a.methods: continue
            if not co: print('==', *m[1:], '(abstract/native)'); continue
            regs, ins, outs, tries, dbg, size = struct.unpack_from('<HHHHII', d.b, co)
            print('==', *m[1:], '| registers=%d ins=%d (p0=v%d) locals=%d tries=%d' % (regs, ins, regs - ins, regs - ins, tries))
            for u, op, kind, idx, w0 in d.insns(co):
                if kind: print('   %04x  op=%02x  %s' % (u, op, _fmt(d, kind, idx)))

if __name__ == '__main__':
    main(sys.argv[1:])
