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
package org.komodo.spi.repository;

import java.util.ArrayList;
import java.util.List;
import org.komodo.spi.constants.StringConstants;

/**
 * Enum of all the Komodo types
 */
public enum KomodoType {

    /**
     * Schema
     */
    SCHEMA,

    /**
     * Teiid
     */
    TEIID,

    /**
     * Vdb
     */
    VDB,

    /**
     * Vdb Entry
     */
    VDB_ENTRY,

    /**
     * Vdb Import
     */
    VDB_IMPORT,

    /**
     * Vdb Model Source
     */
    VDB_MODEL_SOURCE,

    /**
     * Vdb Translator
     */
    VDB_TRANSLATOR,

    /**
     * Vdb Condition
     */
    VDB_CONDITION,

    /**
     * Vdb Data Role
     */
    VDB_DATA_ROLE,

    /**
     * Vdb Make
     */
    VDB_MASK,

    /**
     * Vdb Permission
     */
    VDB_PERMISSION,

    /**
     * Access Pattern
     */
    ACCESS_PATTERN,

    /**
     * Column
     */
    COLUMN,

    /**
     * Foreign Key
     */
    FOREIGN_KEY,

    /**
     * Pushdown Function
     */
    PUSHDOWN_FUNCTION,

    /**
     * UDF
     */
    USER_DEFINED_FUNCTION,

    /**
     * Index
     */
    INDEX,

    /**
     * Model
     */
    MODEL,

    /**
     * Paremeter
     */
    PARAMETER,

    /**
     * Primary Key
     */
    PRIMARY_KEY,

    /**
     * Stored Procedure
     */
    STORED_PROCEDURE,

    /**
     * Virtual Procedure
     */
    VIRTUAL_PROCEDURE,

    /**
     * Data type Result Set
     */
    DATA_TYPE_RESULT_SET,

    /**
     * Tabular Result Set Column
     */
    RESULT_SET_COLUMN,

    /**
     * Tabular Result Set
     */
    TABULAR_RESULT_SET,

    /**
     * Statement Option
     */
    STATEMENT_OPTION,

    /**
     * Table
     */
    TABLE,

    /**
     * Unique Constraint
     */
    UNIQUE_CONSTRAINT,

    /**
     * View
     */
    VIEW,

    /**
     * Type from the DDL schema
     */
    DDL_SCHEMA,

    /**
     * Type from the Teiid SQL schema
     */
    TSQL_SCHEMA,

    /**
     * Type from the VDB schema
     */
    VDB_SCHEMA,

    /**
     * Unknown Type
     */
    UNKNOWN;

    /**
     * @return actual type
     */
    public String getType() {
        StringBuffer sb = new StringBuffer();
        for (String s : name().split(StringConstants.UNDERSCORE)) {
            sb.append(Character.toUpperCase(s.charAt(0)));
            if (s.length() > 1) {
                sb.append(s.substring(1, s.length()).toLowerCase());
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return getType();
    }

    /**
     * @param kType the string definition of a type
     * @return the {@link KomodoType} of the given string definition
     */
    public static KomodoType getKomodoType(String kType) {
        if (kType == null)
            return KomodoType.UNKNOWN;

        for (KomodoType value : values()) {
            if (value.getType().equalsIgnoreCase(kType))
                return value;
        }

        return KomodoType.UNKNOWN;
    }

    /**
     * @return all the string definitions of the types
     */
    public static List<String> getTypes() {
        List<String> names = new ArrayList<String>();
        for (KomodoType kType : values()) {
            names.add(kType.getType());
        }

        return names;
    }
}
