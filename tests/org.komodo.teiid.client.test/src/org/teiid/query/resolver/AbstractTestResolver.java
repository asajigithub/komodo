/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package org.teiid.query.resolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.teiid.api.exception.query.QueryResolverException;
import org.teiid.core.types.DefaultDataTypeManager;
import org.komodo.spi.query.metadata.QueryMetadataInterface;
import org.komodo.spi.query.sql.lang.SPParameter;
import org.komodo.spi.runtime.version.TeiidVersion;
import org.komodo.spi.udf.FunctionLibrary;
import org.teiid.metadata.ColumnSet;
import org.teiid.metadata.MetadataStore;
import org.teiid.metadata.Procedure;
import org.teiid.metadata.ProcedureParameter;
import org.teiid.metadata.Schema;
import org.teiid.metadata.Table;
import org.teiid.metadata.Table.TriggerEvent;
import org.teiid.query.function.TCFunctionDescriptor;
import org.teiid.query.function.DefaultFunctionLibrary;
import org.teiid.query.function.FunctionTree;
import org.teiid.query.mapping.relational.TCQueryNode;
import org.teiid.query.metadata.TempMetadataID;
import org.teiid.query.metadata.TempMetadataStore;
import org.teiid.query.metadata.TransformationMetadata;
import org.teiid.query.optimizer.FakeFunctionMetadataSource;
import org.teiid.query.sql.lang.CommandImpl;
import org.teiid.query.sql.lang.CompareCriteriaImpl;
import org.teiid.query.sql.lang.CriteriaImpl;
import org.teiid.query.sql.lang.CriteriaOperator;
import org.teiid.query.sql.lang.CriteriaOperator.Operator;
import org.teiid.query.sql.lang.FromImpl;
import org.teiid.query.sql.lang.InsertImpl;
import org.teiid.query.sql.lang.BaseLanguageObject;
import org.teiid.query.sql.lang.OrderByImpl;
import org.teiid.query.sql.lang.ProcedureContainer;
import org.teiid.query.sql.lang.QueryImpl;
import org.teiid.query.sql.lang.SPParameterImpl;
import org.teiid.query.sql.lang.SelectImpl;
import org.teiid.query.sql.lang.SetCriteriaImpl;
import org.teiid.query.sql.lang.SetQueryImpl;
import org.teiid.query.sql.lang.StoredProcedureImpl;
import org.teiid.query.sql.lang.SubqueryFromClauseImpl;
import org.teiid.query.sql.lang.SubquerySetCriteriaImpl;
import org.teiid.query.sql.lang.UpdateImpl;
import org.teiid.query.sql.navigator.DeepPreOrderNavigator;
import org.teiid.query.sql.symbol.ConstantImpl;
import org.teiid.query.sql.symbol.ElementSymbolImpl;
import org.teiid.query.sql.symbol.BaseExpression;
import org.teiid.query.sql.symbol.FunctionImpl;
import org.teiid.query.sql.symbol.GroupSymbolImpl;
import org.teiid.query.sql.symbol.ReferenceImpl;
import org.teiid.query.sql.symbol.SymbolImpl;
import org.teiid.query.sql.symbol.XMLQueryImpl;
import org.teiid.query.sql.util.SymbolMap;
import org.teiid.query.sql.visitor.CommandCollectorVisitorImpl;
import org.teiid.query.sql.visitor.ElementCollectorVisitorImpl;
import org.teiid.query.sql.visitor.FunctionCollectorVisitorImpl;
import org.teiid.query.sql.visitor.GroupCollectorVisitorImpl;
import org.teiid.query.unittest.TimestampUtil;
import org.teiid.runtime.client.TeiidClientException;

@SuppressWarnings( {"nls" , "javadoc"})
public abstract class AbstractTestResolver extends AbstractTest {

    protected QueryMetadataInterface metadata;

    /**
     * @param teiidVersion
     */
    public AbstractTestResolver(TeiidVersion teiidVersion) {
        super(teiidVersion);
    }

    @Before
    public void setUp() {
        metadata = getMetadataFactory().example1Cached();
    }

    // ################################## TEST HELPERS ################################

    protected CommandImpl helpParse(String sql) {
        try {
            return getQueryParser().parseCommand(sql);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Helps resolve command, then check that the actual resolved Elements variables are the same as
     * the expected variable names.  The variableNames param will be empty unless the subquery
     * is a correlated subquery.
     * @param sql Command to parse and resolve
     * @param variableNames expected element symbol variable names, in order
     * @return parsed and resolved Query
     */
    private CommandImpl helpResolveSubquery(String sql, String[] variableNames) {
        QueryImpl query = (QueryImpl)helpResolve(sql);
        Collection<ElementSymbolImpl> variables = getVariables(query);

        assertTrue("Expected variables size " + variableNames.length + " but was " + variables.size(), //$NON-NLS-1$ //$NON-NLS-2$
                   variables.size() == variableNames.length);
        Iterator<ElementSymbolImpl> variablesIter = variables.iterator();
        for (int i = 0; variablesIter.hasNext(); i++) {
            ElementSymbolImpl variable = variablesIter.next();
            assertTrue("Expected variable name " + variableNames[i] + " but was " + variable.getName(), //$NON-NLS-1$ //$NON-NLS-2$
                       variable.getName().equalsIgnoreCase(variableNames[i]));
        }

        if (variableNames.length == 0) {
            //There should be no TempMetadataIDs
            Collection<SymbolImpl> symbols = CheckNoTempMetadataIDsVisitor.checkSymbols(query);
            assertTrue("Expected no symbols with temp metadataIDs, but got " + symbols, symbols.isEmpty()); //$NON-NLS-1$
        }

        return query;
    }

    public Collection<ElementSymbolImpl> getVariables(BaseLanguageObject languageObject) {
        Collection<ElementSymbolImpl> variables = ElementCollectorVisitorImpl.getElements(languageObject, false, true);
        for (Iterator<ElementSymbolImpl> iterator = variables.iterator(); iterator.hasNext();) {
            ElementSymbolImpl elementSymbol = iterator.next();
            if (!elementSymbol.isExternalReference()) {
                iterator.remove();
            }
        }
        return variables;
    }

    protected CommandImpl helpResolve(String sql, QueryMetadataInterface queryMetadata) {
        return helpResolve(helpParse(sql), queryMetadata);
    }

    protected CommandImpl helpResolve(String sql) {
        return helpResolve(helpParse(sql));
    }

    protected CommandImpl helpResolve(CommandImpl command) {
        return helpResolve(command, this.metadata);
    }

    protected CommandImpl helpResolve(CommandImpl command, QueryMetadataInterface queryMetadataInterface) {
        // resolve
        try {
            TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
            queryResolver.resolveCommand(command, queryMetadataInterface);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        CheckSymbolsAreResolvedVisitor vis = new CheckSymbolsAreResolvedVisitor(getTeiidVersion());
        DeepPreOrderNavigator.doVisit(command, vis);
        Collection<BaseLanguageObject> unresolvedSymbols = vis.getUnresolvedSymbols();
        assertTrue("Found unresolved symbols: " + unresolvedSymbols, unresolvedSymbols.isEmpty()); //$NON-NLS-1$
        return command;
    }

    protected CriteriaImpl helpResolveCriteria(String sql) {
        CriteriaImpl criteria = null;

        // parse
        try {
            criteria = getQueryParser().parseCriteria(sql);

        } catch (Exception e) {
            fail("Exception during parsing (" + e.getClass().getName() + "): " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
        }

        // resolve
        try {
            TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
            queryResolver.resolveCriteria(criteria, metadata);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception during resolution (" + e.getClass().getName() + "): " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
        }

        CheckSymbolsAreResolvedVisitor vis = new CheckSymbolsAreResolvedVisitor(getTeiidVersion());
        DeepPreOrderNavigator.doVisit(criteria, vis);
        Collection<BaseLanguageObject> unresolvedSymbols = vis.getUnresolvedSymbols();
        assertTrue("Found unresolved symbols: " + unresolvedSymbols, unresolvedSymbols.isEmpty()); //$NON-NLS-1$
        return criteria;
    }

    protected CommandImpl helpResolveWithBindings(String sql, QueryMetadataInterface metadata, List bindings) throws Exception {

        // parse
        CommandImpl command = helpParse(sql);

        TCQueryNode qn = new TCQueryNode(sql);
        qn.setBindings(bindings);
        // resolve
        TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
        queryResolver.resolveWithBindingMetadata(command, metadata, qn, true);

        CheckSymbolsAreResolvedVisitor vis = new CheckSymbolsAreResolvedVisitor(getTeiidVersion());
        DeepPreOrderNavigator.doVisit(command, vis);

        Collection<BaseLanguageObject> unresolvedSymbols = vis.getUnresolvedSymbols();
        assertTrue("Found unresolved symbols: " + unresolvedSymbols, unresolvedSymbols.isEmpty()); //$NON-NLS-1$
        return command;
    }

    protected void helpResolveException(String sql, QueryMetadataInterface queryMetadata) {
        helpResolveException(sql, queryMetadata, null);
    }

    protected void helpResolveException(String sql, QueryMetadataInterface queryMetadata, String expectedExceptionMessage) {

        // parse
        CommandImpl command = helpParse(sql);

        // resolve
        try {
            TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
            queryResolver.resolveCommand(command, queryMetadata);
            fail("Expected exception for resolving " + sql); //$NON-NLS-1$
        } catch (QueryResolverException e) {
            assertNotNull(e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void helpResolveException(String sql, String expectedExceptionMessage) {
        helpResolveException(sql, this.metadata, expectedExceptionMessage);
    }

    protected void helpResolveException(String sql) {
        helpResolveException(sql, this.metadata);
    }

    protected void helpCheckFrom(QueryImpl query, String[] groupIDs) {
        FromImpl from = query.getFrom();
        List<GroupSymbolImpl> groups = from.getGroups();
        assertEquals("Wrong number of group IDs: ", groupIDs.length, groups.size()); //$NON-NLS-1$

        for (int i = 0; i < groups.size(); i++) {
            GroupSymbolImpl group = groups.get(i);
            assertNotNull(group.getMetadataID());
            assertEquals("Group ID does not match: ", groupIDs[i].toUpperCase(), group.getNonCorrelationName().toUpperCase()); //$NON-NLS-1$
        }
    }

    protected void helpCheckSelect(QueryImpl query, String[] elementNames) {
        SelectImpl select = query.getSelect();
        List<BaseExpression> elements = select.getProjectedSymbols();
        assertEquals("Wrong number of select symbols: ", elementNames.length, elements.size()); //$NON-NLS-1$

        for (int i = 0; i < elements.size(); i++) {
            BaseExpression symbol = elements.get(i);
            String name = SymbolImpl.getShortName(symbol);
            if (symbol instanceof ElementSymbolImpl) {
                name = ((ElementSymbolImpl)symbol).getName();
            }
            assertEquals("Element name does not match: ", elementNames[i].toUpperCase(), name.toString().toUpperCase()); //$NON-NLS-1$
        }
    }

    protected void helpCheckElements(BaseLanguageObject langObj, String[] elementNames, String[] elementIDs) {
        List<ElementSymbolImpl> elements = new ArrayList<ElementSymbolImpl>();
        ElementCollectorVisitorImpl.getElements(langObj, elements);
        assertEquals("Wrong number of elements: ", elementNames.length, elements.size()); //$NON-NLS-1$

        for (int i = 0; i < elements.size(); i++) {
            ElementSymbolImpl symbol = elements.get(i);
            assertEquals("Element name does not match: ", elementNames[i].toUpperCase(), symbol.getName().toUpperCase()); //$NON-NLS-1$

            Object elementID = symbol.getMetadataID();
            try {
                String name = metadata.getFullName(elementID);
                assertNotNull("ElementSymbol " + symbol + " was not resolved and has no metadataID", elementID); //$NON-NLS-1$ //$NON-NLS-2$
                assertEquals("ElementID name does not match: ", elementIDs[i].toUpperCase(), name.toUpperCase()); //$NON-NLS-1$
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected void helpTestIsXMLQuery(String sql, boolean isXML) throws Exception {
        // parse
        QueryImpl query = (QueryImpl)helpParse(sql);

        // check whether it's xml
        TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
        boolean actual = queryResolver.isXMLQuery(query, metadata);
        assertEquals("Wrong answer for isXMLQuery", isXML, actual); //$NON-NLS-1$
    }

    /**
     * Helper method to resolve an exec aka stored procedure, then check that the
     * expected parameter expressions are the same as actual parameter expressions. 
     * @param sql
     * @param expectedParameterExpressions
     * @since 4.3
     */
    protected StoredProcedureImpl helpResolveExec(String sql, Object[] expectedParameterExpressions) {

        StoredProcedureImpl proc = (StoredProcedureImpl)helpResolve(sql);

        Collection<SPParameterImpl> params = proc.getParameters();

        // Check remaining params against expected expressions
        int i = 0;
        for (SPParameterImpl param : params) {
            if (param.getParameterType() != SPParameterImpl.IN && param.getParameterType() != SPParameterImpl.INOUT) {
                continue;
            }
            if (expectedParameterExpressions[i] == null) {
                assertNull(param.getExpression());
            } else {
                assertEquals(expectedParameterExpressions[i], param.getExpression());
            }
            i++;
        }
        assertEquals(expectedParameterExpressions.length, i);

        return proc;
    }

    protected CommandImpl helpResolveUpdateProcedure(String procedure, String userUpdateStr) throws Exception {
        QueryMetadataInterface metadata = getMetadataFactory().exampleUpdateProc(TriggerEvent.UPDATE, procedure);

        CommandImpl userCommand = getQueryParser().parseCommand(userUpdateStr);
        assertTrue(userCommand instanceof ProcedureContainer);

        TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
        queryResolver.resolveCommand(userCommand, metadata);
        return queryResolver.expandCommand((ProcedureContainer) userCommand, metadata);
    }

    // ################################## ACTUAL TESTS ################################

    @Test
    public void testElementSymbolForms() {
        String sql = "SELECT pm1.g1.e1, e2, pm1.g1.e3 AS a, e4 AS b FROM pm1.g1"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g1"}); //$NON-NLS-1$
        helpCheckSelect(resolvedQuery, new String[] {"pm1.g1.e1", "pm1.g1.e2", "a", "b"}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        helpCheckElements(resolvedQuery.getSelect(), new String[] {"pm1.g1.e1", "pm1.g1.e2", "pm1.g1.e3", "pm1.g1.e4"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                          new String[] {"pm1.g1.e1", "pm1.g1.e2", "pm1.g1.e3", "pm1.g1.e4"}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        assertEquals("Resolved string form was incorrect ", sql, resolvedQuery.toString()); //$NON-NLS-1$
    }

    @Test
    public void testElementSymbolFormsWithAliasedGroup() {
        String sql = "SELECT x.e1, e2, x.e3 AS a, e4 AS b FROM pm1.g1 AS x"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g1"}); //$NON-NLS-1$
        helpCheckSelect(resolvedQuery, new String[] {"x.e1", "x.e2", "a", "b"}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        helpCheckElements(resolvedQuery.getSelect(), new String[] {"x.e1", "x.e2", "x.e3", "x.e4"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                          new String[] {"pm1.g1.e1", "pm1.g1.e2", "pm1.g1.e3", "pm1.g1.e4"}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        assertEquals("Resolved string form was incorrect ", sql, resolvedQuery.toString()); //$NON-NLS-1$
    }

    @Test
    public void testGroupWithVDB() {
        String sql = "SELECT e1 FROM example1.pm1.g1"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g1"}); //$NON-NLS-1$
        assertEquals("Resolved string form was incorrect ", sql, resolvedQuery.toString()); //$NON-NLS-1$
    }

    @Test
    public void testAliasedGroupWithVDB() {
        String sql = "SELECT e1 FROM example1.pm1.g1 AS x"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g1"}); //$NON-NLS-1$
        assertEquals("Resolved string form was incorrect ", sql, resolvedQuery.toString()); //$NON-NLS-1$
    }

    @Test
    public void testPartiallyQualifiedGroup1() {
        metadata = getMetadataFactory().example3();
        String sql = "SELECT e1 FROM cat2.cat3.g1"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckFrom(resolvedQuery, new String[] {"pm1.cat1.cat2.cat3.g1"}); //$NON-NLS-1$
    }

    @Test
    public void testPartiallyQualifiedGroup2() {
        metadata = getMetadataFactory().example3();
        String sql = "SELECT e1 FROM cat1.g2"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckFrom(resolvedQuery, new String[] {"pm1.cat1.g2"}); //$NON-NLS-1$
    }

    @Test
    public void testPartiallyQualifiedGroup3() {
        metadata = getMetadataFactory().example3();
        String sql = "SELECT e1 FROM cat1.cat2.cat3.g1"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckFrom(resolvedQuery, new String[] {"pm1.cat1.cat2.cat3.g1"}); //$NON-NLS-1$
    }

    @Test
    public void testPartiallyQualifiedGroup4() {
        metadata = getMetadataFactory().example3();
        String sql = "SELECT e1 FROM cat2.g2"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckFrom(resolvedQuery, new String[] {"pm2.cat2.g2"}); //$NON-NLS-1$
    }

    @Test
    public void testPartiallyQualifiedGroup5() {
        metadata = getMetadataFactory().example3();
        String sql = "SELECT e1 FROM cat2.g3"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckFrom(resolvedQuery, new String[] {"pm1.cat2.g3"}); //$NON-NLS-1$
    }

    @Test
    public void testPartiallyQualifiedGroup6() {
        metadata = getMetadataFactory().example3();
        String sql = "SELECT e1 FROM cat1.g1"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckFrom(resolvedQuery, new String[] {"pm2.cat1.g1"}); //$NON-NLS-1$
    }

    @Test
    public void testPartiallyQualifiedGroup7() {
        metadata = getMetadataFactory().example3();
        String sql = "SELECT e1 FROM g4"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckFrom(resolvedQuery, new String[] {"pm3.g4"}); //$NON-NLS-1$
    }

    @Test
    public void testPartiallyQualifiedGroup8() {
        metadata = getMetadataFactory().example3();
        String sql = "SELECT e1 FROM pm2.g3"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckFrom(resolvedQuery, new String[] {"pm2.g3"}); //$NON-NLS-1$
    }

    @Test
    public void testPartiallyQualifiedGroupWithAlias() {
        metadata = getMetadataFactory().example3();
        String sql = "SELECT X.e1 FROM cat2.cat3.g1 as X"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckFrom(resolvedQuery, new String[] {"pm1.cat1.cat2.cat3.g1"}); //$NON-NLS-1$
    }

    @Test
    public void testPartiallyQualifiedElement1() {
        metadata = getMetadataFactory().example3();
        String sql = "SELECT cat2.cat3.g1.e1 FROM cat2.cat3.g1"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckSelect(resolvedQuery, new String[] {"pm1.cat1.cat2.cat3.g1.e1"}); //$NON-NLS-1$
    }

    /** defect 12536 */
    @Test
    public void testPartiallyQualifiedElement2() {
        metadata = getMetadataFactory().example3();
        String sql = "SELECT cat3.g1.e1 FROM cat2.cat3.g1"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckSelect(resolvedQuery, new String[] {"pm1.cat1.cat2.cat3.g1.e1"}); //$NON-NLS-1$
    }

    /** defect 12536 */
    @Test
    public void testPartiallyQualifiedElement3() {
        metadata = getMetadataFactory().example3();
        String sql = "SELECT cat3.g1.e1 FROM cat2.cat3.g1, cat1.g2"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckSelect(resolvedQuery, new String[] {"pm1.cat1.cat2.cat3.g1.e1"}); //$NON-NLS-1$
    }

    /** defect 12536 */
    @Test
    public void testPartiallyQualifiedElement4() {
        metadata = getMetadataFactory().example3();
        String sql = "SELECT cat3.g1.e1, cat1.g2.e1 FROM cat2.cat3.g1, cat1.g2"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckSelect(resolvedQuery, new String[] {"pm1.cat1.cat2.cat3.g1.e1", "pm1.cat1.g2.e1"}); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testPartiallyQualifiedElement5() {
        metadata = getMetadataFactory().example3();
        String sql = "SELECT cat3.g1.e1, cat1.g2.e1 FROM example3.pm1.cat1.cat2.cat3.g1, pm1.cat1.g2"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckSelect(resolvedQuery, new String[] {"pm1.cat1.cat2.cat3.g1.e1", "pm1.cat1.g2.e1"}); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** defect 12536 */
    @Test
    public void testPartiallyQualifiedElement6() {
        metadata = getMetadataFactory().example3();
        String sql = "SELECT cat3.g1.e1, e2 FROM cat2.cat3.g1"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckSelect(resolvedQuery, new String[] {"pm1.cat1.cat2.cat3.g1.e1", "pm1.cat1.cat2.cat3.g1.e2"}); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testPartiallyQualifiedElement7() {
        metadata = getMetadataFactory().example3();
        String sql = "SELECT cat3.g1.e1, cat2.cat3.g1.e2, g1.e3 FROM pm1.cat1.cat2.cat3.g1"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckSelect(resolvedQuery, new String[] {
            "pm1.cat1.cat2.cat3.g1.e1", "pm1.cat1.cat2.cat3.g1.e2", "pm1.cat1.cat2.cat3.g1.e3"}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    @Test
    public void testFailPartiallyQualifiedGroup1() {
        metadata = getMetadataFactory().example3();
        helpResolveException("SELECT e1 FROM cat3.g1"); //$NON-NLS-1$
    }

    @Test
    public void testFailPartiallyQualifiedGroup2() {
        metadata = getMetadataFactory().example3();
        helpResolveException("SELECT e1 FROM g1"); //$NON-NLS-1$
    }

    @Test
    public void testFailPartiallyQualifiedGroup3() {
        metadata = getMetadataFactory().example3();
        helpResolveException("SELECT e1 FROM g2"); //$NON-NLS-1$
    }

    @Test
    public void testFailPartiallyQualifiedGroup4() {
        metadata = getMetadataFactory().example3();
        helpResolveException("SELECT e1 FROM g3"); //$NON-NLS-1$
    }

    @Test
    public void testFailPartiallyQualifiedGroup5() {
        metadata = getMetadataFactory().example3();
        helpResolveException("SELECT e1 FROM g5"); //$NON-NLS-1$
    }

    @Test
    public void testFailPartiallyQualifiedElement1() {
        metadata = getMetadataFactory().example3();
        helpResolveException("SELECT cat3.g1.e1 FROM pm1.cat1.cat2.cat3.g1, pm2.cat3.g1"); //$NON-NLS-1$
    }

    @Test
    public void testFailPartiallyQualifiedElement2() {
        metadata = getMetadataFactory().example3();
        helpResolveException("SELECT g1.e1 FROM pm1.cat1.cat2.cat3.g1, pm2.cat3.g1"); //$NON-NLS-1$
    }

    @Test
    public void testFailPartiallyQualifiedElement3() {
        metadata = getMetadataFactory().example3();
        helpResolveException("SELECT cat3.g1.e1 FROM pm2.cat2.g2, pm1.cat2.g3"); //$NON-NLS-1$
    }

    @Test
    public void testFailPartiallyQualifiedElement4() {
        metadata = getMetadataFactory().example3();
        helpResolveException("SELECT cat3.g1.e1 FROM pm2.cat2.g2"); //$NON-NLS-1$
    }

    @Test
    public void testFailPartiallyQualifiedElement5() {
        metadata = getMetadataFactory().example3();
        helpResolveException("SELECT cat3.g1.e1 FROM g1"); //$NON-NLS-1$
    }

    @Test
    public void testElementWithVDB() {
        String sql = "SELECT example1.pm1.g1.e1 FROM pm1.g1"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckSelect(resolvedQuery, new String[] {"pm1.g1.e1"}); //$NON-NLS-1$
        helpCheckElements(resolvedQuery.getSelect(), new String[] {"pm1.g1.e1"}, //$NON-NLS-1$
                          new String[] {"pm1.g1.e1"}); //$NON-NLS-1$
        assertEquals("Resolved string form was incorrect ", sql, resolvedQuery.toString()); //$NON-NLS-1$
    }

    @Test
    public void testAliasedElementWithVDB() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT example1.pm1.g1.e1 AS x FROM pm1.g1"); //$NON-NLS-1$
        helpCheckSelect(resolvedQuery, new String[] {"x"}); //$NON-NLS-1$
        helpCheckElements(resolvedQuery.getSelect(), new String[] {"pm1.g1.e1"}, //$NON-NLS-1$
                          new String[] {"pm1.g1.e1"}); //$NON-NLS-1$
    }

    @Test
    public void testSelectStar() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT * FROM pm1.g1"); //$NON-NLS-1$
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g1"}); //$NON-NLS-1$
        helpCheckElements(resolvedQuery.getSelect(), new String[] {"pm1.g1.e1", "pm1.g1.e2", "pm1.g1.e3", "pm1.g1.e4"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                          new String[] {"pm1.g1.e1", "pm1.g1.e2", "pm1.g1.e3", "pm1.g1.e4"}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    @Test
    public void testSelectStarFromAliasedGroup() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT * FROM pm1.g1 as x"); //$NON-NLS-1$
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g1"}); //$NON-NLS-1$
        helpCheckElements(resolvedQuery.getSelect(), new String[] {"x.e1", "x.e2", "x.e3", "x.e4"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                          new String[] {"pm1.g1.e1", "pm1.g1.e2", "pm1.g1.e3", "pm1.g1.e4"}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    @Test
    public void testSelectStarFromMultipleAliasedGroups() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT * FROM pm1.g1 as x, pm1.g1 as y"); //$NON-NLS-1$
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g1", "pm1.g1"}); //$NON-NLS-1$ //$NON-NLS-2$
        helpCheckElements(resolvedQuery.getSelect(),
                          new String[] {"x.e1", "x.e2", "x.e3", "x.e4", "y.e1", "y.e2", "y.e3", "y.e4"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
                          new String[] {
                              "pm1.g1.e1", "pm1.g1.e2", "pm1.g1.e3", "pm1.g1.e4", "pm1.g1.e1", "pm1.g1.e2", "pm1.g1.e3", "pm1.g1.e4"}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
    }

    @Test
    public void testSelectStarWhereSomeElementsAreNotSelectable() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT * FROM pm1.g4"); //$NON-NLS-1$
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g4"}); //$NON-NLS-1$
        helpCheckElements(resolvedQuery.getSelect(), new String[] {"pm1.g4.e1", "pm1.g4.e3"}, //$NON-NLS-1$ //$NON-NLS-2$
                          new String[] {"pm1.g4.e1", "pm1.g4.e3"}); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testSelectGroupStarWhereSomeElementsAreNotSelectable() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT pm1.g4.* FROM pm1.g4"); //$NON-NLS-1$
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g4"}); //$NON-NLS-1$
        helpCheckElements(resolvedQuery.getSelect(), new String[] {"pm1.g4.e1", "pm1.g4.e3"}, //$NON-NLS-1$ //$NON-NLS-2$
                          new String[] {"pm1.g4.e1", "pm1.g4.e3"}); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testFullyQualifiedSelectStar() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT pm1.g1.* FROM pm1.g1"); //$NON-NLS-1$
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g1"}); //$NON-NLS-1$
        helpCheckElements(resolvedQuery.getSelect(), new String[] {"pm1.g1.e1", "pm1.g1.e2", "pm1.g1.e3", "pm1.g1.e4"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                          new String[] {"pm1.g1.e1", "pm1.g1.e2", "pm1.g1.e3", "pm1.g1.e4"}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    @Test
    public void testSelectAllInAliasedGroup() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT x.* FROM pm1.g1 as x"); //$NON-NLS-1$
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g1"}); //$NON-NLS-1$
        helpCheckElements(resolvedQuery.getSelect(), new String[] {"x.e1", "x.e2", "x.e3", "x.e4"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                          new String[] {"pm1.g1.e1", "pm1.g1.e2", "pm1.g1.e3", "pm1.g1.e4"}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    @Test
    public void testMultipleIdenticalElements() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT e1, e1 FROM pm1.g1"); //$NON-NLS-1$
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g1"}); //$NON-NLS-1$
        helpCheckSelect(resolvedQuery, new String[] {"pm1.g1.e1", "pm1.g1.e1"}); //$NON-NLS-1$ //$NON-NLS-2$
        helpCheckElements(resolvedQuery.getSelect(), new String[] {"pm1.g1.e1", "pm1.g1.e1"}, //$NON-NLS-1$ //$NON-NLS-2$
                          new String[] {"pm1.g1.e1", "pm1.g1.e1"}); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testMultipleIdenticalElements2() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT e1, pm1.g1.e1 FROM pm1.g1"); //$NON-NLS-1$
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g1"}); //$NON-NLS-1$
        helpCheckSelect(resolvedQuery, new String[] {"pm1.g1.e1", "pm1.g1.e1"}); //$NON-NLS-1$ //$NON-NLS-2$
        helpCheckElements(resolvedQuery.getSelect(), new String[] {"pm1.g1.e1", "pm1.g1.e1"}, //$NON-NLS-1$ //$NON-NLS-2$
                          new String[] {"pm1.g1.e1", "pm1.g1.e1"}); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testMultipleIdenticalElements3() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT e1, e1 as x FROM pm1.g1"); //$NON-NLS-1$
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g1"}); //$NON-NLS-1$
        helpCheckSelect(resolvedQuery, new String[] {"pm1.g1.e1", "x"}); //$NON-NLS-1$ //$NON-NLS-2$
        helpCheckElements(resolvedQuery.getSelect(), new String[] {"pm1.g1.e1", "pm1.g1.e1"}, //$NON-NLS-1$ //$NON-NLS-2$
                          new String[] {"pm1.g1.e1", "pm1.g1.e1"}); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testDifferentElementsSameName() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT e1 as x, e2 as x FROM pm1.g2"); //$NON-NLS-1$
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g2"}); //$NON-NLS-1$
        helpCheckSelect(resolvedQuery, new String[] {"x", "x"}); //$NON-NLS-1$ //$NON-NLS-2$
        helpCheckElements(resolvedQuery.getSelect(), new String[] {"pm1.g2.e1", "pm1.g2.e2"}, //$NON-NLS-1$ //$NON-NLS-2$
                          new String[] {"pm1.g2.e1", "pm1.g2.e2"}); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testDifferentConstantsSameName() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT 1 as x, 2 as x FROM pm1.g2"); //$NON-NLS-1$
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g2"}); //$NON-NLS-1$
        helpCheckSelect(resolvedQuery, new String[] {"x", "x"}); //$NON-NLS-1$ //$NON-NLS-2$
        helpCheckElements(resolvedQuery.getSelect(), new String[] {}, new String[] {});
    }

    @Test
    public void testFailSameGroupsWithSameNames() {
        helpResolveException("SELECT * FROM pm1.g1 as x, pm1.g1 as x"); //$NON-NLS-1$
    }

    @Test
    public void testFailDifferentGroupsWithSameNames() {
        helpResolveException("SELECT * FROM pm1.g1 as x, pm1.g2 as x"); //$NON-NLS-1$
    }

    @Test
    public void testFailAmbiguousElement() {
        helpResolveException("SELECT e1 FROM pm1.g1, pm1.g2"); //$NON-NLS-1$
    }

    @Test
    public void testFailAmbiguousElementAliasedGroup() {
        helpResolveException("SELECT e1 FROM pm1.g1 as x, pm1.g1"); //$NON-NLS-1$
    }

    @Test
    public void testFailFullyQualifiedElementUnknownGroup() {
        helpResolveException("SELECT pm1.g1.e1 FROM pm1.g2"); //$NON-NLS-1$
    }

    @Test
    public void testFailUnknownGroup() {
        helpResolveException("SELECT x.e1 FROM x"); //$NON-NLS-1$
    }

    @Test
    public void testFailUnknownElement() {
        helpResolveException("SELECT x FROM pm1.g1"); //$NON-NLS-1$
    }

    @Test
    public void testFailFunctionOfAggregatesInSelect() {
        helpResolveException("SELECT (SUM(e0) * COUNT(e0)) FROM test.group GROUP BY e0"); //$NON-NLS-1$
    }

    /*
     * per defect 4404 
     */
    @Test
    public void testFailGroupNotReferencedByAlias() {
        helpResolveException("SELECT pm1.g1.x FROM pm1.g1 as H"); //$NON-NLS-1$
    }

    /*
     * per defect 4404 - this one reproduced the defect,
     * then succeeded after the fix
     */
    @Test
    public void testFailGroupNotReferencedByAliasSelectAll() {
        helpResolveException("SELECT pm1.g1.* FROM pm1.g1 as H"); //$NON-NLS-1$
    }

    @Test
    public void testComplicatedQuery() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT pm1.g1.e2 as y, pm1.g1.E3 as z, CONVERT(pm1.g1.e1, integer) * 1000 as w  FROM pm1.g1 WHERE e1 <> 'x'"); //$NON-NLS-1$
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g1"}); //$NON-NLS-1$
        helpCheckSelect(resolvedQuery, new String[] {"y", "z", "w"}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        helpCheckElements(resolvedQuery, new String[] {"pm1.g1.e2", "pm1.g1.e3", "pm1.g1.e1", "pm1.g1.e1"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                          new String[] {"pm1.g1.e2", "pm1.g1.e3", "pm1.g1.e1", "pm1.g1.e1"}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    @Test
    public void testJoinQuery() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT pm3.g1.e2, pm3.g2.e2 FROM pm3.g1, pm3.g2 WHERE pm3.g1.e2=pm3.g2.e2"); //$NON-NLS-1$
        helpCheckFrom(resolvedQuery, new String[] {"pm3.g1", "pm3.g2"}); //$NON-NLS-1$ //$NON-NLS-2$
        helpCheckSelect(resolvedQuery, new String[] {"pm3.g1.e2", "pm3.g2.e2"}); //$NON-NLS-1$ //$NON-NLS-2$
        helpCheckElements(resolvedQuery, new String[] {"pm3.g1.e2", "pm3.g2.e2", "pm3.g1.e2", "pm3.g2.e2"}, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                          new String[] {"pm3.g1.e2", "pm3.g2.e2", "pm3.g1.e2", "pm3.g2.e2"}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    @Test
    public void testHavingRequiringConvertOnAggregate1() {
        helpResolve("SELECT * FROM pm1.g1 GROUP BY e4 HAVING MAX(e2) > 1.2"); //$NON-NLS-1$
    }

    @Test
    public void testHavingRequiringConvertOnAggregate2() {
        helpResolve("SELECT * FROM pm1.g1 GROUP BY e4 HAVING MIN(e2) > 1.2"); //$NON-NLS-1$
    }

    @Test
    public void testHavingRequiringConvertOnAggregate3() {
        helpResolve("SELECT * FROM pm1.g1 GROUP BY e4 HAVING 1.2 > MAX(e2)"); //$NON-NLS-1$
    }

    @Test
    public void testHavingRequiringConvertOnAggregate4() {
        helpResolve("SELECT * FROM pm1.g1 GROUP BY e4 HAVING 1.2 > MIN(e2)"); //$NON-NLS-1$
    }

    @Test
    public void testHavingWithAggsOfDifferentTypes() {
        helpResolve("SELECT * FROM pm1.g1 GROUP BY e4 HAVING MIN(e1) = MIN(e2)"); //$NON-NLS-1$
    }

    @Test
    public void testCaseInGroupBy() {
        String sql = "SELECT SUM(e2) FROM pm1.g1 GROUP BY CASE WHEN e2 = 0 THEN 1 ELSE 2 END"; //$NON-NLS-1$
        CommandImpl command = helpResolve(sql);
        assertEquals(sql, command.toString());

        helpCheckElements(command, new String[] {"pm1.g1.e2", "pm1.g1.e2"}, new String[] {"pm1.g1.e2", "pm1.g1.e2"}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$//$NON-NLS-4$
    }

    @Test
    public void testFunctionInGroupBy() {
        String sql = "SELECT SUM(e2) FROM pm1.g1 GROUP BY (e2 + 1)"; //$NON-NLS-1$
        CommandImpl command = helpResolve(sql);
        assertEquals(sql, command.toString());

        helpCheckElements(command, new String[] {"pm1.g1.e2", "pm1.g1.e2"}, new String[] {"pm1.g1.e2", "pm1.g1.e2"}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$//$NON-NLS-4$
    }

    @Test
    public void testUnknownFunction() {
        helpResolveException("SELECT abc(e1) FROM pm1.g1", "TEIID30068 The function 'abc(e1)' is an unknown form.  Check that the function name and number of arguments is correct."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testResolveParametersInsert() throws Exception {
        List<String> bindings = Arrays.asList("pm1.g2.e1"); //$NON-NLS-1$

        helpResolveWithBindings("INSERT INTO pm1.g1 (e1) VALUES (?)", metadata, bindings); //$NON-NLS-1$
    }

    @Test
    public void testResolveParametersExec() throws Exception {
        List<String> bindings = Arrays.asList("pm1.g2.e1"); //$NON-NLS-1$

        QueryImpl resolvedQuery = (QueryImpl)helpResolveWithBindings("SELECT * FROM (exec pm1.sq2(?)) as a", metadata, bindings); //$NON-NLS-1$
        StoredProcedureImpl sp = (StoredProcedureImpl)((SubqueryFromClauseImpl)resolvedQuery.getFrom().getClauses().get(0)).getCommand();
        assertEquals(String.class, sp.getInputParameters().get(0).getExpression().getType());
    }

    @Test
    public void testResolveParametersExecNamed() throws Exception {
        List<String> bindings = Arrays.asList("pm1.g2.e1 as x"); //$NON-NLS-1$

        helpResolveWithBindings("SELECT * FROM (exec pm1.sq2(input.x)) as a", metadata, bindings); //$NON-NLS-1$
    }

    @Test
    public void testUseNonExistentAlias() {
        helpResolveException("SELECT portfoliob.e1 FROM ((pm1.g1 AS portfoliob JOIN pm1.g2 AS portidentb ON portfoliob.e1 = portidentb.e1) RIGHT OUTER JOIN pm1.g3 AS identifiersb ON portidentb.e1 = 'ISIN' and portidentb.e2 = identifiersb.e2) RIGHT OUTER JOIN pm1.g1 AS issuesb ON a.identifiersb.e1 = issuesb.e1"); //$NON-NLS-1$
    }

    @Test
    public void testCriteria1() {
        ElementSymbolImpl es = getFactory().newElementSymbol("pm1.g1.e1"); //$NON-NLS-1$
        es.setType(DefaultDataTypeManager.DefaultDataTypes.STRING.getTypeClass());
        GroupSymbolImpl gs = getFactory().newGroupSymbol("pm1.g1"); //$NON-NLS-1$
        es.setGroupSymbol(gs);

        ConstantImpl abc = getFactory().newConstant("abc"); //$NON-NLS-1$
        CompareCriteriaImpl expected = getFactory().newCompareCriteria(es, CriteriaOperator.Operator.EQ, abc);

        CriteriaImpl actual = helpResolveCriteria("pm1.g1.e1 = 'abc'"); //$NON-NLS-1$

        assertEquals("Did not match expected criteria", expected, actual); //$NON-NLS-1$
    }

    @Test
    public void testSubquery1() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT e1 FROM pm1.g1, (SELECT pm1.g2.e1 AS x FROM pm1.g2) AS y WHERE e1 = x"); //$NON-NLS-1$
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g1", "y"}); //$NON-NLS-1$ //$NON-NLS-2$
        helpCheckSelect(resolvedQuery, new String[] {"pm1.g1.e1"}); //$NON-NLS-1$
        helpCheckElements(resolvedQuery.getSelect(), new String[] {"pm1.g1.e1"}, //$NON-NLS-1$
                          new String[] {"pm1.g1.e1"}); //$NON-NLS-1$

    }

    protected void helpCheckParameter(SPParameterImpl param, int paramType, int index, String name, Class<?> type, BaseExpression expr) {
        assertEquals("Did not get expected parameter type", paramType, param.getParameterType()); //$NON-NLS-1$
        assertEquals("Did not get expected index for param", index, param.getIndex()); //$NON-NLS-1$
        assertEquals("Did not get expected name for param", name, param.getName()); //$NON-NLS-1$
        assertEquals("Did not get expected type for param", type, param.getClassType()); //$NON-NLS-1$
        assertEquals("Did not get expected type for param", expr, param.getExpression()); //$NON-NLS-1$
    }

    @Test
    public void testStoredSubQuery1() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("select x.e1 from (EXEC pm1.sq1()) as x"); //$NON-NLS-1$
        helpCheckFrom(resolvedQuery, new String[] {"x"}); //$NON-NLS-1$
        helpCheckSelect(resolvedQuery, new String[] {"x.e1"}); //$NON-NLS-1$
    }

    @Test
    public void testStoredSubQuery2() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("select x.e1 from (EXEC pm1.sq3('abc', 5)) as x"); //$NON-NLS-1$
        helpCheckFrom(resolvedQuery, new String[] {"x"}); //$NON-NLS-1$
        helpCheckSelect(resolvedQuery, new String[] {"x.e1"}); //$NON-NLS-1$
    }

    @Test
    public void testStoredSubQuery3() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("select * from (EXEC pm1.sq2('abc')) as x"); //$NON-NLS-1$
        helpCheckFrom(resolvedQuery, new String[] {"x"}); //$NON-NLS-1$

        List<ElementSymbolImpl> elements = (List<ElementSymbolImpl>)ElementCollectorVisitorImpl.getElements(resolvedQuery.getSelect(), false);

        ElementSymbolImpl elem1 = elements.get(0);
        assertEquals("Did not get expected element", "x.e1", elem1.getName()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("Did not get expected type", DefaultDataTypeManager.DefaultDataTypes.STRING.getTypeClass(), elem1.getType()); //$NON-NLS-1$

        ElementSymbolImpl elem2 = elements.get(1);
        assertEquals("Did not get expected element", "x.e2", elem2.getName()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("Did not get expected type", DefaultDataTypeManager.DefaultDataTypes.INTEGER.getTypeClass(), elem2.getType()); //$NON-NLS-1$
    }

    @Test
    public void testStoredQueryTransformationWithVariable4() throws Exception {
        CommandImpl command = getQueryParser().parseCommand("EXEC pm1.sq2(pm1.sq2.in)"); //$NON-NLS-1$

        // resolve
        try {
            // Construct command metadata
            GroupSymbolImpl sqGroup = getFactory().newGroupSymbol("pm1.sq5"); //$NON-NLS-1$
            ArrayList sqParams = new ArrayList();
            ElementSymbolImpl in = getFactory().newElementSymbol("pm1.sq5.in1"); //$NON-NLS-1$
            in.setType(DefaultDataTypeManager.DefaultDataTypes.STRING.getTypeClass());
            sqParams.add(in);
            Map externalMetadata = new HashMap();
            externalMetadata.put(sqGroup, sqParams);

            TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
            queryResolver.resolveCommand(command, metadata);

            fail("Expected exception on invalid variable pm1.sq2.in"); //$NON-NLS-1$
        } catch (QueryResolverException e) {
            assertEquals("TEIID31119 Symbol pm1.sq2.\"in\" is specified with an unknown group context", e.getMessage()); //$NON-NLS-1$
        }
    }

    @Test
    public void testExec1() {
        helpResolve("EXEC pm1.sq2('xyz')"); //$NON-NLS-1$
    }

    @Test
    public void testExec2() {
        // implicity convert 5 to proper type
        helpResolve("EXEC pm1.sq2(5)"); //$NON-NLS-1$
    }

    @Test
    public void testExecNamedParam() {
        Object[] expectedParameterExpressions = new Object[] {getFactory().newConstant("xyz")};//$NON-NLS-1$
        helpResolveExec("EXEC pm1.sq2(\"in\" = 'xyz')", expectedParameterExpressions);//$NON-NLS-1$
    }

    @Test
    public void testExecNamedParamDup() {
        helpResolveException("EXEC pm1.sq2(\"in\" = 'xyz', \"in\" = 'xyz1')");//$NON-NLS-1$
    }

    /** Should get exception because param name is wrong. */
    @Test
    public void testExecWrongParamName() {
        helpResolveException("EXEC pm1.sq2(in1 = 'xyz')");//$NON-NLS-1$
    }

    @Test
    public void testExecNamedParams() {
        Object[] expectedParameterExpressions = new Object[] {
            getFactory().newConstant("xyz"), getFactory().newConstant(new Integer(5))};//$NON-NLS-1$
        helpResolveExec("EXEC pm1.sq3(\"in\" = 'xyz', in2 = 5)", expectedParameterExpressions);//$NON-NLS-1$
    }

    /** try entering params out of order */
    @Test
    public void testExecNamedParamsReversed() {
        Object[] expectedParameterExpressions = new Object[] {
            getFactory().newConstant("xyz"), getFactory().newConstant(new Integer(5))};//$NON-NLS-1$
        helpResolveExec("EXEC pm1.sq3(in2 = 5, \"in\" = 'xyz')", expectedParameterExpressions);//$NON-NLS-1$
    }

    /** test omitting an optional parameter */
    @Test
    public void testExecNamedParamsOptionalParam() {
        Object[] expectedParameterExpressions = new Object[] {
            getFactory().newConstant("xyz"), getFactory().newConstant(null), getFactory().newConstant("something")};//$NON-NLS-1$ //$NON-NLS-2$
        helpResolveExec("EXEC pm1.sq3b(\"in\" = 'xyz', in3 = 'something')", expectedParameterExpressions);//$NON-NLS-1$
    }

    /** test omitting a required parameter that has a default value */
    @Test
    public void testExecNamedParamsOmitRequiredParamWithDefaultValue() {
        Object[] expectedParameterExpressions = new Object[] {
            getFactory().newConstant("xyz"), getFactory().newConstant(new Integer(666)), getFactory().newConstant("YYZ")};//$NON-NLS-1$ //$NON-NLS-2$
        StoredProcedureImpl sp = helpResolveExec("EXEC pm1.sq3b(\"in\" = 'xyz', in2 = 666)", expectedParameterExpressions);//$NON-NLS-1$
        assertEquals("EXEC pm1.sq3b(\"in\" => 'xyz', in2 => 666)", sp.toString());
    }

    @Test
    public void testExecNamedParamsOptionalParamWithDefaults() {
        Object[] expectedParameterExpressions = helpGetStoredProcDefaultValues();
        //override the default value for the first parameter
        expectedParameterExpressions[0] = getFactory().newConstant("xyz"); //$NON-NLS-1$
        helpResolveExec("EXEC pm1.sqDefaults(inString = 'xyz')", expectedParameterExpressions);//$NON-NLS-1$
    }

    @Test
    public void testExecNamedParamsOptionalParamWithDefaultsCaseInsensitive() {
        Object[] expectedParameterExpressions = helpGetStoredProcDefaultValues();
        //override the default value for the first parameter
        expectedParameterExpressions[0] = getFactory().newConstant("xyz"); //$NON-NLS-1$
        helpResolveExec("EXEC pm1.sqDefaults(iNsTrInG = 'xyz')", expectedParameterExpressions);//$NON-NLS-1$
    }

    /** try just a few named parameters, in no particular order */
    @Test
    public void testExecNamedParamsOptionalParamWithDefaults2() {
        Object[] expectedParameterExpressions = helpGetStoredProcDefaultValues();
        //override the proper default values in expected results
        expectedParameterExpressions[3] = getFactory().newConstant(Boolean.FALSE);
        expectedParameterExpressions[9] = getFactory().newConstant(new Integer(666));
        helpResolveExec("EXEC pm1.sqDefaults(ininteger = 666, inboolean={b'false'})", expectedParameterExpressions);//$NON-NLS-1$
    }

    /** 
     * Try entering in no actual parameters, rely entirely on defaults.  
     * This also tests the default value transformation code in ExecResolver. 
     */
    @Test
    public void testExecNamedParamsOptionalParamWithAllDefaults() {
        Object[] expectedParameterExpressions = helpGetStoredProcDefaultValues();
        helpResolveExec("EXEC pm1.sqDefaults()", expectedParameterExpressions);//$NON-NLS-1$
    }

    /**
     * Retrieve the Object array of expected default values for the stored procedure
     * "pm1.sqDefaults" in FakeMetadataFactory.example1().
     * @return
     * @since 4.3
     */
    private Object[] helpGetStoredProcDefaultValues() {

        // This needs to match what's in FakeMetadataFactory.example1 for this stored proc
        return new Object[] {
            getFactory().newConstant("x"), //$NON-NLS-1$
            getFactory().newConstant(new BigDecimal("13.0")),//$NON-NLS-1$
            getFactory().newConstant(new BigInteger("13")),//$NON-NLS-1$
            getFactory().newConstant(Boolean.TRUE),
            getFactory().newConstant(new Byte("1")),//$NON-NLS-1$
            getFactory().newConstant(new Character('q')),
            getFactory().newConstant(Date.valueOf("2003-03-20")),//$NON-NLS-1$
            getFactory().newConstant(new Double(13.0)), getFactory().newConstant(new Float(13.0)),
            getFactory().newConstant(new Integer(13)), getFactory().newConstant(new Long(13)),
            getFactory().newConstant(new Short((short)13)),
            getFactory().newConstant(Timestamp.valueOf("2003-03-20 21:26:00.000000")),//$NON-NLS-1$
            getFactory().newConstant(Time.valueOf("21:26:00")),//$NON-NLS-1$
        };
    }

    /** Should get exception because there are two required params */
    @Test
    public void testExceptionNotSupplyingRequiredParam() {
        helpResolveException("EXEC pm1.sq3(in2 = 5)");//$NON-NLS-1$
    }

    /** Should get exception because the default value in metadata is bad for input param */
    @Test
    public void testExceptionBadDefaultValue() {
        helpResolveException("EXEC pm1.sqBadDefault()");//$NON-NLS-1$
    }

    @Test
    public void testExecWithForcedConvertOfStringToCorrectType() {
        // force conversion of '5' to proper type (integer)
        helpResolve("EXEC pm1.sq3('x', '5')"); //$NON-NLS-1$
    }

    /**
     * True/false are consistently representable by integers
     */
    @Test
    public void testExecBadType() {
        helpResolve("EXEC pm1.sq3('xyz', {b'true'})"); //$NON-NLS-1$
    }

    @Test
    public void testSubqueryInUnion() {
        String sql = "SELECT IntKey, FloatNum FROM BQT1.MediumA WHERE (IntKey >= 0) AND (IntKey < 15) " + //$NON-NLS-1$
                     "UNION ALL " + //$NON-NLS-1$
                     "SELECT BQT2.SmallB.IntKey, y.FloatNum " + //$NON-NLS-1$
                     "FROM BQT2.SmallB INNER JOIN " + //$NON-NLS-1$
                     "(SELECT IntKey, FloatNum FROM BQT1.MediumA ) AS y ON BQT2.SmallB.IntKey = y.IntKey " + //$NON-NLS-1$
                     "WHERE (y.IntKey >= 10) AND (y.IntKey < 30) " + //$NON-NLS-1$
                     "ORDER BY IntKey, FloatNum"; //$NON-NLS-1$

        helpResolve(sql, getMetadataFactory().exampleBQTCached());
    }

    @Test
    public void testSubQueryINClause1() {
        //select e1 from pm1.g1 where e2 in (select e2 from pm4.g1)

        //sub command
        SelectImpl innerSelect = getFactory().newSelect();
        ElementSymbolImpl e2inner = getFactory().newElementSymbol("e2"); //$NON-NLS-1$
        innerSelect.addSymbol(e2inner);
        FromImpl innerFrom = getFactory().newFrom();
        GroupSymbolImpl pm4g1 = getFactory().newGroupSymbol("pm4.g1"); //$NON-NLS-1$
        innerFrom.addGroup(pm4g1);
        QueryImpl innerQuery = getFactory().newQuery();
        innerQuery.setSelect(innerSelect);
        innerQuery.setFrom(innerFrom);

        //outer command
        SelectImpl outerSelect = getFactory().newSelect();
        ElementSymbolImpl e1 = getFactory().newElementSymbol("e1"); //$NON-NLS-1$
        outerSelect.addSymbol(e1);
        FromImpl outerFrom = getFactory().newFrom();
        GroupSymbolImpl pm1g1 = getFactory().newGroupSymbol("pm1.g1"); //$NON-NLS-1$
        outerFrom.addGroup(pm1g1);
        ElementSymbolImpl e2outer = getFactory().newElementSymbol("e2"); //$NON-NLS-1$
        SubquerySetCriteriaImpl crit = getFactory().newSubquerySetCriteria(e2outer, innerQuery);
        QueryImpl outerQuery = getFactory().newQuery();
        outerQuery.setSelect(outerSelect);
        outerQuery.setFrom(outerFrom);
        outerQuery.setCriteria(crit);

        //test
        helpResolve(outerQuery);

        helpCheckFrom(outerQuery, new String[] {"pm1.g1"}); //$NON-NLS-1$
        helpCheckFrom(innerQuery, new String[] {"pm4.g1"}); //$NON-NLS-1$
        helpCheckSelect(outerQuery, new String[] {"pm1.g1.e1"}); //$NON-NLS-1$
        helpCheckSelect(innerQuery, new String[] {"pm4.g1.e2"}); //$NON-NLS-1$
        helpCheckElements(outerQuery.getSelect(), new String[] {"pm1.g1.e1"}, //$NON-NLS-1$
                          new String[] {"pm1.g1.e1"}); //$NON-NLS-1$
        helpCheckElements(innerQuery.getSelect(), new String[] {"pm4.g1.e2"}, //$NON-NLS-1$
                          new String[] {"pm4.g1.e2"}); //$NON-NLS-1$

        String sql = "SELECT e1 FROM pm1.g1 WHERE e2 IN (SELECT e2 FROM pm4.g1)"; //$NON-NLS-1$
        assertEquals("Resolved string form was incorrect ", sql, outerQuery.toString()); //$NON-NLS-1$
    }

    /**
     * An implicit type conversion needs to be inserted because the
     * project symbol of the subquery is not the same type as the expression in
     * the SubquerySetCriteria object
     */
    @Test
    public void testSubQueryINClauseImplicitConversion() {
        //select e1 from pm1.g1 where e2 in (select e1 from pm4.g1)

        //sub command
        SelectImpl innerSelect = getFactory().newSelect();
        ElementSymbolImpl e1inner = getFactory().newElementSymbol("e1"); //$NON-NLS-1$
        innerSelect.addSymbol(e1inner);
        FromImpl innerFrom = getFactory().newFrom();
        GroupSymbolImpl pm4g1 = getFactory().newGroupSymbol("pm4.g1"); //$NON-NLS-1$
        innerFrom.addGroup(pm4g1);
        QueryImpl innerQuery = getFactory().newQuery();
        innerQuery.setSelect(innerSelect);
        innerQuery.setFrom(innerFrom);

        //outer command
        SelectImpl outerSelect = getFactory().newSelect();
        ElementSymbolImpl e1 = getFactory().newElementSymbol("e1"); //$NON-NLS-1$
        outerSelect.addSymbol(e1);
        FromImpl outerFrom = getFactory().newFrom();
        GroupSymbolImpl pm1g1 = getFactory().newGroupSymbol("pm1.g1"); //$NON-NLS-1$
        outerFrom.addGroup(pm1g1);
        ElementSymbolImpl e2 = getFactory().newElementSymbol("e2"); //$NON-NLS-1$
        SubquerySetCriteriaImpl crit = getFactory().newSubquerySetCriteria(e2, innerQuery);
        QueryImpl outerQuery = getFactory().newQuery();
        outerQuery.setSelect(outerSelect);
        outerQuery.setFrom(outerFrom);
        outerQuery.setCriteria(crit);

        //test
        helpResolve(outerQuery);

        helpCheckFrom(outerQuery, new String[] {"pm1.g1"}); //$NON-NLS-1$
        helpCheckFrom(innerQuery, new String[] {"pm4.g1"}); //$NON-NLS-1$
        helpCheckSelect(outerQuery, new String[] {"pm1.g1.e1"}); //$NON-NLS-1$
        helpCheckSelect(innerQuery, new String[] {"pm4.g1.e1"}); //$NON-NLS-1$
        helpCheckElements(outerQuery.getSelect(), new String[] {"pm1.g1.e1"}, //$NON-NLS-1$
                          new String[] {"pm1.g1.e1"}); //$NON-NLS-1$
        helpCheckElements(innerQuery.getSelect(), new String[] {"pm4.g1.e1"}, //$NON-NLS-1$
                          new String[] {"pm4.g1.e1"}); //$NON-NLS-1$

        String sql = "SELECT e1 FROM pm1.g1 WHERE e2 IN (SELECT e1 FROM pm4.g1)"; //$NON-NLS-1$
        assertEquals("Resolved string form was incorrect ", sql, outerQuery.toString()); //$NON-NLS-1$

        //make sure there is a convert function wrapping the criteria left expression
        Collection functions = FunctionCollectorVisitorImpl.getFunctions(outerQuery, true);
        assertTrue(functions.size() == 1);
        FunctionImpl function = (FunctionImpl)functions.iterator().next();
        assertTrue(function.getName().equals(FunctionLibrary.FunctionName.CONVERT.text()));
        BaseExpression[] args = function.getArgs();
        assertSame(e2, args[0]);
        assertTrue(args[1] instanceof ConstantImpl);
    }

    /**
     * Tests that resolving fails if there is no implicit conversion between the
     * type of the expression of the SubquerySetCriteria and the type of the
     * projected symbol of the subquery.
     */
    @Test
    public void testSubQueryINClauseNoConversionFails() {
        helpResolveException("select e1 from pm1.g1 where e1 in (select e2 from pm4.g1)");
    }

    @Test
    public void testSubQueryINClauseTooManyColumns() {
        String sql = "select e1 from pm1.g1 where e1 in (select e1, e2 from pm4.g1)"; //$NON-NLS-1$

        //test
        this.helpResolveException(sql);
    }

    @Test
    public void testStoredQueryInFROMSubquery() {
        String sql = "select X.e1 from (EXEC pm1.sq3('abc', 123)) as X"; //$NON-NLS-1$

        helpResolve(sql);
    }

    @Test
    public void testStoredQueryInINSubquery() throws Exception {
        String sql = "select * from pm1.g1 where e1 in (EXEC pm1.sqsp1())"; //$NON-NLS-1$

        helpResolve(sql);
    }

    @Test
    public void testIsXMLQuery1() throws Exception {
        helpTestIsXMLQuery("SELECT * FROM pm1.g1", false); //$NON-NLS-1$
    }

    @Test
    public void testIsXMLQuery2() throws Exception {
        helpTestIsXMLQuery("SELECT * FROM xmltest.doc1", true); //$NON-NLS-1$
    }

    /**
     * Must be able to resolve XML query if short doc name
     * is used (assuming short doc name isn't ambiguous in a
     * VDB).  Defect 11479.
     */
    @Test
    public void testIsXMLQuery3() throws Exception {
        helpTestIsXMLQuery("SELECT * FROM doc1", true); //$NON-NLS-1$
    }

    @Test
    public void testIsXMLQueryFail1() throws Exception {
        helpTestIsXMLQuery("SELECT * FROM xmltest.doc1, xmltest.doc2", false); //$NON-NLS-1$
    }

    @Test
    public void testIsXMLQueryFail2() throws Exception {
        helpTestIsXMLQuery("SELECT * FROM xmltest.doc1, pm1.g1", false); //$NON-NLS-1$
    }

    @Test
    public void testIsXMLQueryFail3() throws Exception {
        helpTestIsXMLQuery("SELECT * FROM pm1.g1, xmltest.doc1", false); //$NON-NLS-1$
    }

    /**
     * "docA" is ambiguous as there exist two documents called
     * xmlTest2.docA and xmlTest3.docA.  Defect 11479.
     */
    @Test
    public void testIsXMLQueryFail4() throws Exception {
        QueryImpl query = (QueryImpl)helpParse("SELECT * FROM docA"); //$NON-NLS-1$

        try {
            TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
            queryResolver.isXMLQuery(query, metadata);
            fail("expected exception"); //$NON-NLS-1$
        } catch (QueryResolverException e) {
            assertEquals("Group specified is ambiguous, resubmit the query by fully qualifying the group name: docA", e.getMessage()); //$NON-NLS-1$
        }
    }

    @Test
    public void testStringConversion1() {
        // Expected left expression
        ElementSymbolImpl e1 = getFactory().newElementSymbol("pm3.g1.e2"); //$NON-NLS-1$
        e1.setType(DefaultDataTypeManager.DefaultDataTypes.DATE.getTypeClass());

        // Expected right expression
        Class srcType = DefaultDataTypeManager.DefaultDataTypes.STRING.getTypeClass();
        String tgtTypeName = DefaultDataTypeManager.DefaultDataTypes.DATE.getId();
        BaseExpression expression = getFactory().newConstant("2003-02-27"); //$NON-NLS-1$

        DefaultFunctionLibrary library = getMetadataFactory().getSystemFunctionManager().getSystemFunctionLibrary();
        TCFunctionDescriptor fd = library.findFunction(FunctionLibrary.FunctionName.CONVERT, new Class[] {srcType,
            DefaultDataTypeManager.DefaultDataTypes.STRING.getTypeClass()});

        FunctionImpl conversion = getFactory().newFunction(fd.getName(),
                                                       new BaseExpression[] {expression, getFactory().newConstant(tgtTypeName)});
        conversion.setType(getDataTypeManager().getDataTypeClass(tgtTypeName));
        conversion.setFunctionDescriptor(fd);
        conversion.makeImplicit();

        // Expected criteria
        CompareCriteriaImpl expected = getFactory().newCompareCriteria(e1, Operator.EQ, conversion);

        // Resolve the query and check against expected objects
        CompareCriteriaImpl actual = (CompareCriteriaImpl)helpResolveCriteria("pm3.g1.e2='2003-02-27'"); //$NON-NLS-1$

        //if (! actual.getLeftExpression().equals(expected.getLeftExpression())) {
        //	fail("left exprs not equal");
        //} else if (!actual.getRightExpression().equals(expected.getRightExpression())) {
        //	fail("right not equal");
        //}

        assertEquals("Did not match expected criteria", expected, actual); //$NON-NLS-1$
    }

    @Test
    public void testStringConversion2() {
        // Expected left expression
        ElementSymbolImpl e1 = getFactory().newElementSymbol("pm3.g1.e2"); //$NON-NLS-1$
        e1.setType(DefaultDataTypeManager.DefaultDataTypes.DATE.getTypeClass());

        // Expected right expression
        Class srcType = DefaultDataTypeManager.DefaultDataTypes.STRING.getTypeClass();
        String tgtTypeName = DefaultDataTypeManager.DefaultDataTypes.DATE.getId();
        BaseExpression expression = getFactory().newConstant("2003-02-27"); //$NON-NLS-1$

        DefaultFunctionLibrary library = getMetadataFactory().getSystemFunctionManager().getSystemFunctionLibrary();
        TCFunctionDescriptor fd = library.findFunction(FunctionLibrary.FunctionName.CONVERT, new Class[] {srcType,
            DefaultDataTypeManager.DefaultDataTypes.STRING.getTypeClass()});

        FunctionImpl conversion = getFactory().newFunction(fd.getName(),
                                                       new BaseExpression[] {expression, getFactory().newConstant(tgtTypeName)});
        conversion.setType(getDataTypeManager().getDataTypeClass(tgtTypeName));
        conversion.setFunctionDescriptor(fd);
        conversion.makeImplicit();

        // Expected criteria
        CompareCriteriaImpl expected = getFactory().newCompareCriteria(conversion, Operator.EQ, e1);

        // Resolve the query and check against expected objects
        CompareCriteriaImpl actual = (CompareCriteriaImpl)helpResolveCriteria("'2003-02-27'=pm3.g1.e2"); //$NON-NLS-1$

        //if (! actual.getLeftExpression().equals(expected.getLeftExpression())) {
        //	fail("Left expressions not equal");
        //} else if (!actual.getRightExpression().equals(expected.getRightExpression())) {
        //	fail("Right expressions not equal");
        //}

        assertEquals("Did not match expected criteria", expected, actual); //$NON-NLS-1$
    }

    // special test for both sides are String
    @Test
    public void testStringConversion3() {
        // Expected left expression
        ElementSymbolImpl e1 = getFactory().newElementSymbol("pm3.g1.e1"); //$NON-NLS-1$
        e1.setType(DefaultDataTypeManager.DefaultDataTypes.STRING.getTypeClass());

        // Expected right expression
        ConstantImpl e2 = getFactory().newConstant("2003-02-27"); //$NON-NLS-1$

        // Expected criteria
        CompareCriteriaImpl expected = getFactory().newCompareCriteria(e1, Operator.EQ, e2);

        // Resolve the query and check against expected objects
        CompareCriteriaImpl actual = (CompareCriteriaImpl)helpResolveCriteria("pm3.g1.e1='2003-02-27'"); //$NON-NLS-1$

        //if (! actual.getLeftExpression().equals(expected.getLeftExpression())) {
        //	System.out.println("left exprs not equal");
        //} else if (!actual.getRightExpression().equals(expected.getRightExpression())) {
        //	System.out.println("right exprs not equal");
        //}

        assertEquals("Did not match expected criteria", expected, actual); //$NON-NLS-1$
    }

    @Test
    public void testDateToTimestampConversion_defect9747() {
        // Expected left expression
        ElementSymbolImpl e1 = getFactory().newElementSymbol("pm3.g1.e4"); //$NON-NLS-1$
        e1.setType(DefaultDataTypeManager.DefaultDataTypes.TIMESTAMP.getTypeClass());

        // Expected right expression
        ConstantImpl e2 = getFactory().newConstant(TimestampUtil.createDate(96, 0, 31), DefaultDataTypeManager.DefaultDataTypes.DATE.getTypeClass());
        FunctionImpl f1 = getFactory().newFunction("convert", new BaseExpression[] {e2, getFactory().newConstant(DefaultDataTypeManager.DefaultDataTypes.TIMESTAMP.getId())}); //$NON-NLS-1$
        f1.makeImplicit();

        // Expected criteria
        CompareCriteriaImpl expected = getFactory().newCompareCriteria(e1, Operator.GT, f1);

        // Resolve the query and check against expected objects
        CompareCriteriaImpl actual = (CompareCriteriaImpl)helpResolveCriteria("pm3.g1.e4 > {d '1996-01-31'}"); //$NON-NLS-1$

        assertEquals("Did not match expected criteria", expected, actual); //$NON-NLS-1$
    }

    @Test
    public void testFailedConversion_defect9725() throws Exception {
        helpResolveException("select * from pm3.g1 where pm3.g1.e4 > {b 'true'}", "TEIID30072 The expressions in this criteria are being compared but are of differing types (timestamp and boolean) and no implicit conversion is available: pm3.g1.e4 > TRUE"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testLookupFunction() {
        String sql = "SELECT lookup('pm1.g1', 'e1', 'e2', e2) AS x, lookup('pm1.g1', 'e4', 'e3', e3) AS y FROM pm1.g1"; //$NON-NLS-1$
        QueryImpl resolvedQuery = (QueryImpl)helpResolve(sql);
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g1"}); //$NON-NLS-1$
        helpCheckSelect(resolvedQuery, new String[] {"x", "y"}); //$NON-NLS-1$ //$NON-NLS-2$
        helpCheckElements(resolvedQuery.getSelect(), new String[] {"PM1.G1.E2", "PM1.G1.E3"}, //$NON-NLS-1$ //$NON-NLS-2$
                          new String[] {"PM1.G1.E2", "PM1.G1.E3"}); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("Resolved string form was incorrect ", sql, resolvedQuery.toString()); //$NON-NLS-1$

        List projSymbols = resolvedQuery.getSelect().getProjectedSymbols();
        assertEquals("Wrong number of projected symbols", 2, projSymbols.size()); //$NON-NLS-1$
        assertEquals("Wrong type for first symbol", String.class, ((BaseExpression)projSymbols.get(0)).getType()); //$NON-NLS-1$
        assertEquals("Wrong type for second symbol", Double.class, ((BaseExpression)projSymbols.get(1)).getType()); //$NON-NLS-1$
    }

    @Test
    public void testLookupFunctionFailBadElement() {
        String sql = "SELECT lookup('nosuch', 'elementhere', 'e2', e2) AS x FROM pm1.g1"; //$NON-NLS-1$
        helpResolveException(sql);
    }

    @Test
    public void testLookupFunctionFailNotConstantArg1() {
        String sql = "SELECT lookup(e1, 'e1', 'e2', e2) AS x FROM pm1.g1"; //$NON-NLS-1$
        helpResolveException(sql);
    }

    @Test
    public void testLookupFunctionFailNotConstantArg2() {
        String sql = "SELECT lookup('pm1.g1', e1, 'e2', e2) AS x FROM pm1.g1"; //$NON-NLS-1$
        helpResolveException(sql);
    }

    @Test
    public void testLookupFunctionFailNotConstantArg3() {
        String sql = "SELECT lookup('pm1.g1', 'e1', e1, e2) AS x FROM pm1.g1"; //$NON-NLS-1$
        helpResolveException(sql);
    }

    @Test
    public void testLookupFunctionVirtualGroup() throws Exception {
        String sql = "SELECT lookup('vm1.g1', 'e1', 'e2', e2)  FROM vm1.g1 "; //$NON-NLS-1$
        QueryImpl command = (QueryImpl)helpParse(sql);
        TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
        queryResolver.resolveCommand(command, getMetadataFactory().example1Cached());
    }

    @Test
    public void testLookupFunctionPhysicalGroup() throws Exception {
        String sql = "SELECT lookup('pm1.g1', 'e1', 'e2', e2)  FROM pm1.g1 "; //$NON-NLS-1$
        QueryImpl command = (QueryImpl)helpParse(sql);
        TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
        queryResolver.resolveCommand(command, getMetadataFactory().example1Cached());
    }

    @Test
    public void testLookupFunctionFailBadKeyElement() throws Exception {
        String sql = "SELECT lookup('pm1.g1', 'e1', 'x', e2) AS x, lookup('pm1.g1', 'e4', 'e3', e3) AS y FROM pm1.g1"; //$NON-NLS-1$
        CommandImpl command = getQueryParser().parseCommand(sql);
        try {
            TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
            queryResolver.resolveCommand(command, metadata);
            fail("exception expected"); //$NON-NLS-1$
        } catch (QueryResolverException e) {
            // successful test
        }
    }

    @Test
    public void testNamespacedFunction() throws Exception {
        String sql = "SELECT namespace.func('e1')  FROM vm1.g1 "; //$NON-NLS-1$

        QueryMetadataInterface metadata = getMetadataFactory().createTransformationMetadata(getMetadataFactory().example1Cached().getMetadataStore(),
                                                                                        "example1",
                                                                                        new FunctionTree(
                                                                                                         getTeiidVersion(),
                                                                                                         "foo",
                                                                                                         new FakeFunctionMetadataSource()));

        QueryImpl command = (QueryImpl)helpParse(sql);
        TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
        queryResolver.resolveCommand(command, metadata);

        command = (QueryImpl)helpParse("SELECT func('e1')  FROM vm1.g1 ");
        queryResolver.resolveCommand(command, metadata);
    }

    // special test for both sides are String
    @Test
    public void testSetCriteriaCastFromExpression_9657() {
        // parse
        CriteriaImpl expected = null;
        CriteriaImpl actual = null;
        try {
            actual = getQueryParser().parseCriteria("bqt1.smalla.shortvalue IN (1, 2)"); //$NON-NLS-1$
            expected = getQueryParser().parseCriteria("convert(bqt1.smalla.shortvalue, integer) IN (1, 2)"); //$NON-NLS-1$

        } catch (Exception e) {
            fail("Exception during parsing (" + e.getClass().getName() + "): " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
        }

        // resolve
        try {
            TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
            queryResolver.resolveCriteria(expected, getMetadataFactory().exampleBQTCached());
            queryResolver.resolveCriteria(actual, getMetadataFactory().exampleBQTCached());
        } catch (Exception e) {
            fail("Exception during resolution (" + e.getClass().getName() + "): " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
        }

        // Tweak expected to hide convert function - this is expected
        ((FunctionImpl)((SetCriteriaImpl)expected).getExpression()).makeImplicit();

        assertEquals("Did not match expected criteria", expected, actual); //$NON-NLS-1$
    }

    /** select e1 from pm1.g1 where e2 BETWEEN 1000 AND 2000 */
    @Test
    public void testBetween1() {
        String sql = "select e1 from pm1.g1 where e2 BETWEEN 1000 AND 2000"; //$NON-NLS-1$
        helpResolve(sql);
    }

    /** select e1 from pm1.g1 where e2 NOT BETWEEN 1000 AND 2000 */
    @Test
    public void testBetween2() {
        String sql = "select e1 from pm1.g1 where e2 NOT BETWEEN 1000 AND 2000"; //$NON-NLS-1$
        helpResolve(sql);
    }

    /** select e2 from pm1.g1 where e4 BETWEEN 1000 AND e2 */
    @Test
    public void testBetween3() {
        String sql = "select e2 from pm1.g1 where e4 BETWEEN 1000 AND e2"; //$NON-NLS-1$
        helpResolve(sql);
    }

    /** select e2 from pm1.g1 where e2 BETWEEN 1000 AND e4 */
    @Test
    public void testBetween4() {
        String sql = "select e2 from pm1.g1 where e2 BETWEEN 1000 AND e4"; //$NON-NLS-1$
        helpResolve(sql);
    }

    /** select e1 from pm1.g1 where 1000 BETWEEN e1 AND e2 */
    @Test
    public void testBetween5() {
        String sql = "select e1 from pm1.g1 where 1000 BETWEEN e1 AND e2"; //$NON-NLS-1$
        helpResolve(sql);
    }

    /** select e1 from pm1.g1 where 1000 BETWEEN e2 AND e1 */
    @Test
    public void testBetween6() {
        String sql = "select e1 from pm1.g1 where 1000 BETWEEN e2 AND e1"; //$NON-NLS-1$
        helpResolve(sql);
    }

    /** select e1 from pm3.g1 where e2 BETWEEN e3 AND e4 */
    @Test
    public void testBetween7() {
        String sql = "select e1 from pm3.g1 where e2 BETWEEN e3 AND e4"; //$NON-NLS-1$
        helpResolve(sql);
    }

    /** select pm3.g1.e1 from pm3.g1, pm3.g2 where pm3.g1.e4 BETWEEN pm3.g1.e2 AND pm3.g2.e2 */
    @Test
    public void testBetween8() {
        String sql = "select pm3.g1.e1 from pm3.g1, pm3.g2 where pm3.g1.e4 BETWEEN pm3.g1.e2 AND pm3.g2.e2"; //$NON-NLS-1$
        helpResolve(sql);
    }

    /** select e1 from pm1.g1 where e2 = any (select e2 from pm4.g1) */
    @Test
    public void testCompareSubQuery1() {

        String sql = "select e1 from pm1.g1 where e2 = any (select e2 from pm4.g1)"; //$NON-NLS-1$
        QueryImpl outerQuery = (QueryImpl)this.helpResolveSubquery(sql, new String[0]);

        helpCheckFrom(outerQuery, new String[] {"pm1.g1"}); //$NON-NLS-1$
        helpCheckSelect(outerQuery, new String[] {"pm1.g1.e1"}); //$NON-NLS-1$
        helpCheckElements(outerQuery.getSelect(), new String[] {"pm1.g1.e1"}, //$NON-NLS-1$
                          new String[] {"pm1.g1.e1"}); //$NON-NLS-1$
        //        helpCheckFrom(innerQuery, new String[] { "pm4.g1" });
        //        helpCheckSelect(innerQuery, new String[] { "pm4.g1.e2" });
        //        helpCheckElements(innerQuery.getSelect(),
        //            new String[] { "pm4.g1.e2" },
        //            new String[] { "pm4.g1.e2" } );

        String sqlActual = "SELECT e1 FROM pm1.g1 WHERE e2 = ANY (SELECT e2 FROM pm4.g1)"; //$NON-NLS-1$
        assertEquals("Resolved string form was incorrect ", sqlActual, outerQuery.toString()); //$NON-NLS-1$
    }

    /** select e1 from pm1.g1 where e2 = all (select e2 from pm4.g1) */
    @Test
    public void testCompareSubQuery2() {
        String sql = "select e1 from pm1.g1 where e2 = all (select e2 from pm4.g1)"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[0]);
    }

    /** select e1 from pm1.g1 where e2 < (select e2 from pm4.g1 where e1 = '3') */
    @Test
    public void testCompareSubQuery3() {
        String sql = "select e1 from pm1.g1 where e2 < (select e2 from pm4.g1 where e1 = '3')"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[0]);
    }

    /** select e1 from pm1.g1 where e2 < (select e2 from pm4.g1 where e1 = '3') */
    @Test
    public void testCompareSubQueryImplicitConversion() {
        String sql = "select e1 from pm1.g1 where e1 < (select e2 from pm4.g1 where e1 = '3')"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[0]);
    }

    @Test
    public void testExistsSubQuery() {
        String sql = "select e1 from pm1.g1 where exists (select e2 from pm4.g1)"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[0]);
    }

    @Test
    public void testExistsSubQuery2() {
        String sql = "select e1 from pm1.g1 where exists (select e1, e2 from pm4.g1)"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[0]);
    }

    @Test
    public void testScalarSubQueryInSelect() {
        String sql = "select e1, (select e2 from pm4.g1 where e1 = '3') from pm1.g1"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[0]);
    }

    @Test
    public void testScalarSubQueryInSelect2() {
        String sql = "select (select e2 from pm4.g1 where e1 = '3'), e1 from pm1.g1"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[0]);
    }

    @Test
    public void testScalarSubQueryInSelectWithAlias() {
        String sql = "select e1, (select e2 from pm4.g1 where e1 = '3') as X from pm1.g1"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[0]);
    }

    @Test
    public void testSelectWithNoFrom() {
        String sql = "SELECT 5"; //$NON-NLS-1$
        helpResolve(sql);
    }

    @Test
    public void testSelectWithNoFrom_Alias() {
        String sql = "SELECT 5 AS INTKEY"; //$NON-NLS-1$
        helpResolve(sql);
    }

    @Test
    public void testSelectWithNoFrom_Alias_OrderBy() {
        String sql = "SELECT 5 AS INTKEY ORDER BY INTKEY"; //$NON-NLS-1$
        helpResolve(sql);
    }

    @Test
    public void testSubqueryCorrelatedInCriteria() {
        String sql = "select e2 from pm1.g1 where e2 = all (select e2 from pm4.g1 where pm1.g1.e1 = pm4.g1.e1)"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[] {"pm1.g1.e1"}); //$NON-NLS-1$
    }

    @Test
    public void testSubqueryCorrelatedInCriteria2() {
        String sql = "select e1 from pm1.g1 where e2 = all (select e2 from pm4.g1 where pm1.g1.e1 = e1)"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[] {"pm1.g1.e1"}); //$NON-NLS-1$
    }

    @Test
    public void testSubqueryCorrelatedInCriteria3() {
        String sql = "select e1 from pm1.g1 X where e2 = all (select e2 from pm4.g1 where X.e1 = pm4.g1.e1)"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[] {"X.e1"}); //$NON-NLS-1$
    }

    @Test
    public void testSubqueryCorrelatedInCriteria4() {
        String sql = "select e2 from pm1.g1 X where e2 in (select e2 from pm1.g1 Y where X.e1 = Y.e1)"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[] {"X.e1"}); //$NON-NLS-1$
    }

    @Test
    public void testSubqueryCorrelatedInCriteria5() {
        String sql = "select e1 from pm1.g1 X where e2 = all (select e2 from pm1.g1 Y where X.e1 = e1)"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[] {"X.e1"}); //$NON-NLS-1$
    }

    /* 'e5' is only in pm4.g2 */
    @Test
    public void testSubqueryCorrelatedInCriteria6() {
        String sql = "select e1 from pm4.g2 where e2 = some (select e2 from pm4.g1 where e5 = e1)"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[] {"pm4.g2.e5"}); //$NON-NLS-1$
    }

    /* 'e5' is only in pm4.g2 */
    @Test
    public void testSubqueryCorrelatedInCriteria7() {
        String sql = "select e1 from pm4.g2 where exists (select e2 from pm4.g1 where e5 = e1)"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[] {"pm4.g2.e5"}); //$NON-NLS-1$
    }

    @Test
    public void testSubqueryCorrelatedInHaving() {
        String sql = "select e1, e2 from pm4.g2 group by e2 having e2 in (select e2 from pm4.g1 where e5 = e1)"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[] {"pm4.g2.e5"}); //$NON-NLS-1$
    }

    @Test
    public void testSubqueryCorrelatedInHaving2() {
        String sql = "select e1, e2 from pm4.g2 group by e2 having e2 <= all (select e2 from pm4.g1 where e5 = e1)"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[] {"pm4.g2.e5"}); //$NON-NLS-1$
    }

    /* 'e5' is only in pm4.g2 */
    @Test
    public void testSubqueryCorrelatedInSelect() {
        String sql = "select e1, (select e2 from pm4.g1 where e5 = e1) from pm4.g2"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[] {"pm4.g2.e5"}); //$NON-NLS-1$
    }

    @Test
    public void testSubqueryCorrelatedInSelect2() {
        String sql = "select e1, (select e2 from pm4.g1 where pm4.g2.e5 = e1) from pm4.g2"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[] {"pm4.g2.e5"}); //$NON-NLS-1$
    }

    @Test
    public void testSubqueryCorrelatedInSelect3() {
        String sql = "select e1, (select e2 from pm4.g1 Y where X.e5 = Y.e1) from pm4.g2 X"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[] {"X.e5"}); //$NON-NLS-1$
    }

    /* 'e5' is only in pm4.g2 */
    @Test
    public void testNestedCorrelatedSubqueries() {
        String sql = "select e1, (select e2 from pm1.g1 where e2 = all (select e2 from pm4.g1 where e5 = e1)) from pm4.g2"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[] {"pm4.g2.e5"}); //$NON-NLS-1$
    }

    /**
     * 'e5' is in pm4.g2, so it will be resolved to the group aliased as 'Y'
     */
    @Test
    public void testNestedCorrelatedSubqueries2() {
        String sql = "select e1, (select e2 from pm4.g2 Y where e2 = all (select e2 from pm4.g1 where e5 = e1)) from pm4.g2 X"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[] {"Y.e5"}); //$NON-NLS-1$
    }

    /**
     *  'e5' is in pm4.g2; it will be resolved to the group aliased as 'X' 
     */
    @Test
    public void testNestedCorrelatedSubqueries3() {
        String sql = "select e1, (select e2 from pm4.g2 Y where e2 = all (select e2 from pm4.g1 where X.e5 = e1)) from pm4.g2 X"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[] {"X.e5"}); //$NON-NLS-1$
    }

    /**
     *  'e5' is in X and Y 
     */
    @Test
    public void testNestedCorrelatedSubqueries4() {
        String sql = "select X.e2 from pm4.g2 Y, pm4.g2 X where X.e2 = all (select e2 from pm4.g1 where e5 = e1)"; //$NON-NLS-1$
        helpResolveException(sql,
                             metadata,
                             "TEIID31117 Element \"e5\" is ambiguous and should be qualified, at a single scope it exists in [pm4.g2 AS Y, pm4.g2 AS X]"); //$NON-NLS-1$
    }

    @Test
    public void testSubqueryCorrelatedInCriteriaVirtualLayer() {
        String sql = "select e2 from vm1.g1 where e2 = all (select e2 from vm1.g2 where vm1.g1.e1 = vm1.g2.e1)"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[] {"vm1.g1.e1"}); //$NON-NLS-1$
    }

    @Test
    public void testSubqueryCorrelatedInCriteriaVirtualLayer2() {
        String sql = "select e2 from vm1.g1 X where e2 = all (select e2 from vm1.g2 where X.e1 = vm1.g2.e1)"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[] {"X.e1"}); //$NON-NLS-1$
    }

    /** 
     * Although this query makes no sense, the "e1" in the nested criteria is
     * NOT a correlated reference 
     */
    @Test
    public void testSubqueryNonCorrelatedInCriteria() {
        String sql = "select e2 from pm1.g1 where e2 = all (select e2 from pm4.g1)"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[0]);
    }

    /** 
     * Although this query makes no sense, the "e1" in the nested criteria is
     * NOT a correlated reference 
     */
    @Test
    public void testSubqueryNonCorrelatedInCriteria2() {
        String sql = "SELECT e1 FROM pm1.g1 WHERE e2 IN (SELECT e2 FROM pm2.g1 WHERE e1 IN (SELECT e1 FROM pm1.g1))"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[0]);
    }

    /** 
     * Although this query makes no sense, the "e1" in the nested criteria is
     * NOT a correlated reference 
     */
    @Test
    public void testSubqueryNonCorrelatedInCriteria3() {
        String sql = "SELECT e2 FROM pm2.g1 WHERE e1 IN (SELECT e1 FROM pm1.g1)"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[0]);
    }

    /** 
     * The group pm1.g1 in the FROM clause of the subquery should resolve to the 
     * group in metadata, not the temporary child metadata group defined by the
     * outer query.
     */
    @Test
    public void testSubquery_defect10090() {
        String sql = "select pm1.g1.e1 from pm1.g1 where pm1.g1.e2 in (select pm1.g1.e2 from pm1.g1 where pm1.g1.e4 = 2.0)"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[0]);
    }

    /**
     * Workaround is to alias group in FROM of outer query (aliasing subquery group doesn't work)
     */
    @Test
    public void testSubquery_defect10090Workaround() {
        String sql = "select X.e1 from pm1.g1 X where X.e2 in (select pm1.g1.e2 from pm1.g1 where pm1.g1.e4 = 2.0)"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[0]);
    }

    @Test
    public void testSubquery2_defect10090() {
        String sql = "select pm1.g1.e1 from pm1.g1 where pm1.g1.e2 in (select X.e2 from pm1.g1 X where X.e4 = 2.0)"; //$NON-NLS-1$
        this.helpResolveSubquery(sql, new String[0]);
    }

    /** test jdbc USER method */
    @Test
    public void testUser() {
        //String sql = "select intkey from SmallA where user() = 'bqt2'";

        // Expected left expression
        DefaultFunctionLibrary library = getMetadataFactory().getSystemFunctionManager().getSystemFunctionLibrary();
        TCFunctionDescriptor fd = library.findFunction(FunctionLibrary.FunctionName.USER, new Class[] {});
        FunctionImpl user = getFactory().newFunction(fd.getName(), new BaseExpression[] {});
        user.setFunctionDescriptor(fd);

        // Expected right expression
        BaseExpression e1 = getFactory().newConstant("bqt2", String.class); //$NON-NLS-1$

        // Expected criteria
        CompareCriteriaImpl expected = getFactory().newCompareCriteria(user, Operator.EQ, e1);

        // Resolve the query and check against expected objects
        CompareCriteriaImpl actual = (CompareCriteriaImpl)helpResolveCriteria("user()='bqt2'"); //$NON-NLS-1$
        assertEquals("Did not match expected criteria", expected, actual); //$NON-NLS-1$
    }

    @Test
    public void testCaseExpression1() {
        String sql = "SELECT e1, CASE e2 WHEN 0 THEN 20 WHEN 1 THEN 21 WHEN 2 THEN 500 END AS testElement FROM pm1.g1" //$NON-NLS-1$
                     + " WHERE e1 = CASE WHEN e2 = 0 THEN 'a' WHEN e2 = 1 THEN 'b' ELSE 'c' END"; //$NON-NLS-1$
        helpResolve(sql);
    }

    @Test
    public void testCaseExpression2() {
        // nested case expressions
        String sql = "SELECT CASE e2" + //$NON-NLS-1$
                     " WHEN 0 THEN CASE e1 " + //$NON-NLS-1$
                     " WHEN 'a' THEN 100" + //$NON-NLS-1$
                     " WHEN 'b' THEN 200 " + //$NON-NLS-1$
                     " ELSE 1000 " + //$NON-NLS-1$
                     " END" + //$NON-NLS-1$
                     " WHEN 1 THEN 21" + //$NON-NLS-1$
                     " WHEN (CASE WHEN e1 = 'z' THEN 2 WHEN e1 = 'y' THEN 100 ELSE 3 END) THEN 500" + //$NON-NLS-1$
                     " END AS testElement FROM pm1.g1"; //$NON-NLS-1$
        helpResolve(sql);
    }

    @Test
    public void testCaseExpressionWithNestedFunction() {
        String sql = "SELECT CASE WHEN e2 < 0 THEN abs(CASE WHEN e2 < 0 THEN -1 ELSE e2 END)" + //$NON-NLS-1$
                     " ELSE e2 END FROM pm1.g1"; //$NON-NLS-1$
        helpResolve(sql);
    }

    @Test
    public void testFunctionWithNestedCaseExpression() {
        String sql = "SELECT abs(CASE e1 WHEN 'testString1' THEN -13" + //$NON-NLS-1$
                     " WHEN 'testString2' THEN -5" + //$NON-NLS-1$
                     " ELSE abs(e2)" + //$NON-NLS-1$
                     " END) AS absVal FROM pm1.g1"; //$NON-NLS-1$
        helpResolve(sql);
    }

    @Test
    public void testDefect10809() {
        String sql = "select * from LOB_TESTING_ONE where CLOB_COLUMN LIKE '%fff%'"; //$NON-NLS-1$
        helpResolve(helpParse(sql), getMetadataFactory().exampleBQTCached());
    }

    @Test
    public void testNonAutoConversionOfLiteralIntegerToShort() throws Exception {
        // parse
        QueryImpl command = (QueryImpl)getQueryParser().parseCommand("SELECT intkey FROM bqt1.smalla WHERE shortvalue = 5"); //$NON-NLS-1$

        // resolve
        TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
        queryResolver.resolveCommand(command, getMetadataFactory().exampleBQTCached());

        // Check whether an implicit conversion was added on the correct side
        CompareCriteriaImpl crit = (CompareCriteriaImpl)command.getCriteria();

        assertEquals(DefaultDataTypeManager.DefaultDataTypes.SHORT.getTypeClass(), crit.getRightExpression().getType());
        assertEquals("Sql is incorrect after resolving", "SELECT intkey FROM bqt1.smalla WHERE shortvalue = 5", command.toString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testNonAutoConversionOfLiteralIntegerToShort2() throws Exception {
        // parse
        QueryImpl command = (QueryImpl)getQueryParser().parseCommand("SELECT intkey FROM bqt1.smalla WHERE 5 = shortvalue"); //$NON-NLS-1$

        // resolve
        TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
        queryResolver.resolveCommand(command, getMetadataFactory().exampleBQTCached());

        // Check whether an implicit conversion was added on the correct side
        CompareCriteriaImpl crit = (CompareCriteriaImpl)command.getCriteria();

        assertEquals(DefaultDataTypeManager.DefaultDataTypes.SHORT.getTypeClass(), crit.getLeftExpression().getType());
        assertEquals("Sql is incorrect after resolving", "SELECT intkey FROM bqt1.smalla WHERE 5 = shortvalue", command.toString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testAliasedOrderBy() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT pm1.g1.e1 as y FROM pm1.g1 ORDER BY y"); //$NON-NLS-1$
        helpCheckFrom(resolvedQuery, new String[] {"pm1.g1"}); //$NON-NLS-1$
        helpCheckSelect(resolvedQuery, new String[] {"y"}); //$NON-NLS-1$
    }

    @Test
    public void testUnaliasedOrderBySucceeds() {
        helpResolve("SELECT pm1.g1.e1 a, pm1.g1.e1 b FROM pm1.g1 ORDER BY pm1.g1.e1"); //$NON-NLS-1$
    }

    @Test
    public void testUnaliasedOrderBySucceeds1() {
        helpResolve("SELECT pm1.g1.e1 a FROM pm1.g1 group by pm1.g1.e1 ORDER BY pm1.g1.e1"); //$NON-NLS-1$
    }

    @Test
    public void testUnaliasedOrderByFails() {
        helpResolveException("SELECT pm1.g1.e1 e2 FROM pm1.g1 group by pm1.g1.e1 ORDER BY pm1.g1.e2"); //$NON-NLS-1$
    }

    @Test
    public void testUnaliasedOrderByFails1() {
        helpResolveException("SELECT pm1.g1.e1 e2 FROM pm1.g1 group by pm1.g1.e1 ORDER BY pm1.g1.e2 + 1"); //$NON-NLS-1$
    }

    /** 
     * the group g1 is not known to the order by clause of a union
     */
    @Test
    public void testUnionOrderByFail() {
        helpResolveException("SELECT pm1.g1.e1 FROM pm1.g1 UNION SELECT pm1.g2.e1 FROM pm1.g2 ORDER BY g1.e1", "TEIID30086 ORDER BY expression 'g1.e1' cannot be used with a set query."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testUnionOrderByFail1() {
        helpResolveException("SELECT pm1.g1.e1 FROM pm1.g1 UNION SELECT pm1.g2.e1 FROM pm1.g2 ORDER BY pm1.g1.e1", "TEIID30086 ORDER BY expression 'pm1.g1.e1' cannot be used with a set query."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testOrderByPartiallyQualified() {
        helpResolve("SELECT pm1.g1.e1 FROM pm1.g1 ORDER BY g1.e1"); //$NON-NLS-1$
    }

    /** 
     * the group g1 is not known to the order by clause of a union
     */
    @Test
    public void testUnionOrderBy() {
        helpResolve("SELECT pm1.g1.e1 FROM pm1.g1 UNION SELECT pm1.g2.e1 FROM pm1.g2 ORDER BY e1"); //$NON-NLS-1$
    }

    /** 
     * Test for defect 12087 - Insert with implicit conversion from integer to short
     */
    @Test
    public void testImplConversionBetweenIntAndShort() throws Exception {
        InsertImpl command = (InsertImpl)getQueryParser().parseCommand("Insert into pm5.g3(e2) Values(100)"); //$NON-NLS-1$
        TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
        queryResolver.resolveCommand(command, metadata);
        assertTrue(command.getValues().get(0).getType() == DefaultDataTypeManager.DefaultDataTypes.SHORT.getTypeClass());
    }

    public TransformationMetadata example_12968() {
        MetadataStore metadataStore = new MetadataStore();
        // Create models
        Schema pm1 = getMetadataFactory().createPhysicalModel("myModel", metadataStore); //$NON-NLS-1$
        Schema pm2 = getMetadataFactory().createPhysicalModel("myModel2", metadataStore); //$NON-NLS-1$

        Table pm1g1 = getMetadataFactory().createPhysicalGroup("myTable", pm1); //$NON-NLS-1$
        Table pm2g1 = getMetadataFactory().createPhysicalGroup("mySchema.myTable2", pm2); //$NON-NLS-1$

        getMetadataFactory().createElements(pm1g1, new String[] {"myColumn", "myColumn2"}, //$NON-NLS-1$ //$NON-NLS-2$ 
                                       new String[] {DefaultDataTypeManager.DefaultDataTypes.STRING.getId(),
                                           DefaultDataTypeManager.DefaultDataTypes.INTEGER.getId()});
        getMetadataFactory().createElements(pm2g1, new String[] {"myColumn", "myColumn2"}, //$NON-NLS-1$ //$NON-NLS-2$ 
                                       new String[] {DefaultDataTypeManager.DefaultDataTypes.STRING.getId(),
                                           DefaultDataTypeManager.DefaultDataTypes.INTEGER.getId()});

        return getMetadataFactory().createTransformationMetadata(metadataStore, "12968");
    }

    @Test
    public void testDefect12968_union() {
        helpResolve(helpParse("SELECT myModel.myTable.myColumn AS myColumn from myModel.myTable UNION " + //$NON-NLS-1$
                              "SELECT convert(null, string) AS myColumn From myModel2.mySchema.myTable2"), //$NON-NLS-1$
                    example_12968());
    }

    @Test
    public void testUnionQueryWithNull() throws Exception {
        helpResolve("SELECT NULL, e2 FROM pm1.g2 UNION ALL SELECT e1, e2 FROM pm1.g3"); //$NON-NLS-1$
        helpResolve("SELECT e1, e2 FROM pm1.g1 UNION ALL SELECT NULL, e2 FROM pm1.g2 UNION ALL SELECT e1, e2 FROM pm1.g3"); //$NON-NLS-1$
        helpResolve("SELECT e1, NULL FROM pm1.g2 UNION ALL SELECT e1, e2 FROM pm1.g3"); //$NON-NLS-1$
        helpResolve("SELECT e1, NULL FROM pm1.g2 UNION ALL SELECT e1, NULL FROM pm1.g3"); //$NON-NLS-1$
        helpResolve("SELECT e1, NULL as e2 FROM pm1.g2 UNION ALL SELECT e1, e2 FROM pm1.g3"); //$NON-NLS-1$
        helpResolve("SELECT e1, NULL as e2 FROM pm1.g1 UNION ALL SELECT e1, e3 FROM pm1.g2"); //$NON-NLS-1$
    }

    @Test
    public void testUnionQueryWithDiffTypes() throws Exception {
        helpResolve("SELECT e1, e3 FROM pm1.g1 UNION ALL SELECT e2, e3 FROM pm1.g2"); //$NON-NLS-1$
        helpResolve("SELECT e1, e3 FROM pm1.g1 UNION ALL SELECT e2, e3 FROM pm1.g2 UNION ALL SELECT NULL, e3 FROM pm1.g2"); //$NON-NLS-1$
        helpResolve("SELECT e1, e3 FROM pm1.g1 UNION ALL SELECT e3, e3 FROM pm1.g2 UNION ALL SELECT NULL, e3 FROM pm1.g2"); //$NON-NLS-1$
        helpResolve("SELECT e1, e2 FROM pm1.g3 UNION ALL SELECT MAX(e4), e2 FROM pm1.g1 UNION ALL SELECT e3, e2 FROM pm1.g2"); //$NON-NLS-1$
        helpResolve("SELECT e1, e4 FROM pm1.g1 UNION ALL SELECT e2, e3 FROM pm1.g2"); //$NON-NLS-1$
        helpResolve("SELECT e4, e2 FROM pm1.g1 UNION ALL SELECT e3, e2 FROM pm1.g2"); //$NON-NLS-1$
        helpResolve("SELECT e1, e2 FROM pm1.g1 UNION ALL SELECT e3, e4 FROM pm1.g2"); //$NON-NLS-1$
        helpResolve("SELECT e4, e2 FROM pm1.g1 UNION ALL SELECT e3, e2 FROM pm1.g2 UNION ALL SELECT e1, e2 FROM pm1.g2"); //$NON-NLS-1$
        helpResolve("SELECT e4, e2 FROM pm1.g1 UNION ALL SELECT e1, e2 FROM pm1.g2"); //$NON-NLS-1$
        helpResolve("SELECT MAX(e4), e2 FROM pm1.g1 UNION ALL SELECT e3, e2 FROM pm1.g2"); //$NON-NLS-1$
        //chooses a common type
        helpResolve("select e2 from pm3.g1 union select e3 from pm3.g1 union select e4 from pm3.g1"); //$NON-NLS-1$
    }

    @Test
    public void testUnionQueryWithDiffTypesFails() throws Exception {
        helpResolveException("SELECT e1 FROM pm1.g1 UNION (SELECT e2 FROM pm1.g2 UNION SELECT e2 from pm1.g1 order by e2)", "The Expression e2 used in a nested UNION ORDER BY clause cannot be implicitly converted from type integer to type string."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testNestedUnionQueryWithNull() throws Exception {
        SetQueryImpl command = (SetQueryImpl)helpResolve("SELECT e2, e3 FROM pm1.g1 UNION (SELECT null, e3 FROM pm1.g2 UNION SELECT null, e3 from pm1.g1)"); //$NON-NLS-1$

        assertEquals(DefaultDataTypeManager.DefaultDataTypes.INTEGER.getTypeClass(),
                     command.getProjectedSymbols().get(0).getType());
    }

    @Test
    public void testUnionQueryClone() throws Exception {
        SetQueryImpl command = (SetQueryImpl)helpResolve("SELECT e2, e3 FROM pm1.g1 UNION SELECT e3, e2 from pm1.g1"); //$NON-NLS-1$

        assertEquals(DefaultDataTypeManager.DefaultDataTypes.INTEGER.getTypeClass(),
                     command.getProjectedSymbols().get(1).getType());

        command = command.clone();

        assertEquals(DefaultDataTypeManager.DefaultDataTypes.INTEGER.getTypeClass(),
                     command.getProjectedSymbols().get(1).getType());
    }

    @Test
    public void testSelectIntoNoFrom() {
        helpResolve("SELECT 'a', 19, {b'true'}, 13.999 INTO pm1.g1"); //$NON-NLS-1$
    }

    @Test
    public void testSelectInto() {
        helpResolve("SELECT e1, e2, e3, e4 INTO pm1.g1 FROM pm1.g2"); //$NON-NLS-1$
    }

    @Test
    public void testSelectIntoTempGroup() {
        helpResolve("SELECT 'a', 19, {b'true'}, 13.999 INTO #myTempTable"); //$NON-NLS-1$
        helpResolve("SELECT e1, e2, e3, e4 INTO #myTempTable FROM pm1.g1"); //$NON-NLS-1$
    }

    //procedural relational mapping
    @Test
    public void testProcInVirtualGroup1() {
        String sql = "select e1 from pm1.vsp26 where param1=1 and param2='a'"; //$NON-NLS-1$
        helpResolve(sql);
    }

    @Test
    public void testProcInVirtualGroup2() {
        String sql = "select * from pm1.vsp26 as p where param1=1 and param2='a'"; //$NON-NLS-1$
        helpResolve(sql);
    }

    @Test
    public void testProcInVirtualGroup3() {
        String sql = "SELECT P.e1 as ve3 FROM pm1.vsp26 as P, pm1.g2 where P.e1=g2.e1 and param1=1 and param2='a'"; //$NON-NLS-1$
        helpResolve(sql);
    }

    @Test
    public void testProcInVirtualGroup4() {
        String sql = "SELECT P.e1 as ve3 FROM pm1.vsp26 as P, vm1.g1 where P.e1=g1.e1 and param1=1 and param2='a'"; //$NON-NLS-1$
        helpResolve(sql);
    }

    @Test
    public void testProcInVirtualGroup5() {
        String sql = "SELECT * FROM (SELECT p.* FROM pm1.vsp26 as P, vm1.g1 where P.e1=g1.e1) x where param1=1 and param2='a'"; //$NON-NLS-1$
        helpResolve(sql);
    }

    @Test
    public void testProcInVirtualGroup6() {
        String sql = "SELECT P.e1 as ve3, P.e2 as ve4 FROM pm1.vsp26 as P where param1=1 and param2='a'"; //$NON-NLS-1$
        helpResolve(sql);
    }

    @Test
    public void testProcInVirtualGroup7() {
        String sql = "SELECT P.e2 as ve3, P.e1 as ve4 FROM pm1.vsp47 as P where param1=1 and param2='a'"; //$NON-NLS-1$
        helpResolve(sql);
    }

    @Test
    public void testProcInVirtualGroup7a() {
        String sql = "SELECT P.e2 as ve3, P.e1 as ve4 FROM pm1.vsp47 as P where param1=1"; //$NON-NLS-1$
        helpResolve(sql);
    }

    @Test
    public void testProcParamComparison_defect13653() {
        String userSql = "SELECT * FROM (EXEC mmspTest1.MMSP5('a')) AS a, (EXEC mmsptest1.mmsp6('b')) AS b"; //$NON-NLS-1$

        QueryMetadataInterface metadata = getMetadataFactory().exampleBQTCached();

        QueryImpl query = (QueryImpl)helpResolve(userSql, metadata);
        FromImpl from = query.getFrom();
        Collection fromClauses = from.getClauses();
        SPParameterImpl params[] = new SPParameterImpl[2];
        Iterator iter = fromClauses.iterator();
        while (iter.hasNext()) {
            SubqueryFromClauseImpl clause = (SubqueryFromClauseImpl)iter.next();
            StoredProcedureImpl proc = (StoredProcedureImpl)clause.getCommand();
            for (SPParameterImpl param : proc.getParameters()) {
                if (param.getParameterType() == SPParameterImpl.IN) {
                    if (params[0] == null) {
                        params[0] = param;
                    } else {
                        params[1] = param;
                    }
                }
            }
        }

        assertTrue("Params should be not equal", !params[0].equals(params[1])); //$NON-NLS-1$
    }

    @Test
    public void testNullConstantInSelect() throws Exception {
        String userSql = "SELECT null as x"; //$NON-NLS-1$
        QueryImpl query = (QueryImpl)helpParse(userSql);

        TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
        queryResolver.resolveCommand(query, getMetadataFactory().exampleBQTCached());

        // Check type of resolved null constant
        BaseExpression symbol = query.getSelect().getSymbols().get(0);
        assertNotNull(symbol.getType());
        assertEquals(DefaultDataTypeManager.DefaultDataTypes.STRING.getTypeClass(), symbol.getType());
    }

    @Test
    public void test11716() throws Exception {
        String sql = "SELECT e1 FROM pm1.g1 where e1='1'"; //$NON-NLS-1$
        Map externalMetadata = new HashMap();
        GroupSymbolImpl inputSet = getFactory().newGroupSymbol("INPUT"); //$NON-NLS-1$
        List inputSetElements = new ArrayList();
        ElementSymbolImpl inputSetElement = getFactory().newElementSymbol("INPUT.e1"); //$NON-NLS-1$
        inputSetElements.add(inputSetElement);
        externalMetadata.put(inputSet, inputSetElements);
        QueryImpl command = (QueryImpl)helpParse(sql);
        TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
        queryResolver.resolveCommand(command, metadata);
        Collection groups = GroupCollectorVisitorImpl.getGroups(command, false);
        assertFalse(groups.contains(inputSet));
    }

    @Test
    public void testDefect16894_resolverException_1() {
        helpResolve("SELECT * FROM (SELECT * FROM Pm1.g1 AS Y) AS X"); //$NON-NLS-1$
    }

    @Test
    public void testDefect16894_resolverException_2() {
        helpResolve("SELECT * FROM (SELECT * FROM Pm1.g1) AS X"); //$NON-NLS-1$
    }

    @Test
    public void testDefect17385() throws Exception {
        String sql = "select e1 as x ORDER BY x"; //$NON-NLS-1$      
        helpResolveException(sql);
    }

    @Test
    public void testValidFullElementNotInQueryGroups() {
        helpResolveException("select pm1.g1.e1 FROM pm1.g1 g"); //$NON-NLS-1$
    }

    @Test
    public void testUnionInSubquery() throws Exception {
        String sql = "SELECT StringKey FROM (SELECT BQT2.SmallB.StringKey FROM BQT2.SmallB union SELECT convert(BQT2.SmallB.FloatNum, string) FROM BQT2.SmallB) x"; //$NON-NLS-1$
        CommandImpl command = getQueryParser().parseCommand(sql);
        TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
        queryResolver.resolveCommand(command, getMetadataFactory().exampleBQTCached());
    }

    @Test
    public void testParameterError() throws Exception {
        helpResolveException("EXEC pm1.sp2(1, 2)", metadata, "TEIID31113 1 extra positional parameter(s) passed to pm1.sp2."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testUnionOfAliasedLiteralsGetsModified() {
        String sql = "SELECT 5 AS x UNION ALL SELECT 10 AS x"; //$NON-NLS-1$
        CommandImpl c = helpResolve(sql);
        assertEquals(sql, c.toString());
    }

    @Test
    public void testXMLWithProcSubquery() {
        String sql = "SELECT * FROM xmltest.doc4 WHERE node2 IN (SELECT e1 FROM (EXEC pm1.vsp1()) AS x)"; //$NON-NLS-1$
        CommandImpl c = helpResolve(sql);
        assertEquals(sql, c.toString());
    }

    @Test
    public void testDefect18832() {
        String sql = "SELECT * from (SELECT null as a, e1 FROM pm1.g1) b"; //$NON-NLS-1$
        CommandImpl c = helpResolve(sql);
        List projectedSymbols = c.getProjectedSymbols();
        for (int i = 0; i < projectedSymbols.size(); i++) {
            ElementSymbolImpl symbol = (ElementSymbolImpl)projectedSymbols.get(i);
            assertTrue(!symbol.getType().equals(DefaultDataTypeManager.DefaultDataTypes.NULL));
        }
    }

    @Test
    public void testDefect18832_2() {
        String sql = "SELECT a.*, b.* from (SELECT null as a, e1 FROM pm1.g1) a, (SELECT e1 FROM pm1.g1) b"; //$NON-NLS-1$
        CommandImpl c = helpResolve(sql);
        List projectedSymbols = c.getProjectedSymbols();
        for (int i = 0; i < projectedSymbols.size(); i++) {
            ElementSymbolImpl symbol = (ElementSymbolImpl)projectedSymbols.get(i);
            assertTrue(!symbol.getType().equals(DefaultDataTypeManager.DefaultDataTypes.NULL));
        }
    }

    @Test
    public void testDefect20113() {
        String sql = "SELECT g1.* from pm1.g1"; //$NON-NLS-1$
        helpResolve(sql);
    }

    @Test
    public void testDefect20113_2() {
        String sql = "SELECT g7.* from g7"; //$NON-NLS-1$
        helpResolve(sql);
    }

    protected void verifyProjectedTypes(CommandImpl c, Class[] types) {
        List projSymbols = c.getProjectedSymbols();
        for (int i = 0; i < projSymbols.size(); i++) {
            assertEquals("Found type mismatch at column " + i, types[i], ((BaseExpression)projSymbols.get(i)).getType()); //$NON-NLS-1$
        }
    }

    @Test
    public void testNestedInlineViews() throws Exception {
        String sql = "SELECT * FROM (SELECT * FROM (SELECT * FROM pm1.g1) AS Y) AS X"; //$NON-NLS-1$
        CommandImpl c = helpResolve(sql);
        assertEquals(sql, c.toString());

        verifyProjectedTypes(c, new Class[] {String.class, Integer.class, Boolean.class, Double.class});
    }

    @Test
    public void testNestedInlineViewsNoStar() throws Exception {
        String sql = "SELECT e1 FROM (SELECT e1 FROM (SELECT e1 FROM pm1.g1) AS Y) AS X"; //$NON-NLS-1$
        CommandImpl c = helpResolve(sql);
        assertEquals(sql, c.toString());

        verifyProjectedTypes(c, new Class[] {String.class});
    }

    @Test
    public void testNestedInlineViewsCount() throws Exception {
        String sql = "SELECT COUNT(*) FROM (SELECT * FROM (SELECT * FROM pm1.g1) AS Y) AS X"; //$NON-NLS-1$
        CommandImpl c = helpResolve(sql);
        assertEquals(sql, c.toString());
        verifyProjectedTypes(c, new Class[] {Integer.class});
    }

    @Test
    public void testAggOverInlineView() throws Exception {
        String sql = "SELECT SUM(x) FROM (SELECT (e2 + 1) AS x FROM pm1.g1) AS g"; //$NON-NLS-1$
        CommandImpl c = helpResolve(sql);
        assertEquals(sql, c.toString());
        verifyProjectedTypes(c, new Class[] {Long.class});

    }

    //procedure - select * from temp table 
    @Test
    public void testDefect20083_1() {
        helpResolve("EXEC pm1.vsp56()"); //$NON-NLS-1$
    }

    //procedure - select * from temp table order by
    @Test
    public void testDefect20083_2() {
        helpResolve("EXEC pm1.vsp57()"); //$NON-NLS-1$
    }

    @Test
    public void testTypeConversionOverUnion() throws Exception {
        String sql = "SELECT * FROM (SELECT e2, e1 FROM pm1.g1 UNION SELECT convert(e2, string), e1 FROM pm1.g1) FOO where e2/2 = 1"; //$NON-NLS-1$ 
        helpResolveException(sql);
    }

    @Test
    public void testVariableDeclarationAfterStatement() throws Exception {
        String procedure = "CREATE VIRTUAL PROCEDURE "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "select * from pm1.g1 where pm1.g1.e1 = VARIABLES.X;\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE string VARIABLES.X = 1;\n"; //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        helpResolveException(procedure, "TEIID31118 Element \"VARIABLES.X\" is not defined by any relevant group."); //$NON-NLS-1$
    }

    /**
     * same as above, but with an xml query 
     * @throws Exception
     */
    @Test
    public void testVariableDeclarationAfterStatement1() throws Exception {
        String procedure = "CREATE VIRTUAL PROCEDURE "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "select * from xmltest.doc1 where node1 = VARIABLES.X;\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE string VARIABLES.X = 1;\n"; //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        helpResolveException(procedure, "TEIID30136 Unable to resolve element: VARIABLES.X"); //$NON-NLS-1$
    }

    @Test
    public void testCreate() {
        String sql = "CREATE LOCAL TEMPORARY TABLE temp_table (column1 string)"; //$NON-NLS-1$
        CommandImpl c = helpResolve(sql);
        assertEquals(sql, c.toString());
    }

    @Test
    public void testCreateQualifiedName() {
        String sql = "CREATE LOCAL TEMPORARY TABLE pm1.g1 (column1 string)"; //$NON-NLS-1$
        helpResolveException(sql,
                             "TEIID30117 Cannot create temporary table \"pm1.g1\". Local temporary tables must be created with unqualified names."); //$NON-NLS-1$
    }

    @Test
    public void testProcedureConflict() {
        String sql = "create local temporary table MMSP6 (e1 string, e2 integer)"; //$NON-NLS-1$
        helpResolveException(sql, getMetadataFactory().exampleBQTCached()); //$NON-NLS-1$
    }

    @Test
    public void testCreatePk() {
        String sql = "CREATE LOCAL TEMPORARY TABLE foo (column1 string, column2 integer, primary key (column1, column2))"; //$NON-NLS-1$
        helpResolve(sql);
    }

    @Test
    public void testCreateUnknownPk() {
        String sql = "CREATE LOCAL TEMPORARY TABLE foo (column1 string, primary key (column2))"; //$NON-NLS-1$
        helpResolveException(sql, "TEIID31118 Element \"column2\" is not defined by any relevant group."); //$NON-NLS-1$
    }

    @Test
    public void testCreateAlreadyExists() {
        String sql = "CREATE LOCAL TEMPORARY TABLE g1 (column1 string)"; //$NON-NLS-1$
        helpResolveException(sql, "TEIID30118 Cannot create temporary table \"g1\". An object with the same name already exists."); //$NON-NLS-1$
    }

    @Test
    public void testCreateImplicitName() {
        String sql = "CREATE LOCAL TEMPORARY TABLE #g1 (column1 string)"; //$NON-NLS-1$
        CommandImpl c = helpResolve(sql);
        assertEquals(sql, c.toString());
    }

    @Test
    public void testCreateInProc() throws Exception {
        helpResolveException("CREATE VIRTUAL PROCEDURE BEGIN create local temporary table g1(c1 string); end", "TEIID30118 Cannot create temporary table \"g1\". An object with the same name already exists.");//$NON-NLS-1$ //$NON-NLS-2$
    }

    //this was the old virt.agg procedure.  It was defined in such a way that relied on the scope leak of #temp
    //the exception here is a little weak since there are multiple uses of #temp in the block
    @Test
    public void testTempTableScope() {
        String proc = "CREATE VIRTUAL PROCEDURE " //$NON-NLS-1$
                      + "BEGIN " //$NON-NLS-1$
                      + "        DECLARE integer VARIABLES.BITS;" //$NON-NLS-1$
                      + "        LOOP ON (SELECT DISTINCT phys.t.ID, phys.t.Name FROM phys.t) AS idCursor" //$NON-NLS-1$
                      + "        BEGIN" //$NON-NLS-1$
                      + "                VARIABLES.BITS = 0;" //$NON-NLS-1$
                      + "                LOOP ON (SELECT phys.t.source_bits FROM phys.t WHERE phys.t.ID = idCursor.id) AS bitsCursor" //$NON-NLS-1$
                      + "                BEGIN" //$NON-NLS-1$
                      + "                        VARIABLES.BITS = bitor(VARIABLES.BITS, bitsCursor.source_bits);" //$NON-NLS-1$
                      + "                END" //$NON-NLS-1$
                      + "                SELECT idCursor.id, idCursor.name, VARIABLES.BITS INTO #temp;" //$NON-NLS-1$
                      + "        END" //$NON-NLS-1$
                      + "        SELECT ID, Name, #temp.BITS AS source_bits FROM #temp;" //$NON-NLS-1$                                          
                      + "END"; //$NON-NLS-1$ 

        helpResolveException(proc, getMetadataFactory().exampleBitwise(), "Group does not exist: #temp"); //$NON-NLS-1$
    }

    @Test
    public void testDrop() {
        String sql = "DROP TABLE temp_table"; //$NON-NLS-1$
        helpResolveException(sql, "Group does not exist: temp_table"); //$NON-NLS-1$ 
    }

    @Test
    public void testResolveUnqualifiedCriteria() throws Exception {
        CriteriaImpl criteria = getQueryParser().parseCriteria("e1 = 1"); //$NON-NLS-1$

        // resolve
        try {
            TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
            queryResolver.resolveCriteria(criteria, metadata);
            fail("Exception expected"); //$NON-NLS-1$
        } catch (QueryResolverException e) {
            assertEquals("TEIID31119 Symbol e1 is specified with an unknown group context", e.getMessage()); //$NON-NLS-1$
        }
    }

    @Test
    public void testSameNameRoot() {
        String sql = "select p.e1 from pm1.g1 as pp, pm1.g1 as p"; //$NON-NLS-1$

        helpResolve(sql);
    }

    @Test
    public void testAmbiguousAllInGroup() {
        String sql = "SELECT g1.* from pm1.g1, pm2.g1"; //$NON-NLS-1$
        helpResolveException(sql, metadata, "The symbol g1.* refers to more than one group defined in the FROM clause."); //$NON-NLS-1$
    }

    @Test
    public void testRowsUpdatedInProcedure() {
        String sql = "CREATE VIRTUAL PROCEDURE " //$NON-NLS-1$
                     + "BEGIN " //$NON-NLS-1$
                     + "SELECT ROWS_UPDATED; " //$NON-NLS-1$
                     + "end "; //$NON-NLS-1$

        helpResolveException(sql, metadata, "TEIID31118 Element \"ROWS_UPDATED\" is not defined by any relevant group."); //$NON-NLS-1$
    }

    /**
     *  We could check to see if the expressions are evaluatable to a constant, but that seems unnecessary
     */
    @Test
    public void testLookupWithoutConstant() throws Exception {
        String sql = "SELECT lookup('pm1.g1', convert('e3', float), 'e2', e2) FROM pm1.g1"; //$NON-NLS-1$

        helpResolveException(sql,
                             metadata,
                             "TEIID30095 The first three arguments for the LOOKUP function must be specified as constants."); //$NON-NLS-1$
    }

    /**
     * We cannot implicitly convert the argument to double due to lack of precision
     */
    @Test
    public void testPowerWithBigInteger_Fails() throws Exception {
        String sql = "SELECT power(10, 999999999999999999999999999999999999999999999)"; //$NON-NLS-1$

        helpResolveException(sql);
    }

    @Test
    public void testResolveXMLSelect() {
        String procedure = "CREATE VIRTUAL PROCEDURE "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE string VARIABLES.X = 1;\n"; //$NON-NLS-1$
        procedure = procedure + "select VARIABLES.X from xmltest.doc1;\n"; //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        helpResolveException(procedure, "TEIID30136 Unable to resolve element: VARIABLES.X"); //$NON-NLS-1$
    }

    @Test
    public void testXMLJoinFail() {
        String query = "select * from xmltest.doc1, xmltest.doc2"; //$NON-NLS-1$

        helpResolveException(query, "TEIID30112 Only one XML document may be specified in the FROM clause of a query."); //$NON-NLS-1$
    }

    @Test
    public void testExecProjectedSymbols() {
        String query = "exec pm1.sq1()"; //$NON-NLS-1$

        StoredProcedureImpl proc = (StoredProcedureImpl)helpResolve(query);

        List<BaseExpression> projected = proc.getProjectedSymbols();

        assertEquals(2, projected.size());

        for (Iterator<BaseExpression> i = projected.iterator(); i.hasNext();) {
            ElementSymbolImpl symbol = (ElementSymbolImpl)i.next();
            assertNotNull(symbol.getGroupSymbol());
        }
    }

    @Test
    public void testExecWithDuplicateNames() {
        MetadataStore metadataStore = new MetadataStore();

        Schema pm1 = getMetadataFactory().createPhysicalModel("pm1", metadataStore);

        ColumnSet<Procedure> rs2 = getMetadataFactory().createResultSet("rs2", new String[] {"in", "e2"}, new String[] {DefaultDataTypeManager.DefaultDataTypes.STRING.getId(), DefaultDataTypeManager.DefaultDataTypes.INTEGER.getId()}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        ProcedureParameter rs2p2 = getMetadataFactory().createParameter("in", SPParameter.ParameterInfo.IN, DefaultDataTypeManager.DefaultDataTypes.STRING.getId()); //$NON-NLS-1$
        Procedure sq2 = getMetadataFactory().createStoredProcedure("sq2", pm1, Arrays.asList(rs2p2)); //$NON-NLS-1$
        sq2.setResultSet(rs2);

        QueryMetadataInterface metadata = getMetadataFactory().createTransformationMetadata(metadataStore, "example1");

        helpResolveException("select * from pm1.sq2", metadata, "TEIID30114 Cannot access procedure pm1.sq2 using table semantics since the parameter and result set column names are not all unique."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testInlineViewNullLiteralInUnion() {
        String sql = "select e2 from pm1.g1 union all (select x from (select null as x) y)"; //$NON-NLS-1$

        helpResolve(sql);
    }

    @Test
    public void testSelectIntoWithDuplicateNames() {
        String sql = "select 1 as x, 2 as x into #temp"; //$NON-NLS-1$

        helpResolveException(sql, "TEIID30091 Cannot create group '#temp' with multiple columns named 'x'"); //$NON-NLS-1$
    }

    @Test
    public void testCreateWithDuplicateNames() {
        String sql = "CREATE LOCAL TEMPORARY TABLE temp_table (column1 string, column1 string)"; //$NON-NLS-1$

        helpResolveException(sql, "TEIID30091 Cannot create group \'temp_table\' with multiple columns named \'column1\'"); //$NON-NLS-1$
    }

    @Test
    public void testXMLQuery4() {
        helpResolveException("SELECT * FROM xmltest.doc1 group by a2", "TEIID30130 Queries against XML documents can not have a GROUP By clause"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testXMLQuery5() {
        helpResolveException("SELECT * FROM xmltest.doc1 having a2='x'", "TEIID30131 Queries against XML documents can not have a HAVING clause"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testSelectIntoWithOrderBy() {
        String sql = "select e1, e2 into #temp from pm1.g1 order by e1 limit 10"; //$NON-NLS-1$

        helpResolve(sql);
    }

    @Test
    public void testUnionBranchesWithDifferentElementCounts() {
        helpResolveException("SELECT e2, e3 FROM pm1.g1 UNION SELECT e2 FROM pm1.g2", "TEIID30147 Queries combined with the set operator UNION must have the same number of output elements."); //$NON-NLS-1$ //$NON-NLS-2$
        helpResolveException("SELECT e2 FROM pm1.g1 UNION SELECT e2, e3 FROM pm1.g2", "TEIID30147 Queries combined with the set operator UNION must have the same number of output elements."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testSelectIntoWithNullLiteral() {
        String sql = "select null as x into #temp from pm1.g1"; //$NON-NLS-1$

        QueryImpl query = (QueryImpl)helpResolve(sql);

        TempMetadataStore store = query.getTemporaryMetadata();

        TempMetadataID id = store.getTempElementID("#temp.x"); //$NON-NLS-1$

        assertEquals(DefaultDataTypeManager.DefaultDataTypes.STRING.getTypeClass(), id.getType());
    }

    @Test
    public void testInsertWithNullLiteral() {
        String sql = "insert into #temp (x) values (null)"; //$NON-NLS-1$

        InsertImpl insert = (InsertImpl)helpResolve(sql);

        TempMetadataStore store = insert.getTemporaryMetadata();

        TempMetadataID id = store.getTempElementID("#temp.x"); //$NON-NLS-1$

        assertEquals(DefaultDataTypeManager.DefaultDataTypes.STRING.getTypeClass(), id.getType());
    }

    @Test
    public void testInsertWithoutColumnsFails() {
        String sql = "Insert into pm1.g1 values (1, 2)"; //$NON-NLS-1$

        helpResolveException(sql,
                             "TEIID30127 INSERT statement must have the same number of elements and values specified.  This statement has 4 elements and 2 values."); //$NON-NLS-1$
    }

    @Test
    public void testInsertWithoutColumnsFails1() {
        String sql = "Insert into pm1.g1 values (1, 2, 3, 4)"; //$NON-NLS-1$

        helpResolveException(sql,
                             "TEIID30082 Expected value of type 'boolean' but '3' is of type 'integer' and no implicit conversion is available."); //$NON-NLS-1$
    }

    @Test
    public void testInsertWithQueryFails() {
        String sql = "Insert into pm1.g1 select 1, 2, 3, 4"; //$NON-NLS-1$

        helpResolveException(sql,
                             "TEIID30128 Cannot convert insert query expression projected symbol '3' of type java.lang.Integer to insert column 'pm1.g1.e3' of type java.lang.Boolean"); //$NON-NLS-1$
    }

    @Test
    public void testInsertWithQueryImplicitWithColumns() {
        String sql = "Insert into #X (x) select 1 as x"; //$NON-NLS-1$
        helpResolve(sql); //$NON-NLS-1$
    }

    @Test
    public void testInsertWithQueryImplicitWithoutColumns() {
        String sql = "Insert into #X select 1 as x, 2 as y, 3 as z"; //$NON-NLS-1$
        helpResolve(sql); //$NON-NLS-1$
    }

    @Test
    public void testInsertWithQueryImplicitWithoutColumns1() {
        String sql = "Insert into #X select 1 as x, 2 as y, 3 as y"; //$NON-NLS-1$

        helpResolveException(sql, "TEIID30091 Cannot create group '#X' with multiple columns named 'y'"); //$NON-NLS-1$
    }

    @Test
    public void testInsertWithoutColumnsPasses() {
        String sql = "Insert into pm1.g1 values (1, 2, true, 4)"; //$NON-NLS-1$

        helpResolve(sql);
        InsertImpl command = (InsertImpl)helpResolve(sql);
        assertEquals(4, command.getVariables().size());
    }

    @Test
    public void testInsertWithoutColumnsUndefinedTemp() {
        String sql = "Insert into #temp values (1, 2)"; //$NON-NLS-1$

        InsertImpl command = (InsertImpl)helpResolve(sql);
        assertEquals(2, command.getVariables().size());
    }

    @Test
    public void testCase6319() throws Exception {
        String sql = "select floatnum from bqt1.smalla group by floatnum having sum(floatnum) between 51.0 and 100.0 "; //$NON-NLS-1$
        QueryImpl query = (QueryImpl)helpParse(sql);
        TCQueryResolver queryResolver = new TCQueryResolver(getQueryParser());
        queryResolver.resolveCommand(query, getMetadataFactory().exampleBQTCached());
    }

    @Test
    public void testUniqeNamesWithInlineView() {
        helpResolveException("select * from (select count(intNum) a, count(stringKey) b, bqt1.smalla.intkey as b from bqt1.smalla group by bqt1.smalla.intkey) q1 order by q1.a", getMetadataFactory().exampleBQTCached(), "TEIID30091 Cannot create group 'q1' with multiple columns named 'b'"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testResolveOldProcRelational() {
        helpResolveException("SELECT * FROM pm1.g1, (exec pm1.sq2(pm1.g1.e1)) as a", "TEIID31119 Symbol pm1.g1.e1 is specified with an unknown group context"); //$NON-NLS-1$  //$NON-NLS-2$
    }

    @Test
    public void testResolverOrderOfPrecedence() {
        helpResolveException("SELECT pm1.g1.e1, pm1.g1.e2 FROM pm1.g1 CROSS JOIN (pm1.g2 LEFT OUTER JOIN pm2.g1 on pm1.g1.e1 = pm2.g1.e1)", "TEIID31119 Symbol pm1.g1.e1 is specified with an unknown group context"); //$NON-NLS-1$  //$NON-NLS-2$
    }

    /**
     * The cross join should parse/resolve with higher precedence
     */
    @Test
    public void testResolverOrderOfPrecedence_1() {
        helpResolve("SELECT pm1.g1.e1, pm1.g1.e2 FROM pm1.g1 CROSS JOIN pm1.g2 LEFT OUTER JOIN pm2.g1 on pm1.g1.e1 = pm2.g1.e1"); //$NON-NLS-1$ 
    }

    @Test
    public void testInvalidColumnReferenceWithNestedJoin() {
        helpResolveException("SELECT a.* FROM (pm1.g2 a left outer join pm1.g2 b on a.e1= b.e1) LEFT OUTER JOIN (select a.e1) c on (a.e1 = c.e1)"); //$NON-NLS-1$ 
    }

    /**
     * should be the same as exec with too many params
     */
    @Test
    public void testCallableStatementTooManyParameters() throws Exception {
        String sql = "{call pm4.spTest9(?, ?)}"; //$NON-NLS-1$

        helpResolveException(sql,
                             getMetadataFactory().exampleBQTCached(),
                             "TEIID31113 1 extra positional parameter(s) passed to pm4.spTest9."); //$NON-NLS-1$
    }

    @Test
    public void testUpdateSetClauseReferenceType() {
        String sql = "UPDATE pm1.g1 SET pm1.g1.e1 = 1, pm1.g1.e2 = ?;"; //$NON-NLS-1$

        UpdateImpl update = (UpdateImpl)helpResolve(sql, getMetadataFactory().example1Cached());

        BaseExpression ref = update.getChangeList().getClauses().get(1).getValue();
        assertTrue(ref instanceof ReferenceImpl);
        assertNotNull(ref.getType());
    }

    @Test
    public void testNoTypeCriteria() {
        String sql = "select * from pm1.g1 where ? = ?"; //$NON-NLS-1$

        helpResolveException(sql,
                             getMetadataFactory().example1Cached(),
                             "TEIID30083 Expression '? = ?' has a parameter with non-determinable type information.  The use of an explicit convert may be necessary."); //$NON-NLS-1$
    }

    @Test
    public void testReferenceInSelect() {
        String sql = "select ?, e1 from pm1.g1"; //$NON-NLS-1$
        QueryImpl command = (QueryImpl)helpResolve(sql, getMetadataFactory().example1Cached());
        assertEquals(DefaultDataTypeManager.DefaultDataTypes.STRING.getTypeClass(),
                     command.getProjectedSymbols().get(0).getType());
    }

    @Test
    public void testReferenceInSelect1() {
        String sql = "select convert(?, integer), e1 from pm1.g1"; //$NON-NLS-1$

        QueryImpl command = (QueryImpl)helpResolve(sql, getMetadataFactory().example1Cached());
        assertEquals(DefaultDataTypeManager.DefaultDataTypes.INTEGER.getTypeClass(),
                     command.getProjectedSymbols().get(0).getType());
    }

    @Test
    public void testUnionWithObjectTypeConversion() {
        String sql = "select convert(null, xml) from pm1.g1 union all select 1"; //$NON-NLS-1$

        SetQueryImpl query = (SetQueryImpl)helpResolve(sql, getMetadataFactory().example1Cached());
        assertEquals(DefaultDataTypeManager.DefaultDataTypes.OBJECT.getTypeClass(),
                     query.getProjectedSymbols().get(0).getType());
    }

    @Test
    public void testUnionWithSubQuery() {
        String sql = "select 1 from pm1.g1 where exists (select 1) union select 2"; //$NON-NLS-1$

        SetQueryImpl command = (SetQueryImpl)helpResolve(sql);

        assertEquals(1, CommandCollectorVisitorImpl.getCommands(command).size());
    }

    @Test
    public void testOrderBy_J658a() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT pm1.g1.e1, e2, e3 as x, (5+2) as y FROM pm1.g1 ORDER BY e3"); //$NON-NLS-1$
        OrderByImpl orderBy = resolvedQuery.getOrderBy();
        int[] expectedPositions = new int[] {2};
        helpTestOrderBy(orderBy, expectedPositions);
    }

    private void helpTestOrderBy(OrderByImpl orderBy, int[] expectedPositions) {
        assertEquals(expectedPositions.length, orderBy.getVariableCount());
        for (int i = 0; i < expectedPositions.length; i++) {
            assertEquals(expectedPositions[i], orderBy.getExpressionPosition(i));
        }
    }

    @Test
    public void testOrderBy_J658b() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT pm1.g1.e1, e2, e3 as x, (5+2) as y FROM pm1.g1 ORDER BY e2, e3 "); //$NON-NLS-1$
        helpTestOrderBy(resolvedQuery.getOrderBy(), new int[] {1, 2});
    }

    @Test
    public void testOrderBy_J658c() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT pm1.g1.e1, e2 as x, e3 as y FROM pm1.g1 ORDER BY x, e3 "); //$NON-NLS-1$
        helpTestOrderBy(resolvedQuery.getOrderBy(), new int[] {1, 2});
    }

    // ambiguous, should fail
    @Test
    public void testOrderBy_J658d() {
        helpResolveException("SELECT pm1.g1.e1, e2 as x, e3 as x FROM pm1.g1 ORDER BY x, e1 ", "TEIID30084 Element 'x' in ORDER BY is ambiguous and may refer to more than one element of SELECT clause."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testOrderBy_J658e() {
        QueryImpl resolvedQuery = (QueryImpl)helpResolve("SELECT pm1.g1.e1, e2 as x, e3 as e2 FROM pm1.g1 ORDER BY x, e2 "); //$NON-NLS-1$
        helpTestOrderBy(resolvedQuery.getOrderBy(), new int[] {1, 2});
    }

    @Test
    public void testSPOutParamWithExec() {
        StoredProcedureImpl proc = (StoredProcedureImpl)helpResolve("exec pm2.spTest8(1)", getMetadataFactory().exampleBQTCached());
        assertEquals(2, proc.getProjectedSymbols().size());
    }

    /**
     * Note that the call syntax is not quite correct, the output parameter is not in the arg list.
     * That hack is handled by the PreparedStatementRequest
     */
    @Test
    public void testSPOutParamWithCallableStatement() {
        StoredProcedureImpl proc = (StoredProcedureImpl)helpResolve("{call pm2.spTest8(1)}", getMetadataFactory().exampleBQTCached());
        assertEquals(3, proc.getProjectedSymbols().size());
    }

    @Test
    public void testOutWithWrongType() {
        helpResolveException("exec pm2.spTest8(inkey=>1, outkey=>{t '12:00:00'})", getMetadataFactory().exampleBQTCached());
    }

    @Test
    public void testProcRelationalWithOutParam() {
        QueryImpl proc = (QueryImpl)helpResolve("select * from pm2.spTest8 where inkey = 1", getMetadataFactory().exampleBQTCached());
        assertEquals(3, proc.getProjectedSymbols().size());
    }

    @Test
    public void testSPReturnParamWithNoResultSet() {
        StoredProcedureImpl proc = (StoredProcedureImpl)helpResolve("exec pm4.spTest9(1)", getMetadataFactory().exampleBQTCached());
        assertEquals(1, proc.getProjectedSymbols().size());
    }

    @Test
    public void testSecondPassFunctionResolving() {
        helpResolve("SELECT pm1.g1.e1 FROM pm1.g1 where lower(?) = e1 "); //$NON-NLS-1$
    }

    @Test
    public void testSecondPassFunctionResolving1() {
        try {
            helpResolve("SELECT pm1.g1.e1 FROM pm1.g1 where 1/(e1 - 2) <> 4 "); //$NON-NLS-1$
            fail("expected exception");
        } catch (RuntimeException e) {
            QueryResolverException qre = (QueryResolverException)e.getCause();
            assertTrue(qre.getMessage().contains("TEIID30070"));
        }
    }

    /**
     * Test <code>QueryResolver</code>'s ability to resolve a query that 
     * contains an aggregate <code>SUM</code> which uses a <code>CASE</code> 
     * expression which contains <code>BETWEEN</code> criteria as its value.
     * <p>
     * For example:
     * <p>
     * SELECT SUM(CASE WHEN e2 BETWEEN 3 AND 5 THEN e2 ELSE -1 END) FROM pm1.g1
     */
    @Test
    public void testAggregateWithBetweenInCaseInSelect() {
        String sql = "SELECT SUM(CASE WHEN e2 BETWEEN 3 AND 5 THEN e2 ELSE -1 END) FROM pm1.g1"; //$NON-NLS-1$
        helpResolve(sql);
    }

    /**
     * Test <code>QueryResolver</code>'s ability to resolve a query that 
     * contains a <code>CASE</code> expression which contains 
     * <code>BETWEEN</code> criteria in the queries <code>SELECT</code> clause.
     * <p>
     * For example:
     * <p>
     * SELECT CASE WHEN e2 BETWEEN 3 AND 5 THEN e2 ELSE -1 END FROM pm1.g1
     */
    @Test
    public void testBetweenInCaseInSelect() {
        String sql = "SELECT CASE WHEN e2 BETWEEN 3 AND 5 THEN e2 ELSE -1 END FROM pm1.g1"; //$NON-NLS-1$
        helpResolve(sql);
    }

    /**
     * Test <code>QueryResolver</code>'s ability to resolve a query that 
     * contains a <code>CASE</code> expression which contains 
     * <code>BETWEEN</code> criteria in the queries <code>WHERE</code> clause.
     * <p>
     * For example:
     * <p>
     * SELECT * FROM pm1.g1 WHERE e3 = CASE WHEN e2 BETWEEN 3 AND 5 THEN e2 ELSE -1 END
     */
    @Test
    public void testBetweenInCase() {
        String sql = "SELECT * FROM pm1.g1 WHERE e3 = CASE WHEN e2 BETWEEN 3 AND 5 THEN e2 ELSE -1 END"; //$NON-NLS-1$
        helpResolve(sql);
    }

    @Test
    public void testOrderByUnrelated() {
        helpResolve("SELECT pm1.g1.e1, e2 as x, e3 as y FROM pm1.g1 ORDER BY e4"); //$NON-NLS-1$
    }

    @Test
    public void testOrderByUnrelated1() {
        helpResolveException("SELECT distinct pm1.g1.e1, e2 as x, e3 as y FROM pm1.g1 ORDER BY e4"); //$NON-NLS-1$
    }

    @Test
    public void testOrderByUnrelated2() {
        helpResolveException("SELECT max(e2) FROM pm1.g1 group by e1 ORDER BY e4"); //$NON-NLS-1$
    }

    @Test
    public void testOrderByExpression() {
        QueryImpl query = (QueryImpl)helpResolve("select pm1.g1.e1 from pm1.g1 order by e2 || e3 "); //$NON-NLS-1$
        assertEquals(-1, query.getOrderBy().getExpressionPosition(0));
    }

    @Test
    public void testOrderByExpression1() {
        QueryImpl query = (QueryImpl)helpResolve("select pm1.g1.e1 || e2 from pm1.g1 order by pm1.g1.e1 || e2 "); //$NON-NLS-1$
        assertEquals(0, query.getOrderBy().getExpressionPosition(0));
    }

    @Test
    public void testOrderByExpression2() {
        helpResolveException("select pm1.g1.e1 from pm1.g1 union select pm1.g2.e1 from pm1.g2 order by pm1.g1.e1 || 2", "TEIID30086 ORDER BY expression '(pm1.g1.e1 || 2)' cannot be used with a set query."); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testOrderByConstantFails() {
        helpResolveException("select pm1.g1.e1 from pm1.g1 order by 2"); //$NON-NLS-1$
    }

    @Test
    public void testCorrelatedNestedTableReference() {
        helpResolve("select pm1.g1.e1 from pm1.g1, table (exec pm1.sq2(pm1.g1.e2)) x"); //$NON-NLS-1$
        helpResolveException("select pm1.g1.e1 from pm1.g1, (exec pm1.sq2(pm1.g1.e2)) x"); //$NON-NLS-1$
    }

    @Test
    public void testCorrelatedTextTable() {
        CommandImpl command = helpResolve("select x.* from pm1.g1, texttable(e1 COLUMNS x string) x"); //$NON-NLS-1$
        assertEquals(1, command.getProjectedSymbols().size());
    }

    @Test
    public void testQueryString() throws Exception {
        helpResolveException("select querystring(xmlparse(document '<a/>'))");
    }

    // validating AssignmentStatement, ROWS_UPDATED element assigned
    @Test( expected = TeiidClientException.class )
    public void testCreateUpdateProcedure9() throws Exception {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED = Select pm1.g1.e1 from pm1.g1;\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n"; //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userUpdateStr = "UPDATE vm1.g1 SET e1='x'"; //$NON-NLS-1$

        helpResolveUpdateProcedure(procedure, userUpdateStr);
    }

    // validating AssignmentStatement, variable type and assigned type 
    // do not match
    @Test(expected=TeiidClientException.class)
    public void testCreateUpdateProcedure10() throws Exception {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "var1 = Select pm1.g1.e1 from pm1.g1;\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n"; //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userUpdateStr = "UPDATE vm1.g1 SET e1='x'"; //$NON-NLS-1$

        helpResolveUpdateProcedure(procedure, userUpdateStr);
    }

    @Test
    public void testOrderByAggregatesError() throws Exception {
        helpResolveException("select count(*) from pm1.g1 order by e1");
    }

    @Test
    public void testWithDuplidateName() {
        helpResolveException("with x as (TABLE pm1.g1), x as (TABLE pm1.g2) SELECT * from x");
    }

    @Test
    public void testWithColumns() {
        helpResolveException("with x (a, b) as (TABLE pm1.g1) SELECT * from x");
    }

    @Test
    public void testWithNameMatchesFrom() {
        helpResolve("with x as (TABLE pm1.g1) SELECT * from (TABLE x) x");
    }

    // variables cannot be used among insert elements
    @Test( expected = TeiidClientException.class )
    public void testCreateUpdateProcedure23() throws Exception {
        String procedure = "CREATE PROCEDURE  "; //$NON-NLS-1$
        procedure = procedure + "BEGIN\n"; //$NON-NLS-1$
        procedure = procedure + "DECLARE integer var1;\n"; //$NON-NLS-1$
        procedure = procedure + "Update pm1.g1 SET pm1.g1.e2 =1 , var1 = 2;\n"; //$NON-NLS-1$
        procedure = procedure + "ROWS_UPDATED =0;\n"; //$NON-NLS-1$
        procedure = procedure + "END\n"; //$NON-NLS-1$

        String userQuery = "UPDATE vm1.g3 SET x='x' where e3= 1"; //$NON-NLS-1$

        helpResolveUpdateProcedure(procedure, userQuery);
    }

    @Test
    public void testTrim() {
        QueryImpl query = (QueryImpl)helpResolve("select trim(e1) from pm1.g1");
        assertEquals(DefaultDataTypeManager.DefaultDataTypes.STRING.getTypeClass(), query.getProjectedSymbols().get(0).getType());
    }

    @Test
    public void testTrim1() {
        helpResolve("select trim('x' from e1) from pm1.g1");
    }

    @Test
    public void testXmlTableWithParam() {
        helpResolve("select * from xmltable('/a' passing ?) as x");
    }

    @Test
    public void testXmlQueryWithParam() {
        QueryImpl q = (QueryImpl)helpResolve("select xmlquery('/a' passing ?)");
        XMLQueryImpl ex = (XMLQueryImpl)SymbolMap.getExpression(q.getSelect().getSymbols().get(0));
        assertEquals(DefaultDataTypeManager.DefaultDataTypes.XML.getTypeClass(), ex.getPassing().get(0).getExpression().getType());
    }

    @Test
    public void testImplicitTempTableWithExplicitColumns() {
        helpResolve("insert into #temp(x, y) select e1, e2 from pm1.g1");
    }

}
