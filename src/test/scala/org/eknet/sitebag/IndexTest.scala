package org.eknet.sitebag

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, WordSpecLike}
import java.util.concurrent.atomic.AtomicReference
import java.nio.file.{Paths, Path}
import java.io.File

trait IndexTest extends ActorTestBase {

  private val indexDirs = new AtomicReference[List[Path]](Nil)

  override def afterAll() = {
    for (dir <- indexDirs.get()) {
      commons.deleteDirectory(dir)
    }
    super.afterAll()
  }

  def newIndexDir: File = {
    val dirs = indexDirs.get()
    val next = Paths.get("target", commons.randomWord)
    if (! indexDirs.compareAndSet(dirs, next :: dirs)) {
      newIndexDir
    } else {
      next.toFile
    }
  }

}
