package org.dwrs.consul.clients

import com.softwaremill.sttp.Method
import netscape.javascript.JSObject
import org.dwrs.consul.requests.ConsulResponses.Service
import org.dwrs.consul.requests.{AgentRequests, ConsulResponses}

import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds

trait AgentClient {
  import org.dwrs.consul.requests.AgentModel._
  type ResponseType[_]

  def registerService(serviceName: String, port: Int, address: Option[String],
                      serviceId: Option[String] = None, checks: Option[List[Check]]): ResponseType[Unit]

  def deRegisterService(serviceId: String): ResponseType[Unit]

  def listChecks(): ResponseType[collection.Map[String, ConsulResponses.CheckResponse]]

  def registerTtlCheck(checkName: String,
                       ttlInterval: FiniteDuration,
                       deregisterCriticalAfter: FiniteDuration,
                       checkNotes: Option[String],
                       associatedServiceId: Option[String] = None,
                       initialStatus: Option[String] = None,
                       checkId: Option[String] = None): ResponseType[Check]

  def registerHttpCheck(checkName: String,
                        interval: FiniteDuration,
                        healthUrl: String,
                        httpMethod: Option[Method],
                        deregisterCriticalAfter: FiniteDuration,
                        checkNotes: Option[String],
                        associatedServiceId: Option[String] = None,
                        initialStatus: Option[String] = None,
                        checkId: Option[String] = None): ResponseType[Check]

  def deRegisterCheck(id: String): ResponseType[String]

  def passTtl(id: String, note: String): ResponseType[String]

  def warnTtl(id: String, note: String): ResponseType[String]

  def failTtl(id: String, note: String): ResponseType[String]

  def updateTtl(id: String, status: String, output: String): ResponseType[String]

  type ServicesMap = collection.Map[String, Service]

  def listServices(): ResponseType[ServicesMap]

}
