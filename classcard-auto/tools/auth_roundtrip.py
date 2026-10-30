#!/usr/bin/env python3
"""Plays the phones in Python and checks the app's real CertificateVerifier (Java) accepts/rejects as it should.

usage: python3 tools/auth_roundtrip.py <issuer_private.pem> <android.jar> <kotlin-stdlib.jar>
"""
import base64, datetime, json, os, subprocess, sys, tempfile, uuid
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, padding, rsa

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
PEM, ANDROID, KSTD = sys.argv[1:4]
PKG = "net.classcardauto.mobile.nocert"
ISSUER_DEVICE, TARGET_DEVICE = "aaaa1111bbbb2222", "f881a45eedbd33af"
tmp = tempfile.mkdtemp()
OUT = os.path.join(tmp, "classes")
os.makedirs(OUT)
env = dict(os.environ, JAVA_TOOL_OPTIONS="")
subprocess.run(["javac", "-nowarn", "-d", OUT, "-cp", f"{ROOT}/tools/lib/json.jar:{KSTD}:{ANDROID}", "-sourcepath", f"{ROOT}/app/src/main/java",
                f"{ROOT}/solver/src/VerifyMain.java"], check=True, env=env, stderr=subprocess.DEVNULL)


def verify(text, device=TARGET_DEVICE, pkg=PKG):
    path = os.path.join(tmp, "case.txt")
    open(path, "w").write(text)
    r = subprocess.run(["java", "-cp", f"{OUT}:{ROOT}/tools/lib/json.jar:{KSTD}:{ANDROID}", "net.classcardauto.mobile.VerifyMain", path, device, pkg],
                       capture_output=True, text=True, env=env)
    return r.stdout.strip().splitlines()[-1] if r.stdout.strip() else r.stderr.strip()[-200:]


def issue(*args):
    subprocess.run([sys.executable, "-I", f"{ROOT}/issuer/issue.py", "--key", PEM, *args], check=True, capture_output=True, cwd=tmp)


def b64(b):
    return base64.b64encode(b).decode()


def now():
    return datetime.datetime.now().astimezone().replace(microsecond=0)


# ---- the issuer phone: EC key pair (the app keeps it in the Android keystore)
phone = ec.generate_private_key(ec.SECP256R1())
pub = b64(phone.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo))
request = json.dumps({"kind": "issuer-request", "issuerPub": pub, "device": ISSUER_DEVICE}, separators=(",", ":"))
issue("--issuer-request", request)
issuer_cert = open(os.path.join(tmp, "mimic-issuer.cert")).read()


def activation(device=TARGET_DEVICE, days=7, key=phone, cert=issuer_cert, pkg=PKG, tamper=False):
    payload = {"version": 1, "kind": "activation", "deviceId": device, "packageName": pkg, "issuedAt": now().isoformat(), "number": str(uuid.uuid4())}
    if days:
        payload["expiresAt"] = (now() + datetime.timedelta(days=days)).isoformat()
    body = json.dumps(payload, separators=(",", ":")).encode()
    sig = key.sign(body, ec.ECDSA(hashes.SHA256()))
    if tamper:
        body = body.replace(device.encode(), b"0000000000000000")
    outer = {"issuer": json.loads(cert), "payload": b64(body), "signature": b64(sig)}
    return "MC1." + base64.urlsafe_b64encode(json.dumps(outer, separators=(",", ":")).encode()).decode().rstrip("=")


results = []


def check(name, got, want_ok):
    ok = got.startswith("OK") if want_ok else got.startswith("REJECT")
    results.append(ok)
    print(("PASS " if ok else "FAIL ") + name + "  ->  " + got[:110])


check("valid activation code accepted", verify(activation()), True)
check("no expiry accepted", verify(activation(days=0)), True)
print("activation code length: %d characters" % len(activation()))
check("other phone rejected", verify(activation(), device="1234567890abcdef"), False)
check("other app rejected", verify(activation(), pkg="net.classcardauto.mobile.noauth"), False)
check("tampered payload rejected", verify(activation(tamper=True)), False)
other = ec.generate_private_key(ec.SECP256R1())
check("code signed by a key not in the issuer cert rejected", verify(activation(key=other)), False)
# expired code: build with an expiry in the past
payload = {"version": 1, "kind": "activation", "deviceId": TARGET_DEVICE, "packageName": PKG, "issuedAt": now().isoformat(), "number": str(uuid.uuid4()),
           "expiresAt": (now() - datetime.timedelta(days=1)).isoformat()}
body = json.dumps(payload, separators=(",", ":")).encode()
outer = {"issuer": json.loads(issuer_cert), "payload": b64(body), "signature": b64(phone.sign(body, ec.ECDSA(hashes.SHA256())))}
expired = "MC1." + base64.urlsafe_b64encode(json.dumps(outer, separators=(",", ":")).encode()).decode().rstrip("=")
check("expired activation code rejected", verify(expired), False)
# an issuer certificate signed by some other computer key
fake_root = rsa.generate_private_key(public_exponent=65537, key_size=3072)
fake_pem = os.path.join(tmp, "fake.pem")
open(fake_pem, "wb").write(fake_root.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
subprocess.run([sys.executable, "-I", f"{ROOT}/issuer/issue.py", "--key", fake_pem, "--issuer-request", request, "--out", "fake.cert"], check=True, capture_output=True, cwd=tmp)
check("issuer cert from an untrusted computer rejected", verify(activation(cert=open(os.path.join(tmp, "fake.cert")).read())), False)
# a classic key file for a device must NOT be accepted as an issuer cert, and vice versa
issue("--device", TARGET_DEVICE, "--out", "classic.key")
classic = open(os.path.join(tmp, "classic.key")).read()
check("classic computer key file still accepted", verify(classic), True)
check("an issuer certificate is not a device key", verify(issuer_cert), False)
check("garbage rejected", verify("MC1.xxxx"), False)
print("%d/%d passed" % (sum(results), len(results)))
sys.exit(0 if all(results) else 1)
