# Living Gotham — City Index Report

## Status

**RUNTIME/OFFLINE CONFIRMED — 2026-09-08.** A primeira versão do City Index
extraiu integralmente o overworld da baseline convertida, usando somente uma
cópia de análise. Isto é fundação técnica e inventário espacial; não cria
districts, crimes ou qualquer gameplay.

## Environment

- Baseline: `LosPerrito2.0/Los Perrito` (imutável).
- Cópia analisada: `runtime/analysis/Los Perrito CITY INDEX` (ignorada pelo
  Git).
- Python: 3.12.14.
- City Index: 0.2.0, schema 2.
- `amulet-nbt`: 2.1.8; NumPy: 1.26.4; Pillow: 12.3.0.
- LevelName: `Los Perrito`; DataVersion: 3465 (Minecraft 1.20.1).
- `level.dat` baseline e cópia:
  `8d79d69541005bda1118b1f07928e9358a8b5d340e251e244d766670d861cea0`.
- `session.lock` baseline e cópia:
  `225114ee5da2e0c5c5a60a381b95079cabddba153ae29ab332989166a53e56db`.

Os hashes da baseline foram conferidos novamente depois das extrações e não
mudaram. O parser abre diretamente Anvil/NBT e não usa a API de mundo do
Amulet. Uma tentativa explícita de apontá-lo para `LosPerrito2.0/Los Perrito`
foi recusada com exit code 1. Schema 2 também recusa atribuir a identidade da
baseline quando `--source-level-hash` não coincide com a cópia analisada.

## Scope and coordinates

Somente `minecraft:overworld` foi indexado nesta versão. Bounds encontrados:

```text
blocks: x -3584..5215, z -3584..3583
chunks: x -224..325, z -224..223
regions: 231 files
```

Presentes, mas deliberadamente não indexadas:

- `minecraft:the_nether`;
- `minecraft:the_end`;
- `batman_mod:fear_dimension`;
- `batman_mod:nanda_parbat`;
- `batman_mod:virtual_training_environment`.

Conversões com coordenadas negativas e round trips de chunk/region/tile estão
cobertas por testes unitários. Tiles registram origem, bounds, escala de dois
blocos por pixel e orientação `+Z = south`.

## Pipeline implemented

### Fingerprint and protection

O manifesto guarda nome, dimensão, DataVersion, hashes, bounds, inventário de
regiões, versão da ferramenta e versão do schema. A entrada deve ser uma cópia
fora de `LosPerrito2.0/`.

### Pass 1 — complete surface inventory

Foram lidos **224.047/224.047 chunks**, sem falhas. Para cada chunk o banco
mantém timestamp, setores comprimidos, DataVersion, `Status`, altura e
variância, rugosidade, frações de água/vegetação/artificial/rua/telhado,
densidade urbana, presença de POI e presença de arquivo de entidades.

A superfície é agregada em células de 4×4 blocos. Foram persistidas
**3.584.752 células**. Chunks periféricos em estágios de geração incompletos
são preservados com seu `Status`; eles apresentaram densidade urbana zero nos
dados atuais. `full` corresponde a 214.272 chunks e `minecraft:full` a 1.500;
os demais estados são principalmente o envelope externo ainda não promovido.

### Pass 2 — geometry and selective metadata

O detector produziu:

| Tipo | Quantidade |
|---|---:|
| buildings | 748 |
| rooftops | 723 |
| roads | 631 |
| road nodes/intersections | 6.797 |
| alleys | 868 |
| water bodies | 70 |
| open areas | 307 |
| spatial clusters | 35 |

Isso totaliza **3.382 features**. IDs derivam deterministicamente do tipo,
dimensão, geometria e bounds; não dependem da ordem de enumeração.

Metadados detalhados foram lidos apenas em 18.948 chunks urbanos ou com POI.
Foram encontradas 3.559 placas, das quais 1.438 têm texto não vazio. O fluxo de
busca encontrou fatos reais como `Melport Police | Department`, `Bank of Los
Perrito` e nomes de estações. A associação placa→prédio é apenas proximidade e
pode ficar vazia ou incorreta.

### Semantic rankings

Foram geradas **645 linhas candidatas**:

| Ranking | Linhas | Natureza |
|---|---:|---|
| Wayne Manor | 70 | INFERRED |
| GCPD | 265 | INFERRED |
| industrial | 142 | INFERRED |
| harbor | 29 | INFERRED |
| Ace Chemicals | 139 | INFERRED |

Esses nomes **não identificam locais canônicos**. São rankings explicáveis de
geometria, isolamento, acesso viário, água, tamanho e centralidade. Os sinais
de material/indústria ainda usam priors neutros e nenhuma placa encontrada
confirma Wayne Manor, GCPD ou Ace Chemicals. Pesquisa por substring também pode
produzir falsos positivos (`ace` dentro de `place`), portanto UI futura deve
deixar claro `INFERRED` versus `MEASURED`.

## Required queries

Foram executados smoke tests reais de:

- densidade urbana;
- maiores prédios por footprint e volume;
- prédios mais altos;
- rooftops por área/flatness;
- lookup por feature ID;
- busca espacial por raio;
- busca de placas;
- candidatos por tipo;
- alleys globais e próximos a prédio;
- respostas consolidadas em `answers.json`.

As consultas operam somente sobre SQLite. O artifact canônico local ocupa
350.568.448 bytes; cache NumPy, 4.377.915 bytes; todo o output gerado ocupa
aproximadamente 345 MiB e está ignorado pelo Git.

## Visual evidence

Foram produzidos 12 mapas overview, 252 tiles com coordenadas e 50 crops de
candidatos. A inspeção visual de `buildings-overview.png`,
`urban-density-overview.png` e da contact sheet determinística de 20 amostras
confirmou:

- a transformação de coordenadas está orientada e alinhada ao mapa;
- overlays vermelhos recaem sobre superfícies artificiais construídas nas 20
  amostras observadas;
- algumas features representam apenas partes de telhado ou estruturas cuja
  função semântica é desconhecida;
- a amostra avalia precisão visual local, não recall global e não valida os
  nomes semânticos candidatos.

Logo, os mapas são adequados para triagem técnica e revisão humana, não para
decidir automaticamente locais finais de gameplay.

## Interior feasibility

Um probe sob demanda abriu volumes limitados de três prédios em 0,96 s. Ele
encontrou, entre outros resultados, 575 blocos de ar enclausurado, 19 portas e
níveis navegáveis Y=73 e Y=76 em uma amostra. A detecção atual prova que análise
3D localizada é possível, mas ainda não produz um grafo confiável de cômodos.
Varredura global de interiores não é recomendada nesta versão.

## Performance and cache

Benchmark distribuído de 8.192 chunks: 1.577 chunks/s, 118 MiB de pico e
estimativa de 142 s para o decode bruto completo.

Extração canônica observada:

```text
Pass 1: 224047 chunks, 276.79 s, 809.44 chunks/s, 287.7 MiB peak RSS
Pass 2: 18.01 s, 659.9 MiB peak RSS
failures: 0
```

Execução incremental sem mudanças:

```text
regions reused: 231/231
chunks reused: 224047/224047
chunks rescanned: 0
Pass 1: 2.48 s
Pass 2 + maps: ~16.8 s
```

O hash de cada `.mca` participa da invalidação, além de tamanho e mtime. O custo
da segunda passada ainda é pago integralmente; cache geométrico incremental é
uma otimização futura, não necessária para a prova atual.

## Interrupted-scan finding and fix

Durante a retomada foi encontrado um SQLite interrompido após exatamente
162.000 chunks, coexistindo com JSONs de uma execução completa anterior. O
banco estava estruturalmente válido para SQLite, mas semanticamente incompleto.

Schema 2 corrige a detecção desse risco com a tabela `scan_state`:

```text
RUNNING_PASS1 -> PASS1_COMPLETE -> RUNNING_PASS2
-> PASS2_COMPLETE -> RUNNING_RENDER -> COMPLETE
```

As consultas recusam qualquer estado diferente de `COMPLETE`. O término exige
contagem exata de chunks e células e zero falhas. Após a correção, uma extração
completa e outra incremental terminaram com:

```text
state: COMPLETE
chunks: 224047/224047
surface cells: 3584752/3584752
failure_count: 0
PRAGMA integrity_check: ok
```

## Tests

- 23 testes unitários: PASS.
- Compilação Python (`compileall`): PASS.
- Proteção da baseline: PASS (exit 1).
- Rejeição de hash divergente: PASS (exit 1).
- Extração completa: PASS.
- Extração incremental: PASS.
- Smoke tests de todos os comandos de consulta: PASS.
- Probe interior limitado: PASS.
- Revisão visual: PASS com as ressalvas acima.
- Regressão Forge: `scripts/with-java17 ./gradlew clean build` passou em 22 s
  sobre Java 17.0.19, incluindo a verificação dos JARs locais esperados.

## Risks and limits

- Classificação de blocos é configurável, mas ainda baseada em nome/material de
  superfície; fachadas, sobreposições e telhados conectados podem fragmentar ou
  unir features.
- A resolução 4×4 sacrifica quiosques e detalhes estreitos para reduzir ruído.
- Altura de prédio é estimada pela diferença entre superfície e anel ao redor;
  torres finas e terreno acidentado podem inflar o valor.
- Roads e alleys são sinais geométricos, não redes navegáveis validadas.
- Spatial clusters não são Districts de gameplay.
- Nenhum candidato semântico deve ser promovido sem inspeção no Minecraft.
- Interiores, dimensões adicionais, entidades completas e blockstates 3D ficam
  fora do schema 2.

## Conclusion

**RECOMMENDATION: usar o City Index 0.2 como inventário espacial offline e
fonte de candidatos revisáveis.** A cobertura, proteção da baseline,
reprodutibilidade, cache e queries foram comprovados. Antes de qualquer sistema
de gameplay consumir locais, fazer uma rodada humana de ground truth dos
candidatos escolhidos e persistir as confirmações separadamente das inferências
automáticas.
