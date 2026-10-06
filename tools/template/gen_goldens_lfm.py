#!/usr/bin/env python3
"""LFM2.5 (LiquidAI/LFM2.5-8B-A1B) chat_template.jinja 골든 생성.

HF apply_chat_template 환경(trim_blocks, lstrip_blocks, loopcontrols, tojson(ensure_ascii=False),
{% generation %} 블록)을 재현한다. 앱 테스트 LfmPromptBuilderTest 가 Kotlin 구현과 바이트 비교한다.

  python3 tools/template/gen_goldens_lfm.py > app/src/test/resources/lfm_template_goldens.json
"""
import json
import os
import sys

from jinja2 import nodes
from jinja2.ext import Extension, loopcontrols
from jinja2.sandbox import ImmutableSandboxedEnvironment


class GenerationTracker(Extension):
    """transformers AssistantTracker 와 같게 {% generation %} 본문을 그대로 출력"""
    tags = {"generation"}

    def parse(self, parser):
        lineno = next(parser.stream).lineno
        body = parser.parse_statements(["name:endgeneration"], drop_needle=True)
        return nodes.Scope(body, lineno=lineno)


def tojson(x, ensure_ascii=False, indent=None, separators=None, sort_keys=False):
    return json.dumps(x, ensure_ascii=ensure_ascii, indent=indent, separators=separators, sort_keys=sort_keys)


env = ImmutableSandboxedEnvironment(trim_blocks=True, lstrip_blocks=True, extensions=[loopcontrols, GenerationTracker])
env.filters["tojson"] = tojson
here = os.path.dirname(os.path.abspath(__file__))
tmpl = env.from_string(open(os.path.join(here, "lfm2_chat_template.jinja"), encoding="utf-8").read())

# LFM 문서 예시처럼 function 객체를 그대로 넘긴다(type/function 래퍼 없음)
WEATHER = {"name": "get_weather", "description": "현재 날씨 조회",
           "parameters": {"type": "object", "properties": {"city": {"type": "string"}, "days": {"type": "integer"}},
                          "required": ["city"]}}
CALC = {"name": "calc", "description": "Evaluate", "parameters": {"type": "object", "properties": {"expr": {"type": "string"}}}}


def call(name, **args):
    return {"type": "function", "function": {"name": name, "arguments": args}}


CASES = [
    ("single_user", dict(messages=[{"role": "user", "content": "안녕?"}])),
    ("system_user", dict(messages=[{"role": "system", "content": "You are helpful."}, {"role": "user", "content": "Q"}])),
    ("empty_system", dict(messages=[{"role": "system", "content": ""}, {"role": "user", "content": "Q"}])),
    ("multi_turn_preserve", dict(messages=[
        {"role": "system", "content": "S"},
        {"role": "user", "content": "1+1?"},
        {"role": "assistant", "content": "2", "reasoning_content": "\nadd\n"},
        {"role": "user", "content": "times 3?"},
    ], preserve_thinking=True)),
    ("multi_turn_drop_old_thinking", dict(messages=[
        {"role": "user", "content": "1+1?"},
        {"role": "assistant", "content": "2", "reasoning_content": "add"},
        {"role": "user", "content": "times 3?"},
        {"role": "assistant", "content": "6", "reasoning_content": "mul"},
    ], add_generation_prompt=False)),
    ("inline_think_dropped", dict(messages=[
        {"role": "user", "content": "a"},
        {"role": "assistant", "content": "<think>r1</think>\n\nans1"},
        {"role": "user", "content": "b"},
    ])),
    ("tools_basic", dict(messages=[{"role": "system", "content": "Sys"}, {"role": "user", "content": "서울 날씨?"}], tools=[WEATHER, CALC])),
    ("tools_no_system", dict(messages=[{"role": "user", "content": "x"}], tools=[CALC])),
    ("tool_call_roundtrip", dict(messages=[
        {"role": "user", "content": "서울 날씨?"},
        {"role": "assistant", "content": "", "reasoning_content": "need tool", "tool_calls": [call("get_weather", city="서울", days=2)]},
        {"role": "tool", "content": "{\"temp\": 21}"},
        {"role": "assistant", "content": "21도입니다.", "reasoning_content": "answer"},
        {"role": "user", "content": "thanks"},
    ], tools=[WEATHER], preserve_thinking=True)),
    ("tool_call_content_two_calls", dict(messages=[
        {"role": "user", "content": "q"},
        {"role": "assistant", "content": "Let me check.", "tool_calls": [
            call("calc", expr="1+2"), call("calc", expr="3*4", flag=True, obj={"a": [1, "b"]}, x=1.5)]},
        {"role": "tool", "content": "3"},
        {"role": "tool", "content": "12"},
    ], tools=[CALC])),
    ("no_generation_prompt", dict(messages=[{"role": "user", "content": "a"}, {"role": "assistant", "content": "b"}], add_generation_prompt=False)),
]

out = []
for name, kw in CASES:
    kw = dict(kw)
    agp = kw.pop("add_generation_prompt", True)
    rendered = tmpl.render(add_generation_prompt=agp, bos_token="<|startoftext|>", **kw)
    out.append({"name": name, "input": {**kw, "add_generation_prompt": agp}, "expected": rendered})

json.dump(out, sys.stdout, ensure_ascii=False, indent=1)
sys.stdout.write("\n")
