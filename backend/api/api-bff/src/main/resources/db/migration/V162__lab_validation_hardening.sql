CREATE UNIQUE INDEX uq_lab_tests_tenant_code_ci
    ON lab_tests (tenant_id, lower(btrim(test_code)))
    WHERE test_code IS NOT NULL;

CREATE UNIQUE INDEX uq_lab_tests_tenant_name_ci
    ON lab_tests (tenant_id, lower(btrim(test_name)));

CREATE UNIQUE INDEX uq_lab_test_parameters_tenant_name_ci
    ON lab_test_parameters (tenant_id, lab_test_id, lower(btrim(parameter_name)));

ALTER TABLE lab_tests
    ADD CONSTRAINT chk_lab_tests_tenant_price_override_non_negative
        CHECK (tenant_price_override IS NULL OR tenant_price_override >= 0),
    ADD CONSTRAINT chk_lab_tests_display_order_non_negative
        CHECK (display_order IS NULL OR display_order >= 0);

ALTER TABLE lab_category_settings
    ADD CONSTRAINT chk_lab_category_settings_display_order_non_negative
        CHECK (display_order IS NULL OR display_order >= 0);
