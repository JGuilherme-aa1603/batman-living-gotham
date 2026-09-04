# Amulet — relatório de validação

Data do teste: 2026-09-04

## Ambiente e instalação

- Python global: **3.14.7**.
- Python isolado escolhido: **3.12.14**, gerenciado por `uv` em
  `.tooling/amulet-venv/` (ignorado pelo Git).
- Amulet Core: **1.9.45**.
- amulet-nbt: **2.1.8**.
- Pillow: **12.3.0**.

A tentativa com Python 3.14 não foi adequada: dependências nativas do Amulet,
incluindo o conjunto NumPy/rocksdb selecionado, não tinham um caminho de wheel
compatível e exigiam build. Nada foi instalado globalmente. O ambiente Python
3.12 importou e executou a versão estável publicada do
[Amulet Core](https://pypi.org/project/amulet-core/).

## Proteção do original e leitura

O código de Amulet 1.9.45 foi inspecionado antes de abrir o mundo. O método
`AnvilFormat._reload_world()` abre `session.lock` com `wb+`, grava timestamp e
adquire lock exclusivo. Portanto, `amulet.load_level()` **não é read-only no
filesystem**, mesmo que nenhuma chamada `save()` seja feita.

No original `LosPerrito2.0/Los Perrito` foram usadas somente:

- `amulet.load_format()` sem `open()`, que faz leitura rasa de `level.dat`;
- leitura binária dos cabeçalhos de localização dos arquivos `.mca`;
- `stat` e SHA-256 antes/depois.

`level.dat` e `session.lock` mantiveram tamanho, mtime e hashes. Resultado:

- formato: Anvil Java;
- versão gravada: **Java 1.19.2**, DataVersion **3120**;
- nome: `Los Perrito`;
- overworld: **231** arquivos de região, **224.047** chunks, aproximadamente
  1.037.148.160 bytes de regiões;
- bounds de chunks encontrados: X -224..325, Z -224..223;
- Nether e End: dimensões/pastas existentes, sem chunks de região.

Não foi feita varredura de conteúdo do mapa inteiro.

## Abertura completa em cópia descartável

Foi criada uma cópia por reflink em `/tmp/living-gotham-amulet.t79qpV/world`.
Os hashes iniciais de `level.dat` e `session.lock` correspondiam ao original.
Somente essa cópia foi aberta por `amulet.load_level()`.

- abertura: **0,014 s**;
- enumeração dos 224.047 chunks: **2,330 s**;
- carregamento do chunk (0,0): **0,035 s**;
- dimensões retornadas: overworld, Nether e End;
- paleta do chunk (0,0): 28 blockstates universais;
- block entities no chunk: 2 (`lodestone` e `barrel`);
- entidades no chunk: 0.

Blockstates com propriedades foram preservados na representação universal, por
exemplo `grass_block[snowy="false"]` e `barrel[facing="up",open="false"]`.
Block entities puderam ser enumerados e seus NBT universais consultados.

## Escrita controlada em cópia

No bloco (0,319,0) do overworld da cópia:

1. leu `minecraft:air`;
2. escreveu `minecraft:gold_block`;
3. salvou e fechou em **0,015 s**;
4. reabriu e confirmou `gold_block`;
5. restaurou o bloco original;
6. salvou, fechou, reabriu e confirmou `air` e mundo legível.

Os hashes do original continuaram inalterados depois da prova.

## World inspector

Foi criado `tools/world-inspector/`. A prova em 2×2 chunks (0..1, 0..1) levou
**0,098 s** e gerou em `/tmp/living-gotham-inspection/`:

- `summary.json`;
- `block_stats.json`;
- `heightmap.png` (32×32, grayscale normalizado).

Foram carregados 4 chunks, 393.216 posições nas seções presentes, 34
blockstates únicos, 2 block entities e superfície entre Y 69 e 114. O utilitário
tem limite padrão de 256 chunks e recusa qualquer path resolvido sob
`LosPerrito2.0/`.

## Riscos e limitações

- `load_level()` escreve `session.lock`; nunca apontar para o worldbase original.
- Amulet usa cache/history local. Sem `XDG_CACHE_HOME` gravável, a inicialização
  falhou e o destrutor emitiu um segundo `AttributeError` por objeto parcialmente
  inicializado. O inspector define cache temporário por padrão.
- Python 3.14 não é uma base segura para Amulet 1.9.45; manter Python 3.12 isolado.
- O mapa está salvo como 1.19.2, enquanto o runtime alvo é Forge 1.20.1. Qualquer
  upgrade deve ocorrer numa cópia e ser validado com os mods reais.
- Block entities comuns foram legíveis, mas isso não prova round-trip seguro de
  block entities ou contraptions do Create. Não fabricar NBT complexo offline.
- Métricas de tempo são uma amostra nesta máquina, não benchmark formal.

## Recomendação

```text
RECOMMENDATION:
[ ] use Amulet
[ ] use Amulet read-only
[x] Amulet + complementary tool
[ ] replace with another Anvil/NBT solution
```

Usar Amulet para City Index, estatísticas e alterações vanilla mínimas somente
em cópias. Usar leitura direta Anvil/NBT quando a operação precisar ser
fisicamente read-only, e WorldEdit dentro do runtime Forge para transformações
estruturais e dados de mods. Não substituir Amulet nesta fase.
