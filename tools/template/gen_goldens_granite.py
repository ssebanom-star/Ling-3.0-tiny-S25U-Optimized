#!/usr/bin/env python3
"""Granite 4.0 (ibm-granite/granite-4.0-h-tiny) chat_template.jinja 골든 생성.

HF apply_chat_template 환경(trim_blocks, lstrip_blocks, loopcontrols, tojson)을 재현.

  python3 tools/template/gen_goldens_granite.py > app/src/test/resources/granite_template_goldens.json
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
tmpl = env.from_string(open(os.path.join(here, "granite4_chat_template.jinja"), encoding="utf-8").read())

WEATHER = {"type": "function", "function": {"name": "get_weather", "description": "현재 날씨 조회",
           "parameters": {"type": "object", "properties": {"city": {"type": "string"}, "days": {"type": "integer"}},
                          "required": ["city"]}}}
CALC = {"type": "function", "function": {"name": "calc", "description": "Evaluate", "parameters": {"type": "object", "properties": {"expr": {"type": "string"}}, "required": []}}}


def call(name, **args):
    return {"type": "function", "function": {"name": name, "arguments": args}}


def A(content, **kw):
    return {"role": "assistant", "content": content, **kw}


CASES = [
    ("single_user_default_system", dict(messages=[{"role": "user", "content": "안녕?"}])),
    ("system_user", dict(messages=[{"role": "system", "content": "You are helpful."}, {"role": "user", "content": "Q"}])),
    ("multi_turn", dict(messages=[
        {"role": "system", "content": "S"},
        {"role": "user", "content": "1+1?"},
        A("2"),
        {"role": "user", "content": "times 3?"},
    ])),
    ("tools_basic", dict(messages=[{"role": "system", "content": "Sys"}, {"role": "user", "content": "서울 날씨?"}], tools=[WEATHER, CALC])),
    ("tools_no_system", dict(messages=[{"role": "user", "content": "x"}], tools=[CALC])),
    ("tool_call_roundtrip", dict(messages=[
        {"role": "user", "content": "서울 날씨?"},
        A("", tool_calls=[call("get_weather", city="서울", days=2)]),
        {"role": "tool", "content": "{\"temp\": 21}"},
        A("21도입니다."),
        {"role": "user", "content": "thanks"},
    ], tools=[WEATHER])),
    ("two_calls_two_results", dict(messages=[
        {"role": "user", "content": "q"},
        A("Let me check.", tool_calls=[call("calc", expr="1+2"), call("calc", expr="3*4", flag=True, obj={"a": [1, "b"]})]),
        {"role": "tool", "content": "3"},
        {"role": "tool", "content": "12"},
    ], tools=[CALC])),
    ("mid_system", dict(messages=[
        {"role": "user", "content": "a"}, A("b"), {"role": "system", "content": "late"}, {"role": "user", "content": "c"}])),
    ("no_generation_prompt", dict(messages=[{"role": "user", "content": "a"}, A("b")], add_generation_prompt=False)),
]

out = []
for name, kw in CASES:
    kw = dict(kw)
    agp = kw.pop("add_generation_prompt", True)
    rendered = tmpl.render(add_generation_prompt=agp, **kw)
    out.append({"name": name, "input": {**kw, "add_generation_prompt": agp}, "expected": rendered})

json.dump(out, sys.stdout, ensure_ascii=False, indent=1)
sys.stdout.write("\n")
