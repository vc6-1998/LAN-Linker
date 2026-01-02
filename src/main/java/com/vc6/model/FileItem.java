package com.vc6.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * 用于在 TableView 中显示的文件数据模型
 */
public class FileItem {
    private final StringProperty name;
    private final StringProperty size;
    private final StringProperty date;
    private final boolean isDirectory;
    private final String absolutePath;

    public FileItem(String name, String size, String date, boolean isDirectory, String absolutePath) {
        this.name = new SimpleStringProperty(name);
        this.size = new SimpleStringProperty(size);
        this.date = new SimpleStringProperty(date);
        this.isDirectory = isDirectory;
        this.absolutePath = absolutePath;
    }

    public String getName() { return name.get(); }
    public StringProperty nameProperty() { return name; }

    public String getSize() { return size.get(); }
    public StringProperty sizeProperty() { return size; }

    public String getDate() { return date.get(); }
    public StringProperty dateProperty() { return date; }

    public boolean isDirectory() { return isDirectory; }
    public String getAbsolutePath() { return absolutePath; }
}
