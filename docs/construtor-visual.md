# Construtor visual (protótipo local)

O protótipo permite desenhar e pré-visualizar uma interface JavaFX sem escrever código Java, além de ligar cliques de botões a procedimentos Portugol.
Ele tem um menu próprio na barra superior, logo depois de **Editar**: **Construtor visual → Abrir construtor visual…**.

## Fluxo rápido

1. Escolha Texto, Botão, Campo de texto, Área de texto, Caixa de seleção ou Lista suspensa na paleta, ou arraste o item para a área branca.
2. Arraste o componente no canvas; a posição acompanha uma grade de 8 pixels. Segure Shift enquanto arrasta para redimensioná-lo. **Organizar** distribui os componentes em coluna ou em uma grade de duas ou três colunas. Em **Organização da prévia**, escolha Livre, Coluna responsiva ou Grade responsiva.
3. Selecione o componente na tela ou na lista lateral e edite nome, texto, posição e tamanho.
4. Ajuste título e dimensões da janela no inspetor.
5. Use **Vincular algoritmo .alg** para escolher o algoritmo que contém os procedimentos dos botões.
6. Selecione um campo de texto ou uma caixa de seleção e escolha uma variável global escalar em **Variável Portugol**. Campos de texto recebem e devolvem valores; caixas de seleção aceitam variáveis `logico`; rótulos mostram o valor após a ação.
7. Associe um procedimento: botões, caixas de seleção e listas suspensas executam ao ativar; campos de texto executam ao pressionar Enter. Parâmetros precisam ter uma variável global escalar com o mesmo nome; parâmetros `var` atualizam essa variável.
8. Use **Pré-visualizar**. O algoritmo executa o bloco principal uma vez; cada clique envia os valores dos campos vinculados, executa o procedimento e atualiza os controles com os valores globais resultantes. Os eventos são processados em sequência. `escreva` e `escreval` aparecem no terminal da IDE. Cada `leia` abre uma caixa de entrada.
9. Salve como `*.cgui.json`; use **Abrir** para continuar depois. O caminho do `.alg` é relativo ao formulário para facilitar mover ambos juntos.

O formulário é um JSON versionado, separado do código Portugol, e mantém o caminho do algoritmo, as variáveis vinculadas e o procedimento associado a cada botão. A leitura valida a versão, os tipos conhecidos, os nomes únicos, os limites da janela e até 100 componentes. Um formulário alterado pede confirmação antes de ser descartado.

## Limites atuais

As opções da lista suspensa são separadas por `|` no inspetor. As variáveis vinculáveis são globais, escalares e mutáveis; vetores, constantes e variáveis locais não aparecem. Uma variável só pode receber entrada de um campo de texto, área de texto, lista suspensa ou caixa de seleção. Parâmetros de eventos devem corresponder pelo nome a uma variável global escalar; referências `var` precisam ter o mesmo tipo. O editor oferece Undo/Redo limitado ao histórico da sessão, comandos simples de organização e layouts responsivos na prévia. Contêineres aninhados, múltiplas janelas e sistema completo de projetos ainda não existem. Funções não podem ser chamadas como eventos. A sintaxe VisualG existente foi preservada.

## Validar localmente

```sh
./gradlew :desktop:test
./gradlew :desktop:run
```

Os testes exercitam persistência e validação do JSON, execução serial de procedimentos de evento com estado global compartilhado, além de abrir o construtor, adicionar componentes, editar texto e criar o preview JavaFX.
