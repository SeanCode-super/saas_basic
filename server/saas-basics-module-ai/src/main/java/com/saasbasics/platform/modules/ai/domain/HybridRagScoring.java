package com.saasbasics.platform.modules.ai.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure domain Hybrid RAG retrieval and chunking engine:
 * - Paragraph & heading aware sliding-window chunker
 * - Bilingual CJK + alphanumeric tokenizer
 * - BM25 sparse retrieval + signed random-projection dense vector cosine similarity
 * - Reciprocal Rank Fusion (RRF) ranking with citation traceability
 * <p>
 * Remains free of Spring/MyBatis/Infrastructure dependencies per ArchUnit boundary rules.
 */
public final class HybridRagScoring {

    public static final int DEFAULT_VECTOR_DIMS = 128;
    private static final Pattern EN_TOKEN = Pattern.compile("[a-z0-9_\\-]{2,}");

    public record TextChunk(
            int chunkIndex,
            int pageNo,
            String sectionTitle,
            String content,
            String keywords,
            double[] denseVector
    ) {}

    public record ChunkCandidate(
            Long chunkId,
            Long docId,
            String docName,
            int chunkIndex,
            int pageNo,
            String sectionTitle,
            String content,
            double[] denseVector
    ) {}

    public record RankedChunk(
            ChunkCandidate chunk,
            double bm25Score,
            double cosineScore,
            double rrfScore
    ) {}

    private HybridRagScoring() {
    }

    public static List<TextChunk> splitIntoChunks(String rawText, int chunkSize, int overlap) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        int safeSize = Math.min(Math.max(chunkSize, 120), 2000);
        int safeOverlap = Math.min(Math.max(overlap, 0), safeSize / 2);

        String normalized = rawText.replace("\r\n", "\n").replace("\r", "\n").trim();
        String[] lines = normalized.split("\n");

        List<TextChunk> chunks = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        String currentHeading = "正文";
        int chunkIdx = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("#")) {
                if (buf.length() > 0) {
                    chunks.add(buildChunk(chunkIdx++, currentHeading, buf.toString()));
                    buf.setLength(0);
                }
                currentHeading = trimmed.replaceFirst("^#+\\s*", "");
                continue;
            }
            if (buf.length() + trimmed.length() + 1 > safeSize && buf.length() > 0) {
                String block = buf.toString();
                chunks.add(buildChunk(chunkIdx++, currentHeading, block));
                String tail = safeOverlap > 0 && block.length() > safeOverlap
                        ? block.substring(block.length() - safeOverlap)
                        : "";
                buf.setLength(0);
                if (!tail.isEmpty()) {
                    buf.append(tail).append("\n");
                }
            }
            if (buf.length() > 0) {
                buf.append("\n");
            }
            buf.append(trimmed);
        }
        if (buf.length() > 0) {
            chunks.add(buildChunk(chunkIdx, currentHeading, buf.toString()));
        }
        return chunks;
    }

    private static TextChunk buildChunk(int idx, String sectionTitle, String content) {
        int pageNo = (idx / 2) + 1;
        List<String> tokens = tokenize(content);
        Map<String, Integer> freq = new LinkedHashMap<>();
        for (String t : tokens) {
            if (t.length() >= 2) {
                freq.merge(t, 1, Integer::sum);
            }
        }
        String keywords = freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(6)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.joining(","));
        return new TextChunk(idx, pageNo, sectionTitle, content, keywords, computeDenseVector(content, DEFAULT_VECTOR_DIMS));
    }

    public static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();

        Matcher m = EN_TOKEN.matcher(lower);
        while (m.find()) {
            tokens.add(m.group());
        }

        List<Character> cjkRun = new ArrayList<>();
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (isCjk(ch)) {
                cjkRun.add(ch);
            } else {
                flushCjkRun(cjkRun, tokens);
            }
        }
        flushCjkRun(cjkRun, tokens);
        return tokens;
    }

    private static void flushCjkRun(List<Character> run, List<String> out) {
        if (run.isEmpty()) {
            return;
        }
        for (int i = 0; i < run.size(); i++) {
            out.add(String.valueOf(run.get(i)));
            if (i + 1 < run.size()) {
                out.add(run.get(i) + "" + run.get(i + 1));
            }
        }
        run.clear();
    }

    public static double[] computeDenseVector(String text, int dims) {
        double[] vec = new double[dims];
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return vec;
        }
        for (String tok : tokens) {
            long h1 = fnv1a64(tok);
            int bucket = (int) Math.floorMod(h1, dims);
            double sign = ((h1 >>> 63) == 0) ? 1.0 : -1.0;
            double weight = tok.length() >= 2 ? 1.35 : 0.65;
            vec[bucket] += sign * weight;
        }
        double norm = 0.0;
        for (double v : vec) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 1e-9) {
            for (int i = 0; i < dims; i++) {
                vec[i] /= norm;
            }
        }
        return vec;
    }

    public static double cosineSimilarity(double[] a, double[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return Math.max(0.0, Math.min(1.0, (dot + 1.0) / 2.0));
    }

    public static List<RankedChunk> rankHybrid(String query, List<ChunkCandidate> candidates, int topK) {
        if (query == null || query.isBlank() || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<String> qTokens = tokenize(query);
        double[] qVec = computeDenseVector(query, DEFAULT_VECTOR_DIMS);

        int nDocs = candidates.size();
        Map<Long, List<String>> docTokensMap = new HashMap<>(nDocs);
        Map<String, Integer> docFreq = new HashMap<>();
        double totalLen = 0.0;

        for (ChunkCandidate c : candidates) {
            List<String> toks = tokenize(c.sectionTitle() + " " + c.content());
            docTokensMap.put(c.chunkId(), toks);
            totalLen += toks.size();
            Set<String> unique = new LinkedHashSet<>(toks);
            for (String t : unique) {
                docFreq.merge(t, 1, Integer::sum);
            }
        }
        double avgDocLen = Math.max(1.0, totalLen / nDocs);
        double k1 = 1.5;
        double b = 0.75;

        Map<Long, Double> bm25Scores = new HashMap<>(nDocs);
        Map<Long, Double> cosScores = new HashMap<>(nDocs);

        for (ChunkCandidate c : candidates) {
            List<String> toks = docTokensMap.getOrDefault(c.chunkId(), Collections.emptyList());
            Map<String, Integer> tfMap = new HashMap<>();
            for (String t : toks) {
                tfMap.merge(t, 1, Integer::sum);
            }
            double docLen = Math.max(1.0, toks.size());
            double bm25 = 0.0;
            for (String qt : qTokens) {
                int tf = tfMap.getOrDefault(qt, 0);
                if (tf == 0) continue;
                int df = docFreq.getOrDefault(qt, 0);
                double idf = Math.log(1.0 + (nDocs - df + 0.5) / (df + 0.5));
                double denom = tf + k1 * (1.0 - b + b * (docLen / avgDocLen));
                bm25 += idf * (tf * (k1 + 1.0)) / denom;
            }
            bm25Scores.put(c.chunkId(), bm25);

            double[] docVec = (c.denseVector() != null && c.denseVector().length == DEFAULT_VECTOR_DIMS)
                    ? c.denseVector()
                    : computeDenseVector(c.content(), DEFAULT_VECTOR_DIMS);
            cosScores.put(c.chunkId(), cosineSimilarity(qVec, docVec));
        }

        // Compute ranks for BM25 and Dense Cosine for Reciprocal Rank Fusion
        List<ChunkCandidate> byBm25 = new ArrayList<>(candidates);
        byBm25.sort(Comparator.comparingDouble((ChunkCandidate c) -> bm25Scores.getOrDefault(c.chunkId(), 0.0)).reversed());
        List<ChunkCandidate> byCos = new ArrayList<>(candidates);
        byCos.sort(Comparator.comparingDouble((ChunkCandidate c) -> cosScores.getOrDefault(c.chunkId(), 0.0)).reversed());

        Map<Long, Integer> bm25Rank = new HashMap<>();
        Map<Long, Integer> cosRank = new HashMap<>();
        for (int i = 0; i < byBm25.size(); i++) bm25Rank.put(byBm25.get(i).chunkId(), i + 1);
        for (int i = 0; i < byCos.size(); i++) cosRank.put(byCos.get(i).chunkId(), i + 1);

        List<RankedChunk> ranked = new ArrayList<>(candidates.size());
        for (ChunkCandidate c : candidates) {
            double rBm = bm25Rank.getOrDefault(c.chunkId(), nDocs);
            double rCs = cosRank.getOrDefault(c.chunkId(), nDocs);
            double bm = bm25Scores.getOrDefault(c.chunkId(), 0.0);
            double cs = cosScores.getOrDefault(c.chunkId(), 0.0);
            // Hybrid fusion combines RRF rank stability with normalized lexical + semantic confidence
            double rrf = (1.0 / (20.0 + rBm)) + (1.0 / (20.0 + rCs)) + (bm > 0 ? 0.15 : 0.0) + (0.25 * cs);
            ranked.add(new RankedChunk(c, bm, cs, rrf));
        }
        ranked.sort(Comparator.comparingDouble(RankedChunk::rrfScore).reversed());
        int limit = Math.min(Math.max(1, topK), ranked.size());
        return ranked.subList(0, limit);
    }

    private static boolean isCjk(char ch) {
        return (ch >= 0x4E00 && ch <= 0x9FFF) || (ch >= 0x3400 && ch <= 0x4DBF);
    }

    private static long fnv1a64(String input) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < input.length(); i++) {
            hash ^= input.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
