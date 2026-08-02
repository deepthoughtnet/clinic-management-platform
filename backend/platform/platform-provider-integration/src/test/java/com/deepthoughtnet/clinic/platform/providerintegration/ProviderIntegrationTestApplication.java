package com.deepthoughtnet.clinic.platform.providerintegration;

import com.deepthoughtnet.clinic.platform.providerintegration.db.PublicClinicPlatformLinkEntity;
import com.deepthoughtnet.clinic.platform.providerintegration.db.PublicClinicPlatformLinkRepository;
import com.deepthoughtnet.clinic.platform.providerintegration.db.PublicDoctorPracticePlatformLinkEntity;
import com.deepthoughtnet.clinic.platform.providerintegration.db.PublicDoctorPracticePlatformLinkRepository;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.deepthoughtnet.clinic.platform.providerintegration")
@EntityScan(basePackageClasses = {
        PublicClinicPlatformLinkEntity.class,
        PublicDoctorPracticePlatformLinkEntity.class
})
@EnableJpaRepositories(basePackageClasses = {
        PublicClinicPlatformLinkRepository.class,
        PublicDoctorPracticePlatformLinkRepository.class
})
public class ProviderIntegrationTestApplication {
}
