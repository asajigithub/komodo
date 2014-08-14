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

package org.komodo.modeshape.teiid.sql.lang;

import org.komodo.modeshape.teiid.parser.LanguageVisitor;
import org.komodo.modeshape.teiid.parser.TeiidParser;
import org.komodo.spi.query.sql.lang.IExistsCriteria;

public class ExistsCriteria extends Criteria implements PredicateCriteria, SubqueryContainer<QueryCommand>, IExistsCriteria<LanguageVisitor, QueryCommand> {

    public ExistsCriteria(TeiidParser p, int id) {
        super(p, id);
    }

    @Override
    public boolean isNegated() {
        return false;
    }

    @Override
    public void setNegated(boolean value) {
    }

    @Override
    public QueryCommand getCommand() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCommand(QueryCommand command) {
    }

    public SubqueryHint getSubqueryHint() {
        throw new UnsupportedOperationException();
    }

    /**
     * @param hint
     */
    public void setSubqueryHint(SubqueryHint hint) {
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((this.getCommand() == null) ? 0 : this.getCommand().hashCode());
        result = prime * result + (this.isNegated() ? 1231 : 1237);
        result = prime * result + ((this.getSubqueryHint() == null) ? 0 : this.getSubqueryHint().hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        ExistsCriteria other = (ExistsCriteria)obj;
        if (this.getCommand() == null) {
            if (other.getCommand() != null)
                return false;
        } else if (!this.getCommand().equals(other.getCommand()))
            return false;
        if (this.isNegated() != other.isNegated())
            return false;
        if (this.getSubqueryHint() == null) {
            if (other.getSubqueryHint() != null)
                return false;
        } else if (!this.getSubqueryHint().equals(other.getSubqueryHint()))
            return false;
        return true;
    }

    @Override
    public void acceptVisitor(LanguageVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public ExistsCriteria clone() {
        ExistsCriteria clone = new ExistsCriteria(this.getTeiidParser(), this.getId());

        if (getCommand() != null)
            clone.setCommand(getCommand().clone());
        clone.setNegated(isNegated());
        if (getSubqueryHint() != null)
            clone.setSubqueryHint(getSubqueryHint().clone());

        return clone;
    }

}
