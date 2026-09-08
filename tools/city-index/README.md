# Living Gotham City Index

Offline, two-pass spatial extraction for the Los Perrito overworld. The tool
uses physically read-only Anvil/NBT parsing, but still refuses paths below
`LosPerrito2.0/`; scans must target a dedicated analysis copy. Schema 2 also
records the extraction lifecycle and refuses queries unless the scan reached
`COMPLETE`.

## Setup

Python 3.12 is the validated interpreter. The City Index may reuse the isolated
Amulet environment, but owns an explicit dependency lock:

```bash
uv venv --python 3.12 .tooling/amulet-venv
uv pip install --python .tooling/amulet-venv/bin/python \
  -r tools/city-index/requirements.txt
```

## Extract

```bash
scripts/create-dev-world 'runtime/analysis/Los Perrito CITY INDEX'
.tooling/amulet-venv/bin/python tools/city-index/city-index benchmark \
  'runtime/analysis/Los Perrito CITY INDEX' --output generated/city-index
.tooling/amulet-venv/bin/python tools/city-index/city-index scan \
  'runtime/analysis/Los Perrito CITY INDEX' --output generated/city-index \
  --source-level-hash 8d79d69541005bda1118b1f07928e9358a8b5d340e251e244d766670d861cea0
.tooling/amulet-venv/bin/python tools/city-index/city-index stats \
  generated/city-index/city_index.sqlite
.tooling/amulet-venv/bin/python tools/city-index/city-index answers \
  generated/city-index/city_index.sqlite \
  --output generated/city-index/answers.json
```

Generated databases, caches, maps, tiles and the analysis world are local and
Git-ignored. Only `minecraft:overworld` is extracted in schema version 2.
Semantic names such as `wayne_manor`, `gcpd` and `ace_chemicals` are ranked
candidates, never confirmed identities.

## Query and test

The CLI also exposes `buildings`, `feature`, `rooftops`, `near`, `signs`,
`candidates`, `alleys` and a bounded, on-demand `interior` feasibility probe.
Run the pure tests from the repository root:

```bash
PYTHONPATH=tools/city-index \
  .tooling/amulet-venv/bin/python -m unittest discover \
  -s tools/city-index/tests -v
```

See `docs/research/CITY_INDEX_REPORT.md` for measured coverage, performance,
visual inspection and known limits.
