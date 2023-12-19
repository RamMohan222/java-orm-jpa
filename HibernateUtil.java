import java.util.Arrays;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.resource.transaction.spi.TransactionStatus;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Set of static utility methods for use by the Hibernate persistence layer.
 */
public final class HibernateUtil {

    private static final Logger LOG = LoggerFactory.getLogger(HibernateUtil.class);

    private static final ServiceRegistry SERVICE_REGISTRY;
    private static final SessionFactory SESSION_FACTORY;
    private static final ThreadLocal<Session> SESSION;
    private static final ThreadLocal<Transaction> TRANSACTION;

    /* Initialise Hibernate Factory using Annotations */
    static {
        final Iterable<Class<? extends Persistable>> entities = Arrays.asList(User.class);

        try {
            final Configuration configuration = new Configuration().configure(); // from hibernate.cfg.xml
            for (final Class<?> clazz : entities) {
                configuration.addAnnotatedClass(clazz);
            }

            SERVICE_REGISTRY = new StandardServiceRegistryBuilder()
                    .applySettings(configuration.getProperties())
                    .build();
            SESSION_FACTORY = configuration.buildSessionFactory(SERVICE_REGISTRY);
            SESSION = new ThreadLocal<>();
            TRANSACTION = new ThreadLocal<>();

        } catch (final HibernateException e) {
            LOG.error("Exception in HibernateUtil's static initialiser", e);
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void closeSession() {
        final Session session = SESSION.get();
        if (session != null) {
            SESSION.remove();
            if (session.isOpen()) {
                session.close();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Closed session: {}", session);
                }
            }
        }
    }

    private static Session openSession() throws HibernateException {
        final Session session = SESSION_FACTORY.openSession();
        SESSION.set(session);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Opened session: {}", session);
        }

        /* Create a new transaction and associate it with the current thread. */
        final Transaction transaction;
        try {
            /*
             * The method beginTransaction() throws org.hibernate.exception.JDBCConnectionException
             * when unable to get a JDBC connection.
             */
            transaction = session.beginTransaction();
        } catch (final HibernateException e) {
            /* Failed to begin the transaction, so close the session. */
            closeSession();
            throw e;
        }

        TRANSACTION.set(transaction);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Begun transaction: {}", transaction);
        }

        return session;
    }

    /**
     * Returns the Session associated with the current thread.
     * @return The Session associated with the current thread.
     * @throws HibernateException If an Hibernate error occurs.
     * @see Session
     */
    public static Session getSession() throws HibernateException {
        final Session session = SESSION.get();
        return session == null ? openSession() : session;
    }

    /**
     * Checks if there is a {@link Session} associated with the current thread.
     * @return {@code true} if there is a {@link Session} associated with the
     *         current thread. Otherwise returns {@code false}.
     */
    public static boolean hasSession() {
        return SESSION.get() != null;
    }

    /**
     * Flush the Session associated with the current thread.
     * @throws HibernateException If an Hibernate error occurs.
     * @see Session
     */
    public static void flushSession() throws HibernateException {
        final Session session = SESSION.get();
        if (session != null) {
            session.flush();
        }
    }

    /**
     * Closes the Session associated with the current thread.
     * @param commit <tt>true</tt> if the transaction should be committed;
     *               <tt>false</tt> if the transaction should be rolled back;
     * @throws HibernateException If an Hibernate error occurs.
     * @see Session
     */
    public static void closeSession(final boolean commit) throws HibernateException {
        try {
            if (commit) {
                commitTransaction();
            } else {
                rollbackTransaction();
            }
        } finally {
            /* now close the session */
            closeSession();
        }
    }

    /**
     * Roll back the transaction associated with the current thread.
     * @throws HibernateException If an Hibernate error occurs.
     * @see Transaction
     */
    private static void rollbackTransaction() throws HibernateException {
        final Transaction transaction = TRANSACTION.get();
        TRANSACTION.remove();

        if (transaction == null) {
            if (SESSION.get() != null) {
                LOG.warn(exception.getMessage(), exception);
            }
        } else if (transaction.getStatus() == TransactionStatus.COMMITTED) {
            LOG.warn("Transaction was already comitted: " + transaction);
            LOG.warn(exception.getMessage(), exception);
        } else if (transaction.getStatus() == TransactionStatus.ROLLED_BACK) {
            LOG.warn("Transaction was already rolled back: " + transaction);
            LOG.warn(exception.getMessage(), exception);
        } else {
            transaction.rollback();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Rolled back transaction: {}", transaction);
            }
        }
    }

    /**
     * Complete the transaction associated with the current thread.
     * @throws HibernateException If an Hibernate error occurs.
     * @see Transaction
     */
    private static void commitTransaction() throws HibernateException {
        final Transaction transaction = TRANSACTION.get();
        TRANSACTION.remove();

        if (transaction == null) {
            if (SESSION.get() != null) {
                LOG.warn("Can't commit null transaction.");
                LOG.warn(exception.getMessage(), exception);
            }

        } else if (transaction.getStatus() == TransactionStatus.COMMITTED) {
            LOG.warn("Transaction was already comitted: " + transaction);
            LOG.warn(exception.getMessage(), exception);
        } else if (transaction.getStatus() == TransactionStatus.ROLLED_BACK) {
            LOG.warn("Transaction was already rolled back: " + transaction);
            LOG.warn(exception.getMessage(), exception);
        } else {
            transaction.commit();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Committed transaction: {}", transaction);
            }
        }
    }

    /**
     * Called to indicate that the Hibernate service is being taken out of service permanently.
     * @throws HibernateException If an error occurs.
     */
    public static void destroy() throws HibernateException {
        SESSION_FACTORY.close();
        for (
                ServiceRegistry serviceRegistry = SERVICE_REGISTRY;
                serviceRegistry != null;
                serviceRegistry = serviceRegistry.getParentServiceRegistry()
        ) {
            if (serviceRegistry instanceof ServiceRegistryImplementor) {
                ((ServiceRegistryImplementor)serviceRegistry).destroy();
            }
        }
    }

    /** Hide default constructor. */
    private HibernateUtil() {
        throw new UnsupportedOperationException();
    }

    /* This method is not belogs to this utils class, another way of creating SessionFactory with MetadataSources */
    private SessionFactory createSessionFactory() throws Exception {
        final StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .configure() // from hibernate.cfg.xml
                .build();

        SessionFactory sessionFactory = null;
        try {
            sessionFactory = new MetadataSources(registry).buildMetadata().buildSessionFactory();
        } catch (Exception e) {
            StandardServiceRegistryBuilder.destroy(registry);
        }
        return sessionFactory;
    }
    
}
