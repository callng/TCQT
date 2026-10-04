package bsh.preprocess;

import java.util.ArrayList;
import java.util.List;

/**
 * Desugars Kotlin-like string templates in string literals to Java concatenation.
 *
 * <p>Example:
 * <pre>
 * "hello $name, age=${age + 1}"
 * </pre>
 * becomes:
 * <pre>
 * "hello " + String.valueOf(name) + ", age=" + String.valueOf(age + 1)
 * </pre>
 */
public final class KtStringTemplate {
    private KtStringTemplate() {
    }

    public static String rewrite(String source) {
        if (source == null || source.indexOf('$') < 0 || source.indexOf('"') < 0) {
            return source;
        }

        final int len = source.length();
        StringBuilder out = new StringBuilder(len + 32);
        int i = 0;
        while (i < len) {
            char ch = source.charAt(i);

            if (ch == '"') {
                if (isTripleQuote(source, i)) {
                    int end = findTripleQuoteEnd(source, i + 3);
                    if (end < 0) {
                        out.append(source, i, len);
                        break;
                    }
                    out.append(rewriteStringLiteral(source.substring(i + 3, end), true));
                    i = end + 3;
                    continue;
                }

                int end = findNormalStringEnd(source, i + 1);
                if (end < 0) {
                    out.append(source, i, len);
                    break;
                }
                out.append(rewriteStringLiteral(source.substring(i + 1, end), false));
                i = end + 1;
                continue;
            }

            if (ch == '\'') {
                int end = findCharLiteralEnd(source, i + 1);
                if (end < 0) {
                    out.append(source, i, len);
                    break;
                }
                out.append(source, i, end + 1);
                i = end + 1;
                continue;
            }

            if (ch == '/' && i + 1 < len) {
                char next = source.charAt(i + 1);
                if (next == '/') {
                    int end = i + 2;
                    while (end < len) {
                        char c = source.charAt(end);
                        if (c == '\n' || c == '\r') {
                            break;
                        }
                        end++;
                    }
                    out.append(source, i, end);
                    i = end;
                    continue;
                }
                if (next == '*') {
                    int end = findBlockCommentEnd(source, i + 2);
                    if (end < 0) {
                        out.append(source, i, len);
                        break;
                    }
                    out.append(source, i, end + 2);
                    i = end + 2;
                    continue;
                }
            }

            out.append(ch);
            i++;
        }
        return out.toString();
    }

    private static String rewriteStringLiteral(String content, boolean fromLongString) {
        if (content.indexOf('$') < 0) {
            return quoteString(content, fromLongString);
        }

        final int len = content.length();
        List<String> pieces = new ArrayList<>();
        boolean hasExpression = false;
        int segmentStart = 0;
        int i = 0;
        while (i < len) {
            char ch = content.charAt(i);
            if (ch != '$') {
                i++;
                continue;
            }
            if (!fromLongString && isEscapedDollar(content, i)) {
                i++;
                continue;
            }
            if (i + 1 >= len) {
                i++;
                continue;
            }

            char next = content.charAt(i + 1);
            if (next == '{') {
                int exprEnd = findTemplateExprEnd(content, i + 2);
                if (exprEnd < 0) {
                    i++;
                    continue;
                }

                String text = content.substring(segmentStart, i);
                if (!text.isEmpty()) {
                    pieces.add(quoteString(text, fromLongString));
                }
                String expr = content.substring(i + 2, exprEnd).trim();
                pieces.add("String.valueOf(" + expr + ")");
                hasExpression = true;

                i = exprEnd + 1;
                segmentStart = i;
                continue;
            }

            if (isIdentifierStart(next)) {
                int idEnd = i + 2;
                while (idEnd < len && isIdentifierPart(content.charAt(idEnd))) {
                    idEnd++;
                }

                String text = content.substring(segmentStart, i);
                if (!text.isEmpty()) {
                    pieces.add(quoteString(text, fromLongString));
                }
                pieces.add("String.valueOf(" + content.substring(i + 1, idEnd) + ")");
                hasExpression = true;

                i = idEnd;
                segmentStart = i;
                continue;
            }

            i++;
        }

        if (!hasExpression) {
            return quoteString(content, fromLongString);
        }

        String tail = content.substring(segmentStart);
        if (!tail.isEmpty()) {
            pieces.add(quoteString(tail, fromLongString));
        }

        if (pieces.isEmpty()) {
            return "\"\"";
        }
        return "(" + joinWithPlus(pieces) + ")";
    }

    private static String joinWithPlus(List<String> pieces) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pieces.size(); i++) {
            if (i > 0) {
                sb.append(" + ");
            }
            sb.append(pieces.get(i));
        }
        return sb.toString();
    }

    private static String quoteString(String text, boolean fromLongString) {
        if (!fromLongString) {
            return "\"" + text + "\"";
        }
        return "\"" + escapeForDoubleQuoted(text) + "\"";
    }

    private static String escapeForDoubleQuoted(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    sb.append(ch);
                    break;
            }
        }
        return sb.toString();
    }

    private static boolean isEscapedDollar(String text, int dollarPos) {
        int slashCount = 0;
        for (int i = dollarPos - 1; i >= 0 && text.charAt(i) == '\\'; i--) {
            slashCount++;
        }
        return (slashCount & 1) == 1;
    }

    private static boolean isIdentifierStart(char ch) {
        return ch != '$' && Character.isJavaIdentifierStart(ch);
    }

    private static boolean isIdentifierPart(char ch) {
        return ch != '$' && Character.isJavaIdentifierPart(ch);
    }

    private static int findTemplateExprEnd(String text, int start) {
        final int len = text.length();
        int depth = 1;
        int i = start;
        while (i < len) {
            char ch = text.charAt(i);

            if (ch == '"') {
                int end = findNormalStringEnd(text, i + 1);
                if (end < 0) {
                    return -1;
                }
                i = end + 1;
                continue;
            }

            if (ch == '\'') {
                int end = findCharLiteralEnd(text, i + 1);
                if (end < 0) {
                    return -1;
                }
                i = end + 1;
                continue;
            }

            if (ch == '/' && i + 1 < len) {
                char next = text.charAt(i + 1);
                if (next == '/') {
                    i += 2;
                    while (i < len) {
                        char c = text.charAt(i);
                        if (c == '\n' || c == '\r') {
                            break;
                        }
                        i++;
                    }
                    continue;
                }
                if (next == '*') {
                    int end = findBlockCommentEnd(text, i + 2);
                    if (end < 0) {
                        return -1;
                    }
                    i = end + 2;
                    continue;
                }
            }

            if (ch == '{') {
                depth++;
                i++;
                continue;
            }
            if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
                i++;
                continue;
            }

            i++;
        }
        return -1;
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

    private static int findTripleQuoteEnd(String text, int start) {
        for (int i = start; i + 2 < text.length(); i++) {
            if (isTripleQuote(text, i)) {
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
}
