/*
 * Copyright © 2003 - 2024 The eFaps Team (-)
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
 */
package org.efaps.esjp.construction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.efaps.admin.datamodel.Attribute;
import org.efaps.admin.datamodel.Dimension;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.ui.AbstractCommand;
import org.efaps.admin.ui.AbstractUserInterfaceObject.TargetMode;
import org.efaps.db.AttributeQuery;
import org.efaps.db.CachedPrintQuery;
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
import org.efaps.esjp.common.jasperreport.StandartReport;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.esjp.common.uiform.Field;
import org.efaps.esjp.common.uiform.Field_Base.DropDownPosition;
import org.efaps.esjp.common.uiform.Field_Base.ListType;
import org.efaps.esjp.common.uitable.CommonDelete;
import org.efaps.esjp.common.uitable.MultiPrint;
import org.efaps.esjp.common.util.InterfaceUtils;
import org.efaps.esjp.construction.util.Construction;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.esjp.erp.AbstractWarning;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.erp.IWarning;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.erp.Revision;
import org.efaps.esjp.erp.WarningUtil;
import org.efaps.esjp.products.Product;
import org.efaps.esjp.projects.document.Naming;
import org.efaps.esjp.sales.Calculator;
import org.efaps.esjp.sales.PriceUtil;
import org.efaps.esjp.sales.PriceUtil_Base.ProductPrice;
import org.efaps.esjp.sales.document.AbstractDocumentSum;
import org.efaps.esjp.sales.tax.TaxCat;
import org.efaps.esjp.sales.tax.TaxesAttribute;
import org.efaps.esjp.ui.html.Table;
import org.efaps.ui.wicket.util.EFapsKey;
import org.efaps.util.EFapsException;
import org.efaps.util.cache.InfinispanCache;
import org.infinispan.AdvancedCache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 *
 * @author Jan Moxter
 */
@EFapsUUID("18cf4ce4-fda3-420e-b864-01da4b79a3f6")
@EFapsApplication("eFapsApp-Construction")
public abstract class CostEstimate_Base
    extends AbstractDocumentSum
{
    /**
     * Instance key.
     */
    protected static final String COESTINSTKEY = CostEstimate.class.getName() + ".CostEstimateInstanceKey";

    /**
     * Cache for calculator key.
     */
    protected static final String CALCCACHE = CostEstimate.class.getName() + ".CalculatorCache";

    /**
     * Logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(CostEstimate.class);

    protected static boolean CACHEINIT = false;

    protected void initCache()
    {
        if (!CACHEINIT && ((EmbeddedCacheManager) InfinispanCache.get().getContainer()).getCacheConfiguration(
                        CostEstimate.CALCCACHE) == null) {
            ((EmbeddedCacheManager) InfinispanCache.get().getContainer()).defineConfiguration(CostEstimate.CALCCACHE,
                            new ConfigurationBuilder().build());
            CACHEINIT = true;
        }
    }

    @Override
    public Return activatePositionsCalculator(final Parameter _parameter)
        throws EFapsException
    {
        super.activatePositionsCalculator(_parameter);
        Context.getThreadContext().setSessionAttribute(CostEstimate.COESTINSTKEY, _parameter.getInstance());
        return new Return();
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return new empty Return
     * @throws EFapsException on error
     */
    public Return create(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final CreatedDoc createdDoc = createDoc(_parameter);

        final Instance projectInst = Instance.get(_parameter
                        .getParameterValue(CIFormConstruction.Construction_CostEstimateBaseForm.project.name));
        if (projectInst.isValid() && createdDoc != null) {
            final PrintQuery print = new PrintQuery(projectInst);
            print.addAttribute(CIProjects.ProjectService.Contact);
            print.execute();
            final Long contactid = print.<Long>getAttribute(CIProjects.ProjectService.Contact);
            final Update update = new Update(createdDoc.getInstance());
            update.add(CIConstruction.CostEstimateAbstract.Contact, contactid);
            update.execute();
        }
        insertRelation(_parameter, createdDoc);
        return ret;
    }

    @Override
    protected void add2DocCreate(final Parameter _parameter,
                                 final Insert _insert,
                                 final CreatedDoc _createdDoc)
        throws EFapsException
    {
        super.add2DocCreate(_parameter, _insert, _createdDoc);
        _insert.add(CIConstruction.CostEstimateAbstract.Annotation, _parameter
                        .getParameterValue(CIFormConstruction.Construction_CostEstimateCreateForm.annotation.name));
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return empty Return
     * @throws EFapsException on error
     */
    public Return edit(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        initCache();
        InfinispanCache.get().getIgnReCache(CostEstimate.CALCCACHE).clear();

        // update the main document
        editDoc(_parameter);

        @SuppressWarnings("unchecked")
        final Map<String, String> oidMap = (Map<String, String>) _parameter.get(ParameterValues.OIDMAP4UI);
        final String[] rowKeys = _parameter.getParameterValues(EFapsKey.TABLEROW_NAME.getKey());
        final String[] prodDescs = _parameter.getParameterValues(
                        CITableConstruction.Construction_CostEstimatePositionTable.productDesc.name);
        final String[] codes = _parameter.getParameterValues(
                        CITableConstruction.Construction_CostEstimatePosition4PositionGroupTable.code.name);

        final Instance baseCurrInst = Currency.getBaseCurrency();

        final PrintQuery docPrint = new PrintQuery(_parameter.getInstance());
        docPrint.addAttribute(CIConstruction.CostEstimateAbstract.RateCurrencyId);
        docPrint.executeWithoutAccessCheck();

        Long rateCurrId = docPrint.getAttribute(CIConstruction.CostEstimateAbstract.RateCurrencyId);
        if (rateCurrId == null) {
            rateCurrId = baseCurrInst.getId();
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

            final Update update = new Update(inst);
            update.add(CIConstruction.PositionGroupAbstract.Name, prodDescs[i]);
            update.add(CIConstruction.PositionGroupAbstract.Code, codes[i]);
            update.add(CIConstruction.PositionGroupAbstract.Order, i);
            update.execute();

            if (inst.getType().isKindOf(CIConstruction.PositionGroupItem.getType())) {
                final PrintQuery print = new PrintQuery(inst);
                final SelectBuilder sel = new SelectBuilder()
                                .linkto(CIConstruction.PositionGroupAbstract.AbstractPositionAbstractLink).oid();
                print.addSelect(sel);
                print.executeWithoutAccessCheck();
                final Instance posInst = Instance.get(print.<String>getSelect(sel));
                final Update posUpdate = new Update(posInst);
                final Long productdId = Instance.get(_parameter.getParameterValues("product")[i]).getId();
                posUpdate.add(CISales.PositionSumAbstract.PositionNumber, i);
                posUpdate.add(CISales.PositionSumAbstract.Product, productdId.toString());
                posUpdate.add(CISales.PositionSumAbstract.ProductDesc, prodDescs[i]);
                posUpdate.add(CISales.PositionSumAbstract.Quantity, calc.getQuantity());
                posUpdate.add(CISales.PositionSumAbstract.UoM, _parameter.getParameterValues("uoM")[i]);
                posUpdate.add(CISales.PositionSumAbstract.CrossUnitPrice, calc.getCrossUnitPrice()
                                .divide(rate, 12, RoundingMode.HALF_UP)
                                .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
                posUpdate.add(CISales.PositionSumAbstract.NetUnitPrice, calc.getNetUnitPrice()
                                .divide(rate, 12, RoundingMode.HALF_UP)
                                .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
                posUpdate.add(CISales.PositionSumAbstract.CrossPrice, calc.getCrossPrice()
                                .divide(rate, 12, RoundingMode.HALF_UP)
                                .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
                posUpdate.add(CISales.PositionSumAbstract.NetPrice, calc.getNetPrice()
                                .divide(rate, 12, RoundingMode.HALF_UP)
                                .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
                posUpdate.add(CISales.PositionSumAbstract.Tax, calc.getTaxCatId());
                posUpdate.add(CISales.PositionSumAbstract.Discount, calc.getDiscountStr());
                posUpdate.add(CISales.PositionSumAbstract.DiscountNetUnitPrice, calc.getDiscountNetUnitPrice()
                                .divide(rate, 12, RoundingMode.HALF_UP)
                                .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
                posUpdate.add(CISales.PositionSumAbstract.CurrencyId, baseCurrInst.getId());
                posUpdate.add(CISales.PositionSumAbstract.Rate, rateObj);
                posUpdate.add(CISales.PositionSumAbstract.RateCurrencyId, rateCurrId);
                posUpdate.add(CISales.PositionSumAbstract.RateNetUnitPrice, calc.getNetUnitPrice()
                                .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
                posUpdate.add(CISales.PositionSumAbstract.RateCrossUnitPrice, calc.getCrossUnitPrice()
                                .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
                posUpdate.add(CISales.PositionSumAbstract.RateDiscountNetUnitPrice, calc.getDiscountNetUnitPrice()
                                .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
                posUpdate.add(CISales.PositionSumAbstract.RateNetPrice,
                                calc.getNetPrice().setScale(unitFrmt.getMaximumFractionDigits(),
                                                RoundingMode.HALF_UP));
                posUpdate.add(CISales.PositionSumAbstract.RateCrossPrice,
                                calc.getCrossPrice().setScale(unitFrmt.getMaximumFractionDigits(),
                                                RoundingMode.HALF_UP));
                posUpdate.execute();
            }
        }
        updateCostEstimate4Edit(_parameter, _parameter.getInstance(), null);
        updateTotals4CostEstimateGroups(_parameter, _parameter.getInstance());
        return ret;
    }

    @Override
    protected void add2DocEdit(final Parameter _parameter,
                               final Update _update,
                               final EditedDoc _editDoc)
        throws EFapsException
    {
        super.add2DocEdit(_parameter, _update, _editDoc);
        _update.add(CIConstruction.CostEstimateAbstract.Annotation, _parameter
                        .getParameterValue(CIFormConstruction.Construction_CostEstimateCreateForm.annotation.name));
    }

    @Override
    protected String getDocName4Create(final Parameter _parameter)
        throws EFapsException
    {
        final String ret;
        final Instance projectInst = Instance.get(_parameter
                        .getParameterValue(CIFormConstruction.Construction_CostEstimateBaseForm.project.name));
        if (projectInst.isValid()) {
            ret = Naming.getName(projectInst, getType4DocCreate(_parameter), true);
        } else {
            final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.ProjectService2CostEstimate);
            queryBldr.addWhereAttrEqValue(CIConstruction.ProjectService2CostEstimate.ToLink, _parameter.getInstance().getId());
            final InstanceQuery query = queryBldr.getQuery();
            final List<Instance> instances = query.execute();
            if (!instances.isEmpty() && instances.get(0).isValid()) {
                final PrintQuery print = new PrintQuery(instances.get(0));
                final SelectBuilder sel = new SelectBuilder().linkto(CIConstruction.ProjectService2CostEstimate.FromLink)
                                .instance();
                print.addSelect(sel);
                print.execute();
                final Instance inst = (Instance) print.getSelect(sel);
                if (inst.isValid()) {
                    ret = Naming.getName(inst, getType4DocCreate(_parameter), true);
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
     * @param _parameter Parameter as passed by the eFaps API
     * @param _createdDoc created Doc
     * @throws EFapsException on error
     */
    protected void insertRelation(final Parameter _parameter,
                                  final CreatedDoc _createdDoc)
        throws EFapsException
    {
        final Instance projInst = Instance
                        .get(_parameter.getParameterValue(CIFormConstruction.Construction_CostEstimateBaseForm.project.name));
        if (projInst.isValid()) {
            final QueryBuilder queryRoot = new QueryBuilder(CIConstruction.PositionGroupRoot);
            queryRoot.addWhereAttrEqValue(CIConstruction.PositionGroupRoot.DocumentAbstractLink, _createdDoc.getInstance());
            final InstanceQuery instanceQuery = queryRoot.getQuery();
            if (instanceQuery.execute().isEmpty()) {
                final Insert insert = new Insert(CIConstruction.PositionGroupRoot);
                insert.add(CIConstruction.PositionGroupAbstract.DocumentAbstractLink, _createdDoc.getInstance());
                insert.add(CIConstruction.PositionGroupAbstract.Name,
                               DBProperties.getProperty(CostEstimate.class.getName() + ".GoupRootDescription4Default"));
                insert.add(CIConstruction.PositionGroupAbstract.Level, 1);
                insert.add(CIConstruction.PositionGroupAbstract.Order, 1);
                insert.execute();
            }

            final Insert relInsert = new Insert(CIConstruction.ProjectService2CostEstimate);
            relInsert.add(CIConstruction.ProjectService2CostEstimate.FromLink, projInst);
            relInsert.add(CIConstruction.ProjectService2CostEstimate.ToLink, _createdDoc.getInstance());
            relInsert.execute();

            final String[] entrySheets = _parameter.getParameterValues("entrySheets");
            if (entrySheets != null && entrySheets.length > 0) {
                for (final String costSheetIterator : entrySheets) {
                    final Instance costSheet = Instance.get(costSheetIterator);
                    if (costSheet.isValid()) {
                        final Insert relInsert2 = new Insert(CIConstruction.CostEstimate2EntrySheet);
                        relInsert2.add(CIConstruction.CostEstimate2EntrySheet.FromLink, _createdDoc.getInstance());
                        relInsert2.add(CIConstruction.CostEstimate2EntrySheet.ToLink, costSheet);
                        relInsert2.execute();
                    }
                }
            }

            updateTotals4CostEstimateGroups(_parameter, _createdDoc.getInstance());
        }
    }

    /**
     * Update the total amounts for the group positions.
     * @param _parameter Parameter as passed by the eFaps API
     * @param _costEstimateInst Instance of the costEstimate
     * @throws EFapsException on error
     */
    public void updateTotals4CostEstimateGroups(final Parameter _parameter,
                                                final Instance _costEstimateInst)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.PositionGroupRoot);
        queryBldr.addWhereAttrEqValue(CIConstruction.PositionGroupRoot.DocumentAbstractLink, _costEstimateInst);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.execute();
        final DecimalFormat unitFrmt = NumberFormatter.get().getFrmt4UnitPrice(getType4SysConf(_parameter));
        while (multi.next()) {
            final BigDecimal[] totals = getTotals(_parameter, multi.getCurrentInstance());
            final Update update = new Update(multi.getCurrentInstance());
            update.add(CIConstruction.PositionGroupAbstract.SumsTotal,
                            totals[1].setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
            update.add(CIConstruction.PositionGroupAbstract.RateSumsTotal,
                            totals[0].setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
            update.executeWithoutTrigger();
        }
    }

    /**
     * Recursive method to sums the "tree".
     * @param _parameter Parameter as passed by the eFaps API
     * @param _groupInstance Instance of the group
     * @return array with RateTotal, Total
     * @throws EFapsException on error
     */
    protected BigDecimal[] getTotals(final Parameter _parameter,
                                     final Instance _groupInstance)
        throws EFapsException
    {
        final BigDecimal[] ret = new BigDecimal[]{ BigDecimal.ZERO, BigDecimal.ZERO};
        final SelectBuilder selNetPrice = new SelectBuilder()
                        .linkto(CIConstruction.PositionGroupAbstract.AbstractPositionLink)
                        .attribute(CISales.PositionSumAbstract.NetPrice);
        final SelectBuilder selRateNetPrice = new SelectBuilder()
            .linkto(CIConstruction.PositionGroupAbstract.AbstractPositionLink)
            .attribute(CISales.PositionSumAbstract.RateNetPrice);

        final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.PositionGroupNode);
        queryBldr.addWhereAttrEqValue(CIConstruction.PositionGroupNode.ParentGroupLink, _groupInstance);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addSelect(selNetPrice, selRateNetPrice);
        multi.execute();
        final DecimalFormat unitFrmt = NumberFormatter.get().getFrmt4UnitPrice(getType4SysConf(_parameter));
        while (multi.next()) {
            final Instance childInst = multi.getCurrentInstance();
            if (childInst.getType().isKindOf(CIConstruction.PositionGroupItem.getType())) {
                final BigDecimal rateNetPrice = multi.<BigDecimal>getSelect(selRateNetPrice);
                final BigDecimal netPrice = multi.<BigDecimal>getSelect(selNetPrice);
                ret[0] = ret[0].add(rateNetPrice == null ? BigDecimal.ZERO : rateNetPrice);
                ret[1] = ret[1].add(netPrice == null ? BigDecimal.ZERO : netPrice);
            } else {
                final BigDecimal[] totals = getTotals(_parameter, childInst);
                ret[0] = ret[0].add(totals[0]);
                ret[1] = ret[1].add(totals[1]);
                final Update update = new Update(childInst);
                update.add(CIConstruction.PositionGroupAbstract.SumsTotal,
                                totals[1].setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
                update.add(CIConstruction.PositionGroupAbstract.RateSumsTotal,
                                totals[0].setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
                update.executeWithoutTrigger();
            }
        }
        return ret;
    }

    @Override
    public Return autoComplete4Product(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final String input = (String) _parameter.get(ParameterValues.OTHERS);
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);

        final Map<String, Map<String, String>> orderMap = new TreeMap<>();
        if (!input.isEmpty()) {
            final Instance inst;
            if (InstanceUtils.isKindOf(_parameter.getInstance(), CIConstruction.CostEstimateAbstract)) {
                inst = _parameter.getInstance();
            } else if (InstanceUtils.isKindOf(_parameter.getCallInstance(), CIConstruction.CostEstimateAbstract)) {
                inst = _parameter.getCallInstance();
            } else {
                inst = (Instance) Context.getThreadContext().getSessionAttribute(CostEstimate.COESTINSTKEY);
            }
            final boolean nameSearch = Character.isDigit(input.charAt(0));
            final QueryBuilder attrQueryBldr = new QueryBuilder(CIConstruction.CostEstimate2EntrySheet);
            attrQueryBldr.addWhereAttrEqValue(CIConstruction.CostEstimate2EntrySheet.FromLink, inst);
            final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CIConstruction.CostEstimate2EntrySheet.ToLink);

            final QueryBuilder attrQueryBldr2 = new QueryBuilder(CIProducts.ProductAbstract);
            if (nameSearch) {
                attrQueryBldr2.addWhereAttrMatchValue(CIProducts.ProductAbstract.Name, input + "*")
                                .setIgnoreCase(true);
            } else {
                attrQueryBldr2.addWhereAttrMatchValue(CIProducts.ProductAbstract.Description, input + "*")
                                .setIgnoreCase(true);
            }
            final AttributeQuery attrQuery2 = attrQueryBldr2.getAttributeQuery(CIProducts.ProductAbstract.ID);

            final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.EntrySheetPosition);
            queryBldr.addWhereAttrInQuery(CIConstruction.EntrySheetPosition.EntrySheetLink, attrQuery);
            queryBldr.addWhereAttrInQuery(CIConstruction.EntrySheetPosition.Product, attrQuery2);
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder select1 = new SelectBuilder().linkto(CIConstruction.EntrySheetPosition.Product)
                            .attribute(CIProducts.ProductAbstract.OID);
            multi.addSelect(select1);
            multi.execute();
            while (multi.next()) {
                final String prodOid = multi.<String>getSelect(select1);
                final PrintQuery print = new PrintQuery(prodOid);
                print.addAttribute(CIProducts.ProductAbstract.Name,
                                CIProducts.ProductAbstract.Description,
                                CIProducts.ProductAbstract.Dimension);
                print.execute();
                final String name = print.<String>getAttribute(CIProducts.ProductAbstract.Name);
                final String desc = print.<String>getAttribute(CIProducts.ProductAbstract.Description);
                final Map<String, String> map = new HashMap<>();
                final String choice = name + "- " + desc;
                if (props.containsKey("ReturnPositionOid")
                                && Boolean.parseBoolean((String) props.get("ReturnPositionOid"))) {
                    map.put(EFapsKey.AUTOCOMPLETE_KEY.getKey(), multi.getCurrentInstance().getOid());
                } else {
                    map.put(EFapsKey.AUTOCOMPLETE_KEY.getKey(), prodOid);
                }
                map.put(EFapsKey.AUTOCOMPLETE_VALUE.getKey(), name);
                map.put(EFapsKey.AUTOCOMPLETE_CHOICE.getKey(), choice);
                orderMap.put(choice, map);
            }
        }
        final List<Map<String, String>> list = new ArrayList<>();
        list.addAll(orderMap.values());
        ret.put(ReturnValues.VALUES, list);
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return Return conting map fo rautocpomlete
     * @throws EFapsException on error
     */
    public Return autoComplete4ProductsUsed(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final String input = (String) _parameter.get(ParameterValues.OTHERS);
        _parameter.get(ParameterValues.PROPERTIES);

        final Map<String, Map<String, String>> orderMap = new TreeMap<>();
        if (!input.isEmpty()) {
            final boolean nameSearch = Character.isDigit(input.charAt(0));

            final QueryBuilder quotAttrQueryBldr = new QueryBuilder(CIConstruction.CostEstimate2EntrySheet);
            quotAttrQueryBldr.addWhereAttrEqValue(CIConstruction.CostEstimate2EntrySheet.FromLink, _parameter.getInstance());
            final AttributeQuery quotAttrQuery = quotAttrQueryBldr
                            .getAttributeQuery(CIConstruction.CostEstimate2EntrySheet.ToLink);

            final QueryBuilder posAttrQueryBldr = new QueryBuilder(CISales.PositionAbstract);
            posAttrQueryBldr.addWhereAttrInQuery(CISales.PositionAbstract.DocumentAbstractLink, quotAttrQuery);
            final AttributeQuery posAttrQuery = posAttrQueryBldr.getAttributeQuery(CISales.PositionAbstract.Product);

            final QueryBuilder entryAttrQueryBldr = new QueryBuilder(CIConstruction.EntryPartList);
            entryAttrQueryBldr.addWhereAttrInQuery(CIConstruction.EntryPartList.ID, posAttrQuery);
            final AttributeQuery entryAttrQuery = entryAttrQueryBldr.getAttributeQuery(CIConstruction.EntryPartList.ID);

            final QueryBuilder bomAttrQueryBldr = new QueryBuilder(CIConstruction.EntryBOM);
            bomAttrQueryBldr.addWhereAttrInQuery(CIConstruction.EntryBOM.FromLink, entryAttrQuery);
            final AttributeQuery bomAttrQuery = bomAttrQueryBldr.getAttributeQuery(CIConstruction.EntryBOM.ToLink);

            final QueryBuilder prodAttrQueryBldr = new QueryBuilder(CIProducts.ProductAbstract);
            prodAttrQueryBldr.setOr(true);
            prodAttrQueryBldr.addWhereAttrInQuery(CIProducts.ProductAbstract.ID, bomAttrQuery);
            prodAttrQueryBldr.addWhereAttrInQuery(CIProducts.ProductAbstract.ID, posAttrQuery);
            final AttributeQuery prodAttrQuery = prodAttrQueryBldr.getAttributeQuery(CIProducts.ProductAbstract.ID);

            final QueryBuilder queryBldr = new QueryBuilder(CIProducts.ProductAbstract);
            queryBldr.addWhereAttrNotInQuery(CIProducts.ProductAbstract.ID, entryAttrQuery);
            queryBldr.addWhereAttrInQuery(CIProducts.ProductAbstract.ID, prodAttrQuery);
            if (nameSearch) {
                queryBldr.addWhereAttrMatchValue(CIProducts.ProductAbstract.Name, input + "*")
                                .setIgnoreCase(true);
            } else {
                queryBldr.addWhereAttrMatchValue(CIProducts.ProductAbstract.Description, input + "*")
                                .setIgnoreCase(true);
            }
            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.addAttribute(CIProducts.ProductAbstract.Name,
                            CIProducts.ProductAbstract.Description,
                            CIProducts.ProductAbstract.Dimension);
            multi.execute();
            while (multi.next()) {

                final String name = multi.<String>getAttribute(CIProducts.ProductAbstract.Name);
                final String desc = multi.<String>getAttribute(CIProducts.ProductAbstract.Description);
                final Map<String, String> map = new HashMap<>();
                final String choice = name + "- " + desc;
                map.put(EFapsKey.AUTOCOMPLETE_KEY.getKey(), multi.getCurrentInstance().getOid());
                map.put(EFapsKey.AUTOCOMPLETE_VALUE.getKey(), name);
                map.put(EFapsKey.AUTOCOMPLETE_CHOICE.getKey(), choice);
                map.put("productDesc", desc);
                orderMap.put(choice, map);
            }
        }
        final List<Map<String, String>> list = new ArrayList<>();
        list.addAll(orderMap.values());
        ret.put(ReturnValues.VALUES, list);
        return ret;
    }

    /**
     * Create a position with default values when a position item group is created.
     *
     * @param _parameter as passed from eFaps API.
     * @param _docInst Instance of the current document.
     * @param _nameDesc Description of the position product.
     * @return Instance of the position created.
     * @throws EFapsException on error.
     */
    protected Instance createDefaultPosition(final Parameter _parameter,
                                             final Instance _docInst,
                                             final String _nameDesc)
        throws EFapsException
    {
        final Instance baseCurrInst = Currency.getBaseCurrency();
        final PrintQuery docPrint = new PrintQuery(_docInst);
        docPrint.addAttribute(CISales.DocumentSumAbstract.RateCurrencyId, CISales.DocumentSumAbstract.Rate);
        docPrint.executeWithoutAccessCheck();

        final Instance entrySheetPosInst = Instance.get(_parameter
                        .getParameterValue(CIFormConstruction.Construction_CostEstimateGroupItemForm.product.name));

        final PrintQuery print = new PrintQuery(entrySheetPosInst);
        final SelectBuilder selProdInst = new SelectBuilder().linkto(CIConstruction.EntrySheetPosition.Product).instance();
        final SelectBuilder selProdDim = new SelectBuilder().linkto(CIConstruction.EntrySheetPosition.Product)
                        .attribute(CIProducts.ProductAbstract.Dimension);
        final SelectBuilder selProdTax = new SelectBuilder().linkto(CIConstruction.EntrySheetPosition.Product)
                        .attribute(CIProducts.ProductAbstract.TaxCategory);
        print.addSelect(selProdDim, selProdInst, selProdTax);
        print.execute();
        final Instance productdInst = print.<Instance>getSelect(selProdInst);
        final Long uoM = Dimension.get(print.<Long>getSelect(selProdDim)).getBaseUoM().getId();

        final int qty = Construction.DEFAULTCOSTESTIMATEPOSQTY.get();

        final Insert insert = new Insert(getPositionType4DefaultPosition(_parameter));
        insert.add(CISales.PositionSumAbstract.DocumentAbstractLink, _docInst);
        insert.add(CISales.PositionSumAbstract.PositionNumber, 1);
        insert.add(CISales.PositionSumAbstract.Product, productdInst);
        insert.add(CISales.PositionSumAbstract.ProductDesc, _nameDesc);
        insert.add(CISales.PositionSumAbstract.Quantity,
                        productdInst.getType().isKindOf(CIConstruction.EntryPartList.getType()) ? qty : BigDecimal.ONE);
        insert.add(CISales.PositionSumAbstract.UoM, uoM);
        insert.add(CISales.PositionSumAbstract.CrossUnitPrice, BigDecimal.ZERO);
        insert.add(CISales.PositionSumAbstract.NetUnitPrice, BigDecimal.ZERO);
        insert.add(CISales.PositionSumAbstract.CrossPrice, BigDecimal.ZERO);
        insert.add(CISales.PositionSumAbstract.NetPrice, BigDecimal.ZERO);
        insert.add(CISales.PositionSumAbstract.Tax, print.<Object>getSelect(selProdTax));
        insert.add(CISales.PositionSumAbstract.Discount, BigDecimal.ZERO);
        insert.add(CISales.PositionSumAbstract.DiscountNetUnitPrice, BigDecimal.ZERO);
        insert.add(CISales.PositionSumAbstract.CurrencyId, baseCurrInst.getId());
        insert.add(CISales.PositionSumAbstract.Rate, docPrint.<Object>getAttribute(CISales.DocumentSumAbstract.Rate));
        insert.add(CISales.PositionSumAbstract.RateCurrencyId,
                        docPrint.<Long>getAttribute(CISales.DocumentSumAbstract.RateCurrencyId));
        insert.add(CISales.PositionSumAbstract.RateNetUnitPrice, BigDecimal.ZERO);
        insert.add(CISales.PositionSumAbstract.RateCrossUnitPrice, BigDecimal.ZERO);
        insert.add(CISales.PositionSumAbstract.RateDiscountNetUnitPrice, BigDecimal.ZERO);
        insert.add(CISales.PositionSumAbstract.RateNetPrice, BigDecimal.ZERO);
        insert.add(CISales.PositionSumAbstract.RateCrossPrice, BigDecimal.ZERO);
        if (!productdInst.getType().isKindOf(CIConstruction.EntryPartList.getType())) {
            insert.add(CIConstruction.CostEstimatePositionAbstract.EntrySheetPositionLink, entrySheetPosInst);
        }
        insert.execute();
        return insert.getInstance();
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return type
     */
    protected Type getPositionType4DefaultPosition(final Parameter _parameter)
    {
        final Instance inst = _parameter.getInstance();
        Type ret = null;
        if (inst.getType().isKindOf(CIConstruction.CostEstimateBase.getType())) {
            ret = CIConstruction.CostEstimatePositionBase.getType();
        } else if (inst.getType().isKindOf(CIConstruction.CostEstimateSale.getType())) {
            ret = CIConstruction.CostEstimatePositionSale.getType();
        } else  if (inst.getType().isKindOf(CIConstruction.CostEstimateTarget.getType())) {
            ret = CIConstruction.CostEstimatePositionTarget.getType();
        } else  if (inst.getType().isKindOf(CIConstruction.CostEstimateRealization.getType())) {
            ret = CIConstruction.CostEstimatePositionRealization.getType();
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return Return with Snipllet
     * @throws EFapsException on error
     */
    public Return getCheckBoxList4ProductPricePercent(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final List<DropDownPosition> values = new ArrayList<>();
        for (final PositionType pt : PositionType.values()) {
            final DropDownPosition pos = new DropDownPosition(pt.toString(), pt.getLabel());
            pos.setSelected(true);
            values.add(pos);
        }
        ret.put(ReturnValues.SNIPLETT, new Field().getInputField(_parameter, values, ListType.CHECKBOX));
        return ret;
    }

    /**
     * Update all product price percentual in the related PartLists and updates all
     * EntrySheet etc.
     * @param _parameter Parameter as passed by the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return updateProductPricePercent(final Parameter _parameter)
        throws EFapsException
    {
        final DecimalFormat formatter = NumberFormatter.get().getFormatter();
        BigDecimal percent = null;
        try {
            percent = (BigDecimal) formatter.parse(_parameter
                            .getParameterValue(CIFormConstruction.Construction_CostEstimateUpdateProductPricePercentForm.percent.name));
        } catch (final ParseException e) {
            LOG.error("Catched ParseException", e);
        }
        final String[] posTypesArr = _parameter
                        .getParameterValues(CIFormConstruction.Construction_CostEstimateUpdateProductPricePercentForm.positionType.name);
        if (percent != null && posTypesArr != null) {
            final List<String> posTypes = Arrays.asList(posTypesArr);
            final QueryBuilder quotAttrQueryBldr = new QueryBuilder(CIConstruction.CostEstimate2EntrySheet);
            quotAttrQueryBldr.addWhereAttrEqValue(CIConstruction.CostEstimate2EntrySheet.FromLink, _parameter.getInstance());
            final AttributeQuery quotAttrQuery = quotAttrQueryBldr
                            .getAttributeQuery(CIConstruction.CostEstimate2EntrySheet.ToLink);

            final QueryBuilder posAttrQueryBldr = new QueryBuilder(CISales.PositionAbstract);
            posAttrQueryBldr.addWhereAttrInQuery(CISales.PositionAbstract.DocumentAbstractLink, quotAttrQuery);
            final AttributeQuery posAttrQuery = posAttrQueryBldr.getAttributeQuery(CISales.PositionAbstract.Product);

            final QueryBuilder entryAttrQueryBldr = new QueryBuilder(CIConstruction.EntryPartList);
            entryAttrQueryBldr.addWhereAttrInQuery(CIConstruction.EntryPartList.ID, posAttrQuery);
            final AttributeQuery entryAttrQuery = entryAttrQueryBldr.getAttributeQuery(CIConstruction.EntryPartList.ID);

            final QueryBuilder bomQueryBldr = new QueryBuilder(CIConstruction.EntryBOM);
            bomQueryBldr.addWhereAttrInQuery(CIConstruction.EntryBOM.FromLink, entryAttrQuery);
            final MultiPrintQuery multi = bomQueryBldr.getPrint();
            final SelectBuilder selProdInst = SelectBuilder.get().linkto(CIConstruction.EntryBOM.ToLink).instance();
            final SelectBuilder selProdClass = SelectBuilder.get().linkto(CIConstruction.EntryBOM.ToLink)
                            .clazz(CIConstruction.ProductServiceClassPersonal).id();
            multi.addSelect(selProdInst, selProdClass);
            multi.addAttribute(CIConstruction.EntryBOM.UnitPrice);
            multi.execute();
            while (multi.next()) {
                BigDecimal price = multi.<BigDecimal>getAttribute(CIConstruction.EntryBOM.UnitPrice);
                final Instance prodInst = multi.<Instance>getSelect(selProdInst);
                boolean update = false;
                // if all wre selected no further check needed
                if (posTypesArr.length == PositionType.values().length) {
                    update = true;
                    // if material was selected and it is material
                } else if (prodInst.getType().isKindOf(CIProducts.ProductMaterial.getType())
                                && posTypes.contains(PositionType.MATERIAL.toString())) {
                    update = true;
                } else if (multi.<Long>getSelect(selProdClass) != null
                                && posTypes.contains(PositionType.PERSONAL.toString())) {
                    update = true;
                } else if (multi.<Long>getSelect(selProdClass) == null
                                && posTypes.contains(PositionType.TOOLS.toString())) {
                    update = true;
                }
                if (update) {
                    price = price.multiply(BigDecimal.ONE.add(percent.setScale(12, RoundingMode.HALF_UP)
                                    .divide(new BigDecimal(100), RoundingMode.HALF_UP)));
                    updateProductPrice(_parameter, prodInst, price);
                }
            }
        }
        return new Return();
    }

    /**
     * Update the product price in the related PartLists and updates all
     * EntrySheet etc.
     * @param _parameter Parameter as passed by the eFaps API
     * @param _prodInst instance of the product the price will be updated
     * @param _price price for the product
     * @throws EFapsException on error
     */
    protected void updateProductPrice(final Parameter _parameter,
                                      final Instance _prodInst,
                                      final BigDecimal _price)
        throws EFapsException
    {
        final Set<Instance> entryPListInsts = new HashSet<>();
        if (_prodInst.isValid()) {
            final QueryBuilder quotAttrQueryBldr = new QueryBuilder(CIConstruction.CostEstimate2EntrySheet);
            quotAttrQueryBldr.addWhereAttrEqValue(CIConstruction.CostEstimate2EntrySheet.FromLink, _parameter.getInstance());
            final AttributeQuery quotAttrQuery = quotAttrQueryBldr
                            .getAttributeQuery(CIConstruction.CostEstimate2EntrySheet.ToLink);

            final QueryBuilder posQueryBldr = new QueryBuilder(CISales.PositionAbstract);
            posQueryBldr.addWhereAttrInQuery(CISales.PositionAbstract.DocumentAbstractLink, quotAttrQuery);
            posQueryBldr.addWhereAttrEqValue(CISales.PositionAbstract.Product, _prodInst);
            final InstanceQuery query = posQueryBldr.getQuery();
            if (!query.execute().isEmpty()) {
                LOG.warn("That is not expected to happen!!!", _parameter);
            } else {
                final QueryBuilder posAttrQueryBldr = new QueryBuilder(CISales.PositionAbstract);
                posAttrQueryBldr.addWhereAttrInQuery(CISales.PositionAbstract.DocumentAbstractLink, quotAttrQuery);
                final AttributeQuery posAttrQuery = posAttrQueryBldr
                                .getAttributeQuery(CISales.PositionAbstract.Product);

                final QueryBuilder entryAttrQueryBldr = new QueryBuilder(CIConstruction.EntryPartList);
                entryAttrQueryBldr.addWhereAttrInQuery(CIConstruction.EntryPartList.ID, posAttrQuery);
                final AttributeQuery entryAttrQuery = entryAttrQueryBldr.getAttributeQuery(CIConstruction.EntryPartList.ID);

                final QueryBuilder bomQueryBldr = new QueryBuilder(CIConstruction.EntryBOM);
                bomQueryBldr.addWhereAttrInQuery(CIConstruction.EntryBOM.FromLink, entryAttrQuery);
                bomQueryBldr.addWhereAttrEqValue(CIConstruction.EntryBOM.ToLink, _prodInst);
                final InstanceQuery bomQuery = bomQueryBldr.getQuery();
                bomQuery.execute();
                while (bomQuery.next()) {
                    entryPListInsts.add(new EntryPartList()
                                    .updateProductPrice(_parameter, bomQuery.getCurrentValue(), _price));
                }
            }
        }
        // update the EntrySheets
        if (!entryPListInsts.isEmpty()) {
            final Set<Instance> entrySheetInsts = new HashSet<>();
            final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionAbstract);
            queryBldr.addWhereAttrEqValue(CISales.PositionAbstract.Product, entryPListInsts.toArray());
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selInst = SelectBuilder.get().linkto(CISales.PositionAbstract.DocumentAbstractLink)
                            .instance();
            multi.addSelect(selInst);
            multi.execute();
            while (multi.next()) {
                final Instance inst = multi.<Instance>getSelect(selInst);
                if (inst != null && inst.isValid() && inst.getType().isKindOf(CIConstruction.EntrySheet.getType())) {
                    entrySheetInsts.add(inst);
                }
            }
            if (!entrySheetInsts.isEmpty()) {
                new EntrySheet().updateTotals(_parameter,
                                entrySheetInsts.toArray(new Instance[entrySheetInsts.size()]));
            }
        }
    }

    /**
     * Update the product price in the related PartLists and updates all
     * EntrySheet etc.
     * @param _parameter Parameter as passed by the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return updateProductPrice(final Parameter _parameter)
        throws EFapsException
    {
        final Instance prodInst = Instance.get(_parameter
                        .getParameterValue(CIFormConstruction.Construction_CostEstimateUpdateProductPriceForm.product.name));

        final DecimalFormat formatter = NumberFormatter.get().getFormatter();
        BigDecimal price = BigDecimal.ZERO;
        try {
            price = (BigDecimal) formatter.parse(_parameter
                            .getParameterValue(CIFormConstruction.Construction_CostEstimateUpdateProductPriceForm.price.name));
        } catch (final ParseException e) {
            LOG.error("Catched ParseException", e);
        }
        updateProductPrice(_parameter, prodInst, price);
        return new Return();
    }

    /**
     * Update cost estimate4 edit.
     *
     * @param _parameter Paramter as passed by the eFaps API
     * @param _docInst instance of the document
     * @param _posInstance the pos instance
     * @return new Empty Return
     * @throws EFapsException on error
     */
    public Return updateCostEstimate4Edit(final Parameter _parameter,
                                          final Instance _docInst,
                                          final Instance _posInstance)
        throws EFapsException
    {
        final Instance baseCurrInst = Currency.getBaseCurrency();
        final List<Calculator> calcList = new ArrayList<>();
        Instance costEstInst = null;

        final QueryBuilder queryBldrQuoPos = new QueryBuilder(CIConstruction.CostEstimatePositionAbstract);
        // if the cost sheet is updated
        if (_docInst.getType().isKindOf(CIConstruction.EntrySheet.getType())) {
            final QueryBuilder attrQueryBldr = new QueryBuilder(CIConstruction.CostEstimate2EntrySheet);
            attrQueryBldr.addWhereAttrEqValue(CIConstruction.CostEstimate2EntrySheet.ToLink, _docInst);
            final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CIConstruction.CostEstimate2EntrySheet.FromLink);
            queryBldrQuoPos.addWhereAttrInQuery(CISales.PositionSumAbstract.DocumentAbstractLink, attrQuery);
        } else {
            queryBldrQuoPos.addWhereAttrEqValue(CISales.PositionSumAbstract.DocumentAbstractLink, _docInst);
        }

        Object[] rateObj = new Object[] { BigDecimal.ONE, BigDecimal.ONE, baseCurrInst.getId(), baseCurrInst.getId() };

        final MultiPrintQuery multi = queryBldrQuoPos.getPrint();
        final SelectBuilder selESPosInst = new SelectBuilder()
                        .linkto(CIConstruction.CostEstimatePositionAbstract.EntrySheetPositionLink).instance();
        final SelectBuilder selQuotInst = new SelectBuilder()
                        .linkto(CISales.PositionSumAbstract.DocumentAbstractLink).instance();
        final SelectBuilder selQuoProdInst = new SelectBuilder()
                        .linkto(CISales.PositionSumAbstract.Product).instance();
        multi.addAttribute(CISales.PositionSumAbstract.Quantity,
                        CISales.PositionSumAbstract.Discount,
                        CISales.PositionSumAbstract.RateNetUnitPrice);
        multi.addSelect(selESPosInst, selQuotInst, selQuoProdInst);
        multi.execute();
        while (multi.next()) {
            if (costEstInst == null) {
                costEstInst = multi.<Instance>getSelect(selQuotInst);
                final PrintQuery printQuot = new PrintQuery(costEstInst);
                printQuot.addAttribute(CIConstruction.CostEstimateAbstract.Rate);
                final SelectBuilder selCurrInst = new SelectBuilder().linkto(CIConstruction.CostEstimateAbstract.RateCurrencyId)
                                .instance();
                printQuot.addSelect(selCurrInst);
                printQuot.execute();
                rateObj = printQuot.<Object[]>getAttribute(CISales.PositionSumAbstract.Rate);
            }
            final Instance instQuotPos = multi.getCurrentInstance();
            final Instance ceProdInst = multi.<Instance>getSelect(selQuoProdInst);
            final BigDecimal quantity = multi.<BigDecimal>getAttribute(CISales.PositionSumAbstract.Quantity);
            final BigDecimal discount = multi.<BigDecimal>getAttribute(CISales.PositionSumAbstract.Discount);

            BigDecimal rateNetPrice = multi.<BigDecimal>getAttribute(CISales.PositionSumAbstract.RateNetUnitPrice);

            BigDecimal netPriceAux = BigDecimal.ZERO;
            if (_docInst.getType().isKindOf(CIConstruction.EntrySheet.getType())) {
                final Instance eSPosInst = multi.getSelect(selESPosInst);
                netPriceAux = getEntrySheetPositionRateNetTotal(_parameter,
                                eSPosInst != null && eSPosInst.isValid() ? eSPosInst : _docInst, ceProdInst);
            } else {
                // that means that it is a create mode, and only for the currently given position the
                // evaluation will be done
                final Instance costSheetPosInst = Instance.get(_parameter
                                .getParameterValue(CIFormConstruction.Construction_CostEstimateGroupItemForm.product.name));
                if (instQuotPos.equals(_posInstance) &&  costSheetPosInst.isValid()) {
                    netPriceAux = getEntrySheetPositionRateNetTotal(_parameter, costSheetPosInst, ceProdInst);
                }
            }
            boolean updatePos = false;
            if (netPriceAux.compareTo(BigDecimal.ZERO) != 0 && rateNetPrice.compareTo(netPriceAux) != 0) {
                rateNetPrice = netPriceAux;
                updatePos = true;
            }
            final Instance cosEstInstane = costEstInst;
            final Calculator calc = new Calculator(_parameter, null, ceProdInst, quantity, rateNetPrice, discount,
                            false, this)
            {
                /** */
                private static final long serialVersionUID = 1L;

                @Override
                protected String getDocKey()
                {
                    final String ret;
                    if (cosEstInstane != null && cosEstInstane.isValid()) {
                        ret = cosEstInstane.getType().getName();
                    } else {
                        ret = super.getDocKey();
                    }
                    return ret;
                }

                @Override
                protected String getPosKey()
                {
                    final String ret;
                    if (cosEstInstane != null && cosEstInstane.isValid()) {
                        ret = cosEstInstane.getType().getName() + "Position";
                    } else {
                        ret = super.getPosKey();
                    }
                    return ret;
                }
            };
            calcList.add(calc);

            if (updatePos) {
                final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                                RoundingMode.HALF_UP);
                updateCostEstimatePosition(_parameter, instQuotPos, calc, rate);
                updateTotals4CostEstimateGroups(_parameter, costEstInst);
            }
        }

        if (costEstInst != null && costEstInst.isValid()) {
            updateCostEstimateAmounts(_parameter, costEstInst, true);
        }
        return new Return();
    }

    /**
     * @param _parameter    Parameter as passed by the eFaps API
     * @param _instance  Instance of a position
     * @param _quoProdInst         calculator
     * @return nettotal
     * @throws EFapsException on error
     */
    protected BigDecimal getEntrySheetPositionRateNetTotal(final Parameter _parameter,
                                                           final Instance _instance,
                                                           final Instance _quoProdInst)
        throws EFapsException
    {
        BigDecimal ret = BigDecimal.ZERO;
        final QueryBuilder cSQueryBldr = new QueryBuilder(CIConstruction.EntrySheetPosition);
        if (_instance.getType().isKindOf(CIConstruction.EntrySheetPosition.getType())) {
            cSQueryBldr.addWhereAttrEqValue(CIConstruction.EntrySheetPosition.ID, _instance);
        } else {
            cSQueryBldr.addWhereAttrEqValue(CIConstruction.EntrySheetPosition.EntrySheetLink, _instance);
        }
        final MultiPrintQuery multi = cSQueryBldr.getPrint();
        final SelectBuilder selCSProdInst = new SelectBuilder().linkto(CIConstruction.EntrySheetPosition.Product).instance();
        multi.addSelect(selCSProdInst);
        multi.addAttribute(CIConstruction.EntrySheetPosition.RateNetPrice);
        multi.execute();
        while (multi.next()) {
            final Instance csProdInst = multi.<Instance>getSelect(selCSProdInst);
            if (csProdInst.getOid().equals(_quoProdInst.getOid())) {
                ret = multi.<BigDecimal>getAttribute(CIConstruction.EntrySheetPosition.RateNetPrice);
            }
        }
        return ret;
    }

    /**
     * @param _parameter    Parameter as passed by the eFaps API
     * @param _instQuotPos  Instance of a position
     * @param _calc         calculator
     * @param _rate         rate
     * @throws EFapsException on error
     */
    protected void updateCostEstimatePosition(final Parameter _parameter,
                                              final Instance _instQuotPos,
                                              final Calculator _calc,
                                              final BigDecimal _rate)
        throws EFapsException
    {
        final DecimalFormat unitFrmt = NumberFormatter.get().getFrmt4UnitPrice(getType4SysConf(_parameter));
        final Update posUpdate = new Update(_instQuotPos);
        posUpdate.add(CISales.PositionSumAbstract.Quantity, _calc.getQuantity());
        posUpdate.add(CISales.PositionSumAbstract.RateCrossUnitPrice, _calc.getCrossUnitPrice());
        posUpdate.add(CISales.PositionSumAbstract.RateNetUnitPrice, _calc.getNetUnitPrice());
        posUpdate.add(CISales.PositionSumAbstract.RateCrossPrice, _calc.getCrossPrice());
        posUpdate.add(CISales.PositionSumAbstract.RateNetPrice, _calc.getNetPrice());
        posUpdate.add(CISales.PositionSumAbstract.Discount, _calc.getDiscount());
        posUpdate.add(CISales.PositionSumAbstract.RateDiscountNetUnitPrice, _calc.getDiscountNetUnitPrice());
        posUpdate.add(CISales.PositionSumAbstract.DiscountNetUnitPrice, _calc.getDiscountNetUnitPrice()
                        .divide(_rate, RoundingMode.HALF_UP)
                        .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
        posUpdate.add(CISales.PositionSumAbstract.CrossUnitPrice, _calc.getCrossUnitPrice()
                        .divide(_rate, RoundingMode.HALF_UP)
                        .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
        posUpdate.add(CISales.PositionSumAbstract.NetUnitPrice, _calc.getNetUnitPrice()
                        .divide(_rate, RoundingMode.HALF_UP)
                        .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
        posUpdate.add(CISales.PositionSumAbstract.CrossPrice, _calc.getCrossPrice()
                        .divide(_rate, RoundingMode.HALF_UP)
                        .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
        posUpdate.add(CISales.PositionSumAbstract.NetPrice, _calc.getNetPrice()
                        .divide(_rate, RoundingMode.HALF_UP)
                        .setScale(unitFrmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
        posUpdate.execute();
    }

    /**
     * @param _parameter    Parameter as passed by the eFaps API
     * @param _ceInst       Instance of a CosteEstimate
     * @param _verifyPercentageCost verify and correct the percentageCost Amounts
     * @throws EFapsException on error
     */
    public void updateCostEstimateAmounts(final Parameter _parameter,
                                          final Instance _ceInst,
                                          final boolean _verifyPercentageCost)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_ceInst);
        print.addAttribute(CIConstruction.CostEstimateAbstract.Rate);
        final SelectBuilder selCurrInst = new SelectBuilder().linkto(CIConstruction.CostEstimateAbstract.RateCurrencyId)
                        .instance();
        print.addSelect(selCurrInst);
        print.execute();
        final Object[] rateObj = print.<Object[]>getAttribute(CISales.PositionSumAbstract.Rate);
        final Instance rateCurrInst = print.<Instance>getSelect(selCurrInst);
        final List<Calculator> calcs = getCalculators4Doc(_parameter, _ceInst, null);
        final BigDecimal subNetTotal = getNetTotal(_parameter, calcs);
        calcs.addAll(getCalculators4PercentageCostAmmounts(_parameter, _ceInst, _verifyPercentageCost ? subNetTotal
                        : null));
        final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                        RoundingMode.HALF_UP);

        updateCostEstimateAmounts(_parameter, _ceInst, calcs, rate, rateCurrInst);
    }

    /**
     * @param _parameter    Parameter as passed by the eFaps API
     * @param _ceInst       Instance of a CosteEstimate
     * @param _calcList     List of calculators
     * @param _rate         rate
     * @param _rateCurrInst instacne of the rate currency
     * @throws EFapsException on error
     */
    public void updateCostEstimateAmounts(final Parameter _parameter,
                                          final Instance _ceInst,
                                          final List<Calculator> _calcList,
                                          final BigDecimal _rate,
                                          final Instance _rateCurrInst)
        throws EFapsException
    {
        final DecimalFormat frmt = NumberFormatter.get().getFrmt4Total(getType4SysConf(_parameter));

        final Update update = new Update(_ceInst);
        update.add(CISales.DocumentSumAbstract.CrossTotal, getCrossTotal(_parameter, _calcList)
                        .divide(_rate, 12, RoundingMode.HALF_UP).setScale(frmt.getMaximumFractionDigits(),
                                        RoundingMode.HALF_UP));
        update.add(CISales.DocumentSumAbstract.NetTotal, getNetTotal(_parameter, _calcList)
                        .divide(_rate, 12, RoundingMode.HALF_UP).setScale(frmt.getMaximumFractionDigits(),
                                        RoundingMode.HALF_UP));
        update.add(CISales.DocumentSumAbstract.RateNetTotal, getNetTotal(_parameter, _calcList)
                        .setScale(frmt.getMaximumFractionDigits(), RoundingMode.HALF_UP).setScale(
                                        frmt.getMaximumFractionDigits(),
                                        RoundingMode.HALF_UP));
        update.add(CISales.DocumentSumAbstract.RateCrossTotal, getCrossTotal(_parameter, _calcList)
                        .setScale(frmt.getMaximumFractionDigits(), RoundingMode.HALF_UP).setScale(
                                        frmt.getMaximumFractionDigits(), RoundingMode.HALF_UP));
        update.add(CISales.DocumentSumAbstract.DiscountTotal, BigDecimal.ZERO);
        update.add(CISales.DocumentSumAbstract.RateDiscountTotal, BigDecimal.ZERO);
        update.add(CISales.DocumentSumAbstract.RateTaxes, getRateTaxes(_parameter, _calcList, _rateCurrInst));
        update.add(CISales.DocumentSumAbstract.Taxes, getTaxes(_parameter, _calcList, _rate,
                        Currency.getBaseCurrency()));
        update.execute();
    }

    /**
     * @param _parameter as passed from eFaps API.
     * @return Return with the checkbox.
     * @throws EFapsException on error.
     */
    public Return getEntrySheets4Connect(final Parameter _parameter)
        throws EFapsException
    {
        return new MultiPrint() {

            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                                 final QueryBuilder _queryBldr)
                throws EFapsException
            {
                final QueryBuilder attrQueryBldr1 = new QueryBuilder(CIConstruction.ProjectService2CostEstimate);
                attrQueryBldr1.addWhereAttrEqValue(CIConstruction.ProjectService2CostEstimate.ToAbstract,
                                _parameter.getInstance());
                final AttributeQuery attrQuery1 = attrQueryBldr1
                                .getAttributeQuery(CIConstruction.ProjectService2CostEstimate.FromAbstract);

                final QueryBuilder attrQueryBldr = new QueryBuilder(CIProjects.Project2DocumentAbstract);
                attrQueryBldr.addWhereAttrInQuery(CIProjects.Project2DocumentAbstract.FromAbstract, attrQuery1);
                final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(
                                CIProjects.Project2DocumentAbstract.ToAbstract);

                final QueryBuilder attrQueryBldr2 = new QueryBuilder(CIConstruction.CostEstimate2EntrySheet);
                final AttributeQuery attrQuery2 = attrQueryBldr2.getAttributeQuery(
                                CIConstruction.CostEstimate2EntrySheet.ToLink);

                _queryBldr.addWhereAttrInQuery(CIConstruction.EntrySheet.ID, attrQuery);
                _queryBldr.addWhereAttrNotInQuery(CIConstruction.EntrySheet.ID, attrQuery2);

                _queryBldr.addWhereAttrEqValue(CIConstruction.EntrySheet.Status, Status.find(CIConstruction.EntrySheetStatus.Open));
            }

        } .execute(_parameter);
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return file
     * @throws EFapsException on error
     */
    public Return downloadCostEstimate(final Parameter _parameter)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_parameter.getInstance());
        print.addAttribute("Name");
        print.execute();
        final String name = print.<String>getAttribute("Name");

        final StandartReport report = new StandartReport();
        add2Report(_parameter, null, report);

        final String fileName = DBProperties.getProperty(_parameter.getInstance().getType().getLabelKey(), "es") + "_"
                        + name;
        report.setFileName(fileName);

        return report.execute(_parameter);
    }

    /**
     * Method to copy or revise a costestimate.
     *
     * @param _parameter as parameter from eFaps API.
     * @return new Return
     * @throws EFapsException on error
     */
    @SuppressWarnings("checkstyle:methodlength")
    public Return revise(final Parameter _parameter)
        throws EFapsException
    {
        final String[] oids;
        if (_parameter.get(ParameterValues.OTHERS) instanceof Map) {
            oids = (String[]) Context.getThreadContext().getSessionAttribute("selectedCostEstimate");
        } else {
            oids = (String[]) _parameter.get(ParameterValues.OTHERS);
        }
        final Instance qInst = Instance.get(oids[0]);
        _parameter.put(ParameterValues.INSTANCE, qInst);
        final Revision rev = new Revision()
        {
            @Override
            public Return revise(final Parameter _parameter)
                throws EFapsException
            {
                if (reviseable(_parameter.getInstance())) {
                    // store the current props
                    final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);

                    // revise the related EntrySheets and connect them
                    final Set<Instance> entrySheets = new HashSet<>();
                    final Map<String, String> tmpProps = new HashMap<>();
                    final StringBuilder revRelStr = new StringBuilder()
                            .append(CIConstruction.ProjectService2EntrySheet.getType().getName()).append(";")
                            .append(CIConstruction.EntrySheetPosition.getType().getName());
                    tmpProps.put("ReviseRelations", revRelStr.toString());

                    final StringBuilder revRelAttStr = new StringBuilder()
                            .append(CIConstruction.ProjectService2EntrySheet.ToLink.name).append(";")
                            .append(CIConstruction.EntrySheetPosition.EntrySheetLink.name);
                    tmpProps.put("ReviseRelationsAttribute", revRelAttStr.toString());

                    if (_parameter.getParameterValue(CIFormConstruction.Construction_CostEstimateCopyToForm.type.name) == null) {
                        tmpProps.put("Status", CIConstruction.EntrySheetStatus.Canceled.key);
                        tmpProps.put("RevisionStatus", CIConstruction.EntrySheetStatus.Open.key);
                    } else {
                        tmpProps.put("RevisionUpdate", "false");
                        tmpProps.put("RevisionConnect", "false");
                    }

                    if (props.containsKey("UseNumberGenerator4Name")) {
                        tmpProps.put("UseNumberGenerator4Name", (String) props.get("UseNumberGenerator4Name"));
                    }
                    _parameter.put(ParameterValues.PROPERTIES, tmpProps);

                    final QueryBuilder csQueryBldr = new QueryBuilder(CIConstruction.CostEstimate2EntrySheet);
                    csQueryBldr.addWhereAttrEqValue(CIConstruction.CostEstimate2EntrySheet.FromLink, qInst);
                    final MultiPrintQuery csMulti = csQueryBldr.getPrint();
                    final SelectBuilder csSel = new SelectBuilder()
                                    .linkto(CIConstruction.CostEstimate2EntrySheet.ToLink).instance();
                    csMulti.addSelect(csSel);
                    csMulti.execute();
                    final Map<Instance, Instance> oldInst2newInst = new HashMap<>();
                    while (csMulti.next()) {
                        final Instance csInst = csMulti.<Instance>getSelect(csSel);
                        _parameter.put(ParameterValues.OTHERS, new String[] { csInst.getOid() });

                        final Return retTmp = new EntrySheet().revise(_parameter);
                        @SuppressWarnings("unchecked")
                        final Map<Instance, Instance> val = (Map<Instance, Instance>) retTmp
                                        .get(ReturnValues.VALUES);
                        oldInst2newInst.putAll(val);
                        if (val != null) {
                            entrySheets.add(val.get(csInst));
                        }
                    }

                    // return to the current costestimate
                    _parameter.put(ParameterValues.PROPERTIES, props);
                    _parameter.put(ParameterValues.INSTANCE, qInst);

                    final Instance newInst = copyDoc(_parameter);

                    for (final Instance inst : entrySheets) {
                        final Insert insert = new Insert(CIConstruction.CostEstimate2EntrySheet);
                        insert.add(CIConstruction.CostEstimate2EntrySheet.FromLink, newInst);
                        insert.add(CIConstruction.CostEstimate2EntrySheet.ToLink, inst);
                        insert.executeWithoutAccessCheck();
                    }

                    updateRevision(_parameter, newInst);

                    final Map<Instance, Instance> relMap = copyRelations(_parameter, newInst);
                    connectRevision(_parameter, newInst);
                    setStati(_parameter, newInst);
                    // update the group positions
                    final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.PositionGroupAbstract);
                    queryBldr.addWhereAttrEqValue(CIConstruction.PositionGroupAbstract.DocumentAbstractLink, qInst);
                    final MultiPrintQuery multi = queryBldr.getPrint();
                    multi.addAttribute(CIConstruction.PositionGroupItem.ParentGroupLink,
                                    CIConstruction.PositionGroupAbstract.AbstractPositionAbstractLink);
                    multi.execute();
                    final Map<Long, Long> current2new = new HashMap<>();
                    final Map<Instance, Long> new2parent = new HashMap<>();
                    while (multi.next()) {
                        final Instance origInst = multi.getCurrentInstance();

                        final Long curParentId = multi.<Long>getAttribute(CIConstruction.PositionGroupItem.ParentGroupLink);
                        final Insert insert = new Insert(origInst.getType());
                        final Set<String> added = new HashSet<>();
                        insert.add(CIConstruction.PositionGroupItem.DocumentAbstractLink, newInst);
                        added.add(origInst.getType().getAttribute(CIConstruction.PositionGroupItem.DocumentAbstractLink.name)
                                        .getSqlColNames().toString());

                        if (multi.getAttribute(CIConstruction.PositionGroupItem.AbstractPositionAbstractLink) != null) {
                            final PrintQuery print = new PrintQuery(origInst);
                            final SelectBuilder posSel = new SelectBuilder()
                                            .linkto(CIConstruction.PositionGroupItem.AbstractPositionAbstractLink).instance();
                            print.addSelect(posSel);
                            print.execute();
                            final Instance posOrigInst = print.<Instance>getSelect(posSel);
                            final Instance posNewInst = relMap.get(posOrigInst);
                            if (posNewInst == null) {
                                break;
                            }
                            insert.add(CIConstruction.PositionGroupItem.PositionLink, posNewInst);
                            added.add(origInst.getType()
                                            .getAttribute(CIConstruction.PositionGroupItem.PositionLink.name)
                                            .getSqlColNames().toString());
                        }

                        addAttributes(_parameter, origInst, insert, added);
                        insert.execute();
                        new2parent.put(insert.getInstance(), curParentId);
                        current2new.put(origInst.getId(), insert.getInstance().getId());
                    }

                    for (final Entry<Instance, Long> entry : new2parent.entrySet()) {
                        if (entry.getValue() != null) {
                            final Update update = new Update(entry.getKey());
                            update.add(CIConstruction.PositionGroupItem.ParentGroupLink, current2new.get(entry.getValue()));
                            update.execute();
                        }
                    }
                    reconnectPartLists(_parameter, newInst);
                    reconnectEntrySheetPosition(_parameter, newInst, oldInst2newInst);
                }
                return new Return();
            }

            @Override
            protected Instance copyDoc(final Parameter _parameter)
                throws EFapsException
            {
                final Instance origInst = _parameter.getInstance();
                final Insert insert;
                final Set<String> added = new HashSet<>();

                final String typeId = _parameter.getParameterValue(CIFormConstruction.Construction_CostEstimateCopyToForm.type.name);
                // in case of copying
                if (typeId != null) {
                    final Type type = Type.get(Long.parseLong(typeId));
                    insert = new Insert(type);
                    final Attribute revAttr = type.getAttribute(CIERP.DocumentAbstract.Revision.name);
                    added.add(revAttr.getSqlColNames().toString());
                    Instance projectInst = Instance.get(_parameter
                                    .getParameterValue(CIFormConstruction.Construction_CostEstimateCopyToForm.project.name));
                    // check if it must be copied to another project
                    if (!projectInst.isValid()) {
                        final QueryBuilder queryBldr = new QueryBuilder(CIProjects.Project2DocumentAbstract);
                        queryBldr.addWhereAttrEqValue(CIProjects.Project2DocumentAbstract.ToAbstract, origInst);
                        final MultiPrintQuery multi = queryBldr.getPrint();
                        final SelectBuilder sel = new SelectBuilder().linkto(
                                        CIProjects.Project2DocumentAbstract.FromAbstract).instance();
                        multi.addSelect(sel);
                        multi.execute();
                        if (multi.next()) {
                            projectInst = multi.<Instance>getSelect(sel);
                        }
                    }
                    if (projectInst.isValid()) {
                        final PrintQuery print = new PrintQuery(projectInst);
                        print.addAttribute(CIProjects.ProjectService.Contact);
                        print.execute();

                        insert.add(CIERP.DocumentAbstract.Contact,
                                        print.<Long>getAttribute(CIProjects.ProjectService.Contact));
                        final Attribute contactAttr = type.getAttribute(CIERP.DocumentAbstract.Contact.name);
                        added.add(contactAttr.getSqlColNames().toString());

                        insert.add(CIERP.DocumentAbstract.Name, getDocName4Create(_parameter));
                        final Attribute nameAttr = type.getAttribute(CIERP.DocumentAbstract.Name.name);
                        added.add(nameAttr.getSqlColNames().toString());
                    }

                    final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
                    if (props.containsKey("RevisionStatus")) {

                        for (final Map.Entry<?, ?> entry : props.entrySet()) {
                            if (entry.getKey().equals("RevisionStatus")) {
                                final Type statusType = type.getStatusAttribute().getLink();
                                final Status status = Status.find(statusType.getUUID(), entry.getValue().toString());
                                if (status != null) {
                                    insert.add(CIERP.DocumentAbstract.StatusAbstract, status.getId());
                                    final Attribute statusAttr = type
                                                    .getAttribute(CIERP.DocumentAbstract.StatusAbstract.name);
                                    added.add(statusAttr.getSqlColNames().toString());
                                }
                            }
                        }
                    }
                } else {
                    insert = new Insert(origInst.getType());
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
                final Type type;
                Type createType = null;
                if (CIConstruction.CostEstimatePositionAbstract.getType().getName().equals(_typeName)) {
                    if (_newInst.getType().isKindOf(CIConstruction.CostEstimateBase.getType())) {
                        type = CIConstruction.CostEstimatePositionAbstract.getType();
                        createType = CIConstruction.CostEstimatePositionBase.getType();
                    } else if (_newInst.getType().isKindOf(CIConstruction.CostEstimateSale.getType())) {
                        type = CIConstruction.CostEstimatePositionAbstract.getType();
                        createType = CIConstruction.CostEstimatePositionSale.getType();
                    } else if (_newInst.getType().isKindOf(CIConstruction.CostEstimateRealization.getType())) {
                        type = CIConstruction.CostEstimatePositionAbstract.getType();
                        createType = CIConstruction.CostEstimatePositionRealization.getType();
                    } else if (_newInst.getType().isKindOf(CIConstruction.CostEstimateTarget.getType())) {
                        type = CIConstruction.CostEstimatePositionAbstract.getType();
                        createType = CIConstruction.CostEstimatePositionTarget.getType();
                    } else {
                        type =  Type.get(_typeName);
                    }
                } else {
                    type = Type.get(_typeName);
                }

                Map<Instance, Instance> ret = new HashMap<>();
                final String project = _parameter.getParameterValue(CIFormConstruction.Construction_CostEstimateCopyToForm.project.name);
                final Instance projectInst = Instance.get(project);
                // check if it must be copied to another project
                if (CIConstruction.ProjectService2CostEstimate.getType().equals(type)
                                && projectInst.isValid()) {
                    final Insert insert = new Insert(CIConstruction.ProjectService2CostEstimate);
                    insert.add(CIConstruction.ProjectService2CostEstimate.FromLink, projectInst);
                    insert.add(CIConstruction.ProjectService2CostEstimate.ToLink, _newInst);
                    insert.execute();
                } else {
                    ret = super.copyRelation(_parameter, _newInst, type, type.getAttribute(_linkAttrName), createType);
                }
                return ret;
            }
        };
        rev.revise(_parameter);
        return new Return();
    }

    /**
     *  update or change the products used in positions, by
     *  searching the latest revision of the product.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _costEtimateInst instance
     * @throws EFapsException on error
     */
    protected void reconnectPartLists(final Parameter _parameter,
                                      final Instance _costEtimateInst)
        throws EFapsException
    {
        final QueryBuilder posQueryBldr = new QueryBuilder(CISales.PositionSumAbstract);
        posQueryBldr.addWhereAttrEqValue(CISales.PositionSumAbstract.DocumentAbstractLink, _costEtimateInst);
        final MultiPrintQuery posMulti = posQueryBldr.getPrint();
        final SelectBuilder sel = new SelectBuilder().linkto(CISales.PositionAbstract.Product).instance();
        posMulti.addSelect(sel);
        posMulti.execute();
        while (posMulti.next()) {
            final Instance partInst = posMulti.<Instance>getSelect(sel);
            final Instance revInst = getPartListRevInst(_parameter, partInst);
            if (!revInst.equals(partInst)) {
                final Update update = new Update(posMulti.getCurrentInstance());
                update.add(CISales.PositionAbstract.Product, revInst);
                update.execute();
            }
        }
    }

    /**
     * Gets the part list rev inst.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _partInst instacne of the part
     * @return revisioned instance
     * @throws EFapsException on error
     */
    protected Instance getPartListRevInst(final Parameter _parameter,
                                          final Instance _partInst)
        throws EFapsException
    {
        Instance ret = null;
        if (Context.getThreadContext().containsRequestAttribute(EntryPartList.REQKEY4COPIED)) {
            @SuppressWarnings("unchecked")
            final Map<Instance, Instance> orig2copy = (Map<Instance, Instance>) Context.getThreadContext()
                .getRequestAttribute(EntryPartList.REQKEY4COPIED);
            if (orig2copy.containsKey(_partInst)) {
                ret = orig2copy.get(_partInst);
            }
        }
        if (ret == null) {
            final PrintQuery print = new PrintQuery(_partInst);
            final SelectBuilder sel = new SelectBuilder()
                            .linkfrom(CIConstruction.EntryPartList2EntryPartListRev.FromLink)
                            .linkto(CIConstruction.EntryPartList2EntryPartListRev.ToLink).instance();
            print.addSelect(sel);
            if (print.execute()) {
                final Instance revInst = print.<Instance>getSelect(sel);
                if (revInst != null && revInst.isValid()) {
                    ret = getPartListRevInst(_parameter, revInst);
                } else {
                    ret = _partInst;
                }
            } else {
                ret = _partInst;
            }
        }
        return ret;
    }

    /**
     * Reconnect entry sheet position.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _quotInst the quot inst
     * @param _retInst the ret inst
     * @throws EFapsException on error
     */
    public void reconnectEntrySheetPosition(final Parameter _parameter,
                                            final Instance _quotInst,
                                            final Map<Instance, Instance> _retInst)
        throws EFapsException
    {
        for (final Entry<Instance, Instance> entry : _retInst.entrySet()) {
            if (entry.getKey().getType().isCIType(CIConstruction.EntrySheetPosition)) {
                final QueryBuilder posQueryBldr = new QueryBuilder(CIConstruction.CostEstimatePositionAbstract);
                posQueryBldr.addWhereAttrEqValue(CIConstruction.CostEstimatePositionAbstract.CostEstimateAbstractLink, _quotInst);
                posQueryBldr.addWhereAttrEqValue(CIConstruction.CostEstimatePositionAbstract.EntrySheetPositionLink,
                                entry.getKey());
                final InstanceQuery query = posQueryBldr.getQuery();
                query.execute();
                while (query.next()) {
                    final Update update = new Update(query.getCurrentValue());
                    update.add(CIConstruction.CostEstimatePositionAbstract.EntrySheetPositionLink, entry.getValue());
                    update.execute();
                }
            }
        }
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
            final String typeId = _parameter.getParameterValue(CIFormConstruction.Construction_CostEstimateCopyToForm.type.name);
            if (typeId != null && !typeId.isEmpty()) {
                type = Type.get(Long.parseLong(typeId));
            } else {
                type = CIConstruction.CostEstimateBase.getType();
            }
        }
        return type;
    }

    @Override
    public String getSysConfKey4Doc(final Parameter _parameter)
        throws EFapsException
    {
        final String ret;
        if (_parameter.getInstance() != null && _parameter.getInstance().isValid()
                        && _parameter.getInstance().getType().isKindOf(CIConstruction.CostEstimateAbstract)) {
            ret = _parameter.getInstance().getType().getName();
        } else if (_parameter.getInstance()  != null && _parameter.getInstance().isValid()
                        && _parameter.getInstance().getType().isKindOf(CIConstruction.PositionGroupAbstract)) {
            final CachedPrintQuery print = CachedPrintQuery.get4Request(_parameter.getInstance());
            final SelectBuilder sel = SelectBuilder.get()
                           .linkto(CIConstruction.PositionGroupAbstract.AbstractPositionAbstractLink)
                           .linkto(CISales.PositionAbstract.DocumentAbstractLink).type().name();
            print.addSelect(sel);
            print.executeWithoutAccessCheck();
            ret = print.getSelect(sel);

        } else {
            Type type = null;
            if (_parameter.get(ParameterValues.UIOBJECT) != null
                            && _parameter.get(ParameterValues.UIOBJECT) instanceof AbstractCommand) {
                final AbstractCommand command = (AbstractCommand) _parameter.get(ParameterValues.UIOBJECT);
                type = command.getTargetCreateType();
            }

            if (type == null || type != null && !type.isKindOf(CIConstruction.CostEstimateAbstract)) {
                final String typeId = _parameter.getParameterValue(CIFormConstruction.Construction_CostEstimateCopyToForm.type.name);
                if (typeId != null && !typeId.isEmpty()) {
                    type = Type.get(Long.parseLong(typeId));
                }
            }
            if (type != null && type.isKindOf(CIConstruction.CostEstimateAbstract)) {
                ret = type.getName();
            } else {
                ret = super.getSysConfKey4Doc(_parameter);
            }
        }
        return ret;
    }

    @Override
    public String getSysConfKey4Pos(final Parameter _parameter)
        throws EFapsException
    {
        return getSysConfKey4Doc(_parameter) + "Position";
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return Return
     * @throws EFapsException on error
     */
    public Return itemsMultiPrint(final Parameter _parameter)
        throws EFapsException
    {

        final MultiPrint multi = new MultiPrint()
        {
            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                final QueryBuilder attrQueryBldr = new QueryBuilder(CIConstruction.CostEstimate2EntrySheet);
                attrQueryBldr.addWhereAttrEqValue(CIConstruction.CostEstimate2EntrySheet.FromLink, _parameter.getInstance());
                final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CIConstruction.CostEstimate2EntrySheet.ToLink);
                _queryBldr.addWhereAttrInQuery(CIConstruction.EntrySheetPosition.EntrySheetLink, attrQuery);
            }
        };
        return multi.execute(_parameter);
    }

    @Override
    protected void add2UpdateField4Product(final Parameter _parameter,
                                           final Map<String, Object> _map,
                                           final Instance _prodInst)
        throws EFapsException
    {
        super.add2UpdateField4Product(_parameter, _map, _prodInst);
        if (_prodInst.getType().isKindOf(CIConstruction.EntryPartList.getType())) {
            final PrintQuery print = new PrintQuery(_prodInst);
            final SelectBuilder sel = SelectBuilder.get().clazz(CIConstruction.EntryPartList_Class)
                            .attribute(CIConstruction.EntryPartList_Class.Total);
            print.addSelect(sel);
            print.execute();
            final BigDecimal total = print.<BigDecimal>getSelect(sel);
            final int selected = getSelectedRow(_parameter);
            final String[] valueArr = _parameter.getParameterValues("netUnitPrice");
            valueArr[selected] = NumberFormatter.get().getFormatter().format(total);
        }
    }

    @Override
    public List<Calculator> analyseTable(final Parameter _parameter,
                                         final Integer _row4priceFromDB)
        throws EFapsException
    {
        initCache();
        final String[] oids = _parameter.getParameterValues("product");
        if (oids != null) {
            for (int i = 0; i < oids.length; i++) {
                final Instance prodInst = Instance.get(oids[i]);
                if (prodInst.isValid() && prodInst.getType().isCIType(CIConstruction.EntrySheetPosition)) {
                    final PrintQuery print = new PrintQuery(prodInst);
                    final SelectBuilder selProdOid = SelectBuilder.get().linkto(CIConstruction.EntrySheetPosition.Product).oid();
                    print.addSelect(selProdOid);
                    print.execute();
                    oids[i] = print.getSelect(selProdOid);
                }
            }
        }
        final Parameter parameter = ParameterUtil.clone(_parameter);
        ParameterUtil.setParameterValues(parameter, "product", oids);
        final List<Calculator> ret = super.analyseTable(parameter, -1);

        Instance costEstimateInst = _parameter.getInstance();
        if (costEstimateInst != null && costEstimateInst.isValid()
                        && costEstimateInst.getType().isCIType(CIConstruction.PositionGroupItem)) {
            final PrintQuery print = new PrintQuery(costEstimateInst);
            final SelectBuilder selCostEstimateInst = SelectBuilder.get()
                            .linkto(CIConstruction.PositionGroupItem.DocumentAbstractLink).instance();
            print.addSelect(selCostEstimateInst);
            print.execute();
            costEstimateInst = print.getSelect(selCostEstimateInst);
        }
        if (costEstimateInst != null && costEstimateInst.isValid()) {
            final List<Instance> tempList = new ArrayList<>();
            @SuppressWarnings("unchecked")
            final Map<String, String> oidMap = (Map<String, String>) _parameter.get(ParameterValues.OIDMAP4UI);
            for (final String oid : oidMap.values()) {
                final Instance inst = Instance.get(oid);
                if (inst.getType().isCIType(CIConstruction.PositionGroupItem)) {
                    tempList.add(inst);
                }
            }
            final Set<Instance> exclude = new HashSet<>();
            final Set<String> keySel = new TreeSet<>();
            final MultiPrintQuery multi = new MultiPrintQuery(tempList);
            final SelectBuilder selPosInst = SelectBuilder.get()
                            .linkto(CIConstruction.PositionGroupItem.PositionLink).instance();
            multi.addSelect(selPosInst);
            multi.executeWithoutAccessCheck();
            while (multi.next()) {
                final Instance posInst = multi.<Instance>getSelect(selPosInst);
                exclude.add(posInst);
                keySel.add(posInst.getOid());
            }
            final String key = costEstimateInst.getOid() + "|" + StringUtils.join(keySel, "|");
            final AdvancedCache<String, List<Calculator>> cache = InfinispanCache.get().getIgnReCache(
                            CostEstimate.CALCCACHE);
            if (cache.containsKey(key)) {
                ret.addAll(cache.get(key));
            } else {
                final List<Calculator> calcs = getCalculators4Doc(_parameter, costEstimateInst, exclude);
                for (final Calculator calc : calcs) {
                    calc.setBackground(true);
                    ret.add(calc);
                }
                cache.put(key, calcs, 2, TimeUnit.MINUTES, 2, TimeUnit.MINUTES);
            }
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return empty Return
     * @throws EFapsException on error
     */
    @SuppressWarnings("unchecked")
    public Return updateField4EntrySheets4Project(final Parameter _parameter)
        throws EFapsException
    {
        final Instance projInst = Instance.get(_parameter
                        .getParameterValue(CIFormConstruction.Construction_CostEstimateCreateForm.project.name));

        final Return retTmp = new Field()
        {
            @Override
            protected void add2QueryBuilder4List(final Parameter _parameter,
                                                 final QueryBuilder _queryBldr)
                throws EFapsException
            {
                final QueryBuilder attrQueryBldr = new QueryBuilder(CIProjects.Project2DocumentAbstract);
                attrQueryBldr.addWhereAttrEqValue(CIProjects.Project2DocumentAbstract.FromAbstract, projInst);
                final AttributeQuery attrQuery = attrQueryBldr
                                .getAttributeQuery(CIProjects.Project2DocumentAbstract.ToAbstract);

                final QueryBuilder attrQueryBldr2 = new QueryBuilder(CIConstruction.CostEstimate2EntrySheet);
                final AttributeQuery attrQuery2 = attrQueryBldr2
                                .getAttributeQuery(CIConstruction.CostEstimate2EntrySheet.ToLink);

                _queryBldr.addWhereAttrInQuery(CIConstruction.EntrySheet.ID, attrQuery);
                _queryBldr.addWhereAttrNotInQuery(CIConstruction.EntrySheet.ID, attrQuery2);

                _queryBldr.addWhereAttrEqValue(CIConstruction.EntrySheet.Status,
                                Status.find(CIConstruction.EntrySheetStatus.Open));
            }
        } .getCheckBoxList(_parameter);

        final StringBuilder js = new StringBuilder();
        js.append("document.getElementsByName('")
            .append(CIFormConstruction.Construction_CostEstimateCreateForm.entrySheets.name).append("')[0].innerHTML='")
            .append(retTmp.get(ReturnValues.SNIPLETT).toString()).append("';");

        if (projInst.isValid()) {
            final PrintQuery print = new PrintQuery(projInst);
            print.addAttribute(CIProjects.ProjectAbstract.CurrencyLink);
            print.execute();
            final Long currencyId = print.<Long>getAttribute(CIProjects.ProjectAbstract.CurrencyLink);
            if (currencyId != null) {
                js.append(getSetFieldValue(0, "rateCurrencyId", currencyId.toString()));

                final Map<Object, Object> parameters = (Map<Object, Object>) _parameter.get(ParameterValues.PARAMETERS);
                parameters.put("rateCurrencyId", new String[] { currencyId.toString() });
                final Return retTmp2 = updateFields4RateCurrency(_parameter);
                final List<Map<String, String>> list = (List<Map<String, String>>) retTmp2.get(ReturnValues.VALUES);
                if (!list.isEmpty()) {
                    js.append(list.get(0).get(EFapsKey.FIELDUPDATE_JAVASCRIPT.getKey()));
                }
            }
        }
        final List<Map<String, String>> list = new ArrayList<>();
        final Map<String, String> map = new HashMap<>();
        map.put(EFapsKey.FIELDUPDATE_JAVASCRIPT.getKey(), js.toString());
        list.add(map);
        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
    }

    ///////////////////
    // Only applies for the CostEstimateSale
    ///////////////////

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return getNetSubTotalFieldvalue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final List<Calculator> posCalcs = getCalculators4Doc(_parameter, _parameter.getInstance(), null);
        ret.put(ReturnValues.VALUES, getNetTotal(_parameter, posCalcs));
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return editPercentageCost(final Parameter _parameter)
        throws EFapsException
    {
        @SuppressWarnings("unchecked")
        final Map<String, String> oidMap = (Map<String, String>) _parameter.get(ParameterValues.OIDMAP4UI);
        final String[] rowKeys = _parameter.getParameterValues(EFapsKey.TABLEROW_NAME.getKey());
        final String[] percentages = _parameter
                        .getParameterValues(CITableConstruction.Construction_CostEstimateSalePercentageCostTable.percentage.name);
        final String[] amounts = _parameter
                        .getParameterValues(CITableConstruction.Construction_CostEstimateSalePercentageCostTable.amount.name);
        final String[] costLinks = _parameter
                        .getParameterValues(CITableConstruction.Construction_CostEstimateSalePercentageCostTable.percentageCostLink.name);

        final DecimalFormat totalFrmt = NumberFormatter.get().getFrmt4Total(getType4SysConf(_parameter));
        final DecimalFormat frmt = NumberFormatter.get().getFormatter();

        final Set<Instance> costInsts = new HashSet<>();
        try {
            if (rowKeys != null) {
                final PrintQuery print = new PrintQuery(_parameter.getInstance());
                print.addAttribute(CIConstruction.CostEstimateSale.Rate);
                print.executeWithoutAccessCheck();
                final Object[] rateObj = print.getAttribute(CIConstruction.CostEstimateSale.Rate);
                final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                                RoundingMode.HALF_UP);
                for (int i = 0; i < rowKeys.length; i++) {
                    if (!percentages[i].isEmpty() && !amounts[i].isEmpty()) {
                        final Instance inst = Instance.get(oidMap.get(rowKeys[i]));
                        final Update update;
                        if (inst.isValid()) {
                            update = new Update(inst);
                        } else {
                            update = new Insert(CIConstruction.CostEstimatePercentageCost);
                            update.add(CIConstruction.CostEstimatePercentageCost.CostEstimateLink, _parameter.getInstance());
                        }
                        final BigDecimal rateAmount = (BigDecimal) totalFrmt.parse(amounts[i]);

                        final BigDecimal amount = rateAmount.divide(rate, 12, RoundingMode.HALF_UP)
                                        .setScale(totalFrmt.getMaximumFractionDigits(),
                                                        RoundingMode.HALF_UP);

                        update.add(CIConstruction.CostEstimatePercentageCost.PercentageCostLink, costLinks[i]);
                        update.add(CIConstruction.CostEstimatePercentageCost.Amount, amount);
                        update.add(CIConstruction.CostEstimatePercentageCost.RateAmount, rateAmount);
                        update.add(CIConstruction.CostEstimatePercentageCost.Percentage, frmt.parse(percentages[i]));
                        update.add(CIConstruction.CostEstimatePercentageCost.PositionNumber, i + 1);
                        update.execute();
                        costInsts.add(update.getInstance());
                    }
                }
            }
        } catch (final ParseException e) {
            LOG.error("Catched ParseException", e);
        }

        final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.CostEstimatePercentageCost);
        queryBldr.addWhereAttrEqValue(CIConstruction.CostEstimatePercentageCost.CostEstimateLink, _parameter.getInstance());
        final InstanceQuery query = queryBldr.getQuery();
        query.execute();
        while (query.next()) {
            final Instance inst = query.getCurrentValue();
            if (!costInsts.contains(inst)) {
                final Delete delete = new Delete(inst);
                delete.execute();
            }
        }
        updateCostEstimateAmounts(_parameter, _parameter.getInstance(), false);
        return new Return();
    }

    /**
     * Method is executed as an update event of the field containing the
     * quantity of products to calculate the new totals.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return Return containing the list
     * @throws EFapsException on error
     */
    public Return updateFields4PercentageCostPercentage(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        final List<Map<String, String>> list = new ArrayList<>();
        final Map<String, String> map = new HashMap<>();

        final DecimalFormat formatter = NumberFormatter.get().getFormatter();
        final Instance docInst = (Instance) Context.getThreadContext().getSessionAttribute(CostEstimate.COESTINSTKEY);
        final List<Calculator> posCalcs = getCalculators4Doc(_parameter, docInst, null);
        final List<Calculator> amountCalcs = analyzePercentageCostAmmounts(_parameter);
        final List<Calculator> allCalcs = new ArrayList<>();
        allCalcs.addAll(posCalcs);
        allCalcs.addAll(amountCalcs);

        final int selected = getSelectedRow(_parameter);

        final String[] percentages = _parameter
                        .getParameterValues(CITableConstruction.Construction_CostEstimateSalePercentageCostTable.percentage.name);
        final String percentageStr = percentages[selected];
        BigDecimal percentage = null;
        try {
            percentage = (BigDecimal) formatter.parse(percentageStr);
        } catch (final ParseException e) {
            LOG.error("Catched ParseException", e);
        }
        if (percentage == null) {
            percentage = new BigDecimal(100);
        }
        final BigDecimal subTotal = getNetTotal(_parameter, posCalcs);

        final BigDecimal amount = percentage.setScale(12)
                        .divide(new BigDecimal(100), RoundingMode.HALF_UP).multiply(subTotal);
        amountCalcs.get(selected).setNetUnitPrice(amount);
        final String netTotalStr = getNetTotalFmtStr(_parameter, allCalcs);
        final String crossTotalStr = getCrossTotalFmtStr(_parameter, allCalcs);
        final String amountStr = amountCalcs.get(selected).getNetPriceFmtStr();
        final StringBuilder taxes = getTaxesScript(_parameter,
                    new TaxesAttribute().getUI4ReadOnly(getRateTaxes(_parameter, allCalcs, null)));
        map.put(CITableConstruction.Construction_CostEstimateSalePercentageCostTable.percentage.name, formatter.format(percentage));
        map.put(CITableConstruction.Construction_CostEstimateSalePercentageCostTable.amount.name, amountStr);
        map.put(CIFormConstruction.Construction_CostEstimateSaleEditPercentageCostForm.netTotal.name, netTotalStr);
        map.put(EFapsKey.FIELDUPDATE_JAVASCRIPT.getKey(), taxes.toString());
        map.put(CIFormConstruction.Construction_CostEstimateSaleEditPercentageCostForm.crossTotal.name, crossTotalStr);
        list.add(map);
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
    }

    /**
     * Method is executed as an update event of the field containing the
     * quantity of products to calculate the new totals.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return Return containing the list
     * @throws EFapsException on error
     */
    public Return updateFields4PercentageCostAmount(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        final List<Map<String, String>> list = new ArrayList<>();
        final Map<String, String> map = new HashMap<>();

        final int selected = getSelectedRow(_parameter);
        final DecimalFormat formatter = NumberFormatter.get().getFormatter();

        final Instance docInst = (Instance) Context.getThreadContext().getSessionAttribute(CostEstimate.COESTINSTKEY);

        final List<Calculator> posCalcs = getCalculators4Doc(_parameter, docInst, null);
        final List<Calculator> amountCalcs = analyzePercentageCostAmmounts(_parameter);
        final List<Calculator> allCalcs = new ArrayList<>();
        allCalcs.addAll(posCalcs);
        allCalcs.addAll(amountCalcs);

        final BigDecimal subTotal = getNetTotal(_parameter, posCalcs);

        final BigDecimal percentage = amountCalcs.get(selected).getNetUnitPrice()
                        .setScale(12, RoundingMode.HALF_UP)
                        .divide(subTotal, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal(100));
        final String netTotalStr = getNetTotalFmtStr(_parameter, allCalcs);
        final String crossTotalStr = getCrossTotalFmtStr(_parameter, allCalcs);
        final String amountStr = amountCalcs.get(selected).getNetPriceFmtStr();
        final StringBuilder taxes = getTaxesScript(_parameter,
                    new TaxesAttribute().getUI4ReadOnly(getRateTaxes(_parameter, allCalcs, null)));
        map.put(CITableConstruction.Construction_CostEstimateSalePercentageCostTable.percentage.name, formatter.format(percentage));
        map.put(CITableConstruction.Construction_CostEstimateSalePercentageCostTable.amount.name, amountStr);
        map.put(CIFormConstruction.Construction_CostEstimateSaleEditPercentageCostForm.netTotal.name, netTotalStr);
        map.put(EFapsKey.FIELDUPDATE_JAVASCRIPT.getKey(), taxes.toString());
        map.put(CIFormConstruction.Construction_CostEstimateSaleEditPercentageCostForm.crossTotal.name, crossTotalStr);
        list.add(map);
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
    }


    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return List of Calculator
     * @throws EFapsException on error
     */
    protected List<Calculator> analyzePercentageCostAmmounts(final Parameter _parameter)
        throws EFapsException
    {
        final List<Calculator> ret = new ArrayList<>();
        final DecimalFormat formatter = NumberFormatter.get().getFormatter();
        final String[] amountArray = _parameter
                        .getParameterValues(CITableConstruction.Construction_CostEstimateSalePercentageCostTable.amount.name);

        for (final String amountStr : amountArray) {
            BigDecimal tmp = null;
            if (!amountStr.isEmpty()) {
                try {
                    tmp = (BigDecimal) formatter.parse(amountStr);
                } catch (final ParseException e) {
                    e.printStackTrace();
                }
            }
            final Calculator calc = getCalculator(_parameter, null, null, BigDecimal.ONE, null, BigDecimal.ZERO, false,
                            0);
            calc.setTaxCatId(TaxCat.get(UUID.fromString("ed28d3c0-e55d-45e5-8025-e48fc989c9dd")).getInstance().getId());
            calc.setNetUnitPrice(tmp == null ? BigDecimal.ZERO : tmp);
            ret.add(calc);
        }
        return ret;
    }

    /**
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _ceInst   costestimateinstance
     * @param _netSubTotal subtotal
     * @return List with calculators containing the list
     * @throws EFapsException on error
     */
    protected List<Calculator> getCalculators4PercentageCostAmmounts(final Parameter _parameter,
                                                                     final Instance _ceInst,
                                                                     final BigDecimal _netSubTotal)
        throws EFapsException
    {
        final List<Calculator> ret = new ArrayList<>();
        if (_ceInst != null && _ceInst.isValid() && _ceInst.getType().isKindOf(CIConstruction.CostEstimateSale.getType())) {
            final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.CostEstimatePercentageCost);
            queryBldr.addWhereAttrEqValue(CIConstruction.CostEstimatePercentageCost.CostEstimateLink, _ceInst);
            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.addAttribute(CIConstruction.CostEstimatePercentageCost.Amount, CIConstruction.CostEstimatePercentageCost.Percentage,
                            CIConstruction.CostEstimatePercentageCost.RateAmount);
            multi.execute();
            while (multi.next()) {
                final Calculator calc = getCalculator(_parameter, null, null, BigDecimal.ONE, null, BigDecimal.ZERO,
                                false, 0);
                calc.setTaxCatId(TaxCat.get(UUID.fromString("ed28d3c0-e55d-45e5-8025-e48fc989c9dd")).getInstance()
                                .getId());
                BigDecimal rateAmount = multi.<BigDecimal>getAttribute(CIConstruction.CostEstimatePercentageCost.RateAmount);
                final BigDecimal percentage = multi
                                .<BigDecimal>getAttribute(CIConstruction.CostEstimatePercentageCost.Percentage);
                if (_netSubTotal != null) {
                    BigDecimal amountTmp = percentage.setScale(12)
                                    .divide(new BigDecimal(100), RoundingMode.HALF_UP).multiply(_netSubTotal);
                    final DecimalFormat frmt = NumberFormatter.get().getFrmt4Total(getType4SysConf(_parameter));
                    amountTmp = amountTmp.setScale(frmt.getMaximumFractionDigits(), RoundingMode.HALF_UP);
                    if (amountTmp.compareTo(rateAmount) != 0) {
                        final PrintQuery print = new PrintQuery(_parameter.getInstance());
                        print.addAttribute(CIConstruction.CostEstimateSale.Rate);
                        print.executeWithoutAccessCheck();
                        final Object[] rateObj = print.getAttribute(CIConstruction.CostEstimateSale.Rate);
                        final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                                        RoundingMode.HALF_UP);
                        final DecimalFormat totalFrmt = NumberFormatter.get().getFrmt4Total(
                                        getType4SysConf(_parameter));
                        final BigDecimal amount = rateAmount.divide(rate, 12, RoundingMode.HALF_UP)
                                        .setScale(totalFrmt.getMaximumFractionDigits(),
                                                        RoundingMode.HALF_UP);

                        final Update update = new Update(multi.getCurrentInstance());
                        update.add(CIConstruction.CostEstimatePercentageCost.RateAmount, amountTmp);
                        update.add(CIConstruction.CostEstimatePercentageCost.Amount, amount);
                        update.execute();
                    }
                    rateAmount = amountTmp;
                }
                calc.setNetUnitPrice(rateAmount);
                ret.add(calc);
            }
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return empty Return
     * @throws EFapsException on error
     */
    public Return deleteEntrySheet(final Parameter _parameter)
        throws EFapsException
    {
        final Return delete = new CommonDelete()
        {

            @Override
            protected boolean getValidate4Instance(final Parameter _parameter,
                                                   final Instance _delete)
                throws EFapsException
            {
                final Instance instance = _parameter.getInstance();

                boolean ret = true;
                if (instance.getType().isKindOf(CIConstruction.CostEstimateAbstract.getType())
                                && _delete.getType().equals(CIConstruction.CostEstimate2EntrySheet.getType())) {
                    final PrintQuery print = new PrintQuery(_delete);
                    print.addAttribute(CIConstruction.CostEstimate2EntrySheet.ToLink);
                    print.execute();

                    final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.EntrySheetPosition);
                    queryBldr.addWhereAttrEqValue(CIConstruction.EntrySheetPosition.EntrySheetLink,
                                    print.<Long>getAttribute(CIConstruction.CostEstimate2EntrySheet.ToLink));
                    final MultiPrintQuery multi = queryBldr.getPrint();
                    multi.addAttribute(CIConstruction.EntrySheetPosition.Product);
                    multi.execute();
                    while (multi.next()) {
                        final QueryBuilder queryBldr2 = new QueryBuilder(CIConstruction.CostEstimatePositionAbstract);
                        queryBldr2.addWhereAttrEqValue(CIConstruction.CostEstimatePositionAbstract.CostEstimateAbstractLink,
                                        instance);
                        queryBldr2.addWhereAttrEqValue(CIConstruction.CostEstimatePositionAbstract.Product,
                                        multi.<Long>getAttribute(CIConstruction.EntrySheetPosition.Product));
                        final InstanceQuery query = queryBldr2.getQuery();
                        query.execute();
                        if (query.next()) {
                            ret = false;
                            break;
                        }
                    }
                }
                return ret;
            }
        } .execute(_parameter);

        return delete;
    }

    @Override
    protected Instance getRateCurrencyInstance(final Parameter _parameter,
                                               final CreatedDoc _createdDoc)
        throws EFapsException
    {
        final String curId;
        if (_parameter.getParameterValue("rateCurrencyId") == null) {
            final PrintQuery print = new PrintQuery(_createdDoc.getInstance());
            print.addAttribute(CIConstruction.CostEstimateAbstract.RateCurrencyId);
            print.execute();

            curId = String.valueOf(print.<Long>getAttribute(CIConstruction.CostEstimateAbstract.RateCurrencyId));
        } else {
            curId = _parameter.getParameterValue("rateCurrencyId");
        }
        return Instance.get(CIERP.Currency.getType(), curId);
    }

    /**
     * Validate realization.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return validateRealization(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final List<IWarning> warnings = new ArrayList<>();
        if (!getGenericProducts(_parameter.getInstance()).isEmpty()) {
            warnings.add(new GenericProductsNotPermittedWarning());
        }

        if (warnings.isEmpty()) {
            ret.put(ReturnValues.TRUE, true);
        } else {
            ret.put(ReturnValues.SNIPLETT, WarningUtil.getHtml4Warning(warnings).toString());
            if (!WarningUtil.hasError(warnings)) {
                ret.put(ReturnValues.TRUE, true);
            }
        }
        return ret;
    }

    /**
     * Gets the generic products.
     *
     * @param _costEstimateInst the cost estimate inst
     * @return the generic products
     * @throws EFapsException on error
     */
    protected List<Instance> getGenericProducts(final Instance _costEstimateInst)
        throws EFapsException
    {
        final List<Instance> ret = new ArrayList<>();
        final QueryBuilder ce2esQueryBldr = new QueryBuilder(CIConstruction.CostEstimate2EntrySheet);
        ce2esQueryBldr.addWhereAttrEqValue(CIConstruction.CostEstimate2EntrySheet.FromLink, _costEstimateInst);

        final QueryBuilder espQueryBldr = new QueryBuilder(CIConstruction.EntrySheetPosition);
        espQueryBldr.addWhereAttrInQuery(CIConstruction.EntrySheetPosition.DocumentAbstractLink,
                        ce2esQueryBldr.getAttributeQuery(CIConstruction.CostEstimate2EntrySheet.ToLink));

        final QueryBuilder ebQueryBldr = new QueryBuilder(CIConstruction.EntryBOM);
        ebQueryBldr.addWhereAttrInQuery(CIConstruction.EntryBOM.FromLink,
                        espQueryBldr.getAttributeQuery(CIConstruction.EntrySheetPosition.Product));

        final QueryBuilder queryBldr = new QueryBuilder(CIProducts.ProductGeneric);
        queryBldr.addWhereAttrInQuery(CIProducts.ProductGeneric.ID,
                        ebQueryBldr.getAttributeQuery(CIConstruction.EntryBOM.ToLink));
        final InstanceQuery query = queryBldr.getQuery();
        ret.addAll(query.execute());
        return ret;
    }

    /**
     * Generic products ui field value.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return genericProductsUIFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Table table = new Table();
        table.addRow()
            .addHeaderColumn(DBProperties.getProperty(CostEstimate.class.getName() + ".GenericProductColumn"))
            .addHeaderColumn(DBProperties.getProperty(CostEstimate.class.getName() + ".ReplacementProductColumn"));
        for (final Instance inst : getGenericProducts(_parameter.getInstance())) {
            final PrintQuery print = new PrintQuery(inst);
            print.addAttribute(CIProducts.ProductAbstract.Name, CIProducts.ProductAbstract.Description);
            print.execute();
            table.addRow()
                .addColumn("<input type=\"hidden\" name=\"orginal\" value=\"" + inst.getOid() + "\">"
                                +   print.getAttribute(CIProducts.ProductAbstract.Name) + " - "
                                + print.getAttribute(CIProducts.ProductAbstract.Description));

            final List<DropDownPosition> pos = new ArrayList<>();
            final List<Instance> replacInsts = new Product().getReplacements4Generic(_parameter, inst);
            final MultiPrintQuery multi = new MultiPrintQuery(replacInsts);
            multi.addAttribute(CIProducts.ProductAbstract.Name, CIProducts.ProductAbstract.Description);
            multi.execute();
            while (multi.next()) {
                pos.add(new DropDownPosition(multi.getCurrentInstance().getOid(), multi
                                .getAttribute(CIProducts.ProductAbstract.Name) + " - "
                                + multi.getAttribute(CIProducts.ProductAbstract.Description)));
            }
            table.addColumn(new Field().getDropDownField(_parameter, pos));
        }

        ret.put(ReturnValues.SNIPLETT, table.toHtml());
        return ret;
    }

    /**
     * Replace generic products.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return replaceGenericProducts(final Parameter _parameter)
        throws EFapsException
    {
        final String[] origOids = _parameter.getParameterValues("orginal");
        final String[] replOids = _parameter.getParameterValues(
                                    CIFormConstruction.Construction_CostEstimateRealizationReplaceGenericProductsForm.genericProducts.name);
        for (int i = 0; i < origOids.length; i++) {
            final Instance origInstance = Instance.get(origOids[i]);
            final Instance replInstance = Instance.get(replOids[i]);
            final QueryBuilder ce2esQueryBldr = new QueryBuilder(CIConstruction.CostEstimate2EntrySheet);
            ce2esQueryBldr.addWhereAttrEqValue(CIConstruction.CostEstimate2EntrySheet.FromLink, _parameter.getInstance());

            final QueryBuilder espQueryBldr = new QueryBuilder(CIConstruction.EntrySheetPosition);
            espQueryBldr.addWhereAttrInQuery(CIConstruction.EntrySheetPosition.DocumentAbstractLink,
                            ce2esQueryBldr.getAttributeQuery(CIConstruction.CostEstimate2EntrySheet.ToLink));

            final QueryBuilder ebQueryBldr = new QueryBuilder(CIConstruction.EntryBOM);
            ebQueryBldr.addWhereAttrInQuery(CIConstruction.EntryBOM.FromLink,
                            espQueryBldr.getAttributeQuery(CIConstruction.EntrySheetPosition.Product));
            ebQueryBldr.addWhereAttrEqValue(CIConstruction.EntryBOM.ToLink, origInstance);
            final InstanceQuery query = ebQueryBldr.getQuery();
            query.execute();
            while (query.next()) {
                final Update update = new Update(query.getCurrentValue());
                update.add(CIConstruction.EntryBOM.ToLink, replInstance);
                update.execute();
            }
        }
        return new Return();
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
            final QueryBuilder attrQueryBlr = new QueryBuilder(CIConstruction.CostEstimate2EntrySheet);
            attrQueryBlr.addWhereAttrEqValue(CIConstruction.CostEstimate2EntrySheet.FromLink, _parameter.getInstance());

            final QueryBuilder queryBlr = new QueryBuilder(CIConstruction.EntrySheet);
            queryBlr.addWhereAttrInQuery(CIConstruction.EntrySheet.ID, attrQueryBlr.getAttributeQuery(
                            CIConstruction.CostEstimate2EntrySheet.ToLink));

            final List<Instance> instances = queryBlr.getQuery().execute();

            new EntrySheet().setEfficiencyOverAll(_parameter, effOA, instances.toArray(new Instance[instances.size()]));
        } catch (final ParseException e) {
            LOG.error("Catched ParseException", e);
        }
        return new Return();
    }

    /**
     * Gets the aternate instance.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the aternate instance
     * @throws EFapsException on error
     */
    public Return getAternateInstance(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final PrintQuery print = new PrintQuery(_parameter.getInstance());
        final SelectBuilder selPos = SelectBuilder.get()
                        .linkto(CIConstruction.PositionGroupAbstract.AbstractPositionAbstractLink);
        final SelectBuilder selESPosID = new SelectBuilder(selPos)
            .attribute(CIConstruction.CostEstimatePositionAbstract.EntrySheetPositionLink);
        final SelectBuilder selProdInst = new SelectBuilder(selPos)
                        .linkto(CIConstruction.CostEstimatePositionAbstract.Product)
                        .instance();
        print.addSelect(selESPosID, selProdInst);
        print.execute();
        final Instance prodInst = print.getSelect(selProdInst);
        final Long eSPosID = print.<Long>getSelect(selESPosID);
        if (eSPosID != null) {
            final Instance eSPosInst = Instance.get(CIConstruction.EntrySheetPosition.getType(), eSPosID);
            ret.put(ReturnValues.INSTANCE, eSPosInst);
        } else if (prodInst != null && prodInst.isValid()) {
            ret.put(ReturnValues.INSTANCE, prodInst);
        }
        return ret;
    }

    @Override
    public String getJavaScript4SelectDoc(final Parameter _parameter)
        throws EFapsException
    {
        final String ret;
        if (TargetMode.EDIT.equals(_parameter.get(ParameterValues.ACCESSMODE))) {
            ret = InterfaceUtils.wrappInScriptTag(_parameter, "executeCalculator();\n", false, 2000).toString();
        } else {
            ret = super.getJavaScript4SelectDoc(_parameter);
        }
        return ret;
    }

    @Override
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
        return new Calculator(_parameter, _oldCalc, _oid, _quantity, _unitPrice, _discount, _priceFromDB, this) {

            private static final long serialVersionUID = 1L;

            @Override
            protected ProductPrice evalPriceFromDB(final Parameter _parameter)
                throws EFapsException
            {
                ProductPrice ret;
                final var prodInst = getProductInstance();
                if (InstanceUtils.isKindOf(prodInst, CIConstruction.EntryPartList)) {
                    final var print = new PrintQuery(prodInst);
                    final SelectBuilder selTotal = SelectBuilder.get().clazz(CIConstruction.EntryPartList_Class)
                                    .attribute(CIConstruction.EntryPartList_Class.Total);
                    print.addSelect(selTotal);
                    print.execute();
                    final BigDecimal total = print.getSelect(selTotal);
                    final var productPrice = new PriceUtil().new ProductPrice();
                    productPrice.setBasePrice(total);
                    productPrice.setCurrentPrice(total);
                    productPrice.setOrigPrice(total);
                    ret = productPrice;
                } else {
                    ret = super.evalPriceFromDB(_parameter);
                }
                return ret;
            }

        };
    }

    /**
     * Warning for amount greater zero.
     */
    public static class GenericProductsNotPermittedWarning
        extends AbstractWarning
    {
        /**
         * Constructor.
         */
        public GenericProductsNotPermittedWarning()
        {
            try {
                setError(!Construction.VERIFICATIONLISTALLOWGENERIC.get());
            } catch (final EFapsException e) {
                LOG.error("Catehc", e);
            }
        }
    }
}
