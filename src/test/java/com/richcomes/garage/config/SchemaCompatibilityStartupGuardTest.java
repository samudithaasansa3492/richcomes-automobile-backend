package com.richcomes.garage.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SchemaCompatibilityStartupGuardTest {
    @Test
    void allowsV23SchemaWithRequiredCustomerProofAndChallenge() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(5);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
        assertDoesNotThrow(() -> new SchemaCompatibilityStartupGuard(jdbc).run(null));
        verify(jdbc, times(2)).queryForObject(anyString(), any());
    }

    @Test
    void refusesOldV14SchemaWithNoCustomerProofColumns() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);
        var error = assertThrows(IllegalStateException.class,
                () -> new SchemaCompatibilityStartupGuard(jdbc).run(null));
        assertTrue(error.getMessage().contains("schema is not compatible"));
    }

    @Test
    void refusesPartiallyAppliedOrModifiedV23Schema() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(5);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);
        assertThrows(IllegalStateException.class,
                () -> new SchemaCompatibilityStartupGuard(jdbc).run(null));
    }

    @Test
    void refusesUnavailableSchemaMetadata() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(null);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(null);
        assertThrows(IllegalStateException.class,
                () -> new SchemaCompatibilityStartupGuard(jdbc).run(null));
    }
}
