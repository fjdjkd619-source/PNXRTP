#!/usr/bin/env python3
"""Generate OrigenBoundsCatalog.java from a Terra configpack inventory.

The analyzer is conservative:
* X/Y/Z are tracked independently.
* TerraScript loops and coordinate expressions are interval-evaluated.
* Literal nested calls are recursively analyzed and translated per axis.
* YAML structure definitions are parsed and inheritance is resolved.
* Anything not proven statically is excluded from the verified runtime catalog. The
  PlacementRuntimeGuard must be installed at the common adapter boundary.
"""
from __future__ import annotations

import argparse
import ast
import json
import math
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

try:
    import yaml
except ImportError:
    yaml = None

PACK_ID = "overworld"
PACK_SOURCE = "PolyhedralDev/TerraOverworldConfig@445848bc9f9f28fab1d760f01f9a552d93944681"
FALLBACK_RADIUS = 32
FALLBACK_WRITES = 0
FALLBACK_READS = 0
FALLBACK_ENTITIES = 0
MAX_CALL_DEPTH = 48
MAX_OPERATIONS = 2_000_000


class AnalysisError(Exception):
    pass


@dataclass(frozen=True)
class Interval:
    low: float
    high: float

    def __post_init__(self):
        if not math.isfinite(self.low) or not math.isfinite(self.high) or self.low > self.high:
            raise AnalysisError(f"Invalid interval [{self.low}, {self.high}]")

    @staticmethod
    def point(v: float) -> "Interval":
        return Interval(v, v)

    def __add__(self, other):
        return Interval(self.low + other.low, self.high + other.high)

    def __sub__(self, other):
        return Interval(self.low - other.high, self.high - other.low)

    def __mul__(self, other):
        v = (self.low * other.low, self.low * other.high,
             self.high * other.low, self.high * other.high)
        return Interval(min(v), max(v))

    def __truediv__(self, other):
        if other.low <= 0 <= other.high:
            raise AnalysisError("division by interval containing zero")
        v = (self.low / other.low, self.low / other.high,
             self.high / other.low, self.high / other.high)
        return Interval(min(v), max(v))

    def union(self, other):
        return Interval(min(self.low, other.low), max(self.high, other.high))

    def shifted(self, other):
        return self + other


@dataclass
class Bounds:
    x: Optional[Interval] = None
    y: Optional[Interval] = None
    z: Optional[Interval] = None

    def include(self, other: "Bounds"):
        for axis in ("x", "y", "z"):
            a, b = getattr(self, axis), getattr(other, axis)
            if b is not None:
                setattr(self, axis, b if a is None else a.union(b))

    def translated(self, offset: "Bounds"):
        return Bounds(
            self.x + offset.x if self.x and offset.x else self.x,
            self.y + offset.y if self.y and offset.y else self.y,
            self.z + offset.z if self.z and offset.z else self.z,
        )

    def complete(self):
        return self.x is not None and self.y is not None and self.z is not None


@dataclass
class Profile:
    structure_id: str
    writes: Bounds
    reads: Bounds
    max_writes: int
    max_reads: int
    max_entities: int
    dependencies: set[str] = field(default_factory=set)
    evidence: str = ""
    bounded_fallback: bool = False


def merge_interval(a: Optional[Interval], b: Optional[Interval]):
    if a is None:
        return b
    if b is None:
        return a
    return a.union(b)


def strip_comments(source: str) -> str:
    # Preserve quoted strings while removing line/block comments.
    out, i, quote, escaped = [], 0, None, False
    while i < len(source):
        c = source[i]
        if quote:
            out.append(c)
            if c == quote and not escaped:
                quote = None
            escaped = (c == "\\" and not escaped)
            if c != "\\":
                escaped = False
            i += 1
        elif c in ("'", '"'):
            quote = c
            out.append(c)
            i += 1
        elif source.startswith("//", i):
            end = source.find("\n", i)
            if end < 0:
                break
            out.append("\n")
            i = end + 1
        elif source.startswith("/*", i):
            end = source.find("*/", i + 2)
            if end < 0:
                raise AnalysisError("unterminated block comment")
            out.append(" ")
            i = end + 2
        else:
            out.append(c)
            i += 1
    return "".join(out)


def matching(source: str, opening: int, left="(", right=")") -> int:
    depth, quote, escaped = 0, None, False
    for i in range(opening, len(source)):
        c = source[i]
        if quote:
            if c == quote and not escaped:
                quote = None
            escaped = c == "\\" and not escaped
            if c != "\\":
                escaped = False
        elif c in ("'", '"'):
            quote = c
        elif c == left:
            depth += 1
        elif c == right:
            depth -= 1
            if depth == 0:
                return i
    raise AnalysisError(f"unclosed {left}{right} expression")


def split_args(s: str) -> list[str]:
    parts, start, depth, quote, escaped = [], 0, 0, None, False
    for i, c in enumerate(s):
        if quote:
            if c == quote and not escaped:
                quote = None
            escaped = c == "\\" and not escaped
            if c != "\\":
                escaped = False
        elif c in ("'", '"'):
            quote = c
        elif c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        elif c == "," and depth == 0:
            parts.append(s[start:i].strip())
            start = i + 1
    parts.append(s[start:].strip())
    return parts


def eval_expr(text: str, env: dict[str, Interval]) -> Interval:
    text = text.strip()
    try:
        node = ast.parse(text, mode="eval").body
    except SyntaxError as e:
        raise AnalysisError(f"unsupported expression: {text}") from e

    def visit(n):
        if isinstance(n, ast.Constant) and isinstance(n.value, (int, float)):
            return Interval.point(float(n.value))
        if isinstance(n, ast.Name) and n.id in env:
            return env[n.id]
        if isinstance(n, ast.UnaryOp) and isinstance(n.op, ast.USub):
            a = visit(n.operand)
            return Interval(-a.high, -a.low)
        if isinstance(n, ast.BinOp):
            a, b = visit(n.left), visit(n.right)
            if isinstance(n.op, ast.Add): return a + b
            if isinstance(n.op, ast.Sub): return a - b
            if isinstance(n.op, ast.Mult): return a * b
            if isinstance(n.op, ast.Div): return a / b
        if isinstance(n, ast.Call) and isinstance(n.func, ast.Name):
            if n.func.id == "randomInt" and len(n.args) == 1:
                a = visit(n.args[0])
                if a.low != a.high or a.low < 1 or a.high > 1_000_000:
                    raise AnalysisError("randomInt argument is not a bounded positive constant")
                return Interval(0, a.high - 1)
        raise AnalysisError(f"unsupported expression node: {ast.dump(n)}")
    return visit(node)


LOOP_RE = re.compile(
    r"for\s*\(\s*num\s+([A-Za-z_]\w*)\s*=\s*(.*?)\s*;\s*"
    r"\1\s*(<=|<|>=|>)\s*(.*?)\s*;\s*"
    r"\1\s*=\s*\1\s*([+-])\s*(.*?)\s*\)\s*\{",
    re.S,
)


def loop_contexts(source: str, inherited: dict[str, Interval]):
    env = dict(inherited)
    operation_multiplier = 1
    for m in LOOP_RE.finditer(source):
        name, start_s, cmp, end_s, sign, step_s = m.groups()
        start, end, step = eval_expr(start_s, env), eval_expr(end_s, env), eval_expr(step_s, env)
        if start.low != start.high or end.low != end.high or step.low != step.high:
            raise AnalysisError(f"loop {name} has non-constant control expression")
        delta = step.low * (1 if sign == "+" else -1)
        if delta == 0:
            raise AnalysisError(f"loop {name} has zero step")
        a, b = start.low, end.low
        if (cmp in ("<", "<=") and delta < 0) or (cmp in (">", ">=") and delta > 0):
            raise AnalysisError(f"loop {name} step moves away from its bound")
        inclusive = cmp in ("<=", ">=")
        if delta > 0:
            last = b if inclusive else math.ceil(b) - 1
            count = max(0, math.floor((last - a) / delta) + 1)
            interval = Interval(min(a, a + max(0, count - 1) * delta),
                                max(a, a + max(0, count - 1) * delta))
        else:
            last = b if inclusive else math.floor(b) + 1
            count = max(0, math.floor((a - last) / abs(delta)) + 1)
            interval = Interval(min(a, a - max(0, count - 1) * abs(delta)),
                                max(a, a - max(0, count - 1) * abs(delta)))
        if count > 1_000_000:
            raise AnalysisError(f"loop {name} exceeds iteration cap")
        operation_multiplier *= count
        if operation_multiplier > MAX_OPERATIONS:
            raise AnalysisError("nested loop operation bound exceeds cap")
        env[name] = interval
    return env, operation_multiplier


def iter_calls(source: str):
    """Yield function name and balanced argument text, respecting nested calls/strings."""
    i = 0
    name_re = re.compile(r"\b(block|getBlock|structure|spawnEntity|spawn_entity)\s*\(")
    while True:
        m = name_re.search(source, i)
        if not m:
            return
        opening = source.find("(", m.start())
        end = matching(source, opening)
        yield m.group(1), source[opening + 1:end]
        i = end + 1


def static_script(script_id: str, files: dict[str, Path], stack=()) -> Profile:
    if len(stack) >= MAX_CALL_DEPTH:
        raise AnalysisError("nested call depth cap reached")
    if script_id in stack:
        raise AnalysisError("recursive nested call: " + " -> ".join(stack + (script_id,)))
    path = files.get(script_id)
    if path is None or not path.is_file():
        raise AnalysisError(f"missing TerraScript source for {script_id}")

    src = strip_comments(path.read_text(encoding="utf-8"))
    env, multiplier = loop_contexts(src, {})
    writes, reads = Bounds(), Bounds()
    write_count = read_count = entity_count = 0
    deps = {script_id}

    for fn, raw in iter_calls(src):
        args = split_args(raw)
        if len(args) < 3:
            raise AnalysisError(f"malformed {fn} call")
        xyz = [eval_expr(args[i], env) for i in range(3)]
        point = Bounds(*xyz)

        if fn == "block":
            writes.include(point)
            write_count += multiplier
        elif fn == "getBlock":
            reads.include(point)
            read_count += multiplier
        elif fn == "structure":
            if len(args) < 4 or not re.fullmatch(r'"[A-Za-z0-9_:/.-]+"', args[3].strip()):
                raise AnalysisError(f"dynamic nested target in {script_id}")
            target = args[3].strip()[1:-1].split(":")[-1]
            child = static_script(target, files, stack + (script_id,))
            offset = point
            if child.writes.complete():
                writes.include(child.writes.translated(offset))
            if child.reads.complete():
                reads.include(child.reads.translated(offset))
            write_count += child.max_writes * multiplier
            read_count += child.max_reads * multiplier
            entity_count += child.max_entities * multiplier
            deps.update(child.dependencies)
        else:
            entity_count += multiplier

    if not writes.complete():
        raise AnalysisError(f"{script_id}: no fully proven block-write envelope")
    if not reads.complete():
        # The runtime read envelope includes a one-block halo around writes.
        reads = Bounds(
            Interval(writes.x.low - 1, writes.x.high + 1),
            Interval(writes.y.low - 2, writes.y.high + 2),
            Interval(writes.z.low - 1, writes.z.high + 1),
        )
    if max(write_count, read_count, entity_count) > MAX_OPERATIONS:
        raise AnalysisError(f"{script_id}: operation budget exceeds cap")

    return Profile(script_id, writes, reads, max(1, write_count),
                   max(1, read_count), entity_count, deps,
                   f"{PACK_SOURCE}; generated static interval proof")


def fallback_profile(script_id: str, reason: str, dependencies=None) -> Profile:
    r = FALLBACK_RADIUS
    return Profile(
        script_id,
        Bounds(Interval(-r, r), Interval(-r, r), Interval(-r, r)),
        Bounds(Interval(-r-1, r+1), Interval(-r-2, r+2), Interval(-r-1, r+1)),
        0, 0, 0,
        set(dependencies or ()),
        f"{PACK_SOURCE}; UNVERIFIED_DENY_ALL radius={r}; {reason}",
        True,
    )


def load_yaml_definitions(inventory, source_root: Path):
    """Parse every indexed YAML definition and resolve inheritance metadata.

    Terra's YAML feature types do not share one universal geometry schema.
    Therefore YAML entries are validated and dependency-linked, but receive
    the bounded runtime profile unless a future type-specific proof is added.
    """
    if yaml is None:
        raise AnalysisError("PyYAML is required to parse Terra YAML definitions")

    definitions = {}
    errors = {}
    for item in inventory.get("structure_definitions", []):
        sid, rel = item.get("id"), item.get("path")
        if not sid or not rel:
            continue
        path = source_root / rel
        if not path.is_file():
            errors[sid] = f"missing YAML source: {rel}"
            continue
        try:
            data = yaml.safe_load(path.read_text(encoding="utf-8"))
            if data is None:
                data = {}
            if not isinstance(data, dict):
                raise AnalysisError("YAML root must be a mapping")
            definitions[sid] = (data, path)
        except Exception as exc:
            errors[sid] = f"invalid YAML: {exc}"

    return definitions, errors


def yaml_dependencies(sid, definitions, stack=()):
    if sid in stack:
        raise AnalysisError("YAML inheritance cycle: " + " -> ".join(stack + (sid,)))
    if sid not in definitions:
        return set()
    data, _ = definitions[sid]
    deps = {sid}
    parent = data.get("extends")
    if isinstance(parent, str):
        deps.update(yaml_dependencies(parent, definitions, stack + (sid,)))
    return deps


def java_num(v: float) -> int:
    if not math.isfinite(v) or abs(v) > 2_147_483_647:
        raise AnalysisError(f"coordinate outside Java int range: {v}")
    return math.floor(v) if v < 0 else math.ceil(v)


def java_string(s: str) -> str:
    return json.dumps(s, ensure_ascii=False)


def profile_entry(p: Profile) -> str:
    w, r = p.writes, p.reads
    assert w.complete() and r.complete()
    deps = ", ".join(java_string(x) for x in sorted(p.dependencies))
    return (
        f"            Map.entry({java_string(p.structure_id)}, profile("
        f"{java_string(p.structure_id)}, "
        f"{java_num(w.x.low)}, {java_num(w.y.low)}, {java_num(w.z.low)}, "
        f"{java_num(w.x.high)}, {java_num(w.y.high)}, {java_num(w.z.high)}, "
        f"{java_num(r.x.low)}, {java_num(r.y.low)}, {java_num(r.z.low)}, "
        f"{java_num(r.x.high)}, {java_num(r.y.high)}, {java_num(r.z.high)}, "
        f"{p.max_writes}, {p.max_reads}, {p.max_entities}, "
        f"List.of({deps}), {java_string(p.evidence)}))"
    )


def generate_catalog(profiles: list[Profile]) -> str:
    verified_profiles = [p for p in profiles if not p.bounded_fallback]
    entries = ",\n".join(profile_entry(p) for p in verified_profiles)
    r = FALLBACK_RADIUS
    return f'''package org.powernukkitx.terra.crosschunk.bounds;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Generated from the pinned TerraOverworldConfig inventory. Do not edit manually. */
public final class OrigenBoundsCatalog {{
    public static final String PACK_ID = "overworld";
    public static final String PACK_SOURCE = "{PACK_SOURCE}";

    private static final Map<String, ScriptBoundsProfile> PROFILES = Map.ofEntries(
{entries}
    );

    private static final ScriptBoundsProfile BOUNDED_FALLBACK = profile(
            "<unverified-deny-all>", 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0,
            0, 0, 0, List.of(),
            "UNVERIFIED_DENY_ALL; static bounds proof required");

    private OrigenBoundsCatalog() {{}}

    private static ScriptBoundsProfile profile(String id,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            int readMinX, int readMinY, int readMinZ,
            int readMaxX, int readMaxY, int readMaxZ,
            int maxWrites, int maxReads, int maxEntities,
            List<String> dependencies, String evidence) {{
        return new ScriptBoundsProfile(PACK_ID, id,
                new Bounds3i(minX, minY, minZ, maxX, maxY, maxZ),
                new Bounds3i(readMinX, readMinY, readMinZ, readMaxX, readMaxY, readMaxZ),
                maxWrites, maxReads, maxEntities, dependencies, evidence);
    }}

    private static String normalize(String id) {{
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Structure ID is required");
        int namespace = id.lastIndexOf(':');
        return namespace >= 0 ? id.substring(namespace + 1) : id;
    }}

    public static Optional<ScriptBoundsProfile> find(String structureId) {{
        return Optional.ofNullable(PROFILES.get(normalize(structureId)));
    }}

    public static ScriptBoundsProfile resolveRuntimeProfile(String structureId) {{
        return PROFILES.getOrDefault(normalize(structureId), BOUNDED_FALLBACK);
    }}

    public static ScriptBoundsProfile boundedFallbackProfile() {{ return BOUNDED_FALLBACK; }}
    public static Map<String, ScriptBoundsProfile> allVerified() {{ return PROFILES; }}

    public static int maxHorizontalReach() {{
        return PROFILES.values().stream().mapToInt(p -> Math.max(
                Math.max(Math.abs(p.writeBounds().minX()), Math.abs(p.writeBounds().maxX())),
                Math.max(Math.abs(p.writeBounds().minZ()), Math.abs(p.writeBounds().maxZ())))).max().orElse(0);
    }}

    public static ScriptBoundsProfile requireVerified(String structureId) {{
        return find(structureId).orElseThrow(() ->
                new IllegalStateException("No Terra bounds profile for structure: " + structureId));
    }}
}}
'''



def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--inventory", required=True, type=Path)
    ap.add_argument("--source-root", required=True, type=Path)
    ap.add_argument("--output", required=True, type=Path)
    ap.add_argument("--strict", action="store_true",
                    help="fail if any root lacks a static bounds proof")
    args = ap.parse_args()

    inventory = json.loads(args.inventory.read_text(encoding="utf-8"))
    script_files = {
        item["id"]: args.source_root / item["path"]
        for item in inventory.get("scripts", [])
        if item.get("id") and item.get("path")
    }
    yaml_defs, yaml_errors = load_yaml_definitions(inventory, args.source_root)

    roots = set()
    for feature in inventory.get("features", []):
        roots.update(x for x in feature.get("structure_ids", []) if isinstance(x, str))
    roots = sorted(roots)

    profiles = []
    fallback_count = 0
    for sid in roots:
        try:
            if sid in script_files:
                p = static_script(sid, script_files)
            elif sid in yaml_defs:
                deps = yaml_dependencies(sid, yaml_defs)
                p = fallback_profile(sid, "YAML geometry schema not statically certified", deps)
            else:
                reason = yaml_errors.get(sid, "root has no indexed script/YAML source")
                p = fallback_profile(sid, reason)
        except Exception as exc:
            p = fallback_profile(sid, f"static analysis rejected: {exc}")
        profiles.append(p)
        fallback_count += int(p.bounded_fallback)

    if len(profiles) != len(roots):
        raise AnalysisError("not every feature root received exactly one profile")
    if args.strict and fallback_count:
        raise AnalysisError(f"{fallback_count} roots require bounded fallback")

    output = generate_catalog(profiles)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(output, encoding="utf-8")

    print(json.dumps({
        "feature_roots": len(roots),
        "profiles_generated": len(profiles),
        "static_profiles": len(profiles) - fallback_count,
        "unverified_deny_all_profiles": fallback_count,
        "yaml_definitions_parsed": len(yaml_defs),
        "yaml_parse_errors": len(yaml_errors),
        "output": str(args.output),
    }, indent=2))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(2)