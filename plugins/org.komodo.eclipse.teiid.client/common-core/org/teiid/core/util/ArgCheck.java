/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software;you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation;either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY;without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library;if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package org.teiid.core.util;

import java.util.Collection;
import java.util.Map;
import org.teiid.runtime.client.Messages;


/**
 * This class contains a set of static utility methods for checking method arguments.
 * It contains many of the common checks that are done, such as checking that an 
 * Object is non-null, checking the range of a value, etc.  All of these methods
 * throw {@link IllegalArgumentException}
 */
public class ArgCheck {

	/**
	 * Can't construct - utility class
	 */
	private ArgCheck() {
	}
	
	/**
	 * Check that the boolean condition is true;throw an
	 * IllegalArgumentException if not.
	 * @param condition The boolean condition to check
	 * @param message Exception message if check fails
	 * @throws IllegalArgumentException if condition is false
	 */
	public static final void isTrue(boolean condition, String message){
		if(!condition) {
			throw new IllegalArgumentException(message);   
		}
	}

	// ########################## int METHODS ###################################

    /**
     * Check that the value is non-negative (>=0). 
     * @param value Value
     * @throws IllegalArgumentException If value is negative (<0)
     */
	public static final void isNonNegative(int value) {
		isNonNegative(value,null);
	}

    /**
     * Check that the value is non-negative (>=0). 
     * @param value Value
     * @param message Exception message if check fails
     * @throws IllegalArgumentException If value is negative (<0)
     */
	public static final void isNonNegative(int value, String message) {
		if(value < 0) {
            final String msg = message != null ? 
                               message :
                               Messages.getString(Messages.ArgCheck.isNonNegativeInt);
			throw new IllegalArgumentException(msg);   
		}
	}

    /**
     * Check that the value is non-positive (<=0). 
     * @param value Value
     * @throws IllegalArgumentException If value is positive (>0)
     */
	public static final void isNonPositive(int value) {
	    isNonPositive(value,null);
	}

    /**
     * Check that the value is non-positive (<=0). 
     * @param value Value
     * @param message Exception message if check fails
     * @throws IllegalArgumentException If value is positive (>0)
     */
	public static final void isNonPositive(int value, String message) {
	    if(value > 0) {
            final String msg = message != null ? 
                               message :
                               Messages.getString(Messages.ArgCheck.isNonPositiveInt);
	        throw new IllegalArgumentException(msg);
	    }
	}

    /**
     * Check that the value is negative (<0). 
     * @param value Value
     * @throws IllegalArgumentException If value is non-negative (>=0)
     */
	public static final void isNegative(int value) {
	    isNegative(value,null);
	}

    /**
     * Check that the value is negative (<0). 
     * @param value Value
     * @param message Exception message if check fails
     * @throws IllegalArgumentException If value is non-negative (>=0)
     */
	public static final void isNegative(int value, String message) {
	    if(value >= 0) {
            final String msg = message != null ? 
                               message :
                               Messages.getString(Messages.ArgCheck.isNegativeInt);
	        throw new IllegalArgumentException(msg);
	    }
	}

    /**
     * Check that the value is positive (>0). 
     * @param value Value
     * @throws IllegalArgumentException If value is non-positive (<=0)
     */
	public static final void isPositive(int value) {
	    isPositive(value,null);
	}

    /**
     * Check that the value is positive (>0). 
     * @param value Value
     * @param message Exception message if check fails
     * @throws IllegalArgumentException If value is non-positive (<=0)
     */
	public static final void isPositive(int value, String message) {
	    if(value <= 0) { 
            final String msg = message != null ? 
                               message :
                               Messages.getString(Messages.ArgCheck.isPositiveInt);
	        throw new IllegalArgumentException(msg);
	    }
	}

	// ########################## long METHODS ###################################

    /**
     * Check that the value is non-negative (>=0). 
     * @param value Value
     * @throws IllegalArgumentException If value is negative (<0)
     */
	public static final void isNonNegative(long value) {
	    isNonNegative(value,null);
	}

    /**
     * Check that the value is non-negative (>=0). 
     * @param value Value
     * @param message Exception message if check fails
     * @throws IllegalArgumentException If value is negative (<0)
     */
	public static final void isNonNegative(long value, String message) {
		if(value < 0) {
            final String msg = message != null ? 
                               message :
                               Messages.getString(Messages.ArgCheck.isNonNegativeInt);
			throw new IllegalArgumentException(msg);   
		}
	}

    /**
     * Check that the value is non-positive (<=0). 
     * @param value Value
     * @throws IllegalArgumentException If value is positive (>0)
     */
	public static final void isNonPositive(long value) {
	    isNonPositive(value,null);
	}

    /**
     * Check that the value is non-positive (<=0). 
     * @param value Value
     * @param message Exception message if check fails
     * @throws IllegalArgumentException If value is positive (>0)
     */
	public static final void isNonPositive(long value, String message) {
	    if(value > 0) {
            final String msg = message != null ? 
                               message :
                               Messages.getString(Messages.ArgCheck.isNonPositiveInt);
	        throw new IllegalArgumentException(msg);
	    }
	}

    /**
     * Check that the value is negative (<0). 
     * @param value Value
     * @throws IllegalArgumentException If value is non-negative (>=0)
     */
	public static final void isNegative(long value) {
	    isNegative(value,null);
	}

    /**
     * Check that the value is negative (<0). 
     * @param value Value
     * @param message Exception message if check fails
     * @throws IllegalArgumentException If value is non-negative (>=0)
     */
	public static final void isNegative(long value, String message) {
	    if(value >= 0) {
            final String msg = message != null ? 
                               message :
                               Messages.getString(Messages.ArgCheck.isNegativeInt);
            throw new IllegalArgumentException(msg);
	    }
	}

    /**
     * Check that the value is positive (>0). 
     * @param value Value
     * @throws IllegalArgumentException If value is non-positive (<=0)
     */
	public static final void isPositive(long value) {
	    isPositive(value,null);
	}

    /**
     * Check that the value is positive (>0). 
     * @param value Value
     * @param message Exception message if check fails
     * @throws IllegalArgumentException If value is non-positive (<=0)
     */
	public static final void isPositive(long value, String message) {
	    if(value <= 0) { 
            final String msg = message != null ? 
                               message :
                               Messages.getString(Messages.ArgCheck.isPositiveInt);
            throw new IllegalArgumentException(msg);
	    }
	}

	// ########################## String METHODS ###################################

    /**
     * Check that the string is non-null and has length > 0
     * @param value Value
     * @throws IllegalArgumentException If value is null or length == 0
     */
	public static final void isNotZeroLength(String value) {	    
		isNotZeroLength(value,null);
	}

    /**
     * Check that the string is non-null and has length > 0
     * @param value Value
     * @param message Exception message if check fails
     * @throws IllegalArgumentException If value is null or length == 0
     */
	public static final void isNotZeroLength(String value, String message) {
		isNotNull(value);
		if(value.length() <= 0) {
            final String msg = message != null ? 
                               message :
                               Messages.getString(Messages.ArgCheck.isStringNonZeroLength);
            throw new IllegalArgumentException(msg);
		}
	}

	// ########################## Object METHODS ###################################
	
    /**
     * Check that the object is non-null
     * @param value Value
     * @throws IllegalArgumentException If value is null
     */
    public static final void isNotNull(Object value) {
        isNotNull(value,null);
    }

    /**
     * Check that the object is non-null
     * @param value Value
     * @param message Exception message if check fails
     * @throws IllegalArgumentException If value is null
     */
	public static final void isNotNull(Object value, String message) {
	    if(value == null) { 
            final String msg = message != null ? 
                               message :
                               Messages.getString(Messages.ArgCheck.isNonNull);
            throw new IllegalArgumentException(msg);
	    }
	}

    /**
     * Check that the object is null
     * @param value Value
     * @throws IllegalArgumentException If value is non-null
     */
    public static final void isNull(Object value) {
        isNull(value,null);
    }

    /**
     * Check that the object is null
     * @param value Value
     * @param message Exception message if check fails
     * @throws IllegalArgumentException If value is non-null
     */
    public static final void isNull(Object value, String message) {
        if(value != null) { 
            final String msg = message != null ? 
                               message :
                               Messages.getString(Messages.ArgCheck.isNull);
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * Check that the object is an instance of the specified Class
     * @param theClass Class
     * @param value Value
     * @throws IllegalArgumentException If value is null
     */
	public static final void isInstanceOf(Class theClass, Object value) {
        isInstanceOf(theClass,value,null);
	}

    /**
     * Check that the object is an instance of the specified Class
     * @param theClass Class
     * @param value Value
     * @param message Exception message if check fails
     * @throws IllegalArgumentException If value is null
     */
	public static final void isInstanceOf(Class theClass, Object value, String message) {
        isNotNull(value);
	    if( ! theClass.isInstance(value) ) {
            final String msg = message != null ? 
                               message :
                               Messages.getString(Messages.ArgCheck.isInstanceOf, theClass.getName(),value.getClass().getName());
	        throw new IllegalArgumentException(msg);
	    }
	}

	// ########################## COLLECTION METHODS ###################################
	
    /**
     * Check that the collection is not empty
     * @param collection Collection 
     * @throws IllegalArgumentException If collection is null or empty
     */
    public static final void isNotEmpty(Collection collection) {
        isNotEmpty(collection,null);
    }

    /**
     * Check that the collection is not empty
     * @param collection Collection 
     * @param message Exception message if check fails
     * @throws IllegalArgumentException If collection is null or empty
     */
    public static final void isNotEmpty(Collection collection, String message) {
        isNotNull(collection);
        if(collection.isEmpty()) {
            final String msg = message != null ? 
                               message :
                               Messages.getString(Messages.ArgCheck.isCollectionNotEmpty);
            throw new IllegalArgumentException(msg);
        }
    }
    
    /**
     * Check that the map is not empty
     * @param map Map 
     * @throws IllegalArgumentException If map is null or empty
     */
    public static final void isNotEmpty(Map map) {
        isNotEmpty(map,null);
    }

    /**
     * Check that the map is not empty
     * @param map Map 
     * @param message Exception message if check fails
     * @throws IllegalArgumentException If map is null or empty
     */
    public static final void isNotEmpty(Map map, String message) {
        isNotNull(map);
        if(map.isEmpty()) {
            final String msg = message != null ? 
                               message :
                               Messages.getString(Messages.ArgCheck.isMapNotEmpty);
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * Check that the array is not empty
     * @param array Array 
     * @throws IllegalArgumentException If array is null or empty
     *
     */
    public static final void isNotEmpty(Object[] array) {
        isNotEmpty(array,null);
    }

    /**
     * Check that the array is not empty
     * @param array Array 
     * @param message Exception message if check fails
     * @throws IllegalArgumentException If array is null or empty
     *
     */
    public static final void isNotEmpty(Object[] array, String message) {
        isNotNull(array);
        if(array.length == 0) {
            final String msg = message != null ? 
                               message :
                               Messages.getString(Messages.ArgCheck.isArrayNotEmpty);
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * Check that the string is not empty
     * @param string String 
     * @throws IllegalArgumentException If string is null or empty
     *
     */
    public static final void isNotEmpty(String string) {
        isNotZeroLength(string,null);
    }

    /**
     * Check that the string is not empty
     * @param string String 
     * @param message Exception message if check fails
     * @throws IllegalArgumentException If string is null or empty
     *
     */
    public static final void isNotEmpty(String string, String message) {
        isNotZeroLength(string,message);
    }

    /**
     * Asserts that the specified first object is not the same as (==) the specified second object.
     * @param firstObject  The first object to assert as not the same as the second object.
     * @param firstName    The name that will be used within the exception message for the first object, should an exception be
     *                      thrown;if null and <code>firstObject</code> is not null, <code>firstObject.toString()</code> will be
     *                      used.
     * @param secondObject The second object to assert as not the same as the first object.
     * @param secondName   The name that will be used within the exception message for the second object, should an exception be
     *                      thrown;if null and <code>secondObject</code> is not null, <code>secondObject.toString()</code> will
     *                      be used.
     * @throws IllegalArgumentException If the specified objects are the same.
     *
     */
    public static void isNotSame(final Object firstObject, String firstName, final Object secondObject, String secondName) {
        if (firstObject == secondObject) {
            if (firstName == null && firstObject != null) {
                firstName = firstObject.toString();
            }
            if (secondName == null && secondObject != null) {
                secondName = secondObject.toString();
            }
            throw new IllegalArgumentException(Messages.getString(Messages.ArgCheck.isNotSame, firstName, secondName ));
        }
    }

    /**
     * Check that the collection contains the value
     * @param collection Collection to check
     * @param value Value to check for, may be null
     * @throws IllegalArgumentException If collection is null or doesn't contain value
     */
    public static final void contains(Collection collection, Object value) {
        contains(collection, value, null);
    }

    /**
     * Check that the collection contains the value
     * @param collection Collection to check
     * @param value Value to check for, may be null
     * @param message Exception message if check fails
     * @throws IllegalArgumentException If collection is null or doesn't contain value
     */
    public static final void contains(Collection collection, Object value, String message) {
		isNotNull(collection);
		if(! collection.contains(value)) {
            final String msg = message != null ? 
                               message :
                               Messages.getString(Messages.ArgCheck.contains);
            throw new IllegalArgumentException(msg);
		}
	}

    /**
     * Check that the map contains the key
     * @param map Map to check
     * @param key Key to check for, may be null
     * @throws IllegalArgumentException If map  is null or doesn't contain key
     */
	public static final void containsKey(Map map, Object key) {
	    containsKey(map, key, null);
	}

    /**
     * Check that the map contains the key
     * @param map Map to check
     * @param key Key to check for, may be null
     * @param message Exception message if check fails
     * @throws IllegalArgumentException If map  is null or doesn't contain key
     */
	public static final void containsKey(Map map, Object key, String message) {
		isNotNull(map);
		if(! map.containsKey(key)) {
            final String msg = message != null ? 
                               message :
                               Messages.getString(Messages.ArgCheck.containsKey);
            throw new IllegalArgumentException(msg);
		}
	}

}
