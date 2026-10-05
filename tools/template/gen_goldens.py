#!/usr/bin/env python3
"""Ling-3.0 chat_template.jinja 로 골든 출력을 생성한다.

HF transformers apply_chat_template 과 같은 Jinja 환경(trim_blocks, lstrip_blocks,
loopcontrols, tojson(ensure_ascii=False))을 재현한다. 출력은 앱 JVM 단위 테스트
(LingPromptBuilderTest)가 Kotlin 구현과 바이트 단위로 비교한다.

  python3 tools/template/gen_goldens.py > app/src/test/resources/template_goldens.json
"""
import json
import os
import sys

from jinja2.ext import loopcontrols
from jinja2.sandbox import ImmutableSandboxedEnvironment


def tojson(x, ensure_ascii=False, indent=None, separators=None, sort_keys=False):
    return json.dumps(x, ensure_ascii=ensure_ascii, indent=indent, separators=separators, sort_keys=sort_keys)


env = ImmutableSandboxedEnvironment(trim_blocks=True, lstrip_blocks=True, extensions=[loopcontrols])
env.filters["tojson"] = tojson
here = os.path.dirname(os.path.abspath(__file__))
tmpl = env.from_string(open(os.path.join(here, "chat_template.jinja"), encoding="utf-8").read())

WEATHER = {
    "type": "function",
    "function": {
        "name": "get_weather",
        "description": "현재 날씨 조회",
        "parameters": {
            "type": "object",
            "properties": {"city": {"type": "string"}, "days": {"type": "integer"}},
            "required": ["city"],
        },
    },
}
CALC = {"type": "function", "function": {"name": "calc", "description": "Evaluate", "parameters": {"type": "object", "properties": {"expr": {"type": "string"}}}}}

CASES = [
    ("single_user_think_default", dict(messages=[{"role": "user", "content": "안녕?"}])),
    ("single_user_think_off", dict(messages=[{"role": "user", "content": "Hi"}], enable_thinking=False)),
    ("system_think_on", dict(messages=[{"role": "system", "content": "You are helpful."}, {"role": "user", "content": "Q"}], enable_thinking=True)),
    ("system_with_flag", dict(messages=[{"role": "system", "content": "Be brief.\ndetailed thinking off"}, {"role": "user", "content": "Q"}])),
    ("multi_turn_reasoning", dict(messages=[
        {"role": "system", "content": "S"},
        {"role": "user", "content": "1+1?"},
        {"role": "assistant", "content": "2", "reasoning_content": "\nadd\n"},
        {"role": "user", "content": "times 3?"},
    ])),
    ("multi_turn_inline_think", dict(messages=[
        {"role": "user", "content": "a"},
        {"role": "assistant", "content": "<think>\nr1\n</think>\n\nans1"},
        {"role": "user", "content": "b"},
    ], enable_thinking=False)),
    ("assistant_no_reasoning", dict(messages=[
        {"role": "user", "content": "a"},
        {"role": "assistant", "content": "plain"},
        {"role": "user", "content": "b"},
    ])),
    ("mid_system", dict(messages=[
        {"role": "user", "content": "a"},
        {"role": "assistant", "content": "x"},
        {"role": "system", "content": "new rule"},
        {"role": "user", "content": "b"},
    ])),
    ("tools_basic", dict(messages=[{"role": "system", "content": "Sys"}, {"role": "user", "content": "서울 날씨?"}], tools=[WEATHER, CALC])),
    ("tools_no_system_off", dict(messages=[{"role": "user", "content": "x"}], tools=[CALC], enable_thinking=False)),
    ("tool_call_roundtrip", dict(messages=[
        {"role": "user", "content": "서울 날씨?"},
        {"role": "assistant", "content": "", "reasoning_content": "need tool", "tool_calls": [
            {"type": "function", "function": {"name": "get_weather", "arguments": {"city": "서울", "days": 2}}},
        ]},
        {"role": "tool", "content": "{\"temp\": 21}"},
        {"role": "user", "content": "thanks"},
    ], tools=[WEATHER])),
    ("tool_call_with_content_two_calls", dict(messages=[
        {"role": "user", "content": "q"},
        {"role": "assistant", "content": "Let me check.", "tool_calls": [
            {"type": "function", "function": {"name": "calc", "arguments": {"expr": "1+2"}}},
            {"type": "function", "function": {"name": "calc", "arguments": {"expr": "3*4", "obj": {"a": [1, "b"]}}}},
        ]},
        {"role": "tool", "content": "3"},
        {"role": "tool", "content": "12"},
    ], tools=[CALC])),
    ("no_generation_prompt", dict(messages=[{"role": "user", "content": "a"}, {"role": "assistant", "content": "b"}], add_generation_prompt=False)),
]

out = []
for name, kw in CASES:
    kw = dict(kw)
    agp = kw.pop("add_generation_prompt", True)
    rendered = tmpl.render(add_generation_prompt=agp, **kw)
    out.append({"name": name, "input": {**kw, "add_generation_prompt": agp}, "expected": rendered})

json.dump(out, sys.stdout, ensure_ascii=False, indent=1)
sys.stdout.write("\n")
