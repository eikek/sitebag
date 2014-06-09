import sbt._
import sbt.classpath.ClasspathUtilities

object Distribution extends Plugin {

  object DistributionKeys {
    val dist = TaskKey[File]("dist", "Creates a zip file with sitebag in it.")
  }

  private val libraries = Keys.dependencyClasspath.in(Runtime)
  private val distJar = Keys.packageBin.in(Compile)

  val distSettings = super.projectSettings ++ Seq(
    DistributionKeys.dist <<=
      (libraries, distJar, Keys.target, Keys.sourceDirectory, Keys.version, Keys.streams) map {
        (libs, distr, target, srcdir, version, out) =>
          val log = out.log
          val (files, dirs) = libs.map(_.data).toVector.partition(ClasspathUtilities.isArchive)
          val outdir = target / s"sitebag-$version"
          val zipped = target / s"sitebag-$version.zip"
          IO.delete(outdir)
          val libdir = outdir / "lib"
          files.withFilter(noLog4jCore).foreach(f => { log.info("Add library "+ f.getName);  IO.copyFile(f, libdir / f.getName)})

          IO.createDirectories(Seq(libdir, outdir / "bin", outdir / "etc"))
          IO.copyFile(distr, outdir / "lib" / "sitebag.jar")
          IO.listFiles(srcdir / "main" / "dist" / "bin").map(f => IO.copyFile(f, outdir / "bin" / f.getName))
          IO.listFiles(srcdir / "main" / "dist" / "etc").map(f => IO.copyFile(f, outdir / "etc" / f.getName))

          log.info("Create zip file ...")
          IO.zip(entries(outdir).map(d => (d, d.getAbsolutePath.substring(outdir.getParent.length +1))), zipped)
          zipped
      }
  )

  def noLog4jCore(f: File): Boolean = ! (f.getName contains "log4j-core")

  def entries(f: File) :List[File] =
    f :: (if (f.isDirectory) IO.listFiles(f).toList.flatMap(entries) else Nil)
}
