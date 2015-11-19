package play.modules.swagger

import io.swagger.annotations.Api
import io.swagger.config._
import play.api.Logger
import play.api.routing.Router
import play.modules.swagger.util.SwaggerContext
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import io.swagger.models.Info
import io.swagger.models.Swagger
import io.swagger.models.Scheme
import org.apache.commons.lang3.StringUtils
import io.swagger.models.Contact
import io.swagger.models.License

import play.modules.swagger.routes.Route
import play.modules.swagger.routes.{Route=>PlayRoute,Parameter => PlayParameter}

/**
 * Identifies Play Controllers annotated as Swagger API's.
 * Uses the Play Router to identify Controllers, and then tests each for the API annotation.
 */
class PlayApiScanner(router: Option[Router]) extends Scanner with SwaggerConfig {
  
  // TODO keep config elsewhere
    var schemes : Array[String] = _
    var title : String = _
    var version : String = _
    var description : String = _
    var termsOfServiceUrl : String = _
    var contact : String = _
    var license : String = _
    var licenseUrl : String = _
    var filterClass : String = _
    //var info : Info = _
    var host : String = _
    var basePath : String = _

    
    private def updateInfoFromConfig(swagger: Swagger) : Swagger = {

        var info = new Info()

        if (StringUtils.isNotBlank(description)) {
            info.description(description);
        }

        if (StringUtils.isNotBlank(title)) {
            info.title(title);
        }

        if (StringUtils.isNotBlank(version)) {
            info.version(version);
        }

        if (StringUtils.isNotBlank(termsOfServiceUrl)) {
            info.termsOfService(termsOfServiceUrl);
        }

        if (contact != null) {
            info.contact(new Contact()
                    .name(contact));
        }
        if (license != null && licenseUrl != null) {
            info.license(new License()
                    .name(license)
                    .url(licenseUrl));
        }
        swagger.info(info)
    }
  override def configure(swagger: Swagger) : Swagger = {
    if (schemes != null) {
      for (s <- schemes) swagger.scheme(Scheme.forValue(s))
    }
    updateInfoFromConfig(swagger)
    swagger.host(host)    
    swagger.basePath(basePath);
      
  }
  override def getFilterClass() : String = {
      null
  }    
    
  override def classes(): java.util.Set[Class[_]] = {
    Logger("swagger").info("ControllerScanner - looking for controllers with API annotation")

    
    var routes = RouteCacheFactory.getRouteCache().getAll().toList
        
    // get controller names from application routes
    val controllers =         routes.map{ case (_,route) =>
          s"${route.call.packageName}.${route.call.controller}"
        }.distinct

        
    var list = controllers.collect {
      case className: String if {
        try {
          SwaggerContext.loadClass(className).getAnnotation(classOf[Api]) != null
        } catch {
          case ex: Exception => {
            Logger("swagger").error("Problem loading class:  %s. %s: %s".format(className, ex.getClass.getName, ex.getMessage))
            false}
        }
      } =>
        Logger("swagger").info("Found API controller:  %s".format(className))
        SwaggerContext.loadClass(className)
    }
        
   list.toSet.asJava
    
  }
  
  override def getPrettyPrint(): Boolean = {
    true;
  }
  override def setPrettyPrint(x: Boolean) {}
}
