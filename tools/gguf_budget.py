#!/usr/bin/env python3
"""GGUF 헤더만 읽어 Ling-3.0-tiny의 메모리/대역폭 예산을 계산한다.

전체 파일(수 GB)을 받지 않고, 앞부분(헤더 + 텐서 정보)만 있으면 된다.
  curl -r 0-16000000 -o head.bin <gguf url>
  python3 tools/gguf_budget.py head.bin [--bw 60]

계산 항목
  - 텐서 그룹별 크기 (임베딩 / 출력 헤드 / 라우티드 전문가 / 나머지)
  - 디코드 1토큰당 읽는 가중치 바이트 (라우티드 전문가는 top-k/n_expert 비율만,
    token_embd는 get_rows로 1행만 읽으므로 제외)
  - 주어진 유효 대역폭(GB/s)에서의 디코드 속도 상한
  - MLA 레이턴트 캐시(토큰당) 및 KDA 재귀 상태(시퀀스당) 크기
"""
import argparse
import struct
import sys

# ggml type id -> (block_size, type_size)  (ggml.h / gguf-py GGML_QUANT_SIZES 기준)
GGML_TYPES = {
    0: ("F32", 1, 4), 1: ("F16", 1, 2), 2: ("Q4_0", 32, 18), 3: ("Q4_1", 32, 20),
    6: ("Q5_0", 32, 22), 7: ("Q5_1", 32, 24), 8: ("Q8_0", 32, 34), 9: ("Q8_1", 32, 36),
    10: ("Q2_K", 256, 84), 11: ("Q3_K", 256, 110), 12: ("Q4_K", 256, 144),
    13: ("Q5_K", 256, 176), 14: ("Q6_K", 256, 210), 15: ("Q8_K", 256, 292),
    16: ("IQ2_XXS", 256, 66), 17: ("IQ2_XS", 256, 74), 18: ("IQ3_XXS", 256, 98),
    19: ("IQ1_S", 256, 50), 20: ("IQ4_NL", 32, 18), 21: ("IQ3_S", 256, 110),
    22: ("IQ2_S", 256, 82), 23: ("IQ4_XS", 256, 136), 24: ("I8", 1, 1),
    25: ("I16", 1, 2), 26: ("I32", 1, 4), 27: ("I64", 1, 8), 28: ("F64", 1, 8),
    29: ("IQ1_M", 256, 56), 30: ("BF16", 1, 2), 39: ("MXFP4", 32, 17),
}

# gguf metadata value types
_SCALAR = {0: "<B", 1: "<b", 2: "<H", 3: "<h", 4: "<I", 5: "<i", 6: "<f", 7: "<?",
           10: "<Q", 11: "<q", 12: "<d"}


class Reader:
    def __init__(self, buf):
        self.b, self.o = buf, 0

    def take(self, fmt):
        v = struct.unpack_from(fmt, self.b, self.o)[0]
        self.o += struct.calcsize(fmt)
        return v

    def string(self):
        n = self.take("<Q")
        s = self.b[self.o:self.o + n].decode("utf-8", "replace")
        self.o += n
        return s

    def value(self, t):
        if t in _SCALAR:
            return self.take(_SCALAR[t])
        if t == 8:
            return self.string()
        if t == 9:
            at, n = self.take("<I"), self.take("<Q")
            return [self.value(at) for _ in range(n)]
        raise ValueError(f"unknown gguf value type {t}")


def parse(path):
    with open(path, "rb") as f:
        r = Reader(f.read())
    if r.b[:4] != b"GGUF":
        sys.exit("not a GGUF file")
    r.o = 4
    _version, n_tensors, n_kv = r.take("<I"), r.take("<Q"), r.take("<Q")
    kv = {}
    for _ in range(n_kv):
        k = r.string()
        kv[k] = r.value(r.take("<I"))
    tensors = []
    for _ in range(n_tensors):
        name = r.string()
        dims = [r.take("<Q") for _ in range(r.take("<I"))]
        ttype, _off = r.take("<I"), r.take("<Q")
        tensors.append((name, dims, ttype))
    return kv, tensors


def nbytes(dims, ttype):
    _, bs, ts = GGML_TYPES[ttype]
    n = 1
    for d in dims:
        n *= d
    return n // bs * ts


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("gguf")
    ap.add_argument("--bw", type=float, default=60.0, help="유효 메모리 대역폭 GB/s")
    a = ap.parse_args()

    kv, tensors = parse(a.gguf)
    arch = kv.get("general.architecture", "?")
    g = lambda k, d=None: kv.get(f"{arch}.{k}", d)
    n_expert, n_used = g("expert_count"), g("expert_used_count")

    groups = {"embd": 0, "output": 0, "experts": 0, "other": 0}
    types = {}
    for name, dims, t in tensors:
        sz = nbytes(dims, t)
        types.setdefault(GGML_TYPES[t][0], 0)
        types[GGML_TYPES[t][0]] += sz
        if name.startswith("token_embd"):
            groups["embd"] += sz
        elif name.startswith("output."):
            groups["output"] += sz
        elif "_exps" in name:
            groups["experts"] += sz
        else:
            groups["other"] += sz

    total = sum(groups.values())
    # 출력층이 임베딩과 묶인(tied) 모델은 lm_head 로 token_embd 전체를 매 토큰 읽는다
    lm_head = groups["output"] if groups["output"] > 0 else groups["embd"]
    active = lm_head + groups["other"] + groups["experts"] * n_used / n_expert
    GB = 1e9
    print(f"arch={arch} tensors={len(tensors)} experts={n_used}/{n_expert}")
    for k, v in groups.items():
        print(f"  {k:8s} {v / GB:7.3f} GB")
    print(f"  total    {total / GB:7.3f} GB")
    print("  by type: " + ", ".join(f"{k}={v / GB:.2f}GB" for k, v in sorted(types.items(), key=lambda x: -x[1])))
    print(f"decode bytes/token (weights) = {active / GB:.3f} GB")
    print(f"decode upper bound @ {a.bw:.0f} GB/s = {a.bw * GB / active:.1f} tok/s")

    # 캐시/상태 크기 (llama.cpp MLA는 kv_lora_rank + rope_dim 레이턴트를 K-only 캐시로 저장)
    n_layer = g("block_count")
    kv_lora = g("attention.kv_lora_rank")
    rope_dim = g("rope.dimension_count")
    head_kv = g("attention.head_count_kv")
    n_head = g("attention.head_count")
    if isinstance(head_kv, list):
        n_mla = sum(1 for h in head_kv if h > 0)
    else:
        n_mla = n_layer // 4
    n_kda = n_layer - n_mla
    if kv_lora and rope_dim:
        per_tok = n_mla * (kv_lora + rope_dim) * 2  # f16
        print(f"MLA layers={n_mla} KDA layers={n_kda}")
        print(f"MLA latent cache f16 = {per_tok} B/token -> "
              + ", ".join(f"{c // 1024}K:{per_tok * c / 2**20:.0f}MiB" for c in (8192, 32768, 131072)))
    if not arch.startswith("bailingmoe"):
        return  # 이하 KDA 재귀 상태 계산은 Ling(bailingmoe3) 전용
    hd = g("kda.head_dim") or 128
    d_conv = g("ssm.conv_kernel") or 4
    if isinstance(n_head, list):
        n_head = max(n_head)
    state = n_kda * (n_head * hd * hd * 4 + (d_conv - 1) * 3 * n_head * hd * 4)
    print(f"KDA recurrent state (f32, per sequence) = {state / 2**20:.1f} MiB")


if __name__ == "__main__":
    main()
