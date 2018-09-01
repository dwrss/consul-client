package org.dwrs.consul.clients

import java.nio.ByteBuffer

import com.softwaremill.sttp.asynchttpclient.monix.AsyncHttpClientMonixBackend
import com.softwaremill.sttp.{Response, SttpBackend}
import monix.eval.Task
import monix.execution.CancelableFuture
import monix.reactive.Observable
import monix.execution.Scheduler.Implicits.global
import org.dwrs.consul.requests.{AgentRequests, ConsulResponses}

abstract class FutureAgentClient(uri: String = "localhost", port: Int = 8500) extends AgentClient {
  import org.dwrs.consul.requests.AgentModel._
  import org.dwrs.consul.requests.AgentRequests
  import org.dwrs.consul.requests.ConsulResponses
  override type ResponseType[R] = CancelableFuture[Response[R]]
  implicit val sttpBackend: SttpBackend[Task, Observable[ByteBuffer]] = AsyncHttpClientMonixBackend()

  val agentRequest = AgentRequests(agentUri = uri, agentPort = port)

//  def listServices(): ResponseType[collection.Map[String, ConsulResponses.Service]] = ???

  def registerService(serviceName: String, port: Int, address: Option[String],
                      serviceId: Option[String] = None, checks: Option[List[Check]]): ResponseType[Unit] = {
    val requestBody = ServiceRegistration(name = serviceName, id = serviceId,
      port = port, checks = checks)
    agentRequest.registerServiceRequest(requestBody).send().runAsync
  }

  def deRegisterService(serviceId: String): ResponseType[Unit] = agentRequest.deRegisterServiceRequest(serviceId).send().runAsync
}
