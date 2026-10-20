CREATE TABLE communication_channels (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    clinic_id UUID NULL,
    location_id UUID NULL,
    channel_type VARCHAR(24) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    address VARCHAR(128) NOT NULL,
    normalized_address VARCHAR(32) NOT NULL,
    display_name VARCHAR(160) NULL,
    trunk VARCHAR(128) NULL,
    provider_config_ref VARCHAR(256) NULL,
    enabled BOOLEAN NOT NULL,
    inbound_enabled BOOLEAN NOT NULL,
    outbound_enabled BOOLEAN NOT NULL,
    default_outbound BOOLEAN NOT NULL,
    priority INTEGER NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_comm_channels_normalized_address ON communication_channels (normalized_address);
CREATE INDEX ix_comm_channels_tenant_type ON communication_channels (tenant_id, channel_type);
CREATE INDEX ix_comm_channels_tenant_clinic ON communication_channels (tenant_id, clinic_id);
CREATE INDEX ix_comm_channels_tenant_clinic_location ON communication_channels (tenant_id, clinic_id, location_id);

CREATE UNIQUE INDEX uq_comm_channels_active_inbound_did
    ON communication_channels (normalized_address, channel_type)
    WHERE enabled = TRUE AND inbound_enabled = TRUE AND status = 'ACTIVE';

CREATE UNIQUE INDEX uq_comm_channels_default_tenant
    ON communication_channels (tenant_id, channel_type)
    WHERE enabled = TRUE AND outbound_enabled = TRUE AND default_outbound = TRUE
      AND status = 'ACTIVE' AND clinic_id IS NULL AND location_id IS NULL;

CREATE UNIQUE INDEX uq_comm_channels_default_clinic
    ON communication_channels (tenant_id, channel_type, clinic_id)
    WHERE enabled = TRUE AND outbound_enabled = TRUE AND default_outbound = TRUE
      AND status = 'ACTIVE' AND clinic_id IS NOT NULL AND location_id IS NULL;

CREATE UNIQUE INDEX uq_comm_channels_default_location
    ON communication_channels (tenant_id, channel_type, clinic_id, location_id)
    WHERE enabled = TRUE AND outbound_enabled = TRUE AND default_outbound = TRUE
      AND status = 'ACTIVE' AND location_id IS NOT NULL;
