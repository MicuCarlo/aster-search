package com.astersearch.model;

public final record Document(int id, String title, String content) {
    public Document{
        if (id < 0){
            throw new IllegalArgumentException("ID cannot be negative");
        }
        if (title == null || title.isBlank()){
            throw new IllegalArgumentException("Title cannot be null or blank");
        }
        if (content == null){
            throw new IllegalArgumentException("Content cannot be null");
        }
    }

}
