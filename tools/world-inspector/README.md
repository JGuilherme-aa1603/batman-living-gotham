# Living Gotham world inspector

Small, bounded proof-of-concept for the future City Index pipeline. It reads a
rectangular chunk selection and writes:

- `summary.json`
- `block_stats.json`
- `heightmap.png`

Amulet Core 1.9.45 opens Java worlds for read/write and rewrites
`session.lock`, even when the caller only intends to inspect data. For that
reason this tool refuses to open anything below the repository's
`LosPerrito2.0/` directory. Always point it at a disposable copy while
Minecraft is closed.

## Environment

Python 3.12 is recommended. The system Python may be newer than Amulet's
native dependencies support.

```bash
uv venv --python 3.12 .tooling/amulet-venv
uv pip install --python .tooling/amulet-venv/bin/python \
  -r tools/world-inspector/requirements.txt
```

## Example

```bash
.tooling/amulet-venv/bin/python tools/world-inspector/world_inspector.py \
  /tmp/los-perrito-working-copy \
  --dimension minecraft:overworld \
  --min-chunk-x 0 --max-chunk-x 1 \
  --min-chunk-z 0 --max-chunk-z 1 \
  --output-dir /tmp/living-gotham-inspection
```

Bounds are inclusive. The default safety limit is 256 requested chunks; use
`--max-chunks` to choose a smaller or larger explicit limit.
