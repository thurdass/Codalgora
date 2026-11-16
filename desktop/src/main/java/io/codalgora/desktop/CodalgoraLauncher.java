package io.codalgora.desktop;

/** Entry point kept separate from Application for packaged and classpath launches. */
public final class CodalgoraLauncher {
  private CodalgoraLauncher() {}

  public static void main(String[] args) {
    CodalgoraApplication.main(args);
  }
}
