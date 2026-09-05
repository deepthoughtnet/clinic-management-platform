alter table patient_longitudinal_concepts
    add column if not exists original_value_text text,
    add column if not exists original_value_unit varchar(64),
    add column if not exists original_reference_range text,
    add column if not exists original_flag varchar(32),
    add column if not exists reviewed_reference_range text,
    add column if not exists reviewed_flag varchar(32),
    add column if not exists review_decision varchar(32);

update patient_longitudinal_concepts
set original_value_text = value_text,
    original_value_unit = value_unit
where original_value_text is null;
