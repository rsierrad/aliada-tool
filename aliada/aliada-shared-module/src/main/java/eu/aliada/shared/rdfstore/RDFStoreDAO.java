// ALIADA - Automatic publication under Linked Data paradigm
//          of library and museum data
//
// Component: aliada-shared
// Responsible: ALIADA Consortium
package eu.aliada.shared.rdfstore;

import static eu.aliada.shared.Strings.isNotNullAndNotEmpty;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.jena.atlas.web.auth.HttpAuthenticator;
import org.apache.jena.atlas.web.auth.SimpleAuthenticator;

import com.hp.hpl.jena.update.UpdateExecutionFactory;
import com.hp.hpl.jena.update.UpdateFactory;

import eu.aliada.shared.log.Log;
import eu.aliada.shared.log.MessageCatalog;

/**
 * RDF Store common operations. 
 *  
 * @author Idoia Murua
 * @since 1.0
 */
public class RDFStoreDAO {
	private final Log logger = new Log(RDFStoreDAO.class);
	
	private final Map<String, HttpAuthenticator> authCache = new HashMap<String, HttpAuthenticator>();
	
	/**
	 * It clears the graph in the RDF store using ISQL.
	 *
	 * @param isqlCommandPath	the path of the ISQL command of the RDF store.  
	 * @param storeIp			the IP of the RDF store.  
	 * @param storeSqlPort		the port for SQL data access.  
	 * @param user				the login required for authentication in the RDF store.
	 * @param password			the password required for authentication in the RDF store.
	 * @param graphUri			the URI of the graph.
	 * @return true if the graph has been cleared. False otherwise.
	 * @since 1.0
	 */
	public boolean clearGraphByIsql(String isqlCommandPath, String storeIp, int storeSqlPort, String user, String password, String graphUri) {
		boolean done = false;
		String clearGraphSPARQL = "EXEC=SPARQL CLEAR GRAPH <" + graphUri + ">;";
		//Compose ISQL command calling statement
		String isqlCommandFormat = "%s %s:%d %s %s \"%s\"";
		String isqlCommand = String.format(isqlCommandFormat,
				isqlCommandPath, storeIp,
				storeSqlPort, user,
				password, clearGraphSPARQL);
		//Execute ISQL command
		try {
			Runtime.getRuntime().exec(isqlCommand);
		} catch (Exception exception) {
			logger.error(MessageCatalog._00033_EXTERNAL_PROCESS_START_FAILURE, exception, isqlCommand);
		}
		return done;
	}

	/**
	 * It loads triples in a graph in the RDF store.
	 *
	 * @param triplesFilename	the path of the file containing the triples to load.  
	 * @param rdfSinkFolder		the URI of the RDF SINK folder in the RDF Store.  
	 * @param user				the login required for authentication in the RDF store.
	 * @param password			the password required for authentication in the RDF store.
	 * @return true if the triples have been loaded. False otherwise.
	 * @since 1.0
	 */
	public boolean loadDataIntoGraph(String triplesFilename, String rdfSinkFolder, String user, String password) {
		boolean done = false;
		//Get the triples file name
		File triplesFile = new File(triplesFilename);
		String triplesFilenameNoPath = triplesFile.getName();
		//Append the file name to the rdfSinkFolder 
		if ((rdfSinkFolder != null) && (!rdfSinkFolder.endsWith("/")))
			rdfSinkFolder = rdfSinkFolder + "/";
		rdfSinkFolder = rdfSinkFolder + triplesFilenameNoPath;
		//HTTP Authentication
		Base64 b = new Base64();
		byte[] authenticationArray = new String(user + ":" + password).getBytes();
		String encoding = b.encodeAsString(authenticationArray);
		
		try {
			URL putUrl = new URL(rdfSinkFolder);
			HttpURLConnection connection = (HttpURLConnection) putUrl.openConnection();
	
			connection.setDoOutput(true);
			connection.setInstanceFollowRedirects(false);
			connection.setRequestMethod("PUT");
			//Write Authentication
			connection.setRequestProperty  ("Authorization", "Basic " + encoding);			
			//Write File Data
			OutputStream os = connection.getOutputStream();		
			InputStream is = new FileInputStream(triplesFilename);
			byte buf[] = new byte[1024];
			int len;
			while ((len = is.read(buf)) > 0) {
				os.write(buf, 0, len);
			}
			is.close();
			int responseCode=((HttpURLConnection)connection).getResponseCode();
			if ((responseCode>= 200) &&(responseCode<=202) ) {
				done = true;
			} else {
				done = false;
			}
			((HttpURLConnection)connection).disconnect();
		} catch (Exception exception) {
			done = false;
			logger.error(MessageCatalog._00034_HTTP_PUT_FAILED, exception, rdfSinkFolder);
	    }
		return done;
	}

	/**
	 * It executes an UPDATE SPARQL query on the SPARQL endpoint.
	 *
	 * @param sparqlEndpointURI		the SPARQL endpoint URI.  
	 * @param user					the user name for the SPARQl endpoint.
	 * @param password				the password for the SPARQl endpoint.
	 * @param query					the UPDATE query to execute.
	 * @return true if the SPARQL has been executed. False otherwise.
	 * @since 1.0
	 */
	public boolean executeUpdateQuerySparqlEndpoint(String sparqlEndpointURI, String user, String password, String query) {
		boolean done = false;
		try {
			UpdateExecutionFactory.createRemote(
					UpdateFactory.create(query), 
					sparqlEndpointURI, 
					auth(sparqlEndpointURI, user, password))
				.execute();
			done = true;
		} catch (Exception exception) {
			logger.error(MessageCatalog._00035_SPARQL_FAILED, exception, query);
		}
		return done;
	}
	
	/**
	 * It executes an INSERT SPARQL query on the SPARQL endpoint.
	 *
	 * @param sparqlEndpointURI		the SPARQL endpoint URI.  
	 * @param user					the user name for the SPARQl endpoint.
	 * @param password				the password for the SPARQl endpoint.
	 * @param triples				the triples that will be added.
	 * throws Exception in case the INSERT goes wrong.
	 */
	public void executeInsert(
			final String sparqlEndpointURI, 
			final String graphName, 
			final String user, 
			final String password, 
			final String triples) throws Exception {
		UpdateExecutionFactory.createRemote(
				UpdateFactory.create(buildInsertQuery(graphName, triples)), 
				sparqlEndpointURI, 
				auth(sparqlEndpointURI, user, password))
			.execute();
	}	

	/**
	 * It clears the graph in the RDF store using SPARQL endpoint.
	 *
	 * @param sparqlEndpointURI		the SPARQL endpoint URI.  
	 * @param user					the user name for the SPARQl endpoint.
	 * @param password				the password for the SPARQl endpoint.
	 * @param graphUri				the URI of the graph.
	 * @return true if the graph has been cleared. False otherwise.
	 * @since 1.0
	 */
	public boolean clearGraphBySparql(String sparqlEndpointURI, String user, String password, String graphUri) {
		boolean done = false;
		String clearGraphSPARQL = "CLEAR GRAPH <" + graphUri + ">;";
		//Execute SPARQL
		done = executeUpdateQuerySparqlEndpoint(sparqlEndpointURI, user, password, clearGraphSPARQL);
		return done;
	}
	
	/**
	 * Builds a SPARQL INSERT with the given data.
	 * 
	 * @param graphName the graphName, null in case of default graph.
	 * @param triples the triples (in N3 format) that will be added.
	 * @return a new SPARQL INSERT query.
	 */
	String buildInsertQuery(final String graphName, final String triples) {
		final StringBuilder builder = new StringBuilder();
		builder.append("INSERT DATA ");
		if (isNotNullAndNotEmpty(graphName)) {
			builder.append("{ GRAPH ");
			if (graphName.startsWith("<") && graphName.endsWith(">")) {
				builder.append(graphName);
			} else 
			{
				builder.append("<").append(graphName).append(">");
			}
		}
		
		builder.append("{ ").append(triples).append("}");
		if (isNotNullAndNotEmpty(graphName)) {
			builder.append("}");
		}
		logger.debug(MessageCatalog._00036_QUERY_INFO, builder.toString());
		return builder.toString();
	}
	
	/**
	 * Returns the authenticator associated with a given endpoint.
	 * If no authentication can be found, then a new one is created and cached for further reuse.
	 * 
	 * @param endpointAddress the endpoint address.
	 * @param username the username.
	 * @param password the password.
	 * @return the authenticator associated with a given endpoint.
	 */
	HttpAuthenticator auth(final String endpointAddress, final String username, final String password) {
		HttpAuthenticator auth = authCache.get(endpointAddress);
		if (auth == null) {
			auth = new SimpleAuthenticator(username, password.toCharArray());
			authCache.put(endpointAddress, auth);
		}
		return auth;
	}
}