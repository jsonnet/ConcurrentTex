package com.pseuco.np19.project.rocket;

import com.pseuco.np19.project.launcher.parser.DocumentBuilder;
import com.pseuco.np19.project.launcher.parser.ParagraphBuilder;
import com.pseuco.np19.project.launcher.parser.Position;
import com.pseuco.np19.project.slug.tree.block.BlockElement;
import com.pseuco.np19.project.slug.tree.block.ForcedPageBreak;
import com.pseuco.np19.project.slug.tree.block.Paragraph;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ConcurrentDocument implements DocumentBuilder {
    //TODO thought about ConcurrentBlockingQueue but sync list should be enough as only one thread is writing and other just reading
    private List<BlockElement> syncElements = Collections.synchronizedList(new LinkedList<>());

    /**
     * @return Returns the block elements of the document.
     */
    public List<BlockElement> getElements() {
        return this.syncElements;
    }

    @Override
    public void appendForcedPageBreak(Position position) {
        syncElements.add(new ForcedPageBreak());
    }

    @Override
    public ParagraphBuilder appendParagraph(Position position) {
        Paragraph paragraph = new Paragraph();
        this.syncElements.add(paragraph);
        return paragraph;
    }

    @Override
    public void finish() {
        this.syncElements = Collections.unmodifiableList(this.syncElements);
    }
}
