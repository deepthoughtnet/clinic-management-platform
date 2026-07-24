package com.deepthoughtnet.clinic.api.platform.commercialcatalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CommercialCatalogArchitectureTest {
    private static final Path MODULE_ROOT = resolveModuleRoot();
    private static final Path REPO_ROOT = MODULE_ROOT.getParent().getParent();
    private static final Path API_CATALOG_ROOT = MODULE_ROOT.resolve("src/main/java/com/deepthoughtnet/clinic/api/platform/commercialcatalog");
    private static final Path DOMAIN_CATALOG_ROOT = REPO_ROOT.resolve("domains/commercial-domain/src/main/java/com/deepthoughtnet/clinic/commercial/catalog");

    @Test
    void apiPackageMustNotContainPersistenceFilesOrPackages() throws IOException {
        assertThat(Files.exists(API_CATALOG_ROOT.resolve("db"))).isFalse();

        try (Stream<Path> stream = Files.walk(API_CATALOG_ROOT)) {
            List<Path> offenders = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        return fileName.endsWith("Entity.java") || fileName.endsWith("Repository.java");
                    })
                    .toList();
            assertThat(offenders).isEmpty();
        }

        try (Stream<Path> stream = Files.walk(API_CATALOG_ROOT)) {
            List<Path> offenders = stream
                    .filter(Files::isDirectory)
                    .filter(path -> {
                        String name = path.getFileName() == null ? "" : path.getFileName().toString();
                        return name.equals("db") || name.equals("entity") || name.equals("repository") || name.equals("persistence");
                    })
                    .toList();
            assertThat(offenders).isEmpty();
        }
    }

    @Test
    void apiControllerAndAdapterMustNotReferenceRepositoryTypes() {
        assertThat(containsRepositoryOrEntityType(CommercialCatalogController.class)).isFalse();
        assertThat(containsRepositoryOrEntityType(CommercialCatalogApiService.class)).isFalse();
    }

    @Test
    void commercialDomainSourcesMustNotReferenceApiPackages() throws IOException {
        try (Stream<Path> stream = Files.walk(DOMAIN_CATALOG_ROOT)) {
            List<Path> offenders = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8)
                                    .contains("com.deepthoughtnet.clinic.api.platform.commercialcatalog");
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .toList();
            assertThat(offenders).isEmpty();
        }
    }

    private boolean containsRepositoryOrEntityType(Class<?> type) {
        if (type.getPackageName().contains(".db.")) {
            return true;
        }
        for (var field : type.getDeclaredFields()) {
            if (isForbiddenType(field.getType())) {
                return true;
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            for (Class<?> parameterType : constructor.getParameterTypes()) {
                if (isForbiddenType(parameterType)) {
                    return true;
                }
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (isForbiddenType(method.getReturnType())) {
                return true;
            }
            for (Class<?> parameterType : method.getParameterTypes()) {
                if (isForbiddenType(parameterType)) {
                    return true;
                }
            }
            for (Type genericParameter : method.getGenericParameterTypes()) {
                if (genericParameter instanceof ParameterizedType parameterizedType) {
                    for (Type argument : parameterizedType.getActualTypeArguments()) {
                        if (argument.getTypeName().contains(".db.")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isForbiddenType(Class<?> type) {
        String name = type.getName();
        return name.contains(".db.") || name.endsWith("Repository") || name.endsWith("Entity");
    }

    private static Path resolveModuleRoot() {
        try {
            Path location = Path.of(CommercialCatalogArchitectureTest.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            if (location.getFileName() != null && location.getFileName().toString().equals("test-classes")) {
                return location.getParent().getParent().toAbsolutePath().normalize();
            }
            if (location.getFileName() != null && location.getFileName().toString().equals("classes")) {
                return location.getParent().getParent().toAbsolutePath().normalize();
            }
            return Path.of("").toAbsolutePath().normalize();
        } catch (URISyntaxException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
