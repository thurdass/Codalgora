package io.codalgora.desktop.guibuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuiFormStoreTest {
  @TempDir Path directory;

  @Test
  void savesAndReloadsUtf8FormData() throws Exception {
    GuiForm form = new GuiForm();
    form.title = "Boas-vindas à programação";
    form.elements.add(new GuiForm.Element(GuiForm.Type.BUTTON, "botaoIniciar", "Começar", 28, 64, 160, 38));
    Path file = directory.resolve("boas-vindas.cgui.json");

    GuiFormStore.write(file, form);
    GuiForm loaded = GuiFormStore.read(file);

    assertEquals(form.title, loaded.title);
    assertEquals(form.elements.size(), loaded.elements.size());
    assertEquals(form.elements.getFirst().name, loaded.elements.getFirst().name);
    assertEquals(form.elements.getFirst().text, loaded.elements.getFirst().text);
    assertEquals(form.elements.getFirst().id, loaded.elements.getFirst().id);
  }

  @Test
  void rejectsFutureSchemaAndDuplicateNames() {
    assertThrows(IllegalArgumentException.class, () -> GuiFormStore.deserialize("{\"schemaVersion\":99}"));

    GuiForm form = new GuiForm();
    form.elements.add(new GuiForm.Element(GuiForm.Type.LABEL, "nome", "Nome", 0, 0, 100, 32));
    form.elements.add(new GuiForm.Element(GuiForm.Type.BUTTON, "NOME", "Enviar", 0, 48, 100, 36));
    assertThrows(IllegalArgumentException.class, () -> GuiFormStore.serialize(form));
  }

  @Test
  void rejectsMalformedJsonAndUnknownComponentTypes() {
    assertThrows(IllegalArgumentException.class, () -> GuiFormStore.deserialize("{not-json"));
    assertThrows(
        IllegalArgumentException.class,
        () -> GuiFormStore.deserialize("{\"title\":\"Sem versão\",\"elements\":[]}"));
    assertThrows(IllegalArgumentException.class, () -> GuiFormStore.deserialize(
        "{\"schemaVersion\":1,\"title\":\"Teste\",\"width\":420,\"height\":520,"
            + "\"elements\":[{\"id\":\"1\",\"type\":\"SCRIPT\",\"name\":\"x\","
            + "\"text\":\"x\",\"x\":0,\"y\":0,\"width\":80,\"height\":32}]}"));
  }
}
