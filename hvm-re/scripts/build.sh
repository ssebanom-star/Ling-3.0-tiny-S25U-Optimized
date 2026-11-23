#!/bin/bash
# work/apk_src -> out/HorizonVM-patched.apk (재빌드 + zipalign + 디버그키 서명)
set -e
source "$(dirname "$0")/env.sh"
cd "$HVM_RE"
rm -rf work/signed && mkdir -p work/signed out
$APKTOOL b -o work/unsigned.apk work/apk_src 2>&1 | grep -v "^Picked up"
# 추가 dex(드라이버 UI) 가 있으면 classes2.dex 로 합침
if [ -f driverui/out/classes2.dex ]; then
  python3 - <<'PY'
import zipfile,shutil
src,dst="work/unsigned.apk","work/unsigned_dex2.apk"
zi=zipfile.ZipFile(src); zo=zipfile.ZipFile(dst,"w")
for i in zi.infolist():
    zo.writestr(i,zi.read(i.filename),i.compress_type)
zo.write("driverui/out/classes2.dex","classes2.dex",zipfile.ZIP_DEFLATED); zo.close()
shutil.move(dst,src)
PY
fi
$SIGNER -a work/unsigned.apk --allowResign --out work/signed 2>&1 | grep -E "VERIFY|SUCCESS|ERROR|Warn" || true
cp work/signed/*.apk out/HorizonVM-patched.apk
ls -l out/HorizonVM-patched.apk
