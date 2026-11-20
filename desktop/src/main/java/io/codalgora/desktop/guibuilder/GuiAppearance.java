package io.codalgora.desktop.guibuilder;

import java.util.Locale;
import javafx.scene.Node;

/** Only validated values are turned into CSS; documents never supply CSS expressions. */
final class GuiAppearance {
  private GuiAppearance() {}

  static void apply(Node node, GuiForm.Appearance style) {
    GuiFormStore.validateAppearance(style);
    StringBuilder css = new StringBuilder();
    css.append("-fx-font-family: '").append(style.fontFamily).append("';")
        .append("-fx-font-size: ").append(style.fontSize).append("px;")
        .append("-fx-font-weight: ").append(style.bold ? "bold" : "normal").append(';')
        .append("-fx-font-style: ").append(style.italic ? "italic" : "normal").append(';')
        .append("-fx-border-width: ").append(style.borderWidth).append(';')
        .append("-fx-border-radius: ").append(style.radius).append(';')
        .append("-fx-background-radius: ").append(style.radius).append(';')
        .append("-fx-padding: ").append(style.padding).append(';');
    if (style.alignment != GuiForm.Alignment.DEFAULT)
      css.append("-fx-alignment: center-").append(style.alignment.name().toLowerCase(Locale.ROOT)).append(';')
          .append("-fx-text-alignment: ").append(style.alignment.name().toLowerCase(Locale.ROOT)).append(';');
    // JavaFX alignment calls the middle position "center", not "center-center".
    String text = css.toString().replace("center-center", "center");
    css = new StringBuilder(text);
    GuiForm.Colors state = node.isDisabled() ? style.disabled : node.isPressed() ? style.pressed
        : node.isHover() ? style.hover : style.normal;
    color(css, "-fx-background-color", pick(state.background, style.normal.background));
    color(css, "-fx-control-inner-background", pick(state.background, style.normal.background));
    color(css, "-fx-text-fill", pick(state.foreground, style.normal.foreground));
    color(css, "-fx-border-color", pick(state.border, style.normal.border));
    node.setStyle(css.toString());
    if (node instanceof javafx.scene.control.ComboBox<?> combo && combo.getButtonCell() != null)
      combo.getButtonCell().setStyle(css + "-fx-background-color: transparent; -fx-padding: 0;");
    node.setOpacity(style.opacity);
  }

  private static String pick(String state, String normal) { return state == null ? normal : state; }
  private static void color(StringBuilder css, String property, String value) {
    if (value != null) css.append(property).append(':').append(value).append(';');
  }
}
