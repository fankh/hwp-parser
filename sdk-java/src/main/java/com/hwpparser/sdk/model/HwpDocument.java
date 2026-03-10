package com.hwpparser.sdk.model;

import java.util.ArrayList;
import java.util.List;

public class HwpDocument {
    private String version = "";
    private boolean compressed;
    private boolean encrypted;
    private List<HwpSection> sections = new ArrayList<>();
    private String previewText = "";

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public boolean isCompressed() { return compressed; }
    public void setCompressed(boolean compressed) { this.compressed = compressed; }

    public boolean isEncrypted() { return encrypted; }
    public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }

    public List<HwpSection> getSections() { return sections; }
    public void setSections(List<HwpSection> sections) { this.sections = sections; }

    public String getPreviewText() { return previewText; }
    public void setPreviewText(String previewText) { this.previewText = previewText; }
}
