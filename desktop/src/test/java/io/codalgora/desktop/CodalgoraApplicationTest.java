package io.codalgora.desktop;

import static org.junit.jupiter.api.Assertions.*;

import io.codalgora.desktop.guibuilder.GuiBuilderPane;
import io.codalgora.desktop.guibuilder.GuiForm;
import io.codalgora.desktop.guibuilder.GuiFormStore;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.scene.input.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import javax.imageio.ImageIO;
import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class CodalgoraApplicationTest {
  @TempDir Path directory;
  private Stage stage;
  private CodalgoraApplication app;

  @BeforeAll
  static void initializeJavaFx() throws Exception {
    Assumptions.assumeTrue(
        !System.getProperty("os.name").toLowerCase().contains("linux")
            || System.getenv("DISPLAY") != null,
        "Execute os testes gráficos com xvfb-run -a ./gradlew test em Linux sem display.");
    CountDownLatch ready = new CountDownLatch(1);
    Platform.startup(
        () -> {
          Platform.setImplicitExit(false);
          ready.countDown();
        });
    assertTrue(ready.await(15, TimeUnit.SECONDS));
  }

  @AfterAll
  static void shutdownJavaFx() {
    Platform.exit();
  }

  @BeforeEach
  void launch() throws Exception {
    fx(
        () -> {
          app = new CodalgoraApplication();
          stage = new Stage();
          app.start(stage);
          stage.getScene().getRoot().applyCss();
          stage.getScene().getRoot().layout();
          return null;
        });
  }

  @AfterEach
  void close() throws Exception {
    fx(
        () -> {
          app.stop();
          stage.hide();
          return null;
        });
  }

  @Test
  void themesRecolorEverySurfaceAndPersistSelection() throws Exception {
    EditorTheme original = EditorTheme.saved();
    try {
      for (EditorTheme theme : EditorTheme.values()) {
        fx(
            () -> {
              app.applyTheme(theme);
              stage.getScene().getRoot().applyCss();
              stage.getScene().getRoot().layout();
              assertEquals(theme, EditorTheme.saved());
              assertTrue(stage.getScene().getRoot().getStyle().contains("-alg-keyword:"));
              capture("Codalgora-tema-" + theme.name() + ".png");
              return null;
            });
      }
      fx(
          () -> {
            app.applyTheme(EditorTheme.GRAPHITE);
            stage.getScene().getRoot().applyCss();
            capture("Codalgora.png");
            return null;
          });
    } finally {
      fx(
          () -> {
            app.applyTheme(original);
            return null;
          });
    }
  }

  private static <T> T fx(Callable<T> action) throws Exception {
    FutureTask<T> task = new FutureTask<>(action);
    Platform.runLater(task);
    return task.get(10, TimeUnit.SECONDS);
  }

  private <T> T node(String id, Class<T> type) {
    return type.cast(stage.getScene().lookup("#" + id));
  }

  private String output() throws Exception {
    return fx(() -> node("terminalOutput", TextArea.class).getText());
  }

  private void capture(String name) throws Exception {
    captureStage(stage, name);
  }

  private void captureStage(Stage target, String name) throws Exception {
    var image = target.getScene().snapshot(null);
    int width = (int) image.getWidth(), height = (int) image.getHeight();
    int[] pixels = new int[width * height];
    image
        .getPixelReader()
        .getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
    BufferedImage png = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    png.setRGB(0, 0, width, height, pixels, 0, width);
    Path directory = Path.of("build/screenshots");
    Files.createDirectories(directory);
    ImageIO.write(png, "png", directory.resolve(name).toFile());
  }

  private void await(BooleanSupplier condition) throws Exception {
    long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
    while (System.nanoTime() < limit) {
      if (fx(condition::getAsBoolean)) return;
      Thread.sleep(20);
    }
    fail("A interface não atingiu o estado esperado. Terminal: " + output());
  }

  @Test
  void richTextEditorRunsWithInteractiveTerminalInput() throws Exception {
    fx(
        () -> {
          assertNotNull(node("codeEditor", CodeArea.class));
          capture("Codalgora.png");
          node("runButton", Button.class).fire();
          return null;
        });
    await(() -> !node("terminalInput", TextField.class).isDisabled());
    fx(
        () -> {
          TextField input = node("terminalInput", TextField.class);
          input.setText("Ada");
          input.fireEvent(new ActionEvent());
          return null;
        });
    await(() -> node("terminalOutput", TextArea.class).getText().contains("Execução finalizada"));
    assertTrue(output().contains("Bem-vindo ao Codalgora, Ada!"));
    assertTrue(output().contains("Passo 3"));
    assertEquals("Finalizado", fx(() -> node("executionStatus", Label.class).getText()));
    fx(
        () -> {
          capture("Codalgora-execucao.png");
          return null;
        });
  }

  @Test
  void debuggerPausesStepsAndCancelsWithoutError() throws Exception {
    fx(
        () -> {
          node("codeEditor", CodeArea.class)
              .replaceText(
                  "algoritmo \"debug\"\n"
                      + "var x:inteiro\n"
                      + "inicio\n"
                      + "escreval(\"um\")\n"
                      + "x <- 2\n"
                      + "fimalgoritmo");
          node("debugButton", Button.class).fire();
          return null;
        });
    await(() -> node("runButton", Button.class).getText().contains("Continuar"));
    fx(
        () -> {
          node("stepButton", Button.class).fire();
          return null;
        });
    await(() -> node("terminalOutput", TextArea.class).getText().contains("um"));
    fx(
        () -> {
          node("stopButton", Button.class).fire();
          return null;
        });
    await(() -> node("stopButton", Button.class).isDisabled());
    assertFalse(output().contains("Erro:"));
  }

  @Test
  void reportsSyntaxErrorsAndRecoversForNextRun() throws Exception {
    fx(
        () -> {
          node("codeEditor", CodeArea.class)
              .replaceText("algoritmo \"erro\"\ninicio\nescreval(\nfimalgoritmo");
          node("runButton", Button.class).fire();
          return null;
        });
    await(() -> node("terminalOutput", TextArea.class).getText().contains("Erro:"));
    fx(
        () -> {
          node("codeEditor", CodeArea.class)
              .replaceText("algoritmo \"ok\"\ninicio\nescreval(\"recuperado\")\nfimalgoritmo");
          node("runButton", Button.class).fire();
          return null;
        });
    await(() -> node("terminalOutput", TextArea.class).getText().contains("Execução finalizada"));
    assertTrue(output().contains("recuperado"));
  }

  @Test
  void completionUsesPortugolKeywordsAndGutterStoresBreakpoint() throws Exception {
    fx(
        () -> {
          CodeArea editor = node("codeEditor", CodeArea.class);
          editor.replaceText("es");
          editor.moveTo(2);
          editor.requestFocus();
          Event.fireEvent(
              editor,
              new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE, false, true, false, false));
          return null;
        });
    await(
        () ->
            Window.getWindows().stream()
                .anyMatch(w -> w instanceof javafx.stage.PopupWindow && w.isShowing()));
    fx(
        () -> {
          ContextMenu popup =
              (ContextMenu)
                  Window.getWindows().stream()
                      .filter(w -> w instanceof ContextMenu && w.isShowing())
                      .findFirst()
                      .orElseThrow();
          MenuItem complete =
              popup.getItems().stream()
                  .filter(i -> i.getText().equals("escreval"))
                  .findFirst()
                  .orElseThrow();
          complete.fire();
          popup.hide();
          assertEquals("escreval", node("codeEditor", CodeArea.class).getText());
          stage.getScene().getAccelerators().get(new KeyCodeCombination(KeyCode.F9)).run();
          assertFalse(stage.getScene().getRoot().lookupAll(".breakpoint-item").isEmpty());
          return null;
        });
  }

  @Test
  @SuppressWarnings("unchecked")
  void visualBuilderAddsEditsAndPreviewsAForm() throws Exception {
    Path algorithm = directory.resolve("eventos.alg");
    Files.writeString(
        algorithm,
        "algoritmo \"eventos\"\n"
            + "var\n"
            + "total:inteiro\n"
            + "nome:caractere\n"
            + "procedimento incrementar(var total:inteiro)\n"
            + "inicio\n"
            + "total <- total + 1\n"
            + "escreval(\"clique \", total, \" \", nome)\n"
            + "nome <- \"Codalgora\"\n"
            + "fimprocedimento\n"
            + "inicio\n"
            + "total <- 0\n"
            + "nome <- \"\"\n"
            + "escreval(\"prévia pronta\")\n"
            + "fimalgoritmo\n");
    Stage[] builder = new Stage[1];
    Stage[] preview = new Stage[1];
    fx(
        () -> {
          MenuBar menuBar = (MenuBar) stage.getScene().lookup(".menu-bar");
          assertNotNull(menuBar);
          assertEquals("Arquivo", menuBar.getMenus().get(0).getText());
          assertEquals("Editar", menuBar.getMenus().get(1).getText());
          Menu builderMenu = menuBar.getMenus().get(2);
          assertEquals("Construtor visual", builderMenu.getText());
          MenuItem openBuilder = builderMenu.getItems().getFirst();
          assertEquals("Abrir construtor visual…", openBuilder.getText());
          openBuilder.fire();
          builder[0] = Window.getWindows().stream()
              .filter(window -> window instanceof Stage candidate
                  && candidate.getTitle().startsWith("Construtor visual"))
              .map(Stage.class::cast).findFirst().orElseThrow();
          builder[0].getScene().getRoot().applyCss();
          ((Button) builder[0].getScene().lookup("#palette-LABEL")).fire();
          javafx.scene.layout.Pane canvas =
              (javafx.scene.layout.Pane) builder[0].getScene().lookup("#guiCanvas");
          assertEquals(1, canvas.getChildren().size());
          TextField text = (TextField) builder[0].getScene().lookup("#guiText");
          text.setText("Olá, estudante!");
          text.fireEvent(new ActionEvent());
          assertEquals("Olá, estudante!", ((Label) canvas.getChildren().getFirst()).getText());
          ((Button) builder[0].getScene().lookup("#palette-TEXT_FIELD")).fire();
          ((Button) builder[0].getScene().lookup("#palette-BUTTON")).fire();
          ((Button) builder[0].getScene().lookup("#palette-COMBO_BOX")).fire();
          assertEquals(4, canvas.getChildren().size());
          ((Button) builder[0].getScene().lookup("#guiUndo")).fire();
          assertEquals(3, canvas.getChildren().size());
          ((Button) builder[0].getScene().lookup("#guiRedo")).fire();
          assertEquals(4, canvas.getChildren().size());
          MenuButton arrange = (MenuButton) builder[0].getScene().lookup(".menu-button");
          arrange.getItems().get(1).fire();
          assertEquals(16.0, canvas.getChildren().getFirst().getLayoutX());
          GuiBuilderPane pane = (GuiBuilderPane) builder[0].getScene().getRoot();
          pane.linkAlgorithm(algorithm);
          ListView<?> outline = (ListView<?>) builder[0].getScene().lookup("#guiOutline");
          outline.getSelectionModel().select(1);
          ComboBox<String> variable =
              (ComboBox<String>) builder[0].getScene().lookup("#guiVariableName");
          assertTrue(variable.getItems().contains("nome"));
          variable.setValue("nome");
          variable.fireEvent(new ActionEvent());
          outline.getSelectionModel().select(2);
          ComboBox<String> event = (ComboBox<String>) builder[0].getScene().lookup("#guiEventRoutine");
          assertTrue(event.getItems().contains("incrementar"));
          event.setValue("incrementar");
          event.fireEvent(new ActionEvent());
          Path formFile = directory.resolve("interface.cgui.json");
          pane.saveForm(formFile);
          pane.loadForm(formFile);
          assertTrue(Files.readString(formFile).contains("\"variableName\": \"nome\""));
          ComboBox<GuiForm.LayoutMode> layout =
              (ComboBox<GuiForm.LayoutMode>) builder[0].getScene().lookup("#guiLayoutMode");
          layout.setValue(GuiForm.LayoutMode.GRID);
          layout.fireEvent(new ActionEvent());
          pane.saveForm(formFile);
          pane.loadForm(formFile);
          builder[0].getScene().getRoot().applyCss();
          builder[0].getScene().getRoot().layout();
          captureStage(builder[0], "Codalgora-GUI-Builder.png");
          ((Button) builder[0].getScene().lookup("#guiPreview")).fire();
          preview[0] = Window.getWindows().stream()
              .filter(window -> window instanceof Stage candidate
                  && candidate.getTitle().equals("Minha primeira interface"))
              .map(Stage.class::cast).findFirst().orElseThrow();
          assertEquals(4, ((javafx.scene.layout.Pane) preview[0].getScene().getRoot()).getChildren().size());
          assertInstanceOf(javafx.scene.layout.GridPane.class, preview[0].getScene().getRoot());
          return null;
        });
    assertNotNull(preview[0]);
    await(() -> node("terminalOutput", TextArea.class).getText().contains("prévia pronta"));
    fx(
        () -> {
          javafx.scene.layout.Pane previewContent =
              (javafx.scene.layout.Pane) preview[0].getScene().getRoot();
          TextField boundInput =
              (TextField)
                  previewContent.getChildren().stream()
                      .filter(TextField.class::isInstance)
                      .findFirst().orElseThrow();
          boundInput.setText("Arthur");
          Button interactive =
              (Button)
                  previewContent.getChildren().stream()
                      .filter(node -> node instanceof Button button && button.getText().equals("Botão"))
                      .findFirst().orElseThrow();
          interactive.fire();
          return null;
        });
    await(() -> node("terminalOutput", TextArea.class).getText().contains("clique 1 Arthur"));
    fx(
        () -> {
          TextField boundInput =
              (TextField)
                  ((javafx.scene.layout.Pane) preview[0].getScene().getRoot())
                      .getChildren().stream()
                      .filter(TextField.class::isInstance)
                      .findFirst().orElseThrow();
          assertEquals("Codalgora", boundInput.getText());
          ((Button)
                  ((javafx.scene.layout.Pane) preview[0].getScene().getRoot())
                      .getChildren().stream()
                      .filter(node -> node instanceof Button button && button.getText().equals("Botão"))
                      .findFirst().orElseThrow())
              .fire();
          return null;
        });
    await(() -> node("terminalOutput", TextArea.class).getText().contains("clique 2 Codalgora"));
    fx(
        () -> {
          preview[0].hide();
          builder[0].hide();
          return null;
        });
  }

  @Test
  void standaloneFormLauncherRebuildsSavedControlsAfterStageRestart() throws Exception {
    GuiForm form = new GuiForm();
    form.title = "Aplicativo exportado";
    form.elements.add(
        new GuiForm.Element(GuiForm.Type.BUTTON, "acao", "Executar", 24, 24, 160, 38));
    form.elements.add(
        new GuiForm.Element(GuiForm.Type.TEXT_FIELD, "nome", "Nome", 24, 72, 160, 38));
    form.elements.add(
        new GuiForm.Element(GuiForm.Type.CHECK_BOX, "ativo", "Ativo", 24, 120, 160, 38));
    form.elements.add(
        new GuiForm.Element(GuiForm.Type.COMBO_BOX, "opcao", "A | B", 24, 168, 160, 38));
    Path file = directory.resolve("standalone.cgui.json");
    GuiFormStore.write(file, form);

    String stylesheet = CodalgoraApplication.class.getResource("codalgora.css").toExternalForm();
    fx(
        () -> {
          GuiBuilderPane.openFormPreview(
              stage,
              stylesheet,
              EditorTheme.saved().css(),
              file,
              ignored -> {});
          assertEquals("Aplicativo exportado", stage.getTitle());
          assertEquals(4, ((javafx.scene.layout.Pane) stage.getScene().getRoot()).getChildren().size());
          assertNotNull(stage.getScene().lookup(".button"));
          assertNotNull(stage.getScene().lookup(".text-field"));
          assertNotNull(stage.getScene().lookup(".check-box"));
          assertNotNull(stage.getScene().lookup(".combo-box"));
          return null;
        });
    fx(
        () -> {
          stage.hide();
          Stage restarted = new Stage();
          GuiBuilderPane.openFormPreview(restarted, stylesheet, EditorTheme.saved().css(), file, ignored -> {});
          assertEquals("Aplicativo exportado", restarted.getTitle());
          assertEquals(4, ((javafx.scene.layout.Pane) restarted.getScene().getRoot()).getChildren().size());
          assertNotNull(restarted.getScene().lookup(".combo-box"));
          restarted.hide();
          return null;
        });
  }

  @Test
  void exportedStylePreviewProcessesOneHundredRapidClicksInOrder() throws Exception {
    Path algorithm = directory.resolve("contador.alg");
    Files.writeString(
        algorithm,
        "algoritmo \"contador visual\"\n"
            + "var total:inteiro\n"
            + "procedimento incrementar(var total:inteiro)\n"
            + "inicio\n"
            + "total <- total + 1\n"
            + "escreval(total)\n"
            + "fimprocedimento\n"
            + "inicio\n"
            + "total <- 0\n"
            + "fimalgoritmo\n");
    GuiForm form = new GuiForm();
    form.algorithmPath = algorithm.getFileName().toString();
    GuiForm.Element button =
        new GuiForm.Element(GuiForm.Type.BUTTON, "incrementar", "Incrementar", 16, 16, 160, 38);
    button.eventRoutine = "incrementar";
    form.elements.add(button);
    GuiForm.Element total =
        new GuiForm.Element(GuiForm.Type.LABEL, "total", "0", 16, 64, 160, 32);
    total.variableName = "total";
    form.elements.add(total);
    Path formPath = directory.resolve("contador.cgui.json");
    GuiFormStore.write(formPath, form);

    Stage exported = new Stage();
    ConcurrentLinkedQueue<String> output = new ConcurrentLinkedQueue<>();
    String stylesheet = CodalgoraApplication.class.getResource("codalgora.css").toExternalForm();
    fx(
        () -> {
          GuiBuilderPane.openFormPreview(exported, stylesheet, EditorTheme.saved().css(), formPath, output::add);
          return null;
        });
    try {
      Button increment = fx(() -> (Button) exported.getScene().getRoot().lookup(".button"));
      fx(
          () -> {
            for (int click = 0; click < 100; click++) increment.fire();
            return null;
          });

      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (System.nanoTime() < deadline && !String.join("", output).contains("100\n"))
        Thread.sleep(10);
      List<Integer> values =
          String.join("", output).lines().map(String::trim)
              .filter(line -> line.matches("\\d+"))
              .map(Integer::parseInt).toList();
      assertEquals(java.util.stream.IntStream.rangeClosed(1, 100).boxed().toList(), values);

      Label totalLabel = fx(() -> (Label) exported.getScene().getRoot().lookup(".label"));
      deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (System.nanoTime() < deadline && !fx(() -> totalLabel.getText().equals("100")))
        Thread.sleep(10);
      assertEquals("100", fx(totalLabel::getText));
    } finally {
      fx(
          () -> {
            exported.hide();
            return null;
          });
    }
  }

  @Test
  void closingPreviewCancelsInfiniteEventWithoutBlockingJavaFx() throws Exception {
    Path algorithm = directory.resolve("infinito.alg");
    Files.writeString(
        algorithm,
        "algoritmo \"cancelamento visual\"\n"
            + "var total:inteiro\n"
            + "procedimento girar\n"
            + "inicio\n"
            + "escreval(\"evento iniciado\")\n"
            + "enquanto verdadeiro faca\n"
            + "total <- total + 1\n"
            + "fimenquanto\n"
            + "fimprocedimento\n"
            + "inicio\n"
            + "total <- 0\n"
            + "fimalgoritmo\n");
    GuiForm form = new GuiForm();
    form.algorithmPath = algorithm.getFileName().toString();
    GuiForm.Element button =
        new GuiForm.Element(GuiForm.Type.BUTTON, "girar", "Girar", 16, 16, 160, 38);
    button.eventRoutine = "girar";
    form.elements.add(button);
    Path formPath = directory.resolve("infinito.cgui.json");
    GuiFormStore.write(formPath, form);

    Stage preview = new Stage();
    ConcurrentLinkedQueue<String> output = new ConcurrentLinkedQueue<>();
    fx(
        () -> {
          GuiBuilderPane.openFormPreview(
              preview,
              CodalgoraApplication.class.getResource("codalgora.css").toExternalForm(),
              EditorTheme.saved().css(),
              formPath,
              output::add);
          ((Button) preview.getScene().getRoot().lookup(".button")).fire();
          return null;
        });
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline && !String.join("", output).contains("evento iniciado"))
      Thread.sleep(10);
    assertTrue(String.join("", output).contains("evento iniciado"));

    CompletableFuture<Void> responsive = new CompletableFuture<>();
    fx(
        () -> {
          preview.hide();
          Platform.runLater(() -> responsive.complete(null));
          return null;
        });
    responsive.get(2, TimeUnit.SECONDS);
  }

  @Test
  void textCheckAndComboBindingsReachPortugolAndRefreshAfterEvent() throws Exception {
    Path algorithm = directory.resolve("controles.alg");
    Files.writeString(
        algorithm,
        "algoritmo \"controles visuais\"\n"
            + "var nome:caractere, ativo:logico, escolha:caractere\n"
            + "procedimento receber(var nome:caractere, var ativo:logico, var escolha:caractere)\n"
            + "inicio\n"
            + "se ativo entao\n"
            + "escreval(nome, \"|\", escolha)\n"
            + "senao\n"
            + "escreval(\"desmarcado\")\n"
            + "fimse\n"
            + "nome <- \"Codalgora\"\n"
            + "ativo <- falso\n"
            + "escolha <- \"B\"\n"
            + "fimprocedimento\n"
            + "inicio\n"
            + "nome <- \"\"\n"
            + "ativo <- falso\n"
            + "escolha <- \"A\"\n"
            + "fimalgoritmo\n");
    GuiForm form = new GuiForm();
    form.algorithmPath = algorithm.getFileName().toString();
    GuiForm.Element button =
        new GuiForm.Element(GuiForm.Type.BUTTON, "receber", "Enviar", 16, 16, 160, 38);
    button.eventRoutine = "receber";
    form.elements.add(button);
    GuiForm.Element name =
        new GuiForm.Element(GuiForm.Type.TEXT_FIELD, "nome", "Nome", 16, 64, 180, 38);
    name.variableName = "nome";
    form.elements.add(name);
    GuiForm.Element active =
        new GuiForm.Element(GuiForm.Type.CHECK_BOX, "ativo", "Ativo", 16, 112, 160, 38);
    active.variableName = "ativo";
    form.elements.add(active);
    GuiForm.Element choice =
        new GuiForm.Element(GuiForm.Type.COMBO_BOX, "escolha", "A | B", 16, 160, 180, 38);
    choice.variableName = "escolha";
    form.elements.add(choice);
    Path formPath = directory.resolve("controles.cgui.json");
    GuiFormStore.write(formPath, form);

    Stage preview = new Stage();
    ConcurrentLinkedQueue<String> output = new ConcurrentLinkedQueue<>();
    fx(
        () -> {
          GuiBuilderPane.openFormPreview(
              preview,
              CodalgoraApplication.class.getResource("codalgora.css").toExternalForm(),
              EditorTheme.saved().css(),
              formPath,
              output::add);
          javafx.scene.layout.Pane root = (javafx.scene.layout.Pane) preview.getScene().getRoot();
          root.getChildren().stream().filter(TextField.class::isInstance).map(TextField.class::cast)
              .findFirst().orElseThrow().setText("Arthur");
          root.getChildren().stream().filter(CheckBox.class::isInstance).map(CheckBox.class::cast)
              .findFirst().orElseThrow().setSelected(true);
          root.getChildren().stream().filter(ComboBox.class::isInstance).map(ComboBox.class::cast)
              .findFirst().orElseThrow().setValue("B");
          root.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
              .findFirst().orElseThrow().fire();
          return null;
        });
    try {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (System.nanoTime() < deadline && !String.join("", output).contains("Arthur|B"))
        Thread.sleep(10);
      assertTrue(String.join("", output).contains("Arthur|B"));

      deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (System.nanoTime() < deadline
          && !fx(
              () -> {
                javafx.scene.layout.Pane root = (javafx.scene.layout.Pane) preview.getScene().getRoot();
                return root.getChildren().stream().filter(TextField.class::isInstance)
                        .map(TextField.class::cast).findFirst().orElseThrow().getText().equals("Codalgora")
                    && !root.getChildren().stream().filter(CheckBox.class::isInstance)
                        .map(CheckBox.class::cast).findFirst().orElseThrow().isSelected()
                    && "B".equals(root.getChildren().stream().filter(ComboBox.class::isInstance)
                        .map(ComboBox.class::cast).findFirst().orElseThrow().getValue());
              })) Thread.sleep(10);
      assertTrue(
          fx(
              () -> {
                javafx.scene.layout.Pane root = (javafx.scene.layout.Pane) preview.getScene().getRoot();
                return root.getChildren().stream().filter(TextField.class::isInstance)
                        .map(TextField.class::cast).findFirst().orElseThrow().getText().equals("Codalgora")
                    && !root.getChildren().stream().filter(CheckBox.class::isInstance)
                        .map(CheckBox.class::cast).findFirst().orElseThrow().isSelected()
                    && "B".equals(root.getChildren().stream().filter(ComboBox.class::isInstance)
                        .map(ComboBox.class::cast).findFirst().orElseThrow().getValue());
              }));
    } finally {
      fx(
          () -> {
            preview.hide();
            return null;
          });
    }
  }

  @Test
  void compactLayoutKeepsEditorUsableAndPanelsCanBeReopened() throws Exception {
    fx(
        () -> {
          stage.setWidth(820);
          stage.setHeight(620);
          return null;
        });
    await(
        () ->
            !node("filesPanel", javafx.scene.layout.VBox.class).isManaged()
                && !node("inspectionPanel", javafx.scene.layout.VBox.class).isManaged()
                && node("editorPane", javafx.scene.layout.VBox.class).getWidth() > 700);
    fx(
        () -> {
          capture("Codalgora-compacto.png");
          node("filesToggle", Button.class).fire();
          return null;
        });
    await(() -> node("filesPanel", javafx.scene.layout.VBox.class).isManaged());
    fx(
        () -> {
          node("inspectionToggle", Button.class).fire();
          return null;
        });
    await(
        () ->
            node("inspectionPanel", javafx.scene.layout.VBox.class).isManaged()
                && !node("filesPanel", javafx.scene.layout.VBox.class).isManaged()
                && node("editorPane", javafx.scene.layout.VBox.class).getWidth() > 480);
    fx(
        () -> {
          assertTrue(node("runButton", Button.class).isVisible());
          assertTrue(javafx.scene.text.Font.getFamilies().contains("IBM Plex Sans"));
          return null;
        });
  }
}
