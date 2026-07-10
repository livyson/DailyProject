# DailyProject

Projeto Java que, **três vezes por dia**, gera pelo menos **100 linhas** de código, commita na branch `develop`, abre um pull request para `main`, comenta (com uma pergunta), abre uma discussão e, por fim, **aprova e mergeia** o PR.

## Fluxo de cada execução

1. Gera uma classe Java em `src/main/java/com/dailyproject/generated/` (≥ 100 linhas)
2. Faz checkout de `develop`, commit e push
3. Abre (ou reutiliza) um PR `develop` → `main`
4. Publica um comentário no PR em primeira pessoa, sempre com **uma pergunta**
5. Abre uma **Discussion** no repositório
6. Aprova o PR e faz **squash merge** para `main`

## Pré-requisitos

- Java 17+ e Maven 3.9+
- Repositório GitHub com remote `origin` (já configurado)
- Token GitHub (`GITHUB_TOKEN`) com permissões:
  - `repo` (commits, PRs, merge, reviews)
  - `discussions:write` (criar discussões)
- **Discussions** habilitadas no repositório: *Settings → General → Features → Discussions*
- Branch protection: se `main` exigir aprovação de **outro** usuário, a API do GitHub **não permite** aprovar o próprio PR. O projeto tenta `APPROVE` e, se a API recusar, segue direto para o merge (desde que não haja regra bloqueando). Para aprovação “de verdade”, use um segundo token/bot ou desative a exigência em *Settings → Branches*.
- Token precisa ter permissão de merge na `main`

## Configuração

```bash
cp .env.example .env
# edite .env e cole o GITHUB_TOKEN
```

Variáveis principais:

| Variável | Padrão | Descrição |
|----------|--------|-----------|
| `GITHUB_TOKEN` | — | obrigatório |
| `GITHUB_OWNER` | `livyson` | dono do repo |
| `GITHUB_REPO` | `DailyProject` | nome do repo |
| `GIT_SOURCE_BRANCH` | `develop` | branch de trabalho |
| `GIT_TARGET_BRANCH` | `main` | branch alvo do PR |
| `MIN_LINES` | `100` | mínimo de linhas geradas |
| `SCHEDULE_TIMES` | `09:00,14:00,19:00` | 3 horários locais por dia |

## Build e execução

```bash
mvn -q package

# Uma execução imediata (útil para testar)
java -jar target/daily-project-1.0.0.jar --once

# Modo contínuo: agenda as 3 execuções diárias
java -jar target/daily-project-1.0.0.jar
```

Para deixar rodando em background (ex.: launchd/cron/tmux):

```bash
nohup java -jar target/daily-project-1.0.0.jar > daily-project.log 2>&1 &
```

Ou via cron (alternativa ao scheduler interno), três vezes ao dia:

```cron
0 9,14,19 * * * cd /caminho/DailyProject && /usr/bin/java -jar target/daily-project-1.0.0.jar --once >> daily-project.log 2>&1
```

## Estrutura

```
src/main/java/com/dailyproject/
  App.java              # entrypoint
  Config.java           # .env + env vars
  CodeGenerator.java    # gera ≥100 linhas
  GitService.java       # develop: add/commit/push
  GitHubService.java    # PR, comment, discussion, approve+merge
  PersonaComments.java  # textos em primeira pessoa
  DailyWorkflow.java    # orquestra o ciclo
  DailyScheduler.java   # agenda 3x/dia
  generated/            # snippets commitados diariamente
```
