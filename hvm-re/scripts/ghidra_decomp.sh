#!/bin/bash
# 아직 내보내지 않은 함수(Global FUN_ 등)를 주소로 디컴파일:  ghidra_decomp.sh 00dd93ec [00xxxx ...]
source "$(dirname "$0")/env.sh"
cat > /tmp/_dec.py <<'P'
from ghidra.app.decompiler import DecompInterface
from ghidra.util.task import ConsoleTaskMonitor
import os
di=DecompInterface(); di.openProgram(currentProgram); fm=currentProgram.getFunctionManager()
for a in os.environ["ADDRS"].split():
    f=fm.getFunctionContaining(toAddr(a))
    if f:
        r=di.decompileFunction(f,120,ConsoleTaskMonitor())
        print("//// @%s %s::%s"%(f.getEntryPoint(),f.getParentNamespace().getName(True),f.getName()))
        print(r.getDecompiledFunction().getC() if r.decompileCompleted() else "// fail")
P
ADDRS="$*" $GHIDRA_HEADLESS "$HVM_RE/ghidra/proj" hvm -process libhvm_android.so -noanalysis -scriptPath /tmp -postScript _dec.py 2>&1 | sed -n '/^\/\/\/\/ @/,$p' | grep -v "^INFO\|^(HeadlessAnalyzer\|^Picked"
