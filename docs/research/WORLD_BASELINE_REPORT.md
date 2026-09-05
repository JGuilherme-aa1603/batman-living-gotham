# Los Perrito — baseline de desenvolvimento 1.20.1

Data da validação: 2026-09-05

## Declaração

`LosPerrito2.0/Los Perrito` é a baseline atual do Living Gotham. Ela já foi
convertida manualmente pelo usuário e salva pelo Minecraft/Forge 1.20.1.
Nenhuma migração adicional foi executada nesta rodada.

> **Esta é a baseline 1.20.1 a partir da qual Living Gotham será construída.**

## Metadados confirmados sem abrir o mundo para escrita

- path relativo: `LosPerrito2.0/Los Perrito`;
- `LevelName`: `Los Perrito`;
- `DataVersion`: **3465**;
- `Version.Name`: **1.20.1**;
- `Version.Series`: `main`;
- formato: Anvil Java/Forge;
- tamanho aparente: **1.069.613.991 bytes** em 6.428 arquivos;
- arquivos `.mca`: **646** — 231 `region`, 199 `entities`, 216 `poi`;
- regiões do overworld: X -7..10, Z -7..6;
- bounds aproximados cobertos pelos arquivos: X -3584..5631, Z -3584..3583
  em blocos. Isso é envelope de arquivos, não footprint exato de chunks;
- Nether e End existem, mas sem arquivos de região;
- dimensões de mods declaradas no `level.dat`:
  `batman_mod:virtual_training_environment`, `batman_mod:nanda_parbat` e
  `batman_mod:fear_dimension`.

O inventário de mods salvo no mundo inclui Forge 47.4.10, Batman Mod 1.0.9,
Create 6.0.8, WorldEdit 7.2.15, Flywheel 1.0.5, Ponder 1.0.91, Curios 5.14.1,
GeckoLib 4.8.4 e PlayerAnimator 1.0.2-rc1.

## Integridade da baseline

Antes e depois dos testes:

```text
level.dat SHA-256
8d79d69541005bda1118b1f07928e9358a8b5d340e251e244d766670d861cea0

session.lock SHA-256
225114ee5da2e0c5c5a60a381b95079cabddba153ae29ab332989166a53e56db
```

Também permaneceram inalterados os mtimes observados:

```text
level.dat    2026-09-04 15:22:18 -0300
session.lock 2026-09-04 15:22:02 -0300
```

Não foi usado `amulet.load_level`, Minecraft, Forge, WorldEdit ou qualquer
outra ferramenta capaz de escrever apontando para a baseline. A inspeção de
metadados foi feita por leitura binária/NBT que não abre `session.lock` para
escrita.

## Cópia DEV e runtime

O filesystem é Btrfs e o suporte real a reflink foi comprovado. O script
`scripts/create-dev-world` usa `cp -a --reflink=auto` para criar uma cópia em
um destino temporário e só então renomeá-la para o destino final. Ele:

- resolve paths e recusa origem/destino iguais;
- recusa destino dentro da baseline;
- recusa sobrescrever destino existente;
- recusa symlink como destino;
- mostra origem e destino antes da operação;
- limpa apenas seu diretório parcial se a cópia falhar.

A cópia testada foi `runtime/forge/saves/Los Perrito DEV`; seu `level.dat`
começou com o mesmo hash mas inode distinto. Todas as escritas de probes foram
feitas nessa cópia.

Ela abriu no cliente Forge 1.20.1 / Forge 47.4.10 com Living Gotham, Batman By
Yo Fadda, Create, WorldEdit e TaCZ. O servidor integrado salvou normalmente,
foi fechado, reaberto em uma segunda execução e permaneceu legível. A segunda
execução encontrou `footprints_in_chunk=1`, confirmando persistência do
`SavedData` criado no primeiro teste.

Comando recomendado:

```bash
scripts/create-dev-world 'runtime/forge/saves/Los Perrito DEV'
```

## Problemas existentes observados na cópia

O runtime registrou problemas já presentes nos dados/mods carregados:

- duas entradas inválidas em `data/random_sequences.dat`, sem os campos
  esperados `salt`/`sequences`;
- componente JSON inválido em `data/scoreboard.dat`;
- referências inválidas no tag `battag:flight_damagable`;
- vários sons, modelos e texturas ausentes ou resolvidos sob namespace errado
  pelo Batman Mod.

Esses erros não impediram abertura, save e reabertura da cópia, mas devem ser
tratados como dívida de compatibilidade antes de usar esses conteúdos em
gameplay. Nenhum desses arquivos foi reparado nesta fase.

## Regra operacional

```text
Los Perrito baseline 1.20.1 -> imutável
reflink/cópia DEV           -> mutável e descartável
```
