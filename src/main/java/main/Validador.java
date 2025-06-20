package main;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.*;
import javax.tools.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class Validador {
    private static boolean verbose = false;

    public static void main(String[] args) throws Exception {
        String modo = null;

        for (String arg : args) {
            if (arg.startsWith("-")) {
                if (arg.contains("v")) verbose = true;
                if (arg.contains("z")) modo = "zip";
                else if (arg.contains("d")) modo = "dir";
                else if (arg.contains("i")) modo = "injetar";
            }
        }

        if (modo == null) {
            System.out.println("""
                Uso:
                  java -jar autocorretor.jar -z projeto.zip
                  java -jar autocorretor.jar -d projeto
                  java -jar autocorretor.jar -i dir_pacote proj_base
                """);
            return;
        }

        Path baseDir;

        switch (modo) {
            case "zip":
                if (args.length != 2 || !args[1].endsWith(".zip")) {
                    System.out.println("Uso: -z projeto.zip");
                    return;
                }
                Path zipPath = Paths.get(args[1]);
                Path tempDir = Files.createTempDirectory("projeto_java_");
                unzip(zipPath, tempDir);
                baseDir = encontrarRaizDoProjeto(tempDir);
                System.out.printf(Utils.color("Projeto descompactado e localizado em: %s%n", Color.CYAN), baseDir);
                validarProjeto(baseDir);
                deletarDiretorio(tempDir);
                break;

            case "dir":
                if (args.length != 2) {
                    System.out.println("Uso: -d projeto");
                    return;
                }
                baseDir = encontrarRaizDoProjeto(Paths.get(args[1]));
                validarProjeto(baseDir);
                break;

            case "injetar":
                if (args.length != 3) {
                    System.out.println("Uso: -i dir_pacote proj_base");
                    return;
                }
                Path dirPacote = Paths.get(args[1]);
                Path projBase = Paths.get(args[2]);

                limparProjetoBase(projBase);
                copiarPacoteParaProjetoBase(dirPacote, projBase.resolve("src/main/java"));
                baseDir = encontrarRaizDoProjeto(projBase);
                validarProjeto(baseDir);
                break;

            default:
                System.out.println("Argumento invalido.");
                break;
        }
    }

    private static List<Path> arquivosJava;

    static void limparProjetoBase(Path projBase) throws IOException, InterruptedException {
        Utils.printInfo("Limpando projeto base...");

        ProcessBuilder pb = new ProcessBuilder("gradle", "clean");
        pb.directory(projBase.toFile());
        if (verbose) pb.inheritIO();
        pb.start().waitFor();

        Path javaDir = projBase.resolve("src/main/java");
        if (Files.exists(javaDir)) {
            deletarDiretorio(javaDir);
        }
        Files.createDirectories(javaDir);
    }

    static void copiarPacoteParaProjetoBase(Path origem, Path destinoPai) throws IOException {
        System.out.println(Utils.color("Copiando pacote ", Color.CYAN) + origem + Utils.color(" para ", Color.CYAN) + destinoPai);
        System.out.println();

        Path destino = destinoPai.resolve(origem.getFileName()); // Cria pasta com mesmo nome do pacote
        Files.createDirectories(destino);

        Files.walk(origem)
                .forEach(orig -> {
                    try {
                        Path destinoFinal = destino.resolve(origem.relativize(orig));
                        if (Files.isDirectory(orig)) {
                            Files.createDirectories(destinoFinal);
                        } else {
                            Files.copy(orig, destinoFinal, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }


    static void validarProjeto(Path baseDir) throws IOException, InterruptedException {
        percorrerArquivos(baseDir);

        if (Files.exists(baseDir.resolve("pom.xml"))) {
            Utils.printInfo("Projeto Maven detectado.");
            executarTestesMaven(baseDir);
            listarTestesExecutados(baseDir);
        } else if (Files.exists(baseDir.resolve("build.gradle")) || Files.exists(baseDir.resolve("build.gradle.kts"))) {
            Utils.printInfo("Projeto Gradle detectado.\n");
            executarTestesGradle(baseDir);
            listarTestesExecutadosGradle(baseDir);
        } else {
            Utils.printInfo("Projeto simples detectado.");
            processarProjetoSimples(baseDir);
        }
    }

    static void listarTestesExecutados(Path baseDir) throws IOException {
        Path surefireDir = baseDir.resolve("target/surefire-reports");

        if (!Files.exists(surefireDir)) {
            Utils.printError("Diretorio 'target/surefire-reports' noo encontrado. Nenhum teste listado.");
            return;
        }

        Utils.printSuccess("\nTestes Maven executados:");
        try (Stream<Path> arquivos = Files.list(surefireDir)) {
            arquivos
                    .filter(p -> p.toString().endsWith(".txt") || p.toString().endsWith(".xml"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .forEach(nome -> System.out.println(" - " + nome));
        }
    }

    static void listarTestesExecutadosGradle(Path baseDir) throws IOException {
        Path resultsDir = baseDir.resolve("build/test-results/test");
        if (!Files.exists(resultsDir)) {
            Utils.printError("Nenhum teste listado.");
            return;
        }

        Utils.printSuccess("Testes Gradle executados:");
        listarResultadosDeTestes(resultsDir);
    }

    static void listarResultadosDeTestes(Path pastaXml) throws IOException {
        Files.walk(pastaXml)
                .filter(p -> p.toString().endsWith(".xml"))
                .sorted()
                .forEach(arquivo -> {
                    try {
                        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                        DocumentBuilder db = dbf.newDocumentBuilder();
                        Document doc = db.parse(arquivo.toFile());

                        Element root = doc.getDocumentElement();

                        String nomeSuite = root.getAttribute("name");
                        int total = Integer.parseInt(root.getAttribute("tests"));
                        int falhas = Integer.parseInt(root.getAttribute("failures"));
                        int erros = Integer.parseInt(root.getAttribute("errors"));
                        int ignorados = Integer.parseInt(root.getAttribute("skipped"));

                        int passou = total - falhas - erros - ignorados;
                        String nomeColorido = Utils.color(nomeSuite, falhas > 0 ? Color.RED : Color.GREEN);
                        System.out.printf("- %s: %d/%d passaram (falhas: %d, erros: %d, ignorados: %d)%n",
                            nomeColorido, passou, total, falhas, erros, ignorados);

                        NodeList testcases = root.getElementsByTagName("testcase");
                        for (int i = 0; i < testcases.getLength(); i++) {
                            Element tc = (Element) testcases.item(i);
                            NodeList falhasTc = tc.getElementsByTagName("failure");
                            NodeList errosTc = tc.getElementsByTagName("error");

                            if (falhasTc.getLength() > 0 || errosTc.getLength() > 0) {
                                String metodo = tc.getAttribute("name");
                                Utils.printError(String.format("  [x] Falhou: %s", metodo));
                            }
                        }
                    } catch (Exception e) {
                        Utils.printError("Erro ao processar: " + arquivo.getFileName());
                        e.printStackTrace();
                    }
                });
    }

    static Path encontrarRaizDoProjeto(Path baseDir) throws IOException {
        try (Stream<Path> caminhos = Files.walk(baseDir)) {
            Optional<Path> raizMaven = caminhos
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().equals("pom.xml"))
                    .findFirst();
            if (raizMaven.isPresent()) {
                return raizMaven.get().getParent();
            }
        }

        try (Stream<Path> caminhos = Files.walk(baseDir)) {
            Optional<Path> raizGradle = caminhos
                    .filter(p -> Files.isRegularFile(p) &&
                            (p.getFileName().toString().equals("build.gradle") ||
                                    p.getFileName().toString().equals("build.gradle.kts")))
                    .findFirst();
            if (raizGradle.isPresent()) {
                return raizGradle.get().getParent();
            }
        }

        return baseDir;
    }


    static List<String> carregarListaRequisitos() {
        List<String> arquivos = new ArrayList<>();
        Path file = Paths.get("requisitos");

        try (BufferedReader br = Files.newBufferedReader(file)) {
            String linha;
            while ((linha = br.readLine()) != null) {
                if (!linha.isEmpty() && !linha.isBlank())
                    arquivos.add(linha);
            }
        } catch (FileNotFoundException e) {
            Utils.printError("O arquivo 'requisitos' deve estar no mesmo diretorio que esse jar.");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            return arquivos;
        }
    }

    static void percorrerArquivos(Path dir) throws IOException {
        List<String> shouldContain = carregarListaRequisitos();

        if (shouldContain.isEmpty()) {
            Utils.printError("Nenhum requisito carregado");
        }

        List<String> createdFiles = listarArquivosJava(dir)
                .stream()
                .map(p -> {
                    Path relativePath = dir.relativize(p);
                    String pathStr = relativePath.toString().replace("\\", "/");
                    int index = pathStr.indexOf("src");
                    return pathStr.substring(index);
                }).toList();


        boolean allFilesCreated = shouldContain.stream().allMatch(f -> createdFiles.contains(f));
        if (allFilesCreated) {
            Utils.printSuccess("Voce criou todos os arquivos Java necessarios.");
        } else {
            Utils.printError("Alguns arquivos Java nao foram criados: ");
            shouldContain.stream()
                    .filter(elem -> !createdFiles.contains(elem))
                    .forEach(f -> System.out.println("-> " + f));
            System.out.println();
        }

        Utils.printInfo("As seguintes classes de testes foram carregadas:");
        listarArquivosJava(dir)
                .stream()
                .filter(p -> p.toString().replace("\\", "/").contains("/src/test/java/"))
                .forEach(f -> System.out.println("-> " + f));

        System.out.println();
    }

    static void unzip(Path zipFilePath, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path filePath = destDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(filePath);
                } else {
                    Files.createDirectories(filePath.getParent());
                    Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    static void processarProjetoSimples(Path baseDir) throws IOException {
        List<Path> arquivosJava = listarArquivosJava(baseDir);
        if (arquivosJava.isEmpty()) {
            Utils.printError("Nenhum arquivo .java encontrado.");
            return;
        }

        verificarEstrutura(baseDir, arquivosJava);

        if (compilarArquivos(arquivosJava, baseDir)) {
            Utils.printSuccess("Compilacao bem-sucedida. (Nao ha testes para executar automaticamente)");
        } else {
            Utils.printError("Falha na compilacao.");
        }
    }

    static List<Path> listarArquivosJava(Path dir) throws IOException {
        if (arquivosJava == null) {
            arquivosJava = Files.walk(dir)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
        }

        return arquivosJava;
    }

    static void verificarEstrutura(Path baseDir, List<Path> arquivos) {
        Set<String> pacotes = new HashSet<>();
        for (Path path : arquivos) {
            Path relativo = baseDir.relativize(path).getParent();
            if (relativo != null) {
                pacotes.add(relativo.toString());
            }
        }
        if (pacotes.isEmpty()) {
            Utils.printError("Nenhum pacote detectado.");
        } else {
            Utils.printInfo("Pacotes detectados:");
            pacotes.forEach(pkg -> System.out.println(" - " + pkg));
        }
    }

    static boolean compilarArquivos(List<Path> arquivos, Path baseDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path outputDir = baseDir.resolve("bin");
        Files.createDirectories(outputDir);
        List<String> options = List.of("-d", outputDir.toString());
        List<String> arquivosStr = arquivos.stream().map(Path::toString).toList();
        int resultado = compiler.run(null, null, null, Stream.concat(options.stream(), arquivosStr.stream()).toArray(String[]::new));
        return resultado == 0;
    }

    static void executarTestesMaven(Path baseDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("mvn", "test");
        pb.directory(baseDir.toFile());
        if (verbose) pb.inheritIO();
        Process p = pb.start();
        p.waitFor();
    }

    static void executarTestesGradle(Path baseDir) throws IOException, InterruptedException {
        boolean usaWrapper = Files.exists(baseDir.resolve("gradlew"));
        String os = System.getProperty("os.name").toLowerCase();

        String comando;
        if (usaWrapper) {
            // Em sistemas Unix-like (Linux, macOS), ./gradlew
            // Em Windows, gradlew.bat
            comando = os.contains("win") ? "gradlew.bat" : "./gradlew";

            if (!os.contains("win")) {
                // Torna ./gradlew executável
                new ProcessBuilder("chmod", "+x", comando)
                        .directory(baseDir.toFile())
                        .start()
                        .waitFor();
            }
        } else {
            comando = "gradle";
        }

        Path gradlewPath = baseDir.resolve(comando).toAbsolutePath();
        System.out.println(Utils.color("Usando: ", Color.CYAN) + comando);

        // Verificando se as classes compilam
        ProcessBuilder buildCompileProcess = new ProcessBuilder(gradlewPath.toString(), "compileJava");
        buildCompileProcess.directory(baseDir.toFile());
        if (verbose) buildCompileProcess.inheritIO();

        Process buildCompile = buildCompileProcess.start();
        int buildCompileResultado = buildCompile.waitFor();

        System.out.println();

        if (buildCompileResultado != 0) {
            Utils.printError("Falha ao compilar suas classes. Verifique erros de sintaxe antes de enviar.");
            return;
        } else {
            Utils.printSuccess("Nao ha erros de compilação nas suas classes.");
        }

        // Verificando se os testes compilam
        ProcessBuilder buildCompileTestProcess = new ProcessBuilder(gradlewPath.toString(), "compileTestJava");
        buildCompileTestProcess.directory(baseDir.toFile());
        buildCompileTestProcess.redirectErrorStream(true);

        Process buildCompileTest = buildCompileTestProcess.start();

        int buildCompileTestResultado = buildCompileTest.waitFor();

        if (buildCompileTestResultado != 0) {
            Utils.printError("\nFalha ao compilar os testes. Apague de 'src/test/java' os testes para as questoes que voce nao conseguiu solucionar.\n");
            return;
        } else {
            Utils.printSuccess("Não há erros de compilação nos testes.");
        }

        ProcessBuilder buildProcess = new ProcessBuilder(gradlewPath.toString(), "build");
        buildProcess.directory(baseDir.toFile());
        if (verbose) buildProcess.inheritIO();

        System.out.println(Utils.color("\nExecutando build com: ", Color.CYAN) + comando + " build");
        Process build = buildProcess.start();
        int buildResultado = build.waitFor();

        if (buildResultado != 0) {
            Utils.printError("Falha no build do projeto.");
            return;
        }

        ProcessBuilder pb = new ProcessBuilder(gradlewPath.toString(), "test");
        pb.directory(baseDir.toFile());
        if (verbose) pb.inheritIO();

        System.out.println();
        Process p = pb.start();
        int resultado = p.waitFor();

        if (resultado != 0) {
            Utils.printError("Falha ao executar os testes.");
        }
    }

    static void deletarDiretorio(Path path) throws IOException {
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}
