<#--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->
  <@section id="partyContent" title=(pcntTitle!uiLabelMap.PartyContent) menuContent=(pcntMenuCnt!"")><#-- SCIPIO: allow title override -->
    
    <@render resource=(pcntCntListLoc!"component://party/widget/partymgr/ProfileScreens.xml#ContentList")/>
      
  <#if (pcntNoAttach!false) != true>
    <@section title=(pcntAttachTitle!uiLabelMap.PartyAttachContent) id="partyAttachContent" menuContent=(pcntAttachMenuCnt!"")>
      <form id="uploadPartyContent" method="post" enctype="multipart/form-data" action="<@ofbizUrl uri=(pcntUploadUri!"uploadPartyContent") escapeAs='html'/>">
        <input type="hidden" name="dataCategoryId" value="PERSONAL"/>
        <input type="hidden" name="contentTypeId" value="DOCUMENT"/>
        <input type="hidden" name="statusId" value="CTNT_PUBLISHED"/>
        <input type="hidden" name="partyId" value="${partyId}" id="contentPartyId"/>
        <#-- SCIPIO: extra params -->
        <#if pcntExtraParams?has_content>
          <#assign pcntExtraParams = toSimpleMap(pcntExtraParams)>
          <#list pcntExtraParams?keys as paramName>
            <input type="hidden" name="${paramName}" value="${escapeVal(pcntExtraParams[rawString(paramName)]!, 'html')}"/>
          </#list>
        </#if>

        <@field type="file" label=uiLabelMap.PartyAttachFile name="uploadedFile" required=true class="+error" size=25 />
        
        <#assign pcntDefPartyContentTypeId = rawString(pcntDefPartyContentTypeId!"INTERNAL")> 
       
      <#if pcntPartyContentTypeId?has_content>
        <input type="hidden" name="partyContentTypeId" value="${pcntPartyContentTypeId}"/>
      <#else>
        <@field type="select" label=uiLabelMap.PartyContentType name="partyContentTypeId" required=true class="+error">
          <#-- preselect "INTERNAL"
          <option value="">${uiLabelMap.PartySelectPurpose}</option>-->
          <#list partyContentTypes as partyContentType>
            <option value="${partyContentType.partyContentTypeId}"<#if pcntDefPartyContentTypeId==rawString(partyContentType.partyContentTypeId)> selected="selected"</#if>>${partyContentType.get("description", locale)!(partyContentType.partyContentTypeId)}</option>
          </#list>
        </@field>
      </#if>

      <#if (pcntAllowPublic!true) == true>
        <@field type="select" label=uiLabelMap.PartyIsPublic name="isPublic">
          <option value="N">${uiLabelMap.CommonNo}</option>
          <option value="Y">${uiLabelMap.CommonYes}</option>
        </@field>
      <#else>
        <input type="hidden" name="isPublic" value="N"/>
      </#if>
       
        <#-- SCIPIO: 2018-04-10: obscure, impossible to understand what this does here
        <@field type="select" label=uiLabelMap.PartySelectRole name="roleTypeId">
          <#list roles as role>
            <option value="${role.roleTypeId}" <#if role.roleTypeId == "_NA_">selected="selected"</#if>>${role.get("description", locale)!(role.roleTypeId)}</option>
          </#list>
        </@field>-->
   
        <#assign progressOptions = {
            "formSel" : "#uploadPartyContent",
            "progBarId" : "upc_progress_bar",
            "progTextBoxId" : "upcProgressMsgBox",
            
            "msgContainerParentSel" : "#partyAttachContent_content",
            "msgContainerInsertMode" : "prepend",
            
            "iframeParentSel" : "#partyContent",
            "expectedResultContainerSel" : "#partyContentList",
            "errorResultContainerSel" : "#main-${styles.alert_wrap!}",
            "errorResultAddWrapper" : false,

            "resultContentReplace" : true,
            "contentContainerSel" : "#partyContentList",
            "resultContentContainerSel" : "#partyContentList",
            
            "submitHook" : "validate"
        }>
        <@field type="submitarea" progressOptions=progressOptions>
          <input type="submit" value="${uiLabelMap.CommonUpload}" class="${styles.link_run_sys!} ${styles.action_import!}" />
        </@field>
      </form>
    </@section>
  </#if>
    
  </@section>
