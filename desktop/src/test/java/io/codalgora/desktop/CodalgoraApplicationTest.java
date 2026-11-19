package io.codalgora.desktop;

import static org.junit.jupiter.api.Assertions.*;

import io.codalgora.desktop.guibuilder.GuiBuilderPane;
import io.codalgora.desktop.guibuilder.GuiForm;
import java.awt.image.BufferedImage;
import java.nio.file.*;
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
