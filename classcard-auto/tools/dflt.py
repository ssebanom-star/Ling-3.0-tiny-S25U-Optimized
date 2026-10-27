import re,glob,sys
def split_args(s,i):
    # s[i] == '(' ; return (args list, end index of ')')
    d=0;args=[];cur=i+1;k=i
    instr=None
    while True:
        c=s[k]
        if instr:
            if c=='\\': k+=2; continue
            if c==instr: instr=None
        elif c in '"\'': instr=c
        elif c in '([{': d+=1
        elif c in ')]}':
            d-=1
            if d==0:
                args.append(s[cur:k].strip()); return args,k
        elif c==',' and d==1:
            args.append(s[cur:k].strip()); cur=k+1
        k+=1
def strip_cast(a): return re.sub(r'^\((?:String|CharSequence|Object|Void)\)\s*','',a).strip()
def conv(owner,name,args):
    a=[strip_cast(x) for x in args]
    if (owner,name)==('FilesKt','writeText'): return f'FilesKt.writeText({a[0]}, {a[1]}, kotlin.text.Charsets.UTF_8)'
    if (owner,name)==('FilesKt','appendText'): return f'FilesKt.appendText({a[0]}, {a[1]}, kotlin.text.Charsets.UTF_8)'
    if (owner,name)==('FilesKt','readText'): return f'FilesKt.readText({a[0]}, kotlin.text.Charsets.UTF_8)'
    if (owner,name)==('StringsKt','contains'): return f'StringsKt.contains((CharSequence) {a[0]}, (CharSequence) {a[1]}, false)'
    if (owner,name)==('StringsKt','substringAfter'): return f'StringsKt.substringAfter({a[0]}, {a[1]}, {a[0]})'
    if (owner,name)==('Regex','find'): return f'{a[0]}.find({a[1]}, 0)'
    if (owner,name)==('ByteStreamsKt','copyTo'): return f'ByteStreamsKt.copyTo({a[0]}, {a[1]}, 8192)'
    if name=='joinToString':
        mask=int(a[-2]); parts=a[1:-2]
        defaults=['", "','""','""','-1','"..."','null']
        out=[]
        for idx in range(6):
            if mask & (1<<(idx+0)) and idx<len(parts) and False: pass
        # mask bit (idx+1) -> param idx (self is bit0? self not defaultable; separator is bit1)
        res=[]
        for idx in range(6):
            bit=(mask>>(idx+1))&1
            res.append(defaults[idx] if bit else (parts[idx] if idx<len(parts) else defaults[idx]))
        res=[x if not re.match(r'^\(CharSequence\)\s*null$',x) else 'null' for x in res]
        res[0]=f'(CharSequence) {res[0]}' if res[0] not in ('null',) else res[0]
        return f'{owner}.joinToString({a[0]}, (CharSequence) {strip_cast(res[0]) if res[0].startswith("(CharSequence)") else res[0]}, (CharSequence) {strip_cast(res[1])}, (CharSequence) {strip_cast(res[2])}, {res[3]}, (CharSequence) {strip_cast(res[4])}, {res[5]})'
    return None
def run(p):
    s=open(p).read(); o=s
    pat=re.compile(r'(\w+)\.(\w+)\$default\(')
    pos=0
    while True:
        m=pat.search(s,pos)
        if not m: break
        owner,name=m.group(1),m.group(2)
        if owner in ('FilesKt','StringsKt','Regex','ByteStreamsKt','CollectionsKt','ArraysKt') :
            args,end=split_args(s,m.end()-1)
            new=conv(owner,name,args)
            if new:
                s=s[:m.start()]+new+s[end+1:]; pos=m.start()+len(new); continue
        pos=m.end()
    if s!=o: open(p,'w').write(s); print('patched',p)
for p in glob.glob('*.java'): run(p)
