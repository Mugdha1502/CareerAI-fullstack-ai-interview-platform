package com.careerai.backend.migration;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.collection.AbstractCollectionPersister;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every table and column the JPA entities map must be created by a Flyway script in db/migration,
 * otherwise the prod profile (ddl-auto=validate) would fail at startup. Adding an entity field
 * without a migration makes this test fail. (Runs on the in-memory test database.)
 */
@SpringBootTest
@ActiveProfiles("test")
class MigrationCoverageTest {

    private static final Pattern CREATE_TABLE = Pattern.compile("CREATE TABLE (?:IF NOT EXISTS )?`(\\w+)` \\((.*?)\\n\\)", Pattern.DOTALL);
    private static final Pattern COLUMN = Pattern.compile("^\\s*`(\\w+)`\\s", Pattern.MULTILINE);
    private static final Pattern VERSIONED = Pattern.compile("V(\\d+)__[a-z0-9_]+\\.sql");

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Map<String, Set<String>> migratedTables() throws Exception {
        Map<String, Set<String>> tables = new HashMap<>();
        for (Resource script : new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*.sql")) {
            String sql = script.getContentAsString(StandardCharsets.UTF_8);
            Matcher table = CREATE_TABLE.matcher(sql);
            while (table.find()) {
                Set<String> columns = new HashSet<>();
                Matcher column = COLUMN.matcher(table.group(2));
                while (column.find()) columns.add(column.group(1).toLowerCase(Locale.ROOT));
                tables.put(table.group(1).toLowerCase(Locale.ROOT), columns);
            }
        }
        return tables;
    }

    private Map<String, Set<String>> mappedTables() {
        Map<String, Set<String>> mapped = new TreeMap<>();
        var metamodel = entityManagerFactory.unwrap(SessionFactoryImplementor.class).getMappingMetamodel();
        metamodel.forEachEntityDescriptor(descriptor -> {
            AbstractEntityPersister persister = (AbstractEntityPersister) descriptor;
            Set<String> columns = mapped.computeIfAbsent(name(persister.getTableName()), key -> new HashSet<>());
            Arrays.stream(persister.getIdentifierColumnNames()).map(MigrationCoverageTest::name).forEach(columns::add);
            for (String property : persister.getPropertyNames()) {
                Arrays.stream(persister.getPropertyColumnNames(property)).map(MigrationCoverageTest::name).forEach(columns::add);
            }
        });
        metamodel.forEachCollectionDescriptor(descriptor -> {
            AbstractCollectionPersister persister = (AbstractCollectionPersister) descriptor;
            if (persister.isOneToMany()) return; // stored in the child entity's table
            Set<String> columns = mapped.computeIfAbsent(name(persister.getTableName()), key -> new HashSet<>());
            for (String[] group : new String[][] { persister.getKeyColumnNames(), persister.getElementColumnNames(), persister.getIndexColumnNames() }) {
                if (group != null) Arrays.stream(group).filter(c -> c != null).map(MigrationCoverageTest::name).forEach(columns::add);
            }
        });
        return mapped;
    }

    private static String name(String identifier) {
        String plain = identifier.replace("`", "").replace("\"", "");
        return plain.substring(plain.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    @Test
    void everyMappedTableAndColumnIsCreatedByAMigration() throws Exception {
        Map<String, Set<String>> migrated = migratedTables();
        Map<String, Set<String>> mapped = mappedTables();
        assertThat(mapped).isNotEmpty();
        for (var entry : mapped.entrySet()) {
            assertThat(migrated).as("migration creating table %s", entry.getKey()).containsKey(entry.getKey());
            assertThat(migrated.get(entry.getKey())).as("columns of %s", entry.getKey()).containsAll(entry.getValue());
        }
        assertThat(migrated.keySet()).as("migrations create only mapped tables").isEqualTo(mapped.keySet());
        assertThat(migrated).containsKey("resume_file_blobs");
    }

    @Test
    void migrationsAreVersionedWithoutGapsOrDestructiveStatements() throws Exception {
        Set<Integer> versions = new HashSet<>();
        for (Resource script : new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*.sql")) {
            Matcher versioned = VERSIONED.matcher(script.getFilename());
            assertThat(versioned.matches()).as("file name %s", script.getFilename()).isTrue();
            assertThat(versions.add(Integer.parseInt(versioned.group(1)))).as("duplicate version in %s", script.getFilename()).isTrue();
            String sql = script.getContentAsString(StandardCharsets.UTF_8).toUpperCase(Locale.ROOT);
            assertThat(sql).as(script.getFilename()).doesNotContain("DROP TABLE").doesNotContain("DROP DATABASE")
                .doesNotContain("TRUNCATE").doesNotContain("DELETE FROM").doesNotContain("DROP COLUMN");
        }
        assertThat(versions).isNotEmpty();
        for (int v = 1; v <= versions.size(); v++) assertThat(versions).as("version %s present", v).contains(v);
    }
}
