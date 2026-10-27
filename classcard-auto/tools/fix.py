import re,sys
D='/tmp/claude-0/-home-user-Ling-3-0-tiny-S25U-Optimized/a7ad8156-55da-51d6-85e4-4c4a88ff8079/scratchpad/classcard-src/app/src/main/java/net/classcardauto/mobile/'
def span(src, sig):
    i=src.index(sig)
    # include preceding javadoc-ish comment blocks belonging to the method
    j=src.index('{',src.index(')',i))
    d=0;k=j
    while True:
        c=src[k]
        if c=='{': d+=1
        elif c=='}':
            d-=1
            if d==0: break
        k+=1
    # start: back up over preceding comment/annotation lines
    s=src.rfind('\n',0,i)+1
    while True:
        p=src.rfind('\n',0,s-1)+1
        line=src[p:s].strip()
        if line.startswith('/*') or line.startswith('*') or line.startswith('@') or line.endswith('*/') or line=='' and False:
            s=p
        else: break
    return s,k+1
def replace(path, sig, new):
    src=open(path).read()
    s,e=span(src,sig)
    open(path,'w').write(src[:s]+new+src[e:])
