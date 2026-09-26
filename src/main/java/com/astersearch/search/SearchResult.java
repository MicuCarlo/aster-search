package com.astersearch.search;

import com.astersearch.model.Document;

public record SearchResult(Document document, double score) {}
