package com.dailyproject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Textos em primeira pessoa (como se fossem você) para comentários e discussões.
 */
public final class PersonaComments {

    private static final DateTimeFormatter HUMAN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final List<String> QUESTIONS = List.of(
            "Alguém revisou essas mudanças hoje? Tem algo que eu deveria ajustar antes do merge?",
            "Essa abordagem do gerador diário faz sentido ou vocês preferem outro formato de snippet?",
            "Vocês acham que 100 linhas por commit é um bom ritmo, ou aumentamos o mínimo?",
            "Tem algum risco em squash-mergear develop → main três vezes por dia?",
            "Querem que eu adicione testes automáticos para cada snippet gerado?",
            "Prefiro manter o PR aberto o dia todo — alguém vê problema nisso?",
            "Essa organização em `generated/` está clara ou vocês querem outro pacote?",
            "Vale documentar no README cada slot do schedule ou já está óbvio?"
    );

    private static final List<String> REMARKS = List.of(
            "Passei o olho no diff e parece consistente com o que a gente combinou no fluxo diário.",
            "Deixei o commit só na develop de propósito — main só entra via PR.",
            "Gerei o mínimo de 100 linhas; se precisar de mais volume, é só subir o MIN_LINES.",
            "Comentário rápido: o SHA novo já está no head do PR.",
            "Acho que o squash deixa o histórico da main bem limpo neste caso.",
            "Se a discussão for aberta, entro depois com mais contexto."
    );

    private PersonaComments() {
    }

    public static String pullRequestBody(String className, int lines, String sha) {
        return """
                ## Resumo
                Atualização diária automática do DailyProject.

                - Snippet: `%s`
                - Linhas geradas: **%d**
                - Commit (develop): `%s`
                - Data: %s

                ## Checklist
                - [x] Código commitado em `develop`
                - [x] PR para `main`
                - [ ] Comentário / pergunta do autor
                - [ ] Discussão no repositório
                - [ ] Aprovação + merge

                ---
                _Aberto automaticamente pelo DailyProject._
                """.formatted(className, lines, sha, LocalDateTime.now().format(HUMAN));
    }

    public static String prComment(String className, int lines) {
        String question = pick(QUESTIONS);
        String remark = pick(REMARKS);
        return """
                Ei, deixei mais um lote hoje (%s, %d linhas).

                %s

                **Pergunta:** %s

                — %s
                """.formatted(
                className,
                lines,
                remark,
                question,
                "Livyson"
        ).trim();
    }

    public static String discussionTitle(LocalDate day) {
        return "Fluxo diário " + day.format(DateTimeFormatter.ISO_DATE) + " — develop → main";
    }

    public static String discussionBody(String prUrl, String className, int lines) {
        String question = pick(QUESTIONS);
        return """
                Abri essa discussão para acompanhar o ciclo de hoje.

                - PR: %s
                - Snippet: `%s` (%d linhas)
                - Horário: %s

                Estou usando o fluxo:
                1. commit na `develop`
                2. PR para `main`
                3. comentário + pergunta
                4. discussão
                5. approve + merge

                **Pergunta para o time:** %s

                Comentários e sugestões são bem-vindos.
                """.formatted(prUrl, className, lines, LocalDateTime.now().format(HUMAN), question);
    }

    public static String mergeTitle(String className) {
        return "daily: merge develop → main (" + className + ")";
    }

    private static String pick(List<String> options) {
        return options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }
}
