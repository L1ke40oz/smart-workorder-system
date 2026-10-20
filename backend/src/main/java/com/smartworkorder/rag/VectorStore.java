package com.smartworkorder.rag;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Embedded, persistent HNSW vector index. MySQL remains the source of truth. */
@Component
public class VectorStore {
    public record Match(long ticketId,float similarity) {}
    private final Path path;
    private FSDirectory directory;
    private IndexWriter writer;
    public VectorStore(@Value("${app.rag.index-directory}") String path,EmbeddingClient embedding) {
        this.path=Path.of(path).resolve(hash(embedding.space()).substring(0,20));
    }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private void open() throws IOException {
        if(writer==null) { directory=FSDirectory.open(path);writer=new IndexWriter(directory,new IndexWriterConfig());writer.commit(); }
    }
    public synchronized boolean current(long ticketId,String fingerprint) throws IOException {
        open();
        try(var reader=DirectoryReader.open(writer)) {
            var searcher=new IndexSearcher(reader);var result=searcher.search(new TermQuery(new Term("id",Long.toString(ticketId))),1);
            return result.scoreDocs.length==1 && fingerprint.equals(searcher.storedFields().document(result.scoreDocs[0].doc).get("fingerprint"));
        }
    }
    public synchronized void upsert(long ticketId,String fingerprint,float[] vector) throws IOException {
        open();Document doc=new Document();doc.add(new StringField("id",Long.toString(ticketId),Field.Store.YES));
        doc.add(new StoredField("fingerprint",fingerprint));doc.add(new KnnFloatVectorField("vector",vector,VectorSimilarityFunction.COSINE));
        writer.updateDocument(new Term("id",Long.toString(ticketId)),doc);writer.commit();
    }
    public synchronized List<Match> search(float[] vector,long excludedId,int count) throws IOException {
        open();
        try(var reader=DirectoryReader.open(writer)) {
            var searcher=new IndexSearcher(reader);
            Query filter=new BooleanQuery.Builder().add(new MatchAllDocsQuery(),BooleanClause.Occur.FILTER)
                    .add(new TermQuery(new Term("id",Long.toString(excludedId))),BooleanClause.Occur.MUST_NOT).build();
            var hits=searcher.search(new KnnFloatVectorQuery("vector",vector,count,filter),count);List<Match> results=new ArrayList<>();
            for(var hit : hits.scoreDocs) results.add(new Match(Long.parseLong(searcher.storedFields().document(hit.doc).get("id")),
                    Math.max(-1,Math.min(1,hit.score*2-1))));
            return results;
        }
    }
    @PreDestroy public synchronized void close() throws IOException {
        if(writer!=null) writer.close();if(directory!=null) directory.close();
    }
}
