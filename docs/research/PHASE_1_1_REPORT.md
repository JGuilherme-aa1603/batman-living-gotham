# Living Gotham — Phase 1.1 Report

Data: 2026-09-05

## Environment

- Minecraft 1.20.1, Forge 47.4.10, ForgeGradle 6.x, Gradle 8.8;
- Gradle JVM: OpenJDK 17.0.19 em `/usr/lib/jvm/java-17-openjdk`;
- runtime: Living Gotham 0.0.1-probe, Yo Fadda 1.0.9, TaCZ 1.1.8-hotfix,
  Create 6.0.8-289 e WorldEdit 7.2.15+6463-5ca4dff;
- escrita somente em `runtime/forge/saves/Los Perrito Phase 1.1 DEV`, criada
  por `scripts/create-dev-world` a partir da baseline;
- baseline permaneceu fechada, com hashes de `level.dat` e `session.lock`
  inalterados.

## Yo Fadda

### Scanner OFF → ON

- **RUNTIME PASS**, server-side, usando o fluxo normal: Batsuit clássico
  completo + keybind Z mantida por ao menos 10 ticks;
- `DetectiveMode false -> true` apareceu no tick `55135020` e
  `ForensicScanner false -> true` no tick `55135029`, com `buttonTicks=10`;
- um segundo Z produziu `ForensicScanner true -> false` no tick `55135124`;
- o observer CLIENT/SERVER registrou ciclos posteriores equivalentes. A
  capability nunca foi forçada ou escrita pelo Living Gotham.

### BloodInSight

**NOT CONFIRMED em runtime.** A inspeção revelou que a procedure é chamada pelo
overlay cliente Beyond e aparentemente não sincroniza `LocalPlayer` de volta ao
server. Deve ser tratado como candidato client-local até prova contrária.

### Sample Vial

**RUNTIME PASS.** Bloco de sangue e vial reais foram preparados por command
DEV sem chamar procedures. A coleta normal por clique ocorreu no tick
`55137755`; no tick seguinte `sample_vial` virou
`batman_mod:red_blood_sample`. No tick posterior abriu `sample_label` e foram
observadas as tags `Name`, `Age`, `Health`, `Height` e `Potions`. Living Gotham
detectou por `RightClickBlock` + registry id + transição de ItemStack. O suporte
temporário quebrado depois pelo usuário existia apenas na cópia DEV.

### DNA

**RUNTIME PASS ATÉ O LIMITE OBSERVÁVEL.** A amostra real percorreu
`login_page -> landing_page -> dna_scan_page -> dna_potion_scans` pela UI
normal. A tela final abriu no tick `55139326` com `vial_transfer` contendo a
amostra e o NBT intacto. Não há evento semântico confiável de conclusão nem
estado final distinto: a classificação é `REGISTRY + STATE TRANSITION` e
`MENU/INVENTORY HEURISTIC`. Não foi adicionado mixin.

As texturas `dna_scan_page.png` e `dna_potion_scans.png` referenciadas pelas
telas não existem no JAR Yo Fadda 1.0.9 e geraram `FileNotFoundException`; as
telas ainda abriram. É bug upstream, não regressão Living Gotham.

## TaCZ

### DEV entity

**RUNTIME.** `living_gotham:gun_test` é uma entidade técnica própria com alvo
fixo, face-target, `IGunOperator`, Glock e capability de munição. Não usa
FakePlayer, TACZ:NPCs, Hostiles, reflection ou mixin próprio.

### Shooting and reload

**RUNTIME.** Três tiros consumiram magazine/câmara até zero; a tentativa
seguinte retornou `NO_AMMO`; `GunReloadEvent` ocorreu; a recarga levou 38 ticks,
consumiu reserva real 30→13, carregou 16 e permitiu novo tiro 16→15.

### Persistence

**RUNTIME REOPEN.** Entidade, Glock, ammo 15, round na câmara, reserva 13,
profile e attachment Mirage persistiram. Target e objetivo transitório não são
persistidos por design.

### Accuracy

**RUNTIME, 20+20 tiros:** inaccuracy 0,05 resultou em desvio médio 0,0275°;
12,0 resultou em 6,4809°. Controle público por stack funcionou.

### Damage

**RUNTIME:** evento Pre ajustou 7,0→7,0 e 7,0→3,5 para multiplicadores 1,0 e
0,5; Post manteve a razão 2:1.

### Suppressor

**RUNTIME:** sem muzzle foi `SILENCE=(64,false)`; Mirage foi `(40,true)`. Shoot,
fire, projectile e damage continuaram normais em ambos.

### Visual status

Visualização DEV aceitável: modelo/texture zombie e arma na mão. Não há
aim/recoil/reload de qualidade; produção exige renderer/animation bridge próprio.

## WorldEdit

### Paste persistence after reopen

**RUNTIME PASS.** Padrão 2x2x2 em `-1137 84 -829` correspondeu imediatamente e
após save/close/reopen. Depois da limpeza, nova reabertura confirmou oito blocos
de ar. Todo o protocolo passou pelo gate DEV.

## Regressions

- `scripts/with-java17 ./gradlew clean build`: PASS com JVM 17.0.19, 11 tasks;
- dois testes JUnit de lógica pura TaCZ: 2 executados, 0 falhas/erros;
- runtime conjunto carregou os cinco mods em todas as execuções;
- Create continuou respondendo ao probe público: 9 tipos de contraption;
- nenhum crash ou regressão Living Gotham observada;
- warnings existentes continuam: `scoreboard.dat` com JSON inválido na cópia,
  sons/advancements/texturas DNA Yo Fadda e ausência de `libflite`; não foram
  causados por esta mudança;
- descartar bullets imediatamente no probe de precisão gera warning cosmético
  `Fetching packet for removed entity`; não afeta save, mas deve ser refinado.

## Remaining unknowns

- BloodInSight real e confirmação de qual lado é autoritativo;
- um evento semântico estável de DNA não existe; a heurística precisa ser
  revalidada a cada versão Yo Fadda;
- animação de produção da entidade armada;
- medição automatizada da diferença acústica do suppressor.

## Gate for next phase

- [x] Scanner ON real observado server-side
- [x] Scanner OFF novamente observado
- [ ] BloodInSight real observado
- [x] Sample Vial real coletado e detectado
- [x] DNA testado até o limite observável
- [x] DEV TaCZ entity dispara
- [x] DEV TaCZ entity recarrega com ammo real
- [x] gun/ammo/attachment persistem após reopen
- [x] precisão controlável comprovada
- [x] damage tuning comprovado
- [x] suppressor detectável comprovado
- [x] WorldEdit paste e cleanup persistem após reopen
- [x] runtime integrado sem regressão nova observada

Recomendação: considerar fechados os quatro probes prioritários. Manter
`BloodInSight` como pendência explícita, pois sua autoridade parece client-local
e não deve bloquear o próximo planejamento. Não iniciar City Index, Crime
System, Districts ou Gotham Director automaticamente.
