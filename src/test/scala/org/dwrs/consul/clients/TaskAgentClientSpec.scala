package org.dwrs.consul.clients

import com.typesafe.scalalogging.LazyLogging
import org.dwrs.consul.requests.AgentModel._
import org.scalatest._
import monix.execution.Scheduler.Implicits.global
import org.dwrs.consul.requests
import org.dwrs.consul.requests.ConsulResponses
import org.dwrs.consul.requests.ConsulResponses.Service
import org.scalatest.words.ResultOfAfterWordApplication

import scala.concurrent.{Await, Future}
import scala.util.Failure
import scala.concurrent.duration._

class TaskAgentClientSpec extends AsyncWordSpec
  with Matchers with EitherValues with OptionValues with Inside with BeforeAndAfterAll
  with LazyLogging {
  private val agentClient = TaskAgentClient()

  private val testService = "testService"
  private val testServiceId = testService + ":1"
  private val testServiceTtlCheckId = s"$testService:1:ttlCheck"
  private val ttlCheck = TtlCheckRequest(ttl = Some("2s"), status = Some("passing"), id = Some(testServiceTtlCheckId))

  private val maintenanceModeService = "maintenanceModeTest"
  private val maintenanceModeServiceId = maintenanceModeService + ":1"

  override def beforeAll() {
    import scala.concurrent.duration._
    // Registering synchronously for testing. In a real service, you likely shouldn't do this.
    logger.trace("Fixture registered")
    agentClient.registerService(testService, 9000, None, Some(testServiceId), Some(ttlCheck :: Nil)).runSyncUnsafe(30.seconds)
    agentClient.registerService(maintenanceModeService, 8002, None, Some(maintenanceModeServiceId), Some(ttlCheck :: Nil)).runSyncUnsafe(30.seconds)

  }

  override def afterAll() {
    import scala.concurrent.duration._
    agentClient.deRegisterService(testServiceId).runSyncUnsafe(30.seconds)
    agentClient.deRegisterService(maintenanceModeServiceId).runSyncUnsafe(30.seconds)
    logger.trace("Fixture de-registered")
  }

  //  override def withFixture(test: NoArgAsyncTest): FutureOutcome = {
  //    import scala.concurrent.duration._
  //    complete {
  //      // Registering synchronously for testing. In a real service, you likely shouldn't do this.
  //      logger.trace("Fixture registered")
  //      agentClient.registerService(testService, 9000, None, Some(testServiceId), Some(ttlCheck :: Nil)).runSyncUnsafe(30.seconds)
  //      super.withFixture(test)
  //    } lastly {
  //      agentClient.deRegisterService(testServiceId).runSyncUnsafe(30.seconds)
  //      logger.trace("Fixture de-registered")
  //    }
  //  }

  def withRegistration(name: String, port: Int, id: String, checks: Option[List[Check]] = None)(f: => Future[Assertion]): Future[Assertion] = {
    import scala.concurrent.duration._
    agentClient.registerService(name, port, None, Some(id), checks).runSyncUnsafe(30.seconds)
    f
  }

  def deRegisterAfter(serviceId: String)(f: => Future[Assertion]): Future[Assertion] = {
    import scala.concurrent.duration._
    // We don't reuse service ID, so this doesn't have to block.
    f.onComplete(_ => agentClient.deRegisterService(serviceId).runSyncUnsafe(30.seconds))
    f
  }

  def withRegistrationAndDeRegistration(name: String, port: Int, id: String, checks: Option[List[Check]] = None)(f: => Future[Assertion]): Future[Assertion] = {
    import scala.concurrent.duration._
    agentClient.registerService(name, port, None, Some(id), checks).runSyncUnsafe(30.seconds)
    f.onComplete(_ => agentClient.deRegisterService(id).runAsync)
    f
  }

  "A TaskAgentClient" should {
    "register and de-register a service" in {
      val initialTestService = "testServiceRegisterAndDeregister"
      val initialTestServiceId = initialTestService + ":1"
      val registerTask = agentClient.registerService(initialTestService, 9000, Some("localhost"), Some(initialTestServiceId)).memoizeOnSuccess
      val deRegisterTask = agentClient.deRegisterService(initialTestServiceId)

      (for {
        registration <- registerTask
        deRegistration <- deRegisterTask
        assertion = {
          registration.is200 should be(true)
          deRegistration.is200 should be(true)
        }
      } yield assertion).runAsync
    }
  }

  "A TaskAgentClient" when {
    "registering a service" that {
      "has a ttl health check, the check" should {
        "be visible and passing" in {
          val initialTestService = "testServiceTtlRegister"
          val initialTestServiceId = initialTestService + ":1"
          deRegisterAfter(initialTestServiceId) {
            (for {
              registration <- agentClient.registerService(initialTestService, 9000, None, Some(initialTestServiceId), Some(ttlCheck :: Nil))
              list <- agentClient.listChecks()
              assertions = {
                registration.is200 should be(true)
                list.body.right.value.keys should contain(s"service:$initialTestServiceId")
                list.body.right.value.get(s"service:$initialTestServiceId").value.status should be ("passing")
              }
            } yield assertions).runAsync
          }
        }

        "have the correct properties" should {
          val associationTestService = "testServiceTtlRegister"
          val associationTestServiceId = associationTestService + ":1"
          val associationServiceCheckId = s"service:$associationTestServiceId"
          agentClient.registerService(associationTestService, 9000, Some("localhost"), Some(associationTestServiceId), Some(ttlCheck :: Nil)).runSyncUnsafe(30.seconds)
          val checksList = agentClient.listChecks().runSyncUnsafe(30.seconds)
          val responseBody = checksList.body.right.value
          val serviceEntry = responseBody.get(associationServiceCheckId).value

          "have correct service name" in {
            serviceEntry.serviceName should be (associationTestService)
          }

          "have correct service ID" in {
            serviceEntry.serviceId should be (associationTestServiceId)
          }

        }

        val testServiceExpire = "testServiceTtlExpire"
        val testServiceExpireId = testServiceExpire + ":1"

        "expire" in {
          withRegistration(testServiceExpire, 9000, testServiceExpireId, Some(ttlCheck :: Nil)) {
            Thread.sleep(3000) // Wait to make sure check has expired
            (for {
              checksList <- agentClient.listChecks()
              checksBody = checksList.body.right.value
              assertions = {
                checksList.is200 should be(true)
                logger.info("Expire ttl")
                logger.info(s"Checks body: ${checksBody.keys}")
                logger.info(s"Searching for 'service:$testServiceExpireId'")
                inside(checksBody.get(s"service:$testServiceExpireId")) {
                  case Some(c) => c.status should be("critical")
                    c.output should be("TTL expired")
                  case None => fail(s"Check should be present. Checks body: ${checksBody.keys}")
                }
              }
              _ <- agentClient.deRegisterService(testServiceExpireId)
            } yield assertions).runAsync
          }
        }

        val testServicePass = "testServiceTtlPass"
        val testServiceIdPass = testServicePass + ":1"

        "be able to 'pass' ttl" in {
          withRegistration(testServicePass, 9000, testServiceIdPass, Some(ttlCheck :: Nil)) {
            Thread.sleep(3000) // Wait to make sure check has expired
            (for {
              checksList <- agentClient.listChecks()
              checksBody = checksList.body.right.value
              passTtl <- agentClient.passTtl(s"service:$testServiceIdPass", "Test pass")
              passChecksList <- agentClient.listChecks()
              passTtlChecksBody = passChecksList.body.right.value
              assertions = {
                checksList.is200 should be(true)
                logger.info(s"Checkslist code: ${checksList.code}")
                logger.info("Pass ttl")
                logger.info(s"Checks body: $checksBody")
                logger.info(s"Searching for 'service:$testServiceIdPass'")
                inside(checksBody.get(s"service:$testServiceIdPass")) {
                  case Some(c) => c.status should be("critical")
                    c.output should be("TTL expired")
                  case None => fail(s"Check should be present. Checks body: ${checksBody.keys}")
                }
                logger.info(s"Pass ttl code: ${passTtl.code}")
                logger.info(s"Pass ttl code: ${passTtl.body}")
                passTtl.is200 should be(true)
                inside(passTtlChecksBody.get(s"service:$testServiceIdPass")) {
                  case Some(c) => c.status should be("passing")
                  //                  c.output should be("TTL expired")
                  case None => fail("Check should be present")
                }
              }
              _ <- agentClient.deRegisterService(testServiceIdPass)
            } yield assertions).runAsync
          }
        }
      }
    }

    "de-registering a service" that {
      "has a ttl health check" should {
        "also de-register the check" in {
          val initialTestService = "testServiceTtlDeregister"
          val initialTestServiceId = initialTestService + ":1"
          deRegisterAfter(initialTestServiceId) {
            withRegistration(initialTestService, 9000, initialTestServiceId, Some(ttlCheck :: Nil)) {
              val deRegisterServiceTask = agentClient.deRegisterService(s"$initialTestServiceId")
              val listChecksTask = agentClient.listChecks()

              (for {
                registration <- deRegisterServiceTask
                list <- listChecksTask
                assertions = {
                  registration.is200 should be(true)
                  list.body.right.value.keys should not contain s"service:$initialTestServiceId"
                }
              } yield assertions).runAsync
            }
          }
        }
      }
    }
    "Services are registered" should {
      "be able to get a list of the services" in {
        agentClient.listServices()
          .map { response =>
            response.is200 should be(true)
            val responseBody = response.body.right.value
            responseBody.size should be(2)
            responseBody.keys should contain(testServiceId)
            responseBody.values should contain(ConsulResponses.Service(testServiceId,
              testService, Nil, "", 9000, None, enableTagOverride = false))
          }.runAsync
      }
      "be able to enable maintenance mode" in {
        agentClient.enableMaintenanceMode(testServiceId, None)
          .map { response =>
            response.is200 should be(true)
          }.runAsync
      }

      "be able to disable maintenance mode" in {
        agentClient.disableMaintenanceMode(testServiceId, None)
          .map { response =>
            response.is200 should be(true)
          }.runAsync
      }
    }
    "Maintenance mode is enabled for a service" should {
      "show a critical check" in {
        (for {
          enableMaintMode <- agentClient.enableMaintenanceMode(maintenanceModeServiceId)
          checksList <- agentClient.listChecks()
          checksMap = checksList.body.right.value
          assertions = {
            enableMaintMode.is200 should be(true)
            checksMap.keys should contain(s"_service_maintenance:$maintenanceModeServiceId")
            val maintenanceCheck = checksMap.get(s"_service_maintenance:$maintenanceModeServiceId")
            inside(maintenanceCheck) {
              case Some(c) => c.status should be("critical")
              case None => fail("Maintenance check should be present")
            }

          }
        } yield assertions).runAsync
      }

      "be able to re-enable the service" in {
        (for {
          maintMode <- agentClient.disableMaintenanceMode(maintenanceModeServiceId)
          checksList <- agentClient.listChecks()
          checksMap = checksList.body.right.value
          assertions = {
            maintMode.is200 should be(true)
            checksList.is200 should be(true)
            checksMap.keys should not contain s"_service_maintenance:$maintenanceModeServiceId"
          }
        } yield assertions).runAsync
      }

    }

    "Service is registered with a ttl check" should {
      "find the service in the list of checks" in {
        val listChecksTask = agentClient.listChecks()

        listChecksTask.map { response =>
          response.is200 should be(true)
          response.body.right.value.keys should contain(s"service:$testServiceId")
        }.runAsync
      }
    }
  }
}
