# TaCZ 1.1.8-hotfix Integration Report

Data da validação: 2026-09-05

## Environment and identity

- arquivo local: `Instancia/mods/tacz-1.20.1-1.1.8-hotfix.jar`;
- SHA-256: `9ed8ada1283ed7a793a70cc1b51c4a340f367ce84707e1a7b8cf21ee3d288d77`;
- mod id/version: `tacz` / `1.1.8-hotfix`;
- Minecraft range: 1.20 até antes de 1.20.2; Forge mínimo 46;
- runtime validado: Minecraft 1.20.1, Forge 47.4.10, Java 17.0.19;
- upstream: `MCModderAnchor/TACZ`, tag 1.1.8-hotfix, commit
  `b43eb84c38e9768d8e73c8b14f0b845669704b38`;
- licença declarada: código GPL-3.0; assets CC BY-NC-ND 4.0.

O JAR permanece externo e ignorado. A inspeção de bytecode foi feita somente em
`/tmp`. TACZ:NPCs e TaCZ Hostiles não estão instalados e não são dependências do
Living Gotham.

## Registries and TimelessAPI

**PUBLIC_API.** `TimelessAPI` expõe índices common server-safe para guns, ammo e
attachments, além de índices client-only que não devem ser usados como
autoridade. No runtime real:

```text
common guns=54
ammo types=24
attachments=99
tacz:glock_17=true
```

O item físico usual é `tacz:modern_kinetic_gun`; a identidade lógica da arma
fica nos dados do ItemStack. Portanto objetivos e telemetria devem usar o
`ResourceLocation` retornado por `IGun`/eventos, não apenas o item Forge.

## Gun and item interfaces

**PUBLIC_API:**

- `IGun` lê gun id/display id, fire mode, munição corrente, bullet-in-barrel,
  attachments, suppressor/muzzle, RPM, heat e propriedades modificadas;
- `GunItemDataAccessor` e `GunItemBuilder` permitem criar/configurar stacks;
- `GunProperties` inclui `INACCURACY` e `DAMAGE`;
- `AttachmentCacheProperty` e eventos de propriedade permitem modificadores.

O probe constrói uma Glock 17 em SEMI com 17 cartuchos e bala na câmara. Não
fabrica NBT manualmente.

## Firing, reload and ammo state

**PUBLIC_API + FORGE_EVENT.** `IGunOperator` oferece draw, shoot, reload, bolt,
aim, fire-select e estado do operador. O shooter server valida draw/reload,
cooldown, sprint, ammo, bolt e heat antes de disparar.

Eventos públicos relevantes:

- `GunShootEvent` — cancelável, intenção validada;
- `GunFireEvent` — fogo efetivo;
- `GunReloadEvent` — recarga;
- `AmmoHitBlockEvent`;
- `EntityHurtByGunEvent.Pre/Post`;
- `EntityKillByGunEvent`;
- `EntityJoinLevelEvent` para `EntityKineticBullet`.

Alguns eventos aparecem em ambos os lados; Living Gotham filtra explicitamente
`LogicalSide.SERVER` para autoridade. Estado de munição é lido de `IGun` no
ItemStack server-side.

## Projectile, damage and weapon identification

`EntityKineticBullet` expõe gun id, display id e ammo id. Os eventos de dano
expõem bullet, alvo, atacante, gun id/display id, dano base, headshot e lado.
`EntityHurtByGunEvent.Pre` é cancelável e permite ajustar atacante/alvo/gun id,
damage source, dano base, multiplicador e headshot.

Classificação: **PUBLIC_API/FORGE_EVENT**, sem reflection ou mixin. Isso permite
detectar disparo/impacto e identificar a arma no server.

## Non-player entities

O TaCZ aplica mixin a `LivingEntity`, fazendo qualquer entidade viva implementar
`IGunOperator`. Uma entidade Living Gotham própria pode, em princípio:

1. receber ItemStack gun na mão;
2. obter `IGunOperator.fromLivingEntity(entity)`;
3. chamar `initialData()` e `draw(...)`;
4. mirar e chamar `shoot(pitchSupplier, yawSupplier)` no server;
5. reagir a `ShootResult` como `SUCCESS`, `NEED_BOLT`, `COOL_DOWN`.

**RUNTIME:** comprovado com uma `ArmorStand` vanilla como entidade técnica, sem
FakePlayer e sem classe NPC TaCZ.

## Minimal firing probe

`/lgprobe tacz fire`, protegido pelo gate DEV, criou:

- shooter `ArmorStand` com `tacz:glock_17`;
- alvo Zombie sem AI, a oito blocos;
- draw e disparo server-side depois de 20 ticks;
- limpeza das duas entidades após a observação.

Evidência real:

```text
GunShootEvent server, canceled=false
GunFireEvent server, canceled=false
EntityKineticBullet spawned, gun=tacz:glock_17, ammo=tacz:9mm
ShootResult=SUCCESS, ammo 17 -> 16
EntityHurtByGunEvent.Post, base_damage=10.5, headshot=true
probe cleanup fired=true
```

Isso satisfaz a prova `Living Gotham -> entidade de teste -> arma TaCZ ->
disparo real`. Não foi criada IA criminosa nem entidade final.

## NPC references

TACZ:NPCs não foi instalado como dependência. O projeto público MIT
`Corrinedev/tacz-npcs` foi usado somente como referência técnica:

- `TaczShootAttack` (SmartBrainLib) olha o alvo, exige
  `ModernKineticGunItem`, chama aim/shoot e trata `SUCCESS`/`NEED_BOLT`;
- `AbstractScavEntity` estende `PathfinderMob`, implementa/duplica componentes
  internos de shooter, persiste inventário e usa renderer/animações próprios.

O padrão de behavior é reutilizável conceitualmente, mas a duplicação de
internals é frágil e não deve ser copiada sem necessidade. “TaCZ Hostiles” não
foi identificado com segurança na instalação nem validado; nenhuma hipótese
sobre sua API foi transformada em dependência.

## PlayerAnimator and animation

TaCZ integra PlayerAnimator para jogador. Isso não fornece automaticamente
animação adequada para um mob customizado. O tiro server-side de LivingEntity
está provado; pose, recoil, recarga e renderer de NPC continuam uma frente
separada. PlayerAnimator permanece dependência do runtime exato, não uma prova
de solução visual para NPC.

## Suppressors

**PUBLIC_API, static inspection:** muzzle attachments e pares de propriedade
`SILENCE` permitem consultar supressão. Ainda não houve probe acústico/runtime
com suppressor. Não inferir que silenciar som também altera detecção por IA;
Living Gotham deve definir explicitamente sua própria semântica de audição.

## Accuracy and damage per NPC

Há dois caminhos públicos candidatos:

- propriedade do stack/evento de attachment para `GunProperties.INACCURACY` e
  `DAMAGE`;
- `EntityHurtByGunEvent.Pre` server-side para ajustar dano/multiplicador por
  entidade atiradora.

**INFERENCE:** isso permite controle por NPC sem editar TaCZ, mas a melhor
política e o efeito de precisão ainda precisam de probe comparativo. Manter
configuração no estado Living Gotham/stack, não em NBT inventado.

## Authority and lifecycle

- decisão de IA e chamada `shoot`: server thread;
- validação/consumo de ammo/projectile/damage: server authoritative;
- animação, câmera e feedback: client;
- observar somente eventos server para objetivos e persistência;
- registrar listeners TaCZ apenas quando `ModList.isLoaded("tacz")`.

## Integration classification

| Capability | Classification | Status |
|---|---|---|
| índices gun/ammo/attachment | PUBLIC_API | runtime proven |
| ler/criar gun stack | PUBLIC_API | runtime proven |
| entidade LivingEntity disparar | PUBLIC_API backed by TaCZ mixin | runtime proven |
| shoot/fire/reload events | FORGE_EVENT | shoot/fire runtime proven; reload static |
| projectile/hit/kill | PUBLIC_CLASS + FORGE_EVENT | projectile/hit runtime proven |
| suppressor | PUBLIC_API | static only |
| precisão/dano por NPC | PUBLIC_API/FORGE_EVENT | viable; tuning not proven |
| animação NPC | ACCESSIBLE_INTERNAL/custom work | not solved |
| reflection/mixin Living Gotham | not required | none used |

## Risks

- o contrato `IGunOperator` depende do mixin TaCZ em toda `LivingEntity`;
  revalidar a cada update;
- não usar indexes client-only no dedicated server;
- eventos duplicados client/server exigem filtro;
- precisão visual e lógica de dano devem continuar server authoritative;
- TACZ:NPCs acopla-se a internals e não deve virar dependência por conveniência;
- licenças de código/assets são distintas e impedem copiar assets livremente;
- animação de NPC continua não resolvida.

## Recommendation

**Manter TaCZ 1.1.8-hotfix como plataforma candidata e integração opcional.** A
API pública cobre registry, gun state, disparo por entidade, projectile e dano
sem reflection/mixin próprio. A próxima investigação deve construir uma
entidade DEV mínima com behavior de mirar/recarregar/disparar, medir precisão e
testar suppressor, ainda sem integrá-la ao Crime System.

## Phase 1.1 runtime closure

### Custom DEV entity and targeting

**RUNTIME.** `living_gotham:gun_test` / `LivingGothamGunTestEntity` é uma
entidade técnica própria, não um criminoso. Ela possui renderer temporário de
zombie, inventário Forge `ITEM_HANDLER` de um slot e estado persistente do
probe. Um controlador server-side fixa um alvo Zombie, recalcula yaw/pitch,
chama `initialData`, `draw`, `aim(true)` e `shoot` por `IGunOperator`.

Não foram usados FakePlayer, TACZ:NPCs, TaCZ Hostiles, reflection ou mixin do
Living Gotham. A aquisição é propositalmente mínima: alvo criado pelo probe,
sem cover, squad, patrulha ou qualquer IA criminal.

### Shooting and reload

**RUNTIME.** A Glock começou com duas munições no magazine e uma na câmara
(três tiros reais no total), enquanto o inventário próprio continha 30 unidades
reais de `tacz:9mm`:

```text
shot 1 -> SUCCESS
shot 2 -> SUCCESS
shot 3 -> SUCCESS, gun_ammo=0, barrel=false
shot 4 attempt -> NO_AMMO
GunReloadEvent server, canceled=false
EMPTY_RELOAD_FEEDING -> EMPTY_RELOAD_FINISHING -> NOT_RELOADING
reload elapsed=38 ticks
gun_ammo=16, reserve inventory=13
shot after reload -> SUCCESS, gun_ammo=15
```

A recarga consumiu 17 cartuchos do capability Forge real. Não foi usado dummy
ammo nem munição infinita. `GunFinishReloadEvent` não carrega a entidade, então
o boundary confiável para um NPC é `GunReloadEvent` +
`IGunOperator.getSynReloadState()` + estado `IGun` server-side.

### Persistence

**RUNTIME, SAVE/REOPEN.** Após save, fechamento e nova execução, o shooter de
recarga reapareceu com o mesmo UUID, profile `reload`, Glock, 15 no magazine,
round na câmara, 13 munições no inventário e Mirage instalado. O marcador de
ciclo completo também persistiu. Target e estado transitório de aquisição não
são persistidos pelo controlador, por design.

### Accuracy

**RUNTIME, 20 tiros por perfil.** `AttachmentPropertyEvent` substituiu via API
pública experimental todos os valores de `GunProperties.INACCURACY` e
`AIM_INACCURACY`. O desvio foi medido entre a direção ideal e
`EntityKineticBullet.getDeltaMovement()`; esses projéteis de medição foram
descartados antes de dano:

| Configuração | Média | Máximo |
|---|---:|---:|
| 0,05 | 0,0275° | 0,0496° |
| 12,0 | 6,4809° | 11,5402° |

**Conclusão:** precisão por entidade/stack é controlável e quantitativamente
previsível. O evento é público, mas marcado experimental pelo TaCZ; revalidar
na troca de versão.

### Damage tuning

**RUNTIME.** `EntityHurtByGunEvent.Pre` no server identificou a entidade e
alterou `baseAmount` por profile: 1,0 manteve 7,0; 0,5 produziu 3,5. Os eventos
Post correspondentes observaram 10,5 e 5,25 em headshots equivalentes, mantendo
exatamente a razão 2:1. O caminho é server-authoritative e não depende de NBT
de dano fabricado.

### Suppressor

**RUNTIME.** A mesma Glock foi comparada sem muzzle e com
`tacz:muzzle_silencer_mirage`:

```text
unsuppressed: muzzle=tacz:empty, SILENCE=(64,false)
suppressed:   muzzle=tacz:muzzle_silencer_mirage, SILENCE=(40,true)
```

Ambas emitiram `GunShootEvent`, `GunFireEvent`, projectile `tacz:9mm` e hit
normalmente. A mudança auditiva foi ouvida no cliente, mas não foi capturada por
medição de áudio; a identificação técnica é conclusiva. TaCZ não define a
semântica futura de percepção dos NPCs Living Gotham.

### Visual/animation status

**RUNTIME + implementation.** O renderer temporário exibe corpo/texture vanilla
de zombie e `ItemInHandLayer`; a entidade segura a arma e gira para o alvo. O
TaCZ executa tiro e recarga logicamente, mas PlayerAnimator cobre jogadores e
não fornece pose/recoil/reload correta a esse mob. Classificação:
`acceptable as temporary DEV visualization` e `requires custom renderer/custom
animation bridge` para produção. GeckoLib não foi adotado por conveniência.

### Revised classification

| Capability | Classification | Phase 1.1 status |
|---|---|---|
| custom entity shoot/aim | PUBLIC_API + TaCZ LivingEntity mixin | runtime proven |
| actual-ammo reload | PUBLIC_API + FORGE_EVENT + ITEM_HANDLER | runtime proven |
| gun/ammo/attachment persistence | vanilla entity NBT + public stack data | reopen proven |
| accuracy per stack | PUBLIC experimental event/property | quantitative runtime proven |
| damage per shooter | FORGE_EVENT | quantitative runtime proven |
| suppressor detection | PUBLIC_API | runtime proven |
| production mob animation | custom work | not solved |
