package com.pseuco.np19.project.rocket;

public interface ParagraphManager {
    BlockElementJob assignNewBlock();

    void closeJob(BlockElementJob job);

    void handleBrokenDoc();
}
