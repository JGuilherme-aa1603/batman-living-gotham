# Living Gotham — regras operacionais

## Missão e fontes de verdade

Living Gotham é a camada sistêmica que faz Gotham precisar do Batman: cidade,
crime, investigação, resposta e consequências persistentes. O mod **Batman By
Yo Fadda** continua sendo a camada Batman (personagem, equipamentos e conteúdo
existente); Living Gotham integra-se a ele sem substituí-lo.

- Leia `docs/LIVING_GOTHAM_CONTEXT.md` por inteiro antes de decisões de
  arquitetura. Ele contém visão e contexto; este arquivo contém regras diárias.
- Alvo técnico: **Minecraft Forge 1.20.1** e **JDK 17**.
- Não implemente gameplay amplo de uma vez. Entregue fatias pequenas,
  observáveis, testáveis e reversíveis.
- Valide mods, APIs, registries, eventos, NBT e comportamento no runtime real.
  Nome de classe, documentação ou hipótese não prova uma integração.

## Dados locais e proteção do mapa

- `Instancia/` é a instância local do Minecraft usada em desenvolvimento e
  testes. Hoje é um symlink para a instância Prism Launcher; nunca dependa do
  destino absoluto em código versionado.
- `LosPerrito2.0/` é o worldbase original externo. **Nunca escreva, salve,
  converta, atualize, abra com API que reescreva `session.lock`, mova ou renomeie
  esse diretório.**
- Qualquer ferramenta que possa escrever segue obrigatoriamente:
  `LosPerrito2.0 -> cópia descartável -> experimento`.
- Confira que a cópia não resolve para o original antes de escrever. Faça
  alterações mínimas, feche, reabra e confirme a persistência e legibilidade.
- Amulet 1.9.45 abre mundos Java para leitura/escrita e reescreve
  `session.lock`. Use `tools/world-inspector` somente numa cópia; a ferramenta
  bloqueia explicitamente caminhos sob `LosPerrito2.0/`.

## Integração com mods

- Não modifique, repacote, decompile para redistribuição nem versione o JAR
  original de Batman By Yo Fadda. Trate-o como binário proprietário externo.
- Isole a integração Yo Fadda atrás de adapters/capabilities próprios. O projeto
  deve falhar de modo controlado quando um símbolo ou comportamento mudar.
- Antes de depender de uma API, teste a versão exata carregada em `Instancia/`.
- Para Create, prefira APIs públicas e execução com o mod carregado. Não fabrique
  NBT complexo de block entities ou contraptions offline.
- Para WorldEdit, planeje automação programática de `.schem`: localizar codec,
  ler clipboard, aplicar `AffineTransform`, colar com `EditSession`/`Operation` e
  salvar com writer Sponge. O usuário não deve colar schematics manualmente.
- Não inicie Crime System, Gotham Director, facções, Batcomputer, progressão,
  Most Wanted, Villain Schemes, Batcave ou assets finais sem aprovação da etapa.

## Java 17 e Forge

O Java global pode ser mais novo. Não o altere. Execute Forge/Gradle por:

```bash
scripts/with-java17 java -version
scripts/with-java17 ./gradlew -version
scripts/with-java17 ./gradlew build
scripts/with-java17 ./gradlew test
scripts/with-java17 ./gradlew runClient
```

O wrapper Gradle ainda não existe neste bootstrap; quando for criado,
`./gradlew -version` deve mostrar `JVM: 17.x`. Configure também Java Toolchains
17 no build. `JAVA17_HOME` pode sobrescrever a descoberta sem gravar path local:

```bash
JAVA17_HOME=/caminho/do/jdk17 scripts/with-java17 ./gradlew build
```

## Git e dependências locais

- A raiz deste arquivo é a única raiz Git. Não crie repositórios em subpastas.
- Antes de adicionar arquivos, valide `.gitignore` com `git status --ignored` e
  `git check-ignore -v`.
- Nunca versione `Instancia/`, `LosPerrito2.0/`, saves, regiões, `*.mca`, `*.jar`,
  ambientes virtuais, `node_modules/`, logs ou checkouts em `external/`.
- Versione código e documentação autorais: `docs/`, `tools/`, `scripts/`, fontes,
  configs próprias e fontes Blockbench `.bbmodel` produzidas pelo projeto.
- Prefira `git add` seletivo. Não publique remotos nem altere identidade Git
  global sem autorização.

## Assets, licenças e Blockbench

- Registre origem, autor, licença e permissões de todo asset externo. Não copie
  assets do Yo Fadda ou do worldbase para o repositório sem direito explícito.
- Mantenha fontes autorais editáveis (`.bbmodel`) e exportações reproduzíveis;
  não confunda o smoke test descartável com asset final.
- Upstream MCP validado: `jasonjgardner/blockbench-mcp-plugin` v1.6.1 no endpoint
  `http://localhost:3000/bb-mcp`. O checkout local fica ignorado em `external/`.
- Confirme conexão, estado e screenshot antes de tarefas de asset. Sempre inclua
  ao menos um ciclo `screenshot -> inspeção visual -> correção -> screenshot`.
- Salvar não basta: reabra e verifique geometria, UV/textura e animações. Trate
  ferramentas experimentais e `risky_eval` como risco explícito.

## World tools e comandos úteis

Crie uma cópia descartável e inspecione somente uma região limitada:

```bash
uv venv --python 3.12 .tooling/amulet-venv
uv pip install --python .tooling/amulet-venv/bin/python \
  -r tools/world-inspector/requirements.txt
.tooling/amulet-venv/bin/python tools/world-inspector/world_inspector.py \
  /tmp/los-perrito-working-copy \
  --dimension minecraft:overworld \
  --min-chunk-x 0 --max-chunk-x 1 \
  --min-chunk-z 0 --max-chunk-z 1 \
  --output-dir /tmp/living-gotham-inspection
```

Diagnóstico local útil:

```bash
java -version
javac -version
archlinux-java status
codex mcp list
git status --ignored
```

## Relatórios de pesquisa

- `docs/research/BLOCKBENCH_MCP_REPORT.md`
- `docs/research/AMULET_REPORT.md`
- `docs/research/RUNTIME_INTEGRATION_REPORT.md`

Atualize esses relatórios quando versões, runtime ou evidência real mudarem.
