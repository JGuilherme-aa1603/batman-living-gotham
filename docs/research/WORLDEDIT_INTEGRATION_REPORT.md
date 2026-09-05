# WorldEdit 7.2.15 Integration Report

Data da validação: 2026-09-05

## Environment

- Minecraft 1.20.1;
- Forge 47.4.10;
- WorldEdit Forge 7.2.15+6463-5ca4dff;
- JAR SHA-256:
  `17db6b3e94f52d25426684663e1e1846823cbb7907f1c365ac329e5bc7bfaf2c`;
- Java 17.0.19;
- teste executado exclusivamente em `runtime/forge/saves/Los Perrito DEV`.

## API boundary

O adapter usa somente tipos públicos:

- `ForgeAdapter.adapt(ServerLevel)`;
- `BlockArrayClipboard` e `ClipboardHolder`;
- `WorldEdit.getInstance().newEditSession(...)`;
- `createPaste(...).to(...).build()`;
- `Operations.complete(...)`;
- `EditSession.getBlockChangeCount()`.

Classificação: **PUBLIC_API**, server authoritative, executada no server thread
por comando Brigadier. Não foi necessário reflection, mixin, comando `//paste`
ou simulação de jogador WorldEdit.

## Runtime probe

`/lgprobe worldedit paste` construiu um clipboard 2x2x2 em memória com ouro,
concreto preto e vidro e o colou três blocos à frente do jogador. O log real
registrou:

```text
WorldEdit API pasted DEV clipboard at -1141, 78, -836 | changed=8
```

O gate `DevWorldSafety` resolveu o diretório real do mundo, confirmou que o
nome contém `DEV` e que o path não está sob `LosPerrito2.0`. Fora desse gate o
probe falha fechado. A baseline permaneceu com hashes inalterados.

`WorldEdit.getVersion()` retornou `(unknown)` neste userdev; a versão exata foi
obtida do `ModList` e do log do loader. Isso é uma limitação cosmética da API,
não uma falha do paste.

## Próximo fluxo de schematic

O caminho público continua viável para a futura pipeline:

```text
ClipboardFormats.findByFile
-> ClipboardReader.read
-> ClipboardHolder + AffineTransform
-> EditSession / Operations.complete
-> ClipboardWriter Sponge .schem
```

Antes de automatizar estruturas grandes, adicionar limites de volume, preview,
undo/rollback e validação de block entities. Pasting de blocos não prova que
contraptions Create complexas estão válidas.

## Conclusion

**Integração programática comprovada.** Living Gotham consegue realizar um
paste sem ação manual do usuário e sem internals do WorldEdit. Manter WorldEdit
como dependência de runtime para a futura automação estrutural.
