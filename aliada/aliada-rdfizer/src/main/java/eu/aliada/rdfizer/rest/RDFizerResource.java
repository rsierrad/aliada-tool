// ALIADA - Automatic publication under Linked Data paradigm
//          of library and museum data
//
// Component: aliada-rdfizer
// Responsible: ALIADA Consortiums
package eu.aliada.rdfizer.rest;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import javax.management.InstanceAlreadyExistsException;
import javax.management.JMException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import eu.aliada.rdfizer.datasource.Cache;
import eu.aliada.rdfizer.datasource.rdbms.JobConfiguration;
import eu.aliada.rdfizer.log.MessageCatalog;
import eu.aliada.rdfizer.mx.ManagementRegistrar;
import eu.aliada.rdfizer.mx.RDFizer;
import eu.aliada.shared.log.Log;

/**
 * RDF-izer REST resource representation.
 * 
 * @author Andrea Gazzarini
 * @since 1.0
 */
@Singleton
@Component
@Path("/")
public class RDFizerResource implements RDFizer {
	
	private static final Log LOGGER = new Log(RDFizerResource.class);

	@Context
	private UriInfo uriInfo;
	
	@Value(value = "${marcxml.input.dir}")
	protected String marcXmlInputDir;
	
	@Value(value = "${lido.input.dir}")
	protected String lidoInputDir;
	
	@Autowired
	protected Cache cache;

	protected boolean enabled = true;
	
	@PUT
	@Path("/enable")
	@Override
	public void enable() {
		enabled = true;
	}

	@POST
	@Path("/disable")
	@Override
	public void disable() {
		enabled = false;
	}

	/**
	 * Creates a new job on the RDF-izer.
	 * 
	 * @param id the job identifier associated with this instance.
	 * @return a response which includes the URI of the new job.
	 */
	@PUT
	@Path("/jobs/{jobid}")
	public Response newJob(@PathParam("jobid") final Integer id) {
		if (!enabled) {
			return Response.status(Status.NOT_ACCEPTABLE).build();
		}
		
		LOGGER.debug(MessageCatalog._00029_NEW_JOB_REQUEST);
		
		if (id == null) {
			LOGGER.error(MessageCatalog._00028_MISSING_INPUT_PARAM, "jobid");
			return Response.status(Status.BAD_REQUEST).build();			
		}
		
		String path = null;
		try {
			final JobConfiguration configuration = cache.getJobConfiguration(id);
			if (configuration == null) {
				LOGGER.error(MessageCatalog._00032_JOB_CONFIGURATION_NOT_FOUND, id);
				return Response.status(Status.BAD_REQUEST).build();								
			}
			
			final File datafile = new File(configuration.getDatafile());
			if (!datafile.canWrite()) {
				LOGGER.error(MessageCatalog._00020_WRONG_FILE_PERMISSIONS, datafile.getAbsolutePath());
				return Response.status(Status.BAD_REQUEST).build();						
			}
			
			path = datafile.getAbsolutePath();
			LOGGER.debug(MessageCatalog._00030_NEW_JOB_REQUEST_DEBUG, id, path);
			
			final String listenPath = listenPath(configuration.getFormat());
			if (listenPath == null) {
				LOGGER.error(MessageCatalog._00033_UNSUPPORTED_FORMAT, configuration.getFormat(), id);
				return Response.status(Status.BAD_REQUEST).build();										
			}
						
			final java.nio.file.Path source = Paths.get(path);
			final java.nio.file.Path target = Paths.get(listenPath + "/" + rdfizerDataFilename(datafile, id));
			
			try {
				final JobResource newJobResource = new JobResource(configuration);
				ManagementRegistrar.registerJob(newJobResource);
			} catch (JMException exception) {
				LOGGER.error(MessageCatalog._00045_MX_JOB_RESOURCE_REGISTRATION_FAILED, configuration.getId());
			}
			
			Files.move(source, target, REPLACE_EXISTING);
			return Response.created(uriInfo.getAbsolutePathBuilder().build()).build();
		} catch (final IOException exception) {
			LOGGER.debug(MessageCatalog._00030_NEW_JOB_REQUEST_DEBUG, id, path);
			return Response.serverError().build();						
		} catch (final DataAccessException exception)  {
			LOGGER.debug(MessageCatalog._00031_DATA_ACCESS_FAILURE, exception);
			return Response.serverError().build();						
		}
	}
	
	/**
	 * Returns the path where RDF-izer is listening for input datafiles.
	 * 
	 * @param format the format associated with the current conversion request.
	 * @return the path where RDF-izer is listening for input datafiles.
	 */
	String listenPath(final String format) {
		if ("lido".equals(format)) {
			return lidoInputDir;
		} else if ("marcxml".equals(format)) {
			return marcXmlInputDir;
		}
		return null;
	}
	
	/**
	 * Returns a valid RDFizer datafile name.
	 * RDFizer needs an input file with a given format, specifically composed by
	 * 
	 * name.suffix.jobid
	 * 
	 * where 
	 * 
	 * <ul>
	 * 	<li>name is the original input file name;</li>
	 * 	<li>suffix (optional) is the suffix of the original input file;</li>
	 * 	<li>jobid is the identifier that has been assigned to the job;</li>
	 * </ul>
	 * 
	 * @param file the input data file.
	 * @param jobId the job identifier.
	 * @return a valid RDFizer datafile name.
	 */
	String rdfizerDataFilename(final File file, final Integer jobId) {
		return new StringBuilder()
			.append(file.getName())
			.append(".")
			.append(jobId)
			.toString();
	}
	
	/**
	 * Initialises this resource.
	 */
	@PostConstruct
	public void init() {
		final MBeanServer mxServer = ManagementFactory.getPlatformMBeanServer();
		if (mxServer.isRegistered(ManagementRegistrar.RDFIZER_OBJECT_NAME)) {
			LOGGER.error(MessageCatalog._00043_MX_RESOURCE_ALREADY_REGISTERED, ManagementRegistrar.RDFIZER_OBJECT_NAME);
		} else {
			try {
				mxServer.registerMBean(this, ManagementRegistrar.RDFIZER_OBJECT_NAME);
				LOGGER.info(MessageCatalog._00044_MX_RESOURCE_REGISTERED, ManagementRegistrar.RDFIZER_OBJECT_NAME);				
			} catch (JMException exception) {
				LOGGER.error(MessageCatalog._00042_MX_SUBSYSTEM_FAILURE, exception);
			}
		}
	}
	
	/**
	 * Shutdown procedure for this resource.
	 */
	@PreDestroy
	public void destroy() {
		final MBeanServer mxServer = ManagementFactory.getPlatformMBeanServer();
		try {
			mxServer.unregisterMBean(ManagementRegistrar.RDFIZER_OBJECT_NAME);
		} catch (final Exception exception) {
			LOGGER.error(MessageCatalog._00042_MX_SUBSYSTEM_FAILURE, exception);
		}
	}

	@Override
	public int getRunningJobsCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getCompletedJobsCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getProcessedRecordsCount() {
		// TODO Auto-generated method stub
		return 0;
	}
}