#!/usr/bin/env python3
"""Qwen3.6 (Qwen/Qwen3.6-35B-A3B) chat_template.jinja 골든 생성.

HF apply_chat_template 환경(trim_blocks, lstrip_blocks, loopcontrols, tojson(ensure_ascii=False))을 재현한다.
앱 테스트 QwenPromptBuilderTest 가 Kotlin 구현과 바이트 비교한다.

  python3 tools/template/gen_goldens_qwen.py > app/src/test/resources/qwen_template_goldens.json
"""
import json
import os
import sys

from jinja2.ext import loopcontrols
from jinja2.sandbox import ImmutableSandboxedEnvironment


def tojson(x, ensure_ascii=False, indent=None, separators=None, sort_keys=False):
    return json.dumps(x, ensure_ascii=ensure_ascii, indent=indent, separators=separators, sort_keys=sort_keys)


def raise_exception(msg):
    raise ValueError(msg)


env = ImmutableSandboxedEnvironment(trim_blocks=True, lstrip_blocks=True, extensions=[loopcontrols])
env.filters["tojson"] = tojson
env.globals["raise_exception"] = raise_exception
here = os.path.dirname(os.path.abspath(__file__))
tmpl = env.from_string(open(os.path.join(here, "qwen36_chat_template.jinja"), encoding="utf-8").read())

WEATHER = {"type": "function", "function": {"name": "get_weather", "description": "현재 날씨 조회",
           "parameters": {"type": "object", "properties": {"city": {"type": "string"}, "days": {"type": "integer"}},
                          "required": ["city"]}}}
CALC = {"type": "function", "function": {"name": "calc", "description": "Evaluate", "parameters": {"type": "object", "properties": {"expr": {"type": "string"}}}}}


def call(name, **args):
    return {"type": "function", "function": {"name": name, "arguments": args}}


CASES = [
    ("single_user_think", dict(messages=[{"role": "user", "content": "안녕?"}])),
    ("single_user_nothink", dict(messages=[{"role": "user", "content": "Hi"}], enable_thinking=False)),
    ("system_user_trim", dict(messages=[{"role": "system", "content": "  You are helpful.\n"}, {"role": "user", "content": " Q \n"}])),
    ("multi_turn_drop_old_thinking", dict(messages=[
        {"role": "system", "content": "S"},
        {"role": "user", "content": "1+1?"},
        {"role": "assistant", "content": "2", "reasoning_content": "\nadd\n"},
        {"role": "user", "content": "times 3?"},
    ])),
    ("multi_turn_preserve", dict(messages=[
        {"role": "user", "content": "1+1?"},
        {"role": "assistant", "content": "2", "reasoning_content": "add"},
        {"role": "user", "content": "times 3?"},
    ], preserve_thinking=True, enable_thinking=False)),
    ("inline_think_split", dict(messages=[
        {"role": "user", "content": "a"},
        {"role": "assistant", "content": "<think>\nr1\n</think>\n\nans1"},
        {"role": "user", "content": "b"},
        {"role": "assistant", "content": "<think>r2</think>ans2"},
    ], add_generation_prompt=False)),
    ("tools_basic", dict(messages=[{"role": "system", "content": "Sys"}, {"role": "user", "content": "서울 날씨?"}], tools=[WEATHER, CALC])),
    ("tools_no_system", dict(messages=[{"role": "user", "content": "x"}], tools=[CALC])),
    ("tool_call_roundtrip", dict(messages=[
        {"role": "user", "content": "서울 날씨?"},
        {"role": "assistant", "content": "", "reasoning_content": "need tool", "tool_calls": [call("get_weather", city="서울", days=2)]},
        {"role": "tool", "content": "{\"temp\": 21}"},
        {"role": "assistant", "content": "21도입니다.", "reasoning_content": "answer"},
        {"role": "user", "content": "thanks"},
    ], tools=[WEATHER])),
    ("multi_step_in_progress", dict(messages=[
        {"role": "user", "content": "q"},
        {"role": "assistant", "content": "Let me check.", "reasoning_content": "r", "tool_calls": [
            call("calc", expr="1+2"), call("calc", expr="3*4", flag=True, obj={"a": [1, "b"]}, x=1.5)]},
        {"role": "tool", "content": "3"},
        {"role": "tool", "content": "12"},
    ], tools=[CALC])),
    ("preserve_with_tools", dict(messages=[
        {"role": "system", "content": "S"},
        {"role": "user", "content": "q"},
        {"role": "assistant", "content": "", "reasoning_content": "r", "tool_calls": [call("calc", expr="2")]},
        {"role": "tool", "content": "2"},
        {"role": "assistant", "content": "two", "reasoning_content": "r2"},
        {"role": "user", "content": "q2"},
    ], tools=[CALC], preserve_thinking=True)),
]

out = []
for name, kw in CASES:
    kw = dict(kw)
    agp = kw.pop("add_generation_prompt", True)
    rendered = tmpl.render(add_generation_prompt=agp, **kw)
    out.append({"name": name, "input": {**kw, "add_generation_prompt": agp}, "expected": rendered})

json.dump(out, sys.stdout, ensure_ascii=False, indent=1)
sys.stdout.write("\n")
