package com.astersearch.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.astersearch.model.Document;
import com.astersearch.text.Tokenizer;

public class InvertedIndex {
    private final Tokenizer tokenizer;

    private final Map<String, Set<Integer>> index;

    private final Set<Integer> indexedDocumentIds;

    public InvertedIndex(Tokenizer tokenizer){
        if (tokenizer == null){
            throw new IllegalArgumentException("Tokenizer cannot be null");
        }

        this.tokenizer = tokenizer;
        this.index = new HashMap<>();
        this.indexedDocumentIds = new HashSet<>();      
    }

    public void add(Document document){
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }

        if (indexedDocumentIds.contains(document.id())) {
            throw new IllegalArgumentException("Document ID has already been indexed");
        }

        List<String> titleTokens =  tokenizer.tokenize(document.title());

        List<String> contentTokens = tokenizer.tokenize(document.content());

        List<String> allTokens = new ArrayList<>(titleTokens);
        allTokens.addAll(contentTokens);
            
        for(String token : allTokens){
            this.index.computeIfAbsent(
                token,
                ignored -> new HashSet<>()
                ).add(document.id());
        }
        this.indexedDocumentIds.add(document.id());
    }

    public Set<Integer> findDocumentIds(String term){
        List<String> tokens = this.tokenizer.tokenize(term);
        if (tokens.size() > 1){
            throw new IllegalArgumentException("Expected one search term");
        }
        if (tokens.isEmpty()){
            return Set.of();
        }

        String normalisedTerm = tokens.getFirst();

        Set<Integer> storedDocumentIds =index.get(normalisedTerm);

        if (storedDocumentIds == null) {
            return Set.of();
        }

        return Set.copyOf(storedDocumentIds);
    }

    public int documentCount(){
        return this.indexedDocumentIds.size();
    }

}
