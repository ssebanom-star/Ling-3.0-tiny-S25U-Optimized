#!/usr/bin/env python3
"""Computer key files (method A) against the app's real CertificateVerifier (Java).

usage: python3 tools/auth_roundtrip.py <issuer_private.pem> <android.jar> <kotlin-stdlib.jar>
"""
import base64, json, os, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
PEM, ANDROID, KSTD = sys.argv[1:4]
PKG = "net.classcardauto.mobile.nocert"
DEVICE = "f881a45eedbd33af"
tmp = tempfile.mkdtemp()
OUT = os.path.join(tmp, "classes")
os.makedirs(OUT)
env = dict(os.environ, JAVA_TOOL_OPTIONS="")
CP = f"{ROOT}/tools/lib/json.jar:{KSTD}:{ANDROID}"
subprocess.run(["javac", "-nowarn", "-d", OUT, "-cp", CP, "-sourcepath", f"{ROOT}/app/src/main/java", f"{ROOT}/solver/src/VerifyMain.java"],
               check=True, env=env, stderr=subprocess.DEVNULL)


def verify(text, device=DEVICE, pkg=PKG):
    path = os.path.join(tmp, "case.txt")
    open(path, "w").write(text)
    r = subprocess.run(["java", "-cp", f"{OUT}:{CP}", "net.classcardauto.mobile.VerifyMain", path, device, pkg], capture_output=True, text=True, env=env)
    return r.stdout.strip().splitlines()[-1] if r.stdout.strip() else r.stderr.strip()[-200:]


def issue(*args):
    subprocess.run([sys.executable, "-I", f"{ROOT}/issuer/issue.py", "--key", PEM, *args], check=True, capture_output=True, cwd=tmp)


results = []


def check(name, got, want_ok):
    ok = got.startswith("OK") if want_ok else got.startswith("REJECT")
    results.append(ok)
    print(("PASS " if ok else "FAIL ") + name + "  ->  " + got[:110])


issue("--device", DEVICE, "--out", "a.key")
key = open(os.path.join(tmp, "a.key")).read()
check("key file for this phone accepted", verify(key), True)
issue("--device", DEVICE, "--days", "14", "--out", "b.key")
check("key file with an expiry date accepted", verify(open(os.path.join(tmp, "b.key")).read()), True)
check("other phone rejected", verify(key, device="1234567890abcdef"), False)
check("other app rejected", verify(key, pkg="net.classcardauto.mobile.noauth"), False)
env_json = json.loads(key)
payload = json.loads(base64.b64decode(env_json["payload"]))
payload["deviceId"] = "1234567890abcdef"
env_json["payload"] = base64.b64encode(json.dumps(payload, separators=(",", ":")).encode()).decode()
check("edited payload rejected", verify(json.dumps(env_json), device="1234567890abcdef"), False)
check("garbage rejected", verify("not a key"), False)
check("a method-C record is not a key file", verify("MCA1.xxxx"), False)
print("%d/%d passed" % (sum(results), len(results)))
sys.exit(0 if all(results) else 1)
