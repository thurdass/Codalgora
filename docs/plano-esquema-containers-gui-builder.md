# Plano de evolução do esquema do GUI Builder

Este documento propõe como adicionar contêineres horizontais, verticais e em grade sem quebrar formulários existentes. É um plano: **o aplicativo ainda grava e lê somente o esquema 1**, e contêineres aninhados não foram implementados.

## Situação atual

O esquema 1 usa `schemaVersion: 1` e uma lista plana `elements`. Cada elemento é um controle com `id`, `type`, `name`, `text`, geometria, aparência opcional e vínculos opcionais com algoritmo e variável. A organização do formulário (`FREE`, `VERTICAL` ou `GRID`), o espaçamento e o preenchimento pertencem à janela. `GuiFormStore` rejeita versões diferentes de 1. Formulários existentes dependem desses campos e de seus valores padrão.

## Decisões propostas para a versão 2

1. **Usar uma árvore recursiva.** `elements` continua sendo a lista ordenada de filhos da janela. Um contêiner é um nó com tipo próprio e possui sua lista ordenada de `children`; controles existentes continuam sendo folhas. A ordem dos arrays define a ordem dos irmãos e deve sobreviver à edição, serialização e execução.
2. **Manter identidade estável.** Cada nó conserva um `id` único. Seleção, Undo/Redo e referências internas usam esse identificador, sem depender do nome, posição ou índice do nó.
3. **Separar conteúdo de posicionamento.** A janela mantém o layout atual. Um nó sob layout Livre conserva limites absolutos; um filho em contêiner gerenciado usa tamanho preferido e restrições de crescimento/alinhamento, sem coordenadas absolutas. O posicionamento deve ser tipado, validado e explícito no esquema final.
4. **Modelar o contêiner como discriminador, não como texto.** Um campo enum define `HORIZONTAL`, `VERTICAL` ou `GRID`. Dados do contêiner incluem espaçamento e preenchimento; a grade também inclui colunas e, se necessário, expansão por linha/coluna. Não usar CSS ou expressões livres para definir layouts.
5. **Preservar propriedades dos controles.** Aparência, nome, texto, `eventRoutine`, `variableName` e dados do algoritmo continuam associados ao nó de controle. Contêineres recebem somente propriedades visuais e de layout que tenham efeito real no preview e na exportação.

Formato ilustrativo — nomes finais dos campos devem ser fechados antes da implementação:

```json
{
  "schemaVersion": 2,
  "title": "Cadastro",
  "width": 640,
  "height": 480,
  "layout": "FREE",
  "elements": [
    {
      "id": "grupo-dados",
      "type": "CONTAINER",
      "name": "dados",
      "containerLayout": {
        "kind": "VERTICAL",
        "spacing": 12,
        "padding": 8
      },
      "bounds": { "x": 16, "y": 16, "width": 300, "height": 180 },
      "children": [
        {
          "id": "campo-nome",
          "type": "TEXT_FIELD",
          "name": "nome",
          "text": "Digite seu nome",
          "preferredSize": { "width": 240, "height": 36 },
          "layoutConstraints": { "growWidth": true }
        }
      ]
    }
  ]
}
```

O exemplo é conceitual. A implementação deve definir campos obrigatórios, valores padrão e representação de tamanhos antes de publicar arquivos v2. Não aceitar campos desconhecidos como se tivessem sido aplicados.

## Compatibilidade e migração

- Manter um leitor explícito por versão. O leitor v1 valida e carrega o formato atual sem reescrever o arquivo; cada elemento plano vira filho direto da janela na representação interna em árvore.
- O carregamento v1 deve preservar IDs, ordem, geometria, layout, aparência, caminho relativo do `.alg`, eventos, vínculos e campos opcionais. A conversão em memória não pode mover ou redimensionar controles.
- Um formulário v1 permanece v1 enquanto não usar uma funcionalidade exclusiva da v2. Ao adicionar ou salvar estrutura aninhada, avisar que o arquivo será convertido e que versões antigas do Codalgora não abrirão o esquema 2. Preferir gravação atômica por arquivo temporário e substituição para evitar perda em falha durante a migração.
- Não converter silenciosamente para v1 achatando a árvore: isso pode perder relações de layout e comportamento responsivo. Uma futura exportação v1 só deve ser oferecida se a árvore puder ser achatada sem perda, com validação e aviso explícitos.
- Continuar rejeitando versões futuras com mensagem clara. Preservar `.alg`, nomes de procedimentos, bindings e a gramática VisualG; a evolução é restrita ao documento de interface.

## Regras de validação propostas

- Limitar o tamanho do arquivo e o total de nós usando limites explícitos, compatíveis com os atuais 1 MB e 100 componentes; definir também uma profundidade máxima antes de habilitar edição recursiva.
- Exigir IDs únicos e nomes válidos/únicos no formulário inteiro, inclusive entre níveis diferentes.
- Permitir filhos somente em contêineres; rejeitar ciclos nas operações da árvore, filhos em controles-folha, enum desconhecido e estrutura recursiva malformada.
- Validar espaçamento, preenchimento, colunas, tamanhos e restrições dentro de intervalos finitos. Validar que dimensões mínimas e limites da janela continuam respeitados.
- Aplicar regras de eventos e variáveis atuais somente a controles compatíveis. Um contêiner não pode receber evento ou variável como se fosse um botão/campo.
- Rejeitar configurações incompatíveis entre layouts (por exemplo, coordenadas absolutas num filho gerenciado), em vez de ignorar campos.

## Sequência segura de implementação

1. Definir DTOs/modelo de árvore e a especificação v2 sem alterar a gramática nem o runtime Portugol.
2. Implementar leitores separados v1/v2 e testes de fixtures, garantindo leitura e round-trip sem perdas para arquivos v1 e v2.
3. Implementar validação estrutural e operações de árvore no modelo, incluindo Undo/Redo por IDs estáveis.
4. Adicionar criação, seleção, reordenação, aninhamento, remoção e propriedades de contêiner no GUI Builder.
5. Fazer o preview e o aplicativo exportado usarem o mesmo renderizador e os mesmos cálculos responsivos.
6. Validar abertura, salvamento, reabertura, tema, eventos, bindings e exportação; somente então atualizar exemplos e anunciar a v2.

## Critérios de aceite da implementação futura

- Fixtures v1 abrem sem alterações visuais ou perda de dados; salvar sem usar recursos v2 mantém a versão 1.
- Fixtures v2 aninhadas fazem round-trip estável, preservam IDs e ordem e são rejeitadas com mensagens úteis quando inválidas.
- Redimensionar a janela reorganiza os filhos em preview e exportação da mesma forma; seleção e histórico continuam corretos ao mover nós entre contêineres.
- Eventos, variáveis e estilos dos controles existentes funcionam igualmente dentro de contêineres e no formulário raiz.
- Testes de regressão confirmam que a gramática VisualG e a execução de algoritmos não foram alteradas.
