# Construtor visual (protótipo local)

O protótipo permite desenhar e pré-visualizar uma interface JavaFX sem escrever código Java, além de ligar cliques de botões a procedimentos Portugol.
Ele tem um menu próprio na barra superior, logo depois de **Editar**: **Construtor visual → Abrir construtor visual…**.

## Fluxo rápido

1. Escolha Texto, Botão, Campo de texto, Área de texto, Caixa de seleção ou Lista suspensa na paleta, ou arraste o item para a área branca.
2. Arraste o componente no canvas; a posição acompanha uma grade de 8 pixels. Use as oito alças azuis para redimensioná-lo; o tamanho aparece durante o movimento. **Organizar** distribui os componentes em coluna ou em uma grade de duas ou três colunas. Em **Janela e layout → Organização do formulário**, escolha Livre, Coluna responsiva ou Grade responsiva.
3. Selecione o componente na tela ou na lista lateral e edite nome, texto, posição e tamanho.
4. Ajuste título e dimensões da janela no inspetor.
5. Use **Vincular algoritmo .alg** para escolher o algoritmo que contém os procedimentos dos botões.
6. Selecione um campo de texto ou uma caixa de seleção e escolha uma variável global escalar em **Variável Portugol**. Campos de texto recebem e devolvem valores; caixas de seleção aceitam variáveis `logico`; rótulos mostram o valor após a ação.
7. Associe um procedimento: botões, caixas de seleção e listas suspensas executam ao ativar; campos de texto executam ao pressionar Enter. Parâmetros precisam ter uma variável global escalar com o mesmo nome; parâmetros `var` atualizam essa variável.
8. Use **Executar formulário**. O algoritmo executa o bloco principal uma vez; cada clique envia os valores dos campos vinculados, executa o procedimento e atualiza os controles com os valores globais resultantes. Os eventos são processados em sequência. `escreva` e `escreval` aparecem no terminal da IDE. Cada `leia` abre uma caixa de entrada.
9. Salve como `*.cgui.json`; use **Abrir** para continuar depois. O caminho do `.alg` é relativo ao formulário para facilitar mover ambos juntos.
10. Para gerar um pacote independente para o sistema atual, rode o comando Gradle de exportação abaixo; o pacote inclui runtime Java e atalhos de execução.

O formulário é um JSON versionado, separado do código Portugol, e mantém o caminho do algoritmo, as variáveis vinculadas e o procedimento associado a cada botão. A leitura valida a versão, os tipos conhecidos, os nomes únicos, os limites da janela e até 100 componentes. Um formulário alterado pede confirmação antes de ser descartado.

## Limites atuais

As opções da lista suspensa são separadas por `|` no inspetor. As variáveis vinculáveis são globais, escalares e mutáveis; vetores, constantes e variáveis locais não aparecem. Uma variável só pode receber entrada de um campo de texto, área de texto, lista suspensa ou caixa de seleção. Parâmetros de eventos devem corresponder pelo nome a uma variável global escalar; referências `var` precisam ter o mesmo tipo. O editor oferece Undo/Redo limitado ao histórico da sessão, comandos simples de organização e layouts responsivos na prévia. Contêineres aninhados, múltiplas janelas e sistema completo de projetos ainda não existem. Funções não podem ser chamadas como eventos. A sintaxe VisualG existente foi preservada.

## Exportar aplicativo independente

No terminal do repositório, gere um ZIP para a plataforma atual (Linux, Windows ou macOS):

```sh
./gradlew :desktop:exportGuiApplication \
  -PguiForm=/caminho/interface.cgui.json \
  -PguiAlgorithm=/caminho/algoritmo.alg
```

O arquivo fica em `desktop/build/distributions/codalgora-visual-app.zip`. Ele inclui o runtime da plataforma atual, o formulário, o algoritmo e scripts `Executar.sh`/`Executar.bat`. A exportação usa `jpackage` do JDK 21 instalado no computador. Cada sistema precisa gerar seu próprio pacote.

## Validação manual do pacote Linux

Em 19/11/2026, o pacote `codalgora-visual-app.zip` foi extraído em `/tmp/codalgora-gui-test` e iniciado por `./Executar.sh` em um desktop Linux com sessão gráfica. O usuário confirmou a abertura da aplicação JavaFX fora da IDE e quatro ativações do botão; o terminal exibiu `1`, `2`, `3`, `4`. Isso verifica o caminho básico do lançador empacotado até o evento Portugol e a manutenção do contador entre eventos nessa execução.

Durante a inicialização foi observado o aviso `Unsupported JavaFX configuration: classes were loaded from 'unnamed module'`. O pacote atual é produzido com `jpackage --input` e `--main-jar`, enquanto as dependências JavaFX entram no classpath; por isso o runtime encontra JavaFX no módulo sem nome. O aviso não impediu o teste manual. Removê-lo exigiria avaliar uma distribuição modular (module path e descritores de módulos para a aplicação e dependências); não foi aplicado um ajuste de flags que apenas ocultasse o aviso. A migração modular permanece uma investigação separada.

Este teste não valida persistência após fechar e reabrir, outros componentes, cliques concorrentes, erros/cancelamento, dependências em distribuições Linux diferentes ou Windows.

## Validar localmente

```sh
./gradlew :desktop:test
./gradlew :desktop:run
```

Os testes exercitam persistência e validação do JSON, execução serial de procedimentos de evento com estado global compartilhado, além de abrir o construtor, adicionar componentes, editar texto e criar o preview JavaFX.

## Edição visual com o mouse

- Selecione um componente e arraste seu corpo para mover. Nos layouts Coluna e Grade, arraste sobre outro componente para alterar a ordem; as alças alteram os tamanhos preferidos, sem mudar as posições absolutas salvas. A opção **Preencher largura no layout** permite voltar ao crescimento automático.
- As oito alças respeitam o mínimo de 50 × 28 e os limites da janela. Guias azuis ajudam a alinhar bordas e centros no modo Livre. Desmarque **Encaixar na grade** para movimentos livres; o zoom vai de 25% a 200%, com **Ajustar à janela**.
- Duplo clique ou **F2** abre a edição inline. **Enter** confirma, **Esc** cancela; sair do campo confirma. O nome técnico fica separado. Em campos de texto, o texto editado é a dica de entrada; na lista, as opções são separadas por `|`.
- O inspetor organiza **Cores e estados**, **Tipografia** e **Bordas e preenchimento**. As cores podem ser escolhidas visualmente ou informadas em `#RRGGBB`/`#RRGGBBAA`. Campo hexadecimal vazio restaura a herança do padrão. Há cores distintas para Normal, Ao passar o mouse, Pressionado e Desativado. Fontes, opacidade, alinhamento, borda, cantos e preenchimento refletem no canvas e no aplicativo executado.
- Use **Restaurar aparência padrão** para remover a personalização. As fontes são as instaladas no sistema; o pacote não incorpora fontes adicionais escolhidas pelo usuário.
- **Ctrl+C/Ctrl+V**, **Ctrl+D**, **Delete** e o menu de contexto copiam, colam, duplicam e excluem. A cópia é local à janela do construtor. Uma cópia de controle de entrada não recebe automaticamente o mesmo vínculo de variável, evitando duas fontes de entrada; o vínculo original é preservado. Colar componentes com vínculos exige o mesmo algoritmo.
- **Ctrl+Z/Ctrl+Y** (ou Ctrl+Shift+Z) desfaz/refaz gestos, textos e aparência. Um arraste gera uma etapa no histórico. Alterações no Design não executam Portugol; a execução começa na janela aberta por **Executar formulário**.

Os campos visuais são opcionais no esquema JSON 1. Formulários anteriores recebem padrões ao abrir. A leitura aceita somente cores hexadecimais, fontes com nomes simples, alinhamentos conhecidos e números dentro de limites. Não há campo de CSS livre, URLs de estilos ou execução de expressões vindas do formulário.

### Exemplo e validação manual

Abra `examples/gui-builder/contador.cgui.json` no construtor. O algoritmo `contador.alg` está na mesma pasta. Experimente as oito alças, duplo clique, Esc/Enter, cores com transparência e Undo/Redo. Troque entre Livre, Coluna e Grade; execute o formulário e confirme que **Adicionar um** atualiza o contador.

Para exportar o exemplo no Linux, a partir da raiz do repositório:

```sh
./gradlew :desktop:exportGuiApplication \
  -PguiForm="$PWD/examples/gui-builder/contador.cgui.json" \
  -PguiAlgorithm="$PWD/examples/gui-builder/contador.alg"
```

Extraia `desktop/build/distributions/codalgora-visual-app.zip` e abra `Executar.sh`. O pacote contém o mesmo modelo visual e renderizador usados na prévia. Contêineres aninhados, seleção múltipla, fontes incorporadas e imagens continuam fora deste editor.
