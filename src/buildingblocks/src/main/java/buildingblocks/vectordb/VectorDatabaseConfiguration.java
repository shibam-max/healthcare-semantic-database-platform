package buildingblocks.vectordb;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Vector Database Configuration for Healthcare Semantic Search
 * 
 * Configures PostgreSQL with pgvector extension for:
 * - Medical record similarity search
 * - Clinical decision support
 * - Patient case matching
 * - Drug interaction analysis
 */
@Configuration
public class VectorDatabaseConfiguration {

    @Value("${spring.ai.openai.api-key}")
    private String openAiApiKey;

    @Value("${app.vectordb.dimension:1536}")
    private int vectorDimension;

    @Bean
    public EmbeddingModel embeddingModel() {
        return new OpenAiEmbeddingModel(new OpenAiApi(openAiApiKey));
    }

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return new PgVectorStore.Builder(jdbcTemplate, embeddingModel)
                .withSchemaName("healthcare_vectors")
                .withTableName("medical_embeddings")
                .withDimensions(vectorDimension)
                .withDistanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .withRemoveExistingVectorStoreTable(false)
                .withIndexType(PgVectorStore.PgIndexType.HNSW)
                .build();
    }
}