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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.efaps.admin.datamodel.Attribute;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Context;
import org.efaps.db.Delete;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.ci.CIConstruction;
import org.efaps.esjp.ci.CIFormConstruction;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.esjp.construction.util.Construction;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.util.EFapsException;

/**
 * @author Jan Moxter
 */
@EFapsUUID("3164bdc0-8bcb-484d-928b-56ae2981c520")
@EFapsApplication("eFapsApp-Construction")
public abstract class CostEstimatePosGroup_Base
{

    /**
     * Create a new Root for the position groups. They will always by added add
     * the end.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return new empty Return
     * @throws EFapsException on error
     */
    public Return createRoot(final Parameter _parameter)
        throws EFapsException
    {
        int order = -1;
        final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionGroupAbstract);
        queryBldr.addWhereAttrEqValue(CISales.PositionGroupAbstract.DocumentAbstractLink, _parameter.getInstance());
        queryBldr.addOrderByAttributeDesc(CISales.PositionGroupAbstract.Order);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.setEnforceSorted(true);
        multi.addAttribute(CISales.PositionGroupAbstract.Order);
        multi.execute();
        if (multi.next()) {
            order = multi.<Integer>getAttribute(CISales.PositionGroupAbstract.Order);
        }

        final String nameStr = _parameter.getParameterValue(
                        CIFormConstruction.Construction_CostEstimateGroupForm.name.name);
        final String[] nameArr = validateNames(_parameter, nameStr.split("\n"));
        for (int i = 0; i < nameArr.length; i++) {
            order++;
            final Insert insert = new Insert(CIConstruction.PositionGroupRoot);
            insert.add(CIConstruction.PositionGroupAbstract.DocumentAbstractLink, _parameter.getInstance());
            insert.add(CIConstruction.PositionGroupAbstract.Name, nameArr[i]);
            insert.add(CIConstruction.PositionGroupAbstract.Level, 1);
            insert.add(CIConstruction.PositionGroupAbstract.Order, order);
            insert.execute();
        }
        return new Return();
    }

    /**
     * Create a Group. Insert with the correct "Order" and move the others one
     * down.
     *
     * @param _parameter as parameter from eFaps API.
     * @return new Return
     * @throws EFapsException on error.
     */
    public Return createGroup(final Parameter _parameter)
        throws EFapsException
    {
        String[] oids = new String[0];
        if (Context.getThreadContext().containsSessionAttribute("parentOID")
                        && Context.getThreadContext().getSessionAttribute("parentOID") != null) {
            oids = (String[]) Context.getThreadContext().getSessionAttribute("parentOID");
            Context.getThreadContext().removeSessionAttribute("parentOID");
        }
        if (oids.length > 0) {
            final Instance instance = Instance.get(oids[0]);
            if (InstanceUtils.isType(instance, CIConstruction.PositionGroupNode)
                            || InstanceUtils.isType(instance, CIConstruction.PositionGroupRoot)) {

                final String names = _parameter.getParameterValue(CIFormConstruction.Construction_CostEstimateGroupForm.name.name) == null
                                ? _parameter.getParameterValue(CIFormConstruction.Construction_CostEstimateGroupItemForm.productDesc.name)
                                : _parameter.getParameterValue(CIFormConstruction.Construction_CostEstimateGroupForm.name.name);
                final String[] nameArr = validateNames(_parameter, names.split("\n"));
                final boolean isItem = _parameter
                                .getParameterValue(CIFormConstruction.Construction_CostEstimateGroupItemForm.product.name) != null;
                Integer order = 0;
                final PrintQuery print = new PrintQuery(instance);
                print.addAttribute(CISales.PositionGroupRoot.Level, CISales.PositionGroupRoot.Order);
                final SelectBuilder selDocInst = new SelectBuilder()
                                .linkto(CISales.PositionGroupRoot.DocumentAbstractLink).instance();
                print.addSelect(selDocInst);
                print.executeWithoutAccessCheck();

                final Instance docInst = print.<Instance>getSelect(selDocInst);

                final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.PositionGroupNode);
                queryBldr.addWhereAttrEqValue(CISales.PositionGroupNode.ParentGroupLink, instance);
                queryBldr.addOrderByAttributeDesc(CISales.PositionGroupNode.Order);
                final MultiPrintQuery multi = queryBldr.getPrint();
                multi.addAttribute(CISales.PositionGroupNode.Order);
                multi.setEnforceSorted(true);
                multi.executeWithoutAccessCheck();
                if (multi.next()) {
                    order = multi.<Integer>getAttribute(CISales.PositionGroupNode.Order);
                }

                final QueryBuilder queryBldr2 = new QueryBuilder(CISales.PositionGroupAbstract);
                queryBldr2.addWhereAttrEqValue(CISales.PositionGroupAbstract.DocumentAbstractLink, docInst);
                queryBldr2.addWhereAttrGreaterValue(CISales.PositionGroupRoot.Order, order);
                queryBldr2.addOrderByAttributeAsc(CISales.PositionGroupAbstract.Order);
                final InstanceQuery query = queryBldr2.getQuery();
                query.executeWithoutAccessCheck();
                int k = order + nameArr.length + 1;
                while (query.next()) {
                    final Update update = new Update(query.getCurrentValue());
                    update.add(CISales.PositionGroupAbstract.Order, k++);
                    update.execute();
                }
                order++;
                // in case of an item the array always contains only one name
                Instance newPosInstance = null;
                for (int i = 0; i < nameArr.length; i++) {
                    final Insert insert = new Insert(isItem ? CIConstruction.PositionGroupItem
                                    : CIConstruction.PositionGroupNode);
                    insert.add(CIConstruction.PositionGroupNode.Name, nameArr[i]);
                    insert.add(CIConstruction.PositionGroupItem.ParentGroupLink, instance);
                    insert.add(CIConstruction.PositionGroupNode.DocumentAbstractLink, docInst);
                    insert.add(CIConstruction.PositionGroupNode.Level,
                                    print.<Integer>getAttribute(CIConstruction.PositionGroupRoot.Level) + 1);
                    insert.add(CIConstruction.PositionGroupNode.Order, order + i);

                    if (isItem) {
                        newPosInstance = new CostEstimate().createDefaultPosition(_parameter, docInst, nameArr[i]);
                        insert.add(CIConstruction.PositionGroupItem.PositionLink, newPosInstance);
                    }
                    insert.execute();
                }
                if (isItem) {
                    new CostEstimate().updateCostEstimate4Edit(_parameter, docInst, isItem ? newPosInstance : null);
                }
                renumberGroupPostions4Doc(docInst);
            }
        }
        return new Return();
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _names names to be validated
     * @return String[] with validated names
     */
    protected String[] validateNames(final Parameter _parameter,
                                     final String[] _names)
    {
        final String[] ret = new String[_names.length];
        final Attribute attr = CISales.PositionGroupAbstract.getType().getAttribute(
                        CISales.PositionGroupAbstract.Name.name);
        final int size = attr.getSize();
        for (int i = 0; i < _names.length; i++) {
            String name = _names[i].trim();
            if (name.isEmpty()) {
                name = DBProperties.getProperty(CostEstimatePosGroup.class.getName() + ".DefaultName");
            }
            if (name.length() > size) {
                name = name.substring(0, size - 1);
            }
            ret[i] = name;
        }
        return ret;
    }

    /**
     * Recursive method to update the level and order of position groups.
     *
     * @param _grpInst Instance if the current position group.
     * @param _order Current order of the position group.
     * @param _level Current level of the position group.
     * @return int value with the new order.
     * @throws EFapsException on error.
     */
    protected int renumberGroupPostions(final Instance _grpInst,
                                        final int _order,
                                        final int _level)
        throws EFapsException
    {
        int ret = _order + 1;
        final Update update = new Update(_grpInst);
        update.add(CISales.PositionGroupAbstract.Level, _level);
        update.add(CISales.PositionGroupAbstract.Order, ret);
        update.executeWithoutTrigger();

        final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.PositionGroupNode);
        queryBldr.addWhereAttrEqValue(CISales.PositionGroupNode.ParentGroupLink, _grpInst);
        queryBldr.addOrderByAttributeAsc(CISales.PositionGroupNode.Order);
        final InstanceQuery query = queryBldr.getQuery();
        query.executeWithoutAccessCheck();
        while (query.next()) {
            ret = renumberGroupPostions(query.getCurrentValue(), ret, _level + 1);
        }
        return ret;
    }

    /**
     * Method to looking for the position roots of the current doc, and call to
     * {@link #renumberGroupPostions(Instance, int, int)} method to update the
     * level and order.
     *
     * @param _docInst Instance of the current document.
     * @throws EFapsException on error.
     */
    protected void renumberGroupPostions4Doc(final Instance _docInst)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.PositionGroupRoot);
        queryBldr.addWhereAttrEqValue(CISales.PositionGroupRoot.DocumentAbstractLink, _docInst);
        queryBldr.addOrderByAttributeAsc(CISales.PositionGroupRoot.Order);
        final InstanceQuery query = queryBldr.getQuery();
        query.executeWithoutAccessCheck();
        int order = -1;
        final int level = 1;
        while (query.next()) {
            order = renumberGroupPostions(query.getCurrentValue(), order, level);
        }
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return new empty Return
     * @throws EFapsException on error
     */
    public Return createGroupItems(final Parameter _parameter)
        throws EFapsException
    {
        if (Context.getThreadContext().containsSessionAttribute("parentOID")
                        && Context.getThreadContext().getSessionAttribute("parentOID") != null) {
            final String[] parentOids = (String[]) Context.getThreadContext().getSessionAttribute("parentOID");
            final String[] oids = _parameter.getParameterValues("selectedRow");

            if (parentOids != null && parentOids.length > 0 && oids != null && oids.length > 0) {
                for (final String oid : oids) {
                    Context.getThreadContext().setSessionAttribute("parentOID", parentOids);

                    final PrintQuery print = new PrintQuery(Instance.get(oid));
                    print.addAttribute(CIConstruction.EntrySheetPosition.ProductDesc);
                    print.executeWithoutAccessCheck();

                    final String prodDesc = print.<String>getAttribute(CIConstruction.EntrySheetPosition.ProductDesc);

                    ParameterUtil.setParameterValues(_parameter,
                                CIFormConstruction.Construction_CostEstimateGroupItemForm.productDesc.name, prodDesc);
                    ParameterUtil.setParameterValues(_parameter,
                                CIFormConstruction.Construction_CostEstimateGroupItemForm.product.name, oid);
                    createGroup(_parameter);
                }
            }
        }
        return new Return();
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return new empty Return
     * @throws EFapsException on error
     */
    public Return movePositionGroup(final Parameter _parameter)
        throws EFapsException
    {
        String[] oids = new String[0];
        if (Context.getThreadContext().containsSessionAttribute("selectedOIDs")
                        && Context.getThreadContext().getSessionAttribute("selectedOIDs") != null) {
            oids = (String[]) Context.getThreadContext().getSessionAttribute("selectedOIDs");
            Context.getThreadContext().removeSessionAttribute("selectedOIDs");
        }
        final String sel = _parameter.getParameterValue("selectedRow");
        if (oids.length > 0 && sel != null && !sel.isEmpty()) {
            for (final String oid : oids) {
                final Instance instance = Instance.get(oid);
                if (!instance.getType().isKindOf(CISales.PositionGroupRoot.getType())) {
                    final Update update = new Update(Instance.get(oid));
                    update.add(CISales.PositionGroupNode.ParentGroupLink, Instance.get(sel));
                    update.execute();
                }
            }
            final PrintQuery print = new PrintQuery(Instance.get(oids[0]));
            final SelectBuilder selBldr = new SelectBuilder()
                            .linkto(CISales.PositionGroupAbstract.DocumentAbstractLink).oid();
            print.addSelect(selBldr);
            print.executeWithoutAccessCheck();
            final Instance docInst = Instance.get(print.<String>getSelect(selBldr));
            renumberGroupPostions4Doc(docInst);
        }
        return new Return();
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return new empty Return
     * @throws EFapsException on error
     */
    public Return upDownPositionGroup(final Parameter _parameter)
        throws EFapsException
    {
        String[] oids = new String[0];
        if (Context.getThreadContext().containsSessionAttribute("selectedOID")
                        && Context.getThreadContext().getSessionAttribute("selectedOID") != null) {
            oids = (String[]) Context.getThreadContext().getSessionAttribute("selectedOID");
            Context.getThreadContext().removeSessionAttribute("selectedOID");
        }
        final boolean up = "true".equals(_parameter.getParameterValue("upDown"));
        final Integer count = Integer.valueOf(_parameter.getParameterValue("count"));
        if (oids.length == 1) {
            final SelectBuilder selDoc = new SelectBuilder()
                            .linkto(CISales.PositionGroupAbstract.DocumentAbstractLink).instance();

            final Instance movePosInst = Instance.get(oids[0]);
            final PrintQuery print = new PrintQuery(movePosInst);
            print.addAttribute(CISales.PositionGroupAbstract.Order, CISales.PositionGroupAbstract.Level);
            print.addSelect(selDoc);
            print.executeWithoutAccessCheck();
            final int order = print.<Integer>getAttribute(CISales.PositionGroupAbstract.Order);
            final int level = print.<Integer>getAttribute(CISales.PositionGroupAbstract.Level);
            final Instance docInstance = print.<Instance>getSelect(selDoc);

            final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionGroupAbstract);
            queryBldr.addWhereAttrEqValue(CISales.PositionGroupAbstract.Level, level);
            queryBldr.addWhereAttrEqValue(CISales.PositionGroupAbstract.DocumentAbstractLink, docInstance);
            if (up) {
                queryBldr.addWhereAttrLessValue(CISales.PositionGroupAbstract.Order, order + 1 - Math.abs(count));
                queryBldr.addOrderByAttributeDesc(CISales.PositionGroupAbstract.Order);
            } else {
                queryBldr.addWhereAttrGreaterValue(CISales.PositionGroupAbstract.Order, order - 1 + Math.abs(count));
                queryBldr.addOrderByAttributeAsc(CISales.PositionGroupAbstract.Order);
            }
            final InstanceQuery query = queryBldr.getQuery();
            query.executeWithoutAccessCheck();
            if (query.next()) {
                final Instance targetPosInst = query.getCurrentValue();
                final PrintQuery print2 = new PrintQuery(targetPosInst);
                print2.addAttribute(CISales.PositionGroupAbstract.Order);
                print2.executeWithoutAccessCheck();

                final Update update = new Update(movePosInst);
                update.add(CISales.PositionGroupAbstract.Order,
                                print2.<Object>getAttribute(CISales.PositionGroupAbstract.Order));
                update.execute();

                final Update update2 = new Update(targetPosInst);
                update2.add(CISales.PositionGroupAbstract.Order, order);
                update2.execute();

                renumberGroupPostions4Doc(docInstance);
            }
        }
        return new Return();
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return new empty Return
     * @throws EFapsException on error
     */
    public Return deleteGroupPosition(final Parameter _parameter)
        throws EFapsException
    {
        final String[] allOids = (String[]) _parameter.get(ParameterValues.OTHERS);
        final Set<Instance> docInsts = new HashSet<>();
        for (final String rowOids : allOids) {
            final Instance grpInst = Instance.get(rowOids);
            final PrintQuery print = new PrintQuery(grpInst);
            final SelectBuilder sel = new SelectBuilder()
                            .linkto(CISales.PositionGroupAbstract.DocumentAbstractLink).instance();
            print.addSelect(sel);
            print.executeWithoutAccessCheck();
            final Instance docInst = print.<Instance>getSelect(sel);
            if (docInst != null && docInst.isValid()) {
                docInsts.add(docInst);
            }
            deleteGroupPosition(_parameter, grpInst);
        }
        for (final Instance docInst : docInsts) {
            renumberGroupPostions4Doc(docInst);
        }
        return new Return();
    }

    /**
     * Recursive method to delete group.
     * @param _parameter Parameter as passed by the eFaps API
     * @param _parentInst instance the children will be removed for
     * @throws EFapsException on error
     */
    protected void deleteGroupPosition(final Parameter _parameter,
                                       final Instance _parentInst)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.PositionGroupNode);
        queryBldr.addWhereAttrEqValue(CISales.PositionGroupNode.ParentGroupLink, _parentInst);
        final InstanceQuery query = queryBldr.getQuery();
        query.executeWithoutAccessCheck();
        while (query.next()) {
            deleteGroupPosition(_parameter, query.getCurrentValue());
        }
        if (_parentInst.getType().equals(CIConstruction.PositionGroupItem.getType())) {
            final PrintQuery print = new PrintQuery(_parentInst);
            final SelectBuilder sel = new SelectBuilder()
                            .linkto(CISales.PositionGroupAbstract.AbstractPositionAbstractLink).oid();
            print.addSelect(sel);
            print.executeWithoutAccessCheck();
            final Instance posInst = Instance.get(print.<String>getSelect(sel));
            if (posInst.isValid()) {
                final Delete del = new Delete(posInst);
                del.execute();
            }
        }
        final Delete del = new Delete(_parentInst);
        del.execute();
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return new empty Return
     * @throws EFapsException on error
     */
    public Return createPositionItemsDefault(final Parameter _parameter)
        throws EFapsException
    {
        final String names = _parameter.getParameterValue(
                        CIFormConstruction.Construction_CostEstimateItemsDefaultForm.names.name);
        final String[] nameArr = validateNames(_parameter, names.split("\n"));

        String[] oids = new String[0];
        if (Context.getThreadContext().containsSessionAttribute("parentOID")
                        && Context.getThreadContext().getSessionAttribute("parentOID") != null) {
            oids = (String[]) Context.getThreadContext().getSessionAttribute("parentOID");
        }

        for (int i = 0; i < nameArr.length; i++) {
            Context.getThreadContext().setSessionAttribute("parentOID", oids);
            Instance tmplInst = Instance.get(_parameter
                            .getParameterValue(CIFormConstruction.Construction_CostEstimateItemsDefaultForm.templInst.name));
            if (!tmplInst.isValid()) {
                tmplInst = Construction.DEFAULTTEMPLATEPARTLIST.get();
            }
            if (tmplInst.isValid()) {
                final Instance entrySheetInst = Instance.get(_parameter
                                .getParameterValue(CIFormConstruction.Construction_CostEstimateItemsDefaultForm.entrySheets.name));
                final Instance[] insts = new EntryPartList().createEntryFromTemplate(_parameter, tmplInst,
                                entrySheetInst);

                final Update update = new Update(insts[0]);
                update.add(CIProducts.ProductAbstract.Description, nameArr[i]);
                update.execute();

                final Update update2 = new Update(insts[1]);
                update2.add(CIConstruction.EntrySheetPosition.ProductDesc, nameArr[i]);
                update2.execute();

                ParameterUtil.setParameterValues(_parameter, CIFormConstruction.Construction_CostEstimateGroupItemForm.productDesc.name,
                                nameArr[i]);
                ParameterUtil.setParameterValues(_parameter, CIFormConstruction.Construction_CostEstimateGroupItemForm.product.name,
                                insts[1].getOid());
                createGroup(_parameter);
            }
        }
        return new Return();
    }


    public Return templatePicker(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Map<String, String> map = new HashMap<>();
        ret.put(ReturnValues.VALUES, map);

        final Instance viewOid = Instance.get(_parameter.getParameterValue("selectedRow"));
        if (viewOid.isValid()) {
            final PrintQuery print = new PrintQuery(viewOid);
            final SelectBuilder selProd = SelectBuilder.get().linkto(CIProducts.TreeViewAbstract.ProductAbstractLink);
            final SelectBuilder selProdName = new SelectBuilder(selProd).attribute(CIProducts.ProductAbstract.Name);
            final SelectBuilder selProdDescr = new SelectBuilder(selProd)
                            .attribute(CIProducts.ProductAbstract.Description);
            final SelectBuilder selProdInst = new SelectBuilder(selProd).instance();
            print.addSelect(selProdInst, selProdName, selProdDescr);
            if (print.execute()) {
                final String name = print.getSelect(selProdName);
                final String descr = print.getSelect(selProdDescr);
                final Instance prodInst = print.getSelect(selProdInst);
                map.put(CIFormConstruction.Construction_CostEstimateItemsDefaultForm.templName.name, name + " - " + descr);
                map.put(CIFormConstruction.Construction_CostEstimateItemsDefaultForm.templInst.name, prodInst.getOid());
            }
        }
        return ret;
    }
}
