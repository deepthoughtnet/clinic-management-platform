package com.deepthoughtnet.clinic.api.config.db;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class DatabaseRuntimeSafetyValidator implements ApplicationRunner {
    private final Environment environment;
    private final DataSource dataSource;

    public DatabaseRuntimeSafetyValidator(Environment environment, DataSource dataSource) {
        this.environment = environment;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        DatabaseSafetyGuard.assertRuntimeDatabase(environment, dataSource);
    }
}
