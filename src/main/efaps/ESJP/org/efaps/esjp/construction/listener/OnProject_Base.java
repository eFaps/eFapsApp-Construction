/*
 * Copyright 2003 - 2019 The eFaps Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.efaps.esjp.construction.listener;

import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.text.StringEscapeUtils;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.ui.Command;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIConstruction;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.AbstractCommon;
import org.efaps.esjp.common.util.InterfaceUtils;
import org.efaps.esjp.common.util.InterfaceUtils_Base.DojoLibs;
import org.efaps.esjp.construction.util.Construction;
import org.efaps.esjp.projects.listener.IOnProject;
import org.efaps.util.EFapsException;


/**
 * The Class OnProject_Base.
 *
 * @author The eFaps Team
 */
@EFapsUUID("d19758db-dda9-49ad-bdb6-a2bd91fb3b45")
@EFapsApplication("eFapsApp-Construction")
public abstract class OnProject_Base
    extends AbstractCommon
    implements IOnProject
{

    @Override
    public void updateField4Project(final Parameter _parameter,
                                    final Instance _projectInstance,
                                    final Map<String, Object> _uiMap)
        throws EFapsException
    {
        if (_projectInstance != null && _projectInstance.isValid()
                        && _parameter.get(ParameterValues.CALL_CMD) instanceof Command
                        && rel2CostEstimate(_parameter)) {
            final String projDataField = getProperty(_parameter, "Project_DataField", "projectData");

            final QueryBuilder attrQueryBldr = new QueryBuilder(CIConstruction.ProjectService2CostEstimate);
            attrQueryBldr.addWhereAttrEqValue(CIConstruction.ProjectService2CostEstimate.FromLink, _projectInstance.getId());
            final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CIConstruction.ProjectService2CostEstimate.ToLink);

            final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.CostEstimateRealization);
            queryBldr.addWhereAttrInQuery(CIConstruction.CostEstimateAbstract.ID, attrQuery);
            queryBldr.addWhereAttrNotEqValue(CIConstruction.CostEstimateAbstract.StatusAbstract, Status.find(
                            CIConstruction.CostEstimateStatus.Canceled));
            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.addAttribute(CIConstruction.CostEstimateAbstract.Name, CIConstruction.CostEstimateAbstract.Annotation);
            multi.execute();
            final Map<String, Instance> values = new TreeMap<>();
            while (multi.next()) {
                values.put(multi.<String>getAttribute(CIConstruction.CostEstimateAbstract.Name) + " - " + multi
                                .<String>getAttribute(CIConstruction.CostEstimateAbstract.Annotation), multi.getCurrentInstance());
            }

            final StringBuilder js = new StringBuilder()
                            .append("  var dataNode = query(\"[name=projectData]\")[0];\n")
                            .append("  domConstruct.empty(dataNode);\n")
                            .append("domConstruct.create(\"span\", null, dataNode).innerHTML=  \"")
                            .append(StringEscapeUtils.escapeEcmaScript((String) _uiMap.get(projDataField)))
                            .append("\";\n")
                            .append("domConstruct.create(\"br\", null, dataNode);\n");
            boolean first = true;
            boolean first2 = true;
            for (final Entry<String, Instance> entry : values.entrySet()) {
                js.append("domConstruct.create(\"input\", { type: \"radio\", name: \"costEstimate\", value:\"")
                    .append(entry.getValue().getOid())
                    .append("\",id:\"").append(entry.getValue().getOid())
                    .append("\" ");
                if (first) {
                    js.append(", checked:\"checked\"");
                }
                js.append("}, dataNode);\n")
                    .append("domConstruct.create(\"label\", { for:\"").append(entry.getValue().getOid())
                        .append("\"}, dataNode).innerHTML=  \"")
                        .append(StringEscapeUtils.escapeEcmaScript(entry.getKey())).append("\";\n")
                    .append("domConstruct.create(\"br\", null, dataNode);\n");

                if (rel2EntrySheet(_parameter)) {
                    final QueryBuilder ce2esAttrQueryBldr = new QueryBuilder(CIConstruction.CostEstimate2EntrySheet);
                    ce2esAttrQueryBldr.addWhereAttrEqValue(CIConstruction.CostEstimate2EntrySheet.FromLink, entry.getValue());

                    final QueryBuilder ce2esQueryBldr = new QueryBuilder(CIConstruction.EntrySheet);
                    ce2esQueryBldr.addWhereAttrInQuery(CIConstruction.EntrySheet.ID,
                                    ce2esAttrQueryBldr.getAttributeQuery(CIConstruction.CostEstimate2EntrySheet.ToLink));
                    ce2esQueryBldr.addWhereAttrNotEqValue(CIConstruction.EntrySheet.StatusAbstract, Status.find(
                                    CIConstruction.EntrySheetStatus.Canceled));

                    final MultiPrintQuery ce2esPrint = ce2esQueryBldr.getPrint();
                    ce2esPrint.addAttribute(CIConstruction.EntrySheet.Name, CIConstruction.EntrySheet.Description);
                    ce2esPrint.execute();
                    final Map<String, Instance> esValues = new TreeMap<>();
                    while (ce2esPrint.next()) {
                        esValues.put(ce2esPrint.<String>getAttribute(CIConstruction.EntrySheet.Name) + " - "
                                        + ce2esPrint .<String>getAttribute(CIConstruction.EntrySheet.Description),
                                        ce2esPrint.getCurrentInstance());
                    }
                    js.append("var dd = domConstruct.create(\"dd\", { rel:\"").append(entry.getValue().getOid())
                        .append("\"");
                    if (!first) {
                        js.append(", style:\"display:none\"");
                    }
                    js.append(" }, dataNode);\n");
                    for (final Entry<String, Instance> esEntry : esValues.entrySet()) {
                        js.append("domConstruct.create(\"input\", { type: \"radio\", name: \"costSheet\", value:\"")
                            .append(esEntry.getValue().getOid())
                            .append("\",id:\"").append(esEntry.getValue().getOid())
                            .append("\" ");
                        if (first2) {
                            js.append(", checked:\"checked\"");
                            first2 = false;
                        }
                        js.append("}, dd);\n")
                            .append("domConstruct.create(\"label\", { for:\"").append(esEntry.getValue().getOid())
                            .append("\"")
                            .append("}, dd).innerHTML= \"")
                                .append(StringEscapeUtils.escapeEcmaScript(esEntry.getKey())).append("\";\n")
                            .append("domConstruct.create(\"br\", null, dd);\n")
                            .append("");
                    }
                }
                if (first) {
                    first = false;
                }
            }
            js.append("query(\"[name=costEstimate]\",dataNode).on(\"change\", function(_event) {\n")
                .append("var first = true;\n")
                .append("query(\"dd\",dataNode).forEach(function(_node) {\n")
                .append("if (_event.target.value == domAttr.get(_node, \"rel\")) {\n")
                .append("domStyle.set(_node, \"display\", \"\");\n")
                .append("if (first) {\n")
                .append("first=false;\n")
                .append("domAttr.set(query(\"input\",_node)[0], \"checked\", true);")
                .append("")
                .append("}")
                .append("} else {\n")
                .append("domStyle.set(_node, \"display\", \"none\");\n")
                .append("}\n")
                .append("});\n")
                .append("});\n")
                .append("");

            InterfaceUtils.appendScript4FieldUpdate(_uiMap, InterfaceUtils.wrapInDojoRequire(_parameter, js,
                            DojoLibs.QUERY, DojoLibs.DOMCONSTRUCT, DojoLibs.ON, DojoLibs.DOMATTR, DojoLibs.DOMSTYLE));
        }
    }

    @Override
    public CharSequence add2JavaScript4Project4Document(final Parameter _parameter,
                                                        final Instance _docInstance,
                                                        final Instance _projectInstance)
        throws EFapsException
    {
        final StringBuilder ret = new StringBuilder();
        // only if create from a ProductRequest
        if (_docInstance.getType().isCIType(CISales.ProductRequest)) {
            final StringBuilder js = new StringBuilder();
            js.append("  var dataNode = query(\"[name=projectData]\")[0];\n");
            if (rel2CostEstimate(_parameter)) {
                final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.CostEstimate2ProductRequest);
                queryBldr.addWhereAttrEqValue(CIConstruction.CostEstimate2ProductRequest.ToLink, _docInstance);
                final MultiPrintQuery multi = queryBldr.getPrint();
                final SelectBuilder selCE = SelectBuilder.get().linkto(CIConstruction.CostEstimate2ProductRequest.FromLink);
                final SelectBuilder selCEInst = new SelectBuilder(selCE).instance();
                final SelectBuilder selCEName = new SelectBuilder(selCE).attribute(CIConstruction.CostEstimateAbstract.Name);
                final SelectBuilder selCEAnno = new SelectBuilder(selCE).attribute(
                                CIConstruction.CostEstimateAbstract.Annotation);
                multi.addSelect(selCEInst, selCEName, selCEAnno);
                multi.execute();
                if (multi.next()) {
                    final Instance ceInst = multi.getSelect(selCEInst);
                    final String label = multi.<String>getSelect(selCEName) + " - "
                                    + multi.<String>getSelect(selCEAnno);
                    js.append("domConstruct.create(\"input\", { type: \"radio\", name: \"costEstimate\", value:\"")
                        .append(ceInst.getOid()).append("\",id:\"").append(ceInst.getOid())
                        .append("\" ").append(", checked:\"checked\"").append("}, dataNode);\n")
                        .append("domConstruct.create(\"label\", { for:\"").append(ceInst.getOid())
                        .append("\"}, dataNode).innerHTML=  \"")
                        .append(StringEscapeUtils.escapeEcmaScript(label)).append("\";\n")
                        .append("domConstruct.create(\"br\", null, dataNode);\n");
                }
            }
            if (rel2EntrySheet(_parameter)) {
                final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.EntrySheet2ProductRequest);
                queryBldr.addWhereAttrEqValue(CIConstruction.EntrySheet2ProductRequest.ToLink, _docInstance);
                final MultiPrintQuery multi = queryBldr.getPrint();
                final SelectBuilder selES = SelectBuilder.get().linkto(CIConstruction.EntrySheet2ProductRequest.FromLink);
                final SelectBuilder selESInst = new SelectBuilder(selES).instance();
                final SelectBuilder selESName = new SelectBuilder(selES).attribute(CIConstruction.EntrySheet.Name);
                final SelectBuilder selESDesc = new SelectBuilder(selES).attribute(
                                CIConstruction.EntrySheet.Description);
                multi.addSelect(selESInst, selESName, selESDesc);
                multi.execute();
                if (multi.next()) {
                    final Instance esInst = multi.getSelect(selESInst);
                    final String label = multi.<String>getSelect(selESName) + " - "
                                    + multi.<String>getSelect(selESDesc);
                    js.append("domConstruct.create(\"input\", { type: \"radio\", name: \"costSheet\", value:\"")
                        .append(esInst.getOid()).append("\",id:\"").append(esInst.getOid())
                        .append("\" ").append(", checked:\"checked\"").append("}, dataNode);\n")
                        .append("domConstruct.create(\"label\", { for:\"").append(esInst.getOid())
                        .append("\"}, dataNode).innerHTML=  \"")
                        .append(StringEscapeUtils.escapeEcmaScript(label)).append("\";\n")
                        .append("domConstruct.create(\"br\", null, dataNode);\n");
                }
            }
            ret.append(InterfaceUtils.wrapInDojoRequire(_parameter, js,
                            DojoLibs.QUERY, DojoLibs.DOMCONSTRUCT, DojoLibs.ON, DojoLibs.DOMATTR, DojoLibs.DOMSTYLE));
        }
        return ret;
    }

    /**
     * Rel2 cost estimate.
     *
     * @param _parameter the _parameter
     * @return true, if successful
     * @throws EFapsException on error
     */
    protected boolean rel2CostEstimate(final Parameter _parameter)
        throws EFapsException
    {
        final Type type;
        if (_parameter.get(ParameterValues.CALL_CMD) == null) {
            final String input = _parameter.getParameterValue("selectedDoc");
            type = Instance.get(_parameter.getParameterValue(input)).getType();
        } else {
            type = ((Command) _parameter.get(ParameterValues.CALL_CMD)).getTargetCreateType();
        }
        return type != null && (type.isCIType(CISales.ProductRequest) && Construction.COSTESTIMATERELPRODREQ.get()
                    || type.isCIType(CISales.OrderOutbound) && Construction.COSTESTIMATERELORDEROUT.get()
                    || type.isCIType(CISales.ServiceOrderOutbound) && Construction.COSTESTIMATERELSRVORDEROUT.get());
    }

    /**
     * Rel2 entry sheet.
     *
     * @param _parameter the _parameter
     * @return true, if successful
     * @throws EFapsException on error
     */
    protected boolean rel2EntrySheet(final Parameter _parameter)
        throws EFapsException
    {
        final Type type;
        if (_parameter.get(ParameterValues.CALL_CMD) == null) {
            final String input = _parameter.getParameterValue("selectedDoc");
            type = Instance.get(_parameter.getParameterValue(input)).getType();
        } else {
            type = ((Command) _parameter.get(ParameterValues.CALL_CMD)).getTargetCreateType();
        }
        return type != null && (type.isCIType(CISales.ProductRequest) && Construction.ENTRYSHEETRELPRODREQ.get()
                    || type.isCIType(CISales.OrderOutbound) && Construction.ENTRYSHEETRELORDEROUT.get()
                    || type.isCIType(CISales.ServiceOrderOutbound) && Construction.ENTRYSHEETRELSRVORDEROUT.get());
    }

    @Override
    public int getWeight()
    {
        return 0;
    }
}
