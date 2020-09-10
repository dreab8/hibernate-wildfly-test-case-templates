/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hiberntate.util;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.spi.PersistenceUnitTransactionType;

import org.hibernate.bytecode.enhance.spi.EnhancementContext;
import org.hibernate.cfg.Environment;
import org.hibernate.dialect.Dialect;
import org.hibernate.jpa.AvailableSettings;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.hibernate.jpa.boot.spi.Bootstrap;
import org.hibernate.jpa.boot.spi.PersistenceUnitDescriptor;

import org.hibernate.testing.junit4.BaseUnitTestCase;
import org.junit.After;
import org.junit.Before;

import org.jboss.as.jpa.hibernate5.HibernateArchiveScanner;
import org.jboss.logging.Logger;

/**
 * @author Andrea Boriero
 * @author Emmanuel Bernard
 * @author Hardy Ferentschik
 */
public abstract class WildFlyFunctionalTestCase extends BaseUnitTestCase {
	public static final String NAMING_STRATEGY_JPA_COMPLIANT_IMPL = "org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl";
	private static final Logger log = Logger.getLogger( WildFlyFunctionalTestCase.class );

	private static final Dialect dialect = Dialect.getDialect();

	private EntityManagerFactory entityManagerFactory;

	private EntityManager em;
	private ArrayList<EntityManager> isolatedEms = new ArrayList<EntityManager>();

	protected Dialect getDialect() {
		return dialect;
	}

	protected EntityManagerFactory entityManagerFactory() {
		return entityManagerFactory;
	}

	@Before
	@SuppressWarnings({ "UnusedDeclaration" })
	public void buildEntityManagerFactory() {
		log.trace( "Building EntityManagerFactory" );

		entityManagerFactory = Bootstrap.getEntityManagerFactoryBuilder(
				buildPersistenceUnitDescriptor(),
				buildSettings()
		).build().unwrap( EntityManagerFactory.class );

		afterEntityManagerFactoryBuilt();
	}

	private PersistenceUnitDescriptor buildPersistenceUnitDescriptor() {
		return new TestingPersistenceUnitDescriptorImpl( getClass().getSimpleName() );
	}

	public static class TestingPersistenceUnitDescriptorImpl implements PersistenceUnitDescriptor {
		private final String name;

		public TestingPersistenceUnitDescriptorImpl(String name) {
			this.name = name;
		}

		@Override
		public URL getPersistenceUnitRootUrl() {
			return null;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getProviderClassName() {
			return HibernatePersistenceProvider.class.getName();
		}

		@Override
		public boolean isUseQuotedIdentifiers() {
			return false;
		}

		@Override
		public boolean isExcludeUnlistedClasses() {
			return false;
		}

		@Override
		public PersistenceUnitTransactionType getTransactionType() {
			return null;
		}

		@Override
		public ValidationMode getValidationMode() {
			return null;
		}

		@Override
		public SharedCacheMode getSharedCacheMode() {
			return null;
		}

		@Override
		public List<String> getManagedClassNames() {
			return null;
		}

		@Override
		public List<String> getMappingFileNames() {
			return null;
		}

		@Override
		public List<URL> getJarFileUrls() {
			return null;
		}

		@Override
		public Object getNonJtaDataSource() {
			return null;
		}

		@Override
		public Object getJtaDataSource() {
			return null;
		}

		@Override
		public Properties getProperties() {
			return null;
		}

		@Override
		public ClassLoader getClassLoader() {
			return null;
		}

		@Override
		public ClassLoader getTempClassLoader() {
			return null;
		}

		@Override
		public void pushClassTransformer(EnhancementContext enhancementContext) {
		}
	}

	@SuppressWarnings("unchecked")
	protected Map buildSettings() {
		Map settings = getConfig();
		addMappings( settings );

		if ( createSchema() ) {
			settings.put( org.hibernate.cfg.AvailableSettings.HBM2DDL_AUTO, "create-drop" );
		}
		settings.put( org.hibernate.cfg.AvailableSettings.DIALECT, getDialect().getClass().getName() );
		return settings;
	}

	@SuppressWarnings("unchecked")
	protected void addMappings(Map settings) {
		String[] mappings = getMappings();
		if ( mappings != null ) {
			settings.put( AvailableSettings.HBXML_FILES, String.join( ",", mappings ) );
		}
	}

	protected static final String[] NO_MAPPINGS = new String[0];

	protected String[] getMappings() {
		return NO_MAPPINGS;
	}

	protected Map getConfig() {
		Map<Object, Object> config = Environment.getProperties();
		ArrayList<Class> classes = new ArrayList<>();

		classes.addAll( Arrays.asList( getAnnotatedClasses() ) );
		config.put( AvailableSettings.LOADED_CLASSES, classes );
		for ( Map.Entry<Class, String> entry : getCachedClasses().entrySet() ) {
			config.put( AvailableSettings.CLASS_CACHE_PREFIX + "." + entry.getKey().getName(), entry.getValue() );
		}
		for ( Map.Entry<String, String> entry : getCachedCollections().entrySet() ) {
			config.put( AvailableSettings.COLLECTION_CACHE_PREFIX + "." + entry.getKey(), entry.getValue() );
		}
		if ( getEjb3DD().length > 0 ) {
			ArrayList<String> dds = new ArrayList<>();
			dds.addAll( Arrays.asList( getEjb3DD() ) );
			config.put( AvailableSettings.XML_FILE_NAMES, dds );
		}
		addWildFlyConfigOptions( config );
		addConfigOptions( config );
		return config;
	}

	private void addWildFlyConfigOptions(Map<Object, Object> config) {
		config.put(
				org.hibernate.cfg.AvailableSettings.JPAQL_STRICT_COMPLIANCE,
				"true"
		); // JIPI-24 ignore jpql aliases case
		config.put( org.hibernate.cfg.AvailableSettings.USE_NEW_ID_GENERATOR_MAPPINGS, "true" );
		config.put( org.hibernate.cfg.AvailableSettings.KEYWORD_AUTO_QUOTING_ENABLED, "false" );
		config.put( org.hibernate.cfg.AvailableSettings.IMPLICIT_NAMING_STRATEGY, NAMING_STRATEGY_JPA_COMPLIANT_IMPL );
		config.put( org.hibernate.cfg.AvailableSettings.SCANNER, HibernateArchiveScanner.class );
//		options.put(AvailableSettings.APP_CLASSLOADER, pu.getClassLoader());
//		options.put( org.hibernate.ejb.AvailableSettings.ENTITY_MANAGER_FACTORY_NAME, pu.getScopedPersistenceUnitName());
//		options.put( AvailableSettings.SESSION_FACTORY_NAME, pu.getScopedPersistenceUnitName());
//		if (!pu.getProperties().containsKey(AvailableSettings.SESSION_FACTORY_NAME)) {
//			putPropertyIfAbsent(pu, properties, AvailableSettings.SESSION_FACTORY_NAME_IS_JNDI, Boolean.FALSE);
//		}
		// the following properties were added to Hibernate ORM 5.3, for JPA 2.2 spec compliance.
		config.put( org.hibernate.cfg.AvailableSettings.PREFER_GENERATOR_NAME_AS_DEFAULT_SEQUENCE_NAME, true );
		config.put( org.hibernate.cfg.AvailableSettings.JPA_TRANSACTION_COMPLIANCE, true );
		config.put( org.hibernate.cfg.AvailableSettings.JPA_CLOSED_COMPLIANCE, true );
		config.put( org.hibernate.cfg.AvailableSettings.JPA_QUERY_COMPLIANCE, true );
		config.put( org.hibernate.cfg.AvailableSettings.JPA_LIST_COMPLIANCE, true );
		config.put( org.hibernate.cfg.AvailableSettings.JPA_CACHING_COMPLIANCE, true );
		config.put( org.hibernate.cfg.AvailableSettings.JPA_PROXY_COMPLIANCE, true );
		config.put( org.hibernate.cfg.AvailableSettings.ENABLE_LAZY_LOAD_NO_TRANS, false );
		config.put( org.hibernate.cfg.AvailableSettings.JPA_ID_GENERATOR_GLOBAL_SCOPE_COMPLIANCE, true );

		// Search hint
		config.put( "hibernate.search.index_uninverting_allowed", "true" );
	}

	protected void addConfigOptions(Map options) {
	}

	protected static final Class<?>[] NO_CLASSES = new Class[0];

	protected Class<?>[] getAnnotatedClasses() {
		return NO_CLASSES;
	}

	public Map<Class, String> getCachedClasses() {
		return new HashMap<>();
	}

	public Map<String, String> getCachedCollections() {
		return new HashMap<>();
	}

	public String[] getEjb3DD() {
		return new String[] {};
	}

	protected void afterEntityManagerFactoryBuilt() {
	}

	protected boolean createSchema() {
		return true;
	}


	@After
	@SuppressWarnings({ "UnusedDeclaration" })
	public void releaseResources() {
		try {
			releaseUnclosedEntityManagers();
		}
		finally {
			if ( entityManagerFactory != null && entityManagerFactory.isOpen() ) {
				entityManagerFactory.close();
			}
		}
		// Note we don't destroy the service registry as we are not the ones creating it
	}

	private void releaseUnclosedEntityManagers() {
		releaseUnclosedEntityManager( this.em );

		for ( EntityManager isolatedEm : isolatedEms ) {
			releaseUnclosedEntityManager( isolatedEm );
		}
	}

	private void releaseUnclosedEntityManager(EntityManager em) {
		if ( em == null ) {
			return;
		}
		if ( em.getTransaction().isActive() ) {
			em.getTransaction().rollback();
			log.warn( "You left an open transaction! Fix your test case. For now, we are closing it for you." );
		}
		if ( em.isOpen() ) {
			// as we open an EM before the test runs, it will still be open if the test uses a custom EM.
			// or, the person may have forgotten to close. So, do not raise a "fail", but log the fact.
			em.close();
			log.warn( "The EntityManager is not closed. Closing it." );
		}
	}

	protected EntityManager createEntityManager() {
		return createEntityManager( Collections.emptyMap() );
	}

	protected EntityManager createEntityManager(Map properties) {
		// always reopen a new EM and close the existing one
		if ( em != null && em.isOpen() ) {
			em.close();
		}
		em = entityManagerFactory.createEntityManager( properties );
		return em;
	}

	protected void inTransaction(Consumer<EntityManager> action) {
		TransactionUtil.inTransaction( entityManagerFactory, action );
	}

	protected <R> R fromTransaction(Function<EntityManager, R> action) {
		return TransactionUtil.fromTransaction( entityManagerFactory, action );
	}

	protected void inEntityManager(Consumer<EntityManager> action) {
		TransactionUtil.inEntityManager( entityManagerFactory, action );
	}

	public <R> R fromEntityManager(EntityManagerFactory emf, Function<EntityManager, R> action) {
		return TransactionUtil.fromEntityManager( entityManagerFactory, action );
	}

}

