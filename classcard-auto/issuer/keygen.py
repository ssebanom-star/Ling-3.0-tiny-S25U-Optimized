#!/usr/bin/env python3
"""인증 발급용 키 한 쌍을 새로 만든다.

  python keygen.py

만들어지는 것
  issuer_private.pem  개인키. 컴퓨터에만 두세요. 휴대폰·앱·메신저에 올리지 마세요.
  issuer_config.json  발급 도구(issue.py)가 읽는 설정(공개키, 발급자 ID)
  PUBLIC_KEY / MACHINE_ID  앱에 넣어 다시 빌드할 값(화면에도 출력)

이미 issuer_private.pem 이 있으면 덮어쓰지 않습니다. 키를 바꾸면 이전에 발급한 인증 키 파일은 모두 무효가 됩니다.
필요: pip install cryptography
"""
import base64
import json
import os
import secrets
import sys

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

HERE = os.path.dirname(os.path.abspath(__file__))
PRIVATE = os.path.join(HERE, "issuer_private.pem")
CONFIG = os.path.join(HERE, "issuer_config.json")


def main():
    if os.path.exists(PRIVATE):
        print("issuer_private.pem 이 이미 있어 중단합니다. 새로 만들려면 먼저 직접 옮기거나 지워 주세요.")
        sys.exit(1)
    key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    pem = key.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption(),
    )
    with open(PRIVATE, "wb") as f:
        f.write(pem)
    try:
        os.chmod(PRIVATE, 0o600)
    except OSError:
        pass
    der = key.public_key().public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    public_key = base64.b64encode(der).decode("ascii")
    machine_id = secrets.token_hex(32)
    with open(CONFIG, "w", encoding="utf-8") as f:
        json.dump({"machine_id": machine_id, "public_key": public_key}, f, indent=2)
    print("만들었습니다.")
    print()
    print("MACHINE_ID =", machine_id)
    print("PUBLIC_KEY =", public_key)
    print()
    print("위 두 값을 앱의 CertificationIssuer 에 넣고 다시 빌드해야 이 키로 발급한 파일이 통과합니다.")


if __name__ == "__main__":
    main()
