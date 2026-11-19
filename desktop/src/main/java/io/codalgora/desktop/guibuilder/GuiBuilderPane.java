package io.codalgora.desktop.guibuilder;

import java.io.IOException;
import java.nio.file.Path;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.css.PseudoClass;
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
  private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

  private final Stage owner;
  private final String stylesheet;
  private final String themeCss;
  private final GuiForm form = new GuiForm();
  private String savedSnapshot = GuiFormStore.serialize(form);
  private final Pane canvas = new Pane();
  private final ListView<GuiForm.Element> outline = new ListView<>();
  private final VBox inspector = new VBox(10);
  private final Label status = new Label("Arraste componentes para a tela ou escolha na paleta.");
  private Label componentHeading;
  private TextField formTitleField, formWidthField, formHeightField;
  private TextField nameField, textField, xField, yField, widthField, heightField;
  private GuiForm.Element selected;
  private Path file;
  private boolean rendering;

  private GuiBuilderPane(Stage owner, String stylesheet, String themeCss) {
    this.owner = owner;
    this.stylesheet = stylesheet;
    this.themeCss = themeCss;
    getStyleClass().add("gui-builder");
    setTop(toolbar());
    setLeft(palette());
    setCenter(designArea());
    setRight(propertiesPanel());
    setBottom(statusBar());
    outline
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((o, old, value) -> select(value));
    render();
  }

  public static void open(Stage owner, String stylesheet, String themeCss) {
    Stage stage = new Stage();
    stage.initOwner(owner);
    GuiBuilderPane builder = new GuiBuilderPane(stage, stylesheet, themeCss);
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

  private HBox toolbar() {
    Button save = new Button("Salvar formulário");
    save.setId("guiSave");
    save.getStyleClass().add("run-button");
    save.setOnAction(e -> save());
    Button open = new Button("Abrir");
    open.setId("guiOpen");
    open.setOnAction(e -> open());
    Button preview = new Button("Pré-visualizar");
    preview.setId("guiPreview");
    preview.getStyleClass().add("run-button");
    preview.setOnAction(e -> preview());
    Button delete = new Button("Remover selecionado");
    delete.setOnAction(e -> removeSelected());
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox bar = new HBox(8, open, save, delete, spacer, preview);
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
    Label hint = new Label("Arraste para a tela ou clique para adicionar.");
    hint.getStyleClass().add("subtle");
    hint.setWrapText(true);
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
    outline.setPlaceholder(new Label("Adicione o primeiro componente"));
    outline.setId("guiOutline");
    outline.getStyleClass().add("explorer");
    VBox.setVgrow(outline, Priority.ALWAYS);
    box.getChildren().addAll(treeHeading, outline);
    return box;
  }

  private ScrollPane designArea() {
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
    StackPane stage = new StackPane(canvas);
    stage.setPadding(new Insets(30));
    stage.getStyleClass().add("gui-stage");
    ScrollPane scroll = new ScrollPane(stage);
    scroll.setFitToWidth(true);
    scroll.setFitToHeight(true);
    scroll.getStyleClass().add("gui-scroll");
    return scroll;
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
    box.getChildren().addAll(heading, new Separator());
    addProperty(box, "Título da janela", formTitleField);
    addProperty(box, "Largura da janela", formWidthField);
    addProperty(box, "Altura da janela", formHeightField);
    componentHeading = new Label("Componente selecionado");
    componentHeading.getStyleClass().add("section-heading");
    box.getChildren().add(componentHeading);
    inspector.setVisible(false);
    inspector.setManaged(false);
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
    addProperty(inspector, "Posição X", xField);
    addProperty(inspector, "Posição Y", yField);
    addProperty(inspector, "Largura", widthField);
    addProperty(inspector, "Altura", heightField);
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
    box.getChildren().add(inspector);
    ScrollPane scroll = new ScrollPane(box);
    scroll.setFitToWidth(true);
    scroll.setFitToHeight(true);
    scroll.setPrefWidth(255);
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
    double width = type == GuiForm.Type.LABEL ? 180 : 160;
    double height = type == GuiForm.Type.LABEL ? 34 : 38;
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
    canvas.getChildren().clear();
    outline.getItems().setAll(form.elements);
    for (GuiForm.Element element : form.elements) {
      Node node = widget(element);
      node.setLayoutX(element.x);
      node.setLayoutY(element.y);
      node.setUserData(element);
      node.getStyleClass().add("gui-component");
      node.pseudoClassStateChanged(SELECTED, element == selected);
      Region region = (Region) node;
      region.setPrefSize(element.width, element.height);
      region.setMinSize(50, 28);
      node.setOnMousePressed(
          event -> {
            select(element);
            node.getProperties().put("dragOffset", new Point2D(event.getX(), event.getY()));
            event.consume();
          });
      node.setOnMouseDragged(
          event -> {
            Point2D offset = (Point2D) node.getProperties().get("dragOffset");
            Point2D cursor = canvas.sceneToLocal(event.getSceneX(), event.getSceneY());
            element.x =
                clamp(
                    snap(cursor.getX() - offset.getX()),
                    0,
                    form.width - node.getBoundsInLocal().getWidth());
            element.y =
                clamp(
                    snap(cursor.getY() - offset.getY()),
                    0,
                    form.height - node.getBoundsInLocal().getHeight());
            node.setLayoutX(element.x);
            node.setLayoutY(element.y);
            updateFields();
            event.consume();
          });
      node.setOnMouseReleased(event -> markChanged("Posição atualizada."));
      canvas.getChildren().add(node);
    }
    if (selected != null && form.elements.contains(selected)) outline.getSelectionModel().select(selected);
    else {
      selected = null;
      outline.getSelectionModel().clearSelection();
    }
    updateFields();
    rendering = false;
  }

  private Node widget(GuiForm.Element element) {
    return switch (element.type) {
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
      case CHECK_BOX -> new CheckBox(element.text);
    };
  }

  private void select(GuiForm.Element element) {
    if (rendering || element == selected) return;
    selected = element;
    for (Node node : canvas.getChildren()) {
      GuiForm.Element item = (GuiForm.Element) node.getUserData();
      node.pseudoClassStateChanged(SELECTED, item == selected);
    }
    outline.getSelectionModel().select(element);
    updateFields();
  }

  private void updateFields() {
    formTitleField.setText(form.title);
    formWidthField.setText(number(form.width));
    formHeightField.setText(number(form.height));
    boolean hasSelection = selected != null;
    inspector.setVisible(hasSelection);
    inspector.setManaged(hasSelection);
    componentHeading.setText(
        hasSelection ? "Componente selecionado · " + selected.type.label : "Componente selecionado");
    if (!hasSelection) return;
    nameField.setText(selected.name);
    textField.setText(selected.text);
    xField.setText(number(selected.x));
    yField.setText(number(selected.y));
    widthField.setText(number(selected.width));
    heightField.setText(number(selected.height));
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
    if (selected == null) return;
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

  private double snap(double value) {
    return Math.round(value / 8) * 8;
  }

  private void removeSelected() {
    if (selected == null) return;
    form.elements.remove(selected);
    selected = null;
    render();
    markChanged("Componente removido.");
  }

  private void save() {
    if (file == null) {
      FileChooser chooser = chooser();
      chooser.setInitialFileName("interface.cgui.json");
      java.io.File selectedFile = chooser.showSaveDialog(owner);
      if (selectedFile == null) return;
      file = selectedFile.toPath();
      if (!file.getFileName().toString().endsWith(".cgui.json"))
        file = file.resolveSibling(file.getFileName() + ".cgui.json");
    }
    try {
      GuiFormStore.write(file, form);
      savedSnapshot = GuiFormStore.serialize(form);
      status.setText("Salvo em " + file.getFileName());
      owner.setTitle("Construtor visual — Codalgora");
    } catch (IOException | IllegalArgumentException ex) {
      showError("Não foi possível salvar o formulário", ex);
    }
  }

  private void open() {
    if (!confirmDiscard()) return;
    java.io.File selectedFile = chooser().showOpenDialog(owner);
    if (selectedFile == null) return;
    try {
      GuiForm loaded = GuiFormStore.read(selectedFile.toPath());
      form.schemaVersion = loaded.schemaVersion;
      form.title = loaded.title;
      form.width = loaded.width;
      form.height = loaded.height;
      form.elements = loaded.elements;
      file = selectedFile.toPath();
      savedSnapshot = GuiFormStore.serialize(form);
      selected = null;
      render();
      status.setText("Formulário aberto: " + file.getFileName());
      owner.setTitle("Construtor visual — Codalgora");
    } catch (IOException | IllegalArgumentException ex) {
      showError("Arquivo de formulário inválido", ex);
    }
  }

  private void markChanged(String message) {
    boolean dirty = !GuiFormStore.serialize(form).equals(savedSnapshot);
    status.setText(dirty ? message + "  ·  Alterações não salvas" : message);
    owner.setTitle("Construtor visual — Codalgora" + (dirty ? " *" : ""));
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

  private void preview() {
    try {
      GuiFormStore.validate(form);
      Stage preview = new Stage();
      preview.initOwner(owner);
      Pane content = new Pane();
      content.setPrefSize(form.width, form.height);
      content.setMinSize(form.width, form.height);
      content.getStyleClass().add("gui-preview-canvas");
      for (GuiForm.Element element : form.elements) {
        Node node = widget(element);
        node.setLayoutX(element.x);
        node.setLayoutY(element.y);
        Region region = (Region) node;
        region.setPrefSize(element.width, element.height);
        region.setMinSize(50, 28);
        content.getChildren().add(node);
      }
      preview.setTitle(form.title);
      Scene scene = new Scene(content, form.width, form.height);
      scene.getStylesheets().add(stylesheet);
      scene.getRoot().setStyle(themeCss);
      preview.setScene(scene);
      preview.show();
    } catch (IllegalArgumentException ex) {
      showError("Não foi possível pré-visualizar", ex);
    }
  }

  private void showError(String title, Exception exception) {
    Alert alert = new Alert(Alert.AlertType.ERROR, exception.getMessage(), ButtonType.OK);
    alert.setTitle(title);
    alert.setHeaderText(title);
    alert.initOwner(owner);
    alert.showAndWait();
    status.setText(title + ".");
  }
}
