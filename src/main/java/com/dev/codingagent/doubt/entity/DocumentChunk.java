package com.dev.codingagent.doubt.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * A chunk of a document, with its embedding vector stored inline.
 * Similarity search is computed in-app (Option C) — no vector index needed,
 * fine for per-user personal-document scale.
 */
@Document(collection = "doubt_chunks")
public class DocumentChunk {

    @Id
    private String id;

    private String chunkId;

    @Indexed
    private String documentId;

    @Indexed
    private String userId;

    private String text;
    private List<Double> embedding;   // the vector
    private Integer chunkIndex;
    private Integer page;             // optional, for citation

    public DocumentChunk() {}

    public String getId()                       { return id; }
    public void   setId(String id)              { this.id = id; }
    public String getChunkId()                  { return chunkId; }
    public void   setChunkId(String s)          { this.chunkId = s; }
    public String getDocumentId()               { return documentId; }
    public void   setDocumentId(String s)       { this.documentId = s; }
    public String getUserId()                   { return userId; }
    public void   setUserId(String s)           { this.userId = s; }
    public String getText()                     { return text; }
    public void   setText(String s)             { this.text = s; }
    public List<Double> getEmbedding()          { return embedding; }
    public void         setEmbedding(List<Double> e) { this.embedding = e; }
    public Integer getChunkIndex()              { return chunkIndex; }
    public void    setChunkIndex(Integer i)     { this.chunkIndex = i; }
    public Integer getPage()                    { return page; }
    public void    setPage(Integer p)           { this.page = p; }
}