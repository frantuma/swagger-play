package play.modules.swagger;


public class RouteFactory {

	private static RouteWrapper instance;
	
	public static void setRoute(RouteWrapper routes){
		instance = routes;
	}
	public static RouteWrapper getRoute(){
		return instance;
	}
}