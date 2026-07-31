alter table if exists discover_provider_applications
    alter column email drop not null;

alter table if exists discover_provider_contact_verifications
    alter column email_normalized drop not null;
