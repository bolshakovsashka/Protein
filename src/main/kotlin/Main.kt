import protein.common.StorageUtils.getFoldersList
import protein.kotlinbuilders.KotlinApiBuilder
import protein.kotlinbuilders.ProteinApiConfiguration
import protein.tracking.ErrorTracking

fun main(args: Array<String>) {
  println("Api generation started")
  val kotlinApiBuilder = KotlinApiBuilder(ProteinApiConfiguration(
    serviceEndpoint = "",
    swaggerUrl = args[0],
    componentName = args[1],
    moduleName = args[2],
    packageName = args[3],
    swaggerFile = "",
    additionalConfig = "",
    customPath = args[4]), object : ErrorTracking {
    override fun logException(throwable: Throwable) {
      println("Api generation failed!")
      println(throwable.message)
      throwable.printStackTrace()
    }
  })
  kotlinApiBuilder.build()
  kotlinApiBuilder.generateFiles()
  println("Api generation finished")
}