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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
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

  @Test
  void creditsDialogShowsUnchangedBioAndClasspathPixQr() throws Exception {
    Window[] creditsWindow = new Window[1];
    try {
      fx(
          () -> {
            MenuBar menuBar = (MenuBar) stage.getScene().lookup(".menu-bar");
            Menu creditsMenu =
                menuBar.getMenus().stream()
                    .filter(menu -> menu.getText().equals("Créditos"))
                    .findFirst()
                    .orElseThrow();
            creditsMenu.getItems().getFirst().fire();
            creditsWindow[0] =
                Window.getWindows().stream()
                    .filter(window -> window != stage && window.isShowing())
                    .filter(window -> window.getScene() != null
                        && window.getScene().lookup("#pixDonationQr") != null)
                    .findFirst()
                    .orElseThrow();
            var root = creditsWindow[0].getScene().getRoot();
            root.applyCss();
            root.layout();
            javafx.scene.image.ImageView qr =
                (javafx.scene.image.ImageView) root.lookup("#pixDonationQr");
            assertEquals(1000, qr.getImage().getWidth());
            assertEquals(1000, qr.getImage().getHeight());
            assertEquals(320, qr.getFitWidth());
            assertTrue(qr.isPreserveRatio());
            assertFalse(qr.isSmooth());
            assertTrue(root.lookup("#pixDonationCard").getStyle().contains("white"));
            List<String> labels =
                root.lookupAll(".label").stream()
                    .filter(Label.class::isInstance)
                    .map(Label.class::cast)
                    .map(Label::getText)
                    .toList();
            assertTrue(labels.contains("Sobre o desenvolvedor"));
            assertTrue(labels.contains("Arthur Almeida"));
            assertTrue(labels.contains("Apoie o Codalgora ❤️"));
            assertTrue(
                labels.stream()
                    .anyMatch(
                        text -> text != null && text.contains("contribuição voluntária via Pix")));
            assertTrue(labels.contains("Escaneie com o aplicativo do seu banco para contribuir."));
            assertTrue(
                labels.stream()
                    .anyMatch(text -> text != null && text.contains("O Codalgora é gratuito")));
            assertTrue(
                root.lookupAll(".hyperlink").stream()
                    .anyMatch(
                        node ->
                            node instanceof Hyperlink link
                                && link.getText().equals("github.com/thurdass")));
            assertTrue(
                root.lookupAll(".button").stream()
                    .anyMatch(node -> node instanceof Button button && button.getText().equals("Fechar")));
            captureWindow(creditsWindow[0], "Codalgora-Creditos-Pix.png");
            ScrollPane creditsScroll = (ScrollPane) root.lookup(".scroll-pane");
            creditsScroll.setVvalue(1);
            root.applyCss();
            root.layout();
            captureWindow(creditsWindow[0], "Codalgora-Creditos-Pix-Doacao.png");
            return null;
          });
    } finally {
      if (creditsWindow[0] != null)
        fx(
            () -> {
              creditsWindow[0].hide();
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
    captureWindow(target, name);
  }

  private void captureWindow(Window target, String name) throws Exception {
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
          assertEquals("Créditos", menuBar.getMenus().get(3).getText());
          Menu helpMenu = menuBar.getMenus().get(5);
          assertEquals("Ajuda", helpMenu.getText());
          assertTrue(helpMenu.getItems().stream().noneMatch(item -> item.getText().equals("Créditos")));
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
          TreeView<?> outline = (TreeView<?>) builder[0].getScene().lookup("#guiOutline");
          selectOutlineComponent(outline, 1);
          ComboBox<String> variable =
              (ComboBox<String>) builder[0].getScene().lookup("#guiVariableName");
          assertTrue(variable.getItems().contains("nome"));
          variable.setValue("nome");
          variable.fireEvent(new ActionEvent());
          selectOutlineComponent(outline, 2);
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

    Stage exported = fx(Stage::new);
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

    Stage preview = fx(Stage::new);
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
            + "var\n"
            + "nome:caractere\n"
            + "ativo:logico\n"
            + "opcao:caractere\n"
            + "procedimento receber(var nome:caractere; var ativo:logico; var opcao:caractere)\n"
            + "inicio\n"
            + "se ativo entao\n"
            + "escreval(nome, \"|\", opcao)\n"
            + "senao\n"
            + "escreval(\"desmarcado\")\n"
            + "fimse\n"
            + "nome <- \"Codalgora\"\n"
            + "ativo <- falso\n"
            + "opcao <- \"B\"\n"
            + "fimprocedimento\n"
            + "inicio\n"
            + "nome <- \"\"\n"
            + "ativo <- falso\n"
            + "opcao <- \"A\"\n"
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
    choice.variableName = "opcao";
    form.elements.add(choice);
    Path formPath = directory.resolve("controles.cgui.json");
    GuiFormStore.write(formPath, form);

    Stage preview = fx(Stage::new);
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
  void eventErrorsAreReportedToTheApplicationOutput() throws Exception {
    Path algorithm = directory.resolve("erro-evento.alg");
    Files.writeString(
        algorithm,
        "algoritmo \"erro de evento\"\n"
            + "var total:inteiro\n"
            + "procedimento falhar\n"
            + "inicio\n"
            + "total <- 1 / 0\n"
            + "fimprocedimento\n"
            + "inicio\n"
            + "total <- 0\n"
            + "fimalgoritmo\n");
    GuiForm form = new GuiForm();
    form.algorithmPath = algorithm.getFileName().toString();
    GuiForm.Element button =
        new GuiForm.Element(GuiForm.Type.BUTTON, "falhar", "Falhar", 16, 16, 160, 38);
    button.eventRoutine = "falhar";
    form.elements.add(button);
    Path formPath = directory.resolve("erro-evento.cgui.json");
    GuiFormStore.write(formPath, form);

    Stage preview = fx(Stage::new);
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
    try {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (System.nanoTime() < deadline
          && !String.join("", output).contains("Divisão por zero")) Thread.sleep(10);
      assertTrue(
          String.join("", output).contains("Não foi possível executar o evento:"),
          "Saída recebida: " + String.join("", output));
    } finally {
      fx(
          () -> {
            preview.hide();
            return null;
          });
    }
  }

  private static void mouse(javafx.scene.Node target, javafx.event.EventType<MouseEvent> type,
                            double x, double y, int clicks) {
    Event.fireEvent(target, new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, clicks,
        false, false, false, false, type != MouseEvent.MOUSE_RELEASED,
        false, false, false, false, true, new PickResult(target, x, y)));
  }

  private static <T> void selectOutlineComponent(TreeView<T> outline, int index) {
    outline.getSelectionModel().select(outline.getRoot().getChildren().get(index));
  }

  private GuiForm saveAndReadForm(GuiBuilderPane pane, Path path) throws Exception {
    pane.saveForm(path);
    return GuiFormStore.read(path);
  }

  @Test
  void paletteGroupsEverySupportedControl() throws Exception {
    Stage builder =
        fx(
            () -> {
              GuiBuilderPane.open(
                  stage,
                  CodalgoraApplication.class.getResource("codalgora.css").toExternalForm(),
                  EditorTheme.GRAPHITE.css(),
                  ignored -> {});
              return Window.getWindows().stream()
                  .filter(window -> window instanceof Stage candidate
                      && candidate.getTitle().startsWith("Construtor visual"))
                  .map(Stage.class::cast)
                  .findFirst()
                  .orElseThrow();
            });
    try {
      fx(
          () -> {
            TitledPane basic = (TitledPane) builder.getScene().lookup("#guiPaletteBasic");
            TitledPane input = (TitledPane) builder.getScene().lookup("#guiPaletteInput");
            assertEquals("Básicos", basic.getText());
            assertEquals("Entrada de dados", input.getText());
            assertEquals(
                List.of("palette-LABEL", "palette-BUTTON"),
                ((VBox) basic.getContent()).getChildren().stream()
                    .map(node -> node.getId())
                    .toList());
            assertEquals(
                List.of("palette-TEXT_FIELD", "palette-TEXT_AREA", "palette-CHECK_BOX", "palette-COMBO_BOX"),
                ((VBox) input.getContent()).getChildren().stream()
                    .map(node -> node.getId())
                    .toList());
            assertTrue(basic.isExpanded());
            assertTrue(input.isExpanded());
            return null;
          });
    } finally {
      fx(
          () -> {
            builder.hide();
            return null;
          });
    }
  }

  @Test
  void paletteSearchFiltersByNameAndShowsRecoveryForNoResults() throws Exception {
    Stage builder =
        fx(
            () -> {
              GuiBuilderPane.open(
                  stage,
                  CodalgoraApplication.class.getResource("codalgora.css").toExternalForm(),
                  EditorTheme.GRAPHITE.css(),
                  ignored -> {});
              return Window.getWindows().stream()
                  .filter(window -> window instanceof Stage candidate
                      && candidate.getTitle().startsWith("Construtor visual"))
                  .map(Stage.class::cast)
                  .findFirst()
                  .orElseThrow();
            });
    try {
      fx(
          () -> {
            TextField search = (TextField) builder.getScene().lookup("#guiPaletteSearch");
            TitledPane basic = (TitledPane) builder.getScene().lookup("#guiPaletteBasic");
            TitledPane input = (TitledPane) builder.getScene().lookup("#guiPaletteInput");
            Button checkBox = (Button) builder.getScene().lookup("#palette-CHECK_BOX");
            Button textField = (Button) builder.getScene().lookup("#palette-TEXT_FIELD");

            search.setText("selecao");
            assertFalse(basic.isManaged());
            assertTrue(input.isManaged());
            assertTrue(checkBox.isManaged());
            assertFalse(textField.isManaged());

            search.setText("CAMPO");
            assertTrue(textField.isManaged());
            assertFalse(checkBox.isManaged());

            search.setText("nao existe");
            assertTrue(((Label) builder.getScene().lookup("#guiPaletteEmpty")).isManaged());
            assertFalse(basic.isManaged());
            assertFalse(input.isManaged());

            search.clear();
            assertFalse(((Label) builder.getScene().lookup("#guiPaletteEmpty")).isManaged());
            assertTrue(basic.isManaged());
            assertTrue(input.isManaged());
            assertTrue(checkBox.isManaged());
            assertTrue(textField.isManaged());

            search.setText("campo");
            textField.fire();
            assertEquals(
                1,
                ((javafx.scene.layout.Pane) builder.getScene().lookup("#guiCanvas"))
                    .getChildren()
                    .size());
            Event.fireEvent(
                search,
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                    false, false, false, false));
            assertTrue(search.getText().isEmpty());
            return null;
          });
    } finally {
      fx(
          () -> {
            builder.hide();
            return null;
          });
    }
  }

  @Test
  void guiBuilderWorkspaceDividersResizeAndFollowEveryTheme() throws Exception {
    Stage builder =
        fx(
            () -> {
              GuiBuilderPane.open(
                  stage,
                  CodalgoraApplication.class.getResource("codalgora.css").toExternalForm(),
                  EditorTheme.GRAPHITE.css(),
                  ignored -> {});
              return Window.getWindows().stream()
                  .filter(window -> window instanceof Stage candidate
                      && candidate.getTitle().startsWith("Construtor visual"))
                  .map(Stage.class::cast)
                  .findFirst()
                  .orElseThrow();
            });
    try {
      fx(
          () -> {
            builder.setWidth(920);
            return null;
          });
      await(() -> builder.getScene().getWidth() <= 930);
      double compactCanvasWidth =
          fx(
              () -> {
                builder.getScene().getRoot().applyCss();
                builder.getScene().getRoot().layout();
                SplitPane split = (SplitPane) builder.getScene().lookup("#guiWorkspaceSplit");
                assertEquals(3, split.getItems().size());
                assertEquals(2, split.getDividers().size());
                VBox palette = (VBox) split.getItems().get(0);
                BorderPane design = (BorderPane) split.getItems().get(1);
                ScrollPane properties = (ScrollPane) split.getItems().get(2);
                assertTrue(palette.getWidth() >= palette.getMinWidth());
                assertTrue(design.getWidth() >= design.getMinWidth());
                assertTrue(properties.getWidth() >= properties.getMinWidth());

                double initialPaletteWidth = palette.getWidth();
                javafx.scene.Node divider = split.lookup(".split-pane-divider");
                assertNotNull(divider);
                var point =
                    divider.localToScene(
                        divider.getBoundsInLocal().getWidth() / 2,
                        divider.getBoundsInLocal().getHeight() / 2);
                mouse(divider, MouseEvent.MOUSE_PRESSED, point.getX(), point.getY(), 1);
                mouse(divider, MouseEvent.MOUSE_DRAGGED, point.getX() + 24, point.getY(), 1);
                mouse(divider, MouseEvent.MOUSE_RELEASED, point.getX() + 24, point.getY(), 1);
                builder.getScene().getRoot().layout();
                assertTrue(palette.getWidth() > initialPaletteWidth + 10);
                captureStage(builder, "Codalgora-GUI-Compacto.png");
                return design.getWidth();
              });
      double targetWidth = Math.min(1180, Screen.getPrimary().getVisualBounds().getWidth() - 80);
      if (targetWidth > 930) {
        fx(
            () -> {
              builder.setWidth(targetWidth);
              return null;
            });
        await(() -> builder.getScene().getWidth() >= targetWidth - 30);
      }
      fx(
          () -> {
            builder.getScene().getRoot().layout();
            SplitPane split = (SplitPane) builder.getScene().lookup("#guiWorkspaceSplit");
            VBox palette = (VBox) split.getItems().get(0);
            BorderPane design = (BorderPane) split.getItems().get(1);
            ScrollPane properties = (ScrollPane) split.getItems().get(2);
            if (targetWidth > 930)
              assertTrue(design.getWidth() > compactCanvasWidth + (targetWidth - 920) * 0.15);
            assertTrue(palette.getWidth() >= palette.getMinWidth());
            assertTrue(design.getWidth() >= design.getMinWidth());
            assertTrue(properties.getWidth() >= properties.getMinWidth());

            javafx.scene.layout.Region dividerRegion =
                (javafx.scene.layout.Region) split.lookup(".split-pane-divider");
            for (EditorTheme theme : EditorTheme.values()) {
              builder.getScene().getRoot().setStyle(theme.css());
              builder.getScene().getRoot().applyCss();
              String lineColor =
                  java.util.Arrays.stream(theme.css().split(";"))
                      .filter(token -> token.strip().startsWith("-alg-line:"))
                      .map(token -> token.substring(token.indexOf(':') + 1).strip())
                      .findFirst()
                      .orElseThrow();
              assertEquals(
                  javafx.scene.paint.Color.web(lineColor),
                  dividerRegion.getBackground().getFills().getFirst().getFill(),
                  theme.label);
            }
            builder.getScene().getRoot().setStyle(EditorTheme.GRAPHITE.css());
            builder.getScene().getRoot().applyCss();
            captureStage(builder, "Codalgora-GUI-Paineis.png");
            return null;
          });
    } finally {
      fx(
          () -> {
            builder.hide();
            return null;
          });
    }
  }

  @Test
  void componentTreeSelectsCanvasItemsAndWindowAndLoadsLegacyForms() throws Exception {
    String legacyForm = """
        {"schemaVersion":1,"title":"Formulário antigo","width":420,"height":520,
         "elements":[
          {"id":"btn-antigo","type":"BUTTON","name":"calcular","text":"Calcular",
           "x":16,"y":16,"width":120,"height":38},
          {"id":"rotulo-antigo","type":"LABEL","name":"resultado","text":"Resultado",
           "x":80,"y":200,"width":180,"height":48}]}
        """;
    Path legacy = directory.resolve("formulario-legado.cgui.json");
    Path saved = directory.resolve("formulario-legado-salvo.cgui.json");
    Files.writeString(legacy, legacyForm);
    Stage builder =
        fx(
            () -> {
              GuiBuilderPane.open(
                  stage,
                  CodalgoraApplication.class.getResource("codalgora.css").toExternalForm(),
                  EditorTheme.GRAPHITE.css(),
                  ignored -> {});
              return Window.getWindows().stream()
                  .filter(window -> window instanceof Stage candidate
                      && candidate.getTitle().startsWith("Construtor visual"))
                  .map(Stage.class::cast)
                  .findFirst()
                  .orElseThrow();
            });
    try {
      fx(
          () -> {
            GuiBuilderPane pane = (GuiBuilderPane) builder.getScene().getRoot();
            pane.loadForm(legacy);
            pane.applyCss();
            pane.layout();
            TreeView<?> tree = (TreeView<?>) pane.lookup("#guiOutline");
            assertNotNull(tree.getRoot());
            assertEquals(2, tree.getRoot().getChildren().size());
            assertSame(tree.getRoot(), tree.getSelectionModel().getSelectedItem());
            assertTrue(((TitledPane) pane.lookup("#guiWindowProperties")).isExpanded());
            assertFalse(((VBox) pane.lookup("#guiComponentInspector")).isManaged());

            selectOutlineComponent(tree, 1);
            assertSame(tree.getRoot().getChildren().get(1), tree.getSelectionModel().getSelectedItem());
            assertTrue(((VBox) pane.lookup("#guiComponentInspector")).isManaged());
            assertFalse(((TitledPane) pane.lookup("#guiWindowProperties")).isExpanded());

            javafx.scene.layout.Pane canvas = (javafx.scene.layout.Pane) pane.lookup("#guiCanvas");
            javafx.scene.Node firstControl = canvas.getChildren().getFirst();
            var controlPoint = firstControl.localToScene(20, 20);
            mouse(firstControl, MouseEvent.MOUSE_PRESSED, controlPoint.getX(), controlPoint.getY(), 1);
            assertSame(tree.getRoot().getChildren().getFirst(), tree.getSelectionModel().getSelectedItem());

            mouse(canvas, MouseEvent.MOUSE_PRESSED, 390, 490, 1);
            assertSame(tree.getRoot(), tree.getSelectionModel().getSelectedItem());
            assertTrue(((TitledPane) pane.lookup("#guiWindowProperties")).isExpanded());
            assertFalse(((VBox) pane.lookup("#guiComponentInspector")).isManaged());

            pane.saveForm(saved);
            GuiForm reopened = GuiFormStore.read(saved);
            assertEquals(1, reopened.schemaVersion);
            assertEquals("Formulário antigo", reopened.title);
            assertEquals("calcular", reopened.elements.getFirst().name);
            assertEquals("Resultado", reopened.elements.get(1).text);
            assertNotNull(reopened.elements.getFirst().appearance);
            return null;
          });
    } finally {
      fx(
          () -> {
            builder.hide();
            return null;
          });
    }
  }

  @Test
  void formBackgroundColorPersistsThroughPreviewUndoAndReload() throws Exception {
    GuiForm initial = new GuiForm();
    initial.elements.add(
        new GuiForm.Element(GuiForm.Type.BUTTON, "continuar", "Continuar", 16, 16, 140, 40));
    Path source = directory.resolve("fundo.cgui.json");
    Path saved = directory.resolve("fundo-salvo.cgui.json");
    GuiFormStore.write(source, initial);
    Stage builder =
        fx(
            () -> {
              GuiBuilderPane.open(
                  stage,
                  CodalgoraApplication.class.getResource("codalgora.css").toExternalForm(),
                  EditorTheme.GRAPHITE.css(),
                  ignored -> {});
              return Window.getWindows().stream()
                  .filter(window -> window instanceof Stage candidate
                      && candidate.getTitle().startsWith("Construtor visual"))
                  .map(Stage.class::cast)
                  .findFirst()
                  .orElseThrow();
            });
    try {
      fx(
          () -> {
            GuiBuilderPane pane = (GuiBuilderPane) builder.getScene().getRoot();
            pane.loadForm(source);
            pane.applyCss();
            pane.layout();
            TextField background = (TextField) pane.lookup("#guiFormBackgroundHex");
            background.setText("#12345680");
            background.fireEvent(new ActionEvent());
            javafx.scene.layout.Pane designCanvas =
                (javafx.scene.layout.Pane) pane.lookup("#guiCanvas");
            pane.applyCss();
            assertTrue(designCanvas.getStyle().contains("-fx-background-color: #12345680;"));
            assertEquals(
                javafx.scene.paint.Color.web("#12345680"),
                designCanvas.getBackground().getFills().getFirst().getFill());
            pane.saveForm(saved);
            assertEquals("#12345680", GuiFormStore.read(saved).backgroundColor);

            ((Button) pane.lookup("#guiUndo")).fire();
            pane.saveForm(saved);
            assertNull(GuiFormStore.read(saved).backgroundColor);
            ((Button) pane.lookup("#guiRedo")).fire();
            pane.saveForm(saved);
            pane.loadForm(saved);
            assertEquals("#12345680", ((TextField) pane.lookup("#guiFormBackgroundHex")).getText());

            Stage preview = new Stage();
            try {
              GuiBuilderPane.openFormPreview(
                  preview,
                  CodalgoraApplication.class.getResource("codalgora.css").toExternalForm(),
                  EditorTheme.GRAPHITE.css(),
                  saved,
                  ignored -> {});
              preview.getScene().getRoot().applyCss();
              assertEquals(
                  javafx.scene.paint.Color.web("#12345680"),
                  ((javafx.scene.layout.Region) preview.getScene().getRoot())
                      .getBackground().getFills().getFirst().getFill());
            } finally {
              preview.hide();
            }

            ((Button) pane.lookup("#guiResetFormBackground")).fire();
            pane.saveForm(saved);
            assertNull(GuiFormStore.read(saved).backgroundColor);
            return null;
          });
    } finally {
      fx(
          () -> {
            builder.hide();
            return null;
          });
    }
  }

  @Test
  void guiBuilderOpensMaximized() throws Exception {
    Stage builder =
        fx(
            () -> {
              GuiBuilderPane.open(
                  stage,
                  CodalgoraApplication.class.getResource("codalgora.css").toExternalForm(),
                  EditorTheme.GRAPHITE.css(),
                  ignored -> {});
              return Window.getWindows().stream()
                  .filter(window -> window instanceof Stage candidate
                      && candidate.getTitle().startsWith("Construtor visual"))
                  .map(Stage.class::cast)
                  .findFirst()
                  .orElseThrow();
            });
    try {
      fx(
          () -> {
            assertTrue(builder.isMaximized());
            return null;
          });
    } finally {
      fx(
          () -> {
            builder.hide();
            return null;
          });
    }
  }

  @Test
  void componentTreeRenamesAndDeletesWithValidationAndHistory() throws Exception {
    GuiForm initial = new GuiForm();
    initial.elements.add(
        new GuiForm.Element(GuiForm.Type.BUTTON, "calcular", "Calcular", 16, 16, 120, 38));
    initial.elements.add(
        new GuiForm.Element(GuiForm.Type.LABEL, "resultado", "Resultado", 80, 200, 180, 48));
    Path source = directory.resolve("arvore.cgui.json");
    Path saved = directory.resolve("arvore-salva.cgui.json");
    GuiFormStore.write(source, initial);
    Stage builder =
        fx(
            () -> {
              GuiBuilderPane.open(
                  stage,
                  CodalgoraApplication.class.getResource("codalgora.css").toExternalForm(),
                  EditorTheme.GRAPHITE.css(),
                  ignored -> {});
              return Window.getWindows().stream()
                  .filter(window -> window instanceof Stage candidate
                      && candidate.getTitle().startsWith("Construtor visual"))
                  .map(Stage.class::cast)
                  .findFirst()
                  .orElseThrow();
            });
    try {
      fx(
          () -> {
            GuiBuilderPane pane = (GuiBuilderPane) builder.getScene().getRoot();
            pane.loadForm(source);
            pane.applyCss();
            pane.layout();
            TreeView<?> tree = (TreeView<?>) pane.lookup("#guiOutline");
            ContextMenu actions = tree.getContextMenu();
            MenuItem rename = actions.getItems().get(0);
            MenuItem delete = actions.getItems().get(1);
            selectOutlineComponent(tree, 0);

            rename.fire();
            pane.applyCss();
            pane.layout();
            TextField editor = (TextField) tree.lookup("#guiOutlineNameEditor");
            assertNotNull(editor);
            editor.setText("resultado");
            editor.fireEvent(new ActionEvent());
            assertEquals("calcular", GuiFormStore.read(source).elements.getFirst().name);
            assertTrue(
                ((Label) ((VBox) pane.lookup(".statusbar")).getChildren().getFirst())
                    .getText()
                    .contains("Já existe"));

            rename.fire();
            pane.applyCss();
            pane.layout();
            editor = (TextField) tree.lookup("#guiOutlineNameEditor");
            assertNotNull(editor);
            editor.setText("calcular_principal");
            editor.fireEvent(new ActionEvent());
            assertEquals("calcular_principal", saveAndReadForm(pane, saved).elements.getFirst().name);
            ((Button) pane.lookup("#guiUndo")).fire();
            assertEquals("calcular", saveAndReadForm(pane, saved).elements.getFirst().name);
            ((Button) pane.lookup("#guiRedo")).fire();
            assertEquals("calcular_principal", saveAndReadForm(pane, saved).elements.getFirst().name);

            selectOutlineComponent(tree, 0);
            delete.fire();
            assertEquals(1, tree.getRoot().getChildren().size());
            assertEquals(1, ((javafx.scene.layout.Pane) pane.lookup("#guiCanvas")).getChildren().size());
            ((Button) pane.lookup("#guiUndo")).fire();
            assertEquals(2, tree.getRoot().getChildren().size());
            assertEquals("calcular_principal", saveAndReadForm(pane, saved).elements.getFirst().name);
            pane.loadForm(saved);
            assertEquals("calcular_principal", GuiFormStore.read(saved).elements.getFirst().name);
            return null;
          });
    } finally {
      fx(
          () -> {
            builder.hide();
            return null;
          });
    }
  }

  @Test
  void componentTreeReordersElementsAndPreservesSelectionAndHistory() throws Exception {
    GuiForm initial = new GuiForm();
    initial.elements.add(
        new GuiForm.Element(GuiForm.Type.BUTTON, "primeiro", "Primeiro", 16, 16, 120, 38));
    initial.elements.add(
        new GuiForm.Element(GuiForm.Type.LABEL, "segundo", "Segundo", 80, 100, 180, 48));
    initial.elements.add(
        new GuiForm.Element(GuiForm.Type.TEXT_FIELD, "terceiro", "Terceiro", 80, 180, 180, 36));
    Path source = directory.resolve("ordem.cgui.json");
    Path saved = directory.resolve("ordem-salva.cgui.json");
    GuiFormStore.write(source, initial);
    Stage builder =
        fx(
            () -> {
              GuiBuilderPane.open(
                  stage,
                  CodalgoraApplication.class.getResource("codalgora.css").toExternalForm(),
                  EditorTheme.GRAPHITE.css(),
                  ignored -> {});
              return Window.getWindows().stream()
                  .filter(window -> window instanceof Stage candidate
                      && candidate.getTitle().startsWith("Construtor visual"))
                  .map(Stage.class::cast)
                  .findFirst()
                  .orElseThrow();
            });
    try {
      fx(
          () -> {
            GuiBuilderPane pane = (GuiBuilderPane) builder.getScene().getRoot();
            pane.loadForm(source);
            pane.applyCss();
            pane.layout();
            TreeView<?> tree = (TreeView<?>) pane.lookup("#guiOutline");
            ContextMenu actions = tree.getContextMenu();
            MenuItem moveUp = actions.getItems().get(3);
            MenuItem moveDown = actions.getItems().get(4);
            selectOutlineComponent(tree, 1);

            moveUp.fire();
            assertEquals("segundo", saveAndReadForm(pane, saved).elements.get(0).name);
            assertEquals(
                "segundo",
                ((GuiForm.Element) tree.getSelectionModel().getSelectedItem().getValue()).name);
            assertEquals("primeiro", GuiFormStore.read(source).elements.getFirst().name);
            ((Button) pane.lookup("#guiUndo")).fire();
            assertEquals("primeiro", saveAndReadForm(pane, saved).elements.getFirst().name);
            ((Button) pane.lookup("#guiRedo")).fire();
            assertEquals("segundo", saveAndReadForm(pane, saved).elements.getFirst().name);

            selectOutlineComponent(tree, 0);
            moveDown.fire();
            assertEquals("primeiro", saveAndReadForm(pane, saved).elements.getFirst().name);
            assertEquals("segundo", saveAndReadForm(pane, saved).elements.get(1).name);
            assertEquals("terceiro", saveAndReadForm(pane, saved).elements.get(2).name);
            return null;
          });
    } finally {
      fx(
          () -> {
            builder.hide();
            return null;
          });
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void visualEditingHandlesTextAppearanceAndManagedLayouts() throws Exception {
    GuiForm initial = new GuiForm();
    initial.elements.add(new GuiForm.Element(GuiForm.Type.BUTTON, "calcular", "Botão 1", 80, 80, 180, 48));
    initial.elements.add(new GuiForm.Element(GuiForm.Type.LABEL, "resultado", "Resultado", 80, 200, 180, 48));
    Path original = directory.resolve("original.cgui.json"), edited = directory.resolve("editado.cgui.json");
    GuiFormStore.write(original, initial);
    Stage builder = fx(() -> {
      GuiBuilderPane.open(stage, CodalgoraApplication.class.getResource("codalgora.css").toExternalForm(), EditorTheme.saved().css(), ignored -> {});
      return Window.getWindows().stream().filter(w -> w instanceof Stage s && s.getTitle().startsWith("Construtor visual"))
          .map(Stage.class::cast).findFirst().orElseThrow();
    });
    GuiBuilderPane pane = fx(() -> (GuiBuilderPane) builder.getScene().getRoot());
    try {
      for (String direction : new String[] {"NW", "N", "NE", "E", "SE", "S", "SW", "W"}) {
        fx(() -> {
          pane.loadForm(original);
          CheckBox snap = (CheckBox) pane.lookup("#guiSnap");
          if (snap.isSelected()) snap.fire();
          selectOutlineComponent((TreeView<?>) pane.lookup("#guiOutline"), 0);
          pane.applyCss(); pane.layout();
          javafx.scene.Node handle = pane.lookup("#guiResize-" + direction);
          assertTrue(handle.isVisible());
          var point = handle.localToScene(4.5, 4.5);
          double dx = direction.contains("W") ? -16 : direction.contains("E") ? 16 : 0;
          double dy = direction.contains("N") ? -16 : direction.contains("S") ? 16 : 0;
          mouse(handle, MouseEvent.MOUSE_PRESSED, point.getX(), point.getY(), 1);
          mouse(handle, MouseEvent.MOUSE_DRAGGED, point.getX()+dx, point.getY()+dy, 1);
          mouse(handle, MouseEvent.MOUSE_RELEASED, point.getX()+dx, point.getY()+dy, 1);
          pane.saveForm(edited);
          GuiForm.Element resized = GuiFormStore.read(edited).elements.getFirst();
          assertEquals(180 + (dx == 0 ? 0 : 16), resized.width, direction);
          assertEquals(48 + (dy == 0 ? 0 : 16), resized.height, direction);
          ((Button) pane.lookup("#guiUndo")).fire(); pane.saveForm(edited);
          assertEquals(180, GuiFormStore.read(edited).elements.getFirst().width);
          ((Button) pane.lookup("#guiRedo")).fire(); pane.saveForm(edited);
          assertEquals(resized.width, GuiFormStore.read(edited).elements.getFirst().width);
          return null;
        });
      }
      fx(() -> {
        pane.loadForm(original);
        selectOutlineComponent((TreeView<?>) pane.lookup("#guiOutline"), 0);
        pane.applyCss(); pane.layout();
        Event.fireEvent(pane, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F2, false, false, false, false));
        TextField inline = (TextField) pane.lookup("#guiInlineText");
        assertTrue(inline.isVisible()); inline.setText("Cancelar edição");
        Event.fireEvent(inline, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
        pane.saveForm(edited); assertEquals("Botão 1", GuiFormStore.read(edited).elements.getFirst().text);
        javafx.scene.Node button = ((javafx.scene.layout.Pane) pane.lookup("#guiCanvas")).getChildren().getFirst();
        var point = button.localToScene(20, 20);
        mouse(button, MouseEvent.MOUSE_CLICKED, point.getX(), point.getY(), 2);
        inline.setText("Calcular"); inline.fireEvent(new ActionEvent());
        pane.saveForm(edited);
        assertEquals("Calcular", GuiFormStore.read(edited).elements.getFirst().text);
        assertEquals("calcular", GuiFormStore.read(edited).elements.getFirst().name);
        ((Button) pane.lookup("#guiUndo")).fire(); pane.saveForm(edited);
        assertEquals("Botão 1", GuiFormStore.read(edited).elements.getFirst().text);
        ((Button) pane.lookup("#guiRedo")).fire();
        selectOutlineComponent((TreeView<?>) pane.lookup("#guiOutline"), 0);
        ColorPicker background = (ColorPicker) pane.lookup("#guiBackgroundColor");
        background.setValue(javafx.scene.paint.Color.web("#12345680")); background.fireEvent(new ActionEvent());
        ((Spinner<Double>) pane.lookup("#guiFontSize")).getValueFactory().setValue(22.0);
        ((CheckBox) pane.lookup("#guiBold")).fire();
        ((ComboBox<GuiForm.Alignment>) pane.lookup("#guiAlignment")).setValue(GuiForm.Alignment.CENTER);
        pane.saveForm(edited);
        GuiForm styled = GuiFormStore.read(edited);
        assertEquals("#12345680", styled.elements.getFirst().appearance.normal.background);
        assertEquals(22, styled.elements.getFirst().appearance.fontSize);
        assertTrue(styled.elements.getFirst().appearance.bold);
        Event.fireEvent(pane, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.D, false, true, false, false));
        pane.saveForm(edited); assertEquals(3, GuiFormStore.read(edited).elements.size());
        ((Button) pane.lookup("#guiUndo")).fire(); pane.saveForm(edited);
        assertEquals(2, GuiFormStore.read(edited).elements.size());
        pane.loadForm(edited);
        selectOutlineComponent((TreeView<?>) pane.lookup("#guiOutline"), 0);
        captureStage(builder, "Codalgora-GUI-Edicao.png");
        return null;
      });
      for (GuiForm.LayoutMode layout : GuiForm.LayoutMode.values()) {
        fx(() -> {
          ((ComboBox<GuiForm.LayoutMode>) pane.lookup("#guiLayoutMode")).setValue(layout);
          selectOutlineComponent((TreeView<?>) pane.lookup("#guiOutline"), 0);
          pane.applyCss(); pane.layout();
          javafx.scene.Node handle = pane.lookup("#guiResize-SE");
          var point = handle.localToScene(4.5,4.5);
          mouse(handle, MouseEvent.MOUSE_PRESSED, point.getX(), point.getY(), 1);
          mouse(handle, MouseEvent.MOUSE_DRAGGED, point.getX()+16, point.getY()+8, 1);
          mouse(handle, MouseEvent.MOUSE_RELEASED, point.getX()+16, point.getY()+8, 1);
          pane.saveForm(edited);
          GuiForm saved = GuiFormStore.read(edited);
          assertEquals(80, saved.elements.getFirst().x);
          assertEquals(80, saved.elements.getFirst().y);
          Stage preview = new Stage();
          GuiBuilderPane.openFormPreview(preview, CodalgoraApplication.class.getResource("codalgora.css").toExternalForm(), EditorTheme.saved().css(), edited, ignored -> {});
          try {
            javafx.scene.Node node = ((javafx.scene.layout.Pane) preview.getScene().getRoot()).getChildren().getFirst();
            assertTrue(node.getStyle().contains("#12345680"));
            assertTrue(node.getStyle().contains("22.0px"));
            assertEquals("Calcular", ((Button) node).getText());
          } finally { preview.hide(); }
          return null;
        });
      }
    } finally { fx(() -> { builder.hide(); return null; }); }
  }

  @Test
  @SuppressWarnings("unchecked")
  void designDragZoomCopyAndReorderNeverRunPortugol() throws Exception {
    Files.writeString(directory.resolve("design.alg"), """
        algoritmo "design separado"
        procedimento agir
        inicio
        escreval("acionado")
        fimprocedimento
        inicio
        escreval("execucao")
        fimalgoritmo
        """);
    GuiForm form = new GuiForm(); form.algorithmPath = "design.alg";
    var button = new GuiForm.Element(GuiForm.Type.BUTTON, "acao", "Agir", 80, 80, 160, 48);
    button.eventRoutine = "agir"; form.elements.add(button);
    form.elements.add(new GuiForm.Element(GuiForm.Type.LABEL, "rotulo", "Saída", 80, 240, 160, 48));
    Path file = directory.resolve("design.cgui.json"), saved = directory.resolve("design-salvo.cgui.json");
    GuiFormStore.write(file, form);
    ConcurrentLinkedQueue<String> output = new ConcurrentLinkedQueue<>();
    Stage builder = fx(() -> {
      GuiBuilderPane.open(stage, CodalgoraApplication.class.getResource("codalgora.css").toExternalForm(), EditorTheme.saved().css(), output::add);
      return Window.getWindows().stream().filter(w -> w instanceof Stage s && s.getTitle().startsWith("Construtor visual"))
          .map(Stage.class::cast).findFirst().orElseThrow();
    });
    try {
      fx(() -> {
        GuiBuilderPane pane = (GuiBuilderPane) builder.getScene().getRoot(); pane.loadForm(file);
        pane.applyCss(); pane.layout();
        javafx.scene.layout.Pane canvas = (javafx.scene.layout.Pane) pane.lookup("#guiCanvas");
        javafx.scene.Node node = canvas.getChildren().getFirst(); var point = node.localToScene(20, 20);
        mouse(node, MouseEvent.MOUSE_PRESSED, point.getX(), point.getY(), 1);
        mouse(node, MouseEvent.MOUSE_RELEASED, point.getX(), point.getY(), 1);
        pane.saveForm(saved); assertEquals(80, GuiFormStore.read(saved).elements.getFirst().x);
        mouse(node, MouseEvent.MOUSE_PRESSED, point.getX(), point.getY(), 1);
        mouse(node, MouseEvent.MOUSE_DRAGGED, point.getX()+18, point.getY()+20, 1);
        mouse(node, MouseEvent.MOUSE_RELEASED, point.getX()+18, point.getY()+20, 1);
        pane.saveForm(saved);
        assertEquals(96, GuiFormStore.read(saved).elements.getFirst().x);
        assertEquals(104, GuiFormStore.read(saved).elements.getFirst().y);
        ((CheckBox) pane.lookup("#guiSnap")).fire();
        ((Slider) pane.lookup("#guiZoom")).setValue(150);
        pane.layout(); point = node.localToScene(20,20);
        mouse(node, MouseEvent.MOUSE_PRESSED, point.getX(), point.getY(), 1);
        mouse(node, MouseEvent.MOUSE_DRAGGED, point.getX()+30, point.getY()+30, 1);
        mouse(node, MouseEvent.MOUSE_RELEASED, point.getX()+30, point.getY()+30, 1);
        pane.saveForm(saved);
        assertEquals(116, GuiFormStore.read(saved).elements.getFirst().x, .01);
        assertEquals(124, GuiFormStore.read(saved).elements.getFirst().y, .01);
        Event.fireEvent(pane, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.C, false,true,false,false));
        Event.fireEvent(pane, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.V, false,true,false,false));
        pane.saveForm(saved); assertEquals(3, GuiFormStore.read(saved).elements.size());
        ((Button) pane.lookup("#guiUndo")).fire();
        ((ComboBox<GuiForm.LayoutMode>) pane.lookup("#guiLayoutMode")).setValue(GuiForm.LayoutMode.VERTICAL);
        pane.applyCss(); pane.layout();
        javafx.scene.layout.Pane column = (javafx.scene.layout.Pane) canvas.getChildren().getFirst();
        node = column.getChildren().getFirst(); var end = column.getChildren().get(1).localToScene(20,20); point = node.localToScene(20,20);
        mouse(node, MouseEvent.MOUSE_PRESSED, point.getX(), point.getY(), 1);
        mouse(node, MouseEvent.MOUSE_DRAGGED, end.getX(), end.getY(), 1);
        mouse(node, MouseEvent.MOUSE_RELEASED, end.getX(), end.getY(), 1);
        pane.saveForm(saved);
        assertEquals("rotulo", GuiFormStore.read(saved).elements.getFirst().name);
        assertEquals("agir", GuiFormStore.read(saved).elements.get(1).eventRoutine);
        assertTrue(output.isEmpty(), "Gestos de Design não devem executar o algoritmo");
        ((Button) pane.lookup("#guiFit")).fire();
        captureStage(builder, "Codalgora-GUI-Layout.png");
        return null;
      });
    } finally { fx(() -> { builder.hide(); return null; }); }
  }

  @Test
  void comboAppearanceReachesTheDisplayedValue() throws Exception {
    GuiForm form = new GuiForm();
    var element = new GuiForm.Element(GuiForm.Type.COMBO_BOX, "opcao", "A | B", 20, 20, 180, 48);
    element.appearance.normal.foreground = "#0f2345";
    element.appearance.normal.background = "#eeeeee";
    element.appearance.fontSize = 20;
    form.elements.add(element);
    Path file = directory.resolve("combo-estilizado.cgui.json"); GuiFormStore.write(file, form);
    fx(() -> {
      Stage preview = new Stage();
      try {
        GuiBuilderPane.openFormPreview(preview, CodalgoraApplication.class.getResource("codalgora.css").toExternalForm(), EditorTheme.saved().css(), file, ignored -> {});
        preview.getScene().getRoot().applyCss(); preview.getScene().getRoot().layout();
        ComboBox<?> combo = (ComboBox<?>) ((javafx.scene.layout.Pane) preview.getScene().getRoot()).getChildren().getFirst();
        assertEquals(javafx.scene.paint.Color.web("#0f2345"), combo.getButtonCell().getTextFill());
        assertEquals(20, combo.getButtonCell().getFont().getSize());
      } finally { preview.hide(); }
      return null;
    });
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
