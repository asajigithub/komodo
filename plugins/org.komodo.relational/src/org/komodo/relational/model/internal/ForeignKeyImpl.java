/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package org.komodo.relational.model.internal;

import org.komodo.relational.Messages;
import org.komodo.relational.Messages.Relational;
import org.komodo.relational.RelationalProperties;
import org.komodo.relational.internal.AdapterFactory;
import org.komodo.relational.internal.RelationalModelFactory;
import org.komodo.relational.internal.TypeResolver;
import org.komodo.relational.model.Column;
import org.komodo.relational.model.ForeignKey;
import org.komodo.relational.model.Table;
import org.komodo.repository.ObjectImpl;
import org.komodo.spi.KException;
import org.komodo.spi.repository.KomodoObject;
import org.komodo.spi.repository.KomodoType;
import org.komodo.spi.repository.Property;
import org.komodo.spi.repository.Repository;
import org.komodo.spi.repository.Repository.UnitOfWork;
import org.komodo.utils.ArgCheck;
import org.modeshape.jcr.JcrLexicon;
import org.modeshape.sequencer.ddl.dialect.teiid.TeiidDdlLexicon.Constraint;

/**
 * An implementation of a relational model foreign key.
 */
public final class ForeignKeyImpl extends TableConstraintImpl implements ForeignKey {

    /**
     * The resolver of a {@link ForeignKey}.
     */
    public static final TypeResolver RESOLVER = new TypeResolver() {

        /**
         * {@inheritDoc}
         *
         * @see org.komodo.relational.internal.TypeResolver#create(org.komodo.spi.repository.Repository.UnitOfWork,
         *      org.komodo.spi.repository.Repository, org.komodo.spi.repository.KomodoObject, java.lang.String,
         *      org.komodo.relational.RelationalProperties)
         */
        @Override
        public ForeignKey create( final UnitOfWork transaction,
                                  final Repository repository,
                                  final KomodoObject parent,
                                  final String id,
                                  final RelationalProperties properties ) throws KException {
            final AdapterFactory adapter = new AdapterFactory( repository );
            final Table parentTable = adapter.adapt( transaction, parent, Table.class );
            final Object keyRefValue = properties.getValue( Constraint.FOREIGN_KEY_CONSTRAINT );
            final Table keyRef = adapter.adapt( transaction, keyRefValue, Table.class );
            return RelationalModelFactory.createForeignKey( transaction, repository, parentTable, id, keyRef );
        }

        /**
         * {@inheritDoc}
         *
         * @see org.komodo.relational.internal.TypeResolver#identifier()
         */
        @Override
        public KomodoType identifier() {
            return IDENTIFIER;
        }

        /**
         * {@inheritDoc}
         *
         * @see org.komodo.relational.internal.TypeResolver#owningClass()
         */
        @Override
        public Class< ForeignKeyImpl > owningClass() {
            return ForeignKeyImpl.class;
        }

        /**
         * {@inheritDoc}
         *
         * @see org.komodo.relational.internal.TypeResolver#resolvable(org.komodo.spi.repository.Repository.UnitOfWork,
         *      org.komodo.spi.repository.KomodoObject)
         */
        @Override
        public boolean resolvable( final UnitOfWork transaction,
                                   final KomodoObject kobject ) {
            try {
                ObjectImpl.validateType( transaction, kobject.getRepository(), kobject, Constraint.FOREIGN_KEY_CONSTRAINT );
                ObjectImpl.validatePropertyValue( transaction,
                                                  kobject.getRepository(),
                                                  kobject,
                                                  Constraint.TYPE,
                                                  ForeignKey.CONSTRAINT_TYPE.toValue() );
                return true;
            } catch (final Exception e) {
                // not resolvable
            }

            return false;
        }

        /**
         * {@inheritDoc}
         *
         * @see org.komodo.relational.internal.TypeResolver#resolve(org.komodo.spi.repository.Repository.UnitOfWork,
         *      org.komodo.spi.repository.KomodoObject)
         */
        @Override
        public ForeignKey resolve( final UnitOfWork transaction,
                                   final KomodoObject kobject ) throws KException {
            return new ForeignKeyImpl( transaction, kobject.getRepository(), kobject.getAbsolutePath() );
        }

    };

    /**
     * @param uow
     *        the transaction (can be <code>null</code> if update should be automatically committed)
     * @param repository
     *        the repository where the relational object exists (cannot be <code>null</code>)
     * @param workspacePath
     *        the workspace relative path (cannot be empty)
     * @throws KException
     *         if an error occurs or if node at specified path is not a foreign key
     */
    public ForeignKeyImpl( final UnitOfWork uow,
                           final Repository repository,
                           final String workspacePath ) throws KException {
        super(uow, repository, workspacePath);
    }

    @Override
    public KomodoType getTypeIdentifier(UnitOfWork uow) {
        return RESOLVER.identifier();
    }

    /**
     * {@inheritDoc}
     *
     * @see org.komodo.relational.model.ForeignKey#addReferencesColumn(org.komodo.spi.repository.Repository.UnitOfWork,
     *      org.komodo.relational.model.Column)
     */
    @Override
    public void addReferencesColumn( final UnitOfWork uow,
                                     final Column newReferencesColumn ) throws KException {
        ArgCheck.isNotNull(newReferencesColumn, "newReferencesColumn"); //$NON-NLS-1$
        UnitOfWork transaction = uow;

        if (transaction == null) {
            transaction = getRepository().createTransaction("foreignkeyimpl-addReferencesColumn", false, null); //$NON-NLS-1$
        }

        assert (transaction != null);

        String[] newValue = null;

        try {
            final Property property = getProperty(transaction, Constraint.TABLE_REFERENCE_REFERENCES);
            final String columnId = newReferencesColumn.getProperty(transaction, JcrLexicon.UUID.getString()).getStringValue(transaction);

            if (property == null) {
                newValue = new String[1];
                newValue[0] = columnId;
            } else {
                final String[] columnRefs = property.getStringValues(transaction);
                newValue = new String[columnRefs.length + 1];
                System.arraycopy(columnRefs, 0, newValue, 0, columnRefs.length);
                newValue[columnRefs.length] = columnId;
            }

            setProperty(transaction, Constraint.TABLE_REFERENCE_REFERENCES, (Object[])newValue);

            if (uow == null) {
                transaction.commit();
            }
        } catch (final Exception e) {
            throw handleError(uow, transaction, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see org.komodo.relational.model.TableConstraint#getConstraintType()
     */
    @Override
    public ConstraintType getConstraintType() {
        return ConstraintType.FOREIGN_KEY;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.komodo.relational.model.ForeignKey#getReferencesColumns(org.komodo.spi.repository.Repository.UnitOfWork)
     */
    @Override
    public Column[] getReferencesColumns( final UnitOfWork uow ) throws KException {
        UnitOfWork transaction = uow;

        if (transaction == null) {
            transaction = getRepository().createTransaction("foreignkeyimpl-getReferencesColumns", true, null); //$NON-NLS-1$
        }

        assert (transaction != null);

        final Repository repository = getRepository();
        Column[] result = null;

        try {
            final Property property = getProperty(transaction, Constraint.TABLE_REFERENCE_REFERENCES);

            if (property == null) {
                result = new Column[0];
            } else {
                final String[] columnRefs = property.getStringValues(transaction);
                result = new Column[columnRefs.length];
                int i = 0;

                for (final String columnId : columnRefs) {
                    final KomodoObject kobject = repository.getUsingId(transaction, columnId);

                    if (kobject == null) {
                        throw new KException(Messages.getString(Relational.REFERENCED_COLUMN_NOT_FOUND, columnId));
                    }

                    result[i] = new ColumnImpl(transaction, repository, kobject.getAbsolutePath());
                    ++i;
                }
            }

            if (uow == null) {
                transaction.commit();
            }

            return result;
        } catch (final Exception e) {
            throw handleError(uow, transaction, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see org.komodo.relational.model.ForeignKey#getReferencesTable(org.komodo.spi.repository.Repository.UnitOfWork)
     */
    @Override
    public Table getReferencesTable( final UnitOfWork uow ) throws KException {
        UnitOfWork transaction = uow;

        if (transaction == null) {
            transaction = getRepository().createTransaction("foreignkeyimpl-getReferencesTable", true, null); //$NON-NLS-1$
        }

        assert (transaction != null);

        try {
            Table result = null;
            final Property property = getProperty(transaction, Constraint.TABLE_REFERENCE);

            if (property != null) {
                final String tableId = property.getStringValue(transaction);
                final KomodoObject kobject = getRepository().getUsingId(transaction, tableId);

                if (kobject == null) {
                    throw new KException(Messages.getString(Relational.REFERENCED_TABLE_NOT_FOUND, tableId));
                }

                result = new TableImpl(transaction, getRepository(), kobject.getAbsolutePath());
            }

            if (uow == null) {
                transaction.commit();
            }

            return result;
        } catch (final Exception e) {
            throw handleError(uow, transaction, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see org.komodo.spi.repository.KomodoObject#getTypeId()
     */
    @Override
    public int getTypeId() {
        return TYPE_ID;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.komodo.relational.model.ForeignKey#removeReferencesColumn(org.komodo.spi.repository.Repository.UnitOfWork,
     *      org.komodo.relational.model.Column)
     */
    @Override
    public void removeReferencesColumn( final UnitOfWork uow,
                                        final Column removeReferencesColumn ) throws KException {
        ArgCheck.isNotNull(removeReferencesColumn, "removeReferencesColumn"); //$NON-NLS-1$
        UnitOfWork transaction = uow;

        if (transaction == null) {
            transaction = getRepository().createTransaction("columnimpl-removeReferencesColumn", false, null); //$NON-NLS-1$
        }

        assert (transaction != null);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("removeRefencesColumn: transaction = {0}, removeReferencesColumn = {1}", //$NON-NLS-1$
                         transaction.getName(),
                         removeReferencesColumn);
        }

        assert (removeReferencesColumn != null);

        try {
            final String columnId = removeReferencesColumn.getProperty(transaction, JcrLexicon.UUID.getString()).getStringValue(transaction);
            final Column[] current = getReferencesColumns(transaction);

            if (current.length == 0) {
                throw new KException(Messages.getString(Relational.REFERENCED_COLUMN_NOT_FOUND, columnId));
            }

            boolean found = false;
            final Column[] updated = new Column[current.length - 1];
            int i = 0;

            for (final Column column : current) {
                if (column.equals(removeReferencesColumn)) {
                    found = true;
                } else {
                    updated[i] = column;
                    ++i;
                }
            }

            if (found) {
                setProperty(transaction, Constraint.TABLE_REFERENCE_REFERENCES, (Object[])updated);

                if (uow == null) {
                    transaction.commit();
                }
            } else {
                throw new KException(Messages.getString(Relational.REFERENCED_COLUMN_NOT_FOUND, columnId));
            }
        } catch (final Exception e) {
            throw handleError(uow, transaction, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see org.komodo.relational.model.ForeignKey#setReferencesTable(org.komodo.spi.repository.Repository.UnitOfWork,
     *      org.komodo.relational.model.Table)
     */
    @Override
    public void setReferencesTable( final UnitOfWork uow,
                                    final Table newReferencesTable ) throws KException {
        ArgCheck.isNotNull(newReferencesTable, "newReferencesTable"); //$NON-NLS-1$
        UnitOfWork transaction = uow;

        if (transaction == null) {
            transaction = getRepository().createTransaction("foreignkeyimpl-setReferencesTable", false, null); //$NON-NLS-1$
        }

        assert (transaction != null);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("setReferencesTable: transaction = {0}, newReferencesTable = {1}", //$NON-NLS-1$
                         transaction.getName(),
                         newReferencesTable);
        }

        try {
            String tableId = null;

            if (newReferencesTable != null) {
                tableId = newReferencesTable.getProperty(transaction, JcrLexicon.UUID.getString()).getStringValue(transaction);
            }

            setProperty(transaction, Constraint.TABLE_REFERENCE, tableId);

            if (uow == null) {
                transaction.commit();
            }
        } catch (final Exception e) {
            throw handleError(uow, transaction, e);
        }
    }

}
