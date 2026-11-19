package io.codalgora.interpreter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

final class InterpreterTest {
  private String program(String declarations, String body) {
    return "algoritmo \"teste\"\nvar\n" + declarations + "\ninicio\n" + body + "\nfimalgoritmo\n";
  }

  private Snapshot run(String source) {
    return CodalgoraEngine.run(source, null, s -> {});
  }

  private Throwable failure(String source) {
    return assertThrows(CompletionException.class, () -> run(source)).getCause();
  }

  @Test
  void grammarProducesIndependentAstAndPreservesPortugueseText() {
    var ast =
        CodalgoraEngine.parse(
            program("N:inteiro", "N <- 2\nSE n > 0 ENTÃO\nescreval(\"ação\")\nfimse"));
    assertEquals("teste", ast.name());
    assertEquals("assign", ast.body().getFirst().kind());
    assertEquals("if", ast.body().getLast().kind());
    StringBuilder out = new StringBuilder();
    CodalgoraEngine.run(program("", "escreval(\"ação\")"), null, out::append);
    assertEquals("ação\n", out.toString());
  }

  @Test
  void antlrReportsInvalidSyntaxAtOriginalLine() {
    var error =
        assertThrows(
            CodalgoraException.class,
            () -> CodalgoraEngine.parse(program("x:inteiro", "se verdadeiro\nx <- 1\nfimse")));
    assertEquals(5, error.line());
    assertTrue(error.getMessage().contains("Sintaxe inválida"));
    assertThrows(
        CodalgoraException.class, () -> CodalgoraEngine.parse(program("", "escreval(\"inacabado)")));
  }

  @Test
  void typedInputPrecedenceAndFormattedOutput() {
    StringBuilder out = new StringBuilder();
    var result =
        CodalgoraEngine.run(
            program(
                "n:inteiro\nr:real", "leia(n)\nr <- n * 2 + 1\nescreval(\"resultado: \",r:0:2)"),
            () -> "4",
            out::append);
    assertEquals("resultado: 9.00\n", out.toString());
    assertEquals(9.0, result.variables().get("r"));
  }

  @Test
  void arraysNegativeBoundsDescendingLoopsAndControl() {
    var result =
        run(
            program(
                "v:vetor[-1..1] de inteiro\ni,s:inteiro",
                "para i de 1 ate -1 passo -1 faca\n"
                    + "v[i] <- i*i\n"
                    + "s <- s+v[i]\n"
                    + "fimpara\n"
                    + "enquanto s < 4 faca\n"
                    + "s <- s + 1\n"
                    + "fimenquanto\n"
                    + "repita\n"
                    + "s <- s - 1\n"
                    + "ate s = 2\n"
                    + "se s=2 entao\n"
                    + "s <- s+10\n"
                    + "senao\n"
                    + "s <- 0\n"
                    + "fimse"));
    assertEquals(12.0, result.variables().get("s"));
    assertEquals(List.of(1.0, 0.0, 1.0), result.variables().get("v"));
  }

  @Test
  void recursionProceduresByReferenceAndLexicalScope() {
    String source =
        """
        algoritmo "rotinas"
        var
        x:inteiro
        funcao fat(n:inteiro):inteiro
        inicio
        se n <= 1 entao
        retorne 1
        fimse
        retorne n * fat(n-1)
        fimfuncao
        procedimento dobro(var n:inteiro)
        inicio
        n <- n * 2
        fimprocedimento
        funcao global():inteiro
        inicio
        retorne x
        fimfuncao
        funcao caller():inteiro
        var
        x:inteiro
        inicio
        x <- 99
        retorne global()
        fimfuncao
        inicio
        x <- fat(5)
        dobro(x)
        x <- caller()
        fimalgoritmo
        """;
    assertEquals(240.0, run(source).variables().get("x"));
  }

  @Test
  void caseRangesShortCircuitAndUnconditionalRepeat() {
    var result =
        run(
            program(
                "n:inteiro",
                "n <- 5\n"
                    + "escolha n\n"
                    + "caso 4,5 ate 7\n"
                    + "n <- 8\n"
                    + "outrocaso\n"
                    + "n <- 0\n"
                    + "fimescolha\n"
                    + "se falso e 1/0 = 1 entao\n"
                    + "n <- 0\n"
                    + "fimse\n"
                    + "repita\n"
                    + "n <- n + 1\n"
                    + "se n=10 entao\n"
                    + "interrompa\n"
                    + "fimse\n"
                    + "fimrepita"));
    assertEquals(10.0, result.variables().get("n"));
  }

  @Test
  void constantsBoundArraysAndRejectWrites() {
    String source =
        "algoritmo \"const\"\n"
            + "const\n"
            + "base=2\n"
            + "max=base*3+1\n"
            + "var\n"
            + "v:vetor[-base..max] de inteiro\n"
            + "inicio\n"
            + "v[max] <- base\n"
            + "fimalgoritmo";
    assertEquals(7.0, run(source).variables().get("max"));
    assertTrue(
        failure(source.replace("v[max] <- base", "base <- 9")).getMessage().contains("constante"));
  }

  @Test
  void diagnosticsRejectBoundsZeroDivisionAndInfiniteLoop() {
    assertTrue(
        failure(program("v:vetor[1..2] de inteiro", "v[0] <- 1"))
            .getMessage()
            .contains("Índice fora"));
    assertTrue(failure(program("x:real", "x <- 1/0")).getMessage().contains("Divisão por zero"));
    var session =
        CodalgoraEngine.createSession(
                program("", "enquanto verdadeiro faca\nfimenquanto"), null, null, null)
            .limits(10, 10);
    assertTrue(
        assertThrows(CompletionException.class, () -> session.start(false).join())
            .getCause()
            .getMessage()
            .contains("Limite de instruções"));
    assertThrows(
        CodalgoraException.class,
        () ->
            CodalgoraEngine.createSession(
                program("v:vetor[1..10000,1..10000] de inteiro", ""), null, null, null));
  }

  @Test
  void debuggerBreakpointsInspectBeforeExecutionAndResume() throws Exception {
    var paused = new LinkedBlockingQueue<Snapshot>();
    var session =
        CodalgoraEngine.createSession(
            program("x:inteiro", "x <- 1\nx <- 2\nx <- 3"),
            null,
            null,
            s -> {
              if (s.status() == Status.PAUSED) paused.add(s);
            });
    session.setBreakpoints(Set.of(6));
    var future = session.start(true);
    var first = paused.poll(5, TimeUnit.SECONDS);
    assertNotNull(first);
    assertEquals(5, first.line());
    assertEquals(0.0, first.variables().get("x"));
    session.step();
    var second = paused.poll(5, TimeUnit.SECONDS);
    assertNotNull(second);
    assertEquals(6, second.line());
    assertEquals(1.0, second.variables().get("x"));
    session.resume();
    assertEquals(3.0, future.get(5, TimeUnit.SECONDS).variables().get("x"));
  }

  @Test
  void stopCancelsPausedAndBlockedInput() throws Exception {
    var pauses = new CountDownLatch(1);
    var paused =
        CodalgoraEngine.createSession(
            program("x:inteiro", "x <- 1"),
            null,
            null,
            s -> {
              if (s.status() == Status.PAUSED) pauses.countDown();
            });
    var a = paused.start(true);
    assertTrue(pauses.await(5, TimeUnit.SECONDS));
    paused.stop();
    assertEquals(Status.STOPPED, a.get(5, TimeUnit.SECONDS).status());
    var reads = new CountDownLatch(1);
    var blocked =
        CodalgoraEngine.createSession(
            program("x:inteiro", "leia(x)"),
            () -> {
              reads.countDown();
              Thread.sleep(60000);
              return "0";
            },
            null,
            null);
    var b = blocked.start(false);
    assertTrue(reads.await(5, TimeUnit.SECONDS));
    blocked.stop();
    assertEquals(Status.STOPPED, b.get(5, TimeUnit.SECONDS).status());
  }

  @Test
  void documentedMathSignaturesAndBarePiRand() {
    var result =
        CodalgoraEngine.createSession(
                program(
                    "a,b,c,d:real",
                    "a <- exp(2,9)\nb <- radpgrau(pi)\nc <- arcsen(1)+arccos(0)\nd <- rand"),
                null,
                null,
                null)
            .random(() -> 0.25)
            .start(false)
            .join();
    assertEquals(512.0, result.variables().get("a"));
    assertEquals(180.0, result.variables().get("b"));
    assertEquals(Math.PI, (Double) result.variables().get("c"), 1e-12);
    assertEquals(0.25, result.variables().get("d"));
    assertTrue(failure(program("x:real", "x <- exp(2)")).getMessage().contains("argumentos"));
  }

  @Test
  void textFunctionsMatchDocumentedOrdering() {
    StringBuilder out = new StringBuilder();
    CodalgoraEngine.run(
        program(
            "",
            "escreval(pos(\"Brasil\",\"O melhor do Brasil\"),\" \",copia(\"abcdef\",2,3),\""
                + " \",asc(\"B\"),\" \",carac(65))"),
        null,
        out::append);
    assertEquals("13 bcd 66 A\n", out.toString());
  }

  @Test
  void randomInputPrecisionEchoAndRestoredHumanInput() {
    StringBuilder out = new StringBuilder();
    AtomicInteger inputs = new AtomicInteger();
    var result =
        CodalgoraEngine.createSession(
                program(
                    "x:inteiro\ny:real",
                    "eco off\n"
                        + "aleatorio 1,2,2\n"
                        + "leia(y)\n"
                        + "eco on\n"
                        + "aleatorio 7,7\n"
                        + "leia(x)\n"
                        + "aleatorio off\n"
                        + "leia(x)"),
                () -> {
                  inputs.incrementAndGet();
                  return "9";
                },
                out::append,
                null)
            .random(() -> 0.5)
            .start(false)
            .join();
    assertEquals(1.5, result.variables().get("y"));
    assertEquals(9.0, result.variables().get("x"));
    assertEquals(1, inputs.get());
    assertEquals("7\n", out.toString());
  }

  @Test
  void semicolonSeparatorsAndMatrixBounds() {
    var result =
        run(
            program(
                "v:vetor[1..2,-1..1] de real;\ni:inteiro;",
                "i <- 2; v[i,-1] <- 4.5; escreval(v[2,-1])"));
    assertEquals(2.0, result.variables().get("i"));
    assertTrue(
        failure(program("v:vetor[1..2,-1..1] de real", "v[1,2] <- 7"))
            .getMessage()
            .contains("Índice fora"));
  }

  @Test
  void interactiveSessionRunsProceduresSeriallyWithSharedGlobals() throws Exception {
    String source =
        "algoritmo \"eventos\"\n"
            + "var total:inteiro\n"
            + "procedimento incrementar\n"
            + "inicio\n"
            + "total <- total + 1\n"
            + "escreval(total)\n"
            + "fimprocedimento\n"
            + "inicio\n"
            + "total <- 0\n"
            + "escreval(\"pronto\")\n"
            + "fimalgoritmo\n";
    StringBuilder output = new StringBuilder();
    DebugSession session = CodalgoraEngine.createInteractiveSession(source, null, output::append, null);
    CompletableFuture<Snapshot> stopped = session.startInteractive(false);

    assertEquals(1.0, session.fire("incrementar").get(5, TimeUnit.SECONDS).variables().get("total"));
    assertEquals(2.0, session.fire("incrementar").get(5, TimeUnit.SECONDS).variables().get("total"));
    session.stop();

    assertEquals(Status.STOPPED, stopped.get(5, TimeUnit.SECONDS).status());
    assertEquals("pronto\n1\n2\n", output.toString());
  }

  @Test
  void interactiveSessionRejectsFunctionsAndUnknownHandlers() throws Exception {
    String source =
        "algoritmo \"eventos\"\n"
            + "funcao resposta():inteiro\n"
            + "inicio\n"
            + "retorne 1\n"
            + "fimfuncao\n"
            + "procedimento agir\n"
            + "inicio\n"
            + "escreval(\"ok\")\n"
            + "fimprocedimento\n"
            + "inicio\n"
            + "escreval(\"pronto\")\n"
            + "fimalgoritmo\n";
    DebugSession session = CodalgoraEngine.createInteractiveSession(source, null, null, null);
    CompletableFuture<Snapshot> stopped = session.startInteractive(false);
    assertThrows(ExecutionException.class, () -> session.fire("resposta").get(5, TimeUnit.SECONDS));
    assertThrows(ExecutionException.class, () -> session.fire("ausente").get(5, TimeUnit.SECONDS));
    assertEquals(Status.RUNNING, session.fire("agir").get(5, TimeUnit.SECONDS).status());
    session.stop();
    assertEquals(Status.STOPPED, stopped.get(5, TimeUnit.SECONDS).status());
  }

  @Test
  void visualBindingsSetGlobalValuesBeforeProcedureAndReturnUpdatedValues() throws Exception {
    String source =
        "algoritmo \"vinculos\"\n"
            + "var nome:caractere\n"
            + "procedimento atualizar\n"
            + "inicio\n"
            + "escreval(nome)\n"
            + "nome <- \"Codalgora\"\n"
            + "fimprocedimento\n"
            + "inicio\n"
            + "nome <- \"\"\n"
            + "fimalgoritmo\n";
    StringBuilder output = new StringBuilder();
    DebugSession session = CodalgoraEngine.createInteractiveSession(source, null, output::append, null);
    CompletableFuture<Snapshot> stopped = session.startInteractive(false);

    Snapshot result = session.fire("atualizar", Map.of("nome", "Arthur")).get(5, TimeUnit.SECONDS);

    assertEquals("Arthur", output.toString().strip());
    assertEquals("Codalgora", result.variables().get("nome"));
    session.stop();
    assertEquals(Status.STOPPED, stopped.get(5, TimeUnit.SECONDS).status());
  }

  @Test
  void interactiveProcedureParametersUseNamedGlobalBindings() throws Exception {
    String source =
        "algoritmo \"parametros visuais\"\n"
            + "var\ncontador:inteiro\n"
            + "procedimento avancar(var contador:inteiro)\n"
            + "inicio\ncontador <- contador + 1\nfimprocedimento\n"
            + "inicio\ncontador <- 0\nfimalgoritmo\n";
    DebugSession session = CodalgoraEngine.createInteractiveSession(source, null, null, null);
    CompletableFuture<Snapshot> stopped = session.startInteractive(false);

    Snapshot result = session.fire("avancar", Map.of("contador", "40")).get(5, TimeUnit.SECONDS);

    assertEquals(41.0, result.globals().get("contador"));
    session.stop();
    assertEquals(Status.STOPPED, stopped.get(5, TimeUnit.SECONDS).status());
  }

  @Test
  void interactiveSessionProcessesOneHundredQueuedEventsWithoutLoss() throws Exception {
    String source =
        "algoritmo \"eventos em fila\"\n"
            + "var total:inteiro\n"
            + "procedimento incrementar\n"
            + "inicio\n"
            + "total <- total + 1\n"
            + "fimprocedimento\n"
            + "inicio\n"
            + "total <- 0\n"
            + "fimalgoritmo\n";
    DebugSession session = CodalgoraEngine.createInteractiveSession(source, null, null, null);
    CompletableFuture<Snapshot> stopped = session.startInteractive(false);
    List<CompletableFuture<Snapshot>> clicks = new ArrayList<>();

    for (int click = 0; click < 100; click++) clicks.add(session.fire("incrementar"));

    CompletableFuture.allOf(clicks.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
    assertEquals(100.0, clicks.getLast().get().globals().get("total"));
    session.stop();
    assertEquals(Status.STOPPED, stopped.get(5, TimeUnit.SECONDS).status());
  }
}
