package com.pseuco.np19.project.launcher.parser;

import java.io.IOException;
import java.io.Reader;
import java.util.function.IntPredicate;

/**
 * A stream-parser for input documents. Builds a document using a {@link DocumentBuilder}.
 */
public class Parser {
    private static final int NEED_CHAR = Integer.MAX_VALUE;

    private static boolean isSpecialCharacter(int character) {
        return !Character.isAlphabetic(character) && !Character.isWhitespace(character) && character != '-';
    }

    private final Reader reader;

    private final DocumentBuilder documentBuilder;

    private int current = NEED_CHAR;

    private ParagraphBuilder activeParagraph;

    private boolean aborted = false;

    private int savedLine = 0;
    private int savedColumn = 0;

    private int currentLine = 0;
    private int currentColumn = 0;

    /**
     * Parses an input document and builds it using a {@link DocumentBuilder}.
     *
     * @param reader A {@link Reader} to read the input document from.
     * @param builder A {@link DocumentBuilder} to create the document with.
     *
     * @throws IOException In case there is an error reading the document.
     */
    public static void parse(Reader reader, DocumentBuilder builder) throws IOException {
        (new Parser(reader, builder)).buildDocument();
    }

    /**
     * Constructs a {@link Parser} from the given parameters.
     *
     * @param reader A {@link Reader} to read the input document from.
     * @param documentBuilder A {@link DocumentBuilder} to create the document with.
     */
    public Parser(Reader reader, DocumentBuilder documentBuilder) {
        this.reader = reader;
        this.documentBuilder = documentBuilder;
    }

    private int consume() throws IOException {
        this.peek();
        if (this.current == '\n') {
            this.currentLine++;
            this.currentColumn = 0;
        } else {
            this.currentColumn++;
        }
        final int character = this.current;
        this.current = NEED_CHAR;
        return character;
    }

    private int peek() throws IOException {
        if (this.current == NEED_CHAR) {
            this.current = this.reader.read();
        }
        return this.current;
    }

    private boolean accept(int character) throws IOException {
         if (this.peek() == character) {
             this.consume();
             return true;
         }
         return false;
    }

    private String read(IntPredicate predicate) throws IOException {
        final StringBuilder value = new StringBuilder();
        while (predicate.test(this.peek()) && this.current >= 0) {
            value.append((char) this.consume());
        }
        return value.toString();
    }

    private void savePosition() {
        this.savedLine = this.currentLine;
        this.savedColumn = this.currentColumn;
    }

    private Position getSavedPosition() {
        return new Position(this.savedLine, this.savedColumn);
    }

    private void lazyStartParagraph() {
        if (this.activeParagraph == null) {
            this.activeParagraph = this.documentBuilder.appendParagraph(this.getSavedPosition());
        }
    }

    private void lazyEndParagraph() {
        if (this.activeParagraph != null) {
            this.activeParagraph.finish();
            this.activeParagraph = null;
        }
    }

    /**
     * Aborts the parsing process.
     */
    public void abort() {
        this.aborted = true;
    }

    /**
     * Parses the input document and builds it using the {@link DocumentBuilder}.
     *
     * @throws IOException In case there is an error reading the document.
     */
    public void buildDocument() throws IOException {
        while (!this.aborted && this.peek() > 0) {
            this.savePosition();
            if (this.accept('-')) {
                this.lazyStartParagraph();
                this.activeParagraph.appendHyphen(this.getSavedPosition());
            } else if (Character.isWhitespace(this.peek())) {
                this.read(Character::isWhitespace);
                if (this.peek() < 0) {
                    // ignore any whitespace at the end of file
                    break;
                }
                switch (this.currentLine - this.savedLine) {
                    case 0:
                    case 1:
                        this.lazyStartParagraph();
                        this.activeParagraph.appendSpace(this.getSavedPosition());
                        break;
                    case 2:
                        this.lazyEndParagraph();
                        break;
                    default:
                        this.lazyEndParagraph();
                        this.documentBuilder.appendForcedPageBreak(this.getSavedPosition());
                        break;
                }
            } else if (Character.isAlphabetic(this.peek())) {
                final String value = this.read(Character::isAlphabetic);
                this.lazyStartParagraph();
                this.activeParagraph.appendSyllable(this.getSavedPosition(), value);
            } else {
                final String value = this.read(Parser::isSpecialCharacter);
                this.lazyStartParagraph();
                this.activeParagraph.appendSpecial(this.getSavedPosition(), value);
            }
        }
        if (!this.aborted) {
            this.lazyEndParagraph();
            this.documentBuilder.finish();
        }
    }
}
