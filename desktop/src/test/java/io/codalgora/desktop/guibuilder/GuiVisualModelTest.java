package io.codalgora.desktop.guibuilder;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GuiVisualModelTest {
  @Test void allEightHandlesKeepOppositeEdgesAndRespectBounds() {
    GuiGeometry.Box start = new GuiGeometry.Box(80, 80, 120, 80);
    for (GuiGeometry.Handle handle : GuiGeometry.Handle.values()) {
      String d = handle.name();
      double dx = d.contains("W") ? -16 : d.contains("E") ? 16 : 0;
      double dy = d.contains("N") ? -16 : d.contains("S") ? 16 : 0;
      var b = GuiGeometry.resize(start, handle, dx, dy, 420, 520, true, false);
      assertEquals(start.width() + (dx == 0 ? 0 : 16), b.width(), handle.toString());
      assertEquals(start.height() + (dy == 0 ? 0 : 16), b.height(), handle.toString());
      for (double extreme : new double[] {-10000, 10000}) {
        var limited = GuiGeometry.resize(start, handle, extreme, extreme, 420, 520, true, true);
        assertTrue(limited.x() >= 0 && limited.y() >= 0);
        assertTrue(limited.width() >= 50 && limited.height() >= 28);
        assertTrue(limited.x() + limited.width() <= 420 && limited.y() + limited.height() <= 520);
      }
      var managed = GuiGeometry.resize(start, handle, dx, dy, 420, 520, false, false);
      assertEquals(start.x(), managed.x()); assertEquals(start.y(), managed.y());
    }
  }

  @Test void oldFormsGetSafeDefaultsAndStylesRoundTrip() {
    GuiForm old = GuiFormStore.deserialize("""
        {"schemaVersion":1,"title":"Antigo","width":420,"height":520,"elements":[
        {"id":"b1","type":"BUTTON","name":"calcular","text":"Calcular","x":16,"y":16,"width":120,"height":38}]}
        """);
    GuiForm.Appearance a = old.elements.getFirst().appearance;
    assertNotNull(a); assertEquals(13, a.fontSize);
    a.normal.background = "#12345680"; a.hover.foreground = "#ffffff";
    a.bold = true; a.fontSize = 22; a.alignment = GuiForm.Alignment.CENTER;
    old.spacing = 24; old.padding = 20;
    GuiForm loaded = GuiFormStore.deserialize(GuiFormStore.serialize(old));
    assertEquals("#12345680", loaded.elements.getFirst().appearance.normal.background);
    assertEquals("#ffffff", loaded.elements.getFirst().appearance.hover.foreground);
    assertTrue(loaded.elements.getFirst().appearance.bold);
    assertEquals(22, loaded.elements.getFirst().appearance.fontSize);
    assertEquals(24, loaded.spacing); assertEquals(20, loaded.padding);
  }

  @Test void rejectsCssExpressionsAndInvalidVisualValues() {
    GuiForm.Appearance a = new GuiForm.Appearance();
    a.normal.background = "url(file:///tmp/image.png)";
    assertThrows(IllegalArgumentException.class, () -> GuiFormStore.validateAppearance(a));
    a.normal.background = "#ffffff; -fx-font-size: 999";
    assertThrows(IllegalArgumentException.class, () -> GuiFormStore.validateAppearance(a));
    a.normal.background = null; a.fontFamily = "System'; -fx-background-image: url(test)";
    assertThrows(IllegalArgumentException.class, () -> GuiFormStore.validateAppearance(a));
    a.fontFamily = "System"; a.opacity = Double.NaN;
    assertThrows(IllegalArgumentException.class, () -> GuiFormStore.validateAppearance(a));
    a.opacity = 1; a.fontSize = 10000;
    assertThrows(IllegalArgumentException.class, () -> GuiFormStore.validateAppearance(a));
  }
}
