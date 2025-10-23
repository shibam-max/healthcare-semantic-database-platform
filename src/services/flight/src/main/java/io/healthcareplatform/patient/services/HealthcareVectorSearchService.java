package io.healthcareplatform.patient.services;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Healthcare Vector Search Service
 * 
 * Provides semantic search capabilities for medical records using vector embeddings.
 * Supports clinical decision support, similar case finding, and medical knowledge retrieval.
 */
@Service
@Slf4j
public class HealthcareVectorSearchService {

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;

    @Autowired
    public HealthcareVectorSearchService(VectorStore vectorStore, EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * Find similar medical records based on clinical text
     */
    public List<MedicalRecordSearchResult> findSimilarMedicalRecords(String clinicalText, int maxResults, double similarityThreshold) {
        log.info("Searching for similar medical records for query: {}", clinicalText);
        
        try {
            SearchRequest searchRequest = SearchRequest.query(clinicalText)
                    .withTopK(maxResults)
                    .withSimilarityThreshold(similarityThreshold);
            
            List<Document> similarDocuments = vectorStore.similaritySearch(searchRequest);
            
            return similarDocuments.stream()
                    .map(this::mapToMedicalRecordSearchResult)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("Error performing vector search for medical records", e);
            throw new RuntimeException("Failed to perform semantic search", e);
        }
    }

    /**
     * Find clinical guidelines based on patient condition
     */
    public List<ClinicalGuidelineResult> findClinicalGuidelines(String patientCondition, int maxResults) {
        log.info("Searching for clinical guidelines for condition: {}", patientCondition);
        
        try {
            SearchRequest searchRequest = SearchRequest.query(patientCondition)
                    .withTopK(maxResults)
                    .withSimilarityThreshold(0.7);
            
            List<Document> guidelines = vectorStore.similaritySearch(searchRequest);
            
            return guidelines.stream()
                    .filter(doc -> "clinical_guideline".equals(doc.getMetadata().get("type")))
                    .map(this::mapToClinicalGuidelineResult)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("Error searching for clinical guidelines", e);
            throw new RuntimeException("Failed to find clinical guidelines", e);
        }
    }

    /**
     * Check for drug interactions using vector similarity
     */
    public List<DrugInteractionResult> checkDrugInteractions(List<String> medications) {
        log.info("Checking drug interactions for medications: {}", medications);
        
        try {
            String medicationQuery = String.join(" ", medications);
            
            SearchRequest searchRequest = SearchRequest.query(medicationQuery)
                    .withTopK(20)
                    .withSimilarityThreshold(0.8);
            
            List<Document> interactions = vectorStore.similaritySearch(searchRequest);
            
            return interactions.stream()
                    .filter(doc -> "drug_interaction".equals(doc.getMetadata().get("type")))
                    .map(this::mapToDrugInteractionResult)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("Error checking drug interactions", e);
            throw new RuntimeException("Failed to check drug interactions", e);
        }
    }

    /**
     * Add medical record to vector store for future similarity searches
     */
    public void indexMedicalRecord(String recordId, String patientId, String content, Map<String, Object> metadata) {
        log.info("Indexing medical record: {} for patient: {}", recordId, patientId);
        
        try {
            metadata.put("type", "medical_record");
            metadata.put("record_id", recordId);
            metadata.put("patient_id", patientId);
            
            Document document = new Document(content, metadata);
            vectorStore.add(List.of(document));
            
            log.info("Successfully indexed medical record: {}", recordId);
            
        } catch (Exception e) {
            log.error("Error indexing medical record: {}", recordId, e);
            throw new RuntimeException("Failed to index medical record", e);
        }
    }

    /**
     * Perform semantic search for patient cases with similar symptoms
     */
    public List<SimilarCaseResult> findSimilarPatientCases(String symptoms, String demographics, int maxResults) {
        log.info("Searching for similar patient cases with symptoms: {}", symptoms);
        
        try {
            String combinedQuery = symptoms + " " + demographics;
            
            SearchRequest searchRequest = SearchRequest.query(combinedQuery)
                    .withTopK(maxResults)
                    .withSimilarityThreshold(0.75);
            
            List<Document> similarCases = vectorStore.similaritySearch(searchRequest);
            
            return similarCases.stream()
                    .filter(doc -> "patient_case".equals(doc.getMetadata().get("type")))
                    .map(this::mapToSimilarCaseResult)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("Error searching for similar patient cases", e);
            throw new RuntimeException("Failed to find similar patient cases", e);
        }
    }

    private MedicalRecordSearchResult mapToMedicalRecordSearchResult(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return MedicalRecordSearchResult.builder()
                .recordId((String) metadata.get("record_id"))
                .patientId((String) metadata.get("patient_id"))
                .content(document.getContent())
                .similarity((Double) metadata.get("distance"))
                .recordType((String) metadata.get("record_type"))
                .encounterDate((String) metadata.get("encounter_date"))
                .build();
    }

    private ClinicalGuidelineResult mapToClinicalGuidelineResult(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return ClinicalGuidelineResult.builder()
                .guidelineId((String) metadata.get("guideline_id"))
                .guidelineName((String) metadata.get("guideline_name"))
                .recommendation(document.getContent())
                .evidenceLevel((String) metadata.get("evidence_level"))
                .similarity((Double) metadata.get("distance"))
                .build();
    }

    private DrugInteractionResult mapToDrugInteractionResult(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return DrugInteractionResult.builder()
                .interactionId((String) metadata.get("interaction_id"))
                .drugNames((String) metadata.get("drug_names"))
                .interactionType((String) metadata.get("interaction_type"))
                .severity((String) metadata.get("severity"))
                .description(document.getContent())
                .similarity((Double) metadata.get("distance"))
                .build();
    }

    private SimilarCaseResult mapToSimilarCaseResult(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return SimilarCaseResult.builder()
                .caseId((String) metadata.get("case_id"))
                .patientDemographics((String) metadata.get("demographics"))
                .symptoms((String) metadata.get("symptoms"))
                .diagnosis((String) metadata.get("diagnosis"))
                .treatment((String) metadata.get("treatment"))
                .outcome((String) metadata.get("outcome"))
                .similarity((Double) metadata.get("distance"))
                .build();
    }

    // Result DTOs
    public static class MedicalRecordSearchResult {
        public String recordId;
        public String patientId;
        public String content;
        public Double similarity;
        public String recordType;
        public String encounterDate;

        public static MedicalRecordSearchResultBuilder builder() {
            return new MedicalRecordSearchResultBuilder();
        }

        public static class MedicalRecordSearchResultBuilder {
            private MedicalRecordSearchResult result = new MedicalRecordSearchResult();

            public MedicalRecordSearchResultBuilder recordId(String recordId) {
                result.recordId = recordId;
                return this;
            }

            public MedicalRecordSearchResultBuilder patientId(String patientId) {
                result.patientId = patientId;
                return this;
            }

            public MedicalRecordSearchResultBuilder content(String content) {
                result.content = content;
                return this;
            }

            public MedicalRecordSearchResultBuilder similarity(Double similarity) {
                result.similarity = similarity;
                return this;
            }

            public MedicalRecordSearchResultBuilder recordType(String recordType) {
                result.recordType = recordType;
                return this;
            }

            public MedicalRecordSearchResultBuilder encounterDate(String encounterDate) {
                result.encounterDate = encounterDate;
                return this;
            }

            public MedicalRecordSearchResult build() {
                return result;
            }
        }
    }

    public static class ClinicalGuidelineResult {
        public String guidelineId;
        public String guidelineName;
        public String recommendation;
        public String evidenceLevel;
        public Double similarity;

        public static ClinicalGuidelineResultBuilder builder() {
            return new ClinicalGuidelineResultBuilder();
        }

        public static class ClinicalGuidelineResultBuilder {
            private ClinicalGuidelineResult result = new ClinicalGuidelineResult();

            public ClinicalGuidelineResultBuilder guidelineId(String guidelineId) {
                result.guidelineId = guidelineId;
                return this;
            }

            public ClinicalGuidelineResultBuilder guidelineName(String guidelineName) {
                result.guidelineName = guidelineName;
                return this;
            }

            public ClinicalGuidelineResultBuilder recommendation(String recommendation) {
                result.recommendation = recommendation;
                return this;
            }

            public ClinicalGuidelineResultBuilder evidenceLevel(String evidenceLevel) {
                result.evidenceLevel = evidenceLevel;
                return this;
            }

            public ClinicalGuidelineResultBuilder similarity(Double similarity) {
                result.similarity = similarity;
                return this;
            }

            public ClinicalGuidelineResult build() {
                return result;
            }
        }
    }

    public static class DrugInteractionResult {
        public String interactionId;
        public String drugNames;
        public String interactionType;
        public String severity;
        public String description;
        public Double similarity;

        public static DrugInteractionResultBuilder builder() {
            return new DrugInteractionResultBuilder();
        }

        public static class DrugInteractionResultBuilder {
            private DrugInteractionResult result = new DrugInteractionResult();

            public DrugInteractionResultBuilder interactionId(String interactionId) {
                result.interactionId = interactionId;
                return this;
            }

            public DrugInteractionResultBuilder drugNames(String drugNames) {
                result.drugNames = drugNames;
                return this;
            }

            public DrugInteractionResultBuilder interactionType(String interactionType) {
                result.interactionType = interactionType;
                return this;
            }

            public DrugInteractionResultBuilder severity(String severity) {
                result.severity = severity;
                return this;
            }

            public DrugInteractionResultBuilder description(String description) {
                result.description = description;
                return this;
            }

            public DrugInteractionResultBuilder similarity(Double similarity) {
                result.similarity = similarity;
                return this;
            }

            public DrugInteractionResult build() {
                return result;
            }
        }
    }

    public static class SimilarCaseResult {
        public String caseId;
        public String patientDemographics;
        public String symptoms;
        public String diagnosis;
        public String treatment;
        public String outcome;
        public Double similarity;

        public static SimilarCaseResultBuilder builder() {
            return new SimilarCaseResultBuilder();
        }

        public static class SimilarCaseResultBuilder {
            private SimilarCaseResult result = new SimilarCaseResult();

            public SimilarCaseResultBuilder caseId(String caseId) {
                result.caseId = caseId;
                return this;
            }

            public SimilarCaseResultBuilder patientDemographics(String patientDemographics) {
                result.patientDemographics = patientDemographics;
                return this;
            }

            public SimilarCaseResultBuilder symptoms(String symptoms) {
                result.symptoms = symptoms;
                return this;
            }

            public SimilarCaseResultBuilder diagnosis(String diagnosis) {
                result.diagnosis = diagnosis;
                return this;
            }

            public SimilarCaseResultBuilder treatment(String treatment) {
                result.treatment = treatment;
                return this;
            }

            public SimilarCaseResultBuilder outcome(String outcome) {
                result.outcome = outcome;
                return this;
            }

            public SimilarCaseResultBuilder similarity(Double similarity) {
                result.similarity = similarity;
                return this;
            }

            public SimilarCaseResult build() {
                return result;
            }
        }
    }
}