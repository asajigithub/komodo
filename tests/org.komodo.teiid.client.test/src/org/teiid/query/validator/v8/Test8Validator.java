/*
 * JBoss, Home of Professional Open Source.
* See the COPYRIGHT.txt file distributed with this work for information
* regarding copyright ownership. Some portions may be licensed
* to Red Hat, Inc. under one or more contributor license agreements.
*
* This library is free software; you can redistribute it and/or
* modify it under the terms of the GNU Lesser General Public
* License as published by the Free Software Foundation; either
* version 2.1 of the License, or (at your option) any later version.
*
* This library is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this library; if not, write to the Free Software
* Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
* 02110-1301 USA.
*/
package org.teiid.query.validator.v8;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.komodo.spi.query.metadata.QueryMetadataInterface;
import org.komodo.spi.query.sql.lang.Command;
import org.komodo.spi.runtime.version.TeiidVersion;
import org.komodo.spi.runtime.version.DefaultTeiidVersion.Version;
import org.teiid.metadata.Table;
import org.teiid.query.resolver.TCQueryResolver;
import org.teiid.query.sql.AbstractTestFactory;
import org.teiid.query.sql.lang.CommandImpl;
import org.teiid.query.sql.symbol.GroupSymbolImpl;
import org.teiid.query.sql.v8.Test8Factory;
import org.teiid.query.validator.AbstractTestValidator;

/**
 *
 */
@SuppressWarnings( {"javadoc", "nls"} )
public class Test8Validator extends AbstractTestValidator {

    private Test8Factory factory;

    protected Test8Validator(TeiidVersion teiidVersion) {
        super(teiidVersion);
    }

    public Test8Validator() {
        this(Version.TEIID_8_0.get());
    }

    @Override
    protected AbstractTestFactory getFactory() {
        if (factory == null)
            factory = new Test8Factory(getQueryParser());

        return factory;
    }

    // valid variable declared
    @Test
    public void testCreateUpdateProcedure4() {
        String procedure = "FOR EACH ROW ";
        procedure = procedure + "BEGIN\n";
        procedure = procedure + "DECLARE integer var1;\n";
        procedure = procedure + "END\n";

        String userUpdateStr = "UPDATE vm1.g1 SET e1='x'";

        helpValidateProcedure(procedure, userUpdateStr, Table.TriggerEvent.UPDATE);
    }

 // validating AssignmentStatement, more than one project symbol on the
    // command
    @Test
    public void testCreateUpdateProcedure11() {
        String procedure = "FOR EACH ROW ";
        procedure = procedure + "BEGIN\n";
        procedure = procedure + "DECLARE integer var1;\n";
        procedure = procedure + "var1 = Select pm1.g1.e2, pm1.g1.e1 from pm1.g1;\n";
        procedure = procedure + "END\n";

        String userUpdateStr = "UPDATE vm1.g1 SET e1='x'";

        helpFailProcedure(procedure, userUpdateStr, Table.TriggerEvent.UPDATE);
    }

    // validating AssignmentStatement, more than one project symbol on the
    // command
    @Test
    public void testCreateUpdateProcedure12() {
        String procedure = "FOR EACH ROW ";
        procedure = procedure + "BEGIN\n";
        procedure = procedure + "DECLARE integer var1;\n";
        procedure = procedure + "var1 = Select pm1.g1.e2, pm1.g1.e1 from pm1.g1;\n";
        procedure = procedure + "END\n";

        String userUpdateStr = "UPDATE vm1.g1 SET e1='x'";

        helpFailProcedure(procedure, userUpdateStr, Table.TriggerEvent.UPDATE);
    }

    // using aggregate function within a procedure - defect #8394
    @Test
    public void testCreateUpdateProcedure31() {
        String procedure = "FOR EACH ROW ";
        procedure = procedure + "BEGIN\n";
        procedure = procedure + "DECLARE string MaxTran;\n";
        procedure = procedure + "MaxTran = SELECT MAX(e1) FROM pm1.g1;\n";
        procedure = procedure + "END\n";

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1";

        helpValidateProcedure(procedure, userQuery, Table.TriggerEvent.UPDATE);
    }

    // assigning null values to known datatype variable
    @Test
    public void testCreateUpdateProcedure32() {
        String procedure = "FOR EACH ROW ";
        procedure = procedure + "BEGIN\n";
        procedure = procedure + "DECLARE string var;\n";
        procedure = procedure + "var = null;\n";
        procedure = procedure + "END\n";

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1";

        helpValidateProcedure(procedure, userQuery, Table.TriggerEvent.UPDATE);
    }

    @Test
    public void testDefect13643() {
        String procedure = "FOR EACH ROW ";
        procedure = procedure + "BEGIN\n";
        procedure = procedure + "DECLARE integer var1;\n";
        procedure = procedure + "LOOP ON (SELECT * FROM pm1.g1) AS myCursor\n";
        procedure = procedure + "BEGIN\n";
        procedure = procedure + "var1 = SELECT COUNT(*) FROM myCursor;\n";
        procedure = procedure + "END\n";
        procedure = procedure + "END\n";

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1";

        helpFailProcedure(procedure, userQuery, Table.TriggerEvent.UPDATE);
    }

    @Test
    public void testSelectIntoTempGroup() {
        String procedure = "FOR EACH ROW ";
        procedure = procedure + "BEGIN\n";
        procedure = procedure + "SELECT e1, e2, e3, e4 INTO #myTempTable FROM pm1.g2;\n";
        procedure = procedure + "SELECT COUNT(*) FROM #myTempTable;\n";
        procedure = procedure + "END\n";

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1";

        helpValidateProcedure(procedure, userQuery, Table.TriggerEvent.UPDATE);
    }

    /**
     * Defect 24346
     */
    @Test
    public void testInvalidSelectIntoTempGroup() {
        String procedure = "FOR EACH ROW ";
        procedure = procedure + "BEGIN\n";
        procedure = procedure + "SELECT e1, e2, e3, e4 INTO #myTempTable FROM pm1.g2;\n";
        procedure = procedure + "SELECT e1, e2, e3 INTO #myTempTable FROM pm1.g2;\n";
        procedure = procedure + "SELECT COUNT(*) FROM #myTempTable;\n";
        procedure = procedure + "END\n";

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1";

        helpFailProcedure(procedure, userQuery, Table.TriggerEvent.UPDATE);
    }

    @Test
    public void testInvalidSelectIntoTempGroup1() {
        String procedure = "FOR EACH ROW ";
        procedure = procedure + "BEGIN\n";
        procedure = procedure + "create local temporary table #myTempTable (e1 integer);\n";
        procedure = procedure + "SELECT e1 INTO #myTempTable FROM pm1.g2;\n";
        procedure = procedure + "SELECT COUNT(*) FROM #myTempTable;\n";
        procedure = procedure + "END\n";

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1";

        helpFailProcedure(procedure, userQuery, Table.TriggerEvent.UPDATE);
    }

    @Test
    public void testSelectIntoPhysicalGroup() {
        helpValidate("SELECT e1, e2, e3, e4 INTO pm1.g1 FROM pm1.g2", new String[] {}, getMetadataFactory().example1Cached());

        String procedure = "FOR EACH ROW ";
        procedure = procedure + "BEGIN\n";
        procedure = procedure + "SELECT e1, e2, e3, e4 INTO pm1.g1 FROM pm1.g2;\n";
        procedure = procedure + "END\n";

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1";

        helpValidateProcedure(procedure, userQuery, Table.TriggerEvent.UPDATE);
    }

    @Test
    public void testInvalidSelectIntoTooManyElements() {
        helpValidate("SELECT e1, e2, e3, e4, 'val' INTO pm1.g1 FROM pm1.g2", new String[] {"SELECT e1, e2, e3, e4, 'val' INTO pm1.g1 FROM pm1.g2"}, getMetadataFactory().example1Cached()); //$NON-NLS-2$

        String procedure = "FOR EACH ROW ";
        procedure = procedure + "BEGIN\n";
        procedure = procedure + "SELECT e1, e2, e3, e4, 'val' INTO pm1.g1 FROM pm1.g2;\n";
        procedure = procedure + "END\n";

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1";

        helpFailProcedure(procedure, userQuery, Table.TriggerEvent.UPDATE);
    }

    @Test
    public void testInvalidSelectIntoTooFewElements() {
        helpValidate("SELECT e1, e2, e3 INTO pm1.g1 FROM pm1.g2", new String[] {"SELECT e1, e2, e3 INTO pm1.g1 FROM pm1.g2"}, getMetadataFactory().example1Cached()); //$NON-NLS-2$

        String procedure = "FOR EACH ROW ";
        procedure = procedure + "BEGIN\n";
        procedure = procedure + "SELECT e1, e2, e3 INTO pm1.g1 FROM pm1.g2;\n";
        procedure = procedure + "END\n";

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1";

        helpFailProcedure(procedure, userQuery, Table.TriggerEvent.UPDATE);
    }

    @Test
    public void testInvalidSelectIntoIncorrectTypes() {
        helpValidate("SELECT e1, convert(e2, string), e3, e4 INTO pm1.g1 FROM pm1.g2", new String[] {"SELECT e1, convert(e2, string), e3, e4 INTO pm1.g1 FROM pm1.g2"}, getMetadataFactory().example1Cached()); //$NON-NLS-2$

        String procedure = "FOR EACH ROW ";
        procedure = procedure + "BEGIN\n";
        procedure = procedure + "SELECT e1, convert(e2, string), e3, e4 INTO pm1.g1 FROM pm1.g2;\n";
        procedure = procedure + "END\n";

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1";

        helpFailProcedure(procedure, userQuery, Table.TriggerEvent.UPDATE);
    }

    @Test
    public void testInvalidSelectIntoWithStar() {
        helpValidate("SELECT * INTO pm1.g1 FROM pm1.g2, pm1.g1", new String[] {"SELECT * INTO pm1.g1 FROM pm1.g2, pm1.g1"}, getMetadataFactory().example1Cached()); //$NON-NLS-2$

        String procedure = "FOR EACH ROW ";
        procedure = procedure + "BEGIN\n";
        procedure = procedure + "SELECT * INTO pm1.g1 FROM pm1.g2, pm1.g1;\n";
        procedure = procedure + "END\n";

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1";

        helpFailProcedure(procedure, userQuery, Table.TriggerEvent.UPDATE);
    }

    @Test
    public void testSelectIntoVirtualGroup() {
        helpValidate("SELECT e1, e2, e3, e4 INTO vm1.g1 FROM pm1.g2", new String[] {}, getMetadataFactory().example1Cached());

        String procedure = "FOR EACH ROW ";
        procedure = procedure + "BEGIN\n";
        procedure = procedure + "SELECT e1, e2, e3, e4 INTO vm1.g1 FROM pm1.g2;\n";
        procedure = procedure + "END\n";

        String userQuery = "UPDATE vm1.g3 SET x='x' where y = 1";

        helpValidateProcedure(procedure, userQuery, Table.TriggerEvent.UPDATE);
    }

    @Test
    public void testValidateInModeler() throws Exception {
        // SQL is same as pm1.vsp36() in example1 
        String sql = "CREATE VIRTUAL PROCEDURE BEGIN select 1, 2; END";        
        QueryMetadataInterface metadata = getMetadataFactory().example1Cached();
        CommandImpl command = getQueryParser().parseCommand(sql);
        GroupSymbolImpl group = getFactory().newGroupSymbol("pm1.vsp36");
        TCQueryResolver queryResolver = new TCQueryResolver(getTeiidVersion());
        queryResolver.resolveCommand(command, group, Command.TYPE_STORED_PROCEDURE, metadata, true);

        assertEquals(2, command.getResultSetColumns().size());
    }


    @Test
    public void testXMLSerializeEncoding() {
        helpValidate("SELECT xmlserialize(? AS CLOB ENCODING \"UTF-8\")", new String[] {"XMLSERIALIZE(? AS CLOB ENCODING \"UTF-8\")"}, getMetadataFactory().example1Cached()); //$NON-NLS-1$
    }

    @Test
    public void testXMLSerializeEncoding1() {
        helpValidate("SELECT xmlserialize(? AS BLOB ENCODING \"UTF-8\" INCLUDING XMLDECLARATION)", new String[] {}, getMetadataFactory().example1Cached()); //$NON-NLS-1$
    }

    @Test
    public void testXMLSerializeEncoding2() {
        helpValidate("SELECT xmlserialize(? AS BLOB ENCODING \"UTF-75\" INCLUDING XMLDECLARATION)", new String[] {"XMLSERIALIZE(? AS BLOB ENCODING \"UTF-75\" INCLUDING XMLDECLARATION)"}, getMetadataFactory().example1Cached()); //$NON-NLS-1$
    }

    @Test
    public void testUpdateError() {
        String userUpdateStr = "UPDATE vm1.g2 SET e1='x'";

        helpValidate(userUpdateStr, new String[] {"vm1.g2", "UPDATE vm1.g2 SET e1 = 'x'"}, getMetadataFactory().example1Cached());
    }

    @Test
    public void testInsertError() {
        String userUpdateStr = "INSERT into vm1.g2 (e1) values ('x')";

        helpValidate(userUpdateStr,
                     new String[] {"vm1.g2", "INSERT INTO vm1.g2 (e1) VALUES ('x')"}, getMetadataFactory().example1Cached());
    }

    @Test
    public void testMergeNoKey() {
        String userUpdateStr = "MERGE into pm1.g2 (e1) values ('x')";

        helpValidate(userUpdateStr, new String[] {"MERGE INTO pm1.g2 (e1) VALUES ('x')"}, getMetadataFactory().example1Cached());
    }

    @Test
    public void testDeleteError() {
        String userUpdateStr = "DELETE from vm1.g2 where e1='x'";

        helpValidate(userUpdateStr, new String[] {"vm1.g2"}, getMetadataFactory().example1Cached());
    }

    @Test
    public void testJsonArrayBlob() {
        String sql = "select jsonArray(to_bytes('hello', 'us-ascii'))";

        helpValidate(sql, new String[] {"jsonArray(to_bytes('hello', 'us-ascii'))"}, getMetadataFactory().example1Cached());
    }

    @Test
    public void testJsonArrayClob() {
        String sql = "select jsonArray(cast('hello' as clob))";

        helpValidate(sql, new String[] {}, getMetadataFactory().example1Cached());
    }

    @Test
    public void testJsonObject() {
        String sql = "select jsonObject(to_bytes('hello', 'us-ascii'))";

        helpValidate(sql, new String[] {"JSONOBJECT(to_bytes('hello', 'us-ascii'))"}, getMetadataFactory().example1Cached());
    }

    @Test
    public void testWithValidation() {
        String sql = "with a as (select jsonObject(to_bytes('hello', 'us-ascii')) as x) select a.x from a";

        helpValidate(sql, new String[] {"JSONOBJECT(to_bytes('hello', 'us-ascii'))"}, getMetadataFactory().example1Cached());
    }

    @Test
    public void testInsertIntoVirtualWithQueryExpression() {

        QueryMetadataInterface qmi = getMetadataFactory().example1();

        String sql = "insert into vm1.g1 (e1, e2, e3, e4) select * from pm1.g1";

        helpValidate(sql, new String[] {}, qmi);

    }

    @Test
    public void testObjectTablePassing() {
        helpValidate("select * from objecttable('x' passing 'a' columns c integer 'row') as x",
                     new String[] {"OBJECTTABLE('x' PASSING 'a' COLUMNS c integer 'row') AS x"},
                     getMetadataFactory().example1Cached());
    }

    @Test
    public void testObjectTablePassingSameName() {
        helpValidate("select * from objecttable('x' passing 'a' AS X, 'b' AS x columns c integer 'row') as x",
                     new String[] {"OBJECTTABLE('x' PASSING 'a' AS X, 'b' AS x COLUMNS c integer 'row') AS x"},
                     getMetadataFactory().example1Cached());
    }

    @Test
    public void testObjectTableLanguage() {
        helpValidate("select * from objecttable(language 'foo!' 'x' columns c integer 'row') as x",
                     new String[] {"OBJECTTABLE(LANGUAGE 'foo!' 'x' COLUMNS c integer 'row') AS x"},
                     getMetadataFactory().example1Cached());
    }

    @Test
    public void testObjectTableScript() {
        helpValidate("select * from objecttable('this. is not valid' columns c integer 'row') as x",
                     new String[] {"OBJECTTABLE('this. is not valid' COLUMNS c integer 'row') AS x"},
                     getMetadataFactory().example1Cached());
    }

    @Test
    public void testTextTableFixedSelector() {
        helpValidate("SELECT * from texttable(null SELECTOR 'a' columns x string width 1) as x",
                     new String[] {},
                     getMetadataFactory().exampleBQTCached());
    }
}
