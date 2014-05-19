package org.eknet.sitebag

import scala.concurrent.duration._
import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, WordSpecLike}

abstract class ActorTestBase(val testName: String) extends TestKit(ActorSystem(testName, ConfigFactory.load("reference")))
  with WordSpecLike with BeforeAndAfterAll with BeforeAndAfter with ImplicitSender {

  override protected def afterAll() = {
    system.shutdown()
    awaitCond(system.isTerminated, 5.seconds)
    super.afterAll()
  }
}
