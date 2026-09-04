# Blockbench MCP — relatório de validação

Data do teste: 2026-09-04

## Versões e instalação

- Blockbench Desktop: **5.1.6** (`blockbench-bin 5.1.6-1`).
- MCP upstream: **v1.6.1**, commit
  [`6b069e3`](https://github.com/jasonjgardner/blockbench-mcp-plugin/commit/6b069e308fdfc9b0a1c15bc924ca78150815f143).
- Checkout local ignorado: `external/blockbench-mcp/`.
- Runtime de build: Bun 1.3.14, Node 26.8.1 e npm 12.0.2.
- Instalação: `bun install --frozen-lockfile --force` e `bun run build`.
- Bundle validado: `dist/mcp.js`, 568.282 bytes, SHA-256 iniciando em
  `1825bc42`.
- Plugin carregado manualmente em Blockbench por **File > Plugins > Load Plugin
  from File**, apontando para `dist/mcp.js`. As permissões `net`, `process` e
  `fs` foram aceitas para o plugin.
- Codex configurado com `codex mcp add blockbench --url
  http://localhost:3000/bb-mcp`. `codex mcp list` mostra o servidor habilitado.

Tentar iniciar `blockbench dist/mcp.js` fez o Desktop interpretar JavaScript
como modelo e exibir “File format not supported”. Isso é comportamento de
instalação/CLI, não falha da conexão; o bundle precisa ser carregado como plugin.

## Conexão e superfície disponível

O endpoint `/health` respondeu `status: ok`. Um processo Codex novo descobriu o
servidor e tentou `get_project_info`; sem projeto aberto, recebeu corretamente o
erro de estado do Blockbench. A sessão de Codex que antecede a configuração não
adquire ferramentas MCP dinamicamente e precisa ser reiniciada.

O servidor anunciou **94 tools**. As categorias observadas incluem projeto,
outliner, cubos, texturas/pintura, UV, materiais, meshes, armatures, animação,
câmera/screenshot, histórico, exportação e automação de UI. A documentação do
upstream menciona um total maior porque ferramentas condicionais de Hytale não
foram registradas neste ambiente.

## Prova funcional descartável

Projeto: `Living_Gotham_MCP_Smoke`, formato Bedrock Entity. O `.bbmodel` e as
capturas ficaram em `/tmp`; não são assets do projeto.

| Operação | Resultado | Evidência principal |
|---|---|---|
| Codex ↔ MCP ↔ Blockbench | sucesso | endpoint saudável, 94 tools e chamadas reais |
| Consultar estado | sucesso | projeto/nome/formato consultados |
| Criar projeto | sucesso | projeto Bedrock criado |
| Criar grupo/bone | sucesso | `signal_root` |
| Criar cubos | sucesso com ressalva | pedestal, mast e lamp; primeira chamada sem textura falhou |
| Mover/rotacionar/redimensionar | sucesso | lamp movida/rotacionada; pedestal e lamp redimensionados |
| Criar/aplicar textura | sucesso | `signal_palette` 16×16 nos três cubos |
| Pintura e UV | sucesso | retângulo pintado, auto-UV e offset aplicados |
| Criar animação | sucesso com ressalva | 1 animação, duração 1 s, loop |
| Adicionar keyframes | mutação parcial | quinto keyframe persistiu apesar de a tool reportar erro |
| Controlar câmera | sucesso | posição, alvo e perspectiva definidos |
| Screenshot de viewport | sucesso | PNG real retornado pelo MCP |
| Iteração visual | sucesso | primeira imagem inspecionada, lamp corrigida, segunda imagem inspecionada |
| Salvar | sucesso | `/tmp/living-gotham-blockbench-smoke.bbmodel`, 4.492 bytes |
| Reabrir e confirmar | sucesso com `risky_eval` | 3 cubos, 1 textura, 1 animação e 5 keyframes persistiram |

Na primeira captura, o topo da luminária estava largo e inclinado demais para o
poste. A alteração reduziu as dimensões de `[-5,10,-2]..[5,14,2]` para
`[-4,9.5,-1.5]..[4,13,1.5]` e a rotação de -22,5° para -10°. A segunda captura
mostrou o topo mais compacto e melhor apoiado. Logo, screenshots e iteração
visual não são apenas capacidades declaradas: funcionaram ponta a ponta.

## Falhas e limitações observadas

1. `place_cube` com `faces: true` e nenhuma textura existente falha com `No
   texture found for "undefined"`; a combinação aceita pelo schema não tem
   default seguro.
2. `create_animation` recebeu `animation.signal_pulse`, mas criou
   `animation.animation.signal_pulse`, duplicando o prefixo.
3. `manage_keyframes` por nome não achou a animação prefixada. Por UUID, inseriu
   o keyframe e depois retornou `Cannot read properties of null (reading
   'setLength')`. A operação não é atômica: erro não significa ausência de
   mutação. Sempre consultar o estado após falha.
4. Não há tool dedicada para abrir/importar `.bbmodel` entre as 94 anunciadas.
   A prova de reload usou `Codecs.project.load` por `risky_eval`; isso não é uma
   interface desejável para produção.
5. O cliente MCP genérico encerra com código zero quando a resposta contém
   `isError`; automações precisam checar o campo, não só o exit code.
6. A checagem final de `/health` mostrou **23 sessões ativas** depois de várias
   chamadas curtas, apesar de o cliente chamar `close()`. Elas devem expirar pelo
   timeout de 30 minutos, mas o acúmulo indica limpeza tardia ou encerramento
   incompleto. Reutilizar uma sessão longa e investigar o lifecycle/DELETE do
   transporte antes de automação intensiva.

Não ocorreu queda do servidor durante a prova. Exportação e captura foram
estáveis, mas animação e importação ainda exigem guardrails.

## Recomendação

```text
RECOMMENDATION:
[ ] use upstream
[x] upstream + small local patches
[ ] maintain Living Gotham fork
[ ] evaluate alternative MCP
[ ] custom bridge justified
```

Continuar pinando o upstream e propor correções pequenas para criação de cubos,
normalização de nomes, atomicidade de keyframes e import/reopen. Ainda não há
evidência que justifique fork Living Gotham ou bridge própria. O repositório
alternativo não foi adotado.
