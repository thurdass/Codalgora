package io.codalgora.desktop.guibuilder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** JSON persistence and input validation for visual form documents. */
public final class GuiFormStore {
  public static final int MAX_ELEMENTS = 100;
  private static final long MAX_FILE_BYTES = 1_000_000;
  private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

  private GuiFormStore() {}

  public static GuiForm read(Path path) throws IOException {
    if (Files.size(path) > MAX_FILE_BYTES)
      throw new IllegalArgumentException("O arquivo ultrapassa o limite de 1 MB.");
    String source = Files.readString(path, StandardCharsets.UTF_8);
    return deserialize(source);
  }

  public static void write(Path path, GuiForm form) throws IOException {
    validate(form);
    Files.writeString(path, JSON.toJson(form), StandardCharsets.UTF_8);
  }

  public static String serialize(GuiForm form) {
    validate(form);
    return JSON.toJson(form);
  }

  public static GuiForm deserialize(String source) {
    if (source == null || source.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES)
      throw new IllegalArgumentException("O conteúdo está vazio ou ultrapassa o limite de 1 MB.");
    try {
      var json = JsonParser.parseString(source);
      if (!json.isJsonObject()) throw new IllegalArgumentException("A raiz do arquivo precisa ser um objeto JSON.");
      JsonObject root = json.getAsJsonObject();
      require(root, "schemaVersion", "title", "width", "height", "elements");
      if (root.get("elements").isJsonArray())
        for (var element : root.getAsJsonArray("elements")) {
          if (!element.isJsonObject()) throw new IllegalArgumentException("Componente inválido no formulário.");
          require(element.getAsJsonObject(), "id", "type", "name", "text", "x", "y", "width", "height");
        }
      GuiForm form = JSON.fromJson(source, GuiForm.class);
      validate(form);
      return form;
    } catch (JsonParseException | IllegalStateException ex) {
      throw new IllegalArgumentException("JSON de formulário inválido: " + ex.getMessage(), ex);
    }
  }

  private static void require(JsonObject object, String... fields) {
    for (String field : fields)
      if (!object.has(field) || object.get(field).isJsonNull())
        throw new IllegalArgumentException("O campo obrigatório '" + field + "' não foi informado.");
  }

  public static GuiForm.Element copyElement(GuiForm.Element element) {
    return JSON.fromJson(JSON.toJson(element), GuiForm.Element.class);
  }

  public static void validateAppearance(GuiForm.Appearance style) {
    if (style == null || style.alignment == null || style.fontFamily == null
        || !style.fontFamily.matches("[\\p{L}\\p{N} _.-]{1,80}"))
      throw new IllegalArgumentException("Fonte ou alinhamento inválido.");
    range(style.fontSize, 8, 96, "Tamanho da fonte");
    range(style.borderWidth, 0, 12, "Borda");
    range(style.radius, 0, 64, "Arredondamento");
    range(style.padding, 0, 64, "Preenchimento");
    range(style.opacity, 0, 1, "Opacidade");
    for (GuiForm.Colors colors : new GuiForm.Colors[] {style.normal, style.hover, style.pressed, style.disabled}) {
      if (colors == null) throw new IllegalArgumentException("Estado visual inválido.");
      for (String value : new String[] {colors.background, colors.foreground, colors.border})
        if (value != null && !value.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))
          throw new IllegalArgumentException("Use cores hexadecimais #RRGGBB ou #RRGGBBAA.");
    }
  }

  private static void range(double value, double min, double max, String label) {
    if (!Double.isFinite(value) || value < min || value > max)
      throw new IllegalArgumentException(label + " fora dos limites.");
  }

  public static void validate(GuiForm form) {
    if (form == null || form.schemaVersion != 1)
      throw new IllegalArgumentException("Versão de formulário ausente ou não suportada.");
    if (form.title == null || form.title.isBlank() || form.title.length() > 120)
      throw new IllegalArgumentException("Informe um título de até 120 caracteres.");
    if (!Double.isFinite(form.width) || !Double.isFinite(form.height)
        || form.width < 320 || form.width > 1200
        || form.height < 240 || form.height > 1000)
      throw new IllegalArgumentException("Tamanho de janela fora dos limites permitidos.");
    if (form.backgroundColor != null
        && !form.backgroundColor.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))
      throw new IllegalArgumentException("Use uma cor de fundo hexadecimal #RRGGBB ou #RRGGBBAA.");
    if (form.elements == null || form.elements.size() > MAX_ELEMENTS)
      throw new IllegalArgumentException("O formulário precisa ter até " + MAX_ELEMENTS + " componentes.");
    if (form.algorithmPath != null && form.algorithmPath.length() > 1000)
      throw new IllegalArgumentException("O caminho do algoritmo é longo demais.");
    if (form.layout == null) throw new IllegalArgumentException("Layout desconhecido.");
    range(form.spacing, 0, 64, "Espaçamento");
    range(form.padding, 0, 64, "Margem interna da janela");
    Set<String> ids = new HashSet<>(), names = new HashSet<>();
    Set<String> editableBindings = new HashSet<>();
    boolean hasEvent = false;
    boolean hasVariableBinding = false;
    for (GuiForm.Element element : form.elements) {
      if (element == null || element.type == null || element.id == null || element.name == null || element.text == null)
        throw new IllegalArgumentException("Há um componente com dados incompletos.");
      if (element.id.isBlank() || element.name.isBlank() || element.name.length() > 80 || element.text.length() > 1000)
        throw new IllegalArgumentException("Nome e texto precisam estar preenchidos e dentro dos limites.");
      if (!element.name.matches("[A-Za-z_][A-Za-z0-9_]*"))
        throw new IllegalArgumentException("O nome do componente deve ser um identificador simples.");
      if (!ids.add(element.id) || !names.add(element.name.toLowerCase(Locale.ROOT)))
        throw new IllegalArgumentException("IDs e nomes de componentes devem ser únicos.");
      if (element.eventRoutine != null) {
        hasEvent = true;
        if (element.type == GuiForm.Type.LABEL || element.type == GuiForm.Type.TEXT_AREA)
          throw new IllegalArgumentException(
              "Eventos podem ser associados a controles interativos.");
        if (element.eventRoutine.isBlank() || !element.eventRoutine.matches("[A-Za-z_][A-Za-z0-9_]*"))
          throw new IllegalArgumentException("O procedimento associado ao botão é inválido.");
      }
      if (element.variableName != null) {
        hasVariableBinding = true;
        if (element.type == GuiForm.Type.BUTTON)
          throw new IllegalArgumentException("Botões usam eventos; não podem ser vinculados a variáveis.");
        if (element.variableName.isBlank()
            || !element.variableName.matches("[A-Za-z_][A-Za-z0-9_]*"))
          throw new IllegalArgumentException("A variável associada ao componente é inválida.");
        if (element.type != GuiForm.Type.LABEL
            && !editableBindings.add(element.variableName.toLowerCase(Locale.ROOT)))
          throw new IllegalArgumentException(
              "Uma variável só pode receber entrada de um componente por vez.");
      }
      validateAppearance(element.appearance);
      if (!Double.isFinite(element.x) || !Double.isFinite(element.y)
          || !Double.isFinite(element.width) || !Double.isFinite(element.height)
          || element.x < 0 || element.y < 0 || element.width < 50 || element.height < 28
          || element.x + element.width > form.width || element.y + element.height > form.height)
        throw new IllegalArgumentException("Posição ou tamanho de componente fora da janela.");
    }
    if (hasEvent && (form.algorithmPath == null || form.algorithmPath.isBlank()))
      throw new IllegalArgumentException("Vincule um arquivo .alg para usar os eventos dos botões.");
    if (hasVariableBinding && (form.algorithmPath == null || form.algorithmPath.isBlank()))
      throw new IllegalArgumentException("Vincule um arquivo .alg para usar variáveis Portugol.");
  }
}
