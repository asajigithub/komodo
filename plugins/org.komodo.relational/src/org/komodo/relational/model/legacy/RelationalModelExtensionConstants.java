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
package org.komodo.relational.model.legacy;


/**
 *
 */
public interface RelationalModelExtensionConstants {

//    /**
//     * 
//     */
//    NamespaceProvider NAMESPACE_PROVIDER = new NamespaceProvider() {
//
//        /**
//         * {@inheritDoc}
//         * 
//         * @see org.teiid.designer.extension.properties.NamespaceProvider#getNamespacePrefix()
//         */
//        @Override
//        public String getNamespacePrefix() {
//            return "relational"; //$NON-NLS-1$
//        }
//
//        /**
//         * {@inheritDoc}
//         * 
//         * @see org.teiid.designer.extension.properties.NamespaceProvider#getNamespaceUri()
//         */
//        @Override
//        public String getNamespaceUri() {
//            return "http://www.jboss.org/teiiddesigner/ext/relational/2012"; //$NON-NLS-1$
//        }
//    };

    /**
     * The fully qualified extension property definition identifiers.
     */
    interface PropertyIds {

        /**
         * The property definition identifer for the deterministic boolean property.
         */
//        String DETERMINISTIC = ModelExtensionPropertyDefinition.Utils.getPropertyId(NAMESPACE_PROVIDER, "deterministic"); //$NON-NLS-1$
//        String NATIVE_QUERY = ModelExtensionPropertyDefinition.Utils.getPropertyId(NAMESPACE_PROVIDER, "native-query"); //$NON-NLS-1$
//        String NON_PREPARED = ModelExtensionPropertyDefinition.Utils.getPropertyId(NAMESPACE_PROVIDER, "non-prepared"); //$NON-NLS-1$
//        String JAVA_CLASS = ModelExtensionPropertyDefinition.Utils.getPropertyId(NAMESPACE_PROVIDER, "java-class"); //$NON-NLS-1$
//        String JAVA_METHOD = ModelExtensionPropertyDefinition.Utils.getPropertyId(NAMESPACE_PROVIDER, "java-method"); //$NON-NLS-1$
//        String FUNCTION_CATEGORY = ModelExtensionPropertyDefinition.Utils.getPropertyId(NAMESPACE_PROVIDER, "function-category"); //$NON-NLS-1$
//        String UDF_JAR_PATH = ModelExtensionPropertyDefinition.Utils.getPropertyId(NAMESPACE_PROVIDER, "udfJarPath"); //$NON-NLS-1$
//        String NATIVE_TYPE = ModelExtensionPropertyDefinition.Utils.getPropertyId(NAMESPACE_PROVIDER, "native_type"); //$NON-NLS-1$
//        String GLOBAL_TEMP_TABLE = ModelExtensionPropertyDefinition.Utils.getPropertyId(NAMESPACE_PROVIDER, "global-temp-table"); //$NON-NLS-1$
        String DETERMINISTIC = "deterministic"; //$NON-NLS-1$
        String NATIVE_QUERY = "native-query"; //$NON-NLS-1$
        String NON_PREPARED = "non-prepared"; //$NON-NLS-1$
        String JAVA_CLASS = "java-class"; //$NON-NLS-1$
        String JAVA_METHOD = "java-method"; //$NON-NLS-1$
        String FUNCTION_CATEGORY = "function-category"; //$NON-NLS-1$
        String UDF_JAR_PATH = "udfJarPath"; //$NON-NLS-1$
        String NATIVE_TYPE = "native_type"; //$NON-NLS-1$
        String GLOBAL_TEMP_TABLE = "global-temp-table"; //$NON-NLS-1$
    }
    
    @SuppressWarnings("javadoc")
	interface PropertyKeysNoPrefix {
        String JAVA_CLASS = "java-class"; //$NON-NLS-1$
        String JAVA_METHOD = "java-method"; //$NON-NLS-1$
        String FUNCTION_CATEGORY = "function-category"; //$NON-NLS-1$
        String UDF_JAR_PATH = "udfJarPath"; //$NON-NLS-1$
        String VARARGS = "varargs"; //$NON-NLS-1$
        String NULL_ON_NULL= "null-on-null"; //$NON-NLS-1$
        String DETERMINISTIC= "deterministic"; //$NON-NLS-1$
        String AGGREGATE= "aggregate"; //$NON-NLS-1$
        
        String ALLOWS_ORDER_BY = "allows-orderby"; //$NON-NLS-1$
        String ALLOWS_DISTINCT = "allows-distinct"; //$NON-NLS-1$
        String ANALYTIC = "analytic"; //$NON-NLS-1$
        String DECOMPOSABLE= "decomposable"; //$NON-NLS-1$
        String NATIVE_QUERY = "native-query"; //$NON-NLS-1$
        String NON_PREPARED = "non-prepared"; //$NON-NLS-1$
        String USES_DISTINCT_ROWS = "uses-distinct-rows"; //$NON-NLS-1$
        String ALLOW_JOIN = "allow-join"; //$NON-NLS-1$
        String NATIVE_TYPE = "native_type"; //$NON-NLS-1$
        String GLOBAL_TEMP_TABLE = "global-temp-table"; //$NON-NLS-1$

    }

}