package demo;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.LabelExpression;

import org.junit.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class LiquibaseGroovyDslTest {

    private static final String URL = "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";
    private static final String ID = "ID";
    private static final String NAME = "NAME";
    private static final String TABLE_NAME_PATTERN = "DEMO_ITEM";
    private static final String COLUMN_NAME = "COLUMN_NAME";
    private static final String CHANGELOG_GROOVY = "db/changelog.groovy";

    @Test
    public void liquibaseGroovyDslCreatesTableInH2() throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
            JdbcConnection jdbcConnection = new JdbcConnection(connection);
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(jdbcConnection);
            ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor();

            Liquibase liquibase = new Liquibase(CHANGELOG_GROOVY, resourceAccessor, database);

            liquibase.update(new Contexts(), new LabelExpression());

            // Verify table exists and has expected columns
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet tables = meta.getTables(null, null, TABLE_NAME_PATTERN, null)) {
                assertTrue("Table DEMO_ITEM should exist", tables.next());
            }

            List<String> columnNames = new ArrayList<>();
            try (ResultSet columns = meta.getColumns(null, null, TABLE_NAME_PATTERN, null)) {
                while (columns.next()) {
                    columnNames.add(columns.getString(COLUMN_NAME));
                }
            }

            assertTrue("Should have id column", columnNames.contains(ID));
            assertTrue("Should have name column", columnNames.contains(NAME));
        }
    }
}
