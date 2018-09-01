package org.dwrs.consul.requests

import com.softwaremill.sttp._
import com.softwaremill.sttp.json4s._
import netscape.javascript.JSObject
import org.dwrs.consul.requests.ConsulResponses.{CheckResponse, Service}
import org.json4s.FieldSerializer.{renameFrom, renameTo}
import org.json4s.{DefaultFormats, FieldSerializer, Formats}

import scala.collection._
import scala.concurrent.duration.FiniteDuration

class AgentRequests(agentUri: String, agentPort: Int) {
  import AgentModel._
  private val baseUri = uri"$agentUri"
  private val port = agentPort
  private val combinedBase: Uri = baseUri.port(port)
  private val agentPath = List("v1", "agent")
  private val agentCheckPath = agentPath ::: "check" :: Nil
  private val agentServicePath = agentPath ::: "service" :: Nil

  /**
    * Creates a request to register a service
    * @param requestBody the ServiceRegistration for this request
    */
  def registerServiceRequest(requestBody: ServiceRegistration): RequestT[Id, Unit, Nothing] = {
    sttp
      .put(combinedBase.path(agentServicePath ::: "register" :: Nil))
      .body(requestBody)
      .response(ignore)
  }

  /**
    * Creates a request to de-register a service
    * @param serviceId ID of the service to de-register
    */
  def deRegisterServiceRequest(serviceId: String): Request[Unit, Nothing] =
    sttp.put(combinedBase.path(agentServicePath ::: "deregister" :: serviceId :: Nil)).response(ignore)

  /**
    * Create a request to list registered services
    */
  def listServicesRequest: RequestT[Id, Map[String, ConsulResponses.Service], Nothing] = {
    import org.json4s._
    import org.json4s.FieldSerializer._
    //    val modifyIndexSerialiser = FieldSerializer[ConsulResponses.Service](
    //      renameTo("ModifyIndex", "modifyIndex"), renameFrom("modifyIndex", "ModifyIndex"))

    implicit val formats: Formats = DefaultFormats + FieldSerializer[ConsulResponses.Service](
      renameTo("id", "ID") orElse renameTo("service", "Service")
        orElse renameTo("tags", "Tags") orElse renameTo("address", "Address")
        orElse renameTo("meta", "Meta") orElse renameTo("port", "Port")
        orElse renameTo("enableTagOverride", "EnableTagOverride"),
      renameFrom("ID", "id") orElse renameFrom("Service", "service")
        orElse renameFrom("Tags", "tags") orElse renameFrom("Address", "address")
        orElse renameFrom("meta", "Meta") orElse renameFrom("Port", "port")
        orElse renameFrom("EnableTagOverride", "enableTagOverride"))

    sttp.get(combinedBase.path(agentPath ++ List("services")))
      .response[Map[String, ConsulResponses.Service], Nothing](asJson[Map[String, ConsulResponses.Service]])
  }

  /**
    *

    * @return
    */
  def registerTtlCheckRequest(check: CheckRequest): RequestT[Id, Check, Nothing] = {
    sttp.put(combinedBase.path(agentCheckPath ::: "register" :: Nil))
      .body(check)
      .response(asJson[Check])
  }

  def registerHttpCheckRequest(check: CheckRequest): RequestT[Id, Check, Nothing] = {
    sttp.put(combinedBase.path(agentCheckPath ::: "register" :: Nil))
      .body(check)
      .response(asJson[Check])
  }

  /**
    *
    * @param checkId The ID of the check to deregister.
    */
  def deRegisterCheckRequest(checkId: String): Request[String, Nothing] =
    sttp.put(combinedBase.path(agentCheckPath ::: "deregister" :: checkId :: Nil))

  /**
    * Creates a request to list health checks
    */
  def listChecksRequest: RequestT[Id, Map[String, CheckResponse], Nothing] = {
    implicit val formats: Formats = DefaultFormats + FieldSerializer[ConsulResponses.CheckResponse](
      renameTo("node", "Node") orElse renameTo("checkId", "CheckID")
        orElse renameTo("name", "Name") orElse renameTo("status", "Status")
        orElse renameTo("notes", "Notes") orElse renameTo("output", "Output")
        orElse renameTo("serviceId", "ServiceID") orElse renameTo("serviceName", "ServiceName")
        orElse renameTo("serviceTags", "ServiceTags"),
      renameFrom("Node", "node") orElse renameFrom("CheckID", "checkId")
        orElse renameFrom("Name", "name") orElse renameFrom("Status", "status")
        orElse renameFrom("Notes", "notes") orElse renameFrom("Output", "output")
        orElse renameFrom("ServiceID", "serviceId") orElse renameFrom("ServiceName", "serviceName")
        orElse renameFrom("ServiceTags", "serviceTags"))
    sttp.get(combinedBase.path(agentPath ::: "checks" :: Nil))
      .response[Map[String, CheckResponse], Nothing](asJson[Map[String, CheckResponse]])
  }

  private def putTtl(ttlType: String, checkId: String, note: String = "") = {
    val uri = combinedBase.path(agentCheckPath ::: ttlType :: checkId :: Nil)
    if (note.isEmpty) sttp.put(uri)
    else sttp.put(uri.param("note", note))
  }

  /**
    * Sets the check to a "passing" state.
    * @param checkId The ID of the check to update
    * @param note A note to be included in the checks "output" property
    */
  def passTtlRequest(checkId: String, note: String = ""): Request[String, Nothing] =
    putTtl("pass", checkId, note)


  /**
    * Sets the check to a "warning" state.
    * @param checkId The ID of the check to update
    * @param note A note to be included in the checks "output" property
    */
  def warnTtlRequest(checkId: String, note: String = ""): Request[String, Nothing] =
    putTtl("warn", checkId, note)

  /**
    * Sets the check to a "critical" state
    * @param checkId The ID of the check to update
    * @param note A note to be included in the checks "output" property
    */
  def failTtlRequest(checkId: String, note: String = ""): Request[String, Nothing] =
    putTtl("fail", checkId, note)

  /**
    * Updates the ttl with the provided status
    * @param checkId The ID of the check to update
    * @param status The status to which the check should be updated (valid statuses per the v1 api are "passing", "warning" or "critical").
    * @param output The output that the check should display
    */
  def updateTtlRequest(checkId: String, status: String = "", output: String = ""): Request[String, Nothing] =
    putTtl("update", checkId)
      .body(CheckUpdate(status, output))

  /**
    *
    * @param serviceId The serviceID on which to modify maintenance omde
    * @param enabled whether maintenance mode is enabled or disabled
    * @param reasonText the reason for maintenance mode
    */
  def maintenanceModeRequest(serviceId: String, enabled: Boolean, reasonText: Option[String] = None): Request[Unit, Nothing] = {
    // Add params; only append a reason if it is non-empty
    val params = Seq("enable" -> enabled.toString) ++
      (reasonText match {
        case Some(reason) if reason.nonEmpty => Seq("reason" -> reason)
        case None => Nil
      })
    sttp.put(combinedBase.path(agentServicePath ::: "maintenance" :: serviceId :: Nil).params(params.toMap)).response(ignore)
  }
}

object AgentRequests{
  def apply(agentUri: String, agentPort: Int): AgentRequests = new AgentRequests(agentUri, agentPort)
}

object ConsulResponses{
  case class Service(id: String, service: String, tags: List[String],
                     address: String, port: Int, meta:Option[Map[String, String]],
                     enableTagOverride: Boolean)
  case class CheckResponse(node: String, checkId: String, name: String, status: String, notes: String,
                           output: String, serviceId: String, serviceName: String, serviceTags: List[String])
}

//object Method extends Enumeration {
//  final val GET = Value("get")
//  final val POST = Value("post")
//  final val PUT = Value("put")
//  final val DELETE = Value("delete")
//  final val PATCH = Value("patch")
//}