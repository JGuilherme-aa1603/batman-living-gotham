# Create 6.0.8 Integration Report

Data da validação: 2026-09-05

## Environment

- Create runtime: 6.0.8, Git hash
  `1a1a9a2819b4f89f78caec41b55ed8cb222fa24b`;
- JAR runtime SHA-256:
  `6fbb910c367dbce8e4fc7e5bf64b6edd4de980906ed00af8e47e4af843c0d9b0`;
- build Maven equivalente: `create-1.20.1:6.0.8-289:slim`;
- Forge 47.4.10 e Java 17.0.19;
- Flywheel 1.0.5, Ponder 1.0.91, Vanillin 1.0.0 e Registrate 1.3.3.

O hash do JAR de distribuição instalado coincide com o build oficial 289. O
JAR instalado embute versões de produção de Ponder/Flywheel e não é apropriado
como artefato de desenvolvimento Mojmap. Por isso o Gradle usa o artefato
`slim` oficial equivalente e mantém o JAR instalado apenas como referência de
runtime, sem copiá-lo para o repositório.

## Public API probe

O adapter `CreateIntegration` usa:

- `CreateBuiltInRegistries.CONTRAPTION_TYPE`;
- `BlockStressValues.getImpact`;
- `BlockMovementChecks.isMovementAllowed`.

Classificação: **PUBLIC_API**, leitura server-side, sem reflection/mixin. No
runtime real o resultado foi:

```text
Create 6.0.8-289
public contraption types=9
cobblestone stress impact=0.0
block below movement allowed=true
```

O log também confirmou a inicialização do Create no mesmo cliente que Living
Gotham, Batman Mod, WorldEdit e TaCZ.

## Userdev issue resolved

Refmaps de produção do ecossistema Create usam nomes SRG, enquanto o userdev
ForgeGradle usa Mojmap. Sem remapeamento, o cliente falhou ao aplicar mixins.
Os runs agora configuram:

```text
mixin.env.remapRefMap=true
mixin.env.refMapRemappingFile=build/createSrgToMcp/output.srg
```

Isso permitiu carga completa do runtime. A diferença textual entre `6.0.8` no
save e `6.0.8-289` no ambiente de desenvolvimento causa aviso do Forge, embora
o Git hash seja idêntico.

## Boundaries and risks

- APIs de registry/stress/movement estão disponíveis e são adequadas para
  consultas pequenas.
- Block entities, redes cinéticas, trains e contraptions têm lifecycle próprio;
  não fabricar NBT offline.
- Paste WorldEdit de blocos não equivale a montar uma contraption válida.
- Operações futuras devem acontecer com Create carregado, no server thread, e
  ser verificadas após save/reopen.
- A linha Minecraft 1.20.1 deve ficar pinada e coberta por probes de regressão.

## Conclusion

**Boundary pública mínima comprovada.** Create pode permanecer no runtime e ser
consultado por API pública. Ainda não há evidência para automatizar máquinas ou
contraptions, e nenhuma Batcave foi iniciada.
