package org.eknet.sitebag.lucene

import org.eknet.sitebag.{ActorTestBase, IndexTest, Success, commons}
import org.eknet.sitebag.model.Page

class IndexActorSpec extends ActorTestBase("IndexActorSpec") with IndexTest {

  import commons.persons._

  "IndexActor" should {
    "update documents" in {
      val index = newIndexDir
      val john = Person("John", 42, "As well as ballads, the legend was also transmitted by \"Robin Hood games\" or plays that were an important part of the late")
      val ref = system.actorOf(IndexActor(index))

      ref ! Mod(_.updateDocument(john), "John added")
      expectMsg(Success("John added"))

      for (i <- 1 to 5) {
        ref ! FindByTerm.create[Person, Person](john)
        expectMsg(Success(Some(Vector(john.copy(vita = "")))))
      }
      ref ! QueryDirectory.create[Person](QueryMaker.fromString("legend", "vita"), Page.one)
      expectMsg(Success(Some(Vector(john.copy(vita = "")))))

      ref ! Update.create(john, doc => {
        doc.removeFields("age")
        doc += 21.asField("age").stored.indexed.notTokenized
      })
      expectMsg(Success("Modification done."))
      ref ! QueryDirectory.create[Person](QueryMaker.fromString("*", "name"), Page.one)
//      ref ! FindByTerm.create[Person, Person](john)  TODO why is this query failing???
      expectMsg(Success(Some(Vector(john.copy(age = 21, vita = "")))))

      ref ! QueryDirectory.create[Person](QueryMaker.fromString("legend", "vita"), Page.one)
      expectMsg(Success(Some(Vector()))) //does not work if there are not-stored fields!!
    }
  }

}
