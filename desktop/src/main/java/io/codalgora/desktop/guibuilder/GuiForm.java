package io.codalgora.desktop.guibuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Versioned, JavaFX-independent representation of a visual form. */
public final class GuiForm {
  public int schemaVersion = 1;
  public String title = "Minha primeira interface";
  public double width = 420;
  public double height = 520;
  public LayoutMode layout = LayoutMode.FREE;
  public String algorithmPath;
  public List<Element> elements = new ArrayList<>();

  public static final class Element {
    public String id = UUID.randomUUID().toString();
    public Type type;
    public String name;
    public String text;
    public String eventRoutine;
    public String variableName;
    public double x;
    public double y;
    public double width;
    public double height;

    public Element() {}

    public Element(Type type, String name, String text, double x, double y, double width, double height) {
      this.type = type;
      this.name = name;
      this.text = text;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public enum Type {
    LABEL("Texto", "Texto"),
    BUTTON("Botão", "Botão"),
    TEXT_FIELD("Campo de texto", "Digite aqui"),
    TEXT_AREA("Área de texto", "Escreva aqui"),
    CHECK_BOX("Caixa de seleção", "Marcar opção"),
    COMBO_BOX("Lista suspensa", "Opção 1 | Opção 2");

    public final String label;
    public final String defaultText;

    Type(String label, String defaultText) {
      this.label = label;
      this.defaultText = defaultText;
    }
  }

  public enum LayoutMode {
    FREE("Livre"),
    VERTICAL("Coluna responsiva"),
    GRID("Grade responsiva");

    public final String label;

    LayoutMode(String label) {
      this.label = label;
    }
  }
}
