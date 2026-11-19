package io.codalgora.desktop.guibuilder;

import java.util.Locale;
import java.util.function.Consumer;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/** Visual inputs for the bounded appearance model. */
final class GuiStyleInspector extends VBox {
  private GuiForm.Element element;
  private boolean loading;
  private final Runnable changed;
  private final ComboBox<String> state = new ComboBox<>();
  private final ColorPicker[] colors = {new ColorPicker(), new ColorPicker(), new ColorPicker()};
  private final TextField[] hex = {new TextField(), new TextField(), new TextField()};
  private final ComboBox<String> font = new ComboBox<>();
  private final ComboBox<GuiForm.Alignment> alignment = new ComboBox<>();
  private final CheckBox bold = new CheckBox("Negrito"), italic = new CheckBox("Itálico");
  private final CheckBox grow = new CheckBox("Preencher largura no layout");
  private final Spinner<Double> size, border, radius, padding, opacity;

  GuiStyleInspector(Runnable changed) {
    super(8);
    this.changed = changed;
    state.getItems().setAll("Normal", "Ao passar o mouse", "Pressionado", "Desativado");
    state.setId("guiVisualState");
    state.getSelectionModel().selectFirst();
    state.setMaxWidth(Double.MAX_VALUE);
    state.setOnAction(e -> refreshColors());
    VBox palette = new VBox(7, new Label("Estado do controle"), state);
    String[] labels = {"Fundo", "Texto", "Borda"};
    String[] ids = {"Background", "Foreground", "Border"};
    for (int i = 0; i < 3; i++) {
      int index = i;
      colors[i].setId("gui" + ids[i] + "Color");
      hex[i].setId("gui" + ids[i] + "Hex");
      hex[i].setPromptText("Padrão / #RRGGBBAA");
      hex[i].setPrefColumnCount(10);
      colors[i].setPrefWidth(54);
      colors[i].setOnAction(e -> {
        if (loading || element == null) return;
        Color c = colors[index].getValue();
        setColor(index, String.format(Locale.ROOT, "#%02x%02x%02x%02x",
            Math.round(c.getRed()*255), Math.round(c.getGreen()*255), Math.round(c.getBlue()*255), Math.round(c.getOpacity()*255)));
        refreshColors();
      });
      hex[i].setOnAction(e -> applyHex(index));
      hex[i].focusedProperty().addListener((o, old, value) -> { if (old && !value) applyHex(index); });
      palette.getChildren().addAll(new Label(labels[i]), new HBox(6, colors[i], hex[i]));
    }
    font.getItems().setAll(Font.getFamilies().stream().filter(f -> f.matches("[\\p{L}\\p{N} _.-]{1,80}")).toList());
    font.setId("guiFontFamily");
    font.setMaxWidth(Double.MAX_VALUE);
    font.setOnAction(e -> { if (ready() && font.getValue() != null) { element.appearance.fontFamily = font.getValue(); changed.run(); } });
    bold.setId("guiBold"); italic.setId("guiItalic");
    bold.setOnAction(e -> { if (ready()) { element.appearance.bold = bold.isSelected(); changed.run(); } });
    italic.setOnAction(e -> { if (ready()) { element.appearance.italic = italic.isSelected(); changed.run(); } });
    size = number("guiFontSize", 8, 96, 13, v -> element.appearance.fontSize = v);
    border = number("guiBorderWidth", 0, 12, 1, v -> element.appearance.borderWidth = v);
    radius = number("guiRadius", 0, 64, 3, v -> element.appearance.radius = v);
    padding = number("guiPadding", 0, 64, 7, v -> element.appearance.padding = v);
    opacity = number("guiOpacity", 0, 1, 1, v -> element.appearance.opacity = v);
    opacity.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0, 1, 1, .05));
    alignment.getItems().setAll(GuiForm.Alignment.values());
    alignment.setConverter(new javafx.util.StringConverter<>() {
      public String toString(GuiForm.Alignment a) { return a == null ? "" : switch(a) { case DEFAULT -> "Padrão"; case LEFT -> "Esquerda"; case CENTER -> "Centro"; case RIGHT -> "Direita"; }; }
      public GuiForm.Alignment fromString(String s) { return GuiForm.Alignment.DEFAULT; }
    });
    alignment.setId("guiAlignment");
    alignment.setOnAction(e -> { if (ready()) { element.appearance.alignment = alignment.getValue(); changed.run(); } });
    grow.setId("guiGrowWidth");
    grow.setOnAction(e -> { if (ready()) { element.growWidth = grow.isSelected(); changed.run(); } });
    VBox typography = new VBox(7, new Label("Família"), font, new Label("Tamanho"), size,
        new HBox(8, bold, italic), new Label("Alinhamento"), alignment);
    VBox frame = new VBox(7, new Label("Espessura da borda"), border, new Label("Cantos arredondados"), radius,
        new Label("Preenchimento interno"), padding, new Label("Opacidade (0 a 1)"), opacity, grow);
    Button reset = new Button("Restaurar aparência padrão");
    reset.setId("guiResetStyle");
    reset.setOnAction(e -> { if (element != null) { element.appearance = new GuiForm.Appearance(); edit(element); changed.run(); } });
    TitledPane colorPane = new TitledPane("Cores e estados", palette);
    TitledPane fontPane = new TitledPane("Tipografia", typography); fontPane.setExpanded(false);
    TitledPane borderPane = new TitledPane("Bordas e preenchimento", frame); borderPane.setExpanded(false);
    getChildren().addAll(colorPane, fontPane, borderPane, reset);
  }

  private boolean ready() { return !loading && element != null; }
  private Spinner<Double> number(String id, double min, double max, double value, Consumer<Double> setter) {
    Spinner<Double> spinner = new Spinner<>(min, max, value);
    spinner.setId(id); spinner.setMaxWidth(Double.MAX_VALUE);
    spinner.valueProperty().addListener((o, old, v) -> { if (ready()) { setter.accept(v); changed.run(); } });
    return spinner;
  }
  private GuiForm.Colors current() {
    return switch(state.getSelectionModel().getSelectedIndex()) {
      case 1 -> element.appearance.hover; case 2 -> element.appearance.pressed;
      case 3 -> element.appearance.disabled; default -> element.appearance.normal;
    };
  }
  private void setColor(int i, String value) {
    GuiForm.Colors c = current();
    if (i == 0) c.background = value; else if (i == 1) c.foreground = value; else c.border = value;
    changed.run();
  }
  private void applyHex(int index) {
    if (!ready()) return;
    String value = hex[index].getText().strip();
    if (!value.isEmpty() && !value.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
      hex[index].setTooltip(new Tooltip("Use #RRGGBB ou #RRGGBBAA; vazio restaura o padrão."));
      hex[index].setStyle("-fx-border-color: #d34b4b;"); return;
    }
    hex[index].setStyle(""); setColor(index, value.isEmpty() ? null : value); refreshColors();
  }
  private void refreshColors() {
    if (element == null) return;
    boolean previous = loading; loading = true;
    GuiForm.Colors c = current();
    String[] values = {c.background, c.foreground, c.border};
    for (int i = 0; i < 3; i++) {
      hex[i].setText(values[i] == null ? "" : values[i]);
      colors[i].setValue(values[i] == null ? Color.WHITE : Color.web(values[i]));
    }
    loading = previous;
  }
  void edit(GuiForm.Element value) {
    element = value; setDisable(value == null); if (value == null) return;
    loading = true;
    GuiForm.Appearance a = value.appearance;
    font.setValue(a.fontFamily); size.getValueFactory().setValue(a.fontSize);
    bold.setSelected(a.bold); italic.setSelected(a.italic); alignment.setValue(a.alignment);
    border.getValueFactory().setValue(a.borderWidth); radius.getValueFactory().setValue(a.radius);
    padding.getValueFactory().setValue(a.padding); opacity.getValueFactory().setValue(a.opacity);
    grow.setSelected(value.growWidth); refreshColors(); loading = false;
  }
}
