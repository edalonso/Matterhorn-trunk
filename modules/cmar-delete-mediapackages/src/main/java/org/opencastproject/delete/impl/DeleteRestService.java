/**
 *  Copyright 2009, 2010 The Regents of the University of California
 *  Licensed under the Educational Community License, Version 2.0
 *  (the "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at
 *
 *  http://www.osedu.org/licenses/ECL-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an "AS IS"
 *  BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 *  or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 */
package org.opencastproject.delete.impl;

import org.opencastproject.util.doc.rest.RestParameter;
import org.opencastproject.util.doc.rest.RestParameter.Type;
import org.opencastproject.util.doc.rest.RestQuery;
import org.opencastproject.util.doc.rest.RestResponse;
import org.opencastproject.util.doc.rest.RestService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import org.apache.http.client.methods.HttpRequestBase;
import javax.ws.rs.GET;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.WebApplicationException;

//Añadidas por Edu
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.ServiceRegistration;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.security.api.TrustedHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.JobParser;
import org.apache.http.client.methods.HttpDelete;
//import org.apache.http.client.methods.HttpGet;
import javax.ws.rs.FormParam;
//import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import org.apache.http.StatusLine;
import org.opencastproject.util.UrlSupport;
import org.apache.commons.lang.StringUtils;
import java.net.URI;
//import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.workflow.api.WorkflowService;
import org.opencastproject.workflow.api.WorkflowException;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowQuery;
import org.opencastproject.workflow.api.WorkflowSet;
import org.opencastproject.workspace.api.Workspace;
import java.io.File;
import org.apache.commons.io.FileUtils;

/**
 * The REST endpoint for the delete service.
 */
@Path("/")
@RestService(name = "delete", title = "Delete Service", notes = { "" }, abstractText = "This service is used for managing user generated deletes.")
public class DeleteRestService {

  /** The logger */
  private static final Logger logger = LoggerFactory.getLogger(DeleteRestService.class);

  /** The http client to use when connecting to remote servers */
  protected TrustedHttpClient client = null;

  /** the http client */
  protected ServiceRegistry remoteServiceManager = null;

  /** the workflowService */
  protected WorkflowService workflowService = null;

  /** the Workspace */
  protected Workspace workspace = null;

  /** Trash Dir */
  private File trashDir = null;

  /**
   * Sets the trusted http client
   * 
   * @param client
   */
  public void setTrustedHttpClient(TrustedHttpClient client) {
    this.client = client;
  }

  /**
   * Sets the remote service manager.
   * 
   * @param remoteServiceManager
   */
  public void setRemoteServiceManager(ServiceRegistry remoteServiceManager) {
    this.remoteServiceManager = remoteServiceManager;
  }

  /**
   * Sets the remote service manager.
   * 
   * @param remoteServiceManager
   */
  public void setWorkflowService(WorkflowService workflowService) {
    this.workflowService = workflowService;
  }

  /**
   * Sets the remote service manager.
   * 
   * @param remoteServiceManager
   */
  public void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  /**
   * Closes any http connections kept open by this http response.
   */
  protected void closeConnection(HttpResponse response) {
    client.close(response);
  }

  /**
   * The method that is called, when the service is activated
   * 
   * @param cc
   *          The ComponentContext of this service
   */
  public void activate(ComponentContext cc) {
    logger.info("Service Activate");
    this.distributionChannel = (String) cc.getProperties().get(REMOTE_SERVICE_CHANNEL);
    String storageDir = cc.getBundleContext().getProperty("org.opencastproject.storage.dir");
    if (storageDir != null) {
	trashDir = new File(storageDir + File.separator + "trash");
    } else {
	throw new RuntimeException("Couldn't create Trash Directory"); 
    }
  }

  /** The property to look up and append to REMOTE_SERVICE_TYPE_PREFIX */
  public static final String REMOTE_SERVICE_CHANNEL = "distribution.channel";

  /** The distribution channel identifier */
  protected String distributionChannel;

  /**
   * @return XML with all footprints
   */
  @GET
  @Path("test.json")
  @Produces(MediaType.APPLICATION_JSON)
  @RestQuery(name = "test", description = "test", reponses = { @RestResponse(description = "Returns test", responseCode = HttpServletResponse.SC_OK) }, returnDescription = "")
  public Response test() {
    logger.info("test");
    return Response.ok("ok").build();
  }


  /**
   * @return XML with all footprints
   */
  @DELETE
  @Path("deleteRecordings.json")
  @Produces(MediaType.APPLICATION_JSON)
  @RestQuery(name = "deleteRecordings", description = "Delete Recordings from state finished or on hold and save data in the directory /opt/matterhorn/storage/trash", restParameters = {
		 @RestParameter(name = "mpId", description = "The mediapackage definition identifier", isRequired = true, type = Type.STRING),
		 @RestParameter(name = "wId", description = "The workflow instance identifier", isRequired = true, type = Type.STRING),
		 @RestParameter(name = "onhold", description = "The option to state onHold", isRequired = true, type = Type.BOOLEAN) },
	     reponses = { @RestResponse(description = "Returns deleteRecordings", responseCode = HttpServletResponse.SC_OK) }, returnDescription = "")
   public Response deleteRecordings(@FormParam("mpId") String mediaPackageId, @FormParam("wId") long workflowInstanceId, @FormParam("onhold") boolean onhold) throws NotFoundException {
      WorkflowQuery q = new WorkflowQuery();
      if (mediaPackageId == null) {
	  throw new IllegalArgumentException("Mediapackage ID must be specified");
      } else {
	  q.withMediaPackage(mediaPackageId);
      }

      //Codigo de retract en download DownloadDistributionServiceRemoteImpl
      List<BasicNameValuePair> paramsDownload = new ArrayList<BasicNameValuePair>();
      paramsDownload.add(new BasicNameValuePair("mediapackageId", mediaPackageId));
      HttpPost postDownload = new HttpPost("/retract");
      HttpResponse response = null;
      UrlEncodedFormEntity entity = null;
      try {
	  entity = new UrlEncodedFormEntity(paramsDownload);
      } catch (UnsupportedEncodingException e) {
	  throw new RuntimeException("Unable to retract mediapackage " + mediaPackageId + " for http post in download", e); 
      }
      postDownload.setEntity(entity);
      try {
	  response = getResponse(postDownload, "org.opencastproject.distribution.download");
	  Job receipt = null;
	  if (response != null) {
	      logger.info("retracted {} from {}", mediaPackageId, distributionChannel);
	      try {
		  receipt = JobParser.parseJob(response.getEntity().getContent());
	      } catch (Exception e) {
		  throw new RuntimeException("Unable to retract mediapackage '" + mediaPackageId + "' using a remote distribution service in download.", e); 
	      }
	  }
      } finally {
	  closeConnection(response);
      }

      //Codigo de retract en streaming StreamingDistributionServiceRemoteImpl
      //Este no se ejecuta si estamos onHold ya que no existe en la carpeta streams
      if (!onhold) {
	  HttpPost postStreaming = new HttpPost("/retract");
	  List<BasicNameValuePair> paramsStreaming = new ArrayList<BasicNameValuePair>();
	  paramsStreaming.add(new BasicNameValuePair("mediapackageId", mediaPackageId));
	  HttpResponse responseStreaming = null;
	  UrlEncodedFormEntity entityStreaming = null;
	  try {
	      entityStreaming = new UrlEncodedFormEntity(paramsStreaming);
	  } catch (UnsupportedEncodingException e) {
	      throw new RuntimeException("Unable to retract mediapackage " + mediaPackageId + " for http post in streaming", e); 
	  }
	  postStreaming.setEntity(entityStreaming);
	  try {
	      responseStreaming = getResponse(postStreaming, "org.opencastproject.distribution.streaming", HttpStatus.SC_NO_CONTENT);
	      Job receiptStreaming = null;
	      if (responseStreaming != null) {
		  logger.info("retracted {} from {}", mediaPackageId, distributionChannel);
		  try {
		      receiptStreaming = JobParser.parseJob(responseStreaming.getEntity().getContent());
		  } catch (Exception e) {
		      throw new RuntimeException("Unable to retract mediapackage '" + mediaPackageId + "' using a remote distribution service in streaming.", e); 
		  }
	      }
	  } finally {
	      closeConnection(response);
	  }
      }

      try {
	  String urlSuffix = null;
	  HttpPost postFile = null;
	  HttpResponse responseFile = null;
	  WorkflowSet set = workflowService.getWorkflowInstances(q);
	  //File mediapackageDirectorySon = null;
	  File mediapackageDirectory = null;
	  for (WorkflowInstance instance : set.getItems()) {
	      MediaPackage mediaPackage = instance.getMediaPackage();
	      boolean firstTime = true;
	      for (MediaPackageElement element : mediaPackage.elements()) {
		  if (element.getTags().length == 0) { //Is a file archive
		      if (firstTime) {
			  try {
			      File original = workspace.get(workspace.getURI(mediaPackageId, element.getIdentifier(), element.getElementDescription()));
			      if (original.isFile()) {
				  logger.info("Moving: {} to Trash Directory", mediaPackageId);
				  mediapackageDirectory = original.getParentFile().getParentFile();
				  try {
				      FileUtils.copyDirectoryToDirectory(original.getParentFile().getParentFile(), trashDir);
				      firstTime = false;
				  } catch (IOException e) {
				      firstTime = true;
				      logger.info("No se pudo copiar: {} mensaje de error: {}", original.getParentFile().getParentFile(), e.getMessage());
				  } catch (NullPointerException e) {
				      firstTime = true;
				      logger.info("No existe el directorio: {} mensaje de error: {}", original.getParentFile().getParentFile(), e.getMessage());
				  }
			      }
			  } catch (NotFoundException e) {
			      logger.info("Capturada excepción al intentar obtener ruta a: {} con error: {}", element.getIdentifier(), e.getMessage());
			  } catch (IOException e) {
			      logger.info("Capturada excepción al intentar obtener ruta a: {} con error: {}", element.getIdentifier(), e.getMessage());
			  }
			  try {
			      workspace.delete(mediaPackageId, element.getIdentifier());//In onHold state It doesn't delete files directory
			  } catch (NotFoundException e) {
			      logger.info("Capturada excepción al intentar borrar el mediapackage: {} con error: {}", element.getIdentifier(), e.getMessage());
			  } catch (NullPointerException e) {
			      logger.info("No existe el mediapackage: {} o su contenido, mensaje de error: {}", mediaPackageId, e.getMessage());
			  } catch (IOException e) {
			      logger.info("Capturada excepción al intentar borrar el mediapackage: {} con error: {}", element.getIdentifier(), e.getMessage());
			  }
		      }
		  }
	      }
	  }
	  if (onhold) { //Process for on Hold state, la diferentecia es que en la carpeta workspace hay una carpeta llamada track que no aparece en files
	      try {
		  File deleteFileDirectory = new File("/opt/matterhorn/storage/files/mediapackage/" + mediaPackageId);
		  FileUtils.forceDelete(deleteFileDirectory);//Delete mediapackage directory from files
		  logger.info("Delete File directory: {}", deleteFileDirectory);
	      } catch (IOException e) {
		  throw new RuntimeException(e);
	      }
	      try {
		  FileUtils.forceDelete(mediapackageDirectory);//No borra el contenido de la carpeta mediapackageId
		  logger.info("Delete Workspace directory: {}", mediapackageDirectory);
	      } catch (IOException e) {
		  try {
		      mediapackageDirectory.delete();
		  } catch (SecurityException security) {
		      logger.error("Imposible borrar: {} con mensaje de error {}", mediapackageDirectory, security.getMessage());
		  }
	      }
	  } else {
	      File deleteFileDirectory = new File("/opt/matterhorn/storage/files/mediapackage/" + mediaPackageId);
	      try {
		  FileUtils.forceDelete(deleteFileDirectory);//Delete mediapackage directory from files
		  logger.info("Delete File directory: {}", deleteFileDirectory);
	      } catch (IOException e) {
		  logger.error("=========NON borrou {}: {}", deleteFileDirectory, e.getMessage());
		  throw new RuntimeException(e);
	      }
	      try {
		  FileUtils.forceDelete(mediapackageDirectory);//Intentamos borrar de nuevo la carpeta del workspace
		  logger.info("Delete Workspace directory: {}", mediapackageDirectory);
	      } catch (NullPointerException e) {
		  logger.info("no existe el directorio: {} con error: {}", mediapackageDirectory, e.getMessage());
	      } catch (IOException e) {
		  logger.info("Capturada excepción al intentar  borrar: {} con error: {}", mediapackageDirectory, e.getMessage());
		  try {
		      mediapackageDirectory.delete();
		      logger.info("Delete Workspace directory: {}", mediapackageDirectory);
		  } catch (SecurityException security) {
		      logger.error("Imposible borrar: {} con mensaje de error {}", mediapackageDirectory, security.getMessage());
		  }
	      }
	      File deleteWorkspaceDirectory = new File("/opt/matterhorn/storage/workspace/mediapackage/" + mediaPackageId);
	      try {
		  FileUtils.forceDelete(deleteWorkspaceDirectory);//Delete mediapackage directory from files
		  logger.info("Delete File directory: {}", deleteWorkspaceDirectory);
	      } catch (IOException e) {
		  logger.error("=========NON borrou {}: {}", deleteWorkspaceDirectory, e.getMessage());
		  throw new RuntimeException(e);
	      }
	  }
      } catch (WorkflowException e) {
	  throw new WebApplicationException(e);
      }

      //DELETE SEARCH from INDEX only for Finish State
      if (!onhold) {
	  HttpDelete del = new HttpDelete(mediaPackageId);
	  HttpResponse responseDelete = null;
	  
	  try {
	      responseDelete = getResponse(del, "org.opencastproject.search", HttpStatus.SC_NO_CONTENT);
	      if (responseDelete == null) {
		  throw new RuntimeException("Unable to remove " + mediaPackageId + " from a remote search index"); 
	      }
	      int status = responseDelete.getStatusLine().getStatusCode();
	      if (status == HttpStatus.SC_NO_CONTENT) {
		  logger.info("Successfully deleted {} from the remote search index", mediaPackageId);
	      } else if (status == HttpStatus.SC_NOT_FOUND) {
		  logger.info("Mediapackage {} not found in remote search index", mediaPackageId);
	      } else {
		  throw new RuntimeException("Unable to remove " + mediaPackageId + " from a remote search index, http status=" + status); 
	      }
	  } finally {
	      closeConnection(responseDelete);
	  }
      }

      //Codigo de stop en WorkflowServiceRemoteImpl
      HttpPost postStop = new HttpPost("/stop");
      List<BasicNameValuePair> paramsStop = new ArrayList<BasicNameValuePair>();
      paramsStop.add(new BasicNameValuePair("id", Long.toString(workflowInstanceId)));
      try {
	  postStop.setEntity(new UrlEncodedFormEntity(paramsStop));
      } catch (UnsupportedEncodingException e) {
	  throw new RuntimeException("Unable to assemble a remote workflow service request in Stop", e); 
      }

      HttpResponse responseStop = getResponse(postStop, "org.opencastproject.workflow", SC_OK, SC_NOT_FOUND);
      if (responseStop == null) {
	  throw new RuntimeException("Unexpected HTTP response code in Stop");
      } else if (responseStop.getStatusLine().getStatusCode() == SC_NOT_FOUND) {
	  throw new NotFoundException("Workflow instance with id='" + workflowInstanceId + "' not found");
      } else {
	  logger.info("Workflow '{}' stopped", workflowInstanceId);
	  closeConnection(responseStop);
      }

      return Response.noContent().build();
  }


  /**
   * Makes a request to all available remote services and returns the response as soon as the first of them returns the
   * expected http status code.
   * 
   * @param httpRequest
   *          the http request. If the URI is specified, it should include only the path beyond the service endpoint.
   *          For example, a request intended for http://{host}/{service}/extra/path/info.xml should include the URI
   *          "/extra/path/info.xml".
   * @param expectedHttpStatus
   *          any expected status codes to include in the return.
   * @return the response object, or null if we can not connect to any services
   */
  protected HttpResponse getResponse(HttpRequestBase httpRequest, String serviceType, Integer... expectedHttpStatus) {
    List<ServiceRegistration> remoteServices = null;
    try {
      remoteServices = remoteServiceManager.getServiceRegistrationsByLoad(serviceType);
    } catch (ServiceRegistryException e) {
      logger.warn("Unable to obtain a list of remote services", e);
      return null;
    }

    if (remoteServices.size() == 0) {
      logger.warn("No services of type '{}' are currently available", serviceType);
      return null;
    }

    Map<String, String> hostErrors = new HashMap<String, String>();
    URI originalUri = httpRequest.getURI();
    String uriSuffix = null;
    if (originalUri != null && StringUtils.isNotBlank(originalUri.toString())) {
      uriSuffix = originalUri.toString();
    }

    for (ServiceRegistration remoteService : remoteServices) {
      HttpResponse response = null;
      try {
        String fullUrl = null;
        if (uriSuffix == null) {
          fullUrl = UrlSupport.concat(remoteService.getHost(), remoteService.getPath());
        } else {
          fullUrl = UrlSupport.concat(new String[] { remoteService.getHost(), remoteService.getPath(), uriSuffix });
        }
        URI uri = new URI(fullUrl);
        httpRequest.setURI(uri);
        response = client.execute(httpRequest);
        StatusLine status = response.getStatusLine();
        if (Arrays.asList(expectedHttpStatus).contains(status.getStatusCode())) {
          return response;
        } else {
          hostErrors.put(httpRequest.getMethod() + " " + uri.toString(), status.toString());
          closeConnection(response);
        }
      } catch (Exception e) {
        hostErrors.put(httpRequest.getMethod() + " " + remoteService + uriSuffix, e.getMessage());
        closeConnection(response);
      }
    }
    logger.warn(hostErrors.toString());
    return null;
  }

}
