<#-- SCIPIO: SETUP interactive catalog tree implementation -->

<#include "component://setup/webapp/setup/common/common.ftl">
<#include "component://product/webapp/catalog/catalog/tree/treecommon.ftl">

<@alert type="warning">WARNING: WORK-IN-PROGRESS</@alert>

<@script>
    function setupShowFormActivatedCallback(form, ai) {
        <#-- special: if this is a category form (check for isCreateCategory hidden input present),
            adjust field visibility for top vs nested cat -->
        if (jQuery('input[name=isCreateCategory]', form).length) {
            refreshScfFieldVisibility(form);
        }
        
        setupControlMenu.setSubmitFormId(form.prop('id'));
    };
</@script>

<#assign ectCallbacks = {
    "showFormActivated": wrapRawScript("setupShowFormActivatedCallback")
}>
<#assign ectAllHideShowFormIds = [
    "ect-newcatalog", "ect-editcatalog", "ect-newcategory", "ect-editcategory", "ect-newcategory"
]>
<#assign ectActionProps = {
    "default": {
        "newcatalog": {
            "type": "form",
            "mode": "show",
            "id": "ect-newcatalog",
            "defaultParams": wrapRawScript("function() { return defaultCatalogParams; }")
        }
    },
    "catalog": {
        "edit": {
            "type": "form",
            "mode": "show",
            "id": "ect-editcatalog" <#-- NOTE: this can be ancestor container of form, both work (uses first descendent form) -->
            <#-- paramNames* is a preprocess step for easy param renaming before going into link/form
            "paramNames": {"productStoreId": "myProductStoreIdParam" }
            "paramNamesMode": "explicit"|"default"-->
            <#-- replaces the entire default form populate script, must be a single function (ai = ActionInfo object)
            "populateForm": wrapRawScript('function(form, params, ai) { alert("preventing form populate"); return false; }')-->
            <#-- individual form field populate handlers (return false to prevent default/common behavior)
            "populateFormFields": {
                "prodCatalogId": wrapRawScript('function(elem, name, value, form, params, ai) { alert("ignoring this field"); return false; }')
            }-->
        },
        "removeassoc": {
            "type": "form",
            "mode": "submit",
            "confirmMsg": rawLabel('CommonConfirmDeleteRecordAssocPermanent'),
            "id": "ect-removecatalogassoc-form"
        },
        "remove": {
            "type": "form",
            "mode": "submit",
            "confirmMsg": rawLabel('CommonConfirmDeleteRecordPermanent'),
            "id": "ect-removecatalog-form"
        },
        "newcategory": {
            "type": "form",
            "mode": "show",
            "id": "ect-newcategory",
            "defaultParams": wrapRawScript("function() { return defaultCategoryParams; }")
        },
        "manage": {
            "type": "link",
            "target": "_blank",
            "url": makeOfbizInterWebappUrl({"uri":'/catalog/control/EditProdCatalog', "extLoginKey":true}),
            "paramNames": {"prodCatalogId": true },
            "paramNamesMode": "explicit"
        }
    },
    "category": {
        "edit": {
            "type": "form",
            "mode": "show",
            "id": "ect-editcategory"
        },
        "removeassoc": {
            "type": "form",
            "mode": "submit",
            "confirmMsg": rawLabel('CommonConfirmDeleteRecordAssocPermanent'),
            "id": "ect-removecategoryassoc-form"
        },
        "remove": {
            "type": "form",
            "mode": "submit",
            "confirmMsg": rawLabel('CommonConfirmDeleteRecordPermanent'),
            "id": "ect-removecategory-form"
        },
        "newcategory": {
            "type": "form",
            "mode": "show",
            "id": "ect-newcategory",
            "defaultParams": wrapRawScript("function() { return defaultCategoryParams; }")
        },
        "manage": {
            "type": "link",
            "target": "_blank",
            "url": makeOfbizInterWebappUrl({"uri":'/catalog/control/EditCategory', "extLoginKey":true}),
            "paramNames": {"productCategoryId": true },
            "paramNamesMode": "explicit"
        }
    },
    "product": {
        "manage": {
            "type": "link",
            "target": "_blank",
            "url": makeOfbizInterWebappUrl({"uri":'/catalog/control/ViewProduct', "extLoginKey":true}),
            "paramNames": {"productId": true },
            "paramNamesMode": "explicit"
        }
    }
}>

<#macro ectExtrasArea extraArgs...>
  <@section><#-- title=uiLabelMap.CommonDisplayOptions -->
    <@form action=makeOfbizUrl("setupCatalog") method="get">
      <@defaultWizardFormFields/>   
    <@fieldset title=uiLabelMap.CommonDisplayOptions collapsed=true> 
      <@field type="input" name="setupEctMaxProductsPerCat" label=uiLabelMap.ProductMaxProductsPerCategory 
        value=(ectMaxProductsPerCat!) tooltip=uiLabelMap.SetupLargeStoreSlowSettingWarning/>
      <@field type="submit"/>
    </@fieldset>
    </@form>
  </@section>
</#macro>

<#-- CORE INCLUDE -->
<#include "component://product/webapp/catalog/catalog/tree/EditCatalogTreeCore.ftl">
