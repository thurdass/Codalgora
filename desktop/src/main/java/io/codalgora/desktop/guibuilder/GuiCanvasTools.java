package io.codalgora.desktop.guibuilder;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

/** Design-only overlay: gestures never reach the controls' runtime behavior. */
final class GuiCanvasTools extends StackPane {
  private final GuiForm form;
  private final Pane canvas, overlay = new Pane();
  private final Function<GuiForm.Element, Node> factory;
  private final Consumer<GuiForm.Element> select;
  private final Consumer<String> change;
  private final Runnable fields, rebuild;
  private final Map<GuiForm.Element, Region> widgets = new LinkedHashMap<>();
  private final Map<GuiGeometry.Handle, Region> handles = new EnumMap<>(GuiGeometry.Handle.class);
  private final Rectangle frame = new Rectangle();
  private final Label size = new Label();
  private final Line guideX = new Line(), guideY = new Line();
  private final TextField inline = new TextField();
  private GuiForm.Element selected, editing;
  private String originalText;
  private Point2D dragStart;
  private GuiGeometry.Box original;
  private boolean moved;
  boolean snap = true;

  GuiCanvasTools(GuiForm form, Pane canvas, Function<GuiForm.Element, Node> factory,
                 Consumer<GuiForm.Element> select, Consumer<String> change, Runnable fields, Runnable rebuild) {
    this.form = form; this.canvas = canvas; this.factory = factory;
    this.select = select; this.change = change; this.fields = fields; this.rebuild = rebuild;
    getChildren().addAll(canvas, overlay);
    overlay.setPickOnBounds(false);
    frame.setFill(Color.TRANSPARENT); frame.setStroke(Color.web("#1677c8")); frame.setStrokeWidth(1.5);
    frame.setMouseTransparent(true); size.setMouseTransparent(true);
    size.setStyle("-fx-background-color: #1677c8; -fx-text-fill: white; -fx-padding: 2 5; -fx-font-size: 11px;");
    for (Line line : new Line[] {guideX, guideY}) { line.setStroke(Color.web("#1677c8")); line.getStrokeDashArray().setAll(4.0, 3.0); line.setMouseTransparent(true); line.setVisible(false); }
    overlay.getChildren().addAll(guideX, guideY, frame, size);
    for (GuiGeometry.Handle direction : GuiGeometry.Handle.values()) {
      Region handle = new Region(); handle.setId("guiResize-" + direction);
      handle.setStyle("-fx-background-color: white; -fx-border-color: #1677c8; -fx-border-width: 2;");
      handle.setCursor(Cursor.cursor(direction + "_RESIZE"));
      handle.setManaged(false); handle.resize(9, 9);
      handle.setOnMousePressed(e -> { begin(e); e.consume(); });
      handle.setOnMouseDragged(e -> {
        if (selected == null || original == null) return;
        Point2D point = sceneToLocal(e.getSceneX(), e.getSceneY());
        GuiGeometry.Box box = GuiGeometry.resize(original, direction, point.getX()-dragStart.getX(),
            point.getY()-dragStart.getY(), form.width, form.height, form.layout == GuiForm.LayoutMode.FREE, snap);
        selected.x = box.x(); selected.y = box.y(); selected.width = box.width(); selected.height = box.height();
        if (form.layout != GuiForm.LayoutMode.FREE) selected.growWidth = false;
        refreshGeometry(); fields.run(); e.consume();
      });
      handle.setOnMouseReleased(e -> { if (selected != null) change.accept("Tamanho atualizado."); e.consume(); });
      handles.put(direction, handle); overlay.getChildren().add(handle);
    }
    inline.setTextFormatter(new TextFormatter<String>(edit -> edit.getControlNewText().length() <= 1000 ? edit : null));
    inline.setId("guiInlineText"); inline.setManaged(false); inline.setVisible(false);
    inline.textProperty().addListener((o, old, value) -> { if (editing != null) { editing.text = value; text(widgets.get(editing), value); } });
    inline.setOnAction(e -> finishInline(true));
    inline.addEventFilter(KeyEvent.KEY_PRESSED, e -> { if (e.getCode() == KeyCode.ESCAPE) { finishInline(false); e.consume(); } });
    inline.focusedProperty().addListener((o, old, value) -> { if (old && !value) finishInline(true); });
    overlay.getChildren().add(inline);
  }

  void render(GuiForm.Element selection) {
    finishInline(true);
    widgets.clear(); canvas.getChildren().clear(); selected = selection;
    setMinSize(form.width, form.height); setPrefSize(form.width, form.height); setMaxSize(form.width, form.height);
    canvas.setMinSize(form.width, form.height); canvas.setPrefSize(form.width, form.height); canvas.setMaxSize(form.width, form.height);
    Pane content = canvas;
    if (form.layout != GuiForm.LayoutMode.FREE) {
      content = form.layout == GuiForm.LayoutMode.VERTICAL ? new VBox(form.spacing) : new GridPane();
      content.setPadding(new Insets(form.padding)); content.setPrefSize(form.width, form.height);
      if (content instanceof GridPane grid) {
        grid.setHgap(form.spacing); grid.setVgap(form.spacing);
        for (int i=0; i<2; i++) { ColumnConstraints c = new ColumnConstraints(); c.setPercentWidth(50); grid.getColumnConstraints().add(c); }
      }
      canvas.getChildren().add(content);
    }
    int index = 0;
    for (GuiForm.Element element : form.elements) {
      Region node = (Region) factory.apply(element);
      widgets.put(element, node); node.setUserData(element); node.getStyleClass().add("gui-component");
      node.setPrefSize(element.width, element.height); node.setMinSize(50, 28);
      node.setMaxHeight(Region.USE_PREF_SIZE);
      if (form.layout == GuiForm.LayoutMode.FREE) { node.relocate(element.x, element.y); content.getChildren().add(node); }
      else {
        node.setMaxWidth(element.growWidth ? Double.MAX_VALUE : Region.USE_PREF_SIZE);
        if (content instanceof GridPane grid) { grid.add(node, index%2, index/2); GridPane.setHgrow(node, Priority.ALWAYS); }
        else content.getChildren().add(node);
      }
      node.boundsInParentProperty().addListener((o, old, value) -> updateSelection());
      node.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> { if (e.getButton() == MouseButton.PRIMARY || e.getButton() == MouseButton.SECONDARY) { select.accept(element); requestFocus(); begin(e); } e.consume(); });
      node.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> { drag(element, e); e.consume(); });
      node.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
        if (moved && form.layout != GuiForm.LayoutMode.FREE) reorder(element, e);
        if (moved) change.accept("Componente movido.");
        guideX.setVisible(false); guideY.setVisible(false); e.consume();
      });
      node.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> { if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !moved) editInline(); e.consume(); });
      index++;
    }
    requestLayout(); javafx.application.Platform.runLater(this::updateSelection);
  }

  void select(GuiForm.Element element) { if (editing != null && editing != element) finishInline(true); selected = element; updateSelection(); }
  private void begin(MouseEvent event) {
    if (selected == null) return;
    dragStart = sceneToLocal(event.getSceneX(), event.getSceneY());
    original = new GuiGeometry.Box(selected.x, selected.y, selected.width, selected.height); moved = false;
  }
  private void drag(GuiForm.Element element, MouseEvent event) {
    if (original == null || dragStart == null) return;
    Point2D p = sceneToLocal(event.getSceneX(), event.getSceneY());
    double dx = p.getX()-dragStart.getX(), dy = p.getY()-dragStart.getY();
    if (Math.hypot(dx,dy) < 3 && !moved) return;
    moved = true;
    if (form.layout == GuiForm.LayoutMode.FREE) {
      element.x = Math.max(0, Math.min(form.width-element.width, align(GuiGeometry.grid(original.x()+dx, snap), true)));
      element.y = Math.max(0, Math.min(form.height-element.height, align(GuiGeometry.grid(original.y()+dy, snap), false)));
      refreshGeometry(); fields.run();
    }
  }
  private double align(double coordinate, boolean horizontal) {
    Line guide = horizontal ? guideX : guideY; guide.setVisible(false);
    double length = horizontal ? selected.width : selected.height;
    for (GuiForm.Element item : form.elements) {
      if (item == selected) continue;
      double start = horizontal ? item.x : item.y, end = start + (horizontal ? item.width : item.height);
      for (double target : new double[] {start, (start+end)/2, end})
        for (double offset : new double[] {0, length/2, length})
          if (Math.abs(coordinate+offset-target) < 5) {
            if (horizontal) { guide.setStartX(target); guide.setEndX(target); guide.setStartY(0); guide.setEndY(form.height); }
            else { guide.setStartY(target); guide.setEndY(target); guide.setStartX(0); guide.setEndX(form.width); }
            guide.setVisible(true); return target-offset;
          }
    }
    return coordinate;
  }
  private void reorder(GuiForm.Element element, MouseEvent event) {
    for (var entry : widgets.entrySet()) {
      if (entry.getKey() == element) continue;
      Bounds b = entry.getValue().localToScene(entry.getValue().getBoundsInLocal());
      if (b.contains(event.getSceneX(), event.getSceneY())) {
        int target = form.elements.indexOf(entry.getKey()); form.elements.remove(element); form.elements.add(target, element);
        rebuild.run(); return;
      }
    }
  }
  void refreshGeometry() {
    Region node = widgets.get(selected); if (node == null) return;
    node.setPrefSize(selected.width, selected.height);
    if (form.layout == GuiForm.LayoutMode.FREE) node.resizeRelocate(selected.x, selected.y, selected.width, selected.height);
    else { node.setMaxWidth(selected.growWidth ? Double.MAX_VALUE : Region.USE_PREF_SIZE); node.getParent().requestLayout(); }
    updateSelection();
  }
  void refreshAppearance() { widgets.forEach((element, node) -> GuiAppearance.apply(node, element.appearance)); refreshGeometry(); }
  private void updateSelection() {
    Region node = widgets.get(selected); boolean visible = node != null;
    frame.setVisible(visible); size.setVisible(visible); handles.values().forEach(h -> h.setVisible(visible));
    if (!visible || node.getScene() == null) return;
    Bounds b = overlay.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
    frame.setX(b.getMinX()); frame.setY(b.getMinY()); frame.setWidth(b.getWidth()); frame.setHeight(b.getHeight());
    size.setText(Math.round(selected.width) + " × " + Math.round(selected.height)); size.autosize();
    size.relocate(b.getMinX(), b.getMinY()-size.getHeight()-8);
    for (var entry : handles.entrySet()) {
      String d = entry.getKey().name();
      double x = d.contains("W") ? b.getMinX() : d.contains("E") ? b.getMaxX() : (b.getMinX()+b.getMaxX())/2;
      double y = d.contains("N") ? b.getMinY() : d.contains("S") ? b.getMaxY() : (b.getMinY()+b.getMaxY())/2;
      entry.getValue().relocate(x-4.5,y-4.5);
    }
  }
  void editInline() {
    if (selected == null) return;
    finishInline(true); editing = selected; originalText = editing.text;
    Region node = widgets.get(editing);
    Bounds b = overlay.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
    inline.setText(editing.text); inline.resizeRelocate(b.getMinX(), b.getMinY(), Math.max(120,b.getWidth()), Math.max(32,b.getHeight()));
    inline.setVisible(true); inline.toFront(); inline.requestFocus(); inline.selectAll();
  }
  void finishInline(boolean commit) {
    if (editing == null) return;
    GuiForm.Element item = editing; editing = null;
    if (!commit || item.text.length() > 1000) item.text = originalText;
    text(widgets.get(item), item.text); inline.setVisible(false);
    fields.run(); if (commit) change.accept("Texto atualizado.");
  }
  private void text(Node node, String text) {
    if (node instanceof Labeled label) label.setText(text);
    else if (node instanceof TextInputControl input) input.setPromptText(text);
    else if (node instanceof ComboBox<?> combo) {
      @SuppressWarnings("unchecked") ComboBox<String> choices = (ComboBox<String>) combo;
      choices.getItems().setAll(java.util.Arrays.stream(text.split("\\|")).map(String::strip).filter(s -> !s.isEmpty()).toList());
      choices.getSelectionModel().selectFirst();
    }
  }
}
