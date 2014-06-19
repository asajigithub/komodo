/*************************************************************************************
 * Copyright (c) 2014 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     JBoss by Red Hat - Initial implementation.
 ************************************************************************************/
package org.komodo.spi.query.sql.symbol;

import org.komodo.spi.query.sql.ILanguageVisitor;
import org.komodo.spi.query.sql.lang.IExpression;


/**
 *
 */
public interface IConstant<LV extends ILanguageVisitor> extends IExpression<LV> {

    /**
     * Value of the constant
     * 
     * @return value
     */
    Object getValue();

    /**
     * @return if constant is multi valued
     */
    boolean isMultiValued();

}