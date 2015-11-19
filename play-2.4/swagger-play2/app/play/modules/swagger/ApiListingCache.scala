package play.modules.swagger

import io.swagger.config._
import io.swagger.models.Swagger
import play.api.Logger
import scala.collection.JavaConversions._

object ApiListingCache {
  var cache: Option[Swagger] = None

  def listing(docRoot: String): Option[Swagger] = {
    cache.orElse{
      Logger("swagger").debug("Loading API metadata")
      
      val scanner = ScannerFactory.getScanner()
      val classes = scanner.classes()
      classes.foreach{ clazz =>
        //Logger("swagger").debug("Controller: %s".format(clazz.getName))
      }
      val reader = new PlayReader(null)
      var swagger = reader.read(classes)
      
      scanner match {        
        case config: SwaggerConfig => {
          swagger = config.configure(swagger)
        }
        case config => {
          // no config, do nothing
        }
      }
      
      cache = Some(swagger)
      cache
    }    
  }
}