package org.eknet.sitebag.lucene

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, WordSpecLike}
import org.eknet.sitebag.{ActorTestBase, IndexTest, Success, commons}
import java.util.concurrent.atomic.AtomicReference
import java.nio.file.{Paths, Path}
import java.io.File
import org.eknet.sitebag.model.Page

class IndexReaderSpec extends ActorTestBase("IndexReaderSpec") with IndexTest {

  import commons.persons._

  "IndexReaderActor" should {
    "Find results for queries" in {
      val index = newIndexDir
      val (john, johnathan) = (Person("John", 42), Person("Quinten Mac John", 33))
      commons.addToIndex(index, john, johnathan)

      val ref = system.actorOf(IndexReaderActor(index))
      ref ! QueryDirectory.create[Person](QueryMaker.fromTerm(john), Page.one)
      expectMsg(Success(Vector(john)))
      ref ! QueryDirectory.create[Person](QueryMaker.fromTerm(johnathan), Page.one)
      expectMsg(Success(Vector(johnathan)))

      ref ! QueryDirectory.create[Person](QueryMaker.fromString("*", "name"), Page.one)
      expectMsg(Success(Vector(john, johnathan)))
    }
  }
}
