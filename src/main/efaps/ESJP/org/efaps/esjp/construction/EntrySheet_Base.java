/*
 *  Copyright 2003 - 2020 The eFaps Team
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.Format;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.efaps.admin.datamodel.Attribute;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.ui.AbstractCommand;
import org.efaps.ci.CIType;
import org.efaps.db.AttributeQuery;
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
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIFormConstruction;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CIProjects;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.ci.CITableConstruction;
import org.efaps.esjp.common.uitable.MultiPrint;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.erp.Revision;
import org.efaps.esjp.projects.document.Naming;
import org.efaps.esjp.sales.Calculator;
import org.efaps.esjp.sales.ICalculatorConfig;
import org.efaps.esjp.sales.document.AbstractDocumentSum;
import org.efaps.ui.wicket.util.EFapsKey;
import org.efaps.util.EFapsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class EntrySheet_Base.
 *
 * @author The eFaps Team
 */
@EFapsUUID("de6937eb-e18b-49b2-8f15-bcae9bc77886")
@EFapsApplication("eFapsApp-Construction")
public abstract class EntrySheet_Base
    extends AbstractDocumentSum
{
    /**
     * Logging instance used in this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(EntrySheet.class);

    /**
     * Creates the.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return create(final Parameter _parameter)
        throws EFapsException
    {
        final CreatedDoc createdDoc = createDoc(_parameter);
        createPositions(_parameter, createdDoc);
        insertRelation(_parameter, createdDoc);
        return new Return();
    }

    /**
     * Update fields4 project.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    @SuppressWarnings("unchecked")
    public Return updateFields4Project(final Parameter _parameter)
        throws EFapsException
    {
        Return ret = new Return();
        final Instance projInst = Instance.get(_parameter.getParameterValue(CIFormConstruction.Construction_EntrySheetForm.project.name));
        if (projInst.isValid()) {
            final PrintQuery print = new PrintQuery(projInst);
            print.addAttribute(CIProjects.ProjectAbstract.CurrencyLink);
            print.execute();
            final Long currencyId = print.<Long>getAttribute(CIProjects.ProjectAbstract.CurrencyLink);
            if (currencyId != null) {
                final Map<Object, Object> parameters = (Map<Object, Object>) _parameter.get(ParameterValues.PARAMETERS);
                parameters.put("rateCurrencyId", new String[] { currencyId.toString() });
                ret = updateFields4RateCurrency(_parameter);
                final List<Map<String, String>> list = (List<Map<String, String>>) ret.get(ReturnValues.VALUES);
                final Map<String, String> map = new HashMap<String, String>();
                final StringBuilder js = new StringBuilder();
                js.append(getSetFieldValue(0, "rateCurrencyId", currencyId.toString()));
                map.put(EFapsKey.FIELDUPDATE_JAVASCRIPT.getKey(), js.toString());
                list.add(map);
            }
        }
        return ret;
    }

    /* (non-Javadoc)
     * @see org.efaps.esjp.sales.document.AbstractDocument_Base#getDocName4Create(org.efaps.admin.event.Parameter)
     */
    @Override
    protected String getDocName4Create(final Parameter _parameter)
        throws EFapsException
    {
        String ret = null;
        final Instance projectInst = Instance.get(_parameter
                        .getParameterValue(CIFormConstruction.Construction_EntrySheetForm.project.name));
        if (projectInst.isValid()) {
            ret = Naming.getName(projectInst, CIConstruction.EntrySheet.getType(), true);
        } else {
            final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.ProjectService2EntrySheet);
            queryBldr.addWhereAttrEqValue(CIConstruction.ProjectService2EntrySheet.ToLink, _parameter.getInstance().getId());
            final InstanceQuery query = queryBldr.getQuery();
            final List<Instance> instances = query.execute();
            if (!instances.isEmpty() && instances.get(0).isValid()) {
                final PrintQuery print = new PrintQuery(instances.get(0));
                final SelectBuilder sel = new SelectBuilder().linkto(CIConstruction.ProjectService2EntrySheet.FromLink)
                                .instance();
                print.addSelect(sel);
                print.execute();
                final Instance inst = (Instance) print.getSelect(sel);
                if (inst.isValid()) {
                    ret = Naming.getName(inst, CIConstruction.EntrySheet.getType(), true);
                } else {
                    ret = super.getDocName4Create(_parameter);
                }
            } else {
                ret = super.getDocName4Create(_parameter);
            }
        }
        return ret;
    }

    /**
     * Validate entry sheet.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return validateEntrySheet(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final StringBuilder html = new StringBuilder();
        final String description = _parameter.getParameterValue(CIFormConstruction.Construction_EntrySheetForm.description.name);
        if (description != null) {

            if (description.length() <= 64) {
                ret.put(ReturnValues.TRUE, true);
            } else {
                html.insert(0, DBProperties.getProperty(EntrySheet.class + ".InvalidEntrySheet")
                                + "<p>");
                ret.put(ReturnValues.SNIPLETT, html.toString());
            }
        }
        return ret;
    }

    @Override
    protected void add2DocCreate(final Parameter _parameter,
                                 final Insert _insert,
                                 final CreatedDoc _createdDoc)
        throws EFapsException
    {
        final String desc = _parameter.getParameterValue(CIFormConstruction.Construction_EntrySheetForm.description.name);
        _insert.add(CIConstruction.EntrySheet.Description, desc);
        _createdDoc.getValues().put(CIConstruction.EntrySheet.Description.name, desc);
    }

    /**
     * Insert relation.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _createdDoc the created doc
     * @throws EFapsException on error
     */
    protected void insertRelation(final Parameter _parameter,
                                  final CreatedDoc _createdDoc)
        throws EFapsException
    {
        final String project = _parameter.getParameterValue(CIFormConstruction.Construction_EntrySheetForm.project.name);
        if (project != null) {
            final Instance projInst = Instance.get(project);
            if (projInst.isValid()) {
                final Insert insert = new Insert(CIConstruction.ProjectService2EntrySheet);
                insert.add(CIConstruction.ProjectService2EntrySheet.FromLink, projInst);
                insert.add(CIConstruction.ProjectService2EntrySheet.ToLink, _createdDoc.getInstance());
                insert.execute();
            }
        }
    }

    /**
     * Revise.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return revise(final Parameter _parameter)
        throws EFapsException
    {
        final String[] oids;
        if (_parameter.get(ParameterValues.OTHERS) instanceof Map) {
            oids = (String[]) Context.getThreadContext().getSessionAttribute("selectedQuotation");
        } else {
            oids = (String[]) _parameter.get(ParameterValues.OTHERS);
        }
        final Instance inst = Instance.get(oids[0]);
        _parameter.put(ParameterValues.INSTANCE, inst);
        final Revision rev = new Revision()
        {

            @Override
            public Return revise(final Parameter _parameter)
                throws EFapsException
            {
                Map<Instance, Instance> retInst = null;
                final Return ret;
                if (reviseable(_parameter.getInstance())) {
                    final Instance newInst = copyDoc(_parameter);
                    updateRevision(_parameter, newInst);
                    retInst = copyRelations(_parameter, newInst);
                    retInst.put(_parameter.getInstance(), newInst);
                    connectRevision(_parameter, newInst);
                    setStati(_parameter, newInst);
                    final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.EntrySheetPosition);
                    queryBldr.addWhereAttrEqValue(CIConstruction.EntrySheetPosition.EntrySheetLink, newInst.getId());
                    final MultiPrintQuery multi = queryBldr.getPrint();
                    final SelectBuilder sel = new SelectBuilder().linkto(CIConstruction.EntrySheetPosition.Product).oid();
                    multi.addSelect(sel);
                    multi.execute();
                    final EntryPartList_Base partList = new EntryPartList();
                    while (multi.next()) {
                        final Instance prodInst = Instance.get(multi.<String>getSelect(sel));
                        if (prodInst.getType().isKindOf(CIConstruction.EntryPartList.getType())) {
                            final Object[] object = partList.copy(_parameter, prodInst,
                                            prodInst.getType(), null, true, false);
                            final Instance newProdInst = (Instance) object[0];

                            final Update update = new Update(multi.getCurrentInstance());
                            update.add(CIConstruction.EntrySheetPosition.Product, newProdInst.getId());
                            update.execute();
                            // only for revise connect them
                            final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
                            if (!"false".equalsIgnoreCase((String) props.get("RevisionConnect"))) {
                                final Insert insert = new Insert(CIConstruction.EntryPartList2EntryPartListRev);
                                insert.add(CIConstruction.EntryPartList2EntryPartListRev.FromLink, prodInst.getId());
                                insert.add(CIConstruction.EntryPartList2EntryPartListRev.ToLink, newProdInst.getId());
                                insert.execute();
                            }
                        }
                    }
                    final PrintQuery print = new PrintQuery(newInst);
                    final SelectBuilder selQuotInst = new SelectBuilder()
                                    .linkfrom(CIConstruction.CostEstimate2EntrySheet, CIConstruction.CostEstimate2EntrySheet.ToLink)
                                    .linkto(CIConstruction.CostEstimate2EntrySheet.FromLink).instance();
                    print.addSelect(selQuotInst);
                    print.executeWithoutAccessCheck();
                    final Instance quotInst = print.<Instance>getSelect(selQuotInst);
                    if (quotInst != null && quotInst.isValid()) {
                        new CostEstimate().reconnectPartLists(_parameter, quotInst);
                        new CostEstimate().reconnectEntrySheetPosition(_parameter, quotInst, retInst);
                    }
                }
                ret = new Return();
                ret.put(ReturnValues.VALUES, retInst);
                return ret;
            }

            @Override
            protected Instance copyDoc(final Parameter _parameter)
                throws EFapsException
            {
                final Instance origInst = _parameter.getInstance();
                final Insert insert = new Insert(origInst.getType());
                final Set<String> added = new HashSet<String>();
                final String typeId = _parameter.getParameterValue(CIFormConstruction.Construction_CostEstimateCopyToForm.type.name);
                if (typeId != null) {
                    final Attribute revAttr = origInst.getType().getAttribute(CIERP.DocumentAbstract.Revision.name);
                    added.add(revAttr.getSqlColNames().toString());

                    insert.add(CIERP.DocumentAbstract.Name, getDocName4Create(_parameter));
                    final Attribute nameAttr = origInst.getType().getAttribute(CIERP.DocumentAbstract.Name.name);
                    added.add(nameAttr.getSqlColNames().toString());
                }
                addAttributes(_parameter, origInst, insert, added);
                insert.execute();
                return insert.getInstance();
            }

            @Override
            protected Map<Instance, Instance> copyRelation(final Parameter _parameter,
                                                           final Instance _newInst,
                                                           final String _typeName,
                                                           final String _linkAttrName)
                throws EFapsException
            {
                Map<Instance, Instance> ret = new HashMap<Instance, Instance>();
                final String proj = _parameter.getParameterValue(CIFormConstruction.Construction_CostEstimateCopyToForm.project.name);
                final Instance projectInst = Instance.get(proj);
                // check if it must be copied to another project
                if (CIConstruction.ProjectService2EntrySheet.getType().getName().equals(_typeName) && projectInst.isValid()
                                && _parameter.getParameterValue("projectAutoComplete") != null
                                && !_parameter.getParameterValue("projectAutoComplete").isEmpty()) {
                    final Insert insert = new Insert(CIConstruction.ProjectService2EntrySheet);
                    insert.add(CIConstruction.ProjectService2EntrySheet.FromLink, projectInst.getId());
                    insert.add(CIConstruction.ProjectService2EntrySheet.ToLink, _newInst.getId());
                    insert.execute();
                } else {
                    ret = super.copyRelation(_parameter, _newInst, _typeName, _linkAttrName);
                }
                return ret;
            }
        };
        return rev.revise(_parameter);
    }

    @Override
    protected Type getType4DocCreate(final Parameter _parameter)
        throws EFapsException
    {
        Type type = null;
        if (_parameter.get(ParameterValues.UIOBJECT) != null) {
            final AbstractCommand command = (AbstractCommand) _parameter.get(ParameterValues.UIOBJECT);
            type = command.getTargetCreateType();
        }

        if (type == null) {
            type = CIConstruction.EntrySheet.getType();
        }

        return type;
    }

    /**
     * Position multi print.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return positionMultiPrint(final Parameter _parameter)
        throws EFapsException
    {
        final MultiPrint multi = new MultiPrint()
        {

            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                final String productType = getProperty(_parameter, "ProductType");
                if (productType != null) {
                    final Type prodType = Type.get(productType);
                    final QueryBuilder queryBldr = new QueryBuilder(prodType);
                    final AttributeQuery attrQuery = queryBldr.getAttributeQuery(CIProducts.ProductAbstract.ID);
                    _queryBldr.addWhereAttrInQuery(CIConstruction.EntrySheetPosition.Product, attrQuery);
                }
            }
        };
        return multi.execute(_parameter);
    }

    /**
     * Edits the general cost.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return new empty return
     * @throws EFapsException on error
     */
    public Return editGeneralCost(final Parameter _parameter)
        throws EFapsException
    {
        @SuppressWarnings("unchecked")
        final Map<String, String> oidMap = (Map<String, String>) _parameter.get(ParameterValues.OIDMAP4UI);
        final String[] rowKeys = _parameter.getParameterValues(EFapsKey.TABLEROW_NAME.getKey());
        final String[] prodDescs = _parameter
                        .getParameterValues(CITableConstruction.Construction_EntrySheetPositionTable.productDesc.name);
        final Set<Instance> posinsts = new HashSet<Instance>();
        if (rowKeys != null) {
            final Instance baseCurrInst = Currency.getBaseCurrency();

            Instance rateCurrInst = getCurrencyFromUI(_parameter);
            if (rateCurrInst == null) {
                rateCurrInst = baseCurrInst;
            }
            final Object[] rateObj = getRateObject(_parameter);

            final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                            RoundingMode.HALF_UP);

            final List<Calculator> calcList = analyseTable(_parameter, null);
            final Iterator<Calculator> iter = calcList.iterator();

            final DecimalFormat unitFrmt = NumberFormatter.get().getFrmt4UnitPrice(getType4SysConf(_parameter));

            for (int i = 0; i < rowKeys.length; i++) {
                final Calculator calc = iter.next();
                final Instance inst = Instance.get(oidMap.get(rowKeys[i]));
                final Update posUpdate;
                if (inst.isValid()) {
                    posUpdate = new Update(inst);
                } else {
                    posUpdate = new Insert(CIConstruction.EntrySheetPosition);
                    posUpdate.add(CIConstruction.EntrySheetPosition.EntrySheetLink, _parameter.getInstance());
                }
                final Long productdId = Instance.get(_parameter.getParameterValues("product")[i]).getId();
                posUpdate.add(CIConstruction.EntrySheetPosition.PositionNumber, i);
                posUpdate.add(CIConstruction.EntrySheetPosition.Product, productdId.toString());
                posUpdate.add(CIConstruction.EntrySheetPosition.ProductDesc, prodDescs[i]);
                posUpdate.add(CIConstruction.EntrySheetPosition.Quantity, calc.getQuantity());
                posUpdate.add(CIConstruction.EntrySheetPosition.UoM, _parameter.getParameterValues("uoM")[i]);
                posUpdate.add(CIConstruction.EntrySheetPosition.CrossUnitPrice, calc.getCrossUnitPrice()
                                .divide(rate, 12, RoundingMode.HALF_UP)
                                .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
                posUpdate.add(CIConstruction.EntrySheetPosition.NetUnitPrice, calc.getNetUnitPrice()
                                .divide(rate, 12, RoundingMode.HALF_UP)
                                .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
                posUpdate.add(CIConstruction.EntrySheetPosition.CrossPrice, calc.getCrossPrice()
                                .divide(rate, 12, RoundingMode.HALF_UP)
                                .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
                posUpdate.add(CIConstruction.EntrySheetPosition.NetPrice, calc.getNetPrice()
                                .divide(rate, 12, RoundingMode.HALF_UP)
                                .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
                posUpdate.add(CIConstruction.EntrySheetPosition.Tax, calc.getTaxCatId());
                posUpdate.add(CIConstruction.EntrySheetPosition.Discount, calc.getDiscountStr());
                posUpdate.add(CIConstruction.EntrySheetPosition.DiscountNetUnitPrice, calc.getDiscountNetUnitPrice()
                                .divide(rate, 12, RoundingMode.HALF_UP)
                                .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
                posUpdate.add(CIConstruction.EntrySheetPosition.CurrencyId, baseCurrInst.getId());
                posUpdate.add(CIConstruction.EntrySheetPosition.Rate, rateObj);
                posUpdate.add(CIConstruction.EntrySheetPosition.RateCurrencyId, rateCurrInst.getId());
                posUpdate.add(CIConstruction.EntrySheetPosition.RateNetUnitPrice, calc.getNetUnitPrice()
                                .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
                posUpdate.add(CIConstruction.EntrySheetPosition.RateCrossUnitPrice, calc.getCrossUnitPrice()
                                .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
                posUpdate.add(CIConstruction.EntrySheetPosition.RateDiscountNetUnitPrice, calc.getDiscountNetUnitPrice()
                                .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
                posUpdate.add(CIConstruction.EntrySheetPosition.RateNetPrice,
                                calc.getNetPrice().setScale(unitFrmt.getMaximumFractionDigits(),
                                                RoundingMode.HALF_UP));
                posUpdate.add(CIConstruction.EntrySheetPosition.RateCrossPrice,
                                calc.getCrossPrice().setScale(unitFrmt.getMaximumFractionDigits(),
                                                RoundingMode.HALF_UP));
                posUpdate.add(CIConstruction.EntrySheetPosition.RateCrossPrice,
                                calc.getCrossPrice().setScale(unitFrmt.getMaximumFractionDigits(),
                                                RoundingMode.HALF_UP));
                posUpdate.add(CIConstruction.EntrySheetPosition.Participation,
                                ((EntrySheetCalculator) calc).getParticipation4Update());
                posUpdate.add(CIConstruction.EntrySheetPosition.Time, ((EntrySheetCalculator) calc).getTime4Update());

                posUpdate.execute();

                posinsts.add(posUpdate.getInstance());
            }
        }
        final QueryBuilder delQueryAttrBldr = new QueryBuilder(CIConstruction.ProductGeneralCostPosition);
        final AttributeQuery delQueryAttr = delQueryAttrBldr.getAttributeQuery(CIConstruction.ProductGeneralCostPosition.ID);
        final QueryBuilder delQueryBldr = new QueryBuilder(CIConstruction.EntrySheetPosition);
        delQueryBldr.addWhereAttrEqValue(CIConstruction.EntrySheetPosition.EntrySheetLink, _parameter.getInstance());
        delQueryBldr.addWhereAttrInQuery(CIConstruction.EntrySheetPosition.Product, delQueryAttr);
        final InstanceQuery delQuery = delQueryBldr.getQuery();
        delQuery.execute();
        while (delQuery.next()) {
            final Instance posInst = delQuery.getCurrentValue();
            if (!posinsts.contains(posInst)) {
                final Delete del = new Delete(posInst);
                del.execute();
            }
        }
        updateTotals(_parameter, _parameter.getInstance());
        return new Return();
    }

    /**
     * Edits the position.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return new empty return
     * @throws EFapsException on error
     */
    public Return editPosition(final Parameter _parameter)
        throws EFapsException
    {
        final String prodOID = _parameter.getParameterValue(CIFormConstruction.Construction_EntrySheetPositionForm.product.name);
        final String quantity = _parameter.getParameterValue(CIFormConstruction.Construction_EntrySheetPositionForm.quantity.name);
        final String unitPrice = _parameter.getParameterValue(CIFormConstruction.Construction_EntrySheetPositionForm.netUnitPrice.name);
        final String prodDesc = _parameter.getParameterValue(CIFormConstruction.Construction_EntrySheetPositionForm.productDesc.name);
        final Calculator calc = getCalculator(_parameter, null, prodOID, quantity, unitPrice, "", false, 0);

        final DecimalFormat unitFrmt = NumberFormatter.get().getFrmt4UnitPrice(getType4SysConf(_parameter));
        final PrintQuery print = new PrintQuery(_parameter.getInstance());
        final SelectBuilder selESInst = SelectBuilder.get().linkto(CIConstruction.EntrySheetPosition.EntrySheetLink).instance();
        print.addSelect(selESInst);
        print.addAttribute(CIConstruction.EntrySheetPosition.Rate);
        print.execute();
        final Object[] rateObj = print.getAttribute(CIConstruction.EntrySheetPosition.Rate);
        final BigDecimal rate = new Currency().evalRate(rateObj, false);

        final Update posUpdate = new Update(_parameter.getInstance());
        posUpdate.add(CIConstruction.EntrySheetPosition.ProductDesc, prodDesc);
        posUpdate.add(CIConstruction.EntrySheetPosition.Quantity, calc.getQuantity());
        posUpdate.add(CIConstruction.EntrySheetPosition.CrossUnitPrice, calc.getCrossUnitPrice()
                        .divide(rate, 12, RoundingMode.HALF_UP)
                        .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
        posUpdate.add(CIConstruction.EntrySheetPosition.NetUnitPrice, calc.getNetUnitPrice()
                        .divide(rate, 12, RoundingMode.HALF_UP)
                        .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
        posUpdate.add(CIConstruction.EntrySheetPosition.CrossPrice, calc.getCrossPrice()
                        .divide(rate, 12, RoundingMode.HALF_UP)
                        .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
        posUpdate.add(CIConstruction.EntrySheetPosition.NetPrice, calc.getNetPrice()
                        .divide(rate, 12, RoundingMode.HALF_UP)
                        .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
        posUpdate.add(CIConstruction.EntrySheetPosition.RateNetUnitPrice, calc.getNetUnitPrice()
                        .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
        posUpdate.add(CIConstruction.EntrySheetPosition.RateCrossUnitPrice, calc.getCrossUnitPrice()
                        .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
        posUpdate.add(CIConstruction.EntrySheetPosition.RateDiscountNetUnitPrice, calc.getDiscountNetUnitPrice()
                        .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
        posUpdate.add(CIConstruction.EntrySheetPosition.RateNetPrice,
                        calc.getNetPrice().setScale(unitFrmt.getMaximumFractionDigits(),
                                        RoundingMode.HALF_UP));
        posUpdate.add(CIConstruction.EntrySheetPosition.RateCrossPrice,
                        calc.getCrossPrice().setScale(unitFrmt.getMaximumFractionDigits(),
                                        RoundingMode.HALF_UP));
        posUpdate.add(CIConstruction.EntrySheetPosition.RateCrossPrice,
                        calc.getCrossPrice().setScale(unitFrmt.getMaximumFractionDigits(),
                                        RoundingMode.HALF_UP));
        posUpdate.add(CIConstruction.EntrySheetPosition.Participation,
                        ((EntrySheetCalculator) calc).getParticipation4Update());
        posUpdate.add(CIConstruction.EntrySheetPosition.Time, ((EntrySheetCalculator) calc).getTime4Update());
        posUpdate.execute();

        updateTotals(_parameter, print.<Instance>getSelect(selESInst));
        return new Return();
    }


    /**
     * Method to update the total of a EntrySheet.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _entrySheetInst the entry sheet inst
     * @throws EFapsException on error.
     */
    public void updateTotals(final Parameter _parameter,
                             final Instance... _entrySheetInst)
        throws EFapsException
    {
        for (final Instance instance : _entrySheetInst) {
            final PrintQuery print = new PrintQuery(instance);
            final SelectBuilder selCurInst = SelectBuilder.get().linkto(CIConstruction.EntrySheet.CurrencyId).instance();
            final SelectBuilder selRateCurInst = SelectBuilder.get().linkto(CIConstruction.EntrySheet.RateCurrencyId).instance();
            print.addSelect(selCurInst, selRateCurInst);
            print.addAttribute(CIConstruction.EntrySheet.Rate);
            print.execute();

            final Instance rateCurrencyInst = print.<Instance>getSelect(selRateCurInst);
            final Instance currencyInst = print.<Instance>getSelect(selCurInst);
            final BigDecimal rate = new Currency().evalRate(print.<Object[]>getAttribute(CIConstruction.EntrySheet.Rate), false);
            final List<Calculator> calcList = getCalculators4Doc(_parameter, instance, null);

            final DecimalFormat frmt = NumberFormatter.get().getFrmt4Total(getType4SysConf(_parameter));
            final int scale = frmt.getMaximumFractionDigits();

            final BigDecimal rateCrossTotal = getCrossTotal(_parameter, calcList)
                            .setScale(scale, RoundingMode.HALF_UP);
            final BigDecimal rateNetTotal = getNetTotal(_parameter, calcList).setScale(scale, RoundingMode.HALF_UP);
            final BigDecimal netTotal = getNetTotal(_parameter, calcList).divide(rate, RoundingMode.HALF_UP)
                            .setScale(scale, RoundingMode.HALF_UP);
            final BigDecimal crossTotal = getCrossTotal(_parameter, calcList).divide(rate, RoundingMode.HALF_UP)
                            .setScale(scale, RoundingMode.HALF_UP);

            final Update update = new Update(instance);
            update.add(CISales.DocumentSumAbstract.NetTotal, netTotal);
            update.add(CISales.DocumentSumAbstract.RateNetTotal, rateNetTotal);
            update.add(CISales.DocumentSumAbstract.CrossTotal, crossTotal);
            update.add(CISales.DocumentSumAbstract.RateCrossTotal, rateCrossTotal);
            update.add(CISales.DocumentSumAbstract.RateTaxes, getRateTaxes(_parameter, calcList, rateCurrencyInst));
            update.add(CISales.DocumentSumAbstract.Taxes, getTaxes(_parameter, calcList, rate, currencyInst));
            update.execute();
            new CostEstimate().updateCostEstimate4Edit(_parameter, instance, null);
        }
    }

    @Override
    protected List<Calculator> getCalculators4Doc(final Parameter _parameter,
                                                  final Instance _docInst,
                                                  final Collection<Instance> _excludes)
        throws EFapsException
    {
        final List<Calculator> ret = new ArrayList<Calculator>();
        final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionSumAbstract);
        queryBldr.addWhereAttrEqValue(CISales.PositionSumAbstract.DocumentAbstractLink, _docInst);
        queryBldr.addOrderByAttributeAsc(CISales.PositionSumAbstract.PositionNumber);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.setEnforceSorted(true);
        final SelectBuilder selProdInst = SelectBuilder.get().linkto(CISales.PositionSumAbstract.Product).instance();
        multi.addSelect(selProdInst);
        multi.addAttribute(CIConstruction.EntrySheetPosition.Quantity, CIConstruction.EntrySheetPosition.Discount,
                        CIConstruction.EntrySheetPosition.RateNetUnitPrice, CIConstruction.EntrySheetPosition.RateCrossUnitPrice,
                        CIConstruction.EntrySheetPosition.PositionNumber, CIConstruction.EntrySheetPosition.Participation,
                        CIConstruction.EntrySheetPosition.Time);
        multi.execute();
        while (multi.next()) {
            final BigDecimal quantity = multi.<BigDecimal>getAttribute(CIConstruction.EntrySheetPosition.Quantity);
            final BigDecimal time = multi.<BigDecimal>getAttribute(CIConstruction.EntrySheetPosition.Time);
            final BigDecimal participation = multi.<BigDecimal>getAttribute(CIConstruction.EntrySheetPosition.Participation);
            final BigDecimal discount = multi.<BigDecimal>getAttribute(CIConstruction.EntrySheetPosition.Discount);
            final BigDecimal unitPrice;
            if (Calculator.priceIsNet(_parameter, this)) {
                unitPrice = multi.<BigDecimal>getAttribute(CIConstruction.EntrySheetPosition.RateNetUnitPrice);
            } else {
                unitPrice = multi.<BigDecimal>getAttribute(CIConstruction.EntrySheetPosition.RateCrossUnitPrice);
            }
            final Integer idx = multi.<Integer>getAttribute(CIConstruction.EntrySheetPosition.PositionNumber);
            final Instance prodInst = multi.<Instance>getSelect(selProdInst);
            ret.add(getCalculator(_parameter, null, prodInst, quantity, unitPrice, discount, participation, time,
                            false, idx));
        }
        return ret;
    }

    /**
     * Method is executed as an update event of the field containing the
     * discount for products to calculate the new totals.
     *
     * @param _parameter Parameter as passed by the eFasp API
     * @return Return containing the list
     * @throws EFapsException on error
     */
    public Return updateFields4Participation(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        final List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        final Map<String, Object> map = new HashMap<String, Object>();
        final int selected = getSelectedRow(_parameter);

        final List<Calculator> calcList = analyseTable(_parameter, null);
        final Calculator cal = calcList.get(selected);
        if (calcList.size() > 0) {
            add2Map4UpdateField(_parameter, map, calcList, cal, true);
            list.add(map);
            retVal.put(ReturnValues.VALUES, list);
        }
        return retVal;
    }

    /**
     * Update fields4 time.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return updateFields4Time(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        final List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        final Map<String, Object> map = new HashMap<String, Object>();
        final int selected = getSelectedRow(_parameter);

        final List<Calculator> calcList = analyseTable(_parameter, null);
        final Calculator cal = calcList.get(selected);
        if (calcList.size() > 0) {
            add2Map4UpdateField(_parameter, map, calcList, cal, true);
            list.add(map);
            retVal.put(ReturnValues.VALUES, list);
        }
        return retVal;
    }

    @Override
    protected void add2Map4UpdateField(final Parameter _parameter,
                                       final Map<String, Object> _map,
                                       final List<Calculator> _calcList,
                                       final Calculator _cal,
                                       final boolean _addTotals)
        throws EFapsException
    {
        super.add2Map4UpdateField(_parameter, _map, _calcList, _cal, _addTotals);
        final DecimalFormat formatter = NumberFormatter.get().getTwoDigitsFormatter();
        _map.put(CITableConstruction.Construction_EntrySheetCostPositionTable.participation.name,
                        ((EntrySheetCalculator) _cal).getParticipationFmtStr(formatter));
        _map.put(CITableConstruction.Construction_EntrySheetCostPositionTable.time.name,
                        ((EntrySheetCalculator) _cal).getTimeFmtStr(formatter));
    }

    /**
     * Gets the calculator.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _oldCalc the old calc
     * @param _prodInstance the prod instance
     * @param _quantity the quantity
     * @param _unitPrice the unit price
     * @param _discount the discount
     * @param _participation the participation
     * @param _time the time
     * @param _priceFromDB the price from db
     * @param _idx the idx
     * @return the calculator
     * @throws EFapsException on error
     */
    @SuppressWarnings("checkstyle:parameternumber")
    public Calculator getCalculator(final Parameter _parameter,
                                    final Calculator _oldCalc,
                                    final Instance _prodInstance,
                                    final BigDecimal _quantity,
                                    final BigDecimal _unitPrice,
                                    final BigDecimal _discount,
                                    final BigDecimal _participation,
                                    final BigDecimal _time,
                                    final boolean _priceFromDB,
                                    final int _idx)
        throws EFapsException
    {
        return new EntrySheetCalculator(_parameter, _oldCalc, _prodInstance, _quantity, _unitPrice, _discount,
                        _participation, _time, _priceFromDB, this);
    }

    /**
     * Sets the efficiency over all.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return setEfficiencyOverAll(final Parameter _parameter)
        throws EFapsException
    {
        final String effOAStr = _parameter.getParameterValue(
                        CIFormConstruction.Construction_EntryPartListSetEfficiencyOverAllForm.efficiencyOverAll.name);
        try {
            final BigDecimal effOA = (BigDecimal) NumberFormatter.get().getFormatter().parse(effOAStr);
            setEfficiencyOverAll(_parameter, effOA, _parameter.getInstance());
        } catch (final ParseException e) {
            LOG.error("Catched ParseException", e);
        }
        return new Return();
    }

    /**
     * Sets the efficiency over all.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _effOA the eff oa
     * @param _entrySheetInsts the entry sheet insts
     * @throws EFapsException on error
     */
    public void setEfficiencyOverAll(final Parameter _parameter,
                                     final BigDecimal _effOA,
                                     final Instance... _entrySheetInsts)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.EntrySheetPosition);
        queryBldr.addWhereAttrEqValue(CIConstruction.EntrySheetPosition.DocumentAbstractLink, (Object[]) _entrySheetInsts);
        // Update the classification
        final QueryBuilder classQueryBldr = new QueryBuilder(CIConstruction.EntryPartList_Class);
        classQueryBldr.addWhereAttrInQuery(CIConstruction.EntryPartList_Class.ProductLink,
                        queryBldr.getAttributeQuery(CIConstruction.EntrySheetPosition.Product));
        final MultiPrintQuery multi = classQueryBldr.getPrint();
        final SelectBuilder eplInstSel = SelectBuilder.get().linkto(CIConstruction.EntryPartList_Class.ProductLink).instance();
        multi.addSelect(eplInstSel);
        multi.execute();
        while (multi.next()) {
            final Update classUpdate = new Update(multi.getCurrentInstance());
            classUpdate.add(CIConstruction.EntryPartList_Class.EfficiencyOverAll, _effOA);
            classUpdate.execute();
            final Instance eplInst =  multi.getSelect(eplInstSel);
            new EntryPartList().update4EfficenyOverAll(_parameter, eplInst);
        }
        updateTotals(_parameter, _entrySheetInsts);
    }

    /**
     * Gets the calculator.
     *
     * @param _parameter Parameter parameter as passe dfrom the eFaps API
     * @param _oldCalc calculator
     * @param _oid oid of the product
     * @param _quantity quantity
     * @param _unitPrice unit price
     * @param _discount discount
     * @param _priceFromDB must the price set from DB
     * @param _idx the idx
     * @return new Calculator
     * @throws EFapsException on error
     */
    @Override
    @SuppressWarnings("checkstyle:parameternumber")
    public Calculator getCalculator(final Parameter _parameter,
                                    final Calculator _oldCalc,
                                    final String _oid,
                                    final String _quantity,
                                    final String _unitPrice,
                                    final String _discount,
                                    final boolean _priceFromDB,
                                    final int _idx)
        throws EFapsException
    {
        final String[] participation = _parameter
                        .getParameterValues(CITableConstruction.Construction_EntrySheetCostPositionTable.participation.name);
        final String[] time = _parameter.getParameterValues(CITableConstruction.Construction_EntrySheetCostPositionTable.time.name);
        return new EntrySheetCalculator(_parameter, _oldCalc, _oid, _quantity, _unitPrice, _discount,
                        participation[_idx], time[_idx], _priceFromDB, this);
    }

    @Override
    public CIType getCIType()
        throws EFapsException
    {
        return CIConstruction.EntrySheet;
    }

    /**
     * The Class EntrySheetCalculator.
     *
     * @author The eFaps Team
     */
    public static class EntrySheetCalculator
        extends Calculator
    {

        /** The Constant serialVersionUID. */
        private static final long serialVersionUID = 1L;

        /** The participation. */
        private BigDecimal participation;

        /** The time. */
        private BigDecimal time;

        /**
         * Instantiates a new entry sheet calculator.
         *
         * @param _parameter Parameter as passed by the eFaps API
         * @param _calc the calc
         * @param _prodInstance the prod instance
         * @param _quantity the quantity
         * @param _unitPrice the unit price
         * @param _discount the discount
         * @param _participation the participation
         * @param _time the time
         * @param _priceFromDB the price from db
         * @param _config the config
         * @throws EFapsException on error
         */
        @SuppressWarnings("checkstyle:parameternumber")
        public EntrySheetCalculator(final Parameter _parameter,
                                    final Calculator _calc,
                                    final Instance _prodInstance,
                                    final BigDecimal _quantity,
                                    final BigDecimal _unitPrice,
                                    final BigDecimal _discount,
                                    final BigDecimal _participation,
                                    final BigDecimal _time,
                                    final boolean _priceFromDB,
                                    final ICalculatorConfig _config)
            throws EFapsException
        {
            super(_parameter, _calc, _prodInstance, _quantity, _unitPrice, _discount, _priceFromDB, _config);
            participation = _participation;
            time = _time;
        }

        /**
         * Instantiates a new entry sheet calculator.
         *
         * @param _parameter Parameter as passed by the eFaps API
         * @param _calc the calc
         * @param _oid the oid
         * @param _quantity the quantity
         * @param _unitPrice the unit price
         * @param _discount the discount
         * @param _participation the participation
         * @param _time the time
         * @param _priceFromDB the price from db
         * @param _calculatorUse the calculator use
         * @throws EFapsException on error
         */
        @SuppressWarnings("checkstyle:parameternumber")
        public EntrySheetCalculator(final Parameter _parameter,
                                    final Calculator _calc,
                                    final String _oid,
                                    final String _quantity,
                                    final String _unitPrice,
                                    final String _discount,
                                    final String _participation,
                                    final String _time,
                                    final boolean _priceFromDB,
                                    final ICalculatorConfig _calculatorUse)
            throws EFapsException
        {
            super(_parameter, _calc, _oid, _quantity, _unitPrice, _discount, _priceFromDB, _calculatorUse);
            try {
                if (_participation != null && !_participation.isEmpty()) {
                    participation = (BigDecimal) NumberFormatter.get().getFormatter().parse(_participation);
                } else {
                    participation = null;
                }
                if (_time != null && !_time.isEmpty()) {
                    time = (BigDecimal) NumberFormatter.get().getFormatter().parse(_time);
                } else {
                    time = null;
                }
            } catch (final ParseException e) {
                LOG.error("Catched ParseException", e);
            }
        }

        /**
         * Getter method for the instance variable {@link #participation}.
         *
         * @return value of instance variable {@link #participation}
         */
        public BigDecimal getParticipation()
        {
            return participation == null ? new BigDecimal(100) : participation;
        }

        /**
         * Gets the participation4 update.
         *
         * @return the participation4 update
         */
        public BigDecimal getParticipation4Update()
        {
            return participation;
        }

        /**
         * Gets the participation fmt str.
         *
         * @param _formater the formater
         * @return the participation fmt str
         * @throws EFapsException on error
         */
        public String getParticipationFmtStr(final Format _formater)
            throws EFapsException
        {
            return participation == null ? "" : _formater.format(getParticipation());
        }

        /**
         * Getter method for the instance variable {@link #time}.
         *
         * @return value of instance variable {@link #time}
         */
        public BigDecimal getTime()
        {
            return time == null ? new BigDecimal(1) : time;
        }

        /**
         * Getter method for the instance variable {@link #time}.
         *
         * @return value of instance variable {@link #time}
         */
        public BigDecimal getTime4Update()
        {
            return time;
        }

        /**
         * Gets the time fmt str.
         *
         * @param _formater the formater
         * @return the time fmt str
         * @throws EFapsException on error
         */
        public String getTimeFmtStr(final Format _formater)
            throws EFapsException
        {
            return time == null ? "" : _formater.format(getTime());
        }

        @Override
        public BigDecimal getNetPrice()
            throws EFapsException
        {
            BigDecimal ret = super.getNetPrice();
            ret = getParticipation().setScale(8, RoundingMode.HALF_UP)
                            .divide(new BigDecimal(100), RoundingMode.HALF_UP).multiply(getTime()).multiply(ret);
            return ret;
        }

        @Override
        public BigDecimal getCrossPrice()
            throws EFapsException
        {
            BigDecimal ret = super.getCrossPrice();
            ret = getParticipation().setScale(8, RoundingMode.HALF_UP)
                            .divide(new BigDecimal(100), RoundingMode.HALF_UP).multiply(getTime()).multiply(ret);
            return ret;
        }
    }
}
