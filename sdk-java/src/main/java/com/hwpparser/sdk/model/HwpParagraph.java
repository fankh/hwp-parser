package com.hwpparser.sdk.model;

public class HwpParagraph {
    private String text = "";
    private int paraShapeId;
    private int styleId;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public int getParaShapeId() { return paraShapeId; }
    public void setParaShapeId(int paraShapeId) { this.paraShapeId = paraShapeId; }

    public int getStyleId() { return styleId; }
    public void setStyleId(int styleId) { this.styleId = styleId; }
}
