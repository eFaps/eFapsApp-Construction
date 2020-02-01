/*
 *  Copyright 2003 - 2019 The eFaps Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.efaps.esjp.construction;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.eql.builder.Print;
import org.efaps.esjp.ci.CIConstruction;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.sales.Calculator;
import org.efaps.esjp.sales.document.Quotation;
import org.efaps.esjp.ui.structurbrowser.StandartStructurBrowser;
import org.efaps.ui.wicket.models.field.AbstractUIField;
import org.efaps.ui.wicket.models.objects.UIStructurBrowser;
import org.efaps.util.EFapsException;

/**
 * @author Jan Moxter
 */
@EFapsUUID("86a81b09-7b4d-43c3-84d6-bc72b5352ad4")
@EFapsApplication("eFapsApp-Construction")
public abstract class CostEstimateStucturBrowser_Base
    extends StandartStructurBrowser
{

    private final String KEYMAP2SORT = "eFaps_Session_Map2SortStructureBrowser";

    @Override
    protected Return internalExecute(final Parameter _parameter)
        throws EFapsException
    {
        Context.getThreadContext().removeSessionAttribute(KEYMAP2SORT);
        final Map<Instance, Integer> map2sort = new HashMap<Instance, Integer>();
        final Return ret = new Return();
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);

        final Boolean editGr = Boolean.parseBoolean((String) properties.get("EditGroup"));
        final String typeStr = (String) properties.get("Type");

        final Map<Instance, Boolean> tree = new LinkedHashMap<Instance, Boolean>();
        if (_parameter.getInstance() != null) {
            final String[] posStr = (String[]) Context.getThreadContext().getSessionAttribute("selectedPositionGroup");

            QueryBuilder queryBldr = new QueryBuilder(CIConstruction.PositionGroupRoot);
            if (editGr && posStr != null) {
                final Instance instPos = Instance.get(posStr[0]);
                queryBldr = new QueryBuilder(Type.get(typeStr));
                queryBldr.addWhereAttrEqValue(CIConstruction.PositionGroupAbstract.ID, instPos);
            }
            queryBldr.addWhereAttrEqValue(CIConstruction.PositionGroupAbstract.DocumentAbstractLink,
                            _parameter.getInstance());
            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.addAttribute(CIConstruction.PositionGroupAbstract.Order);
            multi.execute();

            while (multi.next()) {
                tree.put(multi.getCurrentInstance(), null);
                map2sort.put(multi.getCurrentInstance(),
                                multi.<Integer>getAttribute(CIConstruction.PositionGroupAbstract.Order));
            }
        }
        if (!map2sort.isEmpty()) {
            Context.getThreadContext().setSessionAttribute(KEYMAP2SORT, map2sort);
        }
        ret.put(ReturnValues.VALUES, tree);
        return ret;
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @param _check check only?
     * @return map with instances
     * @throws EFapsException on error
     */
    @Override
    @SuppressWarnings("unchecked")
    protected Map<Instance, Boolean> getChildren(final Parameter _parameter,
                                                 final boolean _check)
        throws EFapsException
    {
        final Map<Instance, Boolean> ret = new LinkedHashMap<Instance, Boolean>();
        if (_parameter.getInstance() != null) {
            if (_parameter.getInstance().getType().isKindOf(CIConstruction.PositionGroupRoot.getType())
                            || _parameter.getInstance().getType().isKindOf(CIConstruction.PositionGroupNode.getType())) {
                final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);

                final Boolean includeChildTypes = properties.containsKey("includeChildTypes")
                                ? Boolean.parseBoolean((String) properties.get("includeChildTypes"))
                                : true;

                final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.PositionGroupNode);
                queryBldr.addWhereAttrEqValue(CIConstruction.PositionGroupNode.ParentGroupLink,
                                _parameter.getInstance().getId());
                final InstanceQuery query = queryBldr.getQuery();
                query.setIncludeChildTypes(includeChildTypes);
                final MultiPrintQuery multi = queryBldr.getPrint();
                multi.addAttribute(CIConstruction.PositionGroupAbstract.Order);
                multi.execute();

                while (multi.next()) {
                    ret.put(multi.getCurrentInstance(), null);
                    Map<Instance, Integer> map2sort = new HashMap<Instance, Integer>();
                    if (Context.getThreadContext().getSessionAttribute(KEYMAP2SORT) != null) {
                        map2sort = (Map<Instance, Integer>) Context.getThreadContext()
                                        .getSessionAttribute(KEYMAP2SORT);
                    }
                    map2sort.put(multi.getCurrentInstance(),
                                    multi.<Integer>getAttribute(CIConstruction.PositionGroupRoot.Order));
                    if (!map2sort.isEmpty()) {
                        Context.getThreadContext().setSessionAttribute(KEYMAP2SORT, map2sort);
                    }
                }
            }
        }
        return ret;
    }

    /**
     * Method to check if an instance allows children. It is used in the tree to
     * determine "folder" or an "item" must be rendered and if the
     * checkForChildren method must be executed.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return Return with true or false
     * @throws EFapsException on error
     */
    @Override
    protected Return allowChildren(final Parameter _parameter)
    {
        final Return ret = new Return();
        if (_parameter.getInstance() == null || !_parameter.getInstance().isValid()
                        || !_parameter.getInstance().getType().equals(CIConstruction.PositionGroupItem.getType())) {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }

    /**
     * Method is called after the insert etc. of a new node in edit mode to get
     * a JavaScript that will be appended to the AjaxTarget.
     *
     * @param _parameter as passed from eFaps API.
     * @return Return with SNIPPLET
     * @throws EFapsException on error
     */
    @Override
    protected Return getJavaScript4Target(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final StringBuilder js = new StringBuilder();
        final List<Calculator> calcs = new Quotation().analyseTable(_parameter, null);
        final DecimalFormat formater = NumberFormatter.get().getFormatter();
        formater.setMinimumFractionDigits(10);
        formater.setMaximumFractionDigits(10);
        int i = 0;
        for (final Calculator calc : calcs) {
            if (!calc.isEmpty()) {
                js.append("eFapsSetFieldValue(").append(i).append(",'netPrice','")
                                .append(calc.getNetPriceFmtStr()).append("');")
                                .append("eFapsSetFieldValue(").append(i).append(",'discountNetUnitPrice','")
                                .append(calc.getDiscountNetUnitPriceFmtStr()).append("');");
            }
            i++;
        }
        ret.put(ReturnValues.SNIPLETT, js.toString());
        return ret;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Return sort(final Parameter _parameter)
        throws EFapsException
    {
        final UIStructurBrowser strBro = (UIStructurBrowser) _parameter.get(ParameterValues.CLASS);

        Collections.sort(strBro.getChildren(), (_structurBrowser1,
         _structurBrowser2) -> {
            Integer value1 = 0;
            Integer value2 = 0;
            try {
                Map<Instance, Integer> map2sort = new HashMap<Instance, Integer>();
                if (Context.getThreadContext()
                                .getSessionAttribute(KEYMAP2SORT) != null) {
                    map2sort = (Map<Instance, Integer>) Context.getThreadContext()
                                    .getSessionAttribute(KEYMAP2SORT);
                }

                if (map2sort.containsKey(_structurBrowser1.getInstance())) {
                    value1 = map2sort.get(_structurBrowser1.getInstance());
                }
                if (map2sort.containsKey(_structurBrowser2.getInstance())) {
                    value2 = map2sort.get(_structurBrowser2.getInstance());
                }
            } catch (final EFapsException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            return value1.compareTo(value2);
        });
        return new Return();
    }

    @Override
    protected Return checkHideColumn4Row(final Parameter _parameter)
        throws EFapsException
    {
        final UIStructurBrowser strBrws = (UIStructurBrowser) _parameter.get(ParameterValues.CLASS);
        for (final AbstractUIField uiField : strBrws.getColumns()) {
            if (strBrws.isAllowChildren() && !strBrws.isBrowserField(uiField)
                            && !"sumsTotal4View".equals(uiField.getFieldConfiguration().getName())
                            && !"code".equals(uiField.getFieldConfiguration().getName())) {
                uiField.setHide(true);
            }
        }
        return new Return();
    }

    @Override
    protected void add2ChildrenQuery(final Print _print)
    {
        _print.attribute(CISales.PositionGroupAbstract.Order.name).as("ORDER");
        _print.orderBy("ORDER");
    }

}
