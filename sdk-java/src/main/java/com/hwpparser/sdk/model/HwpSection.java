package com.hwpparser.sdk.model;

import java.util.ArrayList;
import java.util.List;

public class HwpSection {
    private int sectionIndex;
    private List<HwpParagraph> paragraphs = new ArrayList<>();

    public HwpSection() {}

    public HwpSection(int sectionIndex) {
        this.sectionIndex = sectionIndex;
    }

    public int getSectionIndex() { return sectionIndex; }
    public void setSectionIndex(int sectionIndex) { this.sectionIndex = sectionIndex; }

    public List<HwpParagraph> getParagraphs() { return paragraphs; }
    public void setParagraphs(List<HwpParagraph> paragraphs) { this.paragraphs = paragraphs; }
}
