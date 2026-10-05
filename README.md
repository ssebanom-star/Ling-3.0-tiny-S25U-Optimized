# Ling-3.0-tiny · S25 Ultra Optimized

Galaxy S25 Ultra(Snapdragon 8 Elite for Galaxy)에서 [inclusionAI/Ling-3.0-tiny](https://huggingface.co/inclusionAI/Ling-3.0-tiny)
(7.9B 총 / 1.3B 활성, KDA+MLA 하이브리드 MoE)를 오프라인으로 실행하는 안드로이드 앱.

현재 단계: **사전조사 · 설계 완료, 구현 전**

- [docs/01-research.md](docs/01-research.md) — 모델·하드웨어·런타임 조사, 메모리/대역폭 예산, 리스크
- [docs/02-design.md](docs/02-design.md) — 아키텍처, 최적화 설계, 검증 계획, 단계별 로드맵
- [tools/gguf_budget.py](tools/gguf_budget.py) — GGUF 헤더만으로 quant별 메모리/디코드 상한 계산

```bash
curl -L -r 0-16000000 -o head.bin \
  https://huggingface.co/bartowski/Ling-3.0-tiny-GGUF/resolve/main/Ling-3.0-tiny-Q4_0.gguf
python3 tools/gguf_budget.py head.bin --bw 60
```
