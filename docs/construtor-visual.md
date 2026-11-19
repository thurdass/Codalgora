# Construtor visual (protótipo local)

O protótipo permite desenhar e pré-visualizar uma interface JavaFX sem escrever código Java.
Ele é acessado em **Arquivo → Construtor visual…**.

## Fluxo rápido

1. Clique em um item da paleta ou arraste-o para a área branca.
2. Arraste o componente no canvas; a posição acompanha uma grade de 8 pixels.
3. Selecione o componente na tela ou na lista lateral e edite nome, texto, posição e tamanho.
4. Ajuste título e dimensões da janela no inspetor.
5. Use **Pré-visualizar** para abrir a interface como uma janela JavaFX.
6. Salve como `*.cgui.json`; use **Abrir** para continuar depois.

O arquivo é um JSON versionado, independente do algoritmo `.alg`. A leitura valida a versão, os tipos conhecidos, os nomes únicos, os limites da janela e até 100 componentes. Um formulário alterado pede confirmação antes de ser descartado.

## O que o protótipo ainda não faz

Os controles são reais durante a pré-visualização, mas ainda não chamam procedimentos Portugol. Não há associação de eventos, acesso do algoritmo aos campos, Undo/Redo, contêineres de layout, múltiplas janelas nem sistema completo de projetos. A sintaxe e a execução dos algoritmos VisualG existentes não foram alteradas.

## Validar localmente

```sh
./gradlew :desktop:test
./gradlew :desktop:run
```

Os testes exercitam persistência e validação do JSON, além de abrir o construtor, adicionar um componente, editar seu texto e criar o preview JavaFX.
