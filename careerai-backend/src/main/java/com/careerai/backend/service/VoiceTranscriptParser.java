package com.careerai.backend.service;

import com.careerai.backend.dto.VoiceTranscriptRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns a voice call transcript into interview question/answer pairs.
 * - keeps only assistant/user entries with text (other roles are ignored), strips control
 *   characters and caps sizes;
 * - merges consecutive entries from the same speaker (speech-to-text splits utterances);
 * - an assistant turn counts as a question only if it contains a question; greetings,
 *   acknowledgements and closing lines are skipped;
 * - the question is paired with the user turn that follows; very short replies are not answers.
 */
@Component
public class VoiceTranscriptParser {

    public static final int MAX_TOTAL_CHARS = 120_000;
    static final int MAX_PAIRS = 15;
    private static final int MIN_ANSWER_WORDS = 3;

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern IMPLIED_QUESTION = Pattern.compile(
        "(?i)^(tell me|describe|explain|walk me through|can you|could you|how would you|what would you|share an example|give me an example)\\b");

    public record Turn(String role, String text, String timestamp) {
    }

    public record QaPair(String question, String answer) {
    }

    /** Valid, sanitised, merged turns in order. */
    public List<Turn> normalize(List<VoiceTranscriptRequest.Entry> entries) {
        List<Turn> turns = new ArrayList<>();
        int total = 0;

        for (VoiceTranscriptRequest.Entry entry : entries) {
            if (entry == null || entry.role() == null || entry.text() == null) continue;
            String role = entry.role().strip().toLowerCase(Locale.ROOT);
            if (role.equals("bot")) role = "assistant";
            if (!role.equals("assistant") && !role.equals("user")) continue;

            String text = clean(entry.text());
            if (text.isEmpty()) continue;
            if (total + text.length() > MAX_TOTAL_CHARS) break;
            total += text.length();

            Turn last = turns.isEmpty() ? null : turns.get(turns.size() - 1);
            if (last != null && last.role().equals(role)) {
                // Merge split utterances; skip exact repeats of the same segment.
                if (!last.text().endsWith(text)) {
                    turns.set(turns.size() - 1, new Turn(role, last.text() + " " + text, last.timestamp()));
                }
            } else {
                turns.add(new Turn(role, text, cleanTimestamp(entry.timestamp())));
            }
        }
        return turns;
    }

    /** Question/answer pairs: an assistant question followed by a meaningful user answer. */
    public List<QaPair> pairs(List<Turn> turns) {
        List<QaPair> pairs = new ArrayList<>();
        for (int i = 0; i < turns.size() - 1 && pairs.size() < MAX_PAIRS; i++) {
            Turn turn = turns.get(i);
            Turn next = turns.get(i + 1);
            if (!turn.role().equals("assistant") || !next.role().equals("user")) continue;

            String question = extractQuestion(turn.text());
            if (question == null) continue;
            if (wordCount(next.text()) < MIN_ANSWER_WORDS) continue;

            pairs.add(new QaPair(limit(question, 800), limit(next.text(), 4000)));
        }
        return pairs;
    }

    /**
     * The question part of an interviewer turn ("Great answer. Next, how does X work?" -> the question),
     * or null when the turn is not a question (greeting, acknowledgement, closing).
     */
    String extractQuestion(String text) {
        String[] sentences = SENTENCE_SPLIT.split(text);
        int firstQuestion = -1;
        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i].strip();
            if (sentence.endsWith("?") || IMPLIED_QUESTION.matcher(sentence).find()) {
                firstQuestion = i;
                break;
            }
        }
        if (firstQuestion < 0) return null;

        StringBuilder question = new StringBuilder();
        for (int i = firstQuestion; i < sentences.length; i++) {
            if (!question.isEmpty()) question.append(' ');
            question.append(sentences[i].strip());
        }
        return question.toString();
    }

    private static String clean(String text) {
        return text.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
            .replaceAll("\\s+", " ")
            .strip();
    }

    private static String cleanTimestamp(String timestamp) {
        if (timestamp == null) return null;
        String value = timestamp.strip();
        return value.matches("[0-9TZ:.+\\- ]{4,40}") ? value : null;
    }

    private static int wordCount(String text) {
        return text.isBlank() ? 0 : text.strip().split("\\s+").length;
    }

    private static String limit(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
