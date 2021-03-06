// ALIADA - Automatic publication under Linked Data paradigm
//          of library and museum data
//
// Component: aliada-ckan-datahub-page-creation
// Responsible: ALIADA Consortium
package eu.aliada.ckancreation.listeners;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import eu.aliada.shared.log.Log;
import eu.aliada.ckancreation.log.MessageCatalog;
import eu.aliada.ckancreation.rdbms.DBConnectionManager;

import javax.servlet.ServletContext;

/**
 * CKAN Creation startup and shutdown listener.
 * 
 * @author Idoia Murua
 * @since 2.0
 */
public class ApplicationLifecycleListener implements ServletContextListener {
	/** For logging. */
	private static final Log LOGGER = new Log(ApplicationLifecycleListener.class);
	
	/**
	 * Override the ServletContextListener.contextInitializedReceives method.
	 * It is called when the web application initialization process is starting.
	 * This function gets the DDBB connection and saves it in a 
	 * Servlet context attribute.
	 *
	 * @param the ServletContextEvent containing the ServletContext  
	 *                  that is being initialized.
	 * @since 2.0
	 */
	@Override
	public void contextInitialized(final ServletContextEvent event) {
		LOGGER.info(MessageCatalog._00001_STARTING);
		final ServletContext servletC = event.getServletContext();
		//Get DDBB connection
		final DBConnectionManager dbConn = new DBConnectionManager();
		//Save DDBB connection in Servlet Context attribute
		servletC.setAttribute("db", dbConn);
	}

	/**
	 * Override the ServletContextListener.contextDestroyed method.
	 * It is called when the ServletContext is about to be shut down.
	 * This function closes the DDBB connection.
	 *
	 * @param the ServletContextEvent containing the ServletContext  
	 *                  that is being destroyed.
	 * @since 2.0
	 */
	@Override
	public void contextDestroyed(final ServletContextEvent event) {
		//Close DDBB connection
		final DBConnectionManager dbConn = (DBConnectionManager) event.getServletContext().getAttribute("db");
		dbConn.closeConnection();
		LOGGER.info(MessageCatalog._00012_STOPPED);	
	}
}
