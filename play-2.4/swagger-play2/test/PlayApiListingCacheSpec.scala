import io.swagger.config.{ ScannerFactory, SwaggerConfig, ConfigFactory }
import play.modules.swagger._
import org.specs2.mutable._
import org.specs2.mock.Mockito
import scala.Some
import play.api.Logger
import io.swagger.util.Json
import io.swagger.jaxrs.ext.SwaggerExtension
import io.swagger.jaxrs.ext.SwaggerExtensions
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import play.modules.swagger.routes.{ Route => PlayRoute, Parameter => PlayParameter, DynamicPart, HandlerCall, HttpVerb, PathPattern }

import play.modules.swagger.routes.Route

class PlayApiListingCacheSpec extends Specification with Mockito {

  // set up mock for Play Router
  val mockRoutes = mock[play.api.routing.Router]
  val routesDocumentation: Seq[(String, String, String)] = Seq(
    ("GET", "/api/dog", "test.testdata.DogController.list"),
    ("PUT", "/api/dog", "test.testdata.DogController.add1"),
    ("GET", "/api/cat", "@test.testdata.CatController.list"),
    ("GET", "/api/cat", "@test.testdata.CatController.add1"),
    ("GET", "/api/fly", "test.testdata.FlyController.list"),
    ("PUT", "/api/dog/:id", "test.testdata.DogController.add0(id:String)"))
  mockRoutes.documentation returns routesDocumentation

  val routesList = {
    play.modules.swagger.routes.RoutesFileParser.parseit("""
GET /api/dog test.testdata.DogController.list
PUT /api/dog test.testdata.DogController.add1
GET /api/cat @test.testdata.CatController.list
GET /api/cat @test.testdata.CatController.add1
GET /api/fly test.testdata.FlyController.list
PUT /api/dog test.testdata.DogController.add1
PUT /api/dog/:id test.testdata.DogController.add0(id:String)
    """, "").right.get.collect {
      case (prefix, route: PlayRoute) => {
        val routeName = s"${route.call.packageName}.${route.call.controller}$$.${route.call.method}"
        (prefix, route)
      }
    }
  }

  val routesRules = Map(routesList map 
  { route =>
    {
      val routeName = s"${route._2.call.packageName}.${route._2.call.controller}$$.${route._2.call.method}"
      (routeName -> route._2)
    }
  } : _*)

  val apiVersion = "test1"
  val basePath = "http://aa.bb.com"

  // TODO use config
  var scanner = new PlayApiScanner(Some(mockRoutes))
  scanner.description = "description"
  scanner.basePath = basePath
  scanner.contact = "contact"
  //scanner.host = host
  scanner.version = "beta"
  scanner.title = "title"
  scanner.termsOfServiceUrl = "http://termsOfServiceUrl"
  scanner.license = "license"
  scanner.licenseUrl = "http://licenseUrl"
  

  ScannerFactory.setScanner(scanner)
  val routeCache = new RouteCache(routesRules)
  RouteCacheFactory.setRouteCache(routeCache)
  val ext: SwaggerExtension = new PlaySwaggerExtension()
  SwaggerExtensions.setExtensions(List(ext).asJava);

  "ApiListingCache" should {

    "load all API specs" in {

      val docRoot = ""
      val listings = ApiListingCache.listing(docRoot)

      Logger.debug ("swagger: " + toJsonString(listings.get))
      listings must beSome
      // TODO complete test
      /*
      val listingMap: Map[String, ApiListing] = listings.get

      listingMap.toList.length must beEqualTo(2)
      listingMap.get("/apitest/cats") must beSome
      val catsApiListing = listingMap.get("/apitest/cats").get

      catsApiListing.apis.length must beEqualTo(1)
      catsApiListing.apis.head.description must beNone
      catsApiListing.apis.head.path must beEqualTo("/api/cat") // do we use this?
      catsApiListing.apis.head.operations.length must beEqualTo(2)
      // todo - look inside operations

      catsApiListing.apiVersion must beEqualTo("test1")
      catsApiListing.authorizations must beEqualTo(List.empty)
      catsApiListing.basePath must beEqualTo("http://aa.bb.com")
      catsApiListing.consumes must beEqualTo(List.empty)
      catsApiListing.description must beEqualTo(Some("play with cats"))
      catsApiListing.models must beSome
      catsApiListing.models.get.toList.length must beEqualTo(1)
      catsApiListing.models.get.get("Cat") must beSome
      val catModel = catsApiListing.models.get.get("Cat").get
      catModel.baseModel must beNone
      catModel.description must beNone
      catModel.discriminator must beNone
      catModel.id must beEqualTo("Cat")
      catModel.name must beEqualTo("Cat")

      catModel.properties.toList.length must beEqualTo(2)
      // todo - look inside properties

      catModel.qualifiedType must beEqualTo("test.testdata.Cat")
      catModel.subTypes must beEqualTo(List.empty)

      catsApiListing.position must beEqualTo(0)
      catsApiListing.produces must beEqualTo(List("application/json"))
      catsApiListing.protocols must beEqualTo(List.empty)
      catsApiListing.resourcePath must beEqualTo("/apitest/cats")
      catsApiListing.swaggerVersion must beEqualTo("1.2")

      listingMap.get("/apitest/dogs") must beSome
      val dogsApiListing = listingMap.get("/apitest/dogs").get

      dogsApiListing.apis.length must beEqualTo(1)
      dogsApiListing.apis.head.description must beNone
      dogsApiListing.apis.head.path must beEqualTo("/api/dog") // do we use this?
      dogsApiListing.apis.head.operations.length must beEqualTo(2)
      // todo - look inside operations

      dogsApiListing.apiVersion must beEqualTo("test1")
      dogsApiListing.authorizations must beEqualTo(List.empty)
      dogsApiListing.basePath must beEqualTo("http://aa.bb.com")
      dogsApiListing.consumes must beEqualTo(List("application/json","application/xml"))
      dogsApiListing.description must beEqualTo(Some("look after the dogs"))
      dogsApiListing.models must beSome
      dogsApiListing.models.get.toList.length must beEqualTo(1)
      dogsApiListing.models.get.get("Dog") must beSome
      val dogModel = dogsApiListing.models.get.get("Dog").get
      dogModel.baseModel must beNone
      dogModel.description must beNone
      dogModel.discriminator must beNone
      dogModel.id must beEqualTo("Dog")
      dogModel.name must beEqualTo("Dog")

      dogModel.properties.toList.length must beEqualTo(2)
      // todo - look inside properties

      dogModel.qualifiedType must beEqualTo("test.testdata.Dog")
      dogModel.subTypes must beEqualTo(List.empty)

      dogsApiListing.position must beEqualTo(0)
      dogsApiListing.produces must beEqualTo(List("application/json", "application/xml"))
      dogsApiListing.protocols must beEqualTo(List("http", "https"))
      dogsApiListing.resourcePath must beEqualTo("/apitest/dogs")
      dogsApiListing.swaggerVersion must beEqualTo("1.2")
			*/
      /*
      listingMap.map {
        case (key, apiListing) =>
          println("key: %s".format(key))
          println("apis: %s".format(apiListing.apis))
          println("apiVersion: %s".format(apiListing.apiVersion))
          println("authorizations: %s".format(apiListing.authorizations))
          println("basePath: %s".format(apiListing.basePath))
          println("consumes: %s".format(apiListing.consumes))
          println("description: %s".format(apiListing.description))

          println("models: %s".format(apiListing.models))
          println("position: %s".format(apiListing.position))
          println("produces: %s".format(apiListing.produces))
          println("protocols: %s".format(apiListing.protocols))

          println("resourcePath: %s".format(apiListing.resourcePath))
          println("swaggerVersion: %s".format(apiListing.swaggerVersion))
          println("====================")
      }
      */

    }
  }
  def toJsonString(data: Any): String = {
    if (data.getClass.equals(classOf[String])) {
      data.asInstanceOf[String]
    } else {
      //Json.prettyPrint(data.asInstanceOf[AnyRef])      
      Json.pretty(data.asInstanceOf[AnyRef])
    }
  }
}