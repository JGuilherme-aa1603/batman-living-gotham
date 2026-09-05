# Yo Fadda 1.0.9 Integration Report

Data da validação: 2026-09-05

## Evidence labels

- **DECOMPILED**: confirmado por inspeção local do bytecode/JAR exato;
- **RUNTIME**: confirmado pelo cliente/server Forge real;
- **INFERENCE**: conclusão técnica ainda sem prova completa em jogo;
- **NOT CONFIRMED**: não demonstrado nesta fase.

O código decompilado foi mantido somente em `/tmp`, não foi copiado para fontes,
docs ou Git. Nenhum código ou asset do mod foi redistribuído.

## Environment

- Minecraft 1.20.1 / Forge 47.4.10 / Java 17.0.19;
- Batman By Yo Fadda: mod id `batman_mod`, versão 1.0.9;
- JAR SHA-256:
  `77f9fc64d6fb919df2551b42e9ff8204fe8a22f5fba30c7d48b843af10f5801c`;
- execução conjunta com Living Gotham, Create, WorldEdit e TaCZ;
- mundo: cópia descartável `Los Perrito DEV`.

## Mod identity and registries

**RUNTIME — PUBLIC_REGISTRY:** `batman_mod:sample_vial`,
`batman_mod:red_blood_sample` e `batman_mod:green_blood_sample` existem.
Living Gotham acessa registries Forge e não referencia os campos gerados de
itens.

O JAR é proprietário/All Rights Reserved. Não modificar, repacotar ou reutilizar
assets. Todas as referências diretas ficam em `integration/yofadda`.

## Forensic Scanner

- **Access:** `PUBLIC_CLASS`; capability Forge pública
  `BatmanModModVariables.PLAYER_VARIABLES_CAPABILITY` e campo público
  `ForensicScanner`. É acessível, mas não é API formal estável.
- **Authority:** **DECOMPILED** — estado persistido/sincronizado por capability;
  a ativação deriva de procedimentos/teclas e equipamento válido.
- **Probe result:** **RUNTIME** — `/lgprobe forensics` reportou scanner `OFF`,
  Detective Mode `OFF` e BloodInSight `OFF`. O observador automático também
  registrou a transição inicial `false` no server.
- **Scanner ON:** **NOT CONFIRMED** — não foi forçado e não houve interação
  manual com traje/tecla durante o probe. Portanto o requisito OFF/ON ainda está
  parcialmente aberto.
- **Version-break risk:** alto para nomes de campos/classes MCreator; usar
  adapter, checagem de presença e teste de regressão na versão exata.

**DECOMPILED:** a tecla chama `DetectiveModeButtonPressed`; capacetes clássicos
ou conjunto Beyond habilitam o fluxo; após `ButtonPressVariable >= 10`,
`ForensicScanner` torna-se verdadeiro. Desligar Detective Mode reseta o scanner.

## Detective Mode

**PUBLIC_CLASS / ACCESSIBLE_INTERNAL.** Campo público `DetectiveMode` na mesma
capability. É legível no server, persistido e sincronizado, porém não há evento
semântico público “mode changed”. O probe compara estados em tick somente para
diagnóstico. Não usar polling amplo como design final antes de medir custo.

## Footprints

### Data model and lifecycle

**DECOMPILED — PUBLIC_CLASS:**

- `Footprint` expõe campos finais `x`, `y`, `z`, `yaw`, `size`, `kind`,
  `rightFoot`, `time` e métodos de NBT/network;
- kinds: player 0, neutral 1, hostile 2, targeting 3;
- `ForensicTrailData extends SavedData`, id `batman_forensic_trails`;
- armazenamento por `ServerLevel`/dimensão, indexado por chunk;
- lifetime 12.000 ticks (aproximadamente dez minutos) e limite 64 por chunk;
- `ForensicTrail` emite no server para entidades no chão, com tamanho mínimo,
  não montadas/espectadoras/voando, respeitando stride; purga a cada 200 ticks;
- renderer usa cache client sincronizado enquanto há scanner.

### Read

**RUNTIME — PUBLIC_CLASS:** sim. `ForensicTrailData.get(level).chunk(key)` foi
lido no server. A primeira sessão mostrou 0 e depois 1; uma execução nova,
read-only, encontrou novamente `footprints_in_chunk=1`.

### Create and persistence

**RUNTIME — PUBLIC_CLASS:** sim, apenas em DEV.
`ForensicTrailData.add(new Footprint(...))` criou uma footprint de teste. O
server salvou `data/batman_forensic_trails.dat`, o mundo fechou/reabriu e a
footprint continuou legível.

### Observe and case association

- criação natural pode ser inferida por diff de `SavedData`, mas o mod não
  publica evento Forge específico;
- não foi encontrado evento semântico para “footprint selecionada”, “escaneada”
  ou “seguida”; scanner/cache/render podem ser observados, não a intenção;
- não adicionar `case id` ao NBT Yo Fadda. **INFERENCE:** manter índice Living
  Gotham separado, usando dimensão + posição + tempo + kind + pé como chave
  compatível e descartável;
- associação robusta com entidade de origem não está persistida no `Footprint`.

## Blood evidence

**DECOMPILED:** `SampleVial` processa `useOn` em bloco; o sangue fica no bloco
acima do clicado. No server, substitui a mão principal pelo sample e no tick
seguinte copia tags persistentes da block entity (`Name`, `Health`, `Height`,
`Age`, `Potions`), remove o bloco de sangue e abre o menu de rótulo.

Living Gotham registra `RightClickBlock` e observa a mudança de inventário no
tick seguinte. Isso é **FORGE_EVENT + PUBLIC_REGISTRY** e evita chamar
procedimentos internos.

**RUNTIME:** os três itens foram encontrados. **NOT CONFIRMED:** uma coleta real
de sangue não foi encenada nesta fase; logo a detecção ponta a ponta
objective -> coleta permanece pendente.

## Sample Vial

O caminho recomendado é observar, no server:

1. interação com `sample_vial` via Forge event;
2. transição do ItemStack para `red_blood_sample`/`green_blood_sample`;
3. NBT público resultante e menu aberto, sem assumir o conteúdo de tags.

Classificação: **FORGE_EVENT + PUBLIC_REGISTRY** para observação; chamar o
procedimento MCreator diretamente seria **ACCESSIBLE_INTERNAL** e deve ser
evitado.

## DNA systems

**DECOMPILED:**

- `ForensicDNAScanCondition` exige scanner e head item Beyond, faz ray trace de
  15 blocos e atualiza `BloodInSight` ao mirar sangue;
- `DeeperDNAScanProcedure` move o sample do slot 0 para `vial_transfer`, abre o
  menu de análise e restaura o item no tick seguinte;
- menu público observado no registry inclui `batman_mod:dna_scan_page`; o fluxo
  decompilado referencia a página de potion scan.

Não foi encontrado um estado persistente e distinto de “DNA analysis
completed”. O que outro mod pode observar hoje é menu/item/NBT/capability.
Classificação: **PUBLIC_REGISTRY/PUBLIC_CLASS** para leitura superficial;
sem evento semântico, integração profunda é **ACCESSIBLE_INTERNAL** ou exige um
hook ainda não justificado. **NOT CONFIRMED:** análise real no runtime.

## Random Crimes

**DECOMPILED + RUNTIME:** `MapVariables.RandomCrimeON` é true por padrão,
persistido como `batman_mod_mapvars` no overworld. O probe real reportou `ON`.

Fluxo decompilado:

- em todo `PlayerTick` server, se ON, chunk carregado e sem Bane/Joker/Scarecrow
  num raio de 200, sorteio uniforme `0..100 <= 0.05` concede `CrimeMagnet` por
  60 ticks, amplifier 1;
- isso equivale aproximadamente a 0,05% por player-tick, média de 100 segundos
  por jogador quando as condições permanecem verdadeiras;
- com `CrimeMagnet`, novo teste e ausência de Bane/Joker/Killer Croc/Scarecrow
  em 300 blocos pode gerar Bane, Joker + quatro Joker Thugs, ou Scarecrow;
- o cálculo deixa coordenadas em zero se a procura de ar falhar, criando risco
  de spawn na origem.

Pode ser desligado pelo campo público, mas isso é **PUBLIC_CLASS**, não API
estável. Spawn pode ser observado via Forge `EntityJoinLevelEvent`; não foi
encontrado evento de origem “random crime”. Interceptar o procedimento exigiria
**MIXIN_REQUIRED**, sem justificativa nesta fase. Antes do Crime System, decidir
se Living Gotham desliga o gerador original ou apenas coexiste; dois diretores
independentes causariam duplicidade e imprevisibilidade.

## Useful entities

Inventário confirmado por registry/classes do JAR exato; comportamento abaixo
é **DECOMPILED**, ainda não testado em combate no runtime:

| Registry id | Classe/base | Categoria e comportamento principal |
|---|---|---|
| `batman_mod:joker_thug` | `JokerThugEntity` / `Monster` | Registrado MISC; melee; alvos Villager/Player; 50 HP, dano 3, follow 16 |
| `batman_mod:robber` | `RobberEntity` / `Monster` | Registrado MISC; melee; Villager/WanderingTrader/Player; 40 HP, dano 3 |
| `batman_mod:league_assassin` | `LeagueAssassinEntity` / `Monster` | MONSTER; melee, portas; 60 HP, armadura 1, dano 2; regra própria de despawn |
| `batman_mod:joker` | `JokerEntity` / `Monster` | MONSTER; crowbar, melee; 50 HP, dano 3 |
| `batman_mod:scarecrow` | `ScarecrowEntity` / `Monster` | melee/portas, alvo amplo; 50 HP, dano 3, follow 25 |
| `batman_mod:bane` | `BaneEntity` / `Monster` | melee/porta; 100 HP, armadura 3, dano 8, knockback resistance 1 |
| `batman_mod:killer_croc` | `KillerCrocEntity` | entidade de vilão usada nas exclusões de random crime |
| `batman_mod:mr_freeze` | `MrFreezeEntity` | entidade de vilão; runtime acusa vários sons ausentes |

Também existem Ras Al Ghul, Fear Zombie/Crow/Clone/Creeper e veículos como
Batmobile, Batwing, Tankmobile, MKII, Tumbler, BatPod e Robin Cycle. Equipment,
persistência e problemas de todas essas entidades não foram exaustivamente
provados; não tratá-las como NPCs finais ainda.

## Vehicles

**PUBLIC_REGISTRY / PUBLIC_CLASS**, mas sem probe funcional. A mera existência
dos entity types não prova API de controle, persistência ou compatibilidade com
objetivos Living Gotham. Reuso fica adiado.

## Integration strategy

### Public APIs/events

- Forge item/entity/player events para observação geral;
- registries Forge para itens, menus e entidades;
- capability pública para estados forenses;
- `SavedData` público para footprints.

### Direct class references

Limitar a `integration/yofadda`; verificar `ModList` antes de registrar/calling;
tratar `NoClassDefFoundError`/mudança de símbolo como incompatibilidade
diagnosticável. Classes públicas geradas por MCreator não equivalem a contrato
de versão.

### Reflection

Não necessária para os probes atuais. Só considerar se uma versão futura
ocultar um dado essencial e não houver registry/event/capability melhor.

### Mixins

Não usados. Poderiam interceptar eventos semânticos ausentes, mas o custo e o
risco não são justificados antes de definir objetivos mínimos.

### Things we should avoid

- modificar o JAR, NBT ou assets Yo Fadda;
- chamar procedures MCreator como API;
- escrever metadados Living Gotham dentro de `Footprint`;
- assumir que renderer/client cache é autoridade;
- manter Random Crime original e um novo director simultaneamente sem política.

## Compatibility risks

- nomes/packages/campos MCreator podem mudar sem semver de API;
- scanner ON e fluxos blood/DNA ainda precisam de prova runtime manual;
- assets/sounds/tags quebrados foram observados no mod real;
- categorias MISC em entidades `Monster` podem afetar spawn/despawn e filtros;
- footprints expiram e não guardam entidade de origem;
- Random Crime tem polling por player e possível fallback de coordenada zero.

## Overall conclusion

Há uma integração pragmática possível sem reflection/mixin para leitura de
scanner, footprints e registries. Leitura, criação e persistência de footprint
foram comprovadas em runtime; scanner OFF e Random Crime ON foram comprovados.
Scanner ON, coleta de sangue e conclusão de DNA ainda não foram provados e
devem permanecer marcados como pendentes. Manter Yo Fadda atrás de adapter e
adicionar um teste de compatibilidade obrigatório a cada troca de JAR.
