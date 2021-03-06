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
package org.komodo.test.utils;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.jcr.Node;
import javax.jcr.PropertyIterator;
import javax.jcr.Session;
import javax.jcr.observation.ObservationManager;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.komodo.repository.LocalRepository;
import org.komodo.repository.LocalRepository.LocalRepositoryId;
import org.komodo.repository.RepositoryImpl.UnitOfWorkImpl;
import org.komodo.spi.constants.StringConstants;
import org.komodo.spi.repository.KomodoObject;
import org.komodo.spi.repository.Property;
import org.komodo.spi.repository.Repository.State;
import org.komodo.spi.repository.Repository.UnitOfWork;
import org.komodo.spi.repository.RepositoryClient;
import org.komodo.spi.repository.RepositoryClientEvent;
import org.komodo.utils.KLog;
import org.modeshape.jcr.api.observation.Event.Sequencing;

/**
 * Provides framework for testing an instance of the local repository
 * which is only cached in memory hence should be persisted between
 * tests.
 *
 * The initLocalRepository method will be called prior to any tests executing
 * ensuring that the _repo is initialised and reachable. This can be added to
 * the singleton KEngine instance using KEngine.setDefaultRepository if required.
 *
 * When tests are complete, destroyLocalRepository will be called and attempt
 * to stop and close down the repository. Since the repository is in-memory only
 * then nullifying it will destroy all data hence clearance between test classes
 * should be unnecessary. Sub-classes using KEngine should stop the KEngine
 * in an @AfterClass annotated method and use the _repoObserver to await
 * the shutdown of the repository. The destoryLocalRepository function will
 * still run but it should do nothing since _repo is shutdown via the KEngine.
 */
@SuppressWarnings( {"javadoc", "nls"} )
public abstract class AbstractLocalRepositoryTest extends AbstractLoggingTest implements StringConstants {

    private static final String TEST_REPOSITORY_CONFIG = "test-local-repository-in-memory-config.json";

    protected static LocalRepository _repo = null;

    protected static LocalRepositoryObserver _repoObserver = null;

    private NodePathListener nodePathListener;

    @BeforeClass
    public static void initLocalRepository() throws Exception {

        URL configUrl = AbstractLocalRepositoryTest.class.getResource(TEST_REPOSITORY_CONFIG);

        LocalRepositoryId id = new LocalRepositoryId(configUrl, DEFAULT_LOCAL_WORKSPACE_NAME);
        _repo = new LocalRepository(id);
        assertThat(_repo.getState(), is(State.NOT_REACHABLE));
        assertThat(_repo.ping(), is(false));

        _repoObserver = new LocalRepositoryObserver();
        assertNotNull(_repoObserver);
        _repo.addObserver(_repoObserver);

        // Start the repository
        final RepositoryClient client = mock(RepositoryClient.class);
        final RepositoryClientEvent event = RepositoryClientEvent.createStartedEvent(client);
        _repo.notify(event);

        // Wait for the starting of the repository or timeout of 1 minute
        if (!_repoObserver.getLatch().await(100, TimeUnit.MINUTES)) {
            throw new RuntimeException("Local repository did not start");
        }
    }

    /**
     * Shutdown and destroy repo
     *
     * @throws Exception
     */
    @AfterClass
    public static void destroyLocalRepository() throws Exception {
        assertNotNull(_repo);
        assertNotNull(_repoObserver);

        if (State.REACHABLE.equals(_repo.getState())) {
            _repoObserver.resetLatch();

            RepositoryClient client = mock(RepositoryClient.class);
            RepositoryClientEvent event = RepositoryClientEvent.createShuttingDownEvent(client);
            _repo.notify(event);
        }

        if (! _repoObserver.getLatch().await(1, TimeUnit.MINUTES))
            throw new RuntimeException("Local repository was not stopped");

        _repo.removeObserver(_repoObserver);

        _repoObserver = null;
        _repo = null;
    }

    @After
    public void clearLocalRepository() throws Exception {
        assertNotNull(_repo);

        if (! State.REACHABLE.equals(_repo.getState()))
            return;

        _repoObserver.resetLatch();

        RepositoryClient client = mock(RepositoryClient.class);
        RepositoryClientEvent event = RepositoryClientEvent.createClearEvent(client);
        _repo.notify(event);

        if (! _repoObserver.getLatch().await(1, TimeUnit.MINUTES))
            throw new RuntimeException("Local repository was not cleared");
    }

    protected Session session(UnitOfWork uow) throws Exception {
        if (!(uow instanceof UnitOfWorkImpl))
            throw new Exception("Attempt to extract session from unit of work which is not a UnitOfWorkImpl");

        Session session = ((UnitOfWorkImpl)uow).getSession();
        return session;
    }

    /**
     * @param countdown equivalent to number of sql query expressions to be sequenced
     * @param pathsToBeSequenced wilcarded patterns against which to compare the sequenced nodes
     * @return the latch for awaiting the sequencing
     * @throws Exception
     */
    protected CountDownLatch addSequencePathListener(UnitOfWork uow, final String... pathsToBeSequenced) throws Exception {
        Session session = session(uow);
        ObservationManager manager = session.getWorkspace().getObservationManager();
        assertNotNull(manager);

        final CountDownLatch updateLatch = new CountDownLatch(pathsToBeSequenced.length);
        List<String> seqPaths = Arrays.asList(pathsToBeSequenced);
        nodePathListener = new NodePathListener(seqPaths, updateLatch);
        manager.addEventListener(nodePathListener, Sequencing.NODE_SEQUENCED, null, true, null, null, false);
        return updateLatch;
    }

    private void traverse(String tabs, Node node, StringBuffer buffer) throws Exception {
        buffer.append(tabs + node.getName() + NEW_LINE);

        PropertyIterator propertyIterator = node.getProperties();
        while (propertyIterator.hasNext()) {
            javax.jcr.Property property = propertyIterator.nextProperty();
            buffer.append(tabs + TAB + "@" + property.toString() + NEW_LINE);
        }

        javax.jcr.NodeIterator children = node.getNodes();
        while (children.hasNext()) {
            traverse(tabs + TAB, children.nextNode(), buffer);
        }
    }

    protected void traverse(UnitOfWork uow) throws Exception {
        Session session = session(uow);
        StringBuffer buffer = new StringBuffer(NEW_LINE);
        traverse(TAB, session.getRootNode(), buffer);
        KLog.getLogger().info(buffer.toString());
    }

    /**
     * @param property
     * @return String representation of property and its values
     * @throws Exception
     */
    private String toString(Property property) throws Exception {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append(property.getName(null)).append('=');
            if (property.isMultiple(null)) {
                sb.append('[');
                Object[] values = property.getValues(null);
                for (int i = 0; i < values.length; ++i) {
                    Object value = values[i];
                    sb.append(value);
                    if ((i + 1) < values.length)
                        sb.append(',');
                }
                sb.append(']');
            } else {
                Object value = property.getValue(null);
                sb.append(value);
            }
        } catch (Exception e) {
            sb.append(" on deleted node ").append(property.getAbsolutePath());
        }

        return sb.toString();
    }

    private void traverse(String tabs, KomodoObject kObject, StringBuffer buffer) throws Exception {
        buffer.append(tabs + kObject.getName(null) + NEW_LINE);

        String[] propertyNames = kObject.getPropertyNames(null);

        for (String propertyName : propertyNames) {
            Property property = kObject.getProperty(null, propertyName);
            buffer.append(tabs + TAB + "@" + toString(property) + NEW_LINE);
        }

        KomodoObject[] children = kObject.getChildren(null);
        for (int i = 0; i < children.length; ++i)
            traverse(tabs + TAB, children[i], buffer);
    }

    protected void traverse(KomodoObject kObject) throws Exception {
        StringBuffer buffer = new StringBuffer(NEW_LINE);
        traverse(TAB, kObject, buffer);
        KLog.getLogger().info(buffer.toString());
    }
}