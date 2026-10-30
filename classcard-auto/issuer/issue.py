#!/usr/bin/env python3
"""휴대폰에 넣을 인증 키 파일을 만든다.

  python issue.py --device 3f9a1c2b7d4e8a60
  python issue.py --device 3f9a-1c2b-7d4e-8a60 --days 14 --out classcard-auth.key

앱의 "인증이 필요해요" 화면에 나오는 기기 코드를 --device 에 넣습니다(공백·하이픈은 무시).
만들어진 파일을 USB 로 연결한 컴퓨터의 파일 관리자에서 휴대폰의
  내장 메모리/Android/data/net.classcardauto.mobile.nocert/files/auth/
폴더에 넣으면 앱이 읽고 확인한 뒤 지웁니다.

옵션
  --days N      N일 뒤에 만료(생략하면 만료 없음)
  --package P   대상 앱 패키지(기본 net.classcardauto.mobile.nocert)
  --key PATH    개인키 경로(기본 issuer_private.pem)
필요: pip install cryptography
"""
import argparse
import base64
import datetime
import json
import os
import platform
import re
import socket
import sys
import uuid

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

HERE = os.path.dirname(os.path.abspath(__file__))


def clean(text, limit):
    text = re.sub(r"[\x00-\x1f\x7f]", " ", text).strip()
    return (text or "unknown")[:limit]


def main():
    ap = argparse.ArgumentParser(description="인증 키 파일 발급")
    ap.add_argument("--device", required=True, help="앱에 표시된 기기 코드")
    ap.add_argument("--days", type=int, default=0, help="만료까지 일수(0=만료 없음)")
    ap.add_argument("--package", default="net.classcardauto.mobile.nocert")
    ap.add_argument("--out", default="classcard-auth.key")
    ap.add_argument("--key", default=os.path.join(HERE, "issuer_private.pem"))
    ap.add_argument("--config", default=os.path.join(HERE, "issuer_config.json"))
    args = ap.parse_args()

    device = re.sub(r"[\s\-]", "", args.device)
    if not re.fullmatch(r"[0-9A-Za-z]{8,64}", device):
        sys.exit("기기 코드 형식이 이상합니다. 앱에 표시된 코드를 그대로 입력하세요.")
    with open(args.config, encoding="utf-8") as f:
        config = json.load(f)
    with open(args.key, "rb") as f:
        key = serialization.load_pem_private_key(f.read(), password=None)

    now = datetime.datetime.now().astimezone().replace(microsecond=0)
    payload = {
        "version": 1,
        "deviceId": device.lower(),
        "packageName": args.package,
        "computerId": config["machine_id"],
        "issuedAt": now.isoformat(),
        "number": str(uuid.uuid4()),
        "computerName": clean(socket.gethostname(), 64),
        "computerModel": clean(platform.platform(), 100),
    }
    if args.days != 0:
        payload["expiresAt"] = (now + datetime.timedelta(days=args.days)).isoformat()
    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    signature = key.sign(body, padding.PKCS1v15(), hashes.SHA256())
    envelope = {
        "payload": base64.b64encode(body).decode("ascii"),
        "signature": base64.b64encode(signature).decode("ascii"),
    }
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(envelope, f, separators=(",", ":"))
    print("만들었습니다:", os.path.abspath(args.out))
    print("기기:", payload["deviceId"], "| 번호:", payload["number"], "| 만료:", payload.get("expiresAt", "없음"))


if __name__ == "__main__":
    main()
