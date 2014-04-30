package com.github.kongchen.swagger.docgen.mavenplugin;

import com.github.kongchen.swagger.docgen.AbstractDocumentSource;
import com.github.kongchen.swagger.docgen.GenerateException;
import com.github.kongchen.swagger.docgen.LogAdapter;
import com.google.common.collect.Sets;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.config.SwaggerConfig;
import com.wordnik.swagger.converter.ModelConverter;
import com.wordnik.swagger.converter.ModelConverters;
import com.wordnik.swagger.core.SwaggerSpec;
import com.wordnik.swagger.jaxrs.JaxrsApiReader;
import com.wordnik.swagger.jaxrs.reader.DefaultJaxrsApiReader;
import com.wordnik.swagger.model.ApiListing;
import com.wordnik.swagger.model.ApiListingReference;
import com.wordnik.swagger.model.AuthorizationType;
import com.wordnik.swagger.model.Model;
import com.wordnik.swagger.model.ResourceListing;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.logging.Log;

import scala.None;
import scala.Option;
import scala.collection.JavaConversions;
import scala.collection.immutable.HashMap;
import scala.collection.immutable.HashSet;
import scala.collection.immutable.Map;
import scala.collection.mutable.Buffer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 *
 * @author: chekong
 * 05/13/2013
 */
public class MavenDocumentSource extends AbstractDocumentSource {
    private final ApiSource apiSource;

    private String[] ignoredTypes;

    public MavenDocumentSource(ApiSource apiSource, Log log) {
        super(new LogAdapter(log),
                apiSource.getOutputPath(), apiSource.getOutputTemplate(), apiSource.getSwaggerDirectory(), apiSource.mustacheFileRoot, apiSource.isUseOutputFlatStructure());

        setApiVersion(apiSource.getApiVersion());
        setBasePath(apiSource.getBasePath());
        this.apiSource = apiSource;
        if (apiSource.getIgnoredTypes() != null) {
            this.ignoredTypes = StringUtils.split(apiSource.getIgnoredTypes(), ";");
        } else {
            this.ignoredTypes = new String[0];
        }
    }

    @Override
    public void loadDocuments() throws GenerateException {
        SwaggerConfig swaggerConfig =  new SwaggerConfig();
        swaggerConfig.setApiVersion(apiSource.getApiVersion());
        swaggerConfig.setSwaggerVersion(SwaggerSpec.version());
        
        ModelConverter modelConverter = new ModelConverter() {

            @Override
            public Map<String, String> typeMap() {
                return new HashMap<String, String>();
            }

            @Override
            public String toName(Class<?> clazz) {
                return clazz.getSimpleName();
            }

            @Override
            public Option<String> toDescriptionOpt(Class<?> clazz) {
                return Option.empty();
            }

            @Override
            public scala.collection.immutable.Set<String> skippedClasses() {
                return new HashSet<String>();
            }

            @Override
            public Option<Model> read(Class<?> arg0, Map<String, String> arg1) {
                return Option.empty();
            }

            @Override
            public scala.collection.immutable.Set<String> ignoredPackages() {
                return new HashSet<String>();
            }

            @Override
            public scala.collection.immutable.Set<String> ignoredClasses() {
                return JavaConversions.asScalaSet(Sets.newHashSet(ignoredTypes)).toSet();
            }
        };

        ModelConverters.addConverter(modelConverter, true);

        List<ApiListingReference> apiListingReferences = new ArrayList<ApiListingReference>();
        List<AuthorizationType> authorizationTypes = new ArrayList<AuthorizationType>();
        for (Class c : apiSource.getValidClasses()) {
            ApiListing doc;
            try {
                doc  = getDocFromClass(c, swaggerConfig, getBasePath());
            } catch (Exception e) {
                throw new GenerateException(e);
            }
            if (doc == null) continue;
            LOG.info("Detect Resource:" + c.getName());

            Buffer<AuthorizationType> buffer = doc.authorizations().toBuffer();
            authorizationTypes.addAll(JavaConversions.asJavaList(buffer));
            ApiListingReference apiListingReference = new ApiListingReference(doc.resourcePath(), doc.description(), doc.position());
            apiListingReferences.add(apiListingReference);
            acceptDocument(doc);
        }
        // sort apiListingRefernce by position
        Collections.sort(apiListingReferences, new Comparator<ApiListingReference>() {
            @Override
            public int compare(ApiListingReference o1, ApiListingReference o2) {
                if (o1 == null && o2 == null) return 0;
                if (o1 == null && o2 != null) return -1;
                if (o1 != null && o2 == null) return 1;
                return  o1.position() - o2.position();
            }
        });
        serviceDocument = new ResourceListing(swaggerConfig.apiVersion(), swaggerConfig.swaggerVersion(),
                scala.collection.immutable.List.fromIterator(JavaConversions.asScalaIterator(apiListingReferences.iterator())),
                scala.collection.immutable.List.fromIterator(JavaConversions.asScalaIterator(authorizationTypes.iterator())),
                swaggerConfig.info());
    }

    private ApiListing getDocFromClass(Class c, SwaggerConfig swaggerConfig, String basePath) throws Exception {
        Api resource = (Api) c.getAnnotation(Api.class);

        if (resource == null) return null;
        JaxrsApiReader reader = new DefaultJaxrsApiReader();
        Option<ApiListing> apiListing = reader.read(basePath, c, swaggerConfig);

        if (None.canEqual(apiListing)) return null;

        return apiListing.get();
    }
}
