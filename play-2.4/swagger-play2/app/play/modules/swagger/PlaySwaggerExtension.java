package play.modules.swagger;

import java.lang.reflect.Method;
import java.util.Iterator;

import org.apache.commons.lang3.StringUtils;

import play.Logger;
import play.modules.swagger.routes.Route;
import io.swagger.annotations.ApiOperation;
import io.swagger.jaxrs.DefaultParameterExtension;
import io.swagger.jaxrs.ext.SwaggerExtension;
//import scala.Tuple3;

public class PlaySwaggerExtension extends DefaultParameterExtension {
	
	RouteWrapper routes;

	public PlaySwaggerExtension(){
		this.routes = RouteFactory.getRoute();
	}

	@Override
	public String extractOperationMethod(ApiOperation apiOperation,
			Method method, Iterator<SwaggerExtension> chain) {
		String httpMethod = super.extractOperationMethod(apiOperation, method, chain);
		if (StringUtils.isEmpty(httpMethod)) {
			//router.documentation();
			Route routeEntry = routes.get(getFullMethodName(method.getDeclaringClass(), method));
			if (routeEntry != null){
				try{
					httpMethod = routeEntry.verb().toString().toLowerCase();
				} catch (Exception e){
					Logger.error("http method not found for method: " + method.getName(), e);
				}

			}
		}
		return httpMethod;
		
	}
	
	
    @Override
    protected boolean shouldIgnoreClass(Class<?> cls) {
        return false;
    }

    // TODO move in utils
    private String getFullMethodName(Class clazz, Method method){
    	
    	if (clazz.getCanonicalName().indexOf("$") == -1){
    		return clazz.getCanonicalName() + "$." + method.getName();
    	} else {
    		return clazz.getCanonicalName() + "." + method.getName();
    	}
    }
}