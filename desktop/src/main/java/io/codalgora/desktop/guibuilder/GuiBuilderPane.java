package io.codalgora.desktop.guibuilder;

import io.codalgora.interpreter.CodalgoraEngine;
import io.codalgora.interpreter.DebugSession;
import io.codalgora.interpreter.Program;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** First visual editor prototype. Form data is kept separate from Portugol source. */
public final class GuiBuilderPane extends BorderPane {
  private static final int MAX_ELEMENTS = GuiFormStore.MAX_ELEMENTS;

  private final Stage owner;
  private final String stylesheet;
  private final String themeCss;
  private final Consumer<String> output;
  private final GuiForm form = new GuiForm();
  private String savedSnapshot = GuiFormStore.serialize(form);
  private final Pane canvas = new Pane();
  private GuiCanvasTools tools;
  private GuiStyleInspector styleInspector;
  private GuiForm.Element copied;
  private Path copiedAlgorithm;
  private final TreeView<Object> outline = new TreeView<>();
  private final Object formRootValue = new Object();
  private final TreeItem<Object> formRoot = new TreeItem<>(formRootValue);
  private final VBox inspector = new VBox(10);
  private final Label status = new Label("Arraste componentes para a tela ou escolha na paleta.");
  private final VBox eventProperty = new VBox(4);
  private final ComboBox<String> eventField = new ComboBox<>();
  private final VBox variableProperty = new VBox(4);
  private final ComboBox<String> variableField = new ComboBox<>();
  private final List<String> eventRoutines = new ArrayList<>();
  private final Map<String, String> globalVariables = new LinkedHashMap<>();
  private final List<String> history = new ArrayList<>();
  private Label componentHeading;
  private TitledPane windowSection;
  private Button undoButton, redoButton;
  private Spinner<Double> spacing, padding;
  private TextField formTitleField, formWidthField, formHeightField;
  private ComboBox<GuiForm.LayoutMode> formLayoutField;
  private TextField nameField, textField, xField, yField, widthField, heightField;
  private GuiForm.Element selected;
  private Path file;
  private Path algorithmFile;
  private boolean rendering;
  private boolean updatingInspector;
  private boolean updatingPreviewControls;
  private boolean restoringHistory;
  private int historyIndex;

  private GuiBuilderPane(Stage owner, String stylesheet, String themeCss, Consumer<String> output) {
    this.owner = owner;
    this.stylesheet = stylesheet;
    this.themeCss = themeCss;
    this.output = output == null ? ignored -> {} : output;
    history.add(savedSnapshot);
    getStyleClass().add("gui-builder");
    tools = new GuiCanvasTools(form, canvas, this::widget, this::select, this::markChanged, this::updateFields, this::render);
    setTop(toolbar());
    setCenter(workspace());
    setBottom(statusBar());
    outline
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (o, old, item) -> {
              if (rendering || item == null) return;
              if (item == formRoot) select(null);
              else if (item.getValue() instanceof GuiForm.Element element) select(element);
            });
    addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
      if (event.getTarget() instanceof TextInputControl) return;
      if (event.getCode() == javafx.scene.input.KeyCode.F2) { tools.editInline(); event.consume(); }
      else if (event.getCode() == javafx.scene.input.KeyCode.DELETE) { removeSelected(); event.consume(); }
      else if (event.isShortcutDown()) {
        switch (event.getCode()) {
          case C -> copySelected(); case V -> pasteSelected(); case D -> duplicateSelected();
          case Z -> { if (event.isShiftDown()) redo(); else undo(); } case Y -> redo();
          default -> { return; }
        }
        event.consume();
      }
    });
    ContextMenu menu = new ContextMenu();
    String[] titles = {"Editar texto (F2)", "Copiar", "Colar", "Duplicar", "Excluir"};
    Runnable[] actions = {tools::editInline, this::copySelected, this::pasteSelected, this::duplicateSelected, this::removeSelected};
    for (int i=0;i<titles.length;i++) { MenuItem item = new MenuItem(titles[i]); Runnable action = actions[i]; item.setOnAction(e -> action.run()); menu.getItems().add(item); }
    tools.setOnContextMenuRequested(e -> { menu.show(tools, e.getScreenX(), e.getScreenY()); e.consume(); });
    render();
  }

  public static void open(Stage owner, String stylesheet, String themeCss, Consumer<String> output) {
    Stage stage = new Stage();
    stage.initOwner(owner);
    GuiBuilderPane builder = new GuiBuilderPane(stage, stylesheet, themeCss, output);
    Scene scene = new Scene(builder, 1180, 760);
    scene.getStylesheets().add(stylesheet);
    scene.getRoot().setStyle(themeCss);
    stage.setTitle("Construtor visual — Codalgora");
    stage.setMinWidth(920);
    stage.setMinHeight(620);
    stage.setScene(scene);
    stage.setOnCloseRequest(
        event -> {
          if (!builder.confirmDiscard()) event.consume();
        });
    stage.show();
  }

  /** Opens a saved visual form directly, for generated application bundles. */
  public static void openFormPreview(
      Stage stage, String stylesheet, String themeCss, Path formPath, Consumer<String> output) {
    GuiBuilderPane runner = new GuiBuilderPane(stage, stylesheet, themeCss, output);
    try {
      runner.loadForm(formPath);
      runner.preview(stage);
    } catch (IOException | RuntimeException ex) {
      runner.showError("Não foi possível abrir o aplicativo", ex);
    }
  }

  private FlowPane toolbar() {
    Button save = new Button("Salvar formulário");
    save.setId("guiSave");
    save.getStyleClass().add("run-button");
    save.setOnAction(e -> save());
    undoButton = new Button("Desfazer");
    undoButton.setId("guiUndo");
    undoButton.setOnAction(e -> undo());
    redoButton = new Button("Refazer");
    redoButton.setId("guiRedo");
    redoButton.setOnAction(e -> redo());
    Button open = new Button("Abrir");
    open.setId("guiOpen");
    open.setOnAction(e -> open());
    Button preview = new Button("Executar formulário");
    preview.setId("guiPreview");
    preview.getStyleClass().add("run-button");
    preview.setOnAction(e -> preview());
    Button delete = new Button("Remover selecionado");
    delete.setOnAction(e -> removeSelected());
    MenuButton arrange = new MenuButton("Organizar");
    MenuItem vertical = new MenuItem("Em coluna");
    vertical.setOnAction(e -> arrangeElements(1));
    MenuItem twoColumns = new MenuItem("Grade · 2 colunas");
    twoColumns.setOnAction(e -> arrangeElements(2));
    MenuItem threeColumns = new MenuItem("Grade · 3 colunas");
    threeColumns.setOnAction(e -> arrangeElements(3));
    arrange.getItems().addAll(vertical, twoColumns, threeColumns);
    Button link = new Button("Vincular algoritmo .alg");
    link.setId("guiLinkAlgorithm");
    link.setOnAction(e -> linkAlgorithm());
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    FlowPane bar = new FlowPane(8, 6, open, save, undoButton, redoButton, link, arrange, delete, preview);
    updateHistoryButtons();
    bar.getStyleClass().add("editor-toolbar");
    return bar;
  }

  private VBox palette() {
    VBox box = new VBox(7);
    box.setPadding(new Insets(14, 10, 14, 12));
    box.setPrefWidth(185);
    box.getStyleClass().add("gui-side");
    Label heading = new Label("Componentes");
    heading.getStyleClass().add("heading");
    Label hint = new Label("Arraste para mover. Use as alças para redimensionar. Duplo clique ou F2 edita o texto.");
    hint.getStyleClass().add("subtle");
    hint.setWrapText(true);
    hint.setMaxWidth(160);
    hint.setMinHeight(Region.USE_PREF_SIZE);
    box.getChildren().addAll(heading, hint, new Separator());
    for (GuiForm.Type type : GuiForm.Type.values()) {
      Button item = new Button("+   " + type.label);
      item.setId("palette-" + type.name());
      item.setMaxWidth(Double.MAX_VALUE);
      item.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
      item.setOnAction(e -> add(type, Double.NaN, Double.NaN));
      item.setOnDragDetected(
          e -> {
            Dragboard drag = item.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString(type.name());
            drag.setContent(content);
            e.consume();
          });
      box.getChildren().add(item);
    }
    Label treeHeading = new Label("Na interface");
    treeHeading.getStyleClass().add("section-heading");
    outline.setId("guiOutline");
    outline.getStyleClass().add("explorer");
    formRoot.setExpanded(true);
    outline.setRoot(formRoot);
    outline.setCellFactory(
        tree ->
            new TreeCell<>() {
              @Override
              protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                  setText(null);
                  setTooltip(null);
                } else if (getTreeItem() == formRoot) {
                  setText("Formulário");
                  setTooltip(new Tooltip(form.title));
                } else if (item instanceof GuiForm.Element element) {
                  setText(element.name);
                  setTooltip(new Tooltip(element.type.label + " · " + element.name));
                }
              }
            });
    VBox.setVgrow(outline, Priority.ALWAYS);
    box.getChildren().addAll(treeHeading, outline);
    return box;
  }

  private SplitPane workspace() {
    VBox componentPalette = palette();
    BorderPane design = designArea();
    ScrollPane properties = propertiesPanel();

    componentPalette.setMinWidth(170);
    design.setMinWidth(300);
    properties.setMinWidth(220);

    SplitPane split = new SplitPane(componentPalette, design, properties);
    split.setId("guiWorkspaceSplit");
    split.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
    split.setAccessibleText("Paleta, área de design e propriedades redimensionáveis");
    split.setAccessibleHelp("Arraste as divisórias entre os painéis para ajustar o espaço de trabalho.");
    split.setDividerPositions(0.19, 0.78);
    split.getStyleClass().add("gui-workspace");
    return split;
  }

  private BorderPane designArea() {
    canvas.setPrefSize(form.width, form.height);
    canvas.setMinSize(form.width, form.height);
    canvas.setMaxSize(form.width, form.height);
    canvas.getStyleClass().add("gui-canvas");
    canvas.setId("guiCanvas");
    canvas.setOnDragOver(
        event -> {
          if (event.getGestureSource() != canvas && event.getDragboard().hasString())
            event.acceptTransferModes(TransferMode.COPY);
          event.consume();
        });
    canvas.setOnDragDropped(
        event -> {
          boolean success = false;
          if (event.getDragboard().hasString()) {
            try {
              GuiForm.Type type = GuiForm.Type.valueOf(event.getDragboard().getString());
              Point2D point = canvas.sceneToLocal(event.getSceneX(), event.getSceneY());
              success = add(type, point.getX(), point.getY());
            } catch (IllegalArgumentException ignored) {
              status.setText("Esse componente não é reconhecido.");
            }
          }
          event.setDropCompleted(success);
          event.consume();
        });
    javafx.scene.Group zoomed = new javafx.scene.Group(tools);
    StackPane stage = new StackPane(new javafx.scene.Group(zoomed));
    stage.setPadding(new Insets(30));
    stage.getStyleClass().add("gui-stage");
    ScrollPane scroll = new ScrollPane(stage);
    scroll.setFitToWidth(true);
    scroll.setFitToHeight(true);
    scroll.getStyleClass().add("gui-scroll");
    CheckBox grid = new CheckBox("Encaixar na grade"); grid.setId("guiSnap"); grid.setSelected(true);
    grid.setOnAction(e -> tools.snap = grid.isSelected());
    Slider zoom = new Slider(25, 200, 100); zoom.setId("guiZoom"); zoom.setPrefWidth(110);
    Label percent = new Label("100%");
    zoom.valueProperty().addListener((o, old, value) -> {
      zoomed.setScaleX(value.doubleValue()/100); zoomed.setScaleY(value.doubleValue()/100);
      percent.setText(Math.round(value.doubleValue()) + "%");
    });
    Button fit = new Button("Ajustar à janela"); fit.setId("guiFit");
    fit.setOnAction(e -> zoom.setValue(Math.max(25, Math.min(200, 100*Math.min(
        (scroll.getViewportBounds().getWidth()-60)/form.width,
        (scroll.getViewportBounds().getHeight()-60)/form.height)))));
    Label mode = new Label("Design"); mode.getStyleClass().add("heading");
    FlowPane controls = new FlowPane(12, 8, mode, grid, zoom, percent, fit);
    controls.setId("guiCanvasToolbar");
    controls.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    controls.setPadding(new Insets(10));
    BorderPane area = new BorderPane(scroll); area.setTop(controls); return area;
  }

  private ScrollPane propertiesPanel() {
    VBox box = new VBox(10);
    box.setPadding(new Insets(14, 14, 14, 14));
    box.setPrefWidth(245);
    box.getStyleClass().add("gui-side");
    Label heading = new Label("Propriedades");
    heading.getStyleClass().add("heading");
    formTitleField = field();
    formWidthField = field();
    formHeightField = field();
    formLayoutField = new ComboBox<>();
    formLayoutField.setId("guiLayoutMode");
    formLayoutField.getItems().setAll(GuiForm.LayoutMode.values());
    formLayoutField.setMaxWidth(Double.MAX_VALUE);
    formLayoutField.setCellFactory(
        view ->
            new ListCell<>() {
              @Override
              protected void updateItem(GuiForm.LayoutMode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label);
              }
            });
    formLayoutField.setButtonCell(
        new ListCell<>() {
          @Override
          protected void updateItem(GuiForm.LayoutMode item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.label);
          }
        });
    formLayoutField.setOnAction(
        event -> {
          if (!updatingInspector && formLayoutField.getValue() != null
              && form.layout != formLayoutField.getValue()) {
            form.layout = formLayoutField.getValue();
            render();
            markChanged("Layout responsivo atualizado.");
          }
        });
    box.getChildren().addAll(heading, new Separator());
    addProperty(box, "Título da janela", formTitleField);
    addProperty(box, "Largura da janela", formWidthField);
    addProperty(box, "Altura da janela", formHeightField);
    Label layoutLabel = new Label("Organização do formulário");
    layoutLabel.getStyleClass().add("subtle");
    box.getChildren().addAll(layoutLabel, formLayoutField);
    spacing = new Spinner<>(0.0, 64.0, form.spacing); spacing.setId("guiSpacing");
    padding = new Spinner<>(0.0, 64.0, form.padding); padding.setId("guiFormPadding");
    spacing.valueProperty().addListener((o, old, value) -> { if (!updatingInspector) { form.spacing=value; render(); markChanged("Espaçamento atualizado."); } });
    padding.valueProperty().addListener((o, old, value) -> { if (!updatingInspector) { form.padding=value; render(); markChanged("Margem interna atualizada."); } });
    box.getChildren().addAll(new Label("Espaçamento entre componentes"), spacing, new Label("Margem interna"), padding);
    List<Node> windowFields = new ArrayList<>(box.getChildren().subList(2, box.getChildren().size()));
    box.getChildren().remove(2, box.getChildren().size());
    VBox windowProperties = new VBox(8); windowProperties.getChildren().setAll(windowFields);
    windowSection = new TitledPane("Janela e layout", windowProperties);
    windowSection.setId("guiWindowProperties");
    windowSection.setExpanded(false); box.getChildren().add(windowSection);
    componentHeading = new Label("Componente selecionado");
    componentHeading.setId("guiComponentHeading");
    componentHeading.getStyleClass().add("section-heading");
    box.getChildren().add(componentHeading);
    inspector.setVisible(false);
    inspector.setManaged(false);
    inspector.setId("guiComponentInspector");
    nameField = field();
    nameField.setId("guiName");
    textField = field();
    textField.setId("guiText");
    xField = field();
    yField = field();
    widthField = field();
    heightField = field();
    addProperty(inspector, "Nome", nameField);
    addProperty(inspector, "Texto", textField);
    VBox geometry = new VBox(8);
    addProperty(geometry, "Posição X", xField);
    addProperty(geometry, "Posição Y", yField);
    addProperty(geometry, "Largura preferida", widthField);
    addProperty(geometry, "Altura preferida", heightField);
    TitledPane geometrySection = new TitledPane("Posição e tamanho", geometry);
    geometrySection.setExpanded(false); inspector.getChildren().add(geometrySection);
    Label eventLabel = new Label("Ao clicar");
    eventLabel.getStyleClass().add("subtle");
    eventField.setId("guiEventRoutine");
    eventField.setMaxWidth(Double.MAX_VALUE);
    eventField.setPromptText("Vincule um procedimento");
    eventField.getItems().add("— Sem ação —");
    eventField.getSelectionModel().selectFirst();
    eventField.setOnAction(e -> applyEvent());
    eventProperty.getChildren().addAll(eventLabel, eventField);
    eventProperty.setVisible(false);
    eventProperty.setManaged(false);

    Label variableLabel = new Label("Variável Portugol");
    variableLabel.getStyleClass().add("subtle");
    variableField.setId("guiVariableName");
    variableField.setMaxWidth(Double.MAX_VALUE);
    variableField.setPromptText("Sem vínculo");
    variableField.setOnAction(e -> applyVariable());
    variableProperty.getChildren().addAll(variableLabel, variableField);
    variableProperty.setVisible(false);
    variableProperty.setManaged(false);
    TitledPane behavior = new TitledPane("Eventos e variáveis Portugol", new VBox(8, eventProperty, variableProperty));
    behavior.setExpanded(false); inspector.getChildren().add(behavior);
    for (TextField input :
        new TextField[] {nameField, textField, xField, yField, widthField, heightField})
      input.setOnAction(e -> applyProperties());
    for (TextField input :
        new TextField[] {nameField, textField, xField, yField, widthField, heightField})
      input
          .focusedProperty()
          .addListener(
              (o, wasFocused, focused) -> {
                if (wasFocused && !focused) applyProperties();
              });
    for (TextField input : new TextField[] {formTitleField, formWidthField, formHeightField}) {
      input.setOnAction(e -> applyFormProperties());
      input
          .focusedProperty()
          .addListener(
              (o, wasFocused, focused) -> {
                if (wasFocused && !focused) applyFormProperties();
              });
    }
    styleInspector = new GuiStyleInspector(() -> { tools.refreshAppearance(); markChanged("Aparência atualizada."); });
    inspector.getChildren().add(styleInspector);
    box.getChildren().add(inspector);
    ScrollPane scroll = new ScrollPane(box);
    scroll.setFitToWidth(true);
    scroll.setFitToHeight(true);
    scroll.setPrefWidth(255);
    scroll.setId("guiPropertiesScroll");
    scroll.setAccessibleText("Propriedades do formulário ou do componente selecionado");
    scroll.getStyleClass().add("gui-properties-scroll");
    return scroll;
  }

  private VBox statusBar() {
    VBox bar = new VBox(status);
    bar.setPadding(new Insets(6, 12, 6, 12));
    bar.getStyleClass().add("statusbar");
    return bar;
  }

  private TextField field() {
    TextField field = new TextField();
    field.setMaxWidth(Double.MAX_VALUE);
    return field;
  }

  private void addProperty(VBox parent, String label, TextField input) {
    Label title = new Label(label);
    title.getStyleClass().add("subtle");
    parent.getChildren().addAll(title, input);
  }

  private boolean add(GuiForm.Type type, double x, double y) {
    if (form.elements.size() >= MAX_ELEMENTS) {
      status.setText("O formulário atingiu o limite de " + MAX_ELEMENTS + " componentes.");
      return false;
    }
    double width = type == GuiForm.Type.LABEL ? 180 : type == GuiForm.Type.TEXT_AREA ? 240 : 180;
    double height = type == GuiForm.Type.TEXT_AREA ? 96 : type == GuiForm.Type.LABEL ? 34 : 38;
    if (!Double.isFinite(x) || !Double.isFinite(y)) {
      int index = form.elements.size();
      x = 24 + (index % 2) * 196;
      y = 24 + (index / 2) * 52;
    }
    int index = 1;
    boolean used;
    do {
      String candidate = "componente" + index++;
      used = form.elements.stream().anyMatch(item -> item.name.equalsIgnoreCase(candidate));
    } while (used);
    String name = "componente" + (index - 1);
    GuiForm.Element element =
        new GuiForm.Element(
            type,
            name,
            type.defaultText,
            clamp(x, 0, form.width - width),
            clamp(y, 0, form.height - height),
            width,
            height);
    form.elements.add(element);
    selected = element;
    render();
    markChanged(type.label + " adicionado. Arraste para posicionar.");
    return true;
  }

  private void render() {
    if (rendering) return;
    rendering = true;
    formRoot.getChildren().setAll(
        form.elements.stream().map(element -> new TreeItem<Object>(element)).toList());
    formRoot.setExpanded(true);
    outline.refresh();
    if (selected != null && !form.elements.contains(selected)) selected = null;
    tools.render(selected);
    if (selected == null) outline.getSelectionModel().select(formRoot);
    else outline.getSelectionModel().select(findOutlineItem(selected));
    updateFields();
    rendering = false;
  }

  private TreeItem<Object> findOutlineItem(GuiForm.Element element) {
    return formRoot.getChildren().stream()
        .filter(item -> item.getValue() == element)
        .findFirst()
        .orElse(formRoot);
  }

  private Node widget(GuiForm.Element element) {
    Node node = switch (element.type) {
      case LABEL -> new Label(element.text);
      case BUTTON -> new Button(element.text);
      case TEXT_FIELD -> {
        TextField field = new TextField();
        field.setPromptText(element.text);
        field.setStyle(
            "-fx-control-inner-background: white; -fx-background-color: white; "
                + "-fx-text-fill: #20262d; -fx-prompt-text-fill: #687480; "
                + "-fx-border-color: #8c969f; -fx-border-radius: 3; -fx-background-radius: 3;");
        yield field;
      }
      case TEXT_AREA -> {
        TextArea area = new TextArea();
        area.setPromptText(element.text);
        area.setWrapText(true);
        area.setStyle(
            "-fx-control-inner-background: white; -fx-background-color: white; "
                + "-fx-text-fill: #20262d; -fx-prompt-text-fill: #687480; "
                + "-fx-border-color: #8c969f; -fx-border-radius: 3; -fx-background-radius: 3;");
        yield area;
      }
      case CHECK_BOX -> new CheckBox(element.text);
      case COMBO_BOX -> {
        ComboBox<String> choices = new ComboBox<>();
        choices.setButtonCell(new ListCell<>() {
          @Override protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty); setText(empty ? null : item);
          }
        });
        choices.setCellFactory(view -> new ListCell<>() {
          @Override protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty); setText(empty ? null : item);
            GuiAppearance.apply(this, element.appearance);
          }
        });
        java.util.Arrays.stream(element.text.split("\\|"))
            .map(String::strip)
            .filter(option -> !option.isEmpty())
            .forEach(choices.getItems()::add);
        if (choices.getItems().isEmpty()) choices.getItems().add("Opção 1");
        choices.getSelectionModel().selectFirst();
        yield choices;
      }
    };
    GuiAppearance.apply(node, element.appearance);
    node.hoverProperty().addListener((o, old, value) -> GuiAppearance.apply(node, element.appearance));
    node.pressedProperty().addListener((o, old, value) -> GuiAppearance.apply(node, element.appearance));
    node.disabledProperty().addListener((o, old, value) -> GuiAppearance.apply(node, element.appearance));
    return node;
  }

  private void select(GuiForm.Element element) {
    if (rendering) return;
    if (element == null) {
      selected = null;
      tools.select(null);
      windowSection.setExpanded(true);
      if (outline.getSelectionModel().getSelectedItem() != formRoot)
        outline.getSelectionModel().select(formRoot);
      updateFields();
      return;
    }
    if (element == selected) return;
    selected = element;
    tools.select(element);
    windowSection.setExpanded(false);
    outline.getSelectionModel().select(findOutlineItem(element));
    updateFields();
  }

  private void updateFields() {
    updatingInspector = true;
    try {
      outline.refresh();
      formTitleField.setText(form.title);
      formWidthField.setText(number(form.width));
      formHeightField.setText(number(form.height));
      formLayoutField.setValue(form.layout);
      spacing.getValueFactory().setValue(form.spacing);
      padding.getValueFactory().setValue(form.padding);
      boolean hasSelection = selected != null;
      inspector.setVisible(hasSelection);
      inspector.setManaged(hasSelection);
      componentHeading.setText(
          hasSelection
              ? "Componente selecionado · " + selected.type.label
              : "Formulário selecionado");
      windowSection.setExpanded(!hasSelection);
      if (!hasSelection) return;
      boolean supportsEvent =
          selected.type == GuiForm.Type.BUTTON
              || selected.type == GuiForm.Type.TEXT_FIELD
              || selected.type == GuiForm.Type.CHECK_BOX
              || selected.type == GuiForm.Type.COMBO_BOX;
      eventProperty.setVisible(supportsEvent);
      eventProperty.setManaged(supportsEvent);
      ((Label) eventProperty.getChildren().getFirst())
          .setText(selected.type == GuiForm.Type.TEXT_FIELD ? "Ao pressionar Enter" : "Ao ativar");
      eventField.setDisable(eventRoutines.isEmpty() && selected.eventRoutine == null);
      eventField.getItems().setAll("— Sem ação —");
      eventField.getItems().addAll(eventRoutines);
      if (selected.eventRoutine != null && !eventRoutines.contains(selected.eventRoutine))
        eventField.getItems().add("(não encontrado) " + selected.eventRoutine);
      eventField.setValue(
          selected.eventRoutine == null
              ? "— Sem ação —"
              : eventRoutines.contains(selected.eventRoutine)
                  ? selected.eventRoutine
                  : "(não encontrado) " + selected.eventRoutine);
      boolean supportsVariable = selected.type != GuiForm.Type.BUTTON;
      variableProperty.setVisible(supportsVariable);
      variableProperty.setManaged(supportsVariable);
      variableField.getItems().setAll("— Sem vínculo —");
      globalVariables.forEach(
          (name, type) -> {
            if (selected.type != GuiForm.Type.CHECK_BOX || type.equals("logico"))
              variableField.getItems().add(name);
          });
      if (selected.variableName != null && !variableField.getItems().contains(selected.variableName))
        variableField.getItems().add("(não encontrada) " + selected.variableName);
      variableField.setDisable(globalVariables.isEmpty() && selected.variableName == null);
      variableField.setValue(
          selected.variableName == null
              ? "— Sem vínculo —"
              : variableField.getItems().contains(selected.variableName)
                  ? selected.variableName
                  : "(não encontrada) " + selected.variableName);
      styleInspector.edit(selected);
      xField.setDisable(form.layout != GuiForm.LayoutMode.FREE);
      yField.setDisable(form.layout != GuiForm.LayoutMode.FREE);
      nameField.setText(selected.name);
      textField.setText(selected.text);
      xField.setText(number(selected.x));
      yField.setText(number(selected.y));
      widthField.setText(number(selected.width));
      heightField.setText(number(selected.height));
    } finally {
      updatingInspector = false;
    }
  }

  private void applyEvent() {
    if (updatingInspector
        || selected == null
        || selected.type == GuiForm.Type.LABEL
        || selected.type == GuiForm.Type.TEXT_AREA
        || eventField.getValue() == null) return;
    String choice = eventField.getValue();
    String routine = choice.equals("— Sem ação —") ? null : choice;
    if (routine != null && routine.startsWith("(não encontrado) ")) return;
    if (!Objects.equals(selected.eventRoutine, routine)) {
      selected.eventRoutine = routine;
      markChanged(routine == null ? "Ação do botão removida." : "Procedimento associado ao botão.");
    }
  }

  private void applyVariable() {
    if (updatingInspector
        || selected == null
        || selected.type == GuiForm.Type.BUTTON
        || variableField.getValue() == null) return;
    String choice = variableField.getValue();
    String variable = choice.equals("— Sem vínculo —") ? null : choice;
    if (variable != null && variable.startsWith("(não encontrada) ")) return;
    if (!Objects.equals(selected.variableName, variable)) {
      selected.variableName = variable;
      markChanged(variable == null ? "Vínculo removido." : "Componente vinculado à variável Portugol.");
    }
  }

  private void applyFormProperties() {
    String title = formTitleField.getText().strip();
    if (title.isEmpty()) {
      status.setText("Informe um título para a janela.");
      return;
    }
    try {
      double width = bounded(formWidthField.getText(), 320, 1200, "Largura da janela");
      double height = bounded(formHeightField.getText(), 240, 1000, "Altura da janela");
      form.title = title;
      form.width = width;
      form.height = height;
      for (GuiForm.Element element : form.elements) {
        element.width = Math.min(element.width, width);
        element.height = Math.min(element.height, height);
        element.x = clamp(element.x, 0, width - element.width);
        element.y = clamp(element.y, 0, height - element.height);
      }
      canvas.setPrefSize(width, height);
      canvas.setMinSize(width, height);
      canvas.setMaxSize(width, height);
      render();
      markChanged("Propriedades da janela atualizadas.");
    } catch (IllegalArgumentException ex) {
      status.setText(ex.getMessage());
    }
  }

  private void applyProperties() {
    if (updatingInspector || selected == null) return;
    try {
      String name = nameField.getText().strip();
      if (name.isEmpty()) throw new IllegalArgumentException("Informe um nome.");
      if (!name.matches("[A-Za-z_][A-Za-z0-9_]*"))
        throw new IllegalArgumentException("Use letras sem acento, números e _; comece com letra ou _.");
      for (GuiForm.Element element : form.elements)
        if (element != selected && element.name.equalsIgnoreCase(name))
          throw new IllegalArgumentException("Já existe um componente com esse nome.");
      selected.name = name;
      selected.text = textField.getText();
      selected.width = bounded(widthField.getText(), 50, form.width, "Largura");
      selected.height = bounded(heightField.getText(), 28, form.height, "Altura");
      selected.x = clamp(numberValue(xField.getText(), "Posição X"), 0, form.width - selected.width);
      selected.y = clamp(numberValue(yField.getText(), "Posição Y"), 0, form.height - selected.height);
      render();
      markChanged("Propriedades atualizadas.");
    } catch (IllegalArgumentException ex) {
      status.setText(ex.getMessage());
    }
  }

  private double bounded(String value, double minimum, double maximum, String label) {
    double number = numberValue(value, label);
    if (number < minimum || number > maximum)
      throw new IllegalArgumentException(
          label + " deve ficar entre " + (int) minimum + " e " + (int) maximum + ".");
    return number;
  }

  private double numberValue(String value, String label) {
    try {
      double number = Double.parseDouble(value);
      if (!Double.isFinite(number)) throw new NumberFormatException();
      return number;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(label + " precisa ser um número.");
    }
  }

  private String number(double value) {
    return Integer.toString((int) Math.round(value));
  }

  private double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private void copySelected() {
    tools.finishInline(true);
    if (selected != null) { copied = GuiFormStore.copyElement(selected); copiedAlgorithm = algorithmFile; status.setText("Componente copiado."); }
  }

  private void duplicateSelected() { if (selected != null) { copySelected(); pasteSelected(); } }

  private void pasteSelected() {
    if (copied == null || form.elements.size() >= MAX_ELEMENTS) return;
    if ((copied.eventRoutine != null || copied.variableName != null)
        && !Objects.equals(copiedAlgorithm, algorithmFile)) {
      status.setText("Vincule o mesmo algoritmo antes de colar este componente com vínculos.");
      return;
    }
    GuiForm.Element clone = GuiFormStore.copyElement(copied);
    clone.id = java.util.UUID.randomUUID().toString();
    int suffix=1; String name;
    do { name = "copia" + suffix++; } while (hasName(name));
    clone.name = name;
    clone.x = clamp(clone.x+16, 0, form.width-clone.width);
    clone.y = clamp(clone.y+16, 0, form.height-clone.height);
    boolean detached = clone.variableName != null && clone.type != GuiForm.Type.LABEL;
    if (detached) clone.variableName = null;
    form.elements.add(clone); selected = clone; render();
    markChanged(detached ? "Cópia criada sem vínculo de entrada; escolha outra variável." : "Componente duplicado.");
  }

  private boolean hasName(String name) { return form.elements.stream().anyMatch(e -> e.name.equalsIgnoreCase(name)); }

  private void removeSelected() {
    if (selected == null) return;
    form.elements.remove(selected);
    selected = null;
    render();
    markChanged("Componente removido.");
  }

  private void arrangeElements(int columns) {
    if (form.elements.isEmpty()) return;
    double gap = 16;
    double columnWidth = (form.width - gap * (columns + 1)) / columns;
    int rows = (form.elements.size() + columns - 1) / columns;
    double[] rowHeights = new double[rows];
    for (int i = 0; i < form.elements.size(); i++) {
      GuiForm.Element element = form.elements.get(i);
      int row = i / columns;
      rowHeights[row] = Math.max(rowHeights[row], Math.min(element.height, form.height));
      element.x = gap + (i % columns) * (columnWidth + gap);
      element.width = Math.min(element.width, columnWidth);
    }
    double y = gap;
    for (int row = 0; row < rows; row++) {
      for (int i = row * columns; i < Math.min(form.elements.size(), (row + 1) * columns); i++)
        form.elements.get(i).y = Math.min(y, Math.max(0, form.height - form.elements.get(i).height));
      y += rowHeights[row] + gap;
    }
    render();
    markChanged(columns == 1 ? "Componentes organizados em coluna." : "Componentes organizados em grade.");
  }

  private void save() {
    Path target = file;
    if (target == null) {
      FileChooser chooser = chooser();
      chooser.setInitialFileName("interface.cgui.json");
      java.io.File selectedFile = chooser.showSaveDialog(owner);
      if (selectedFile == null) return;
      target = selectedFile.toPath();
      if (!target.getFileName().toString().endsWith(".cgui.json"))
        target = target.resolveSibling(target.getFileName() + ".cgui.json");
    }
    try {
      saveForm(target);
    } catch (IOException | RuntimeException ex) {
      showError("Não foi possível salvar o formulário", ex);
    }
  }

  /** Saves the current form and its relative algorithm reference to the given path. */
  public void saveForm(Path target) throws IOException {
    tools.finishInline(true);
    Path destination = target.toAbsolutePath().normalize();
    String previousAlgorithmPath = form.algorithmPath;
    try {
      if (algorithmFile != null) form.algorithmPath = relativeAlgorithmPath(destination, algorithmFile);
      GuiFormStore.write(destination, form);
      file = destination;
      savedSnapshot = GuiFormStore.serialize(form);
      history.set(historyIndex, savedSnapshot);
      status.setText("Salvo em " + file.getFileName());
      owner.setTitle("Construtor visual — Codalgora");
      updateHistoryButtons();
    } catch (IOException | RuntimeException ex) {
      form.algorithmPath = previousAlgorithmPath;
      throw ex;
    }
  }

  private void open() {
    if (!confirmDiscard()) return;
    java.io.File selectedFile = chooser().showOpenDialog(owner);
    if (selectedFile == null) return;
    try {
      loadForm(selectedFile.toPath());
    } catch (IOException | RuntimeException ex) {
      showError("Arquivo de formulário inválido", ex);
    }
  }

  /** Reopens a form, resolves its algorithm path and restores its event and variable bindings. */
  public void loadForm(Path source) throws IOException {
    Path formPath = source.toAbsolutePath().normalize();
    GuiForm loaded = GuiFormStore.read(formPath);
    Path linkedAlgorithm = resolveAlgorithmPath(loaded.algorithmPath, formPath);
    eventRoutines.clear();
    globalVariables.clear();
    String algorithmNotice = null;
    if (linkedAlgorithm != null) {
      try {
        refreshEventRoutines(Files.readString(linkedAlgorithm, StandardCharsets.UTF_8));
      } catch (IOException | RuntimeException ex) {
        linkedAlgorithm = null;
        algorithmNotice = " O algoritmo vinculado não foi encontrado ou contém erros; vincule-o novamente.";
      }
    }
    form.schemaVersion = loaded.schemaVersion;
    form.title = loaded.title;
    form.width = loaded.width;
    form.height = loaded.height;
    form.layout = loaded.layout;
    form.spacing = loaded.spacing; form.padding = loaded.padding;
    form.algorithmPath = loaded.algorithmPath;
    form.elements = loaded.elements;
    file = formPath;
    algorithmFile = linkedAlgorithm;
    savedSnapshot = GuiFormStore.serialize(form);
    history.clear();
    history.add(savedSnapshot);
    historyIndex = 0;
    selected = null;
    render();
    status.setText(
        "Formulário aberto: "
            + file.getFileName()
            + (algorithmNotice == null ? "" : algorithmNotice));
    owner.setTitle("Construtor visual — Codalgora");
    updateHistoryButtons();
  }

  private void markChanged(String message) {
    String current = GuiFormStore.serialize(form);
    if (!restoringHistory && !history.get(historyIndex).equals(current)) {
      while (history.size() > historyIndex + 1) history.removeLast();
      history.add(current);
      historyIndex++;
      if (history.size() > 100) {
        history.removeFirst();
        historyIndex--;
      }
      updateHistoryButtons();
    }
    boolean dirty = !current.equals(savedSnapshot);
    status.setText(dirty ? message + "  ·  Alterações não salvas" : message);
    owner.setTitle("Construtor visual — Codalgora" + (dirty ? " *" : ""));
  }

  private void undo() {
    tools.finishInline(true);
    if (historyIndex == 0) return;
    historyIndex--;
    restoreHistory();
  }

  private void redo() {
    tools.finishInline(true);
    if (historyIndex >= history.size() - 1) return;
    historyIndex++;
    restoreHistory();
  }

  private void restoreHistory() {
    restoringHistory = true;
    try {
      GuiForm restored = GuiFormStore.deserialize(history.get(historyIndex));
      form.schemaVersion = restored.schemaVersion;
      form.title = restored.title;
      form.width = restored.width;
      form.height = restored.height;
      form.layout = restored.layout;
      form.spacing = restored.spacing; form.padding = restored.padding;
      form.algorithmPath = restored.algorithmPath;
      form.elements = restored.elements;
      selected = null;
      algorithmFile = resolveAlgorithmPath(form.algorithmPath, file);
      eventRoutines.clear();
      globalVariables.clear();
      if (algorithmFile != null && Files.isRegularFile(algorithmFile))
        refreshEventRoutines(Files.readString(algorithmFile, StandardCharsets.UTF_8));
      render();
      boolean dirty = !GuiFormStore.serialize(form).equals(savedSnapshot);
      status.setText(dirty ? "Edição restaurada.  ·  Alterações não salvas" : "Edição restaurada.");
      owner.setTitle("Construtor visual — Codalgora" + (dirty ? " *" : ""));
    } catch (IOException | RuntimeException ex) {
      showError("Não foi possível restaurar a edição", ex);
    } finally {
      restoringHistory = false;
      updateHistoryButtons();
    }
  }

  private void updateHistoryButtons() {
    if (undoButton != null) undoButton.setDisable(historyIndex <= 0);
    if (redoButton != null) redoButton.setDisable(historyIndex >= history.size() - 1);
  }

  private boolean confirmDiscard() {
    if (GuiFormStore.serialize(form).equals(savedSnapshot)) return true;
    Alert alert =
        new Alert(
            Alert.AlertType.CONFIRMATION,
            "As alterações deste formulário ainda não foram salvas.",
            ButtonType.YES,
            ButtonType.NO);
    alert.setTitle("Descartar alterações?");
    alert.setHeaderText("O formulário foi modificado");
    alert.initOwner(owner);
    return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
  }

  private FileChooser chooser() {
    FileChooser chooser = new FileChooser();
    chooser
        .getExtensionFilters()
        .add(
            new FileChooser.ExtensionFilter(
                "Formulários Codalgora (*.cgui.json)", "*.cgui.json"));
    return chooser;
  }

  private void linkAlgorithm() {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Vincular algoritmo Portugol");
    chooser
        .getExtensionFilters()
        .add(new FileChooser.ExtensionFilter("Algoritmos Portugol (*.alg)", "*.alg"));
    java.io.File selectedFile = chooser.showOpenDialog(owner);
    if (selectedFile == null) return;
    try {
      linkAlgorithm(selectedFile.toPath());
    } catch (IOException | RuntimeException ex) {
      showError("Não foi possível vincular o algoritmo", ex);
    }
  }

  /** Links a Portugol source file and updates the procedure choices shown for buttons. */
  public void linkAlgorithm(Path path) throws IOException {
    Path candidate = path.toAbsolutePath().normalize();
    String source = Files.readString(candidate, StandardCharsets.UTF_8);
    Program program = CodalgoraEngine.parse(source);
    algorithmFile = candidate;
    form.algorithmPath = candidate.toString();
    refreshEventRoutines(program);
    render();
    markChanged(
        eventRoutines.isEmpty()
            ? "Algoritmo vinculado; ele não possui procedimentos compatíveis com os vínculos de eventos."
            : "Algoritmo vinculado. Escolha um botão para associar um procedimento.");
  }

  private void refreshEventRoutines(String source) {
    refreshEventRoutines(CodalgoraEngine.parse(source));
  }

  private void refreshEventRoutines(Program program) {
    globalVariables.clear();
    program.declarations().stream()
        .filter(declaration -> declaration.constant() == null && declaration.ranges().isEmpty())
        .forEach(declaration -> globalVariables.put(declaration.name(), declaration.type()));
    eventRoutines.clear();
    program.routines().stream()
        .filter(routine -> !routine.function())
        .filter(
            routine ->
                routine.parameters().stream()
                    .allMatch(
                        parameter -> {
                          String type = globalVariables.get(parameter.name());
                          return type != null
                              && (!parameter.reference() || type.equals(parameter.type()));
                        }))
        .map(routine -> routine.name().toLowerCase(Locale.ROOT))
        .distinct()
        .forEach(eventRoutines::add);
  }

  private String relativeAlgorithmPath(Path formFile, Path linkedAlgorithm) {
    Path parent = formFile.toAbsolutePath().normalize().getParent();
    if (parent == null)
      throw new IllegalArgumentException("Salve o formulário em uma pasta para vincular o algoritmo.");
    try {
      return parent
          .relativize(linkedAlgorithm.toAbsolutePath().normalize())
          .toString()
          .replace(java.io.File.separatorChar, '/');
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(
          "O formulário e o algoritmo precisam estar no mesmo volume.", ex);
    }
  }

  private Path resolveAlgorithmPath(String path, Path formFile) {
    if (path == null || path.isBlank()) return null;
    Path savedPath = Path.of(path);
    if (savedPath.isAbsolute()) return savedPath.normalize();
    Path parent = formFile.toAbsolutePath().normalize().getParent();
    return parent == null ? savedPath.normalize() : parent.resolve(savedPath).normalize();
  }

  private void preview() {
    Stage preview = new Stage();
    preview.initOwner(owner);
    preview(preview);
  }

  private void preview(Stage preview) {
    try {
      GuiFormStore.validate(form);
      String algorithmSource = null;
      DebugSession session = null;
      boolean hasEvents = form.elements.stream().anyMatch(element -> element.eventRoutine != null);
      boolean hasBindings = form.elements.stream().anyMatch(element -> element.variableName != null);
      if (hasBindings && !hasEvents)
        throw new IllegalArgumentException(
            "Associe um procedimento a um botão para enviar e receber valores das variáveis.");
      if (hasEvents || hasBindings) {
        Path sourcePath =
            algorithmFile != null ? algorithmFile : resolveAlgorithmPath(form.algorithmPath, file);
        if (sourcePath == null || !Files.isRegularFile(sourcePath))
          throw new IllegalArgumentException(
              "O arquivo .alg vinculado não foi encontrado. Vincule-o novamente.");
        algorithmSource = Files.readString(sourcePath, StandardCharsets.UTF_8);
        Program program = CodalgoraEngine.parse(algorithmSource);
        refreshEventRoutines(program);
        List<String> procedures = List.copyOf(eventRoutines);
        for (GuiForm.Element element : form.elements)
          if (element.eventRoutine != null
              && !procedures.contains(element.eventRoutine.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException(
                "O procedimento '" + element.eventRoutine + "' não existe no algoritmo vinculado.");
        validateVariableBindings();
        render();
      }
      if (algorithmSource != null) {
        String source = algorithmSource;
        session = CodalgoraEngine.createInteractiveSession(
            source, () -> requestInput(preview), output, ignored -> {});
      }
      DebugSession activeSession = session;
      Region content =
          switch (form.layout) {
            case FREE -> new Pane();
            case VERTICAL -> new VBox(form.spacing);
            case GRID -> new GridPane();
          };
      Map<String, Node> boundWidgets = new LinkedHashMap<>();
      content.setPrefSize(form.width, form.height);
      content.setMinSize(form.width, form.height);
      content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
      content.getStyleClass().add("gui-preview-canvas");
      if (content instanceof VBox column) {
        column.setPadding(new Insets(form.padding));
        column.setFillWidth(true);
      }
      if (content instanceof GridPane grid) {
        grid.setPadding(new Insets(form.padding));
        grid.setHgap(form.spacing);
        grid.setVgap(form.spacing);
        for (int i = 0; i < 2; i++) {
          ColumnConstraints constraint = new ColumnConstraints();
          constraint.setPercentWidth(50);
          constraint.setHgrow(Priority.ALWAYS);
          grid.getColumnConstraints().add(constraint);
        }
      }
      int gridRow = 0;
      for (GuiForm.Element element : form.elements) {
        Node node = widget(element);
        Region region = (Region) node;
        region.setPrefSize(element.width, element.height);
        region.setMinSize(50, 28);
        if (form.layout == GuiForm.LayoutMode.FREE) {
          node.setLayoutX(element.x);
          node.setLayoutY(element.y);
        } else {
          region.setMaxSize(element.growWidth ? Double.MAX_VALUE : Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
          if (content instanceof VBox column) VBox.setVgrow(node, Priority.NEVER);
          if (content instanceof GridPane grid) {
            GridPane.setHgrow(node, Priority.ALWAYS);
            GridPane.setFillWidth(node, true);
            grid.add(node, gridRow % 2, gridRow / 2);
            gridRow++;
          } else if (content instanceof VBox column) column.getChildren().add(node);
        }
        if (element.variableName != null) boundWidgets.put(element.variableName, node);
        if (element.eventRoutine != null && activeSession != null) {
          String routine = element.eventRoutine;
          var handler =
              (javafx.event.EventHandler<javafx.event.ActionEvent>)
                  event -> {
                    if (updatingPreviewControls) return;
                    activeSession
                        .fire(routine, readBoundValues(boundWidgets))
                        .whenComplete(
                            (snapshot, failure) -> {
                              if (failure == null && snapshot != null)
                                Platform.runLater(
                                    () -> updateBoundWidgets(boundWidgets, snapshot.globals()));
                              if (failure != null) {
                                Throwable error = rootCause(failure);
                                if (!(error instanceof java.util.concurrent.CancellationException))
                                  output.accept(
                                      "Não foi possível executar o evento: "
                                          + Objects.toString(error.getMessage(), error.toString())
                                          + "\n");
                              }
                            });
                  };
          if (node instanceof Button button) button.setOnAction(handler);
          else if (node instanceof TextField field) field.setOnAction(handler);
          else if (node instanceof CheckBox checkBox) checkBox.setOnAction(handler);
          else if (node instanceof ComboBox<?> choices) choices.setOnAction(handler);
        }
        if (content instanceof Pane pane && form.layout == GuiForm.LayoutMode.FREE)
          pane.getChildren().add(node);
      }
      preview.setTitle(form.title);
      Scene scene = new Scene(content, form.width, form.height);
      scene.getStylesheets().add(stylesheet);
      scene.getRoot().setStyle(themeCss);
      preview.setScene(scene);
      if (activeSession != null) {
        preview.setOnHidden(event -> activeSession.stop());
      }
      preview.show();
      if (activeSession != null) {
        activeSession.startInteractive(false).whenComplete(
            (snapshot, failure) -> {
              if (failure != null
                  && !(rootCause(failure) instanceof java.util.concurrent.CancellationException))
                output.accept(
                    "Erro no algoritmo da interface: "
                        + rootCause(failure).getMessage()
                        + "\n");
            });
      }
    } catch (IOException | RuntimeException ex) {
      showError("Não foi possível pré-visualizar", ex);
    }
  }

  private void validateVariableBindings() {
    for (GuiForm.Element element : form.elements) {
      if (element.variableName == null) continue;
      String type = globalVariables.get(element.variableName.toLowerCase(Locale.ROOT));
      if (type == null)
        throw new IllegalArgumentException(
            "A variável '" + element.variableName + "' não existe como variável global escalar.");
      if (element.type == GuiForm.Type.CHECK_BOX && !type.equals("logico"))
        throw new IllegalArgumentException(
            "Caixas de seleção só podem ser vinculadas a variáveis do tipo logico.");
    }
  }

  private Map<String, Object> readBoundValues(Map<String, Node> boundWidgets) {
    Map<String, Object> values = new LinkedHashMap<>();
    boundWidgets.forEach(
        (name, node) -> {
          if (node instanceof TextField field) values.put(name, field.getText());
          else if (node instanceof TextArea area) values.put(name, area.getText());
          else if (node instanceof CheckBox checkBox) values.put(name, checkBox.isSelected());
          else if (node instanceof ComboBox<?> choices && choices.getValue() != null)
            values.put(name, choices.getValue().toString());
        });
    return values;
  }

  private void updateBoundWidgets(Map<String, Node> boundWidgets, Map<String, Object> variables) {
    updatingPreviewControls = true;
    try {
      boundWidgets.forEach(
          (name, node) -> {
            if (!variables.containsKey(name)) return;
            Object value = variables.get(name);
            if (node instanceof TextField field) field.setText(displayVariable(value));
            else if (node instanceof TextArea area) area.setText(displayVariable(value));
            else if (node instanceof CheckBox checkBox && value instanceof Boolean logical)
              checkBox.setSelected(logical);
            else if (node instanceof Label label) label.setText(displayVariable(value));
            else if (node instanceof ComboBox<?> choices) {
              @SuppressWarnings("unchecked")
              ComboBox<Object> mutableChoices = (ComboBox<Object>) choices;
              mutableChoices.setValue(displayVariable(value));
            }
          });
    } finally {
      updatingPreviewControls = false;
    }
  }

  private String displayVariable(Object value) {
    if (value instanceof Boolean logical) return logical ? "VERDADEIRO" : "FALSO";
    if (value instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue()))
      return String.format(Locale.ROOT, "%.0f", number.doubleValue());
    return String.valueOf(value);
  }

  private String requestInput(Stage preview) throws Exception {
    CompletableFuture<Optional<String>> answer = new CompletableFuture<>();
    Platform.runLater(
        () -> {
          if (!preview.isShowing()) {
            answer.complete(Optional.empty());
            return;
          }
          TextInputDialog dialog = new TextInputDialog();
          dialog.setTitle("Entrada do algoritmo");
          dialog.setHeaderText("O algoritmo precisa de um valor");
          dialog.setContentText("Digite o valor:");
          dialog.initOwner(preview);
          answer.complete(dialog.showAndWait());
        });
    try {
      return answer.get().orElse("");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw ex;
    } catch (ExecutionException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof Exception exception) throw exception;
      throw new IllegalStateException(cause);
    }
  }

  private Throwable rootCause(Throwable error) {
    Throwable current = error;
    while ((current instanceof java.util.concurrent.CompletionException
            || current instanceof ExecutionException)
        && current.getCause() != null) current = current.getCause();
    return current;
  }

  private void showError(String title, Exception exception) {
    Alert alert = new Alert(Alert.AlertType.ERROR, exception.getMessage(), ButtonType.OK);
    alert.setTitle(title);
    alert.setHeaderText(title);
    if (owner.getScene() != null) alert.initOwner(owner);
    alert.showAndWait();
    status.setText(title + ".");
  }
}
