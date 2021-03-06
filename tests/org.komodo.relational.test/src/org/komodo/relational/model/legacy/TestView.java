/*
 * Copyright 2014 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.komodo.relational.model.legacy;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.komodo.relational.model.legacy.Column;
import org.komodo.relational.model.legacy.RelationalConstants;
import org.komodo.relational.model.legacy.RelationalObjectFactory;
import org.komodo.relational.model.legacy.View;
import org.komodo.spi.outcome.Outcome;

/**
 * Test Class to test Table
 * 
 */
public class TestView {
	
	private static final String VIEW_NAME = "myView";  //$NON-NLS-1$
	
	/**
	 * Constructor
	 */
	public TestView( ) {
		super();
	}
	
	/**
     * Test simple creation
     */
    @Test
    public void testCreate() {
    	View view = RelationalObjectFactory.INSTANCE.createView(VIEW_NAME);
    	String tName = view.getName();
    	int type = view.getType();
    	
    	assertEquals(VIEW_NAME, tName);
    	assertEquals(type, RelationalConstants.TYPES.VIEW);
    }
    
	/**
     * Test adding columns
     */
    @Test
    public void testAddColumns() {
    	View view = RelationalObjectFactory.INSTANCE.createView(VIEW_NAME);

    	view.addColumn(RelationalObjectFactory.INSTANCE.createColumn("column1")); //$NON-NLS-1$
    	
    	assertEquals(1, view.getColumns().size());
    }

	/**
     * Test adding columns
     */
    @Test
    public void testAddRemoveColumns() {
    	View view = RelationalObjectFactory.INSTANCE.createView(VIEW_NAME);

    	Column col1 = RelationalObjectFactory.INSTANCE.createColumn("col1"); //$NON-NLS-1$
    	
    	view.addColumn(col1);
    	
    	view.removeColumn(col1);
    	
    	assertEquals(0, view.getColumns().size());
    }
    
	/**
     * Test validation.  expected result - warning : no columns defined
     */
    @Test
    public void testValidateDefaultView() {
    	View view = RelationalObjectFactory.INSTANCE.createView(VIEW_NAME);
    	
    	Outcome outcome = view.validate();
    	
    	assertEquals(Outcome.Level.WARNING, outcome.getLevel());
    	assertEquals("No columns defined for table", outcome.getMessage()); //$NON-NLS-1$
    }
        
}
