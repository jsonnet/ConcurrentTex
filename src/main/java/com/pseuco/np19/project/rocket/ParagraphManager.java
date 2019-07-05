package com.pseuco.np19.project.rocket;

import com.pseuco.np19.project.slug.tree.block.BlockElement;

public interface ParagraphManager {
    BlockElementJob assignNewBlock();
    void closeJob(BlockElementJob job);
    void handleBrokenDoc();
}
