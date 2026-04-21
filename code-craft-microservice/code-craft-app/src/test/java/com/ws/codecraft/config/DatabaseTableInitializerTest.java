package com.ws.codecraft.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseTableInitializerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData databaseMetaData;

    @Mock
    private ResultSet usageRecordTables;

    @Mock
    private ResultSet dailyStatisticsTables;

    @Test
    void initializeTables_shouldSkipWhenCompatibilitySwitchDisabled() {
        DatabaseTableInitializer initializer = new DatabaseTableInitializer(jdbcTemplate);
        ReflectionTestUtils.setField(initializer, "databaseInitEnabled", false);

        initializer.initializeTables();

        verify(jdbcTemplate, never()).getDataSource();
        verify(jdbcTemplate, never()).execute(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void initializeTables_shouldCreateTablesWhenTheyDoNotExist() throws Exception {
        when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getTables(null, null, "ai_usage_record", null)).thenReturn(usageRecordTables);
        when(databaseMetaData.getTables(null, null, "ai_daily_statistics", null)).thenReturn(dailyStatisticsTables);
        when(usageRecordTables.next()).thenReturn(false);
        when(dailyStatisticsTables.next()).thenReturn(false);

        DatabaseTableInitializer initializer = new DatabaseTableInitializer(jdbcTemplate);
        ReflectionTestUtils.setField(initializer, "databaseInitEnabled", true);

        initializer.initializeTables();

        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS ai_usage_record"));
        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS ai_daily_statistics"));
    }
}
