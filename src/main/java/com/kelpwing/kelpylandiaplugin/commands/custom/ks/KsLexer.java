package com.kelpwing.kelpylandiaplugin.commands.custom.ks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tokenizes KS Lite source code into a {@link List} of {@link KsToken}.
 *
 * <h3>Key behaviours</h3>
 * <ul>
 *   <li>Newlines are treated as whitespace and silently skipped - blocks are
 *       delimited by {@code {}} rather than indentation.</li>
 *   <li>Line numbers are tracked for error messages.</li>
 *   <li>Comments: {@code #…} single-line; {@code ###…###} multi-line block.</li>
 *   <li>Strings: both double-quote and single-quote, with {@code \n \t \" \' \\} escapes.</li>
 *   <li>Numbers: integer and decimal (no sign - unary minus is an operator).</li>
 * </ul>
 */
public final class KsLexer {

        // Keyword table
    
    private static final Map<String, KsToken.Type> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("def",      KsToken.Type.DEF);
        KEYWORDS.put("if",       KsToken.Type.IF);
        KEYWORDS.put("elif",     KsToken.Type.ELIF);
        KEYWORDS.put("else",     KsToken.Type.ELSE);
        KEYWORDS.put("while",    KsToken.Type.WHILE);
        KEYWORDS.put("for",      KsToken.Type.FOR);
        KEYWORDS.put("in",       KsToken.Type.IN);
        KEYWORDS.put("return",   KsToken.Type.RETURN);
        KEYWORDS.put("break",    KsToken.Type.BREAK);
        KEYWORDS.put("continue", KsToken.Type.CONTINUE);
        KEYWORDS.put("print",    KsToken.Type.PRINT);
        KEYWORDS.put("try",      KsToken.Type.TRY);
        KEYWORDS.put("catch",    KsToken.Type.CATCH);
        KEYWORDS.put("throw",    KsToken.Type.THROW);
        KEYWORDS.put("and",      KsToken.Type.AND);
        KEYWORDS.put("or",       KsToken.Type.OR);
        KEYWORDS.put("not",      KsToken.Type.NOT);
        KEYWORDS.put("true",     KsToken.Type.BOOL_LIT);
        KEYWORDS.put("false",    KsToken.Type.BOOL_LIT);
        KEYWORDS.put("null",     KsToken.Type.NULL_LIT);
    }

        // State
    
    private final char[] src;
    private int pos  = 0;
    private int line = 1;

    public KsLexer(String source) {
        this.src = source.toCharArray();
    }

        // Public API
    
    /**
     * Tokenize the entire source and return the token list
     * (always ends with an {@link KsToken.Type#EOF} token).
     */
    public List<KsToken> tokenize() {
        List<KsToken> tokens = new ArrayList<>();
        while (true) {
            KsToken t = next();
            tokens.add(t);
            if (t.type == KsToken.Type.EOF) break;
        }
        return tokens;
    }

        // Internal
    
    /** Produce the next token, skipping all whitespace and comments first. */
    private KsToken next() {
        skipTrivia();

        if (pos >= src.length) return make(KsToken.Type.EOF, "");

        int startLine = line;
        char c = src[pos];

        if (c == '"' || c == '\'') return lexString(c, startLine);
        if (Character.isDigit(c))  return lexNumber(startLine);
        if (c == '_' || Character.isLetter(c)) return lexIdent(startLine);

        // Operators and punctuation 
        pos++;
        switch (c) {
            case '+':
                if (pos < src.length && src[pos] == '=') { pos++; return new KsToken(KsToken.Type.PLUS_EQ,  "+=", startLine); }
                return new KsToken(KsToken.Type.PLUS,    "+",  startLine);
            case '-':
                if (pos < src.length && src[pos] == '=') { pos++; return new KsToken(KsToken.Type.MINUS_EQ, "-=", startLine); }
                return new KsToken(KsToken.Type.MINUS,   "-",  startLine);
            case '*':
                if (pos < src.length && src[pos] == '=') { pos++; return new KsToken(KsToken.Type.STAR_EQ,  "*=", startLine); }
                return new KsToken(KsToken.Type.STAR,    "*",  startLine);
            case '/':
                if (pos < src.length && src[pos] == '=') { pos++; return new KsToken(KsToken.Type.SLASH_EQ, "/=", startLine); }
                return new KsToken(KsToken.Type.SLASH,   "/",  startLine);
            case '%': return new KsToken(KsToken.Type.PERCENT,  "%",  startLine);
            case '=':
                if (pos < src.length && src[pos] == '=') { pos++; return new KsToken(KsToken.Type.EQ,     "==", startLine); }
                return new KsToken(KsToken.Type.ASSIGN,  "=",  startLine);
            case '!':
                if (pos < src.length && src[pos] == '=') { pos++; return new KsToken(KsToken.Type.NEQ,    "!=", startLine); }
                return new KsToken(KsToken.Type.NOT,     "!",  startLine);  // bare ! == not
            case '<':
                if (pos < src.length && src[pos] == '=') { pos++; return new KsToken(KsToken.Type.LE,     "<=", startLine); }
                return new KsToken(KsToken.Type.LT,      "<",  startLine);
            case '>':
                if (pos < src.length && src[pos] == '=') { pos++; return new KsToken(KsToken.Type.GE,     ">=", startLine); }
                return new KsToken(KsToken.Type.GT,      ">",  startLine);
            case '(': return new KsToken(KsToken.Type.LPAREN,   "(",  startLine);
            case ')': return new KsToken(KsToken.Type.RPAREN,   ")",  startLine);
            case '{': return new KsToken(KsToken.Type.LBRACE,   "{",  startLine);
            case '}': return new KsToken(KsToken.Type.RBRACE,   "}",  startLine);
            case '[': return new KsToken(KsToken.Type.LBRACKET, "[",  startLine);
            case ']': return new KsToken(KsToken.Type.RBRACKET, "]",  startLine);
            case ',': return new KsToken(KsToken.Type.COMMA,    ",",  startLine);
            case ':': return new KsToken(KsToken.Type.COLON,    ":",  startLine);
            case '.': return new KsToken(KsToken.Type.DOT,      ".",  startLine);
            default:
                // Unknown character - skip and try again (graceful degradation)
                return next();
        }
    }

    /** Skip whitespace, newlines, and comments. */
    private void skipTrivia() {
        while (pos < src.length) {
            char c = src[pos];

            // Whitespace + newlines
            if (c == ' ' || c == '\t' || c == '\r') { pos++; continue; }
            if (c == '\n') { pos++; line++; continue; }

            // Block comment ###…###
            if (c == '#' && pos + 2 < src.length && src[pos+1] == '#' && src[pos+2] == '#') {
                pos += 3;
                while (pos < src.length) {
                    if (pos + 2 < src.length && src[pos] == '#' && src[pos+1] == '#' && src[pos+2] == '#') {
                        pos += 3; break;
                    }
                    if (src[pos] == '\n') line++;
                    pos++;
                }
                continue;
            }

            // Single-line comment: # or //
            if (c == '#') {
                while (pos < src.length && src[pos] != '\n') pos++;
                continue;
            }
            if (c == '/' && pos + 1 < src.length && src[pos+1] == '/') {
                while (pos < src.length && src[pos] != '\n') pos++;
                continue;
            }

            break;
        }
    }

    /** Lex a string literal (opening quote already identified). */
    private KsToken lexString(char quote, int startLine) {
        pos++; // skip opening quote
        StringBuilder sb = new StringBuilder();
        while (pos < src.length && src[pos] != quote) {
            char c = src[pos];
            if (c == '\\' && pos + 1 < src.length) {
                pos++;
                char esc = src[pos];
                switch (esc) {
                    case 'n':  sb.append('\n'); break;
                    case 't':  sb.append('\t'); break;
                    case '"':  sb.append('"');  break;
                    case '\'': sb.append('\''); break;
                    case '\\': sb.append('\\'); break;
                    default:   sb.append('\\'); sb.append(esc); break;
                }
            } else {
                if (c == '\n') line++;
                sb.append(c);
            }
            pos++;
        }
        if (pos < src.length) pos++; // skip closing quote
        return new KsToken(KsToken.Type.STRING, sb.toString(), startLine);
    }

    /** Lex a numeric literal (integer or decimal). */
    private KsToken lexNumber(int startLine) {
        int start = pos;
        while (pos < src.length && Character.isDigit(src[pos])) pos++;
        if (pos < src.length && src[pos] == '.'
                && pos + 1 < src.length && Character.isDigit(src[pos+1])) {
            pos++;
            while (pos < src.length && Character.isDigit(src[pos])) pos++;
        }
        return new KsToken(KsToken.Type.NUMBER, new String(src, start, pos - start), startLine);
    }

    /** Lex an identifier or keyword. */
    private KsToken lexIdent(int startLine) {
        int start = pos;
        while (pos < src.length && (Character.isLetterOrDigit(src[pos]) || src[pos] == '_')) pos++;
        String word = new String(src, start, pos - start);
        KsToken.Type kw = KEYWORDS.get(word);
        return new KsToken(kw != null ? kw : KsToken.Type.IDENT, word, startLine);
    }

    private KsToken make(KsToken.Type type, String val) {
        return new KsToken(type, val, line);
    }
}
