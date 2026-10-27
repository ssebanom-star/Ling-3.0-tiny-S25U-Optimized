import re,sys
log=open(sys.argv[1]).read().split('\n')
seen=set();out=[]
for i,l in enumerate(log):
    m=re.match(r'.*/mobile/([^:]+):(\d+): error: (.*)',l)
    if m and (m.group(1),m.group(2),m.group(3)) not in seen:
        seen.add((m.group(1),m.group(2),m.group(3)))
        out.append(f'{m.group(1)}:{m.group(2)} {m.group(3)} || {log[i+1].strip()[:150]}')
print(len(out)); print('\n'.join(out))
