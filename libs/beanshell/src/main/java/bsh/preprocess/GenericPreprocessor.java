package bsh.preprocess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class GenericPreprocessor {
    private static final String IDENT = "[a-zA-Z_$][a-zA-Z0-9_$]*";
    private static final String START_BOUND = "(?<![a-zA-Z0-9_$])";
    private static final String END_BOUND = "(?![a-zA-Z0-9_$])";
    private static final String FLAT_TYPE = IDENT + "(?:\\s*\\.\\s*" + IDENT + ")*(?:\\s*\\[\\s*\\])*";
    private static final String TYPE_ARG = "(?:" + START_BOUND + FLAT_TYPE + END_BOUND + "|\\?\\s*(?:extends\\s+" + START_BOUND + FLAT_TYPE + END_BOUND + ")?|\\?\\s*super\\s+" + START_BOUND + FLAT_TYPE + END_BOUND + ")";
    private static final Pattern USAGE_CONTENT_PATTERN = Pattern.compile("^\\s*(?:" + TYPE_ARG + "\\s*(?:\\s*,\\s*" + TYPE_ARG + "\\s*)*)?$");
    private static final String BOUND = START_BOUND + FLAT_TYPE + END_BOUND + "(?:\\s*&\\s*" + START_BOUND + FLAT_TYPE + END_BOUND + ")*";
    private static final String TYPE_PARAM = START_BOUND + IDENT + END_BOUND + "(?:\\s+extends\\s+" + BOUND + ")?";
    private static final Pattern DEF_CONTENT_PATTERN = Pattern.compile("^\\s*" + TYPE_PARAM + "\\s*(?:\\s*,\\s*" + TYPE_PARAM + "\\s*)*$");
    private static final char MASK_CHAR = '\0';
    private static final Set<String> MODIFIERS = new HashSet<>(Arrays.asList("public", "protected", "private", "static", "final", "abstract", "synchronized", "native", "strictfp", "default", "class", "interface", "enum"));

    private GenericPreprocessor() {
    }

    public static String rewrite(String source) {
        if (source == null || source.indexOf('<') < 0 || source.indexOf('>') < 0) {
            return source;
        }
        return eraseUsages(eraseDefinitions(source));
    }

    private static String removeNestedBrackets(char[] masked, int start, int end) {
        StringBuilder sb = new StringBuilder(end - start);
        int depth = 0;
        for (int i = start; i < end; i++) {
            char c = masked[i];
            if (c == MASK_CHAR) {
                c = ' ';
            }
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (depth == 0) sb.append(c);
        }
        return sb.toString();
    }

    private static String eraseDefinitions(String source) {
        String currentSource = source;
        while (true) {
            char[] masked = maskSource(currentSource);
            int defStart = -1;
            int defEnd = -1;
            int len = masked.length;

            for (int i = len - 1; i >= 0; i--) {
                if (masked[i] == '<') {
                    int right = findMatchingBracket(masked, i, '<', '>');
                    if (right != -1) {
                        String flatContent = removeNestedBrackets(masked, i + 1, right);
                        if (DEF_CONTENT_PATTERN.matcher(flatContent).matches() && isDefinitionContext(masked, i, right)) {
                            defStart = i;
                            defEnd = right;
                            break;
                        }
                    }
                }
            }

            if (defStart == -1) break;

            String content = currentSource.substring(defStart + 1, defEnd);
            Map<String, String> erasureMap = parseTypeParameters(content);
            int scopeEnd = findScopeEnd(masked, defEnd + 1);

            StringBuilder sb = new StringBuilder(currentSource);
            for (int k = defStart; k <= defEnd; k++) {
                if (sb.charAt(k) != '\n' && sb.charAt(k) != '\r') {
                    sb.setCharAt(k, ' ');
                }
            }
            currentSource = sb.toString();

            String scopePrefix = currentSource.substring(0, defEnd + 1);
            String scopeBody = currentSource.substring(defEnd + 1, scopeEnd);
            String scopeSuffix = currentSource.substring(scopeEnd);

            char[] updatedMask = new char[scopeEnd - (defEnd + 1)];
            System.arraycopy(masked, defEnd + 1, updatedMask, 0, updatedMask.length);

            String replacedBody = safeReplaceWords(scopeBody, updatedMask, erasureMap);
            currentSource = scopePrefix + replacedBody + scopeSuffix;
        }
        return currentSource;
    }

    private static boolean isDefinitionContext(char[] masked, int left, int right) {
        String prev = getPreviousWord(masked, left);
        if (".".equals(prev)) return false;
        if ("class".equals(prev) || "interface".equals(prev) || "enum".equals(prev) || MODIFIERS.contains(prev)) {
            return true;
        }
        int i = skipWhitespaceBackward(masked, left - 1);
        char beforeLeft = i >= 0 ? masked[i] : '\0';
        if (prev.isEmpty() || !Character.isJavaIdentifierStart(prev.charAt(0))) {
            if (beforeLeft == '\0' || beforeLeft == ';' || beforeLeft == '{' || beforeLeft == '}' || beforeLeft == '@') {
                return true;
            }
        }
        while (i >= 0 && isIdentifierPart(masked[i])) i--;
        String beforePrev = getPreviousWord(masked, i + 1);
        if ("class".equals(beforePrev) || "interface".equals(beforePrev) || "enum".equals(beforePrev)) {
            return true;
        }
        return false;
    }

    private static Map<String, String> parseTypeParameters(String content) {
        Map<String, String> map = new HashMap<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        int len = content.length();
        for (int i = 0; i < len; i++) {
            char c = content.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ',' && depth == 0) {
                addTypeParam(map, current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        addTypeParam(map, current.toString());

        boolean changed;
        do {
            changed = false;
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String val = entry.getValue();
                if (map.containsKey(val)) {
                    entry.setValue(map.get(val));
                    changed = true;
                }
            }
        } while (changed);

        return map;
    }

    private static void addTypeParam(Map<String, String> map, String param) {
        String trimmed = param.trim();
        if (trimmed.isEmpty()) return;
        int space = trimmed.indexOf(' ');
        String name = space == -1 ? trimmed : trimmed.substring(0, space);
        if (!name.isEmpty() && name.matches(IDENT)) {
            String bound = "Object";
            if (space != -1) {
                String rest = trimmed.substring(space).trim();
                if (rest.startsWith("extends")) {
                    String afterExtends = rest.substring("extends".length()).trim();
                    int amp = afterExtends.indexOf('&');
                    String leftBound = amp == -1 ? afterExtends : afterExtends.substring(0, amp).trim();
                    int lt = leftBound.indexOf('<');
                    if (lt != -1) {
                        leftBound = leftBound.substring(0, lt).trim();
                    }
                    if (!leftBound.isEmpty()) {
                        bound = leftBound;
                    }
                }
            }
            map.put(name, bound);
        }
    }

    private static int findScopeEnd(char[] masked, int start) {
        int len = masked.length;
        int i = skipWhitespaceForward(masked, start);
        if (i >= len) return len;
        if (masked[i] == '{') {
            int end = findMatchingBracket(masked, i, '{', '}');
            return end != -1 ? end + 1 : len;
        }
        while (i < len) {
            if (masked[i] == ';') return i + 1;
            if (masked[i] == '{') {
                int end = findMatchingBracket(masked, i, '{', '}');
                return end != -1 ? end + 1 : len;
            }
            i++;
        }
        return len;
    }

    private static String safeReplaceWords(String source, char[] mask, Map<String, String> replacements) {
        StringBuilder resText = new StringBuilder(source.length());
        int len = source.length();
        int i = 0;
        while (i < len) {
            if (mask[i] == MASK_CHAR) {
                resText.append(source.charAt(i));
                i++;
                continue;
            }
            if (Character.isJavaIdentifierStart(source.charAt(i)) && (i == 0 || !Character.isJavaIdentifierPart(source.charAt(i - 1)))) {
                int start = i;
                int end = i + 1;
                while (end < len && Character.isJavaIdentifierPart(source.charAt(end))) {
                    end++;
                }
                String word = source.substring(start, end);
                if (replacements.containsKey(word) && isTypeContext(source, mask, start, end)) {
                    resText.append(replacements.get(word));
                    i = end;
                    continue;
                }
            }
            resText.append(source.charAt(i));
            i++;
        }
        return resText.toString();
    }

    private static boolean isThrowsOrImplementsContext(char[] mask, int start) {
        int scanIndex = skipWhitespaceBackward(mask, start - 1);
        while (scanIndex >= 0) {
            char c = mask[scanIndex];
            if (c == '{' || c == ';' || c == '}' || c == '(' || c == ')' || c == '=') return false;
            if (isIdentifierPart(c)) {
                int wordStartIndex = scanIndex;
                while (wordStartIndex >= 0 && isIdentifierPart(mask[wordStartIndex])) wordStartIndex--;
                int wordLen = scanIndex - wordStartIndex;
                if (wordLen == 6 && hasSequenceAt(mask, wordStartIndex + 1, "throws")) return true;
                if (wordLen == 10 && hasSequenceAt(mask, wordStartIndex + 1, "implements")) return true;
                if (wordLen == 7 && hasSequenceAt(mask, wordStartIndex + 1, "extends")) return true;
                scanIndex = wordStartIndex;
                continue;
            }
            if (c == '.' || c == ',' || c == '<' || c == '>' || c == '?' || Character.isWhitespace(c) || c == MASK_CHAR) {
                scanIndex--;
                continue;
            }
            return false;
        }
        return false;
    }

    private static boolean isTypeContext(String source, char[] mask, int start, int end) {
        int prev = skipWhitespaceBackward(mask, start - 1);
        char prevChar = prev >= 0 ? mask[prev] : '\0';
        if (prevChar == '.') return false;
        int next = skipWhitespaceForward(mask, end);
        int len = source.length();
        char nextChar = next < len ? mask[next] : '\0';
        if (nextChar == ':' && source.startsWith("::", next)) return true;
        if (nextChar == '=' || nextChar == ';') return false;
        if (nextChar == ')') {
            return checkParenthesesCast(source, mask, prevChar, next, len);
        }
        if (Character.isJavaIdentifierStart(nextChar)) return true;
        if (nextChar == '[') {
            return checkArrayType(source, next, len);
        }
        if (nextChar == '.' && source.startsWith("..", next + 1)) return true;
        if (nextChar == '.') {
            return source.startsWith(".class", next);
        }
        if (isThrowsOrImplementsContext(mask, start)) return true;
        String prevWord = getPreviousWord(mask, start);
        if ("instanceof".equals(prevWord) || "new".equals(prevWord)) return false;
        return prevChar == '<' || nextChar == '>';
    }

    private static boolean checkParenthesesCast(String source, char[] mask, char prevChar, int next, int len) {
        if (prevChar == '(') {
            int postParenthesis = next + 1;
            while (postParenthesis < len && (Character.isWhitespace(source.charAt(postParenthesis)) || mask[postParenthesis] == MASK_CHAR)) {
                postParenthesis++;
            }
            if (postParenthesis < len) {
                char postChar = source.charAt(postParenthesis);
                if (postChar == ';' || postChar == ',' || postChar == ')' || postChar == '}') {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean checkArrayType(String source, int next, int len) {
        int idx = next + 1;
        while (idx < len && Character.isWhitespace(source.charAt(idx))) {
            idx++;
        }
        return idx < len && source.charAt(idx) == ']';
    }

    private static String eraseUsages(String source) {
        char[] masked = maskSource(source);
        char[] result = source.toCharArray();
        List<Integer> openBrackets = new ArrayList<>();
        int len = masked.length;
        for (int i = 0; i < len; i++) {
            if (masked[i] == '<') {
                openBrackets.add(i);
            } else if (masked[i] == '>') {
                if (!openBrackets.isEmpty()) {
                    int left = openBrackets.remove(openBrackets.size() - 1);
                    int right = i;
                    String content = new String(masked, left + 1, right - (left + 1));
                    String prevWord = getPreviousWord(masked, left);
                    String nextText = getNextText(masked, right + 1);
                    boolean isOutermost = openBrackets.isEmpty();
                    if (verifyGenericUsage(content, prevWord, nextText, masked, left, right, isOutermost)) {
                        for (int k = left; k <= right; k++) {
                            if (result[k] != '\n' && result[k] != '\r') result[k] = ' ';
                            masked[k] = ' ';
                        }
                    }
                }
            }
        }
        return new String(result);
    }

    private static boolean isMethodCallContext(char[] masked, int start) {
        int scanIndex = start - 1;
        int parenthesesCount = 0;
        int len = masked.length;
        while (scanIndex >= 0) {
            char c = masked[scanIndex];
            if (c == MASK_CHAR || Character.isWhitespace(c)) {
                scanIndex--;
                continue;
            }
            if (c == '{' || c == '}' || c == ';') return false;
            if (c == ')') {
                parenthesesCount++;
                scanIndex--;
                continue;
            }
            if (c == '(') {
                if (parenthesesCount > 0) {
                    parenthesesCount--;
                    scanIndex--;
                    continue;
                }
                int identifierEndIndex = skipWhitespaceBackward(masked, scanIndex - 1);
                if (identifierEndIndex >= 0 && Character.isJavaIdentifierStart(masked[identifierEndIndex])) {
                    int identifierStartIndex = identifierEndIndex;
                    while (identifierStartIndex >= 0 && isIdentifierPart(masked[identifierStartIndex])) {
                        identifierStartIndex--;
                    }
                    int wordLen = identifierEndIndex - identifierStartIndex;
                    int previousWordEndIndex = skipWhitespaceBackward(masked, identifierStartIndex);
                    if (previousWordEndIndex >= 0 && Character.isJavaIdentifierStart(masked[previousWordEndIndex])) {
                        int previousWordStartIndex = previousWordEndIndex;
                        while (previousWordStartIndex >= 0 && isIdentifierPart(masked[previousWordStartIndex])) {
                            previousWordStartIndex--;
                        }
                        int popLen = previousWordEndIndex - previousWordStartIndex;
                        if (popLen != 3 || !hasSequenceAt(masked, previousWordStartIndex + 1, "new")) {
                            return false;
                        }
                    }
                    int depth = 1;
                    int matchParenthesisIndex = scanIndex + 1;
                    while (matchParenthesisIndex < len) {
                        if (masked[matchParenthesisIndex] == '(') depth++;
                        else if (masked[matchParenthesisIndex] == ')') {
                            depth--;
                            if (depth == 0) break;
                        }
                        matchParenthesisIndex++;
                    }
                    if (matchParenthesisIndex < len) {
                        int afterMethodIndex = skipWhitespaceForward(masked, matchParenthesisIndex + 1);
                        if (afterMethodIndex < len) {
                            char nextChar = masked[afterMethodIndex];
                            if (nextChar == '{') return false;
                            if (nextChar == 't' && hasSequenceAt(masked, afterMethodIndex, "throws")) {
                                return false;
                            }
                        }
                    }
                    boolean isControlKeyword = (wordLen == 2 && hasSequenceAt(masked, identifierStartIndex + 1, "if"))
                            || (wordLen == 3 && hasSequenceAt(masked, identifierStartIndex + 1, "for"))
                            || (wordLen == 5 && (hasSequenceAt(masked, identifierStartIndex + 1, "while") || hasSequenceAt(masked, identifierStartIndex + 1, "catch")))
                            || (wordLen == 6 && hasSequenceAt(masked, identifierStartIndex + 1, "switch"))
                            || (wordLen == 12 && hasSequenceAt(masked, identifierStartIndex + 1, "synchronized"));
                    return !isControlKeyword;
                }
                return false;
            }
            scanIndex--;
        }
        return false;
    }

    private static boolean hasKeywordBeforePackage(char[] masked, int prevWordStart, String keyword) {
        int scanIndex = prevWordStart - 1;
        while (scanIndex >= 0) {
            scanIndex = skipWhitespaceBackward(masked, scanIndex);
            if (scanIndex >= 0 && masked[scanIndex] == '.') {
                scanIndex--;
                continue;
            }
            if (scanIndex >= 0 && isIdentifierPart(masked[scanIndex])) {
                int wordStartIndex = scanIndex;
                while (wordStartIndex >= 0 && isIdentifierPart(masked[wordStartIndex])) {
                    wordStartIndex--;
                }
                int wordLen = scanIndex - wordStartIndex;
                if (wordLen == keyword.length() && hasSequenceAt(masked, wordStartIndex + 1, keyword)) {
                    return true;
                }
                String scannedWord = new String(masked, wordStartIndex + 1, wordLen);
                if (MODIFIERS.contains(scannedWord)) {
                    return false;
                }
                scanIndex = wordStartIndex;
                continue;
            }
            break;
        }
        return false;
    }

    private static int skipWs(String text, int index) {
        int len = text.length();
        while (index < len && Character.isWhitespace(text.charAt(index))) index++;
        return index;
    }

    private static int findMatchingBracketStr(String text, int start, char open, char close) {
        int depth = 0;
        int len = text.length();
        for (int i = start; i < len; i++) {
            int skip = skipLiteralOrComment(text, i);
            if (skip > i) {
                i = skip - 1;
                continue;
            }
            char c = text.charAt(i);
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static boolean isInvalidDeclTerminator(String text, int start) {
        int paren = 0;
        int brace = 0;
        int bracket = 0;
        int len = text.length();
        for (int i = start; i < len; i++) {
            int skip = skipLiteralOrComment(text, i);
            if (skip > i) {
                i = skip - 1;
                continue;
            }
            char c = text.charAt(i);
            if (c == '(') paren++;
            else if (c == ')') {
                paren--;
                if (paren < 0 && brace == 0 && bracket == 0) return false;
            } else if (c == '[') bracket++;
            else if (c == ']') bracket--;
            else if (c == '{') brace++;
            else if (c == '}') {
                brace--;
                if (brace < 0) return true;
            } else if (c == ';') {
                if (paren == 0 && brace == 0 && bracket == 0) return false;
            }
        }
        return false;
    }

    private static boolean verifyGenericUsage(String content, String previousWord, String nextText, char[] masked, int openIndex, int closeIndex, boolean isOutermost) {
        String cleanContent = content.replace(MASK_CHAR, ' ');
        boolean matchesUsage = USAGE_CONTENT_PATTERN.matcher(cleanContent).matches();
        boolean matchesDef = DEF_CONTENT_PATTERN.matcher(cleanContent).matches();
        if (!matchesUsage && !matchesDef) return false;

        boolean hasQualifiedPrefix = !previousWord.isEmpty()
                && (Character.isJavaIdentifierStart(previousWord.charAt(0))
                || ".".equals(previousWord)
                || "::".equals(previousWord));
        if (!hasQualifiedPrefix) {
            String trimmed = nextText.trim();
            return checkUnqualifiedMethodCallWithTypeArguments(trimmed, masked, openIndex);
        }

        if ("::".equals(previousWord)) {
            String trimmed = nextText.trim();
            return !trimmed.isEmpty() && Character.isJavaIdentifierStart(trimmed.charAt(0));
        }

        if (MODIFIERS.contains(previousWord)) return true;

        if (".".equals(previousWord)) {
            return checkMethodCallAfterDot(nextText);
        }

        int prevWordLength = previousWord.length();
        if ("new".equals(previousWord)
                || isThrowsOrImplementsContext(masked, openIndex)
                || hasKeywordBeforePackage(masked, openIndex - prevWordLength, "new")
                || hasKeywordBeforePackage(masked, openIndex - prevWordLength, "instanceof")) {
            return true;
        }

        if (isMethodCallContext(masked, openIndex)) {
            if (hasKeywordBeforePackage(masked, openIndex - prevWordLength, "new")) return true;
            String trimmedSuffix = nextText.trim();
            return trimmedSuffix.startsWith(".") || trimmedSuffix.startsWith("::");
        }

        String trimmedSuffix = nextText.trim();
        if (trimmedSuffix.startsWith(".") || trimmedSuffix.startsWith("::")) return true;

        if (!trimmedSuffix.isEmpty() && trimmedSuffix.charAt(0) == ')') {
            if (checkParenthesesPredecessor(masked, openIndex)) return true;
        }

        if (!trimmedSuffix.isEmpty() && trimmedSuffix.charAt(0) == '[') {
            return checkArrayBracketsSuffix(trimmedSuffix, isOutermost);
        }

        if (!trimmedSuffix.isEmpty() && Character.isJavaIdentifierStart(trimmedSuffix.charAt(0))) {
            return checkIdentifierSuffix(trimmedSuffix);
        }

        if (!trimmedSuffix.isEmpty()) {
            char first = trimmedSuffix.charAt(0);
            if (first == ',' || first == '&') return true;
            if (first == '>' && !isOutermost) return true;
        }
        return false;
    }

    private static boolean checkUnqualifiedMethodCallWithTypeArguments(String trimmedSuffix, char[] masked, int openIndex) {
        if (!checkIdentifierSuffix(trimmedSuffix)) return false;
        int previous = skipWhitespaceBackward(masked, openIndex - 1);
        if (previous < 0) return true;
        char previousChar = masked[previous];
        if (previousChar == '(' || previousChar == '{' || previousChar == '}' || previousChar == ';'
                || previousChar == ',' || previousChar == '=' || previousChar == '?' || previousChar == ':') {
            return true;
        }
        return previousChar == '>' && previous > 0 && masked[previous - 1] == '-';
    }

    private static boolean checkMethodCallAfterDot(String nextText) {
        String trimmed = nextText.trim();
        if (!trimmed.isEmpty() && Character.isJavaIdentifierStart(trimmed.charAt(0))) {
            int idx = 0;
            int tLen = trimmed.length();
            while (idx < tLen && Character.isJavaIdentifierPart(trimmed.charAt(idx))) idx++;
            while (idx < tLen && Character.isWhitespace(trimmed.charAt(idx))) idx++;
            if (idx < tLen && trimmed.charAt(idx) == '(') return true;
        }
        return false;
    }

    private static boolean checkParenthesesPredecessor(char[] masked, int openIndex) {
        int i = openIndex - 1;
        while (i >= 0) {
            i = skipWhitespaceBackward(masked, i);
            if (i >= 0 && masked[i] == '.') {
                i--;
                continue;
            }
            if (i >= 0 && isIdentifierPart(masked[i])) {
                while (i >= 0 && isIdentifierPart(masked[i])) i--;
                continue;
            }
            break;
        }
        i = skipWhitespaceBackward(masked, i);
        return i >= 0 && masked[i] == '(';
    }

    private static boolean checkArrayBracketsSuffix(String trimmedSuffix, boolean isOutermost) {
        int idx = 0;
        int sLen = trimmedSuffix.length();
        while (idx < sLen && (trimmedSuffix.charAt(idx) == '[' || trimmedSuffix.charAt(idx) == ']' || Character.isWhitespace(trimmedSuffix.charAt(idx)))) idx++;
        if (idx < sLen) {
            char nextChar = trimmedSuffix.charAt(idx);
            if (Character.isJavaIdentifierStart(nextChar) || nextChar == ';' || nextChar == '=' || nextChar == ',' || nextChar == ')') {
                return !isInvalidDeclTerminator(trimmedSuffix, idx);
            }
            if (nextChar == '>' && !isOutermost) return true;
            if (nextChar == ':') return true;
            return nextChar == '.';
        }
        return true;
    }

    private static boolean checkIdentifierSuffix(String trimmedSuffix) {
        int idx = 0;
        int sLen = trimmedSuffix.length();
        while (idx < sLen && Character.isJavaIdentifierPart(trimmedSuffix.charAt(idx))) idx++;
        int nextIdx = skipWs(trimmedSuffix, idx);
        if (nextIdx < sLen) {
            char nextChar = trimmedSuffix.charAt(nextIdx);
            if (nextChar == ';' || nextChar == ':' || nextChar == ')') return true;
            if (nextChar == ',') {
                return !isInvalidDeclTerminator(trimmedSuffix, nextIdx);
            }
            if (nextChar == '=') {
                if (nextIdx + 1 < sLen && trimmedSuffix.charAt(nextIdx + 1) == '=') return false;
                return !isInvalidDeclTerminator(trimmedSuffix, nextIdx);
            }
            if (nextChar == '(') {
                int afterParen = findMatchingBracketStr(trimmedSuffix, nextIdx, '(', ')');
                if (afterParen != -1) {
                    int postIdx = skipWs(trimmedSuffix, afterParen + 1);
                    if (postIdx < sLen) {
                        char postChar = trimmedSuffix.charAt(postIdx);
                        if (postChar == '{' || postChar == ';') return true;
                        if (trimmedSuffix.startsWith("throws", postIdx)) return true;
                    }
                }
            }
        }
        return false;
    }

    private static int skipWhitespaceBackward(char[] str, int index) {
        while (index >= 0 && (Character.isWhitespace(str[index]) || str[index] == MASK_CHAR)) index--;
        return index;
    }

    private static int skipWhitespaceForward(char[] str, int index) {
        int len = str.length;
        while (index < len && (Character.isWhitespace(str[index]) || str[index] == MASK_CHAR)) index++;
        return index;
    }

    private static boolean isIdentifierPart(char ch) {
        return Character.isJavaIdentifierPart(ch) || ch == '_';
    }

    private static String getPreviousWord(char[] mask, int startIndex) {
        int i = skipWhitespaceBackward(mask, startIndex - 1);
        if (i < 0) return "";
        if (mask[i] == '.') return ".";
        if (mask[i] == ':' && i > 0 && mask[i - 1] == ':') return "::";
        int end = i + 1;
        if (!isIdentifierPart(mask[i])) return String.valueOf(mask[i]);
        while (i >= 0 && isIdentifierPart(mask[i])) i--;
        return new String(mask, i + 1, end - (i + 1));
    }

    private static String getNextText(char[] masked, int start) {
        int index = skipWhitespaceForward(masked, start);
        int len = masked.length;
        if (index >= len) return "";
        StringBuilder suffix = new StringBuilder();
        int parenthesesDepth = 0;
        int bracketDepth = 0;
        for (int i = index; i < len; i++) {
            char current = masked[i];
            if (current == MASK_CHAR) continue;
            suffix.append(current);
            if (current == '(') {
                parenthesesDepth++;
            } else if (current == ')') {
                if (parenthesesDepth > 0) parenthesesDepth--;
            } else if (current == '[') {
                bracketDepth++;
            } else if (current == ']') {
                if (bracketDepth > 0) bracketDepth--;
            } else if (parenthesesDepth == 0 && bracketDepth == 0
                    && (current == ';' || current == '{' || current == '}')) {
                break;
            }
        }
        return suffix.toString();
    }

    private static char[] maskSource(String source) {
        int len = source.length();
        char[] masked = source.toCharArray();
        int i = 0;
        while (i < len) {
            int skip = skipLiteralOrComment(source, i);
            if (skip > i) {
                for (int j = i; j < skip; j++) {
                    char c = masked[j];
                    if (c != '\n' && c != '\r') masked[j] = MASK_CHAR;
                }
                i = skip;
            } else {
                i++;
            }
        }
        return masked;
    }

    private static int skipLiteralOrComment(String source, int i) {
        final int len = source.length();
        if (i >= len) return i;
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
                while (j < len && source.charAt(j) != '\n' && source.charAt(j) != '\r') j++;
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
        int len = text.length();
        for (int i = start; i < len; i++) {
            char ch = text.charAt(i);
            if (ch == '\\') {
                i++;
                continue;
            }
            if (ch == '"') return i;
        }
        return -1;
    }

    private static int findCharLiteralEnd(String text, int start) {
        int len = text.length();
        for (int i = start; i < len; i++) {
            char ch = text.charAt(i);
            if (ch == '\\') {
                i++;
                continue;
            }
            if (ch == '\'') return i;
            if (ch == '\n' || ch == '\r') return -1;
        }
        return -1;
    }

    private static int findBlockCommentEnd(String text, int start) {
        int len = text.length();
        for (int i = start; i + 1 < len; i++) {
            if (text.charAt(i) == '*' && text.charAt(i + 1) == '/') return i;
        }
        return -1;
    }

    private static boolean isTripleQuote(String text, int index) {
        return index + 2 < text.length() && text.charAt(index) == '"' && text.charAt(index + 1) == '"' && text.charAt(index + 2) == '"';
    }

    private static int findTripleQuoteEnd(String text, int start) {
        int len = text.length();
        for (int i = start; i + 2 < len; i++) {
            if (isTripleQuote(text, i)) return i;
        }
        return -1;
    }

    private static int findMatchingBracket(char[] masked, int start, char open, char close) {
        int depth = 0;
        int len = masked.length;
        for (int i = start; i < len; i++) {
            if (masked[i] == open) depth++;
            else if (masked[i] == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static boolean hasSequenceAt(char[] seq, int offset, String search) {
        int len = search.length();
        if (offset + len > seq.length) return false;
        for (int i = 0; i < len; i++) {
            if (seq[offset + i] != search.charAt(i)) return false;
        }
        return true;
    }
}
