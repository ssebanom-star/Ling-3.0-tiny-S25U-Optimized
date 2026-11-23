#!/usr/bin/env python3
"""libhvm_android.so 바이너리 패치 도구. 주소는 기본적으로 **Ghidra 주소**(= ELF VA + 0x100000).
 raw ELF VA(readelf/objdump 주소)를 쓰려면 환경변수 RAW=1.
 사용:
  sopatch.py info  <so>                       섹션/세그먼트
  sopatch.py dis   <so> <va_hex> [count]      ARM64 디스어셈블
  sopatch.py asm   <so> <va_hex> "<asm; asm>" 어셈블 후 해당 VA에 기록(NOP 패딩 안 함, 길이 그대로)
  sopatch.py bytes <so> <va_hex> <hex>        원시 바이트 기록
  sopatch.py nop   <so> <va_hex> [count]      NOP(1f2003d5) count개
  sopatch.py ret0  <so> <va_hex>              함수 앞부분을 'mov w0,#0; ret'
  sopatch.py ret1  <so> <va_hex>              함수 앞부분을 'mov w0,#1; ret'
 모두 in-place 수정. 먼저 cp로 백업할 것.
"""
import sys,lief,capstone,keystone
BASE=0 if __import__('os').environ.get('RAW') else 0x100000
def va2off(b,va):
    for s in b.segments:
        if s.type==lief.ELF.Segment.TYPE.LOAD and s.virtual_address<=va<s.virtual_address+s.physical_size:
            return s.file_offset+(va-s.virtual_address)
    raise SystemExit("VA %#x not in file-backed LOAD segment"%va)
def main():
    cmd,so=sys.argv[1],sys.argv[2]
    b=lief.parse(so); data=bytearray(open(so,"rb").read())
    if cmd=="info":
        for s in b.segments: print(s.type,hex(s.virtual_address),hex(s.file_offset),hex(s.physical_size),s.flags)
        return
    gva=int(sys.argv[3],16); va=gva-BASE; off=va2off(b,va)
    def w(bs):
        data[off:off+len(bs)]=bs; open(so,"wb").write(data); print("patched ghidra %#x (+%d bytes) @file %#x: %s"%(gva,len(bs),off,bs.hex()))
    if cmd=="dis":
        n=int(sys.argv[4]) if len(sys.argv)>4 else 16
        md=capstone.Cs(capstone.CS_ARCH_ARM64,capstone.CS_MODE_ARM)
        for i in md.disasm(bytes(data[off:off+4*n]),va): print("%#x:\t%s\t%s"%(i.address+BASE,i.mnemonic,i.op_str))
    elif cmd=="asm":
        ks=keystone.Ks(keystone.KS_ARCH_ARM64,keystone.KS_MODE_LITTLE_ENDIAN)
        enc,_=ks.asm(sys.argv[4],va); w(bytes(enc))
    elif cmd=="bytes": w(bytes.fromhex(sys.argv[4]))
    elif cmd=="nop":
        n=int(sys.argv[4]) if len(sys.argv)>4 else 1; w(bytes.fromhex("1f2003d5")*n)
    elif cmd=="ret0": w(bytes.fromhex("00008052c0035fd6"))
    elif cmd=="ret1": w(bytes.fromhex("20008052c0035fd6"))
main()
