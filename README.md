# DailyProject

Projeto Java que, **três vezes por dia**, gera pelo menos **100 linhas** de código, commita na branch `develop`, abre um pull request para `main`, comenta (com uma pergunta), abre uma discussão e, por fim, **aprova e mergeia** o PR.

A execução no GitHub é feita pelo workflow [`.github/workflows/daily.yml`](.github/workflows/daily.yml) (não basta só ter o código no repositório).

## Fluxo de cada execução

1. Gera uma classe Java em `src/main/java/com/dailyproject/generated/` (≥ 100 linhas)
2. Faz checkout de `develop`, commit e push
3. Abre (ou reutiliza) um PR `develop` → `main`
4. Publica um comentário no PR em primeira pessoa, sempre com **uma pergunta**
5. Abre uma **Discussion** no repositório
6. Tenta aprovar o PR e faz **squash merge** para `main`
7. Realinha `develop` com `main`

## Como roda no GitHub

O schedule (UTC → horário de Brasília):

| Cron (UTC) | Horário (America/Sao_Paulo) |
|------------|-----------------------------|
| `0 12 * * *` | 09:00 |
| `0 17 * * *` | 14:00 |
| `0 22 * * *` | 19:00 |

Também dá para disparar manualmente em **Actions → Daily develop → main → Run workflow**.

### Checklist no repositório

1. **Actions** habilitadas (*Settings → Actions → General*)
2. **Discussions** habilitadas (*Settings → General → Features → Discussions*)
3. Permissões do workflow: o YAML já pede `contents`, `pull-requests`, `discussions` e `issues` write
4. (Opcional, recomendado) Secret `DAILY_GITHUB_TOKEN` com um PAT (`repo` + `discussions`) se o `GITHUB_TOKEN` padrão não bastar para discussions/merge

## Build e execução local

```bash
cp .env.example .env
# cole o GITHUB_TOKEN no .env

mvn -q package
java -jar target/daily-project-1.0.0.jar --once   # uma execução
java -jar target/daily-project-1.0.0.jar          # agenda local 3x/dia
```

## Estrutura

```
.github/workflows/daily.yml   # agenda 3x/dia no GitHub
src/main/java/com/dailyproject/
  App.java
  Config.java
  CodeGenerator.java
  GitService.java
  GitHubService.java
  PersonaComments.java
  DailyWorkflow.java
  DailyScheduler.java         # só para modo local contínuo
  generated/                  # snippets commitados diariamente
```

## Notas

- O GitHub **não permite** aprovar o próprio PR; o app tenta `APPROVE` e, se a API recusar, segue para o merge.
- Se `main` tiver branch protection exigindo review de outra pessoa, configure um segundo token/bot ou ajuste a proteção.
