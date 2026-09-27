package bsh.preprocess;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Removes Java/Kotlin annotation usages from script text while preserving
 * BeanShell @-operator aliases (e.g. @gt, @and, @pow_assign).
 */
public final class AnnotationIgnorePreprocess {
    private static final Set<String> BSH_AT_OPERATORS = new HashSet<String>(Arrays.asList(
            "gt", "lt", "lteq", "gteq", "or", "and",
            "bitwise_and", "bitwise_or", "bitwise_xor",
            "mod", "pow", "left_shift", "right_shift", "right_unsigned_shift",
            "and_assign", "or_assign", "xor_assign", "mod_assign", "pow_assign",
            "left_shift_assign", "right_shift_assign", "right_unsigned_shift_assign"
    ));

    private AnnotationIgnorePreprocess() {
    }

    public static String rewrite(String source) {
        if (source == null || source.indexOf('@') < 0) {
            return source;
        }

        final int len = source.length();
        StringBuilder out = new StringBuilder(len);
        int i = 0;

        while (i < len) {
            int skip = skipLiteralOrComment(source, i);
            if (skip > i) {
                out.append(source, i, skip);
                i = skip;
                continue;
            }

            if (source.charAt(i) != '@') {
                out.append(source.charAt(i));
                i++;
                continue;
            }

            int operatorEnd = parseBshOperatorAlias(source, i);
            if (operatorEnd > i) {
                out.append(source, i, operatorEnd);
                i = operatorEnd;
                continue;
            }

            int annotationEnd = parseAnnotationEnd(source, i);
            if (annotationEnd > i) {
                // Keep line separators stable while removing annotation tokens.
                for (int j = i; j < annotationEnd; j++) {
                    char c = source.charAt(j);
                    if (c == '\n' || c == '\r') {
                        out.append(c);
                    }
                }
                i = annotationEnd;
                while (i < len && (source.charAt(i) == ' ' || source.charAt(i) == '\t')) {
                    i++;
                }
                continue;
            }

            out.append('@');
            i++;
        }

        return out.toString();
    }

    private static int parseBshOperatorAlias(String source, int atPos) {
        int len = source.length();
        int start = atPos + 1;
        if (start >= len || !isAliasPart(source.charAt(start))) {
            return -1;
        }
        int end = start;
        while (end < len && isAliasPart(source.charAt(end))) {
            end++;
        }
        String alias = source.substring(start, end);
        if (!BSH_AT_OPERATORS.contains(alias)) {
            return -1;
        }
        if (end < len && Character.isJavaIdentifierPart(source.charAt(end))) {
            return -1;
        }
        return end;
    }

    private static int parseAnnotationEnd(String source, int atPos) {
        int len = source.length();
        int i = atPos + 1;
        if (i >= len || !Character.isJavaIdentifierStart(source.charAt(i))) {
            return -1;
        }

        while (i < len && Character.isJavaIdentifierPart(source.charAt(i))) {
            i++;
        }
        if (source.substring(atPos + 1, i).equals("interface")) {
            return -1;
        }

        while (i < len && source.charAt(i) == '.') {
            int segStart = ++i;
            if (segStart >= len || !Character.isJavaIdentifierStart(source.charAt(segStart))) {
                return -1;
            }
            while (i < len && Character.isJavaIdentifierPart(source.charAt(i))) {
                i++;
            }
        }

        while (i < len && isInlineWhitespace(source.charAt(i))) {
            i++;
        }

        if (i < len && source.charAt(i) == '(') {
            int end = findMatchingParen(source, i);
            if (end < 0) {
                return -1;
            }
            i = end + 1;
        }
        return i;
    }

    private static int findMatchingParen(String source, int openPos) {
        final int len = source.length();
        int depth = 0;
        for (int i = openPos; i < len; i++) {
            int skip = skipLiteralOrComment(source, i);
            if (skip > i) {
                i = skip - 1;
                continue;
            }

            char ch = source.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int skipLiteralOrComment(String source, int i) {
        final int len = source.length();
        if (i >= len) {
            return i;
        }
        char ch = source.charAt(i);

        if (ch == '"') {
            if (isTripleQuote(source, i)) {
                int end = findTripleQuoteEnd(source, i + 3);
                return end < 0 ? len : end + 3;
            }
            int end = findNormalStringEnd(source, i + 1);
            return end < 0 ? len : end + 1;
        }

        if (ch == '\'') {
            int end = findCharLiteralEnd(source, i + 1);
            return end < 0 ? len : end + 1;
        }

        if (ch == '/' && i + 1 < len) {
            char next = source.charAt(i + 1);
            if (next == '/') {
                int j = i + 2;
                while (j < len && source.charAt(j) != '\n' && source.charAt(j) != '\r') {
                    j++;
                }
                return j;
            }
            if (next == '*') {
                int end = findBlockCommentEnd(source, i + 2);
                return end < 0 ? len : end + 2;
            }
        }

        return i;
    }

    private static int findNormalStringEnd(String text, int start) {
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\\') {
                i++;
                continue;
            }
            if (ch == '"') {
                return i;
            }
        }
        return -1;
    }

    private static int findCharLiteralEnd(String text, int start) {
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\\') {
                i++;
                continue;
            }
            if (ch == '\'') {
                return i;
            }
            if (ch == '\n' || ch == '\r') {
                return -1;
            }
        }
        return -1;
    }

    private static int findBlockCommentEnd(String text, int start) {
        for (int i = start; i + 1 < text.length(); i++) {
            if (text.charAt(i) == '*' && text.charAt(i + 1) == '/') {
                return i;
            }
        }
        return -1;
    }

    private static boolean isTripleQuote(String text, int index) {
        return index + 2 < text.length()
                && text.charAt(index) == '"'
                && text.charAt(index + 1) == '"'
                && text.charAt(index + 2) == '"';
    }

    private static int findTripleQuoteEnd(String text, int start) {
        for (int i = start; i + 2 < text.length(); i++) {
            if (isTripleQuote(text, i)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isAliasPart(char ch) {
        return ch == '_' || Character.isLetterOrDigit(ch);
    }

    private static boolean isInlineWhitespace(char ch) {
        return ch == ' ' || ch == '\t' || ch == '\f';
    }
}
