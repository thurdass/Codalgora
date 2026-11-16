package io.codalgora.interpreter;

@FunctionalInterface
public interface InputProvider {
  String read() throws Exception;
}
