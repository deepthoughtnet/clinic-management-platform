package com.deepthoughtnet.clinic.api.carepilot.notificationsettings.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.carepilot.notificationsettings.db.TenantNotificationSettingsEntity;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.model.NotificationChannelPreference;
import java.lang.reflect.Field;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

class TenantNotificationSettingsPostgresMappingTest {

    @Test
    void notificationPolicyJsonFieldUsesHibernateJsonTyping() throws Exception {
        Field field = TenantNotificationSettingsEntity.class.getDeclaredField("notificationPolicyJson");

        JdbcTypeCode jdbcTypeCode = field.getAnnotation(JdbcTypeCode.class);

        assertThat(jdbcTypeCode).isNotNull();
        assertThat(jdbcTypeCode.value()).isEqualTo(SqlTypes.JSON);
        assertThat(field.getAnnotation(jakarta.persistence.Column.class).columnDefinition()).isEqualTo("jsonb");
    }

    @Test
    void nullNotificationPolicyJsonNormalizesToEmptyObjectOnUpdate() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        TenantNotificationSettingsEntity entity = TenantNotificationSettingsEntity.createDefault(tenantId, actorId);
        entity.updateFrom(
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                LocalTime.of(20, 0),
                LocalTime.of(8, 0),
                "UTC",
                NotificationChannelPreference.EMAIL,
                NotificationChannelPreference.IN_APP,
                false,
                true,
                true,
                5,
                null,
                actorId
        );

        assertThat(entity.getNotificationPolicyJson()).isEqualTo("{}");
    }
}
