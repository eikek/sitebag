package org.eknet.sitebag.lucene

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, WordSpecLike}
import org.eknet.sitebag.{ActorTestBase, IndexTest, Success, commons}
import java.nio.file.{Path, Paths}
import org.apache.lucene.document.{IntField, Field, StringField, Document}
import org.apache.lucene.index.Term
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class IndexWriterSpec extends ActorTestBase("IndexWriterSpec") with IndexTest {

  import commons.persons._

  "The IndexWriter actor" should {
    "create index upon modification" in {
      val indexdir = newIndexDir
      val (john, mary) = (Person("John", 42), Person("Mary", 33))
      val ref = system.actorOf(IndexWriterActor(indexdir))
      ref ! Mod(idx => idx.updateDocument(john, mary))
      expectMsg(Success("Modification done."))
      val ps = commons.search[Person](indexdir, QueryMaker.fromTerm(john))
      assert(ps.toList === List(john))
    }
  }

}
