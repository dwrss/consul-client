package org.dwrs.consul.clients

import java.nio.ByteBuffer

import com.softwaremill.sttp._
import com.softwaremill.sttp.asynchttpclient.monix.AsyncHttpClientMonixBackend
import com.typesafe.scalalogging.LazyLogging
import monix.eval.Task
import monix.reactive.Observable
import netscape.javascript.JSObject

import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration

case class TaskAgentClient(uri: String = "localhost", port: Int = 8500) extends AgentClient with LazyLogging {
  import org.dwrs.consul.requests.AgentModel._
  import org.dwrs.consul.requests.AgentRequests
  import org.dwrs.consul.requests.ConsulResponses
  override type ResponseType[R] = Task[Response[R]]
  implicit val sttpBackend: SttpBackend[Task, Observable[ByteBuffer]] = AsyncHttpClientMonixBackend()

  val agentRequest = AgentRequests(agentUri = uri, agentPort = port)

  /**
    * Registers a service with Consul.
    * Checks passed to this method will be associated with the service.
    * @param serviceName name of the service
    * @param port        port on which the service can be found
    * @param address     address at which the service can be found
    * @param serviceId   ID of the service with (Consul will default to the service name if not specified)
    * @param checks      health checks associated with the service
    * @return Unit
    */
  def registerService(serviceName: String, port: Int, address: Option[String],
                      serviceId: Option[String] = None,
                      checks: Option[List[Check]] = None): ResponseType[Unit] = {
    val requestBody = ServiceRegistration(name = serviceName, id = serviceId,
      address = address, port = port, checks = checks)
    val request = agentRequest.registerServiceRequest(requestBody)
    logger.trace(s"Registered: $serviceName")
    request
      .send()
  }

  def deRegisterService(serviceId: String): ResponseType[Unit] = {
    val request = agentRequest.deRegisterServiceRequest(serviceId)
    logger.trace(s"De-Registered: $serviceId")
    request
      .send()
  }

  def listServices(): ResponseType[ServicesMap] = {
    val request = agentRequest.listServicesRequest
    logger.trace(s"Sending request to: ${request.uri}")
    request
      .send()
  }

  def registerTtlCheck(checkName: String,
                       ttlInterval: FiniteDuration,
                       deregisterCriticalAfter: FiniteDuration,
                       checkNotes: Option[String],
                       associatedServiceId: Option[String] = None,
                       initialStatus: Option[String] = None,
                       checkId: Option[String] = None): ResponseType[Check] = {
    val requestBody = CheckRequest(
      name = Some(checkName),
      id = checkId,
      ttl = Some(ttlInterval.toString),
      deregisterCriticalServiceAfter = Some(deregisterCriticalAfter.toString),
      notes = checkNotes,
      serviceId = associatedServiceId,
      status = initialStatus)
    val request = agentRequest.registerTtlCheckRequest(requestBody)
    logger.trace(s"Sending request to: ${request.uri}")
    request
      .send()
  }

  def registerHttpCheck(checkName: String,
                        interval: FiniteDuration,
                        healthUrl: String,
                        httpMethod: Option[Method],
                        deregisterCriticalAfter: FiniteDuration,
                        checkNotes: Option[String],
                        associatedServiceId: Option[String] = None,
                        initialStatus: Option[String] = None,
                        checkId: Option[String] = None): ResponseType[Check] = {
    val requestBody = CheckRequest(
      name = Some(checkName),
      id = checkId,
      http = Some(healthUrl),
      method = httpMethod.map(_.m), // Get the name of the method enum
      interval = Some(interval.toString),
      deregisterCriticalServiceAfter = Some(deregisterCriticalAfter.toString),
      notes = checkNotes,
      serviceId = associatedServiceId,
      status = initialStatus)
    val request = agentRequest.registerHttpCheckRequest(requestBody)
    logger.trace(s"Sending request to: ${request.uri}")
    request
      .send()
  }

  def deRegisterCheck(id: String): ResponseType[String] = {
    val request = agentRequest.deRegisterCheckRequest(id)
    logger.trace(s"Sending request to: ${request.uri}")
    request
      .send()
  }

  def listChecks(): ResponseType[collection.Map[String, ConsulResponses.CheckResponse]] = {
    val request = agentRequest.listChecksRequest
    logger.trace(s"Sending request to: ${request.uri}")
    request
      .send()
  }

  def passTtl(id: String, note: String = ""): ResponseType[String] = {
    val request = agentRequest.passTtlRequest(id, note)
    logger.trace(s"Sending request to: ${request.uri}")
    request
      .send()
  }

   def warnTtl(id: String, note: String = ""): ResponseType[String] = {
    val request = agentRequest.warnTtlRequest(id, note)
    logger.trace(s"Sending request to: ${request.uri}")
    request
      .send()
  }

  def failTtl(id: String, note: String = ""): ResponseType[String] = {
    val request = agentRequest.failTtlRequest(id, note)
    logger.trace(s"Sending request to: ${request.uri}")
    request
      .send()
  }

  def updateTtl(id: String, status: String, output: String = ""): ResponseType[String] = {
    val request = agentRequest.updateTtlRequest(id, status, output)
    logger.trace(s"Sending request to: ${request.uri}")
    request
      .send()
  }

  def enableMaintenanceMode(serviceId: String, reason: Option[String] = None): ResponseType[Unit] = {
    val request = agentRequest.maintenanceModeRequest(serviceId, enabled = true, reasonText = reason)
    logger.trace(s"Sending request to: ${request.uri}")
    request
      .send()
//      .map(Right(_))
  }

  def disableMaintenanceMode(serviceId: String, reason: Option[String] = None): ResponseType[Unit] = {
    val request = agentRequest.maintenanceModeRequest(serviceId, enabled = false, reasonText = reason)
    logger.trace(s"Sending request to: ${request.uri}")
    request
      .send()
//      .map(Right(_))
  }
}
