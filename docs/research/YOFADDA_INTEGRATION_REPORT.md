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
  Detective Mode `OFF` e BloodInSight `OFF`; depois, o fluxo normal com Batsuit
  clássico e keybind Z produziu `OFF -> ON -> OFF` no server.
- **Scanner ON:** **RUNTIME CONFIRMED** — ativou com `buttonTicks=10`, sem o
  Living Gotham forçar ou escrever qualquer campo da capability.
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

**RUNTIME (Phase 1.1):** os três itens foram encontrados e uma coleta real foi
executada. Living Gotham observou `sample_vial -> red_blood_sample` server-side
um tick após `RightClickBlock` e o menu/tag completo no tick seguinte.

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
hook ainda não justificado. **RUNTIME (Phase 1.1):** o percurso real chegou a
`dna_potion_scans`, com a amostra e seu NBT preservados em `vial_transfer`.

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
- scanner, blood e DNA dependem de campos/menus MCreator sem contrato de API e
  precisam de regressão obrigatória a cada versão;
- assets/sounds/tags quebrados foram observados no mod real;
- categorias MISC em entidades `Monster` podem afetar spawn/despawn e filtros;
- footprints expiram e não guardam entidade de origem;
- Random Crime tem polling por player e possível fallback de coordenada zero.

## Overall conclusion

Há uma integração pragmática possível sem reflection/mixin para leitura de
scanner, footprints e registries. Leitura, criação e persistência de footprint,
scanner OFF/ON/OFF, coleta real de sangue e o percurso DNA até a tela de
resultado foram comprovados em runtime. Como não existe evento semântico de
conclusão de DNA, manter Yo Fadda atrás de adapter e adicionar teste de
compatibilidade obrigatório a cada troca de JAR.

## Phase 1.1 probe status

### Scanner observer and legitimate activation path

**RUNTIME:** o observer Living Gotham registra separadamente CLIENT/SERVER e
somente transições de `DetectiveMode`, `ForensicScanner`, `BloodInSight` e
`DetectiveModeButtonPressed`, incluindo tick, jogador e os quatro slots de
armadura. `/lgprobe yofadda forensic-kit`, protegido por `DevWorldSafety`,
equipou itens reais `batsuit_*`; não escreveu capability.

**DECOMPILED:** a ação normal é a keybind Z, “Detective Mode (Hold For
Forensic)”. Com Batsuit clássico completo, manter Z por pelo menos 10 ticks
ativa scanner; pressionar novamente desliga Detective Mode/scanner.

**RUNTIME CONFIRMED:** uma pessoa acionou a keybind Z pelo fluxo normal. O
server registrou `DetectiveMode false -> true` no tick `55135020` e
`ForensicScanner false -> true` no tick `55135029`, com `buttonTicks=10` e o
Batsuit clássico completo. Um segundo Z produziu `ForensicScanner true ->
false` no tick `55135124`; ciclos posteriores repetiram o resultado. Nenhum
campo da capability foi escrito pelo Living Gotham.

### BloodInSight authority correction

**DECOMPILED:** `ForensicDNAScanConditionProcedure` é invocado pelo overlay
cliente Beyond. Ele altera a capability do `LocalPlayer`; o método de sync só
envia quando a entidade é `ServerPlayer`. Portanto há forte evidência de que
`BloodInSight` real é client-local nessa versão, apesar do campo também existir
na capability server. O observer agora mede ambos os lados.

**NOT CONFIRMED:** sem scanner Beyond ON e mira manual na evidência não houve
transição runtime. Não tratar `BloodInSight` como server-authoritative.

### Sample Vial setup and collection

**RUNTIME:** `/lgprobe yofadda blood-setup` colocou o bloco real
`batman_mod:red_blood_drop` numa cópia DEV em `-1137 83 -829`, sobre suporte
temporário criado apenas em ar em `-1137 82 -829`, e entregou o Sample Vial real
na mão. Nenhuma procedure MCreator foi chamada pelo Living Gotham.

Uma pessoa clicou legitimamente no suporte com o vial. Living Gotham observou
`RightClickBlock` server-side no tick `55137755`; no tick seguinte o item era
`batman_mod:red_blood_sample`, ainda sem tag. No tick `55137757` abriu
`batman_mod:sample_label` e a stack continha:

```nbt
{Age:"DEV",Health:"20",Height:"1.80",Name:"Living Gotham DEV Donor",Potions:"none"}
```

O usuário quebrou o suporte temporário depois da coleta; isso não afeta a
prova, ocorreu somente na cópia descartável e não tocou a baseline. A detecção
usa evento Forge + ids de registry + transição de inventário, sem chamar a
procedure geradora.

### DNA observability

**DECOMPILED:** não há `DNAAnalysisCompletedEvent`. O fluxo usa menus
`batman_mod:dna_scan_page` e `batman_mod:dna_potion_scans`, slot 0 e o campo
`vial_transfer`, que é preenchido e restaurado em ticks subsequentes. O observer
registra menu open/close, slots, mainhand e `vial_transfer` server-side.

**RUNTIME:** com a amostra real, uma pessoa percorreu a interface normal:
`login_page -> landing_page -> dna_scan_page -> dna_potion_scans`. No tick
`55139326`, a tela final abriu server-side com `vial_transfer` igual a
`batman_mod:red_blood_sample` e as cinco tags preservadas. Ao voltar, a amostra
continuou em `vial_transfer`; não apareceu novo estado distinto que signifique
“concluído”.

Classificação de conclusão: `[ ] PUBLIC_EVENT`, `[x] REGISTRY + STATE
TRANSITION`, `[x] MENU/INVENTORY HEURISTIC`, `[x] ACCESSIBLE_INTERNAL`,
`[x] runtime flow proven`. Não há base para adicionar hook/mixin nesta fase.

**BUG RUNTIME DO YO FADDA:** as telas funcionam, mas o cliente registrou
`FileNotFoundException` para
`batman_mod:textures/screens/dna_scan_page.png` e
`batman_mod:textures/screens/dna_potion_scans.png`. O problema pertence ao JAR
1.0.9 carregado; Living Gotham não deve copiar ou fabricar esses assets.
