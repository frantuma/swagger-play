package play.modules.swagger;


public class RouteCacheFactory{

	private static RouteCache instance; 
	
	public static void setRouteCache(RouteCache routes){
		instance = routes;
	}
	public static RouteCache getRouteCache(){
		return instance;
	}
}