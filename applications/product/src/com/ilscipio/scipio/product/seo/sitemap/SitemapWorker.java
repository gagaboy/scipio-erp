package com.ilscipio.scipio.product.seo.sitemap;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.ofbiz.base.component.ComponentConfig.WebappInfo;
import org.ofbiz.base.location.FlexibleLocation;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.webapp.WebAppUtil;
import org.xml.sax.SAXException;

import com.ilscipio.scipio.ce.webapp.filter.UrlRewriteConf;
import com.ilscipio.scipio.product.category.CatalogUrlType;
import com.ilscipio.scipio.product.seo.SeoCatalogUrlWorker;
import com.ilscipio.scipio.product.seo.UrlGenStats;
import com.redfin.sitemapgenerator.SitemapIndexGenerator;
import com.redfin.sitemapgenerator.WebSitemapGenerator;
import com.redfin.sitemapgenerator.WebSitemapUrl;

/**
 * Builds sitemap and records stats.
 * <p>
 * NOT thread-safe.
 * <p>
 * TODO: missing multi-locale link support - unclear if library supports
 * TODO: does not delete old files (minor)
 * FIXME: Content wrappers do not respect the useCache flag
 */
public class SitemapWorker {

    public static final String module = SitemapWorker.class.getName();
    
    protected final Delegator delegator;
    protected final LocalDispatcher dispatcher;
    protected final List<Locale> locales;
    protected final String webSiteId;
    protected final String baseUrl;
    protected final String sitemapContextPath;
    protected final String contextPath;
    protected final SitemapConfig config;
    protected final SeoCatalogUrlWorker urlWorker;
    protected final boolean useCache;
    
    static final String logPrefix = "Seo: Sitemap: ";
    
    protected UrlRewriteConf urlRewriteConf;
    protected final WebappInfo webappInfo;
    
    protected UrlGenStats stats = null;
    protected final String fullSitemapDir;
    
    protected Map<CatalogUrlType, EntityHandler> entityHandlers = null;
    protected EntityHandler productEntityHandler = null; // opt
    protected EntityHandler categoryEntityHandler = null; // opt
    
    protected final GenericValue webSite;
    protected final GenericValue productStore;

    protected SitemapWorker(Delegator delegator, LocalDispatcher dispatcher, List<Locale> locales, String webSiteId, GenericValue webSite, GenericValue productStore, String baseUrl, String sitemapContextPath, String contextPath, SitemapConfig config,
            SeoCatalogUrlWorker urlWorker, UrlRewriteConf urlRewriteConf, boolean useCache) throws GeneralException, IOException, URISyntaxException, SAXException {
        this.delegator = delegator;
        this.dispatcher = dispatcher;
        this.locales = locales;
        this.webSiteId = webSiteId;
        this.webSite = webSite;
        this.productStore = productStore;
        this.baseUrl = baseUrl;
        this.sitemapContextPath = sitemapContextPath;
        this.contextPath = contextPath;
        this.config = config;
        this.urlWorker = urlWorker;
        this.urlRewriteConf = urlRewriteConf;
        this.webappInfo = WebAppUtil.getWebappInfoFromWebsiteId(webSiteId);
        this.useCache = useCache;
        this.fullSitemapDir = config.getSitemapDirUrlLocation(webappInfo.getLocation());
        getSitemapDirFile(); // test this for exception
        reset();
    }

    public static SitemapWorker getWorkerForWebsite(Delegator delegator, LocalDispatcher dispatcher, String webSiteId, boolean useCache) throws GeneralException, IOException, URISyntaxException, SAXException, IllegalArgumentException {
        // TODO: LOCALIZE WITH PROP MESSAGE EXCEPTIONS
        
        GenericValue webSite = delegator.findOne("WebSite", UtilMisc.toMap("webSiteId", webSiteId), useCache);
        if (webSite == null) throw new GeneralException("website not found: " + webSiteId);
        String productStoreId = webSite.getString("productStoreId");
        if (UtilValidate.isEmpty(productStoreId)) throw new GeneralException("website has no product store: " + webSiteId);

        GenericValue productStore = delegator.findOne("ProductStore", UtilMisc.toMap("productStoreId", productStoreId), useCache);
        if (productStore == null) throw new GeneralException("store not found: " + productStoreId);

        SitemapConfig config = SitemapConfig.getSitemapConfigForWebsite(delegator, dispatcher, webSiteId);
        
        return getWorkerForWebsite(delegator, dispatcher, webSite, productStore, config, useCache);
    }
    
    public static SitemapWorker getWorkerForWebsite(Delegator delegator, LocalDispatcher dispatcher, GenericValue webSite, GenericValue productStore, SitemapConfig config, boolean useCache) throws GeneralException, IOException, URISyntaxException, SAXException {
        String webSiteId = webSite.getString("webSiteId");
        if (config == null) throw new IOException("no valid sitemap config for website '" + webSiteId + "'");
        String sitemapContextPath = config.getSitemapContextPath();
        if (sitemapContextPath == null) {
            sitemapContextPath = config.getDefaultContextPath(delegator);
        }
        String contextPath = config.getContextPath();
        if (contextPath == null) {
            contextPath = config.getDefaultContextPath(delegator);
        }
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null) {
            baseUrl = config.getDefaultBaseUrl(delegator, config.isBaseUrlSecure());
        }
        UrlRewriteConf urlRewriteConf = null;
        if (config.getUrlConfPath() != null) {
            urlRewriteConf = UrlRewriteConf.loadConf(config.getUrlConfPath());
        }
        return new SitemapWorker(delegator, dispatcher, 
                config.getLocalesOrDefault(webSite, productStore), 
                webSiteId, webSite, productStore,
                baseUrl, sitemapContextPath, contextPath, config,
                SeoCatalogUrlWorker.getInstance(delegator, webSiteId),
                urlRewriteConf,
                useCache);
    }

    public SitemapConfig getConfig() {
        return config;
    }
    
    public void reset() throws IOException, URISyntaxException {
        resetStats();
        resetEntityHandlers();
    }
    
    protected void resetStats() {
        this.stats = createStats();
    }
    
    protected UrlGenStats createStats() {
        return new UrlGenStats(config.isDoProduct(), config.isDoCategory(), false);
    }
    
    public UrlGenStats getStats() {
        return stats;
    }
    
    protected String getSitemapContextPath() {
        return sitemapContextPath;
    }
    
    protected String getContextPath() {
        return contextPath;
    }
    
    protected String getBaseUrl() {
        return (baseUrl != null ? baseUrl : "");
    }
    
    protected List<Locale> getLocales() {
        return locales;
    }
    
    protected Locale getDefaultLocale() {
        return locales.get(0);
    }
    
    /**
     * Gets cached conf.
     * Avoids reloading the urlrewrite.xml file for every single URL.
     */
    protected UrlRewriteConf getUrlRewriteConf() {
        return urlRewriteConf;
    }
    
    public String getFullSitemapDir() {
        return fullSitemapDir;
    }
    
    protected File getSitemapDirFile() throws IOException, URISyntaxException {
        String fullSitemapDir = getFullSitemapDir();
        try {
            URL url = FlexibleLocation.resolveLocation(fullSitemapDir);
            if (url == null) throw new MalformedURLException("Cannot resolve location: " + getFullSitemapDir());
            return new File(url.toURI());
        } catch(Exception e) {
            throw new IOException("Error resolving sitemap directory: " + fullSitemapDir + ": " + e.getMessage(), e);
        }
    }
    
    protected WebSitemapGenerator getSitemapGenerator(String filePrefix) throws IOException, URISyntaxException {
        File myDir = getSitemapDirFile();
        myDir.mkdirs();
        return WebSitemapGenerator.builder(getBaseUrl(), myDir).fileNamePrefix(filePrefix).dateFormat(config.getDateFormat()).gzip(config.isGzip()).build();
    }
    
    /**
     * Wrapper around {@link #buildSitemapDeepForProductCategories} that automatically applies
     * filters.
     */
    public void buildSitemapDeepForProductStore() throws GeneralException, IOException, URISyntaxException {
        buildSitemapDeepForProductStore(this.productStore);
    }
    
    /**
     * Wrapper around {@link #buildSitemapDeepForProductCategories} that automatically applies
     * filters.
     */
    public void buildSitemapDeepForProductStore(GenericValue productStore) throws GeneralException, IOException, URISyntaxException {
        List<GenericValue> prodCatalogList = EntityQuery.use(delegator).from("ProductStoreCatalog")
                .where(makeProductStoreCatalogCond(productStore.getString("productStoreId")))
                .filterByDate().orderBy("sequenceNum").cache(useCache).queryList();
        prodCatalogList = reorderProductStoreCatalogs(prodCatalogList);
        
        for(GenericValue prodCatalog : prodCatalogList) {
            List<GenericValue> prodCatalogCategoryList = EntityQuery.use(delegator).from("ProdCatalogCategory")
                    .where(makeProdCatalogCategoryCond(prodCatalog.getString("prodCatalogId")))
                    .filterByDate().orderBy("sequenceNum").cache(useCache).queryList();
            prodCatalogCategoryList = reorderProdCatalogCategories(prodCatalogCategoryList);
            buildSitemapDeepForProductCategories(prodCatalogCategoryList);
        }
    }
    
    public EntityCondition makeProductStoreCatalogCond(String productStoreId) {
        EntityCondition cond = EntityCondition.makeCondition("productStoreId", productStoreId);
        if (config.getProdCatalogIds() != null) {
            cond = EntityCondition.makeCondition(cond, EntityOperator.AND, 
                    makeFieldPossibleValuesCond("prodCatalogId", config.getProdCatalogIds()));
        }
        return cond;
    }
    
    public EntityCondition makeProdCatalogCategoryCond(String prodCatalogId) {
        EntityCondition cond = EntityCondition.makeCondition("prodCatalogId", prodCatalogId);
        if (config.getProdCatalogCategoryTypeIds() != null) {
            cond = EntityCondition.makeCondition(cond, EntityOperator.AND, 
                    makeFieldPossibleValuesCond("prodCatalogCategoryTypeId", config.getProdCatalogCategoryTypeIds()));
        }
        return cond;
    }
    
    /**
     * If applicable, reorder by the prodCatalogIds in the config, so that order won't change randomly.
     */
    public List<GenericValue> reorderProductStoreCatalogs(List<GenericValue> productStoreCatalogList) {
        if (config.getProdCatalogIds() == null || config.getProdCatalogIds().size() <= 1) {
            return productStoreCatalogList;
        } else {
            return reorderByStringFieldValues(productStoreCatalogList, "prodCatalogId", 
                    config.getProdCatalogIds(), true);
        }
    }
    
    /**
     * If applicable, reorder by the prodCatalogCategoryTypeIds in the config, so that order won't change randomly.
     */
    public List<GenericValue> reorderProdCatalogCategories(List<GenericValue> prodCatalogCategoryList) {
        if (config.getProdCatalogCategoryTypeIds() == null || config.getProdCatalogCategoryTypeIds().size() <= 1) {
            return prodCatalogCategoryList;
        } else {
            return reorderByStringFieldValues(prodCatalogCategoryList, "prodCatalogCategoryTypeId", 
                    config.getProdCatalogCategoryTypeIds(), true);
        }
    }
    
    /**
     * Applies ProdCatalogCategory filters, post-query.
     * NOTE: use EntityConditions instead of this.
     */
    public boolean isApplicableProdCatalogCategory(GenericValue prodCatalogCategory) {
        if (config.getProdCatalogIds() != null && !config.getProdCatalogIds().contains(prodCatalogCategory.getString("prodCatalogId"))) {
            return false;
        }
        if (config.getProdCatalogCategoryTypeIds() != null && !config.getProdCatalogCategoryTypeIds().contains(prodCatalogCategory.getString("prodCatalogCategoryTypeId"))) {
            return false;
        }
        return true;
    }
    
    public void buildSitemapDeepForProductCategories(List<GenericValue> productCategories) throws GeneralException, IOException, URISyntaxException {
        Map<Locale, List<String>> trailNames = new HashMap<>();
        for(Locale locale : getLocales()) {
            trailNames.put(locale, new ArrayList<String>());
        }
        buildSitemapDeepForProductCategoriesInternal(productCategories, trailNames);
    }
    
    protected void buildSitemapDeepForProductCategoriesInternal(List<GenericValue> categoryAssocList, Map<Locale, List<String>> trailNames) throws GeneralException, IOException {
        for (GenericValue categoryAssoc : categoryAssocList) {
            GenericValue category = null;
            if ("ProductCategoryRollup".equals(categoryAssoc.getEntityName())) {
                category = categoryAssoc.getRelatedOne("CurrentProductCategory", useCache);
            } else if ("ProdCatalogCategory".equals(categoryAssoc.getEntityName())) {
                // REMOVED - faster if done by buildSitemapDeep
                //if (!isApplicableProdCatalogCategory(categoryAssoc)) {
                //    continue;
                //}
                category = categoryAssoc.getRelatedOne("ProductCategory", useCache);
            }
            if (category == null) {
                Debug.logError(getLogMsgPrefix()+"Schema error: Could not get related ProductCategory for: " + categoryAssoc, module);
                continue;
            }
            
            // self
            if (config.isDoCategory()) {
                buildSitemapCategory(category, trailNames);
            }
            
            for(Locale locale : locales) {
                // NOTE: this is non-last - cannot reuse the one determined in previous call
                String trailName = urlWorker.getCategoryUrlTrailName(delegator, dispatcher, locale, category, false, useCache);       
                trailNames.get(locale).add(trailName); // no need copy, just remove after
            }
            try {
                // child cats (recursive)
                List<GenericValue> childProductCategoryRollups = EntityQuery.use(delegator).from("ProductCategoryRollup")
                        .where("parentProductCategoryId", category.getString("productCategoryId")).filterByDate().cache(useCache).queryList(); // not need: .orderBy("sequenceNum")
                if (UtilValidate.isNotEmpty(childProductCategoryRollups)) {
                    buildSitemapDeepForProductCategoriesInternal(childProductCategoryRollups, trailNames);
                }
    
                // products
                if (config.isDoProduct()) {
                    List<GenericValue> productCategoryMembers = EntityQuery.use(delegator).from("ProductCategoryMember")
                            .where("productCategoryId", category.getString("productCategoryId")).filterByDate()
                            .cache(useCache).queryList(); // not need: .orderBy("sequenceNum")
                    if (UtilValidate.isNotEmpty(productCategoryMembers)) {
                        for (GenericValue productCategoryMember : productCategoryMembers) {
                            GenericValue product = productCategoryMember.getRelatedOne("Product", useCache);
                            buildSitemapProduct(product, trailNames);
                        }
                    }
                }
            } finally {
                for(Locale locale : locales) {
                    List<String> trail = trailNames.get(locale);
                    trail.remove(trail.size() - 1);
                }
            }
        }
    }
    
    protected void buildSitemapCategory(GenericValue productCategory, Map<Locale, List<String>> trailNames) throws GeneralException, IOException {
        String productCategoryId = productCategory.getString("productCategoryId");
        try {
            
            // TODO: missing multi-locale link support - unclear if library supports
            
            Locale locale = getDefaultLocale();
            List<String> trail = trailNames.get(locale);

            String url = urlWorker.makeCategoryUrlPath(delegator, dispatcher, locale, productCategory, trail, getContextPath(), useCache).toString();
            url = SitemapConfig.concatPaths(getBaseUrl(), applyUrlRewriteRules(url));
            
            if (Debug.verboseOn()) Debug.logVerbose(getLogMsgPrefix()+"Processing category url: " + url, module);
            
            WebSitemapUrl libUrl = buildSitemapLibUrl(url, null);
            getCategoryHandler().addUrl(libUrl);
            stats.categorySuccess++;
        } catch(Exception e) {
            stats.categoryError++;
            Debug.logError(getLogErrorPrefix() + "Cannot build URL for category '" + productCategoryId + "': " + e.getMessage(), module);
        }
    }
    
    protected void buildSitemapProduct(GenericValue product, Map<Locale, List<String>> trailNames) throws GeneralException, IOException {
        if (!config.isIncludeVariant() && "Y".equals(product.getString("isVariant"))) {
            stats.productSkipped++;
            return;
        }
        
        String productId = product.getString("productId");
        try {
            
            // TODO: missing multi-locale link support - unclear if library supports
            
            Locale locale = getDefaultLocale();
            List<String> trail = trailNames.get(locale);
            
            String url = urlWorker.makeProductUrlPath(delegator, dispatcher, locale, product, trail, getContextPath(), useCache).toString();
            url = postprocessUrl(url);

            if (Debug.verboseOn()) Debug.logVerbose(getLogMsgPrefix()+"Processing product url: " + url, module);

            WebSitemapUrl libUrl = buildSitemapLibUrl(url, config.isUseProductLastModDate() ? product.getTimestamp("lastModifiedDate") : null);
            getProductHandler().addUrl(libUrl);
            stats.productSuccess++;
            
            // TODO?: is there need to do variants (not explicitly associated to category)? 
            // usually don't want to advertise the variants unless attached to category for some reason?...
            //if (config.doChildProduct) {  
            //}
        } catch(Exception e) {
            stats.productError++;
            Debug.logError(getLogErrorPrefix() + "Cannot build URL for product '" + productId + "': " + e.getMessage(), module);
        } 
    }

    protected void resetEntityHandlers() {
        this.entityHandlers = createEntityHandlers();
        this.productEntityHandler = entityHandlers.get(CatalogUrlType.PRODUCT);
        this.categoryEntityHandler = entityHandlers.get(CatalogUrlType.CATEGORY);
    }
    
    protected Map<CatalogUrlType, EntityHandler> createEntityHandlers() {
        Map<CatalogUrlType, EntityHandler> entityHandlers = new EnumMap<CatalogUrlType, EntityHandler>(CatalogUrlType.class);
        if (config.isDoProduct()) {
            entityHandlers.put(CatalogUrlType.PRODUCT, new ProductEntityHandler());
        }
        if (config.isDoCategory()) {
            entityHandlers.put(CatalogUrlType.CATEGORY, new CategoryEntityHandler());
        }
        return entityHandlers;
    }
    
    protected EntityHandler getProductHandler() {
        return productEntityHandler;
    }
    
    protected EntityHandler getCategoryHandler() {
        return categoryEntityHandler;
    }
    
    protected abstract class EntityHandler {
        private WebSitemapGenerator wsg = null;
        private List<String> sitemapFiles = new ArrayList<>();
        private long urlCount = 0;
        private long sitemapFileIndex = 0;
        
        public WebSitemapGenerator getWsg() { return wsg; }
        public List<String> getSitemapFiles() { return sitemapFiles; }
        public long getUrlCount() { return urlCount; }
        public long getSitemapFileIndex() { return sitemapFileIndex; }

        public abstract String getTypeFilenamePrefix();
        
        public String getNumberedSitemapFilenamePrefix() {
            return getTypeFilenamePrefix() + sitemapFileIndex;
        }
        
        public String getSitemapFilename() {
            return getNumberedSitemapFilenamePrefix() + "." + config.getSitemapExtension();
        }
        
        public void addUrl(WebSitemapUrl url) throws IOException, URISyntaxException {
            if (config.getSizemapSize() != null && urlCount >= config.getSizemapSize()) {
                commitSitemapFile();
            }
            if (wsg == null) {
                beginSitemapFile();
            }
            wsg.addUrl(url);
            urlCount++;
        }
        
        protected void beginSitemapFile() throws IOException, URISyntaxException {
            sitemapFileIndex++;
            urlCount = 0;
            wsg = getSitemapGenerator(getNumberedSitemapFilenamePrefix());
            Debug.logInfo(getLogMsgPrefix() + "Building: " + getSitemapFilename(), module);
        }
        
        protected void commitSitemapFile() {
            if (wsg == null) return;
            String fn = getSitemapFilename();
            Debug.logInfo(getLogMsgPrefix() + "Writing: " + fn + " (" + urlCount + " entries)", module);
            wsg.write();
            sitemapFiles.add(fn);
            wsg = null;
        }
        
        protected abstract CatalogUrlType getType();
    }

    protected class ProductEntityHandler extends EntityHandler {
        @Override
        protected CatalogUrlType getType() { return CatalogUrlType.PRODUCT; }
        @Override
        public String getTypeFilenamePrefix() { return config.getProductFilePrefix(); }
    }
    
    protected class CategoryEntityHandler extends EntityHandler {
        @Override
        protected CatalogUrlType getType() { return CatalogUrlType.CATEGORY; }
        @Override
        public String getTypeFilenamePrefix() { return config.getCategoryFilePrefix(); }
    }
    
    protected WebSitemapUrl buildSitemapLibUrl(String url, Timestamp lastModDate) throws MalformedURLException {
        WebSitemapUrl.Options opts = new WebSitemapUrl.Options(url);
        if (lastModDate != null) {
            opts.lastMod(new Date(lastModDate.getTime()));
        }
        return opts.build();
    }
    
    protected List<String> getAllSitemapFilenames() {
        List<String> sitemapFiles = new ArrayList<>();
        for(EntityHandler handler : entityHandlers.values()) {
            sitemapFiles.addAll(handler.getSitemapFiles());
        }
        return sitemapFiles;
    }
    
    public void commitSitemaps() {
        for(EntityHandler handler : entityHandlers.values()) {
            handler.commitSitemapFile();
        }
    }
    
    public void commitSitemapsAndIndex() throws IOException, URISyntaxException {
        commitSitemaps();
        generateSitemapIndex(getAllSitemapFilenames());
    }
    
    protected void writeSitemap(List<WebSitemapUrl> urlList, String filePrefix) throws IOException, URISyntaxException {
        WebSitemapGenerator wsg = getSitemapGenerator(filePrefix);
        for(WebSitemapUrl url : urlList){
            wsg.addUrl(url);
        }
        wsg.write();
    }

    public void generateSitemapIndex(List<String> sitemapFilenames) throws IOException, URISyntaxException {
        Debug.logInfo(getLogMsgPrefix()+"Writing index '" + config.getSitemapIndexFile(), module);
        if (sitemapFilenames.isEmpty()) {
            Debug.logWarning(getLogMsgPrefix()+"No sitemap files were produced - sitemap generator may throw error", module);
        }

        File myDir = getSitemapDirFile();
        myDir.mkdirs();
        
        File myFile = new File(myDir, config.getSitemapIndexFile());
        try {
            myFile.createNewFile();
        } catch (IOException e) {
            Debug.logInfo(getLogMsgPrefix()+"Index file '" + myFile.toString() + "' may already exist; replacing", module);
            // ignore if file already exists
        }
        
        SitemapIndexGenerator sig = new SitemapIndexGenerator(getBaseUrl(), myFile);
        for(String url : sitemapFilenames){
            sig.addUrl(getSitemapFileLink(url));
        }
        sig.write();
        
        Debug.logInfo(getLogMsgPrefix()+"Done writing index '" + config.getSitemapIndexFile() + "'", module);
    }
    
    public String getSitemapFileLink(String filename) {
        return SitemapConfig.concatPaths(getBaseUrl(), getSitemapContextPath(), config.getSitemapDirPath(), filename);
    }
    
    public String getSitemapIndexFileLink() {
        return getSitemapFileLink(config.getSitemapIndexFile());
    }

    /**
     * Use urlrewritefilter rules to convert urls - emulates urlrewritefilter - just like the original url would be
     * WARN: emulation only - see UrlRewriteConf for issues.
     */
    public String applyUrlRewriteRules(String url) {
        if (url == null) return "";
        if (getUrlRewriteConf() == null) return url;
        return getUrlRewriteConf().processOutboundUrl(url);
    }
    
    /**
     * Applies URL rewrite rules and appends baseUrl, as applicable.
     */
    public String postprocessUrl(String url) {
        return SitemapConfig.concatPaths(getBaseUrl(), applyUrlRewriteRules(url));
    }
    
    protected String getLogMsgPrefix() {
        return logPrefix+"Website '" + webSiteId + "': ";
    }
    
    protected String getLogErrorPrefix() {
        return logPrefix+"Error generating sitemap for website '" + webSiteId + "': ";
    }
    
    protected String getLogErrorMsg(Throwable t) {
        return getLogErrorPrefix() + t.getMessage();
    }
    
    // TODO: move (generic)
    static EntityCondition makeFieldPossibleValuesCond(String fieldName, Collection<?> values) {
        //if (values == null || values.isEmpty()) return null;
        List<EntityCondition> condList = new ArrayList<>(values.size());
        for(Object value : values) {
            condList.add(EntityCondition.makeCondition(fieldName, value));
        }
        return EntityCondition.makeCondition(condList, EntityOperator.OR);
    }
    
    // TODO: move (generic)
    static List<GenericValue> reorderByStringFieldValues(List<GenericValue> values, String fieldName, Collection<String> orderedFieldValues, boolean unknownLast) {
        if (values == null || values.isEmpty() || orderedFieldValues == null || orderedFieldValues.isEmpty()) return values;

        Map<String, List<GenericValue>> valuesByType = new HashMap<>();
        for(String typeId : orderedFieldValues) { // simplifies the code
            valuesByType.put(typeId, new ArrayList<GenericValue>());
        }
        List<GenericValue> unknownTypes = new ArrayList<>();
        
        for(GenericValue cat : values) {
            String typeId = cat.getString(fieldName);
            
            List<GenericValue> subList = valuesByType.get(typeId);
            if (subList != null) {
                subList.add(cat);
            } else {
                unknownTypes.add(cat);
            }
        }
        
        List<GenericValue> result = new ArrayList<>();
        if (!unknownLast) result.addAll(unknownTypes);
        
        for(String typeId : orderedFieldValues) {
            result.addAll(valuesByType.get(typeId));
        }
        
        if (unknownLast) result.addAll(unknownTypes);
        return result;
    }
}
