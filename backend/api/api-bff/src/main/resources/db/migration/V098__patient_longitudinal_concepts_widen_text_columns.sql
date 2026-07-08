alter table patient_longitudinal_concepts
    alter column source_document_title type text,
    alter column concept_label type text,
    alter column value_unit type varchar(64);
