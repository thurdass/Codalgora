package io.codalgora.desktop.guibuilder;

/** Resize calculations shared by all eight mouse handles. */
final class GuiGeometry {
  enum Handle { NW, N, NE, E, SE, S, SW, W }
  record Box(double x, double y, double width, double height) {}

  static Box resize(Box start, Handle handle, double dx, double dy,
                    double width, double height, boolean free, boolean snap) {
    String direction = handle.name();
    double left = start.x(), top = start.y(), right = left + start.width(), bottom = top + start.height();
    if (free) {
      if (direction.contains("W")) left = clamp(grid(left + dx, snap), 0, right - 50);
      if (direction.contains("E")) right = clamp(grid(right + dx, snap), left + 50, width);
      if (direction.contains("N")) top = clamp(grid(top + dy, snap), 0, bottom - 28);
      if (direction.contains("S")) bottom = clamp(grid(bottom + dy, snap), top + 28, height);
      return new Box(left, top, right - left, bottom - top);
    }
    double w = start.width(), h = start.height();
    if (direction.contains("W")) w -= dx;
    if (direction.contains("E")) w += dx;
    if (direction.contains("N")) h -= dy;
    if (direction.contains("S")) h += dy;
    return new Box(left, top, clamp(grid(w, snap), 50, width - left), clamp(grid(h, snap), 28, height - top));
  }

  static double grid(double value, boolean snap) { return snap ? Math.round(value / 8) * 8 : value; }
  private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
