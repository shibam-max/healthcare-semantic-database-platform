-- Healthcare Platform Database Schema with Vector Support
-- This migration creates the foundation for a HIPAA-compliant healthcare platform

-- Enable vector extension for semantic search
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create healthcare schema
CREATE SCHEMA IF NOT EXISTS healthcare_vectors;

-- Patient Management Tables
CREATE TABLE patients (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    medical_record_number VARCHAR(50) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender VARCHAR(20),
    phone_number VARCHAR(20),
    email VARCHAR(255),
    address JSONB,
    emergency_contact JSONB,
    insurance_info JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version INTEGER DEFAULT 1
);

-- Provider Network Tables
CREATE TABLE healthcare_providers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    npi_number VARCHAR(20) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    specialty VARCHAR(100) NOT NULL,
    sub_specialty VARCHAR(100),
    license_number VARCHAR(50),
    license_state VARCHAR(2),
    practice_location JSONB,
    contact_info JSONB,
    credentials JSONB,
    network_status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Medical Records with Vector Embeddings
CREATE TABLE medical_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    provider_id UUID NOT NULL REFERENCES healthcare_providers(id),
    record_type VARCHAR(50) NOT NULL, -- DIAGNOSIS, TREATMENT, LAB_RESULT, etc.
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    structured_data JSONB,
    embedding vector(1536), -- OpenAI embedding dimension
    icd_10_codes TEXT[],
    cpt_codes TEXT[],
    encounter_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    confidentiality_level VARCHAR(20) DEFAULT 'NORMAL' -- NORMAL, RESTRICTED, CONFIDENTIAL
);

-- Clinical Decision Support
CREATE TABLE clinical_guidelines (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    guideline_name VARCHAR(255) NOT NULL,
    condition_codes TEXT[],
    recommendation TEXT NOT NULL,
    evidence_level VARCHAR(20),
    embedding vector(1536),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Drug Interaction Database
CREATE TABLE medications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    drug_name VARCHAR(255) NOT NULL,
    generic_name VARCHAR(255),
    ndc_number VARCHAR(20),
    drug_class VARCHAR(100),
    description TEXT,
    contraindications TEXT,
    side_effects TEXT,
    embedding vector(1536),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Patient Medications
CREATE TABLE patient_medications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    medication_id UUID NOT NULL REFERENCES medications(id),
    prescribed_by UUID NOT NULL REFERENCES healthcare_providers(id),
    dosage VARCHAR(100),
    frequency VARCHAR(100),
    start_date DATE,
    end_date DATE,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Audit Trail for HIPAA Compliance
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(100) NOT NULL,
    user_role VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id UUID,
    patient_id UUID,
    ip_address INET,
    user_agent TEXT,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    details JSONB
);

-- Vector Indexes for Performance
CREATE INDEX idx_medical_records_embedding ON medical_records USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_clinical_guidelines_embedding ON clinical_guidelines USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_medications_embedding ON medications USING hnsw (embedding vector_cosine_ops);

-- Regular Indexes
CREATE INDEX idx_patients_mrn ON patients(medical_record_number);
CREATE INDEX idx_patients_name ON patients(last_name, first_name);
CREATE INDEX idx_providers_npi ON healthcare_providers(npi_number);
CREATE INDEX idx_providers_specialty ON healthcare_providers(specialty);
CREATE INDEX idx_medical_records_patient ON medical_records(patient_id);
CREATE INDEX idx_medical_records_provider ON medical_records(provider_id);
CREATE INDEX idx_medical_records_date ON medical_records(encounter_date);
CREATE INDEX idx_medical_records_type ON medical_records(record_type);
CREATE INDEX idx_audit_logs_user ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp);
CREATE INDEX idx_audit_logs_patient ON audit_logs(patient_id);

-- Row Level Security for HIPAA Compliance
ALTER TABLE patients ENABLE ROW LEVEL SECURITY;
ALTER TABLE medical_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE patient_medications ENABLE ROW LEVEL SECURITY;

-- Create policies (examples - would need to be customized based on application roles)
CREATE POLICY patient_access_policy ON patients
    FOR ALL TO healthcare_app_role
    USING (true); -- Application will handle fine-grained access control

CREATE POLICY medical_records_access_policy ON medical_records
    FOR ALL TO healthcare_app_role
    USING (true); -- Application will handle fine-grained access control

-- Functions for vector similarity search
CREATE OR REPLACE FUNCTION find_similar_medical_records(
    query_embedding vector(1536),
    similarity_threshold float DEFAULT 0.8,
    max_results int DEFAULT 50
)
RETURNS TABLE (
    id UUID,
    patient_id UUID,
    title VARCHAR(255),
    content TEXT,
    similarity_score float
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        mr.id,
        mr.patient_id,
        mr.title,
        mr.content,
        1 - (mr.embedding <=> query_embedding) as similarity_score
    FROM medical_records mr
    WHERE 1 - (mr.embedding <=> query_embedding) > similarity_threshold
    ORDER BY mr.embedding <=> query_embedding
    LIMIT max_results;
END;
$$ LANGUAGE plpgsql;

-- Function for clinical decision support
CREATE OR REPLACE FUNCTION get_clinical_recommendations(
    condition_embedding vector(1536),
    similarity_threshold float DEFAULT 0.7
)
RETURNS TABLE (
    guideline_name VARCHAR(255),
    recommendation TEXT,
    evidence_level VARCHAR(20),
    similarity_score float
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        cg.guideline_name,
        cg.recommendation,
        cg.evidence_level,
        1 - (cg.embedding <=> condition_embedding) as similarity_score
    FROM clinical_guidelines cg
    WHERE 1 - (cg.embedding <=> condition_embedding) > similarity_threshold
    ORDER BY cg.embedding <=> condition_embedding
    LIMIT 10;
END;
$$ LANGUAGE plpgsql;

-- Insert sample healthcare providers
INSERT INTO healthcare_providers (npi_number, first_name, last_name, specialty, license_number, license_state, practice_location, contact_info) VALUES
('1234567890', 'Dr. Sarah', 'Johnson', 'Cardiology', 'MD123456', 'CA', '{"address": "123 Medical Center Dr", "city": "Los Angeles", "state": "CA", "zip": "90210"}', '{"phone": "555-0101", "email": "sarah.johnson@hospital.com"}'),
('2345678901', 'Dr. Michael', 'Chen', 'Internal Medicine', 'MD234567', 'CA', '{"address": "456 Health Plaza", "city": "San Francisco", "state": "CA", "zip": "94102"}', '{"phone": "555-0102", "email": "michael.chen@clinic.com"}'),
('3456789012', 'Dr. Emily', 'Rodriguez', 'Pediatrics', 'MD345678', 'NY', '{"address": "789 Children Way", "city": "New York", "state": "NY", "zip": "10001"}', '{"phone": "555-0103", "email": "emily.rodriguez@pediatrics.com"}');

-- Insert sample clinical guidelines
INSERT INTO clinical_guidelines (guideline_name, condition_codes, recommendation, evidence_level) VALUES
('Hypertension Management', ARRAY['I10', 'I11.9'], 'Lifestyle modifications including diet and exercise. Consider ACE inhibitors for initial pharmacological treatment.', 'A'),
('Diabetes Type 2 Management', ARRAY['E11.9', 'E11.65'], 'Metformin as first-line therapy. Monitor HbA1c every 3-6 months. Target HbA1c <7% for most adults.', 'A'),
('Chest Pain Evaluation', ARRAY['R06.02', 'R50.9'], 'Obtain ECG, chest X-ray, and cardiac enzymes. Consider stress testing if stable angina suspected.', 'B');

-- Insert sample medications
INSERT INTO medications (drug_name, generic_name, ndc_number, drug_class, description, contraindications, side_effects) VALUES
('Lisinopril', 'lisinopril', '0093-1530-01', 'ACE Inhibitor', 'Used to treat high blood pressure and heart failure', 'Pregnancy, angioedema history', 'Dry cough, hyperkalemia, angioedema'),
('Metformin', 'metformin hydrochloride', '0093-7267-01', 'Biguanide', 'First-line treatment for type 2 diabetes', 'Severe kidney disease, metabolic acidosis', 'Nausea, diarrhea, lactic acidosis (rare)'),
('Atorvastatin', 'atorvastatin calcium', '0071-0155-23', 'Statin', 'Used to lower cholesterol and reduce cardiovascular risk', 'Active liver disease, pregnancy', 'Muscle pain, liver enzyme elevation');

COMMIT;