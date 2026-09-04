# Living Gotham — Contexto, Visão e Diretrizes do Projeto

> Documento-base para agentes de desenvolvimento.
>
> Este arquivo descreve a visão de longo prazo, as restrições técnicas e artísticas, as integrações planejadas e o processo inicial de investigação do projeto **Living Gotham**.
>
> Ele deve ser tratado como **fonte de contexto e intenção**, não como uma especificação rígida e imutável. Decisões técnicas devem ser validadas contra o código real, as versões reais dos mods e testes locais.

---

## 1. Visão do projeto

O projeto nasce de uma experiência simples: jogar Minecraft com o mod **Batman By Yo Fadda** em um grande mapa de cidade é extremamente divertido no começo, porque o mod entrega uma fantasia de Batman muito forte, mas depois surge uma pergunta:

> “Agora eu faço o quê?”

O objetivo de **Living Gotham** é responder essa pergunta de forma sistêmica.

Não queremos recriar Batman, seus equipamentos ou seus veículos. O mod do Yo Fadda continuará sendo a camada responsável pelo Batman.

Living Gotham será a camada responsável por fazer **Gotham precisar do Batman**.

O projeto deve transformar uma cidade de Minecraft em um mundo persistente com:

- crimes procedurais;
- patrulha;
- investigações;
- distritos;
- facções;
- progressão;
- reputação e medo;
- criminosos persistentes;
- GCPD;
- Arkham e Blackgate;
- narrativa autoral;
- narrativa emergente;
- planos de vilões;
- consequências;
- Batcomputer;
- Batcave funcional;
- integração profunda, quando tecnicamente possível, com o Batman By Yo Fadda.

Referências conceituais incluem elementos de:

- Batman: Arkham;
- Gotham Knights;
- jogos com “game director”;
- sistemas de simulação leve;
- investigação procedural;
- narrativa emergente.

A intenção não é copiar nenhum desses jogos, e sim aproveitar ideias sistêmicas compatíveis com Minecraft.

---

## 2. Princípio central de design

Evitar o padrão:

```text
vá até X
↓
mate 5 mobs
↓
receba recompensa
```

Preferir sistemas que produzam situações.

Exemplo:

```text
um distrito possui criminalidade elevada
↓
uma facção ganha influência
↓
o sistema cria um crime coerente naquela região
↓
GCPD registra a ocorrência
↓
Batman pode responder, ignorar, falhar ou chegar tarde
↓
o resultado altera o estado persistente da cidade
↓
esse novo estado influencia acontecimentos futuros
```

A cidade deve gradualmente produzir suas próprias histórias.

---

## 3. Objetivo emocional

Quando o jogador entrar no mundo, queremos que ele possa:

- consultar o Batcomputer;
- observar o estado da cidade;
- verificar crimes ativos;
- sair em patrulha;
- receber uma ocorrência;
- encontrar algo fora do esperado;
- investigar uma cena;
- seguir evidências;
- perceber que há um padrão maior;
- interromper um plano;
- capturar um criminoso recorrente;
- retornar à Batcave;
- perceber que Gotham mudou.

Living Gotham não deve parecer uma lista de tarefas.

A sensação desejada é:

> **Gotham continua acontecendo, e Batman decidiu interferir.**

---

# PARTE I — FUNDAÇÃO DO MUNDO

## 4. Mapa-base

O mapa utilizado inicialmente será **Los Perrito**, salvo decisão posterior baseada em testes reais.

A escolha é interessante porque o mapa oferece:

- grande escala;
- região urbana;
- diferentes bairros;
- vias;
- regiões fora do núcleo central;
- interiores em determinadas áreas;
- variedade suficiente para distritos e estilos diferentes de criminalidade.

O principal problema é artístico: Los Perrito possui identidade mais próxima de uma grande cidade inspirada em Los Angeles do que de Gotham.

A estratégia inicial NÃO é trocar o mapa inteiro.

A estratégia é:

> **usar Los Perrito como worldbase e gothamizar progressivamente as regiões que importam.**

Isso inclui:

- renomear distritos;
- adaptar áreas existentes;
- converter prédios;
- inserir marcos de Gotham;
- adicionar interiores;
- modificar iluminação e identidade;
- criar estruturas novas quando necessário.

---

## 5. Living Gotham não deve depender de um mapa específico

Não espalhar coordenadas hardcoded pelo código principal.

O core deve trabalhar com conceitos abstratos:

```text
District
CrimeLocation
PointOfInterest
Building
Interior
Rooftop
Alley
Street
Warehouse
PoliceStation
Hospital
Harbor
IndustrialArea
Hideout
```

O mundo concreto deve ser descrito por um índice/adaptador.

Exemplo conceitual:

```text
Living Gotham Core
    ↓
World Index
    ↓
Los Perrito / Gotham configuration
```

Uma possível organização:

```text
living-gotham/
├── mod/
├── tools/
│   ├── world-inspector/
│   ├── city-indexer/
│   ├── map-renderer/
│   └── structure-installer/
├── assets/
│   ├── blockbench/
│   ├── textures/
│   └── structures/
└── docs/
```

A arquitetura final deve ser escolhida após inspeção real do Forge 1.20.1 e dos requisitos.

---

## 6. O mapa NÃO deve ser catalogado manualmente pelo usuário

Los Perrito é grande demais para que o usuário:

- marque cada prédio;
- selecione cada telhado;
- cadastre cada beco;
- desenhe cada distrito;
- escolha milhares de pontos de crime.

Isso seria desperdício de tempo.

O trabalho deve ser automatizado o máximo possível.

Pipeline desejada:

```text
Minecraft World
↓
world/chunk scanner
↓
análise geométrica + análise de blocos
↓
detecção de edifícios, vias, água e áreas abertas
↓
representação simplificada
↓
renderização / mapas
↓
classificação semântica
↓
Living Gotham World Index
```

O scanner pode considerar:

- heightmaps;
- densidade de construções;
- connected components;
- footprints;
- altura;
- pisos estimados;
- telhados;
- ruas;
- calçadas;
- becos prováveis;
- estacionamentos;
- água;
- áreas abertas;
- interiores;
- portas;
- placas;
- textos;
- containers;
- iluminação;
- blocos característicos;
- proximidade de vias;
- proximidade de outros prédios.

Nem tudo será semanticamente inferível.

Um prédio genérico pode não fornecer evidência suficiente para saber se é:

- banco;
- escritório;
- residência;
- hospital.

Nesses casos o agente deve:

1. gerar candidatos;
2. produzir evidências e renders;
3. recomendar classificações;
4. pedir revisão humana somente nos casos importantes.

O usuário deve atuar principalmente como diretor criativo, não como operador GIS manual.

---

## 7. City Index

O projeto deve considerar a criação de um índice do mapa.

Exemplo:

```text
city-index/
├── world.json
├── buildings.json
├── roads.json
├── water.json
├── heightmap.png
├── density.png
├── districts/
├── candidates/
└── tiles/
```

Um prédio poderia ser representado como:

```text
building_00421
bounds: ...
height: 47
floors_estimate: 9
entrances: 2
roof_access: likely
adjacent_alley: true
near_major_road: true
signs:
  - "..."
```

Depois o agente poderia consultar o índice para encontrar candidatos.

Exemplo:

```text
Candidate: Wayne Manor

Reasons:
- isolated
- elevated
- large property
- road access
- cave potential
- reasonable distance from downtown
```

A ideia é que o agente não precise reprocessar a cidade inteira a cada pergunta.

---

## 8. Amulet

**Amulet / Amulet Core** deve ser avaliado como ferramenta de desenvolvimento, não como mod obrigatório do jogador.

Seu papel potencial:

- abrir saves Java offline;
- enumerar chunks;
- ler blocos;
- ler blockstates;
- ler block entities quando suportado;
- produzir índices;
- gerar heightmaps;
- analisar regiões;
- realizar transformações offline seguras;
- salvar cópias modificadas do mapa.

Pense no Amulet como:

> **editor de save Minecraft programável.**

Ele é especialmente útil para processamento em lote sem iniciar o Minecraft.

Exemplo conceitual:

```text
Los Perrito
↓
Amulet
↓
scan de chunks
↓
análise
↓
city-index.json
```

### Regra importante

Não assumir que Amulet é obrigatoriamente a solução final.

Antes de construir a pipeline sobre ele:

1. instalar em ambiente isolado;
2. abrir uma CÓPIA do Los Perrito;
3. listar dimensões;
4. ler chunks;
5. ler blocos conhecidos;
6. gerar um pequeno relatório;
7. escrever uma alteração mínima em uma cópia;
8. reabrir e validar o mundo;
9. avaliar performance e compatibilidade com 1.20.1.

Se houver problemas relevantes:

- avaliar outra biblioteca Anvil/NBT;
- implementar leitura específica;
- usar ferramentas complementares.

Nunca editar a única cópia limpa do mapa.

---

## 9. WorldEdit

WorldEdit será útil tanto como mod quanto como API Java.

O usuário NÃO deve ser obrigado a:

```text
//schem load ...
//paste
```

manualmente toda vez.

Queremos poder carregar e colar schematics programaticamente.

Exemplo:

```text
batcave.schem
↓
Living Gotham World Builder
↓
WorldEdit API
↓
rotate / transform
↓
paste
↓
save / validate
```

WorldEdit pode cumprir papéis como:

- importar estruturas;
- exportar estruturas;
- trabalhar com `.schem`;
- colar estruturas em posições definidas;
- transformar rotação/orientação;
- facilitar world-building reproduzível.

---

## 10. Build reproduzível do mundo

Idealmente o mundo final de desenvolvimento pode ser reconstruído a partir de:

```text
Los Perrito clean
+
Living Gotham World Patch
+
Living Gotham structures
=
Living Gotham development world
```

Evitar modificações irreproduzíveis.

Possível estrutura:

```text
reference/
└── worlds/
    └── LosPerrito-CLEAN/

runtime/
└── minecraft/
    └── saves/
        └── LivingGotham-DEV/

assets/
└── structures/
```

A cópia limpa nunca deve ser alterada.

---

# PARTE II — TRANSFORMAÇÃO DE LOS PERRITO EM GOTHAM

## 11. Modificação física do mapa

O worldbase não é imutável.

Living Gotham pode modificar fisicamente Los Perrito.

Exemplos:

- adaptar região industrial para Ace Chemicals;
- adaptar prédio institucional para GCPD;
- transformar parte do porto em Gotham Harbor;
- converter estruturas adequadas em hospitais/bancos/esconderijos;
- criar Arkham;
- criar Blackgate;
- criar Wayne Manor;
- criar Batcave;
- criar túneis;
- criar acessos secretos;
- adicionar interiores;
- adicionar cenas necessárias para narrativa.

### Regra artística

Sempre que possível:

> **adaptar estruturas boas existentes em vez de substituí-las por construções inferiores.**

A cidade já possui muito trabalho artístico.

O objetivo é preservar esse valor e adicionar identidade de Gotham.

---

## 12. Batcave

A Batcave é uma exceção importante.

Ela deve ser construída especificamente para nossos sistemas.

Possível organização:

```text
WAYNE MANOR
     │
Secret Access
     │
     ▼
BATCAVE
├── Batcomputer
│   ├── Gotham Map
│   ├── Dispatch
│   ├── Case Files
│   ├── Most Wanted
│   └── Forensic Database
├── Suit Vault
├── Evidence Lab
├── Workshop
├── Trophy Area
├── Vehicle Maintenance
├── Batmobile Platform
└── Tunnel → Gotham
```

A Batcave deve ser um hub funcional, não apenas uma construção decorativa.

---

# PARTE III — CREATE

## 13. Create fará parte da experiência

Avaliar e utilizar **Create para Forge 1.20.1**.

Não usar Create apenas como decoração.

Aplicações possíveis:

- entrada secreta da Wayne Manor;
- paredes móveis;
- portas;
- elevadores;
- plataformas;
- plataforma giratória de veículos;
- armazenamento do Batmobile;
- oficina;
- braços mecânicos;
- conveyors;
- displays;
- mecanismos de troféus;
- túneis;
- maquinário da Batcave.

Exemplo:

```text
Batmobile chega
↓
porta fecha
↓
plataforma desce
↓
plataforma gira
↓
veículo é armazenado
```

---

## 14. Integração Living Gotham ↔ Create

Quando necessário, isolar a integração:

```text
integration/
└── create/
```

Possibilidades futuras:

```text
case resolved
→ trigger
→ trophy mechanism

vehicle selected in Batcomputer
→ trigger
→ Batmobile platform rises

Batcave lockdown
→ doors/contraptions close
```

Antes de implementar, investigar APIs/eventos reais da versão utilizada.

---

## 15. Edição de estruturas com Create

Separar duas categorias.

### Estruturas estáticas

Podem ser montadas/offline quando seguro:

- paredes;
- cavernas;
- túneis;
- fundações;
- edifícios;
- blocos vanilla.

### Estruturas modded complexas

Para conteúdo que dependa de:

- block entities;
- inventories;
- kinetic networks;
- contraptions;
- filtros;
- links;
- NBT específico do Create;

preferir construir/colar/testar com uma instância Forge 1.20.1 contendo Create realmente carregado.

Não escrever NBT complexo do Create “no escuro” sem compreender seu formato.

---

# PARTE IV — BATMAN BY YO FADDA

## 16. Mod principal

Versão inicial de referência:

```text
batman_mod-1.0.9-forge-1.20.1.jar
```

Esse JAR deve ser disponibilizado localmente ao agente.

Living Gotham NÃO deve alterar o JAR original.

---

## 17. Filosofia de integração

O Batman By Yo Fadda continua responsável por:

- Batman;
- equipamentos;
- trajes;
- combate;
- veículos;
- animações já existentes;
- stealth;
- treinamento;
- forensic/detective systems;
- entidades do próprio mod.

Living Gotham fornece propósito e mundo.

Possível arquitetura:

```text
Living Gotham
├── core/
└── integration/
    └── yofadda/
```

Exemplo conceitual:

```text
YoFaddaIntegration
YoFaddaForensicsAdapter
YoFaddaEntityAdapter
YoFaddaVehicleAdapter
```

O restante do projeto não deve depender diretamente de internals do Yo Fadda.

Motivo:

Se uma futura versão mudar internals, queremos trocar o adapter e não quebrar o core inteiro.

---

## 18. Licença e assets

Não copiar para Living Gotham:

- texturas do Yo Fadda;
- modelos;
- sons;
- código;
- assets protegidos.

Interagir com conteúdo instalado e registries deve ser avaliado tecnicamente e juridicamente conforme necessário, mas redistribuição de assets não deve ocorrer sem autorização/licença apropriada.

---

## 19. Investigação obrigatória do JAR

Antes de assumir qualquer integração, decompilar e inspecionar o JAR localmente.

Confirmar:

- mod id;
- packages;
- registries;
- entidades;
- itens;
- capabilities/variáveis;
- código client-side;
- código server-side;
- eventos;
- persistência;
- acesso por outro mod;
- dependências;
- pontos frágeis.

Não confiar somente nas notas abaixo.

---

## 20. Pistas encontradas em inspeção preliminar

Em inspeção anterior da versão 1.0.9 foram observados elementos relacionados a:

```text
ForensicTrail
ForensicTrailData
Footprint
ForensicTrailClient
ForensicStainClient
ForensicTrailRenderer
```

Estados/variáveis semelhantes a:

```text
DetectiveMode
ForensicScanner
BloodInSight
```

Conteúdo relacionado a:

```text
RED_BLOOD_DROP
GREEN_BLOOD_DROP
RED_BLOOD_SLASH
GREEN_BLOOD_SLASH
SAMPLE_VIAL
RED_BLOOD_SAMPLE
GREEN_BLOOD_SAMPLE
```

Também apareceram procedimentos associados a:

- coleta de sangue;
- sample vial;
- DNA scan;
- deeper DNA scan.

O sistema `Footprint` aparentemente contém dados semelhantes a:

```text
x
y
z
yaw
size
kind
rightFoot
time
```

e tipos equivalentes ou próximos de:

```text
PLAYER
NEUTRAL
HOSTILE
TARGETING
```

Também foi observado um sistema de trilhas forenses persistentes e lógica ligada ao estado `ForensicScanner`.

Tudo isso precisa ser confirmado pela inspeção local.

---

## 21. Objetivo da integração forense

Não queremos uma investigação “fake” baseada apenas em texto.

Queremos usar mecânicas reais do mod quando possível.

Exemplo:

```text
CASE 014 — Silence in Burnley

Investigate the crime scene.
```

Batman chega.

O objetivo não aparece como um marcador gigante.

Ele ativa o Forensic Scanner real do Yo Fadda.

Living Gotham detecta o estado.

Uma evidência pode então ser analisada.

Batman encontra sangue.

Usa Sample Vial.

Living Gotham detecta a coleta.

```text
evidence.blood_sample = collected
```

O caso avança.

---

## 22. Pegadas e perseguição investigativa

Se o sistema real permitir:

```text
crime scene
↓
alley
↓
fire escape
↓
rooftop
↓
warehouse
↓
suspect
```

O suspeito deve poder deixar uma trilha real.

Batman ativa o scanner real.

Living Gotham associa a trilha ao caso.

O objetivo é responder empiricamente:

> Até onde Living Gotham consegue construir missões reais em cima do Forensic/Detective Mode sem modificar o mod original?

---

## 23. Relatório técnico obrigatório sobre integração

Antes de depender do sistema forense, produzir um documento semelhante a:

```text
Yo Fadda 1.0.9 Integration Report

Forensic Scanner
Status:
Method:
Server authoritative?
Public access?
Risk of version breakage:

Footprints
Status:
Persistence:
Can another mod create them?
Can another mod inspect them?
Can we associate them with a case?

Blood
Status:
Can Living Gotham spawn compatible evidence?
Can collection be detected?

Sample Vial
Status:
Can item use be observed through Forge events?
Need direct dependency?

Random Crimes
Status:
Can disable?
Can listen?
Can reuse entities?

Vehicles
Status:
Can detect/use unlock state?
Can Living Gotham react to vehicle selection?

Overall integration confidence:
```

Não presumir.

Basear no JAR real.

---

## 24. Random Crimes do Yo Fadda

Em inspeção anterior apareceram conceitos semelhantes a:

```text
RandomCrimeON
RandomCrimeCondition
CrimeMagnet
```

Investigar isso.

Pode ser possível:

- ouvir;
- reutilizar;
- desligar;
- substituir;
- aproveitar entidades.

Mas NÃO construir Living Gotham em cima disso antes de entender as limitações.

O nosso sistema precisa compreender:

- distrito;
- localização;
- atores;
- objetivo;
- estado;
- facção;
- consequências;
- persistência;
- resultado.

---

# PARTE V — SISTEMAS DE GAMEPLAY

## 25. Distritos

Cada distrito pode possuir estado persistente.

Exemplo conceitual:

```text
District
- id
- displayName
- region
- crimeRate
- policePresence
- fear
- publicSafety
- dominantFaction
- factionInfluence
- tags
```

Não assumir esse schema como final.

---

## 26. Crime Locations

O sistema deve conhecer locais adequados para eventos.

Exemplo:

```text
CrimeLocation
- id
- region/position
- district
- tags
```

Tags:

```text
alley
rooftop
street
warehouse
bank
apartment
industrial
parking
subway
harbor
```

O diretor escolhe locais compatíveis com o crime.

---

## 27. Crimes procedurais

Exemplos futuros:

- assalto;
- roubo;
- invasão;
- sequestro;
- reféns;
- tráfico;
- briga de gangues;
- ataque contra policiais;
- roubo de veículo;
- perseguição;
- atentado;
- bomba;
- assassinato;
- cena de crime;
- desaparecimento.

Não implementar tudo no início.

Criar arquitetura extensível.

Possíveis abstrações:

```text
CrimeDefinition
CrimeInstance
CrimeActor
CrimeObjective
CrimeState
CrimeOutcome
```

Os nomes finais devem surgir da implementação real.

---

## 28. Dispatch

Algum sistema deve representar o GCPD Dispatch.

Exemplo:

```text
GCPD DISPATCH

ARMED ROBBERY

Tricorner
Suspects: 4–6
Possible hostages: 2
Priority: High
```

Futuras interfaces:

- HUD;
- Batcomputer;
- mapa;
- waypoint;
- áudio.

---

## 29. Gotham Director

O projeto deve eventualmente possuir um diretor de jogo.

Conceito:

```text
GothamDirector
```

Ele observa o estado da cidade e seleciona acontecimentos coerentes.

Não deve ser RNG puro.

Exemplo:

```text
Falcone perde influência
↓
porto fica vulnerável
↓
Penguin recebe peso maior para expansão
↓
contrabando aumenta
↓
GCPD detecta movimentação
↓
ocorrências relacionadas aparecem
```

---

## 30. Simulação abstrata

Não manter centenas de NPCs ativos em chunks distantes.

Grande parte da cidade pode ser simulada numericamente.

```text
simulation layer
↓
event materialization
↓
Minecraft entities
```

Uma guerra de território pode existir como estado até Batman se aproximar de um evento relevante.

Isso é essencial para performance.

---

## 31. Facções

Possíveis facções:

- GCPD;
- Falcone;
- Maroni;
- Penguin;
- Joker;
- League of Assassins;
- criminosos independentes.

Possíveis atributos:

- influência;
- territórios;
- hostilidade;
- recursos;
- membros;
- líderes;
- operações.

Implementar somente quando necessário.

---

## 32. Fear / Reputation

Criminosos poderão reagir ao Batman com base em:

- reputação;
- medo;
- contexto;
- superioridade numérica;
- estado da gangue;
- histórico.

Possíveis reações futuras:

- fugir;
- se render;
- procurar aliados;
- entrar em pânico;
- tentar emboscar;
- atacar impulsivamente.

---

## 33. Progressão

Possível estrutura narrativa:

```text
Year One
↓
basic equipment
↓
advanced gadgets
↓
Batcycle
↓
advanced suits
↓
Batmobile
↓
Bat-Family
```

Mas NÃO bloquear recursos do Yo Fadda arbitrariamente antes de entender como eles funcionam.

A progressão deve parecer uma consequência da campanha e dos sistemas.

---

## 34. Most Wanted

Criminosos menores podem virar personagens persistentes.

Exemplo:

```text
Vincent Moretti
Falcone Crime Family
Wanted Level II
Crimes: 3
```

Se escapar:

```text
Vincent "Vinnie" Moretti
Falcone Lieutenant
Wanted Level IV
Bodyguards: 3
```

O mundo deve lembrar dele.

---

## 35. Captura não é morte

Batman não deve simplesmente matar criminosos humanos.

Distinguir estados como:

```text
alive
incapacitated
arrested
escaped
dead
```

Não tratar `entity removed` como sucesso.

Morte pode futuramente:

- falhar missão;
- alterar narrativa;
- gerar consequência.

---

## 36. Arkham e Blackgate

Prisioneiros podem existir como estado persistente.

Futuros eventos:

- transferências;
- interrogatórios;
- fugas;
- motins;
- Arkham breakout;
- Blackgate breakout.

---

# PARTE VI — NARRATIVA

## 37. Duas camadas narrativas

### Narrativa autoral

Casos e arcos escritos manualmente.

### Narrativa emergente

Acontecimentos derivados dos sistemas.

Exemplo:

```text
Batman prende vários membros de Falcone
↓
Falcone perde influência
↓
Penguin ocupa o porto
↓
violência aumenta
↓
GCPD desloca policiais
↓
outro distrito fica menos protegido
↓
crimes menores aumentam
```

---

## 38. Villain Schemes

Visão futura:

```text
VillainScheme

stage 1: preparation
stage 2: acquisition
stage 3: production
stage 4: deployment
stage 5: attack
```

Exemplo Scarecrow:

```text
chemical theft
↓
strange victims
↓
abandoned laboratory
↓
fear toxin production
↓
distribution
↓
citywide attack
```

O jogador pode detectar pistas cedo.

Se ignorar, o plano escala.

Não implementar na primeira versão.

Apenas manter a arquitetura compatível.

---

# PARTE VII — PERSISTÊNCIA

## 39. O mundo é de longo prazo

Não queremos reset ao reiniciar Minecraft.

Avaliar mecanismos apropriados do Forge 1.20.1:

- SavedData;
- capabilities;
- attachments, se aplicável à versão;
- outras APIs adequadas.

Futuros dados:

```text
GothamState
DistrictState
FactionState
CrimeHistory
ActiveCrimes
CriminalRecords
MostWanted
CaseFiles
PlayerProgression
VillainSchemes
```

Não criar tudo de imediato.

---

# PARTE VIII — ARTE E ASSETS

## 40. Regra de qualidade

Living Gotham NÃO deve parecer:

> “mod genérico feito por IA”.

Preferir:

- menos assets;
- maior qualidade;
- identidade coerente;
- acabamento;
- consistência com Minecraft e com o ecossistema visual do Yo Fadda.

Não gerar dezenas de assets automaticamente apenas porque é possível.

---

## 41. Assets simples vs personagens complexos

Assets simples são bons candidatos a produção interna:

- evidências;
- documentos;
- rádios;
- maletas;
- dispositivos;
- computadores;
- caixas;
- objetos de cena;
- pequenas props.

Personagens complexos exigem padrão muito mais alto.

Não prometer qualidade profissional de personagem sem validação visual adequada.

---

## 42. Blockbench

Blockbench Desktop será usado como principal ferramenta de modelagem para assets Minecraft/GeckoLib.

Manter fontes:

```text
assets/blockbench/
```

Separar exports do mod:

```text
mod/src/main/resources/assets/living_gotham/
├── geo/
├── textures/
└── animations/
```

Arquivos `.bbmodel` devem ser considerados fonte quando existirem.

---

## 43. Blockbench MCP

MCP inicialmente escolhido para teste:

`https://github.com/jasonjgardner/blockbench-mcp-plugin`

Não criar um plugin/MCP próprio antes de testar o existente.

Motivos:

- já possui modelagem;
- bones/grupos;
- UV;
- texturas;
- materiais;
- animações;
- keyframes;
- câmera;
- screenshots;
- integração ampla.

Há relatos/áreas experimentais e possíveis bugs.

Portanto a estratégia preferida é:

```text
upstream Jason
↓
testes locais
↓
se suficiente: usar
↓
se faltar algo: fork Living Gotham
↓
patch/extend
```

Evitar reescrever toda a infraestrutura MCP.

Também pode ser avaliado como referência:

`https://github.com/sosadly/blockbench-mcp`

Ele parece mais direcionado a Minecraft/GeckoLib, porém é muito mais novo e menos maduro.

Não adotar apenas pelo README.

Comparar com uma prova real.

---

## 44. Teste obrigatório do MCP

Antes de decidir a arquitetura artística:

1. instalar/configurar o MCP do Jason;
2. conectar Codex ↔ Blockbench;
3. criar projeto de teste descartável;
4. criar um grupo/bone;
5. criar cubos;
6. editar transformações;
7. criar/aplicar textura simples;
8. manipular UV quando disponível;
9. criar uma animação curta;
10. adicionar keyframes;
11. controlar câmera;
12. capturar screenshot;
13. salvar o projeto;
14. reabrir;
15. validar persistência;
16. verificar erros de schema/parâmetros;
17. documentar operações estáveis e instáveis.

Se houver falha:

- identificar se é MCP;
- plugin;
- versão do Blockbench;
- cliente Codex;
- schema;
- transporte.

Só então decidir por fork ou alternativa.

---

## 45. Loop artístico desejado

```text
design requirement
↓
Blockbench project
↓
model
↓
UV
↓
texture
↓
animation
↓
viewport screenshot
↓
agent visual review
↓
iteration
↓
export
↓
Minecraft test
```

Não considerar asset pronto apenas porque exportou.

A validação visual faz parte da definição de pronto.

---

# PARTE IX — AMBIENTE

## 46. Sistema operacional

Ambiente principal recomendado:

> **Linux nativo**

No caso atual, CachyOS é adequado.

Evitar WSL para o pipeline principal se não houver necessidade.

Razões:

- Gradle/Java;
- Python;
- Node;
- MCP;
- Git;
- scripts;
- Codex;
- Minecraft Java;
- Blockbench;
- world-processing.

Tudo pode operar no mesmo filesystem e ambiente.

---

## 47. Ferramentas esperadas

Disponibilizar ou permitir ao agente instalar/configurar:

- **JDK 17** para todo o toolchain Forge 1.20.1;
- Git;
- Python;
- ambiente virtual Python;
- Node.js/npm;
- Codex;
- Blockbench Desktop;
- Blockbench MCP;
- Minecraft;
- Forge 1.20.1;
- Batman By Yo Fadda 1.0.9;
- Create compatível com Forge 1.20.1;
- WorldEdit compatível com Forge 1.20.1;
- dependências exigidas pelos mods;
- GeckoLib/Blockbench tooling quando necessário.

Amulet deve ser instalado como dependência de desenvolvimento somente após teste de compatibilidade.

### 47.1 Java 17 é obrigatório para Forge 1.20.1

O sistema pode possuir uma versão mais nova de Java como padrão global. Isso NÃO significa que ela deve ser usada no Living Gotham.

Neste ambiente podem coexistir, por exemplo:

```text
Java 26 → Java padrão do sistema
Java 17 → JDK do Living Gotham / Forge 1.20.1
```

O agente NÃO deve presumir que escolherá Java 17 automaticamente.

Antes de executar Gradle/Forge:

1. localizar o JDK 17 instalado;
2. em Arch/CachyOS, consultar quando útil:

```bash
archlinux-java status
```

3. resolver o `JAVA_HOME` do JDK 17;
4. executar Gradle com Java 17 explicitamente;
5. verificar:

```bash
./gradlew -version
```

e confirmar que a JVM utilizada pelo Gradle é **17.x**.

Não alterar o Java padrão global do sistema sem necessidade.

Preferir configuração específica do projeto/processo, por exemplo:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk PATH=/usr/lib/jvm/java-17-openjdk/bin:$PATH ./gradlew runClient
```

O path acima é apenas um exemplo comum em Arch e deve ser descoberto no ambiente real, não hardcoded cegamente.

Quando o projeto Forge for criado, usar também Java Toolchains quando compatível:

```groovy
java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}
```

Importante: a toolchain de compilação NÃO substitui a necessidade de garantir que a própria versão do Gradle/ForgeGradle seja iniciada por uma JVM compatível.

Se for útil, criar um script local/reutilizável, como:

```text
scripts/with-java17
```

que:

- descubra ou aceite `JAVA_HOME`;
- valide Java 17;
- execute o comando recebido com o ambiente correto.

Não versionar um path absoluto específico da máquina se puder ser evitado.

Registrar no `AGENTS.md` que **todo comando Forge/Gradle deste projeto deve usar JDK 17**.

### 47.2 Responsabilidade do agente pela configuração de MCPs

MCPs NÃO devem ser considerados automaticamente instalados apenas porque aparecem neste documento.

Durante a Fase 0, o agente deve:

1. verificar quais MCPs já estão configurados no Codex;
2. testar se o Blockbench MCP já está acessível;
3. se não estiver, clonar/instalar/configurar o MCP indicado, desde que tenha permissão para isso;
4. instalar dependências Node necessárias em ambiente apropriado;
5. configurar o cliente Codex/MCP de acordo com a documentação real da versão instalada;
6. instalar/carregar o plugin correspondente no Blockbench quando necessário;
7. iniciar os processos/servers exigidos;
8. validar a conexão ponta a ponta;
9. documentar os passos reproduzíveis.

MCP inicialmente escolhido:

```text
https://github.com/jasonjgardner/blockbench-mcp-plugin
```

Se alguma etapa exigir interação manual inevitável na UI do Blockbench ou uma permissão que o agente não possui, ele deve:

- parar somente naquela etapa;
- informar exatamente qual ação manual é necessária;
- não fingir que o MCP está conectado;
- continuar automaticamente após a ação quando possível.

Não pedir ao usuário para pré-instalar MCPs sem antes tentar a configuração que pode ser feita via terminal.

Não criar fork próprio antes dos testes do upstream demonstrarem uma necessidade concreta.

---

## 48. Workspace sugerido

```text
LivingGothamWorkspace/
│
├── living-gotham/                 # Git
│   ├── docs/
│   ├── mod/
│   ├── tools/
│   └── assets/
│
├── reference/                     # gitignored
│   ├── mods/
│   │   ├── batman_mod-1.0.9-forge-1.20.1.jar
│   │   ├── create-....jar
│   │   └── ...
│   └── worlds/
│       └── LosPerrito-CLEAN/
│
├── runtime/                       # gitignored
│   └── minecraft/
│       ├── mods/
│       ├── config/
│       ├── logs/
│       └── saves/
│           └── LivingGotham-DEV/
│
└── external/
    └── blockbench-mcp/            # ou localização apropriada
```

A estrutura real pode variar.

---

## 49. Git e dados externos

Não versionar no repositório público:

- JAR do Yo Fadda;
- outros JARs sem necessidade/licença;
- Los Perrito completo;
- saves gigantes;
- `.mca`;
- runtime;
- arquivos protegidos.

Versionar:

- código Living Gotham;
- ferramentas próprias;
- documentação;
- schemas;
- configs próprias;
- `.bbmodel` autorais;
- texturas autorais;
- estruturas autorais quando permitido;
- testes.

---

# PARTE X — DEBUG E TESTES

## 50. Debug tools

Desde cedo criar ferramentas de desenvolvimento.

Exemplos conceituais:

```text
/lg debug district
/lg debug crime
/lg crime spawn robbery
/lg crime complete
/lg district info
/lg director tick
```

Possíveis visualizações:

- bounds;
- crime locations;
- POIs;
- pontos de spawn;
- estado de crimes;
- ids de edifícios;
- debug do director.

Não depender de RNG e espera para testar.

---

## 51. Testes de lógica pura

Sistemas como:

- seleção ponderada de eventos;
- state machines;
- influência de facção;
- resultados;
- progressão;
- decisões do director;

devem ser testados fora do Minecraft sempre que possível.

---

# PARTE XI — DATA-DRIVEN

## 52. Conteúdo configurável

Quando fizer sentido, permitir definitions via JSON/datapack.

Exemplo:

```text
living_gotham/crimes/armed_robbery.json
```

Mas NÃO forçar data-driven para tudo se isso piorar arquitetura ou depuração.

---

# PARTE XII — FASES

## 53. Não implementar tudo de uma vez

O projeto deve evoluir incrementalmente.

---

## 54. Fase 0 — Investigação e workspace

Antes de gameplay real:

1. inspecionar repositório;
2. validar ambiente;
3. localizar JARs;
4. investigar Yo Fadda;
5. testar Blockbench MCP;
6. testar Amulet;
7. validar WorldEdit/API;
8. validar Create;
9. documentar resultados;
10. propor arquitetura mínima.

---

## 55. v0.1 — Patrol

Objetivo:

> provar que a cidade consegue produzir um crime contextualizado e persistente.

Possíveis componentes:

```text
District System
Crime Location Registry
Crime Definitions
Crime Instance
Crime State Machine
basic Gotham Director
Dispatch
Persistence
Yo Fadda Integration
Debug Tools
```

Começar com UM crime se necessário.

Exemplo:

```text
Street Robbery
```

Um crime excelente vale mais que 20 incompletos.

---

## 56. Primeira prova de conceito investigativa

Após a infraestrutura mínima:

```text
Crime Scene
↓
activate real Yo Fadda forensic scanner
↓
detect evidence
↓
collect evidence
↓
follow trail
↓
find suspect
↓
capture
↓
case resolved
```

Essa prova de conceito serve para validar a integração real com o modo forense.

---

# PARTE XIII — QUALIDADE DE CÓDIGO

## 57. Princípios

Priorizar:

- baixo acoplamento;
- separação de responsabilidades;
- código legível;
- interfaces somente quando úteis;
- baixo acoplamento ao Yo Fadda;
- testes de lógica pura;
- documentação;
- commits pequenos;
- ferramentas de debug;
- evitar abstração prematura.

Não criar dezenas de interfaces vazias para prever um futuro que ainda não existe.

---

## 58. Regra de progresso

Não tentar impressionar produzindo muito código rapidamente.

Preferir:

> **um sistema pequeno, testado e coerente**

a:

> **uma arquitetura enorme parcialmente funcional.**

---

# PARTE XIV — TAREFAS INICIAIS DO AGENTE

## 59. O que fazer primeiro

Ao receber este documento:

1. ler todo o contexto;
2. inspecionar a raiz do projeto;
3. identificar ferramentas instaladas;
4. identificar mods e mundo disponíveis;
5. confirmar que o JDK 17 está instalado e determinar seu `JAVA_HOME`;
6. garantir que qualquer execução Forge/Gradle utilize explicitamente Java 17, mesmo que o Java global seja uma versão mais nova;
7. verificar/configurar os MCPs necessários em vez de presumir que já estão instalados;
8. criar um `AGENTS.md` conciso com regras operacionais derivadas deste documento;
9. não duplicar este arquivo inteiro dentro de `AGENTS.md`;
10. usar `AGENTS.md` para instruções de trabalho diárias;
11. manter este documento como visão/fonte de contexto.

---

## 60. O AGENTS.md deve conter

No mínimo:

- objetivo do projeto;
- stack alvo;
- versão Minecraft/Forge;
- regra de não alterar JAR do Yo Fadda;
- regra de preservar Los Perrito clean;
- regra de trabalhar em cópias;
- política de assets;
- política de Git;
- convenções de pasta;
- workflow de teste;
- prioridade de investigação antes de implementação;
- regra de isolamento de integrações externas;
- regra de não criar sistemas massivos prematuramente;
- comandos úteis descobertos;
- como rodar testes/build/dev client;
- obrigação explícita de usar JDK 17 para Forge/Gradle e como o ambiente local faz isso;
- como verificar `./gradlew -version`;
- como lidar com Blockbench MCP;
- localização/configuração do MCP efetivamente validada no ambiente;
- como lidar com world tools.

O `AGENTS.md` deve ser operacional e curto o suficiente para ser realmente útil.

---

# PARTE XV — TESTES INICIAIS OBRIGATÓRIOS

## 61. Teste Blockbench MCP

Executar uma prova descartável e produzir:

```text
docs/research/BLOCKBENCH_MCP_REPORT.md
```

Relatar:

- versão Blockbench;
- versão do MCP;
- método de conexão;
- tools detectadas;
- criação de bone/grupo;
- criação de cubo;
- edição;
- UV;
- textura;
- animação;
- keyframes;
- câmera;
- screenshot;
- save/reopen;
- erros;
- limitações;
- estabilidade;
- recomendação:
  - usar upstream;
  - fork;
  - testar alternativa;
  - criar bridge própria somente se justificado.

Não criar fork antes de existir uma necessidade concreta.

---

## 62. Teste Amulet

Executar em cópia descartável do mundo.

Produzir:

```text
docs/research/AMULET_REPORT.md
```

Relatar:

- versão Python;
- versão Amulet;
- instalação;
- abertura do save;
- dimensões;
- número aproximado de chunks acessíveis;
- leitura de chunk;
- leitura de bloco;
- leitura de blockstate;
- leitura de block entities;
- performance básica;
- geração de pequeno heightmap ou resumo;
- escrita mínima em CÓPIA;
- reabertura do mundo;
- compatibilidade;
- risco;
- recomendação.

Nunca escrever no `LosPerrito-CLEAN`.

---

## 63. Teste adicional desejável: world scanner mínimo

Se Amulet funcionar:

criar uma primeira ferramenta pequena, sem tentar resolver toda a cidade.

Exemplo:

```text
tools/world-inspector/
```

Ela deve ser capaz de:

- abrir uma cópia do mundo;
- escolher uma região pequena;
- listar chunks;
- gerar heightmap simples;
- produzir estatísticas de blocos;
- exportar JSON.

Isso serve como prova da futura pipeline.

---

# PARTE XVI — NÃO FAZER NESTA ETAPA

## 64. Não começar ainda por

- 20 crimes;
- todas as facções;
- progressão completa;
- Villain Schemes;
- interface final;
- modelos complexos;
- Gotham inteira;
- Batcave final;
- dezenas de NPCs;
- sistema completo de narrativa.

Primeiro queremos reduzir incerteza técnica.

---

# PARTE XVII — DEFINIÇÃO DE SUCESSO DA FASE 0

A Fase 0 estará bem encaminhada quando tivermos:

- `AGENTS.md`;
- ambiente reproduzível;
- Yo Fadda investigado;
- relatório de integração;
- Blockbench MCP testado;
- Amulet testado;
- estratégia WorldEdit definida;
- Create validado no runtime;
- cópia limpa do Los Perrito preservada;
- cópia DEV funcional;
- documentação dos riscos;
- arquitetura mínima proposta.

Somente depois disso devemos iniciar a implementação séria da v0.1.

---

# PARTE XVIII — PRINCÍPIO FINAL

Living Gotham não existe para colocar mais coisas no Minecraft.

Existe para transformar:

> “tenho um mod incrível do Batman e uma cidade enorme”

em:

> **“eu tenho uma Gotham que continua viva e que me dá motivos para ser o Batman.”**
