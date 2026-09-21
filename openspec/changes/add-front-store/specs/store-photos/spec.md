# Spec Delta: Store Photos

## Purpose

Fornece interface especializada para gerenciamento de fotos dentro do contexto de edicao de produtos: validacao de URL, upload, reordenacao e remocao com feedback visual.

## ADDED Requirements

### Requirement: Componente de lista editavel de fotos

O sistema SHALL exibir as fotos de um produto em uma lista editavel dentro do drawer de edicao, com reordenacao visual e remocao.

#### Scenario: Exibir fotos existentes
- **WHEN** owner abre drawer de edicao de produto
- **THEN** interface mostra secao "Fotos" com lista de fotos atuais
- **AND** cada foto exibe: preview (thumbnail), URL, position number, botoes de reordenacao (up/down) e remocao
- **AND** abaixo da lista, botao "Adicionar Foto"

#### Scenario: Nenhuma foto
- **WHEN** produto nao tem fotos e owner abre drawer de edicao
- **THEN** interface mostra secao "Fotos" vazia com mensagem "Nenhuma foto adicionada"
- **AND** botao "Adicionar Foto" aparece destacado

#### Scenario: Thumbnail dinamica
- **WHEN** interface carrega foto com URL valida
- **THEN** thumbnail carrega imagem do servidor via tag `<img src="...">`
- **AND** se imagem falhar em carregar (404, CORS), mostra icone de erro

### Requirement: Validacao de URL de foto

O sistema SHALL validar URLs de fotos no client antes de enviar ao servidor.

#### Scenario: URL obrigatoria https
- **WHEN** owner tenta adicionar foto com URL nao-https (ex: "http://cdn.../photo.png")
- **THEN** interface mostra erro inline "URL deve iniciar com https://"
- **AND** botao "Adicionar" fica desabilitado

#### Scenario: Validar formato de URL
- **WHEN** owner digita texto que nao eh URL valida (ex: "isso nao e url")
- **THEN** interface mostra erro "URL invalida"
- **AND** botao "Adicionar" desabilitado

#### Scenario: URL valida
- **WHEN** owner digita URL valida (ex: "https://cdn.loja.dev/p/118/0.webp")
- **THEN** interface remove erro e habilita botao "Adicionar"

### Requirement: Adicionar foto com validacao server

O sistema SHALL fazer POST /products/{id}/photos com validacao no servidor de formato de imagem, dimensoes e tamanho.

#### Scenario: Foto adicionada com sucesso
- **WHEN** owner digita URL valida e clica "Adicionar"
- **THEN** interface faz POST /products/{id}/photos com body `{ "photoUrl": "...", "position": 2 }`
- **AND** resposta 201 retorna nova foto
- **AND** foto aparece na lista com thumbnail carregando
- **AND** mostra toast "Foto adicionada"
- **AND** campo de URL fica limpo pronto para proxima foto

#### Scenario: Imagem com dimensoes insuficientes
- **WHEN** URL aponta para imagem menores que 400x400
- **THEN** interface captura 422 com code `INVALID_IMAGE_DIMENSIONS`
- **AND** mostra toast "Imagem deve ter no minimo 400x400 pixels"

#### Scenario: Arquivo muito grande
- **WHEN** URL aponta para arquivo > 10 MB
- **THEN** interface captura 422 com code similar
- **AND** mostra toast "Arquivo maximo 10 MB"

#### Scenario: MIME type invalido
- **WHEN** URL nao aponta para imagem (ex: documento .pdf)
- **THEN** interface captura 422 com code `INVALID_MIME_TYPE`
- **AND** mostra toast "Formato deve ser JPEG, PNG ou WebP"

#### Scenario: URL com 404
- **WHEN** URL nao existe ou retorna 404
- **THEN** interface captura 422 com mensagem de server
- **AND** mostra toast "URL nao encontrada ou inacessivel"

### Requirement: Reordenar fotos

O sistema SHALL permitir reordenacao de fotos via arrows ou drag-and-drop, com PUT /products/{id}/photos/order.

#### Scenario: Mover foto para cima
- **WHEN** foto esta em posicao 2, owner clica seta "cima"
- **THEN** interface move foto para posicao 1 localmente
- **AND** foto que estava em 1 desce para 2
- **AND** positions no array sao atualizadas

#### Scenario: Mover foto para baixo
- **WHEN** foto esta em posicao 1 (primeira), owner clica seta "baixo"
- **THEN** interface move foto para posicao 2
- **AND** foto que estava em 2 sobe para 1

#### Scenario: Seta desabilitada em extremos
- **WHEN** foto esta em posicao 1 (primeira)
- **THEN** botao de seta "cima" esta desabilitado/oculto

#### Scenario: Seta desabilitada na ultima
- **WHEN** foto esta em posicao N (ultima)
- **THEN** botao de seta "baixo" esta desabilitado/oculto

#### Scenario: Salvar reordenacao
- **WHEN** owner reordena fotos e clica "Salvar" no drawer
- **THEN** interface faz PUT /products/{id}/photos/order com body:
  ```json
  {
    "order": [
      { "id": 904, "position": 0 },
      { "id": 901, "position": 1 },
      { "id": 903, "position": 2 }
    ]
  }
  ```
- **AND** resposta 200 confirma reordenacao
- **AND** mostra toast "Fotos reordenadas"

#### Scenario: Reordenacao invalida (missing positions)
- **WHEN** array de reordenacao nao cobre todas as fotos do produto
- **THEN** interface captura 422 com code `INCOMPLETE_PHOTO_ORDER`
- **AND** mostra toast "Erro ao reordenar fotos, tente novamente"

#### Scenario: Posicoes duplicadas
- **WHEN** array de reordenacao tem duas fotos com position=1
- **THEN** interface captura 422 com code `DUPLICATE_POSITION`
- **AND** mostra toast "Posicoes duplicadas detectadas"

### Requirement: Remover foto

O sistema SHALL permitir remocao de foto individual com DELETE /products/{id}/photos/{photoId}.

#### Scenario: Remover foto com confirmacao
- **WHEN** owner clica botao "Remover" em uma foto
- **THEN** interface mostra confirmacao inline ou modal: "Tem certeza que deseja remover essa foto?"
- **AND** owner clica "Confirmar"
- **THEN** interface faz DELETE /products/{id}/photos/{photoId}
- **AND** resposta 204 remove foto
- **AND** foto desaparece da lista
- **AND** mostra toast "Foto removida"

#### Scenario: Cancelar remocao
- **WHEN** owner clica "Remover", aparece confirmacao, owner clica "Cancelar"
- **THEN** interface nao faz DELETE
- **AND** foto permanece na lista

#### Scenario: Nao remover unica foto de produto ativo
- **WHEN** produto ativo (nao removido) tem 1 foto e owner tenta DELETE
- **THEN** interface captura 409 com code `MUST_HAVE_ONE_PHOTO`
- **AND** mostra toast "Produto ativo precisa de pelo menos uma foto"
- **AND** foto nao eh removida

#### Scenario: Remover foto de produto removido
- **WHEN** produto com status "removido" (deleted_at preenchido) tem N fotos
- **THEN** owner pode remover qualquer foto, sem restricao de minima
- **AND** interface faz DELETE normalmente, resposta 204

#### Scenario: Foto de outro produto
- **WHEN** owner tenta DELETE /products/118/photos/999 onde foto 999 pertence a outro produto
- **THEN** interface captura 404
- **AND** mostra toast "Foto nao encontrada ou nao pertence a esse produto"

### Requirement: Loading states e feedback visual

O sistema SHALL exibir estados de loading, erro e sucesso durante operacoes de foto.

#### Scenario: Loading ao adicionar foto
- **WHEN** owner clica "Adicionar" e POST esta em voo
- **THEN** interface mostra spinner ou skeleton no campo de URL
- **AND** botao "Adicionar" fica desabilitado

#### Scenario: Loading ao reordenar
- **WHEN** owner clica "Salvar" e PUT esta em voo
- **THEN** lista de fotos fica com opacidade reduzida ou spinner sobreposto

#### Scenario: Loading ao remover
- **WHEN** owner clica "Remover" e DELETE esta em voo
- **THEN** foto fica com opacidade reduzida

#### Scenario: Tooltip em photo URL
- **WHEN** usuario passa mouse em URL da foto
- **THEN** tooltip mostra URL completo se truncado na tela

### Requirement: Limite de fotos

O sistema SHALL impedir adicionar mais de 10 fotos por produto.

#### Scenario: Atingir limite de 10
- **WHEN** produto ja tem 9 fotos e owner clica "Adicionar Foto"
- **THEN** interface permite adicionar a 10a foto normalmente

#### Scenario: Bloquear 11a foto
- **WHEN** produto ja tem 10 fotos e owner tenta adicionar a 11a
- **THEN** interface captura 409 com code `PHOTO_LIMIT_EXCEEDED`
- **AND** mostra toast "Limite de 10 fotos por produto atingido"
- **AND** campo de adicionar foto fica desabilitado ate remover alguma

#### Scenario: Desbloquear apos remocao
- **WHEN** produto tem 10 fotos, owner remove 1, fica com 9
- **THEN** campo de adicionar foto volta a ficar habilitado
- **AND** pode adicionar proxima foto

### Requirement: Integracao com drawer de edicao

O sistema SHALL manter fotos sincronizadas entre componente de foto e estado do drawer.

#### Scenario: Cancelar edicao nao salva alteracoes de foto
- **WHEN** owner abre drawer, adiciona foto mas clica "Cancelar" sem clicar "Salvar"
- **THEN** interface descarta mudancas de fotos nao commitadas
- **AND** drawer fecha

#### Scenario: Salvar drawer persiste mudancas de foto
- **WHEN** owner modifica fotos (adicionar, reordenar, remover) e clica "Salvar" no drawer
- **THEN** todas as operacoes de foto sao commitadas via API
- **AND** cache de lista de produtos eh invalidado
- **AND** drawer fecha com toast de sucesso

#### Scenario: Erro em fotos nao fecha drawer
- **WHEN** owner tenta adicionar/remover/reordenar fotos e retorna erro
- **THEN** interface mostra toast de erro
- **AND** drawer permanece aberto para corrigir ou tentar novamente
