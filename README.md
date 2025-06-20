## Autocorretor

O Autocorretor é uma ferramenta desenvolvida para automatizar a avaliação de projetos Java enviados por estudantes, com foco em ensino de programação orientada a objetos. Ele foi pensado para reduzir o esforço manual na verificação de estrutura de projetos, compilação, execução de testes e aplicação de critérios objetivos.

### Funcionalidades Atuais

**Verificação de Estrutura de Projeto:**
- Confirma a presença de pastas padrão (`src/main/java`, `src/test/java`) e arquivos esperados, que são definidos em arquivo para cada atividade.
- Valida nomes de pacotes e classes conforme o enunciado.

**Modos de Entrada:**
- `-z`: aceita projeto zipado.
- `-d`: aceita diretório de projeto estruturado.
- `-i`: insere um pacote fornecido em um projeto base.

**Preparação e Compilação Automática:**
- Utiliza Gradle para compilar os projetos e garantir dependências.

**Execução de Testes Automatizados:**
- Executa testes JUnit previamente definidos.
- Gera relatório de sucesso/falha para cada teste.

**Geração de Relatório de Avaliação:**
- Lista erros de estrutura, falhas de compilação e resultados dos testes.
- Permite uso como base para feedback automatizado aos estudantes.


### Melhorias Futuras

- **Interface Gráfica (GUI)** simples para uso por estudantes sem familiaridade com linha de comando.
- Validação de estilo de código (ex: uso do Checkstyle, regras de identação ou nomenclatura).
- Parâmetros configuráveis por arquivo (ex: config.yml) para adaptar critérios por atividade.
- Detecção de uso de conceitos de POO via análise estática: herança, polimorfismo, composição etc.
- Exportação de relatórios em PDF.
- Integração com ambientes como Google Classroom.
- Execução segura em sandbox para evitar código malicioso.

### Exemplo de Uso
Após compilar o projeto (ou utilizar o `.jar` já empacotado), execute o autocorretor pelo terminal com uma das seguintes opções:

#### Opção 1: Avaliar um projeto zipado

```bash
java -jar autocorretor.jar -z caminho/para/projeto_do_aluno.zip
```

- Descompacta o projeto
- Valida a estrutura e presença dos arquivos exigidos
- Compila com Gradle
- Executa os testes JUnit
- Gera um relatório com os resultados

#### Opção 2: Avaliar um diretório de projeto estruturado

```bash
java -jar autocorretor.jar -d caminho/para/diretorio_do_projeto
```

Use esta opção se o projeto já estiver descompactado e organizado no formato padrão do Gradle (ex: `src/main/java`, `src/test/java`).

#### Opção 3: Inserir um pacote isolado em um projeto base

```bash
java -jar autocorretor.jar -i caminho/para/pacote caminho/para/projeto_base
```

- ⚠️ Substitui o conteúdo de `src/main/java` do projeto base pelo pacote fornecido
- Compila o projeto
- Executa os testes e gera o relatório de avaliação

#### Saída Esperada

Ao final da execução, será exibida uma saída no terminal como:

```
[INFO] Projeto compilado com sucesso.
[INFO] Executando testes...
[PASS] TestLivro.testToString
[FAIL] TestLivro.testEquals
[ERROR] Classe ausente: Autor.java
[TESTS] 1 Passou, 1 Falhou, 1 Erro
```

Um arquivo relatorio.txt (ou similar) pode ser gerado com os detalhes da avaliação, dependendo da configuração.