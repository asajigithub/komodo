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
import org.komodo.relational.model.legacy.Model;
import org.komodo.relational.model.legacy.RelationalConstants;
import org.komodo.relational.model.legacy.RelationalObjectFactory;
import org.komodo.spi.outcome.Outcome;

/**
 * Test Class to test Table
 * 
 */
public class TestModel {
	
	private static final String MODEL_NAME = "myModel";  //$NON-NLS-1$
	
	/**
	 * Constructor
	 */
	public TestModel( ) {
		super();
	}
	
	/**
     * Test simple creation
     */
    @Test
    public void testCreate() {
    	Model model = RelationalObjectFactory.INSTANCE.createModel(MODEL_NAME);
    	String tName = model.getName();
    	int type = model.getType();
    	
    	assertEquals(MODEL_NAME, tName);
    	assertEquals(type, RelationalConstants.TYPES.MODEL);
    }
    
	/**
     * Test adding columns
     */
    @Test
    public void testAddChild() {
    	Model model = RelationalObjectFactory.INSTANCE.createModel(MODEL_NAME);

    	model.addChild(RelationalObjectFactory.INSTANCE.createColumn("column1")); //$NON-NLS-1$
    	
    	assertEquals(1, model.getChildren().size());
    }

	/**
     * Test adding columns
     */
    @Test
    public void testAddRemoveColumns() {
    	Model model = RelationalObjectFactory.INSTANCE.createModel(MODEL_NAME);

    	Column col1 = RelationalObjectFactory.INSTANCE.createColumn("col1"); //$NON-NLS-1$
    	
    	model.addChild(col1);
    	
    	model.removeChild(col1);
    	
    	assertEquals(0, model.getChildren().size());
    }
    
	/**
     * Test validation.  expected result - warning : no columns defined
     */
    @Test
    public void testValidateDefaultModel() {
    	Model model = RelationalObjectFactory.INSTANCE.createModel(MODEL_NAME);
    	
    	Outcome outcome = model.validate();
    	
    	assertEquals(Outcome.Level.OK, outcome.getLevel());
    }
        
}
