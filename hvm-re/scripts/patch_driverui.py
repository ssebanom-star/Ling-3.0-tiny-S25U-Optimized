#!/usr/bin/env python3
"""MainActivity.smali 에 DriverManager 호출 3곳 삽입 (idempotent). 대상: work/apk_src/smali/io/horizonvm/app/MainActivity.smali"""
import re,sys,os
p=os.path.join(os.path.dirname(__file__),"..","work/apk_src/smali/io/horizonvm/app/MainActivity.smali")
s=open(p).read()
if "DriverManager" in s: print("이미 패치됨"); sys.exit(0)
DM="Lio/horizonvm/app/DriverManager;"
# 1) onCreate: buildSetup() 직후 attach
a="    invoke-direct {p0}, Lio/horizonvm/app/MainActivity;->buildSetup()V\n"
assert s.count(a)==1
s=s.replace(a,a+"\n    invoke-static {p0}, %s->attach(Landroid/app/Activity;)V\n"%DM)
# 2) onActivityResult: 시작부에서 위임
m=re.search(r"\.method protected onActivityResult\(IILandroid/content/Intent;\)V\n    \.locals (\d+)\n",s); assert m and int(m.group(1))>=1
ins=("    invoke-static {p0, p1, p2, p3}, %s->onResult(Landroid/app/Activity;IILandroid/content/Intent;)Z\n"
     "    move-result v0\n    if-eqz v0, :hvm_drv_cont\n    return-void\n    :hvm_drv_cont\n")%DM
s=s[:m.end()]+"\n"+ins+s[m.end():]
# 3) launchVm: applyEnv(...) 직후
b=re.search(r"    invoke-direct \{p0, v\d+, v\d+\}, Lio/horizonvm/app/MainActivity;->applyEnv\(Ljava/lang/String;Z\)Ljava/lang/String;\n",s); assert b
s=s[:b.end()]+"\n    invoke-static {p0}, %s->afterEnv(Landroid/app/Activity;)V\n"%DM+s[b.end():]
open(p,"w").write(s); print("patched 3 sites")
