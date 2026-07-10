# DailyProject

Projeto Java que, **três vezes por dia**, gera pelo menos **100 linhas** de código, commita na `develop`, **abre um PR** para `main`, comenta (com pergunta), abre uma discussão e, **depois**, mergeia o PR.

## Por que o PR “sumia”

Antes o mesmo job abria e mergeava em ~3 segundos. O PR existia (ex.: [#2](https://github.com/livyson/DailyProject/pull/2), [#4](https://github.com/livyson/DailyProject/pull/4)), mas ia direto para **Merged**. Agora o fluxo é separado:

| Workflow | Horário (BRT) | O que faz |
|----------|---------------|-----------|
| `daily.yml` | 09:00, 14:00, 19:00 | gera código → commit `develop` → **abre PR** → comenta → discussão |
| `daily-merge.yml` | 09:30, 14:30, 19:30 | aprova (se possível) → **merge** → realinha `develop` |

Assim o PR fica ~30 minutos na aba **Open**.

## Checklist no repositório

1. **Actions** habilitadas
2. *Settings → Actions → General → Workflow permissions*:
   - **Read and write permissions**
   - **Allow GitHub Actions to create and approve pull requests**
3. **Discussions** habilitadas

## Disparo manual

- **Actions → Daily open PR** → Run workflow (abre e deixa o PR aberto)
- **Actions → Daily merge open PR** → Run workflow (mergeia o PR aberto)

## Local

```bash
mvn -q package
java -jar target/daily-project-1.0.0.jar --open    # só abre PR
java -jar target/daily-project-1.0.0.jar --merge   # só mergeia
java -jar target/daily-project-1.0.0.jar --once    # open + merge
```
