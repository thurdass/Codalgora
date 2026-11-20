package io.codalgora.desktop.ui;

import static io.codalgora.desktop.ui.components.IdeControls.*;

import io.codalgora.desktop.documents.DocumentManager;
import io.codalgora.desktop.documents.EditorDocument;
import io.codalgora.desktop.guibuilder.GuiBuilderPane;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/** Existing branding, application menus, panel toggles and help dialog. */
public final class ApplicationHeader {
  private final Stage stage;
  private final HBox view;
  private final Button filesToggle, inspectionToggle;

  public ApplicationHeader(
      Stage stage,
      DocumentManager documentManager,
      BooleanSupplier running,
      Runnable openGuiBuilder,
      Runnable toggleFiles,
      Runnable toggleInspector,
      Menu themes,
      Consumer<String> openDocument) {
    this.stage = stage;
    ImageView logo =
        new ImageView(
            new Image(
                Objects.requireNonNull(getClass().getResource("/io/codalgora/desktop/codalgora-logo.png"))
                    .toExternalForm()));
    // Use the emblem from the supplied full lockup; the adjacent label supplies the name.
    logo.setViewport(new Rectangle2D(245, 210, 780, 620));
    logo.setFitWidth(28);
    logo.setFitHeight(24);
    logo.setPreserveRatio(true);
    logo.setSmooth(true);
    logo.setId("projectLogo");
    logo.setAccessibleText("Logo do Codalgora");
    Label brand = new Label("codalgora");
    brand.getStyleClass().add("brand");
    Menu file = new Menu("Arquivo");
    MenuItem fresh = new MenuItem("Novo algoritmo    Ctrl+N");
    fresh.setOnAction(e -> documentManager.newFile());
    MenuItem open = new MenuItem("Abrir…    Ctrl+O");
    open.setOnAction(e -> documentManager.open());
    MenuItem save = new MenuItem("Salvar    Ctrl+S");
    save.setOnAction(e -> documentManager.save(documentManager.active(), false));
    MenuItem saveAs = new MenuItem("Salvar como…");
    saveAs.setOnAction(e -> documentManager.save(documentManager.active(), true));
    file.getItems().addAll(fresh, open, save, saveAs);
    Menu edit = new Menu("Editar");
    for (String command : List.of("Desfazer", "Refazer", "Copiar", "Colar", "Selecionar tudo")) {
      MenuItem item = new MenuItem(command);
      item.setOnAction(
          e -> {
            EditorDocument d = documentManager.active();
            if (d == null
                || running.getAsBoolean()
                    && !command.equals("Copiar")
                    && !command.equals("Selecionar tudo")) return;
            switch (command) {
              case "Desfazer" -> d.editor.undo();
              case "Refazer" -> d.editor.redo();
              case "Copiar" -> d.editor.copy();
              case "Colar" -> d.editor.paste();
              default -> d.editor.selectAll();
            }
          });
      edit.getItems().add(item);
    }
    Menu visualBuilder = new Menu("Construtor visual");
    MenuItem openVisualBuilder = new MenuItem("Abrir construtor visual…");
    openVisualBuilder.setOnAction(e -> openGuiBuilder.run());
    visualBuilder.getItems().add(openVisualBuilder);
    Menu help = new Menu("Ajuda");
    MenuItem guide = new MenuItem("Guia rápido e atalhos");
    guide.setOnAction(e -> showGuide());
    Menu credits = new Menu("Créditos");
    MenuItem creditsPage = new MenuItem("Sobre o desenvolvedor");
    creditsPage.setOnAction(e -> showCredits(openDocument));
    credits.getItems().add(creditsPage);
    help.getItems().add(guide);
    Menu display = new Menu("Exibir");
    MenuItem showFiles = new MenuItem("Arquivos");
    showFiles.setOnAction(e -> toggleFiles.run());
    MenuItem showInspector = new MenuItem("Depuração");
    showInspector.setOnAction(e -> toggleInspector.run());
    display.getItems().addAll(showFiles, showInspector, new SeparatorMenuItem(), themes);
    MenuBar menu = new MenuBar(file, edit, visualBuilder, credits, display, help);
    Region space = new Region();
    HBox.setHgrow(space, Priority.ALWAYS);
    filesToggle = iconButton("files", "Mostrar ou ocultar arquivos (Ctrl+B)", toggleFiles);
    filesToggle.setId("filesToggle");
    inspectionToggle =
        iconButton("debug", "Mostrar ou ocultar depuração (Ctrl+Shift+D)", toggleInspector);
    inspectionToggle.setId("inspectionToggle");
    view = new HBox(9, logo, brand, menu, space);
    view.getStyleClass().add("header");
  }

  public HBox view() {
    return view;
  }

  public Button filesToggle() {
    return filesToggle;
  }

  public Button inspectionToggle() {
    return inspectionToggle;
  }

  private void showGuide() {
    Alert alert =
        new Alert(
            Alert.AlertType.INFORMATION,
            "F5  Executar / continuar\n"
                + "F9  Alternar ponto de parada\n"
                + "F10  Próximo passo\n"
                + "Ctrl+Espaço  Autocompletar e modelos\n"
                + "Ctrl+O  Abrir arquivo .alg\n"
                + "Ctrl+S  Salvar em UTF-8\n"
                + "Ctrl+N  Novo algoritmo\n"
                + "Ctrl+B  Mostrar ou ocultar arquivos\n"
                + "Ctrl+Shift+D  Mostrar ou ocultar depuração\n\n"
                + "Clique na margem esquerda para adicionar pontos de parada.\n"
                + "O terminal aceita os valores solicitados por leia().\n"
                + "Arquivos legados Windows-1252 são reconhecidos na abertura.",
            ButtonType.OK);
    alert.setHeaderText("Do primeiro passo à próxima ideia.");
    alert.initOwner(stage);
    alert.show();
  }

  private void showCredits(Consumer<String> openDocument) {
    Dialog<ButtonType> dialog = new Dialog<>();
    dialog.setTitle("Créditos");
    dialog.setHeaderText("Sobre o desenvolvedor");
    dialog.initOwner(stage);

    Label name = new Label("Arthur Almeida");
    name.getStyleClass().add("heading");

    VBox biography = new VBox(12);
    biography.getChildren().addAll(
        paragraph(
            "Sou estudante do Centro Territorial de Educação Profissional Piemonte do Paraguaçu II "
                + "e curso o Ensino Médio Técnico em Informática. Sou apaixonado por tecnologia, "
                + "programação e engenharia de software."),
        paragraph(
            "A ideia de desenvolver esta IDE surgiu do desejo de tornar o aprendizado de lógica "
                + "de programação mais acessível, intuitivo e agradável. Acredito que aprender a "
                + "programar não deve se resumir a memorizar comandos, mas a compreender conceitos, "
                + "desenvolver o raciocínio lógico e transformar ideias em soluções."),
        paragraph(
            "Com esse propósito, decidi criar um ambiente que facilitasse os primeiros passos na "
                + "programação, oferecendo aos estudantes uma experiência moderna, prática e acolhedora."),
        paragraph(
            "Mais do que um projeto de software, esta IDE representa minha vontade de inovar, "
                + "compartilhar conhecimento e contribuir para que outras pessoas também se "
                + "interessem por tecnologia."));

    Hyperlink profile = new Hyperlink("github.com/thurdass");
    profile.setOnAction(e -> openDocument.accept("https://github.com/thurdass"));
    VBox content = new VBox(14, name, biography, profile);
    content.setPadding(new Insets(4, 8, 4, 8));
    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.setPrefViewportWidth(460);
    scroll.setPrefViewportHeight(390);
    dialog.getDialogPane().setContent(scroll);
    dialog
        .getDialogPane()
        .getButtonTypes()
        .add(new ButtonType("Fechar", ButtonBar.ButtonData.CANCEL_CLOSE));
    dialog.show();
  }

  private Label paragraph(String text) {
    Label paragraph = new Label(text);
    paragraph.setWrapText(true);
    return paragraph;
  }
}
