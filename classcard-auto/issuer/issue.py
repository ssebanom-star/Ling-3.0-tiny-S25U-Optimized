#!/usr/bin/env python3
"""휴대폰에 넣을 인증 키 파일을 만든다.

  python issue.py --device 3f9a1c2b7d4e8a60
  python issue.py --device 3f9a-1c2b-7d4e-8a60 --days 14 --out classcard-auth.key

앱의 "인증이 필요해요" 화면에 나오는 기기 코드를 --device 에 넣습니다(공백·하이픈은 무시).
만들어진 파일을 USB 로 연결한 컴퓨터의 파일 관리자에서 휴대폰의
  내장 메모리/Android/data/net.classcardauto.mobile.nocert/files/auth/
폴더에 넣으면 앱이 읽고 확인한 뒤 지웁니다.

발급자 휴대폰 등록(처음 한 번, 이후 새 휴대폰은 컴퓨터 없이 인증 가능)
  python issue.py --issuer-request "앱의 발급자 도구에서 복사한 요청 텍스트" [--days N]
  → mimic-issuer.cert 가 만들어집니다. 그 파일의 내용을 발급자 휴대폰에 보내 앱의
    발급자 도구 2단계에 붙여넣으세요. (요청 텍스트는 따옴표 안에 그대로 넣거나 파일 경로를 적어도 됩니다.)

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


def issue_issuer(args):
    """휴대폰 한 대를 '발급자'로 승인하는 인증서를 만든다(컴퓨터 개인키로 서명)."""
    text = args.issuer_request
    if os.path.isfile(text):
        with open(text, encoding="utf-8") as f:
            text = f.read()
    try:
        request = json.loads(text)
        assert request.get("kind") == "issuer-request"
        issuer_pub = request["issuerPub"]
        device = re.sub(r"[\s\-]", "", request["device"]).lower()
        base64.b64decode(issuer_pub, validate=True)
    except Exception:
        sys.exit("요청 텍스트가 올바르지 않습니다. 앱의 발급자 도구에서 '요청 복사'로 복사한 내용 전체를 넣으세요.")
    if args.out == "classcard-auth.key":
        args.out = "mimic-issuer.cert"
    with open(args.config, encoding="utf-8") as f:
        config = json.load(f)
    with open(args.key, "rb") as f:
        key = serialization.load_pem_private_key(f.read(), password=None)
    now = datetime.datetime.now().astimezone().replace(microsecond=0)
    payload = {
        "version": 1,
        "kind": "issuer",
        "issuerPub": issuer_pub,
        "issuerDeviceId": device,
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
    print("발급자 휴대폰:", device, "| 번호:", payload["number"], "| 만료:", payload.get("expiresAt", "없음"))
    print("이 파일의 내용을 발급자 휴대폰으로 보내 앱 > 설정 > 발급자 도구 2단계에 붙여넣으세요.")


def main():
    ap = argparse.ArgumentParser(description="인증 키 파일 발급")
    ap.add_argument("--device", help="앱에 표시된 기기 코드")
    ap.add_argument("--issuer-request", help="앱 발급자 도구의 요청 텍스트(또는 그 내용이 든 파일 경로)")
    ap.add_argument("--days", type=int, default=0, help="만료까지 일수(0=만료 없음)")
    ap.add_argument("--package", default="net.classcardauto.mobile.nocert")
    ap.add_argument("--out", default="classcard-auth.key")
    ap.add_argument("--key", default=os.path.join(HERE, "issuer_private.pem"))
    ap.add_argument("--config", default=os.path.join(HERE, "issuer_config.json"))
    args = ap.parse_args()

    if args.issuer_request:
        return issue_issuer(args)
    if not args.device:
        sys.exit("--device 또는 --issuer-request 가 필요합니다.")
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
