/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package org.komodo.relational.model.internal;

import java.util.ArrayList;
import java.util.List;
import org.komodo.relational.RelationalProperties;
import org.komodo.relational.internal.AdapterFactory;
import org.komodo.relational.internal.RelationalModelFactory;
import org.komodo.relational.internal.TypeResolver;
import org.komodo.relational.model.Model;
import org.komodo.relational.model.StatementOption;
import org.komodo.relational.model.UserDefinedFunction;
import org.komodo.repository.ObjectImpl;
import org.komodo.spi.KException;
import org.komodo.spi.repository.KomodoObject;
import org.komodo.spi.repository.KomodoType;
import org.komodo.spi.repository.Repository;
import org.komodo.spi.repository.Repository.UnitOfWork;
import org.modeshape.sequencer.ddl.dialect.teiid.TeiidDdlLexicon.CreateProcedure;
import org.modeshape.sequencer.ddl.dialect.teiid.TeiidDdlLexicon.SchemaElement;

/**
 * An implementation of a UDF.
 */
public final class UserDefinedFunctionImpl extends FunctionImpl implements UserDefinedFunction {

    private enum StandardOptions {

        CATEGORY,
        JAVA_CLASS,
        JAVA_METHOD;

    }

    /**
     * The resolver of a {@link UserDefinedFunction}.
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
        public UserDefinedFunction create( final UnitOfWork transaction,
                                           final Repository repository,
                                           final KomodoObject parent,
                                           final String id,
                                           final RelationalProperties properties ) throws KException {
            final AdapterFactory adapter = new AdapterFactory( repository );
            final Model parentModel = adapter.adapt( transaction, parent, Model.class );
            return RelationalModelFactory.createUserDefinedFunction( transaction, repository, parentModel, id );
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
        public Class< UserDefinedFunctionImpl > owningClass() {
            return UserDefinedFunctionImpl.class;
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
                ObjectImpl.validateType( transaction, kobject.getRepository(), kobject, CreateProcedure.FUNCTION_STATEMENT );
                ObjectImpl.validatePropertyValue( transaction,
                                                  kobject.getRepository(),
                                                  kobject,
                                                  SchemaElement.TYPE,
                                                  SchemaElementType.VIRTUAL.name() );
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
        public UserDefinedFunction resolve( final UnitOfWork transaction,
                                            final KomodoObject kobject ) throws KException {
            return new UserDefinedFunctionImpl( transaction, kobject.getRepository(), kobject.getAbsolutePath() );
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
     *         if an error occurs or if node at specified path is not a procedure
     */
    public UserDefinedFunctionImpl( final UnitOfWork uow,
                                    final Repository repository,
                                    final String workspacePath ) throws KException {
        super( uow, repository, workspacePath );
    }

    /**
     * {@inheritDoc}
     *
     * @see org.komodo.relational.model.UserDefinedFunction#getCategory(org.komodo.spi.repository.Repository.UnitOfWork)
     */
    @Override
    public String getCategory( final UnitOfWork transaction ) throws KException {
        final StatementOption option = Utils.getOption( transaction, this, StandardOptions.CATEGORY.name() );

        if (option == null) {
            return null;
        }

        return option.getOption( transaction );
    }

    /**
     * {@inheritDoc}
     *
     * @see org.komodo.relational.model.OptionContainer#getCustomOptions(org.komodo.spi.repository.Repository.UnitOfWork)
     */
    @Override
    public StatementOption[] getCustomOptions( final UnitOfWork uow ) throws KException {
        UnitOfWork transaction = uow;

        if (transaction == null) {
            transaction = getRepository().createTransaction( "userdefinedfunctionimpl-getCustomOptions", true, null ); //$NON-NLS-1$
        }

        assert ( transaction != null );

        try {
            // get super custom options then delete any subclass standard options
            final StatementOption[] superOptions = super.getCustomOptions( transaction );
            StatementOption[] result = StatementOption.NO_OPTIONS;

            if (superOptions.length != 0) {
                final List< StatementOption > temp = new ArrayList<>( superOptions.length );

                for (final StatementOption option : superOptions) {
                    if (StandardOptions.valueOf( option.getName( transaction ) ) == null) {
                        temp.add( option );
                    }
                }

                if (!temp.isEmpty()) {
                    result = temp.toArray( new StatementOption[temp.size()] );
                }
            }

            if (uow == null) {
                transaction.commit();
            }

            return result;
        } catch (final Exception e) {
            throw handleError( uow, transaction, e );
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see org.komodo.relational.model.UserDefinedFunction#getJavaClass(org.komodo.spi.repository.Repository.UnitOfWork)
     */
    @Override
    public String getJavaClass( final UnitOfWork transaction ) throws KException {
        final StatementOption option = Utils.getOption( transaction, this, StandardOptions.JAVA_CLASS.name() );

        if (option == null) {
            return null;
        }

        return option.getOption( transaction );
    }

    /**
     * {@inheritDoc}
     *
     * @see org.komodo.relational.model.UserDefinedFunction#getJavaMethod(org.komodo.spi.repository.Repository.UnitOfWork)
     */
    @Override
    public String getJavaMethod( final UnitOfWork transaction ) throws KException {
        final StatementOption option = Utils.getOption( transaction, this, StandardOptions.JAVA_METHOD.name() );

        if (option == null) {
            return null;
        }

        return option.getOption( transaction );
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
     * @see org.komodo.repository.ObjectImpl#getTypeIdentifier(org.komodo.spi.repository.Repository.UnitOfWork)
     */
    @Override
    public KomodoType getTypeIdentifier( final UnitOfWork uow ) {
        return RESOLVER.identifier();
    }

    /**
     * {@inheritDoc}
     *
     * @see org.komodo.relational.model.UserDefinedFunction#setCategory(org.komodo.spi.repository.Repository.UnitOfWork,
     *      java.lang.String)
     */
    @Override
    public void setCategory( final UnitOfWork transaction,
                             final String newCategory ) throws KException {
        setStatementOption( transaction, StandardOptions.CATEGORY.name(), newCategory );
    }

    /**
     * {@inheritDoc}
     *
     * @see org.komodo.relational.model.UserDefinedFunction#setJavaClass(org.komodo.spi.repository.Repository.UnitOfWork,
     *      java.lang.String)
     */
    @Override
    public void setJavaClass( final UnitOfWork transaction,
                              final String newJavaClass ) throws KException {
        setStatementOption( transaction, StandardOptions.JAVA_CLASS.name(), newJavaClass );
    }

    /**
     * {@inheritDoc}
     *
     * @see org.komodo.relational.model.UserDefinedFunction#setJavaMethod(org.komodo.spi.repository.Repository.UnitOfWork,
     *      java.lang.String)
     */
    @Override
    public void setJavaMethod( final UnitOfWork transaction,
                               final String newJavaMethod ) throws KException {
        setStatementOption( transaction, StandardOptions.JAVA_METHOD.name(), newJavaMethod );
    }

}
