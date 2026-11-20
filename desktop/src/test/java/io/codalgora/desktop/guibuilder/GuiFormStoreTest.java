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
  void backgroundColorIsOptionalValidatedAndPersisted() {
    GuiForm form = new GuiForm();
    assertNull(GuiFormStore.deserialize(GuiFormStore.serialize(form)).backgroundColor);

    form.backgroundColor = "#12345680";
    assertEquals("#12345680", GuiFormStore.deserialize(GuiFormStore.serialize(form)).backgroundColor);

    form.backgroundColor = "red; -fx-font-size: 99px";
    assertThrows(IllegalArgumentException.class, () -> GuiFormStore.serialize(form));
  }

  @Test
  void preservesAlgorithmAndButtonProcedureBinding() {
    GuiForm form = new GuiForm();
    form.algorithmPath = "../algoritmos/boas-vindas.alg";
    GuiForm.Element button =
        new GuiForm.Element(GuiForm.Type.BUTTON, "botaoIniciar", "Começar", 28, 64, 160, 38);
    button.eventRoutine = "iniciar";
    form.elements.add(button);
    GuiForm.Element input =
        new GuiForm.Element(GuiForm.Type.TEXT_FIELD, "campoNome", "Nome", 28, 112, 160, 38);
    input.variableName = "nome";
    form.elements.add(input);

    GuiForm loaded = GuiFormStore.deserialize(GuiFormStore.serialize(form));

    assertEquals(form.algorithmPath, loaded.algorithmPath);
    assertEquals("iniciar", loaded.elements.getFirst().eventRoutine);
    assertEquals("nome", loaded.elements.get(1).variableName);
  }

  @Test
  void rejectsInvalidOrUnlinkedButtonEvents() {
    GuiForm form = new GuiForm();
    GuiForm.Element button =
        new GuiForm.Element(GuiForm.Type.BUTTON, "botao", "Executar", 20, 20, 160, 38);
    button.eventRoutine = "iniciar";
    form.elements.add(button);
    assertThrows(IllegalArgumentException.class, () -> GuiFormStore.validate(form));

    form.algorithmPath = "programa.alg";
    button.eventRoutine = "iniciar()";
    assertThrows(IllegalArgumentException.class, () -> GuiFormStore.validate(form));
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
