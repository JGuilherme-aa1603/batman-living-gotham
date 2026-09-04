# Runtime Forge, Yo Fadda, WorldEdit e Create

Data da inspeção: 2026-09-04

## Instância encontrada

`Instancia/` é um symlink para a pasta `minecraft` da instância Prism Launcher
`1.20.1`. O pack declara:

- Minecraft 1.20.1;
- Forge 47.4.10;
- Java Microsoft 17.0.15 configurado especificamente no Prism.

O `latest.log` confirma a inicialização de ModLauncher/Forge com Java 17.0.15 e
o encerramento normal do cliente. Isso valida carga até o ambiente de jogo, não
constitui teste funcional completo de cada mecânica.

## Mods relevantes

- Batman By Yo Fadda: `batman_mod` 1.0.9 para Forge 1.20.1.
- Create: 6.0.8, commit runtime
  `1a1a9a2819b4f89f78caec41b55ed8cb222fa24b`.
- WorldEdit Forge: 7.2.15+6463-5ca4dff.
- GeckoLib: 4.8.4.
- Curios: 5.14.1.
- Player Animator: 1.0.2-rc1+1.20.

Os JARs permanecem exclusivamente na instância local e estão ignorados pelo
Git.

## Batman By Yo Fadda

O JAR é proprietário (`All Rights Reserved`) e não deve ser alterado ou
redistribuído. A inspeção de inventário encontrou classes de crime e forense,
incluindo `RandomCrime`, `CrimeMagnet`, `ForensicTrail`, `ForensicTrailData` e
`Footprint`. Esses nomes são pistas para adapters, não contratos de API.

O log real registra muitos avisos de sons ausentes, modelos resolvidos
incorretamente sob o namespace `minecraft`, textura ausente e referências
inválidas na tag `battag:flight_damagable`. A integração deve isolar falhas e
testar cada capacidade em jogo; não assumir que a carga sem crash significa que
todo conteúdo está funcional.

## WorldEdit

A versão instalada é compatível com Forge 1.20/1.20.1 e o log confirma:
`WorldEdit for Forge ... is loaded`, seguido do registro de comandos.

Fluxo programático recomendado, baseado na
[API oficial de clipboards](https://worldedit.enginehub.org/en/latest/api/examples/clipboard/):

1. localizar o formato com `ClipboardFormats.findByFile`;
2. abrir um `ClipboardReader` e chamar `read()`;
3. envolver em `ClipboardHolder`;
4. aplicar `AffineTransform` (por exemplo, `rotateY`);
5. criar paste com `createPaste(editSession).to(...).build()`;
6. completar via `Operations.complete` dentro de `EditSession`;
7. salvar com writer, preferindo Sponge `.schem`.

Evitar pacotes de comandos e internals de plataforma. A futura integração deve
rodar no server thread/contexto Forge correto, adaptar mundo/jogador por APIs de
plataforma, limitar operações e reportar falhas. Não haverá paste manual como
etapa obrigatória do usuário.

## Create

O log confirma `Create 6.0.8 initializing`, registro de Ponder, carregamento de
77 shaders Flywheel, receitas e datapack dinâmico. O JAR declara Forge >=47.1.3,
Minecraft 1.20.1, Flywheel 1.x e Ponder >=0.8; as dependências jar-in-jar foram
descobertas pelo loader.

Há pacotes públicos `com.simibubi.create.api`, APIs de schematic e classes de
contraption no artefato. Ainda assim, 1.20.1 é uma linha antiga e a documentação
do Create informa que essa versão não recebe novas correções. Pinagem exata e
testes de regressão são obrigatórios.

Block entities, inventories, kinetic networks, trains e contraptions carregam
estado coordenado pelo mod. Para Batcave e estruturas futuras, prefira criação
com Create carregado, placement hooks e APIs públicas. Não escreva NBT offline
nem trate um paste de blocos como contraption válida sem ciclo salvar/reabrir e
teste real no cliente/servidor.
