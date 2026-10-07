#!/usr/bin/env python3
"""K2-Horizon (IFM/K2-Horizon-3.7B) chat_template.jinja 골든 생성.

앱은 tool_presentation_format='json', tool_call_format='xml'(기본), reasoning_effort high/low 를 쓴다.
HF apply_chat_template 환경(trim_blocks, lstrip_blocks, loopcontrols, tojson, {% generation %})을 재현.

  python3 tools/template/gen_goldens_k2h.py > app/src/test/resources/k2h_template_goldens.json
"""
import json
import os
import sys

from jinja2 import nodes
from jinja2.ext import Extension, loopcontrols
from jinja2.sandbox import ImmutableSandboxedEnvironment


class GenerationTracker(Extension):
    tags = {"generation"}

    def parse(self, parser):
        lineno = next(parser.stream).lineno
        body = parser.parse_statements(["name:endgeneration"], drop_needle=True)
        return nodes.Scope(body, lineno=lineno)


def tojson(x, ensure_ascii=False, indent=None, separators=None, sort_keys=False):
    return json.dumps(x, ensure_ascii=ensure_ascii, indent=indent, separators=separators, sort_keys=sort_keys)


def raise_exception(msg):
    raise ValueError(msg)


env = ImmutableSandboxedEnvironment(trim_blocks=True, lstrip_blocks=True, extensions=[loopcontrols, GenerationTracker])
env.filters["tojson"] = tojson
env.globals["raise_exception"] = raise_exception
here = os.path.dirname(os.path.abspath(__file__))
tmpl = env.from_string(open(os.path.join(here, "k2h_chat_template.jinja"), encoding="utf-8").read())

WEATHER = {"type": "function", "function": {"name": "get_weather", "description": "현재 날씨 조회",
           "parameters": {"type": "object", "properties": {"city": {"type": "string"}, "days": {"type": "integer"}},
                          "required": ["city"]}}}
CALC = {"type": "function", "function": {"name": "calc", "description": "Evaluate", "parameters": {"type": "object", "properties": {"expr": {"type": "string"}}, "required": []}}}


def call(name, **args):
    return {"type": "function", "function": {"name": name, "arguments": args}}


def A(content, reasoning="", **kw):
    return {"role": "assistant", "content": content, "reasoning_content": reasoning, **kw}


CASES = [
    ("single_user_high", dict(messages=[{"role": "user", "content": "안녕?"}])),
    ("single_user_low", dict(messages=[{"role": "user", "content": "Hi"}], reasoning_effort="low")),
    ("system_user", dict(messages=[{"role": "system", "content": "You are helpful."}, {"role": "user", "content": "Q"}])),
    ("multi_turn", dict(messages=[
        {"role": "system", "content": "S"},
        {"role": "user", "content": "1+1?"},
        A("2", "\nadd\n"),
        {"role": "user", "content": "times 3?"},
    ])),
    ("empty_reasoning", dict(messages=[
        {"role": "user", "content": "a"}, A("b"), {"role": "user", "content": "c"}])),
    ("tools_basic", dict(messages=[{"role": "system", "content": "Sys"}, {"role": "user", "content": "서울 날씨?"}], tools=[WEATHER, CALC])),
    ("tools_no_system", dict(messages=[{"role": "user", "content": "x"}], tools=[CALC])),
    ("tool_call_roundtrip", dict(messages=[
        {"role": "user", "content": "서울 날씨?"},
        A("", "need tool", tool_calls=[call("get_weather", city="서울", days=2)]),
        {"role": "tool", "content": "{\"temp\": 21}"},
        A("21도입니다.", "answer"),
        {"role": "user", "content": "thanks"},
    ], tools=[WEATHER])),
    ("two_calls_two_results", dict(messages=[
        {"role": "user", "content": "q"},
        A("Let me check.", "r", tool_calls=[call("calc", expr="1+2"), call("calc", expr="3*4", flag=True, obj={"a": [1, "b"]})]),
        {"role": "tool", "content": "3"},
        {"role": "tool", "content": "12"},
    ], tools=[CALC], reasoning_effort="low")),
    ("no_generation_prompt", dict(messages=[{"role": "user", "content": "a"}, A("b", "t")], add_generation_prompt=False)),
]

out = []
for name, kw in CASES:
    kw = dict(kw)
    agp = kw.pop("add_generation_prompt", True)
    rendered = tmpl.render(add_generation_prompt=agp, bos_token="<|ifm|begin_of_text|>",
                           tool_presentation_format="json", **kw)
    out.append({"name": name, "input": {**kw, "add_generation_prompt": agp}, "expected": rendered})

json.dump(out, sys.stdout, ensure_ascii=False, indent=1)
sys.stdout.write("\n")
