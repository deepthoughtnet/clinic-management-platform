package com.deepthoughtnet.clinic.platform.contracts.providerintegration.port;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderSummary;

import java.util.List;
import java.util.Optional;

public interface DiscoverCatalogPort {
    Optional<PublicProviderSummary> findPublishedProvider(PublicProviderReference publicReference);

    Optional<PublicProviderSummary> findPublishedPractice(PublicProviderReference publicReference);

    List<PublicProviderSummary> searchPublishedProviders(String query, String city, PublicProfileType publicProfileType);

    List<PublicProviderSummary> searchPublishedPractices(String query, String city, PublicProfileType publicProfileType);
}
