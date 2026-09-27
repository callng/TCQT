package bsh.preprocess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Desugars method default parameters to overload bridges.
 *
 * <p>Example:
 * <pre>
 * void hi(String name, int age = 18) { ... }
 * </pre>
 * becomes:
 * <pre>
 * void hi(String name, int age) { ... }
 * void hi(String name) { hi(name, 18); }
 * </pre>
 */
public final class DefaultArgsDesugar {
    private static final Set<String> CONTROL_KEYWORDS = new HashSet<String>(Arrays.asList(
            "if", "for", "while", "switch", "catch", "synchronized", "new", "return",
            "throw", "assert", "case", "do", "try"));

    private static final Set<String> METHOD_MODIFIERS = new HashSet<String>(Arrays.asList(
            "public", "protected", "private", "static", "final", "synchronized",
            "native", "abstract", "strictfp", "default"));

    private DefaultArgsDesugar() {
    }

    public static String rewrite(String source) {
        if (source == null || source.indexOf('=') < 0 || source.indexOf('(') < 0) {
            return source;
        }

        Set<String> declaredSignatures = collectDeclaredMethodSignatures(source);
        StringBuilder out = new StringBuilder(source.length() + 128);
        int len = source.length();
        int i = 0;
        int lastEmit = 0;
        while (i < len) {
            int skip = skipLiteralOrComment(source, i);
            if (skip > i) {
                i = skip;
                continue;
            }

            if (source.charAt(i) != '(') {
                i++;
                continue;
            }

            MethodRewrite rewrite = tryRewriteMethod(source, i, declaredSignatures);
            if (rewrite != null) {
                out.append(source, lastEmit, rewrite.declStart);
                out.append(rewrite.rewritten);
                lastEmit = rewrite.declEndExclusive;
                i = lastEmit;
                continue;
            }

            i++;
        }

        out.append(source, lastEmit, len);
        return out.toString();
    }

    private static Set<String> collectDeclaredMethodSignatures(String source) {
        Set<String> signatures = new HashSet<String>();
        int len = source.length();
        int i = 0;
        while (i < len) {
            int skip = skipLiteralOrComment(source, i);
            if (skip > i) {
                i = skip;
                continue;
            }

            if (source.charAt(i) != '(') {
                i++;
                continue;
            }

            MethodShape shape = tryParseMethodShape(source, i);
            if (shape != null) {
                signatures.add(methodSignatureKey(
                        shape.methodName,
                        shape.parsed.paramDeclNoDefault,
                        shape.parsed.paramDeclNoDefault.size()
                ));
                i = shape.declEndExclusive;
                continue;
            }
            i++;
        }
        return signatures;
    }

    private static MethodShape tryParseMethodShape(String source, int openParen) {
        int nameEnd = skipWsBackward(source, openParen - 1) + 1;
        int nameStart = nameEnd;
        while (nameStart > 0 && Character.isJavaIdentifierPart(source.charAt(nameStart - 1))) {
            nameStart--;
        }
        if (nameStart >= nameEnd) {
            return null;
        }

        String methodName = source.substring(nameStart, nameEnd);
        if (!Character.isJavaIdentifierStart(methodName.charAt(0))
                || CONTROL_KEYWORDS.contains(methodName)) {
            return null;
        }

        int beforeName = skipWsBackward(source, nameStart - 1);
        if (beforeName >= 0) {
            char ch = source.charAt(beforeName);
            if (ch == '.' || ch == ':' || ch == '$') {
                return null;
            }
        }

        int closeParen = findMatching(source, openParen, '(', ')');
        if (closeParen < 0) {
            return null;
        }

        int next = skipWsAndCommentsForward(source, closeParen + 1);
        if (next < source.length() && source.startsWith("->", next)) {
            return null;
        }

        int bodyStart = findMethodBodyStart(source, closeParen + 1);
        if (bodyStart < 0) {
            return null;
        }

        int bodyEnd = findMatching(source, bodyStart, '{', '}');
        if (bodyEnd < 0) {
            return null;
        }

        String paramsText = source.substring(openParen + 1, closeParen);
        ParsedParams parsed = parseParams(paramsText);
        if (!parsed.valid) {
            return null;
        }

        int declStart = findDeclarationStart(source, nameStart);
        return new MethodShape(declStart, bodyEnd + 1, methodName, parsed);
    }

    private static MethodRewrite tryRewriteMethod(
            String source, int openParen, Set<String> declaredSignatures) {
        MethodShape shape = tryParseMethodShape(source, openParen);
        if (shape == null) {
            return null;
        }

        ParsedParams parsed = shape.parsed;
        if (!parsed.valid || !parsed.hasDefault || !parsed.trailingDefaults) {
            return null;
        }

        int declStart = shape.declStart;
        int closeParen = findMatching(source, openParen, '(', ')');
        String prefix = source.substring(declStart, openParen);
        int bodyStart = findMethodBodyStart(source, closeParen + 1);
        if (bodyStart < 0) {
            return null;
        }
        String betweenParenAndBody = source.substring(closeParen + 1, bodyStart);
        int bodyEnd = findMatching(source, bodyStart, '{', '}');
        String body = source.substring(bodyStart, bodyEnd + 1);

        String rewritten = buildRewrittenMethod(
                prefix,
                shape.methodName,
                betweenParenAndBody,
                body,
                parsed,
                declaredSignatures
        );
        return new MethodRewrite(declStart, bodyEnd + 1, rewritten);
    }

    private static String buildRewrittenMethod(
            String prefix,
            String methodName,
            String betweenParenAndBody,
            String body,
            ParsedParams parsed,
            Set<String> declaredSignatures
    ) {
        StringBuilder sb = new StringBuilder(prefix.length() + body.length() + 128);
        sb.append(prefix)
                .append('(')
                .append(join(parsed.paramDeclNoDefault, 0, parsed.paramDeclNoDefault.size()))
                .append(')')
                .append(betweenParenAndBody)
                .append(body);

        boolean shouldReturn = shouldBridgeReturn(prefix, methodName);
        int total = parsed.paramDeclNoDefault.size();
        for (int keepCount = total - 1; keepCount >= parsed.requiredCount; keepCount--) {
            String bridgeKey = methodSignatureKey(methodName, parsed.paramDeclNoDefault, keepCount);
            if (declaredSignatures.contains(bridgeKey)) {
                continue;
            }
            sb.append('\n')
                    .append(prefix)
                    .append('(')
                    .append(join(parsed.paramDeclNoDefault, 0, keepCount))
                    .append(')')
                    .append(betweenParenAndBody)
                    .append('{');
            if (shouldReturn) {
                sb.append("return ");
            }
            sb.append(methodName)
                    .append('(')
                    .append(buildBridgeArgs(parsed, keepCount))
                    .append(");}");
            declaredSignatures.add(bridgeKey);
        }
        return sb.toString();
    }

    private static String methodSignatureKey(String methodName, List<String> paramDeclNoDefault, int count) {
        StringBuilder sb = new StringBuilder(methodName.length() + 32);
        sb.append(methodName).append('(');
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(signatureTypeKey(paramDeclNoDefault.get(i)));
        }
        sb.append(')');
        return sb.toString();
    }

    private static String signatureTypeKey(String decl) {
        String name = extractParamName(decl);
        if (name == null) {
            return normalizeTypeKey(decl);
        }

        int nameIndex = findTrailingNameIndex(decl, name);
        if (nameIndex < 0) {
            return normalizeTypeKey(decl);
        }

        String withNameRemoved = decl.substring(0, nameIndex)
                + decl.substring(nameIndex + name.length());
        return normalizeTypeKey(withNameRemoved);
    }

    private static int findTrailingNameIndex(String decl, String name) {
        int i = skipWsBackward(decl, decl.length() - 1);
        while (i >= 1 && decl.charAt(i) == ']' && decl.charAt(i - 1) == '[') {
            i = skipWsBackward(decl, i - 2);
        }
        if (i < 0) {
            return -1;
        }

        int end = i + 1;
        while (i >= 0 && Character.isJavaIdentifierPart(decl.charAt(i))) {
            i--;
        }
        int start = i + 1;
        if (start >= end) {
            return -1;
        }
        return name.equals(decl.substring(start, end)) ? start : -1;
    }

    private static String normalizeTypeKey(String typeLikeDecl) {
        StringBuilder out = new StringBuilder(typeLikeDecl.length());
        int len = typeLikeDecl.length();
        int i = 0;
        while (i < len) {
            int skip = skipLiteralOrComment(typeLikeDecl, i);
            if (skip > i) {
                i = skip;
                continue;
            }

            char ch = typeLikeDecl.charAt(i);
            if (Character.isWhitespace(ch)) {
                i++;
                continue;
            }

            if (ch == '@') {
                i = skipAnnotation(typeLikeDecl, i + 1);
                continue;
            }

            if (startsWithWord(typeLikeDecl, i, "final")) {
                i += "final".length();
                continue;
            }

            out.append(ch);
            i++;
        }
        return out.toString();
    }

    private static int skipAnnotation(String text, int index) {
        int len = text.length();
        int i = index;
        while (i < len) {
            char c = text.charAt(i);
            if (Character.isJavaIdentifierPart(c) || c == '.') {
                i++;
                continue;
            }
            break;
        }

        i = skipWsAndCommentsForward(text, i);
        if (i < len && text.charAt(i) == '(') {
            int end = findMatching(text, i, '(', ')');
            if (end < 0) {
                return len;
            }
            return end + 1;
        }
        return i;
    }

    private static boolean shouldBridgeReturn(String prefix, String methodName) {
        String trimmed = prefix.trim();
        if (!trimmed.endsWith(methodName)) {
            return false;
        }

        String head = trimmed.substring(0, trimmed.length() - methodName.length()).trim();
        if (head.isEmpty()) {
            return false;
        }

        String[] tokens = head.split("\\s+");
        String lastMeaningful = null;
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.startsWith("@")) {
                continue;
            }
            if (METHOD_MODIFIERS.contains(token)) {
                continue;
            }
            lastMeaningful = token;
        }

        if (lastMeaningful == null) {
            return false;
        }
        return !"void".equals(lastMeaningful);
    }

    private static String buildBridgeArgs(ParsedParams parsed, int keepCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keepCount; i++) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(parsed.paramNames.get(i));
        }
        for (int i = keepCount; i < parsed.paramDeclNoDefault.size(); i++) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(parsed.defaultExpr.get(i));
        }
        return sb.toString();
    }

    private static ParsedParams parseParams(String paramsText) {
        ParsedParams parsed = new ParsedParams();
        parsed.trailingDefaults = true;
        if (paramsText.trim().isEmpty()) {
            parsed.valid = true;
            return parsed;
        }

        List<String> parts = splitTopLevel(paramsText, ',');
        boolean defaultsStarted = false;
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i).trim();
            if (part.isEmpty()) {
                parsed.valid = false;
                return parsed;
            }

            int eq = findTopLevelAssign(part);
            String decl = (eq >= 0 ? part.substring(0, eq) : part).trim();
            String def = (eq >= 0 ? part.substring(eq + 1) : null);
            if (def != null) {
                def = def.trim();
                if (def.isEmpty()) {
                    parsed.valid = false;
                    return parsed;
                }
                defaultsStarted = true;
                parsed.hasDefault = true;
            } else if (defaultsStarted) {
                parsed.trailingDefaults = false;
            } else {
                parsed.requiredCount++;
            }

            String name = extractParamName(decl);
            if (name == null) {
                parsed.valid = false;
                return parsed;
            }

            parsed.paramDeclNoDefault.add(decl);
            parsed.paramNames.add(name);
            parsed.defaultExpr.add(def);
        }

        parsed.valid = true;
        if (!parsed.hasDefault) {
            parsed.trailingDefaults = true;
        }
        return parsed;
    }

    private static String extractParamName(String decl) {
        int i = skipWsBackward(decl, decl.length() - 1);
        if (i < 0) {
            return null;
        }

        while (i >= 1) {
            if (decl.charAt(i) == ']' && decl.charAt(i - 1) == '[') {
                i = skipWsBackward(decl, i - 2);
            } else {
                break;
            }
        }
        if (i < 0) {
            return null;
        }

        int end = i + 1;
        while (i >= 0 && Character.isJavaIdentifierPart(decl.charAt(i))) {
            i--;
        }
        int start = i + 1;
        if (start >= end) {
            return null;
        }
        String name = decl.substring(start, end);
        return Character.isJavaIdentifierStart(name.charAt(0)) ? name : null;
    }

    private static int findTopLevelAssign(String text) {
        int len = text.length();
        int paren = 0;
        int bracket = 0;
        int brace = 0;
        int angle = 0;
        int i = 0;
        while (i < len) {
            int skip = skipLiteralOrComment(text, i);
            if (skip > i) {
                i = skip;
                continue;
            }

            char ch = text.charAt(i);
            if (ch == '(') paren++;
            else if (ch == ')') paren = Math.max(0, paren - 1);
            else if (ch == '[') bracket++;
            else if (ch == ']') bracket = Math.max(0, bracket - 1);
            else if (ch == '{') brace++;
            else if (ch == '}') brace = Math.max(0, brace - 1);
            else if (ch == '<') angle++;
            else if (ch == '>') angle = Math.max(0, angle - 1);
            else if (ch == '=' && paren == 0 && bracket == 0 && brace == 0 && angle == 0) {
                char prev = (i > 0) ? text.charAt(i - 1) : '\0';
                char next = (i + 1 < len) ? text.charAt(i + 1) : '\0';
                if (prev == '=' || prev == '!' || prev == '<' || prev == '>'
                        || next == '=') {
                    i++;
                    continue;
                }
                return i;
            }
            i++;
        }
        return -1;
    }

    private static List<String> splitTopLevel(String text, char delimiter) {
        List<String> parts = new ArrayList<String>();
        int len = text.length();
        int paren = 0;
        int bracket = 0;
        int brace = 0;
        int angle = 0;
        int start = 0;
        int i = 0;
        while (i < len) {
            int skip = skipLiteralOrComment(text, i);
            if (skip > i) {
                i = skip;
                continue;
            }

            char ch = text.charAt(i);
            if (ch == '(') paren++;
            else if (ch == ')') paren = Math.max(0, paren - 1);
            else if (ch == '[') bracket++;
            else if (ch == ']') bracket = Math.max(0, bracket - 1);
            else if (ch == '{') brace++;
            else if (ch == '}') brace = Math.max(0, brace - 1);
            else if (ch == '<') angle++;
            else if (ch == '>') angle = Math.max(0, angle - 1);
            else if (ch == delimiter && paren == 0 && bracket == 0 && brace == 0 && angle == 0) {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
            i++;
        }
        parts.add(text.substring(start));
        return parts;
    }

    private static int findDeclarationStart(String source, int methodNameStart) {
        int i = methodNameStart;
        while (i > 0) {
            char ch = source.charAt(i - 1);
            if (ch == ';' || ch == '{' || ch == '}') {
                break;
            }
            if (ch == '\n' || ch == '\r') {
                int lineStart = i;
                while (lineStart > 0) {
                    char c = source.charAt(lineStart - 1);
                    if (c == '\n' || c == '\r') {
                        break;
                    }
                    lineStart--;
                }

                String line = source.substring(lineStart, i).trim();
                if (line.isEmpty()) {
                    break;
                }
                if (line.startsWith("@") || isModifierOnlyLine(line)) {
                    i--;
                    continue;
                }
                break;
            }
            i--;
        }
        return i;
    }

    private static boolean isModifierOnlyLine(String line) {
        if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) {
            return false;
        }
        String[] tokens = line.split("\\s+");
        if (tokens.length == 0) {
            return false;
        }
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if (!METHOD_MODIFIERS.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private static int skipThrowsClause(String source, int index) {
        int i = skipWsAndCommentsForward(source, index);
        if (!startsWithWord(source, i, "throws")) {
            return i;
        }

        i += "throws".length();
        int len = source.length();
        boolean needType = true;
        while (i < len) {
            i = skipWsAndCommentsForward(source, i);
            if (i >= len) {
                return -1;
            }

            char ch = source.charAt(i);
            if (ch == '{') {
                return needType ? -1 : i;
            }
            if (ch == ';') {
                return -1;
            }

            if (needType) {
                if (!Character.isJavaIdentifierStart(ch)) {
                    return -1;
                }
                i++;
                while (i < len) {
                    char c = source.charAt(i);
                    if (Character.isJavaIdentifierPart(c) || c == '.' || c == '$') {
                        i++;
                        continue;
                    }
                    break;
                }
                needType = false;
                continue;
            }

            if (ch == ',') {
                needType = true;
                i++;
                continue;
            }
            return -1;
        }
        return -1;
    }

    private static int findMethodBodyStart(String source, int afterCloseParen) {
        int next = skipWsAndCommentsForward(source, afterCloseParen);
        if (next >= source.length()) {
            return -1;
        }
        if (startsWithWord(source, next, "throws")) {
            int afterThrows = skipThrowsClause(source, next);
            if (afterThrows < 0) {
                return -1;
            }
            next = skipWsAndCommentsForward(source, afterThrows);
        }
        if (next < source.length() && source.charAt(next) == '{') {
            return next;
        }
        return -1;
    }

    private static boolean startsWithWord(String source, int index, String word) {
        if (index < 0 || index + word.length() > source.length()) {
            return false;
        }
        if (!source.regionMatches(index, word, 0, word.length())) {
            return false;
        }

        int left = index - 1;
        if (left >= 0 && Character.isJavaIdentifierPart(source.charAt(left))) {
            return false;
        }
        int right = index + word.length();
        return right >= source.length() || !Character.isJavaIdentifierPart(source.charAt(right));
    }

    private static int findMatching(String text, int openIndex, char open, char close) {
        int len = text.length();
        int depth = 0;
        for (int i = openIndex; i < len; i++) {
            int skip = skipLiteralOrComment(text, i);
            if (skip > i) {
                i = skip - 1;
                continue;
            }

            char ch = text.charAt(i);
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int skipWsAndCommentsForward(String text, int index) {
        int i = index;
        int len = text.length();
        while (i < len) {
            while (i < len && Character.isWhitespace(text.charAt(i))) {
                i++;
            }

            int skip = skipLiteralOrComment(text, i);
            if (skip > i && i + 1 < len && text.charAt(i) == '/') {
                i = skip;
                continue;
            }
            break;
        }
        return i;
    }

    private static int skipWsBackward(String text, int index) {
        int i = index;
        while (i >= 0 && Character.isWhitespace(text.charAt(i))) {
            i--;
        }
        return i;
    }

    private static int skipLiteralOrComment(String text, int index) {
        int len = text.length();
        if (index < 0 || index >= len) {
            return index;
        }

        if (startsWith(text, index, "\"\"\"")) {
            int end = text.indexOf("\"\"\"", index + 3);
            return end < 0 ? len : end + 3;
        }

        char ch = text.charAt(index);
        if (ch == '"') {
            int i = index + 1;
            while (i < len) {
                char c = text.charAt(i);
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    return i + 1;
                }
                i++;
            }
            return len;
        }

        if (ch == '\'') {
            int i = index + 1;
            while (i < len) {
                char c = text.charAt(i);
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == '\'') {
                    return i + 1;
                }
                i++;
            }
            return len;
        }

        if (ch == '/' && index + 1 < len) {
            char n = text.charAt(index + 1);
            if (n == '/') {
                int i = index + 2;
                while (i < len) {
                    char c = text.charAt(i);
                    if (c == '\n' || c == '\r') {
                        break;
                    }
                    i++;
                }
                return i;
            }
            if (n == '*') {
                int end = text.indexOf("*/", index + 2);
                return end < 0 ? len : end + 2;
            }
        }
        return index;
    }

    private static boolean startsWith(String text, int index, String token) {
        return index >= 0
                && index + token.length() <= text.length()
                && text.regionMatches(index, token, 0, token.length());
    }

    private static String join(List<String> list, int from, int toExclusive) {
        if (from >= toExclusive) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < toExclusive; i++) {
            if (i > from) {
                sb.append(", ");
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private static final class MethodRewrite {
        final int declStart;
        final int declEndExclusive;
        final String rewritten;

        MethodRewrite(int declStart, int declEndExclusive, String rewritten) {
            this.declStart = declStart;
            this.declEndExclusive = declEndExclusive;
            this.rewritten = rewritten;
        }
    }

    private static final class MethodShape {
        final int declStart;
        final int declEndExclusive;
        final String methodName;
        final ParsedParams parsed;

        MethodShape(int declStart, int declEndExclusive, String methodName, ParsedParams parsed) {
            this.declStart = declStart;
            this.declEndExclusive = declEndExclusive;
            this.methodName = methodName;
            this.parsed = parsed;
        }
    }

    private static final class ParsedParams {
        boolean valid;
        boolean hasDefault;
        boolean trailingDefaults;
        int requiredCount;
        final List<String> paramDeclNoDefault = new ArrayList<String>();
        final List<String> paramNames = new ArrayList<String>();
        final List<String> defaultExpr = new ArrayList<String>();
    }
}
