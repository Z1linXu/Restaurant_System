package com.restaurant.system.printing.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

class PrintJobEntitySchemaContractTest {

    @Test
    void printingRuleFingerprintUsesPostgresCharJdbcType() throws NoSuchFieldException {
        Field field = PrintJob.class.getDeclaredField("printingRuleFingerprint");

        JdbcTypeCode jdbcTypeCode = field.getAnnotation(JdbcTypeCode.class);

        assertThat(jdbcTypeCode).isNotNull();
        assertThat(jdbcTypeCode.value()).isEqualTo(SqlTypes.CHAR);
    }
}
