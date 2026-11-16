package io.codalgora.interpreter;

public final class CodalgoraException extends RuntimeException {
  private final int line;

  public CodalgoraException(int line, String message) {
    super("Linha " + line + ": " + message);
    this.line = line;
  }

  public int line() {
    return line;
  }
}
