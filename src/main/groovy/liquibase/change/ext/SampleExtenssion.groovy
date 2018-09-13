package liquibase.change.ext

import liquibase.change.*
import liquibase.database.Database
import liquibase.statement.SqlStatement

/**
 * A change that does nothing, this is used to test loading of dynamic extensions
 */
@DatabaseChange(name = "sampleExtension", description = "Sample extension to loading of extension", priority = ChangeMetaData.PRIORITY_DEFAULT, appliesTo = "table")
public class SampleExtenssion extends AbstractChange implements ChangeWithColumns<ColumnConfig> {

	private String name;

	String getName() {
		return name
	}

	void setName(String name) {
		this.name = name
	}

	@Override
	String getConfirmationMessage() {
		return null
	}

	@Override
	public SqlStatement[] generateStatements(Database database) {
		return null
	}

	@Override
	void addColumn(ColumnConfig column) {

	}

	@Override
	List<ColumnConfig> getColumns() {
		return null
	}

	@Override
	void setColumns(List<ColumnConfig> columns) {

	}
}
