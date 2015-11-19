package play.modules.swagger;

import java.util.Map;

import play.modules.swagger.routes.Route;


public class RouteCache{
		
	private Map<String,Route>  router;
	
	public RouteCache(Map<String,Route> router){
		this.router = router;
	}
	
	public Route get(String routeName){
		return router.get(routeName);
	}
	public boolean exists(String routeName){
		return router.containsKey(routeName);
	}
	public Map<String,Route> getAll(){
		return router;
	}
	
	
}