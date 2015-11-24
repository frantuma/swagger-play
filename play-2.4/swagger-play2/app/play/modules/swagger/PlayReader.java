package play.modules.swagger;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.swagger.annotations.*;
import io.swagger.converter.ModelConverters;
import io.swagger.jaxrs.Reader;
import io.swagger.jaxrs.config.ReaderConfig;
import io.swagger.jaxrs.ext.SwaggerExtension;
import io.swagger.jaxrs.ext.SwaggerExtensions;
import io.swagger.jaxrs.utils.ReaderUtils;
import io.swagger.models.*;
import io.swagger.models.Tag;
import io.swagger.models.parameters.*;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.util.BaseReaderUtils;
import io.swagger.util.ParameterProcessor;
import io.swagger.util.PathUtils;
import io.swagger.util.ReflectionUtils;
import org.apache.commons.lang3.StringUtils;
import play.Logger;
import play.modules.swagger.routes.*;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

public class PlayReader extends Reader {

    private static final String SUCCESSFUL_OPERATION = "successful operation";
    private static final String PATH_DELIMITER = "/";


    public PlayReader(Swagger swagger) {
        super(swagger);
    }

    public PlayReader(Swagger swagger, ReaderConfig config) {
        super(swagger, config);
    }

    private static Set<Scheme> parseSchemes(String schemes) {
        final Set<Scheme> result = EnumSet.noneOf(Scheme.class);
        for (String item : StringUtils.trimToEmpty(schemes).split(",")) {
            final Scheme scheme = Scheme.forValue(StringUtils.trimToNull(item));
            if (scheme != null) {
                result.add(scheme);
            }
        }
        return result;
    }

    private static boolean isVoid(Type type) {
        final Class<?> cls = TypeFactory.defaultInstance().constructType(type).getRawClass();
        return Void.class.isAssignableFrom(cls) || Void.TYPE.isAssignableFrom(cls);
    }

    private static boolean isValidResponse(Type type) {
        if (type == null) {
            return false;
        }
        final JavaType javaType = TypeFactory.defaultInstance().constructType(type);
        if (isVoid(javaType)) {
            return false;
        }
        final Class<?> cls = javaType.getRawClass();
        return !javax.ws.rs.core.Response.class.isAssignableFrom(cls) && !isResourceClass(cls);
    }

    private static boolean isResourceClass(Class<?> cls) {
        return cls.getAnnotation(Api.class) != null;
    }

    @Override
    public Swagger read(Class<?> cls) {
        return read(cls, "", null, false, new String[0], new String[0], new HashMap<String, Tag>(), new ArrayList<Parameter>(), new HashSet<Class<?>>());
    }

    private Swagger read(Class<?> cls, String parentPath, String parentMethod, boolean readHidden, String[] parentConsumes, String[] parentProduces, Map<String, Tag> parentTags, List<Parameter> parentParameters, Set<Class<?>> scannedResources) {

        RouteWrapper routes = RouteFactory.getRoute();

        PlaySwaggerConfig config = PlayConfigFactory.getConfig();

        Api api = (Api) cls.getAnnotation(Api.class);
        Map<String, SecurityScope> globalScopes = new HashMap<String, SecurityScope>();

        Map<String, Tag> tags = new HashMap<String, Tag>();
        List<SecurityRequirement> securities = new ArrayList<SecurityRequirement>();
        String[] consumes = new String[0];
        String[] produces = new String[0];
        final Set<Scheme> globalSchemes = EnumSet.noneOf(Scheme.class);

        // readable only if (api annotation and !api.hidden) or route or readHidden
        //final boolean readable = (api != null && !api.hidden()) || readHidden || );
        // if api annotation no route

        final boolean readable = (api != null && readHidden) || (api != null && !api.hidden());
        if (readable) {
            // the value will be used as a tag for 2.0 UNLESS a Tags annotation is present
            Set<String> tagStrings = extractTags(api);
            for (String tagString : tagStrings) {
                Tag tag = new Tag().name(tagString);
                tags.put(tagString, tag);
            }
            if (parentTags != null) {
                tags.putAll(parentTags);
            }
            for (String tagName : tags.keySet()) {
                getSwagger().tag(tags.get(tagName));
            }

            if (!api.produces().isEmpty()) {
                produces = toArray(api.produces());
            } else if (cls.getAnnotation(Produces.class) != null) {
                produces = ReaderUtils.splitContentValues(cls.getAnnotation(Produces.class).value());
            }
            if (!api.consumes().isEmpty()) {
                consumes =  toArray(api.consumes());
            } else if (cls.getAnnotation(Consumes.class) != null) {
                consumes = ReaderUtils.splitContentValues(cls.getAnnotation(Consumes.class).value());
            }
            globalSchemes.addAll(parseSchemes(api.protocols()));
            Authorization[] authorizations = api.authorizations();

            for (Authorization auth : authorizations) {
                if (auth.value() != null && !"".equals(auth.value())) {
                    SecurityRequirement security = new SecurityRequirement();
                    security.setName(auth.value());
                    AuthorizationScope[] scopes = auth.scopes();
                    for (AuthorizationScope scope : scopes) {
                        if (scope.scope() != null && !"".equals(scope.scope())) {
                            security.addScope(scope.scope());
                        }
                    }
                    securities.add(security);
                }
            }
        }

        // TODO possibly allow parsing also without @Api annotation by looking at routes
        if (readable || (api == null && getConfig().isScanAllResources())) {
            // merge consumes, produces

            // look for method-level annotated properties

            // handle sub-resources by looking at return type

            final List<Parameter> globalParameters = new ArrayList<Parameter>();

            // look for constructor-level annotated properties
            globalParameters.addAll(ReaderUtils.collectConstructorParameters(cls, getSwagger()));

            // look for field-level annotated properties
            globalParameters.addAll(ReaderUtils.collectFieldParameters(cls, getSwagger()));

            // parse the method
            // TODO get rid in case of javax.ws annotation checking
            final javax.ws.rs.Path apiPath = ReflectionUtils.getAnnotation(cls, javax.ws.rs.Path.class);
            Method methods[] = cls.getMethods();
            for (Method method : methods) {
                if (ReflectionUtils.isOverriddenMethod(method, cls)) {
                    continue;
                }
                javax.ws.rs.Path methodPath = ReflectionUtils.getAnnotation(method, javax.ws.rs.Path.class);

                // complete name as stored in route
                String fullMethodName = getFullMethodName(cls, method);

                if (!routes.exists(fullMethodName)) {
                    continue;
                }
                Route route = routes.get(fullMethodName);

                String operationPath = getPathFromRoute(route.path(), config.basePath);

                Map<String, String> regexMap = new HashMap<String, String>();

                if (StringUtils.isEmpty(operationPath)) {
                    operationPath = getPath(apiPath, methodPath, parentPath);
                    operationPath = PathUtils.parsePath(operationPath, regexMap);
                }

                if (operationPath != null) {
                    if (isIgnored(operationPath)) {
                        continue;
                    }
                    final ApiOperation apiOperation = ReflectionUtils.getAnnotation(method, ApiOperation.class);
                    // TODO here it uses Extension chain mechanism from swagger-jaxrs, move here
                    String httpMethod = extractOperationMethod(apiOperation, method, SwaggerExtensions.chain());
                    Operation operation = null;
                    if (apiOperation != null || getConfig().isScanAllResources() || httpMethod != null || methodPath != null) {
                        operation = parseMethod(cls, method, globalParameters, route);
                    }
                    if (operation == null) {
                        continue;
                    }
                    if (parentParameters != null) {
                        for (Parameter param : parentParameters) {
                            operation.parameter(param);
                        }
                    }
                    for (Parameter param : operation.getParameters()) {
                        if (regexMap.get(param.getName()) != null) {
                            String pattern = regexMap.get(param.getName());
                            param.setPattern(pattern);
                        }
                    }

                    if (apiOperation != null) {
                        for (Scheme scheme : parseSchemes(apiOperation.protocols())) {
                            operation.scheme(scheme);
                        }
                    }

                    if (operation.getSchemes() == null || operation.getSchemes().isEmpty()) {
                        for (Scheme scheme : globalSchemes) {
                            operation.scheme(scheme);
                        }
                    }

                    String[] apiConsumes = consumes;
                    if (parentConsumes != null) {
                        Set<String> both = new HashSet<String>(Arrays.asList(apiConsumes));
                        both.addAll(new HashSet<String>(Arrays.asList(parentConsumes)));
                        if (operation.getConsumes() != null) {
                            both.addAll(new HashSet<String>(operation.getConsumes()));
                        }
                        apiConsumes = both.toArray(new String[both.size()]);
                    }

                    String[] apiProduces = produces;
                    if (parentProduces != null) {
                        Set<String> both = new HashSet<String>(Arrays.asList(apiProduces));
                        both.addAll(new HashSet<String>(Arrays.asList(parentProduces)));
                        if (operation.getProduces() != null) {
                            both.addAll(new HashSet<String>(operation.getProduces()));
                        }
                        apiProduces = both.toArray(new String[both.size()]);
                    }
                    final Class<?> subResource = getSubResource(method);
                    if (subResource != null && !scannedResources.contains(subResource)) {
                        scannedResources.add(subResource);
                        read(subResource, operationPath, httpMethod, true, apiConsumes, apiProduces, tags, operation.getParameters(), scannedResources);
                    }

                    // can't continue without a valid http method
                    httpMethod = httpMethod == null ? parentMethod : httpMethod;
                    if (httpMethod != null) {
                        if (apiOperation != null) {
                            boolean hasExplicitTag = false;
                            for (String tag : apiOperation.tags()) {
                                if (!"".equals(tag)) {
                                    operation.tag(tag);
                                    getSwagger().tag(new Tag().name(tag));
                                }
                            }

                            if (operation != null) {
                                operation.getVendorExtensions().putAll(BaseReaderUtils.parseExtensions(apiOperation.extensions()));
                            }
                        }
                        if (operation != null) {
                            if (operation.getConsumes() == null) {
                                for (String mediaType : apiConsumes) {
                                    operation.consumes(mediaType);
                                }
                            }
                            if (operation.getProduces() == null) {
                                for (String mediaType : apiProduces) {
                                    operation.produces(mediaType);
                                }
                            }

                            if (operation.getTags() == null) {
                                for (String tagString : tags.keySet()) {
                                    operation.tag(tagString);
                                }
                            }
                            // Only add global @Api securities if operation doesn't already have more specific securities
                            if (operation.getSecurity() == null) {
                                for (SecurityRequirement security : securities) {
                                    operation.security(security);
                                }
                            }
                            Path path = getSwagger().getPath(operationPath);
                            if (path == null) {
                                path = new Path();
                                getSwagger().path(operationPath, path);
                            }
                            path.set(httpMethod, operation);
                            try {
                                readImplicitParameters(method, operation);
                            } catch (Exception e) {
                                throw e;
                            }
                        }
                    }
                }
            }
        }

        return getSwagger();
    }

    String getPathFromRoute(PathPattern pathPattern, String basePath) {

        StringBuilder sb = new StringBuilder();
        scala.collection.Iterator iter = pathPattern.parts().iterator();
        while (iter.hasNext()) {
            PathPart part = (PathPart) iter.next();
            if (part instanceof StaticPart) {
                sb.append(((StaticPart) part).value());
            } else if (part instanceof DynamicPart) {
                // TODO is there any swagger-io mean to handle dynamic part?
                sb.append("{");
                sb.append(((DynamicPart) part).name());
                sb.append("}");
            } else {
                sb.append(((StaticPart) part).value());
            }
        }
        StringBuilder operationPath = new StringBuilder();
        if (basePath.startsWith("/")) basePath = basePath.substring(1);
        operationPath.append(sb.toString().replaceFirst(basePath, ""));
        if (!operationPath.toString().startsWith("/")) operationPath.insert(0, "/");
        return operationPath.toString();
    }

    String getPath(javax.ws.rs.Path classLevelPath, javax.ws.rs.Path methodLevelPath, String parentPath) {
        if (classLevelPath == null && methodLevelPath == null && StringUtils.isEmpty(parentPath)) {
            return null;
        }
        StringBuilder b = new StringBuilder();
        if (parentPath != null && !"".equals(parentPath) && !"/".equals(parentPath)) {
            if (!parentPath.startsWith("/")) {
                parentPath = "/" + parentPath;
            }
            if (parentPath.endsWith("/")) {
                parentPath = parentPath.substring(0, parentPath.length() - 1);
            }

            b.append(parentPath);
        }
        if (classLevelPath != null) {
            b.append(classLevelPath.value());
        }
        if (methodLevelPath != null && !"/".equals(methodLevelPath.value())) {
            String methodPath = methodLevelPath.value();
            if (!methodPath.startsWith("/") && !b.toString().endsWith("/")) {
                b.append("/");
            }
            if (methodPath.endsWith("/")) {
                methodPath = methodPath.substring(0, methodPath.length() - 1);
            }
            b.append(methodPath);
        }
        String output = b.toString();
        if (!output.startsWith("/")) {
            output = "/" + output;
        }
        if (output.endsWith("/") && output.length() > 1) {
            return output.substring(0, output.length() - 1);
        } else {
            return output;
        }
    }

    private void readImplicitParameters(Method method, Operation operation) {
        ApiImplicitParams implicitParams = method.getAnnotation(ApiImplicitParams.class);
        if (implicitParams != null && implicitParams.value().length > 0) {
            for (ApiImplicitParam param : implicitParams.value()) {
                Parameter p = readImplicitParam(param);
                if (p != null) {
                    operation.addParameter(p);
                }
            }
        }
    }

    protected io.swagger.models.parameters.Parameter readImplicitParam(ApiImplicitParam param) {
        final Parameter p;
        if (param.paramType().equalsIgnoreCase("path")) {
            p = new PathParameter();
        } else if (param.paramType().equalsIgnoreCase("query")) {
            p = new QueryParameter();
        } else if (param.paramType().equalsIgnoreCase("form") || param.paramType().equalsIgnoreCase("formData")) {
            p = new FormParameter();
        } else if (param.paramType().equalsIgnoreCase("body")) {
            p = null;
        } else if (param.paramType().equalsIgnoreCase("header")) {
            p = new HeaderParameter();
        } else {
            //LOGGER.warn("Unkown implicit parameter type: [" + param.paramType() + "]");
            return null;
        }
        final Type type = ReflectionUtils.typeFromString(param.dataType());
        return ParameterProcessor.applyAnnotations(getSwagger(), p, type == null ? String.class : type,
                Arrays.<Annotation>asList(param));
    }

    private Operation parseMethod(Class<?> cls, Method method, List<Parameter> globalParameters, Route route) {
        Operation operation = new Operation();

        ApiOperation apiOperation = ReflectionUtils.getAnnotation(method, ApiOperation.class);
        ApiResponses responseAnnotation = ReflectionUtils.getAnnotation(method, ApiResponses.class);

        String operationId = method.getName();
        operation.operationId(operationId);
        String responseContainer = null;

        Type responseType = null;
        Map<String, Property> defaultResponseHeaders = new HashMap<String, Property>();

        if (apiOperation != null) {
            if (apiOperation.hidden()) {
                return null;
            }
            if (!"".equals(apiOperation.nickname())) {
                operationId = apiOperation.nickname();
            }

            defaultResponseHeaders = parseResponseHeaders(apiOperation.responseHeaders());

            operation
                    .summary(apiOperation.value())
                    .description(apiOperation.notes());

            if (apiOperation.response() != null && !isVoid(apiOperation.response())) {
                responseType = apiOperation.response();
            }
            if (!"".equals(apiOperation.responseContainer())) {
                responseContainer = apiOperation.responseContainer();
            }
            if (apiOperation.authorizations() != null) {
                List<SecurityRequirement> securities = new ArrayList<SecurityRequirement>();
                for (Authorization auth : apiOperation.authorizations()) {
                    if (auth.value() != null && !"".equals(auth.value())) {
                        SecurityRequirement security = new SecurityRequirement();
                        security.setName(auth.value());
                        AuthorizationScope[] scopes = auth.scopes();
                        for (AuthorizationScope scope : scopes) {
                            if (scope.scope() != null && !"".equals(scope.scope())) {
                                security.addScope(scope.scope());
                            }
                        }
                        securities.add(security);
                    }
                }
                if (securities.size() > 0) {
                    for (SecurityRequirement sec : securities) {
                        operation.security(sec);
                    }
                }
            }
            if (apiOperation.consumes() != null && !apiOperation.consumes().isEmpty()) {
                operation.consumes(Arrays.asList(toArray(apiOperation.consumes())));
            }
            if (apiOperation.produces() != null && !apiOperation.produces().isEmpty()) {
                operation.produces(Arrays.asList(toArray(apiOperation.produces())));
            }
        }

        if (apiOperation != null && StringUtils.isNotEmpty(apiOperation.responseReference())) {
            Response response = new Response().description(SUCCESSFUL_OPERATION);
            response.schema(new RefProperty(apiOperation.responseReference()));
            operation.addResponse(String.valueOf(apiOperation.code()), response);
        } else if (responseType == null) {
            // pick out response from method declaration
            //LOGGER.debug("picking up response class from method " + method);
            responseType = method.getGenericReturnType();
        }
        if (isValidResponse(responseType)) {
            final Property property = ModelConverters.getInstance().readAsProperty(responseType);
            if (property != null) {
                final Property responseProperty = ContainerWrapper.wrapContainer(responseContainer, property);
                final int responseCode = apiOperation == null ? 200 : apiOperation.code();
                operation.response(responseCode, new Response().description(SUCCESSFUL_OPERATION).schema(responseProperty)
                        .headers(defaultResponseHeaders));
                appendModels(responseType);
            }
        }

        operation.operationId(operationId);

        if (apiOperation != null && apiOperation.consumes() != null && apiOperation.consumes().isEmpty()) {
            final Consumes consumes = ReflectionUtils.getAnnotation(method, Consumes.class);
            if (consumes != null) {
                for (String mediaType : ReaderUtils.splitContentValues(consumes.value())) {
                    operation.consumes(mediaType);
                }
            }
        }

        if (apiOperation != null && apiOperation.produces() != null && apiOperation.produces().isEmpty()) {
            final Produces produces = ReflectionUtils.getAnnotation(method, Produces.class);
            if (produces != null) {
                for (String mediaType : ReaderUtils.splitContentValues(produces.value())) {
                    operation.produces(mediaType);
                }
            }
        }

        List<ApiResponse> apiResponses = new ArrayList<ApiResponse>();
        if (responseAnnotation != null) {
            for (ApiResponse apiResponse : responseAnnotation.value()) {
                Map<String, Property> responseHeaders = parseResponseHeaders(apiResponse.responseHeaders());

                Response response = new Response()
                        .description(apiResponse.message())
                        .headers(responseHeaders);

                if (apiResponse.code() == 0) {
                    operation.defaultResponse(response);
                } else {
                    operation.response(apiResponse.code(), response);
                }

                if (StringUtils.isNotEmpty(apiResponse.reference())) {
                    response.schema(new RefProperty(apiResponse.reference()));
                } else if (!isVoid(apiResponse.response())) {
                    responseType = apiResponse.response();
                    final Property property = ModelConverters.getInstance().readAsProperty(responseType);
                    if (property != null) {
                        response.schema(ContainerWrapper.wrapContainer(apiResponse.responseContainer(), property));
                        appendModels(responseType);
                    }
                }
            }
        }
        if (ReflectionUtils.getAnnotation(method, Deprecated.class) != null) {
            operation.setDeprecated(true);
        }

        // process parameters
        for (Parameter globalParameter : globalParameters) {
            operation.parameter(globalParameter);
        }

        Type[] genericParameterTypes = method.getGenericParameterTypes();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        for (int i = 0; i < genericParameterTypes.length; i++) {
            final Type type = TypeFactory.defaultInstance().constructType(genericParameterTypes[i], cls);
            List<Parameter> parameters = getParameters(type, Arrays.asList(paramAnnotations[i]), i, route);

            for (Parameter parameter : parameters) {
                operation.parameter(parameter);
            }
        }

        if (operation.getResponses() == null) {
            Response response = new Response().description(SUCCESSFUL_OPERATION);
            operation.defaultResponse(response);
        }
        return operation;
    }

    private List<Parameter> getParameters(Type type, List<Annotation> annotations, int position, Route route) {

        // TODO support route multi parameter, body and query string
        final Iterator<SwaggerExtension> chain = SwaggerExtensions.chain();
        if (!chain.hasNext()) {
            return Collections.emptyList();
        }
        Logger.debug("getParameters for " + type);
        Set<Type> typesToSkip = new HashSet<Type>();
        final SwaggerExtension extension = chain.next();
        Logger.debug("trying extension " + extension);

        final List<Parameter> parameters = extension.extractParameters(annotations, type, typesToSkip, chain);
        if (parameters.size() > 0) {
            final List<Parameter> processed = new ArrayList<Parameter>(parameters.size());
            for (Parameter parameter : parameters) {
                if (ParameterProcessor.applyAnnotations(getSwagger(), parameter, type, annotations) != null) {
                    processed.add(parameter);
                }
            }
            return processed;
        } else {
            Logger.debug("no parameter found, looking at body params");
            final List<Parameter> body = new ArrayList<Parameter>();
            if (!typesToSkip.contains(type)) {
                Parameter param = ParameterProcessor.applyAnnotations(getSwagger(), null, type, annotations);

                // only if we have a class of param and it is unnamed (= 'body') get name from route
                if (type instanceof JavaType && "body".equals(param.getName())){
                    // get class of param
                    String paramClassName = ((JavaType)type).getRawClass().getSimpleName();
                    // get param from route at position position
                    if (route.call().parameters().isDefined()) {
                        //int size = route.call().parameters().get().size();
                        play.modules.swagger.routes.Parameter p = null;
                        try {
                            p = route.call().parameters().get().take(position + 1).last();
                            String routeParamClassName = p.typeName();
                            // check if class of param is same as param from route
                            if (routeParamClassName.equals(paramClassName)) {
                                // add param name from route
                                param.setName(p.name());
                            }
                        } catch (Exception e){
                            Logger.debug("exception getting route param: " + e.getMessage());
                        }
                    }
                }
                if (param != null) {
                    body.add(param);
                }
            }
            return body;
        }
    }


    private void appendModels(Type type) {
        final Map<String, Model> models = ModelConverters.getInstance().readAll(type);
        for (Map.Entry<String, Model> entry : models.entrySet()) {
            getSwagger().model(entry.getKey(), entry.getValue());
        }
    }

    private Map<String, Property> parseResponseHeaders(ResponseHeader[] headers) {
        Map<String, Property> responseHeaders = null;
        if (headers != null && headers.length > 0) {
            for (ResponseHeader header : headers) {
                String name = header.name();
                if (!"".equals(name)) {
                    if (responseHeaders == null) {
                        responseHeaders = new HashMap<String, Property>();
                    }
                    String description = header.description();
                    Class<?> cls = header.response();

                    if (!isVoid(cls)) {
                        final Property property = ModelConverters.getInstance().readAsProperty(cls);
                        if (property != null) {
                            Property responseProperty = ContainerWrapper.wrapContainer(header.responseContainer(), property,
                                    ContainerWrapper.ARRAY, ContainerWrapper.LIST, ContainerWrapper.SET);
                            responseProperty.setDescription(description);
                            responseHeaders.put(name, responseProperty);
                            appendModels(cls);
                        }
                    }
                }
            }
        }
        return responseHeaders;
    }

    private boolean isIgnored(String path) {
        for (String item : getConfig().getIgnoredRoutes()) {
            final int length = item.length();
            if (path.startsWith(item) && (path.length() == length || path.startsWith(PATH_DELIMITER, length))) {
                return true;
            }
        }
        return false;
    }

    // TODO move in utils
    private String getFullMethodName(Class clazz, Method method) {

        if (clazz.getCanonicalName().indexOf("$") == -1) {
            return clazz.getCanonicalName() + "$." + method.getName();
        } else {
            return clazz.getCanonicalName() + "." + method.getName();
        }
    }

    private String[] toArray(String csString){
        if (StringUtils.isEmpty(csString)) return new String[] {csString};
        int i = 0;
        String[] result = csString.split(",");
        for (String c: result){
            result[i] = c.trim();
            i++;
        }
        return result;
    }

    enum ContainerWrapper {
        LIST("list") {
            @Override
            protected Property doWrap(Property property) {
                return new ArrayProperty(property);
            }
        },
        ARRAY("array") {
            @Override
            protected Property doWrap(Property property) {
                return new ArrayProperty(property);
            }
        },
        MAP("map") {
            @Override
            protected Property doWrap(Property property) {
                return new MapProperty(property);
            }
        },
        SET("set") {
            @Override
            protected Property doWrap(Property property) {
                ArrayProperty arrayProperty = new ArrayProperty(property);
                arrayProperty.setUniqueItems(true);
                return arrayProperty;
            }
        };

        private final String container;

        ContainerWrapper(String container) {
            this.container = container;
        }

        public static Property wrapContainer(String container, Property property, ContainerWrapper... allowed) {
            final Set<ContainerWrapper> tmp = allowed.length > 0 ? EnumSet.copyOf(Arrays.asList(allowed)) : EnumSet.allOf(ContainerWrapper.class);
            for (ContainerWrapper wrapper : tmp) {
                final Property prop = wrapper.wrap(container, property);
                if (prop != null) {
                    return prop;
                }
            }
            return property;
        }

        public Property wrap(String container, Property property) {
            if (this.container.equalsIgnoreCase(container)) {
                return doWrap(property);
            }
            return null;
        }

        protected abstract Property doWrap(Property property);
    }
}