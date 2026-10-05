package cn.xfone.ai.agent;

import cn.xfone.ai.domain.training.model.entity.TrainingContentEntity;
import cn.xfone.ai.domain.training.service.TrainingContentDomainService;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TrainingKnowledgeService {
    private static final String KNOWLEDGE_NAME = "cognitive-training";

    private final TrainingContentDomainService contentDomainService;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ObjectProvider<JdbcTemplate> vectorJdbcTemplateProvider;
    private final TokenTextSplitter textSplitter = new TokenTextSplitter();

    public TrainingKnowledgeService(TrainingContentDomainService contentDomainService,
                                    ObjectProvider<VectorStore> vectorStoreProvider,
                                    @Qualifier("pgVectorJdbcTemplate")
                                    ObjectProvider<JdbcTemplate> vectorJdbcTemplateProvider) {
        this.contentDomainService = contentDomainService;
        this.vectorStoreProvider = vectorStoreProvider;
        this.vectorJdbcTemplateProvider = vectorJdbcTemplateProvider;
    }

    /**
     * Rebuilds one training content's vector chunks. The operation is idempotent
     * because old chunks for the same content are removed before new chunks are added.
     */
    public synchronized int indexContent(Long contentId) {
        VectorStore vectorStore = requireVectorStore();
        TrainingContentEntity content = contentDomainService.requireContent(contentId);
        deleteContentVectors(vectorStore, contentId);

        List<Document> chunks = toDocuments(content);
        if (!chunks.isEmpty()) {
            vectorStore.add(chunks);
        }
        return chunks.size();
    }

    public synchronized Map<Long, Integer> indexContents(List<Long> contentIds) {
        List<Long> normalizedIds = normalizeContentIds(contentIds);
        Map<Long, Integer> chunkCounts = new LinkedHashMap<>();
        for (Long contentId : normalizedIds) {
            chunkCounts.put(contentId, indexContent(contentId));
        }
        return chunkCounts;
    }

    public synchronized void deleteContent(Long contentId) {
        validateContentId(contentId);
        deleteContentVectors(requireVectorStore(), contentId);
    }

    public List<Document> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            return List.of();
        }
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(4)
                .similarityThreshold(0.25)
                .filterExpression("knowledge == '" + KNOWLEDGE_NAME + "'")
                .build());
        return documents == null ? List.of() : documents;
    }

    public boolean enabled() {
        return vectorStoreProvider.getIfAvailable() != null;
    }

    private List<Document> toDocuments(TrainingContentEntity content) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("knowledge", KNOWLEDGE_NAME);
        metadata.put("contentId", String.valueOf(content.getId()));
        metadata.put("title", content.getTitle());
        metadata.put("contentType", content.getContentType());
        metadata.put("difficulty", content.getDifficulty() == null ? 0 : content.getDifficulty());

        Document document = new Document("训练内容标题：" + content.getTitle()
                + "\n训练内容正文：" + content.getContentBody(), metadata);
        return textSplitter.apply(List.of(document));
    }

    private void deleteContentVectors(VectorStore vectorStore, Long contentId) {
        validateContentId(contentId);
        JdbcTemplate jdbcTemplate = vectorJdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            jdbcTemplate.update(
                    "DELETE FROM cognitive_training_vector_store "
                            + "WHERE metadata->>'knowledge' = ? AND metadata->>'contentId' = ?",
                    KNOWLEDGE_NAME, String.valueOf(contentId));
            return;
        }
        vectorStore.delete("knowledge == '" + KNOWLEDGE_NAME
                + "' && contentId == '" + contentId + "'");
    }

    private List<Long> normalizeContentIds(List<Long> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            throw new IllegalArgumentException("训练内容 ID 列表不能为空");
        }
        List<Long> normalizedIds = contentIds.stream().distinct().toList();
        normalizedIds.forEach(this::validateContentId);
        return normalizedIds;
    }

    private void validateContentId(Long contentId) {
        if (contentId == null || contentId <= 0) {
            throw new IllegalArgumentException("训练内容 ID 必须为正数");
        }
    }

    private VectorStore requireVectorStore() {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new IllegalStateException("认知训练知识库未启用");
        }
        return vectorStore;
    }
}


