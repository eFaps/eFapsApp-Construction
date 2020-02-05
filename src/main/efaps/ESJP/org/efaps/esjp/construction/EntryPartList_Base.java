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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.efaps.admin.datamodel.Dimension;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.ui.AbstractCommand;
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
import org.efaps.esjp.common.AbstractCommon;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.esjp.common.uitable.MultiPrint;
import org.efaps.esjp.construction.EntryBOM_Base.BomCalculator;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.sales.Calculator;
import org.efaps.esjp.sales.PriceUtil;
import org.efaps.esjp.sales.PriceUtil_Base.ProductPrice;
import org.efaps.esjp.sales.util.Sales;
import org.efaps.ui.wicket.util.EFapsKey;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;


/**
 * TODO comment!
 *
 * @author Jan Moxter
 */
@EFapsUUID("cb0cbdd9-3a4e-4c2a-8994-a0f32cd6178d")
@EFapsApplication("eFapsApp-Construction")
public abstract class EntryPartList_Base
    extends AbstractCommon
{
    /**
     * used to store info during the request.
     */
    protected static final String REQKEY4COPIED = EntryPartList.class.getName() + ".RequestKey4Copied";

    /**
     * @param _parameter as passed from eFaps API.
     * @return new Return
     * @throws EFapsException on error.
     */
    public Return bomMultiPrint(final Parameter _parameter)
        throws EFapsException
    {
        final MultiPrint multi = new MultiPrint()
        {

            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                super.add2QueryBldr(_parameter, _queryBldr);
                final EntryBOM_Base bom = new EntryBOM();
                final IPositionType posType = bom.evalPostionType(_parameter);
                _queryBldr.addWhereAttrInQuery(CIProducts.BOMAbstract.ToAbstract,
                                bom.getProductAttrQuery(_parameter, posType));
            }
        };
        return multi.execute(_parameter);
    }

    /**
     * Method to create a entry part.
     *
     * @param _parameter as passed from eFaps API.
     * @return new Return
     * @throws EFapsException on error.
     */
    public Return create(final Parameter _parameter)
        throws EFapsException
    {
        final Instance currInst = Currency.getBaseCurrency();

        // create the PartList including the Classifications
        final Dimension dim = Dimension.get(Long.parseLong(_parameter
                        .getParameterValue(CIFormConstruction.Construction_EntryPartListForm.dimension.name)));
        final AbstractCommand command = (AbstractCommand) _parameter.get(ParameterValues.UIOBJECT);

        final Insert insert = new Insert(command.getTargetCreateType());
        insert.add(CIProducts.ProductAbstract.Name,
                        _parameter.getParameterValue(CIFormConstruction.Construction_EntryPartListForm.name.name));
        insert.add(CIProducts.ProductAbstract.Description,
                        _parameter.getParameterValue(CIFormConstruction.Construction_EntryPartListForm.description.name));
        insert.add(CIProducts.ProductAbstract.Dimension, dim.getId());
        insert.add(CIProducts.ProductAbstract.Active, true);

        final Instance instTax = Sales.DEFAULTTAXCAT4PRODUCT.get();
        insert.add(CISales.ProductAbstract.TaxCategory, instTax);
        insert.execute();
        final Instance prodInst = insert.getInstance();

        final Insert relInsert = new Insert(CIProducts.Product2Class);
        relInsert.add(CIProducts.Product2Class.Product, prodInst.getId());
        relInsert.add(CIProducts.Product2Class.ClassTypeId, CIConstruction.EntryPartList_Class.getType().getId());
        relInsert.execute();

        final EntryBOM_Base bom = new EntryBOM();
        final List<Calculator> calcList = bom.analyseTable(_parameter, null);

        // create the BOM relations
        final BigDecimal rateNetTotal = bom.getNetTotal(_parameter, calcList);
        final BigDecimal rateCrosstotal = bom.getCrossTotal(_parameter, calcList);
        BigDecimal effOA = null;
        BigDecimal effPers = null;
        BigDecimal effTools = null;
        int idx = 1;
        for (final Calculator calc : calcList) {
            if (!calc.isEmpty()) {
                final Insert bomInsert = new Insert(CIConstruction.EntryBOM);
                bomInsert.add(CIConstruction.EntryBOM.Position, idx);
                bomInsert.add(CIConstruction.EntryBOM.FromLink, prodInst.getId());
                bomInsert.add(CIConstruction.EntryBOM.ToLink, Instance.get(calc.getOid()));
                bomInsert.add(CIConstruction.EntryBOM.Quantity, calc.getQuantity());
                bomInsert.add(CIConstruction.EntryBOM.UoM, ((BomCalculator) calc).getUom());
                bomInsert.add(CIConstruction.EntryBOM.UnitPrice, calc.getNetUnitPrice());
                bomInsert.add(CIConstruction.EntryBOM.Price, calc.getNetPrice());
                // use the string to do not insert 1
                bomInsert.add(CIConstruction.EntryBOM.Factor, ((BomCalculator) calc).getFactorStr());
                bomInsert.add(CIConstruction.EntryBOM.Remark, ((BomCalculator) calc).getRemark());
                bomInsert.execute();

                effOA = ((BomCalculator) calc).getEfficiencyOverAll();
                effPers = ((BomCalculator) calc).getEfficiencyPersonal();
                effTools = ((BomCalculator) calc).getEfficiencyTools();
                idx++;
            }
        }

        // insert the classification
        final Insert classInsert = new Insert(CIConstruction.EntryPartList_Class);
        classInsert.add(CIConstruction.EntryPartList_Class.ProductLink, prodInst);
        classInsert.add(CIConstruction.EntryPartList_Class.EfficiencyUoM,
                        _parameter.getParameterValue(CIFormConstruction.Construction_EntryPartListForm.efficiencyUoM.name));
        classInsert.add(CIConstruction.EntryPartList_Class.EfficiencyOverAll, _parameter.getParameterValue(
                        CIFormConstruction.Construction_EntryPartListForm.efficiencyOverAll.name).isEmpty() ? null : effOA);
        classInsert.add(CIConstruction.EntryPartList_Class.EfficiencyPersonal, _parameter.getParameterValue(
                        CIFormConstruction.Construction_EntryPartListForm.efficiencyPersonal.name).isEmpty() ? null : effPers);
        classInsert.add(CIConstruction.EntryPartList_Class.EfficiencyTools, _parameter.getParameterValue(
                        CIFormConstruction.Construction_EntryPartListForm.efficiencyTools.name).isEmpty() ? null : effTools);
        classInsert.add(CIConstruction.EntryPartList_Class.CurrencyLink, currInst.getId());
        classInsert.add(CIConstruction.EntryPartList_Class.Total, rateNetTotal);
        classInsert.add(CIConstruction.EntryPartList_Class.ManualPercent,
                        _parameter.getParameterValue(CIFormConstruction.Construction_EntryPartListForm.manualPercent.name));
        classInsert.add(CIConstruction.EntryPartList_Class.ManualAmount, bom.getManualPercentAmount(_parameter, calcList, false));
        classInsert.execute();

        final Instance entrySheetInst = _parameter.getInstance();

        if (entrySheetInst != null && entrySheetInst.isValid()
                        && entrySheetInst.getType().isKindOf(CIConstruction.EntrySheet.getType())) {
            final DecimalFormat frmt = NumberFormatter.get().getFrmt4Total(CIConstruction.EntryPartList.getType());
            final int scale = frmt.getMaximumFractionDigits();

            final PrintQuery print = new PrintQuery(entrySheetInst);
            print.addAttribute(CIConstruction.EntrySheet.Rate, CIConstruction.EntrySheet.RateCurrencyId);
            print.executeWithoutAccessCheck();
            final Object[] rateObj = print.getAttribute(CIConstruction.EntrySheet.Rate);
            final Long currId = print.getAttribute(CIConstruction.EntrySheet.RateCurrencyId);
            final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                            RoundingMode.HALF_UP);
            final BigDecimal crossTotal = rateCrosstotal.divide(rate, RoundingMode.HALF_UP)
                            .setScale(scale, RoundingMode.HALF_UP);
            final BigDecimal netTotal = rateNetTotal.divide(rate, RoundingMode.HALF_UP)
                            .setScale(scale, RoundingMode.HALF_UP);

            // create the position for the related Document
            final Insert posInsert = new Insert(CIConstruction.EntrySheetPosition);
            posInsert.add(CIConstruction.EntrySheetPosition.EntrySheetLink, entrySheetInst);
            posInsert.add(CIConstruction.EntrySheetPosition.PositionNumber, 1);
            posInsert.add(CIConstruction.EntrySheetPosition.Product, prodInst);
            posInsert.add(CIConstruction.EntrySheetPosition.ProductDesc,
                            _parameter.getParameterValue(CIFormConstruction.Construction_EntryPartListForm.description.name));
            posInsert.add(CIConstruction.EntrySheetPosition.Quantity, BigDecimal.ONE);
            posInsert.add(CIConstruction.EntrySheetPosition.UoM, dim.getBaseUoM());
            posInsert.add(CIConstruction.EntrySheetPosition.CrossUnitPrice, crossTotal);
            posInsert.add(CIConstruction.EntrySheetPosition.NetUnitPrice, netTotal);
            posInsert.add(CIConstruction.EntrySheetPosition.CrossPrice, crossTotal);
            posInsert.add(CIConstruction.EntrySheetPosition.NetPrice, netTotal);
            posInsert.add(CIConstruction.EntrySheetPosition.Tax, calcList.get(0).getTaxCatId());
            posInsert.add(CIConstruction.EntrySheetPosition.Discount, BigDecimal.ZERO);
            posInsert.add(CIConstruction.EntrySheetPosition.DiscountNetUnitPrice, netTotal);
            posInsert.add(CIConstruction.EntrySheetPosition.CurrencyId, currInst);
            posInsert.add(CIConstruction.EntrySheetPosition.Rate, rateObj);
            posInsert.add(CIConstruction.EntrySheetPosition.RateCurrencyId, currId);
            posInsert.add(CIConstruction.EntrySheetPosition.RateNetUnitPrice, rateNetTotal);
            posInsert.add(CIConstruction.EntrySheetPosition.RateCrossUnitPrice, rateCrosstotal);
            posInsert.add(CIConstruction.EntrySheetPosition.RateDiscountNetUnitPrice, rateNetTotal);
            posInsert.add(CIConstruction.EntrySheetPosition.RateNetPrice, rateNetTotal);
            posInsert.add(CIConstruction.EntrySheetPosition.RateCrossPrice, rateCrosstotal);
            posInsert.execute();

            new EntrySheet().updateTotals(_parameter, _parameter.getInstance());
        }
        updatePriceList(_parameter, calcList);
        return new Return();
    }

    /**
     * Method to copy an EntryPartList.
     *
     * @param _parameter as passed from eFaps API.
     * @return new Return
     * @throws EFapsException on error.
     */
    public Return copyEntry(final Parameter _parameter)
        throws EFapsException
    {
        if (Context.getThreadContext().containsSessionAttribute(
                        CIFormConstruction.Construction_EntryPartListCopyForm.selectedEntryPartList.name)) {

            final String[] oids = (String[]) Context.getThreadContext().getSessionAttribute(
                            CIFormConstruction.Construction_EntryPartListCopyForm.selectedEntryPartList.name);
            if (oids != null) {

                Instance inst = Instance.get(oids[0]);
                if (inst.isValid() && inst.getType().isKindOf(CIConstruction.EntrySheetPosition)) {
                    final PrintQuery print = new PrintQuery(inst);
                    final SelectBuilder selInst = SelectBuilder.get().linkto(CIConstruction.EntrySheetPosition.Product)
                                    .instance();
                    print.addSelect(selInst);
                    print.execute();
                    inst = print.getSelect(selInst);
                }
                if (inst.isValid() && inst.getType().isKindOf(CIConstruction.EntryPartList)) {
                    // copy the entrypartlist
                    final Object[] obj = copy(_parameter, inst, inst.getType(), null, true, false);
                    if (obj != null && ((Instance) obj[0]).isValid()) {
                        final Instance currBaseInst = Currency.getBaseCurrency();
                        final Instance newProdInst = (Instance) obj[0];

                        // rename the copied entrypartlist
                        final Update update = new Update(newProdInst);
                        update.add(CIProducts.ProductAbstract.Name,
                                        _parameter.getParameterValue(CIFormConstruction.Construction_EntryPartListCopyForm.name.name));
                        update.add(CIProducts.ProductAbstract.Description, _parameter.getParameterValue(
                                        CIFormConstruction.Construction_EntryPartListCopyForm.description.name));
                        update.execute();

                        final PrintQuery print = new PrintQuery(newProdInst);
                        print.addAttribute(CIProducts.ProductAbstract.Dimension,
                                        CIProducts.ProductAbstract.Description);
                        print.execute();
                        final Dimension dim = Dimension.get(print
                                        .<Long>getAttribute(CIProducts.ProductAbstract.Dimension));

                        // create the position for the related Document
                        final Insert posInsert = new Insert(CIConstruction.EntrySheetPosition);
                        posInsert.add(CIConstruction.EntrySheetPosition.EntrySheetLink, _parameter.getInstance());
                        posInsert.add(CIConstruction.EntrySheetPosition.PositionNumber, 1);
                        posInsert.add(CIConstruction.EntrySheetPosition.Product, ((Instance) obj[0]).getId());
                        posInsert.add(CIConstruction.EntrySheetPosition.ProductDesc,
                                        print.<String>getAttribute(CIProducts.ProductAbstract.Description));
                        posInsert.add(CIConstruction.EntrySheetPosition.Quantity, BigDecimal.ONE);
                        posInsert.add(CIConstruction.EntrySheetPosition.UoM, dim.getBaseUoM());
                        posInsert.add(CIConstruction.EntrySheetPosition.CrossUnitPrice, obj[1]);
                        posInsert.add(CIConstruction.EntrySheetPosition.NetUnitPrice, obj[1]);
                        posInsert.add(CIConstruction.EntrySheetPosition.CrossPrice, obj[1]);
                        posInsert.add(CIConstruction.EntrySheetPosition.NetPrice, obj[1]);
                        posInsert.add(CIConstruction.EntrySheetPosition.Tax, 0);
                        posInsert.add(CIConstruction.EntrySheetPosition.Discount, BigDecimal.ZERO);
                        posInsert.add(CIConstruction.EntrySheetPosition.DiscountNetUnitPrice, obj[1]);
                        posInsert.add(CIConstruction.EntrySheetPosition.CurrencyId, currBaseInst);
                        posInsert.add(CIConstruction.EntrySheetPosition.Rate, new Object[] { BigDecimal.ONE, BigDecimal.ONE });
                        posInsert.add(CIConstruction.EntrySheetPosition.RateCurrencyId, currBaseInst);
                        posInsert.add(CIConstruction.EntrySheetPosition.RateNetUnitPrice, obj[1]);
                        posInsert.add(CIConstruction.EntrySheetPosition.RateCrossUnitPrice, obj[1]);
                        posInsert.add(CIConstruction.EntrySheetPosition.RateDiscountNetUnitPrice, obj[1]);
                        posInsert.add(CIConstruction.EntrySheetPosition.RateNetPrice, obj[1]);
                        posInsert.add(CIConstruction.EntrySheetPosition.RateCrossPrice, obj[1]);
                        posInsert.execute();
                        new EntrySheet().updateTotals(_parameter, _parameter.getInstance());
                    }
                }
            }
        }
        return new Return();
    }

    /**
     * @param _parameter as passed from eFaps API.
     * @return new Return containing the value for the description field
     * @throws EFapsException on error.
     */
    public Return getDescription4CopyFieldValueUI(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        if (_parameter.getParameterValues("selectedRow") != null) {
            final Instance inst = Instance.get(_parameter.getParameterValue("selectedRow"));
            if (inst.isValid() && inst.getType().isKindOf(CIConstruction.EntrySheetPosition)) {
                final PrintQuery print = new PrintQuery(inst);
                final SelectBuilder selDescr = SelectBuilder.get().linkto(CIConstruction.EntrySheetPosition.Product)
                                .attribute(CIProducts.ProductAbstract.Description);
                print.addSelect(selDescr);
                print.execute();
                ret.put(ReturnValues.VALUES, print.getSelect(selDescr));
            } else {
                final PrintQuery print = new PrintQuery(inst);
                print.addAttribute(CIConstruction.EntryPartList.Description);
                print.execute();
                ret.put(ReturnValues.VALUES, print.getAttribute(CIConstruction.EntryPartList.Description));
            }
        }
        return ret;
    }

    /**
     * basically just recalculate the stuff.
     *
     * @param _parameter as passed from eFaps API.
     * @param _entryPartListInst the entry part list inst
     * @throws EFapsException on error.
     */
    public void update4EfficenyOverAll(final Parameter _parameter,
                                       final Instance _entryPartListInst)
        throws EFapsException
    {
        final EntryBOM bom = new EntryBOM();
        final Map<Instance, Calculator> calcMap = new EntryBOM().getCalculators4EntryPartList(_parameter,
                        _entryPartListInst);
        // update the the BOM relations
        for (final Entry<Instance, Calculator> entry : calcMap.entrySet()) {
            final Update update = new Update(entry.getKey());
            update.add(CIConstruction.EntryBOM.Quantity, entry.getValue().getQuantity());
            update.add(CIConstruction.EntryBOM.UnitPrice, entry.getValue().getNetUnitPrice());
            update.add(CIConstruction.EntryBOM.Price, entry.getValue().getNetPrice());
            update.execute();
        }
        final List<Calculator> calcList = new ArrayList<>(calcMap.values());
        final BigDecimal total = bom.getNetTotal(_parameter, calcList);
        final BigDecimal crosstotal = bom.getCrossTotal(_parameter, calcList);

        final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.EntryPartList_Class);
        queryBldr.addWhereAttrEqValue(CIConstruction.EntryPartList_Class.ProductLink, _entryPartListInst);
        final InstanceQuery query = queryBldr.getQuery();
        query.execute();
        query.next();
        final Instance classInst = query.getCurrentValue();
        final Update partListUpdate = new Update(classInst);
        partListUpdate.add(CIConstruction.EntryPartList_Class.Total, total);
        partListUpdate.execute();

        final QueryBuilder posQueryBldr = new QueryBuilder(CIConstruction.EntrySheetPosition);
        posQueryBldr.addWhereAttrEqValue(CIConstruction.EntrySheetPosition.Product, _entryPartListInst);
        final InstanceQuery posQuery = posQueryBldr.getQuery();
        posQuery.execute();
        while (posQuery.next()) {
            final Instance posInst = posQuery.getCurrentValue();
            final Update posUpdate = new Update(posInst);
            posUpdate.add(CIConstruction.EntrySheetPosition.CrossUnitPrice, crosstotal);
            posUpdate.add(CIConstruction.EntrySheetPosition.NetUnitPrice, total);
            posUpdate.add(CIConstruction.EntrySheetPosition.CrossPrice, crosstotal);
            posUpdate.add(CIConstruction.EntrySheetPosition.NetPrice, total);
            posUpdate.add(CIConstruction.EntrySheetPosition.DiscountNetUnitPrice, total);
            posUpdate.add(CIConstruction.EntrySheetPosition.RateNetUnitPrice, total);
            posUpdate.add(CIConstruction.EntrySheetPosition.RateCrossUnitPrice, crosstotal);
            posUpdate.add(CIConstruction.EntrySheetPosition.RateDiscountNetUnitPrice, total);
            posUpdate.add(CIConstruction.EntrySheetPosition.RateNetPrice, total);
            posUpdate.add(CIConstruction.EntrySheetPosition.RateCrossPrice, crosstotal);
            posUpdate.execute();
        }
    }

    /**
     * Method to update a entry part from a product part.
     *
     * @param _parameter as passed from eFaps API.
     * @return new Return
     * @throws EFapsException on error.
     */
    public Return update(final Parameter _parameter)
        throws EFapsException
    {

        final Dimension dim = Dimension.get(Long.parseLong(
                        _parameter.getParameterValue(CIFormConstruction.Construction_EntryPartListForm.dimension.name)));
        // Update the PartList
        final Update update = new Update(_parameter.getInstance());
        update.add(CIProducts.ProductAbstract.Name, _parameter.getParameterValue(
                        CIFormConstruction.Construction_EntryPartListForm.name.name));
        update.add(CIProducts.ProductAbstract.Description,
                        _parameter.getParameterValue(CIFormConstruction.Construction_EntryPartListForm.description.name));
        update.add(CIProducts.ProductAbstract.Dimension, dim.getId());
        update.execute();

        final EntryBOM_Base bom = new EntryBOM();
        final List<Calculator> calcList = bom.analyseTable(_parameter, null);

        @SuppressWarnings("unchecked")
        final Map<String, String> uiId2OID = (Map<String, String>) _parameter.get(ParameterValues.OIDMAP4UI);
        final String[] rowKeys = _parameter.getParameterValues(EFapsKey.TABLEROW_NAME.getKey());
        final Set<Instance> bomInsts = new HashSet<>();
        // create the BOM relations
        final BigDecimal rateNetTotal = bom.getNetTotal(_parameter, calcList);
        final BigDecimal rateCrosstotal = bom.getCrossTotal(_parameter, calcList);

        int i = 0;
        BigDecimal effOA = null;
        BigDecimal effPers = null;
        BigDecimal effTools = null;
        int idx = 1;
        for (final Calculator calc : calcList) {
            final String rowKey = rowKeys[i];
            if (rowKey != null && !rowKey.isEmpty()) {
                final Instance rowInst = Instance.get(uiId2OID.get(rowKey));
                final Instance prodInst = Instance.get(calc.getOid());
                // UPDATE
                final Update rowUpdate;
                if (rowInst.isValid() && prodInst.isValid()) {
                    rowUpdate = new Update(rowInst);
                    bomInsts.add(rowInst);
                } else if (!calc.isEmpty() && prodInst.isValid()) {
                    // CREATE new ones
                    rowUpdate = new Insert(CIConstruction.EntryBOM);
                    rowUpdate.add(CIConstruction.EntryBOM.FromLink, _parameter.getInstance());
                } else {
                    rowUpdate = null;
                }
                if (rowUpdate != null) {
                    rowUpdate.add(CIConstruction.EntryBOM.Position, idx);
                    rowUpdate.add(CIConstruction.EntryBOM.ToLink, prodInst);
                    rowUpdate.add(CIConstruction.EntryBOM.Quantity, calc.getQuantity());
                    rowUpdate.add(CIConstruction.EntryBOM.UoM, ((BomCalculator) calc).getUom());
                    rowUpdate.add(CIConstruction.EntryBOM.UnitPrice, calc.getNetUnitPrice());
                    rowUpdate.add(CIConstruction.EntryBOM.Price, calc.getNetPrice());
                    // use the string to do not insert 1
                    rowUpdate.add(CIConstruction.EntryBOM.Factor, ((BomCalculator) calc).getFactorStr());
                    rowUpdate.add(CIConstruction.EntryBOM.Remark, ((BomCalculator) calc).getRemark());
                    rowUpdate.execute();
                    bomInsts.add(rowUpdate.getInstance());
                    effOA = ((BomCalculator) calc).getEfficiencyOverAll();
                    effPers = ((BomCalculator) calc).getEfficiencyPersonal();
                    effTools = ((BomCalculator) calc).getEfficiencyTools();
                    idx++;
                }
            }
            i++;
        }

        // CHECK for deletion
        final QueryBuilder bomQueryBldr = new QueryBuilder(CIConstruction.EntryBOM);
        bomQueryBldr.addWhereAttrEqValue(CIConstruction.EntryBOM.FromLink, _parameter.getInstance());
        final InstanceQuery bomQuery = bomQueryBldr.getQuery();
        bomQuery.execute();
        while (bomQuery.next()) {
            if (!bomInsts.contains(bomQuery.getCurrentValue())) {
                final Delete del = new Delete(bomQuery.getCurrentValue());
                del.execute();
            }
        }

        // Update the classification
        final QueryBuilder classQueryBldr = new QueryBuilder(CIConstruction.EntryPartList_Class);
        classQueryBldr.addWhereAttrEqValue(CIConstruction.EntryPartList_Class.ProductLink, _parameter.getInstance());
        final InstanceQuery query = classQueryBldr.getQuery();
        query.execute();
        query.next();
        final Update classUpdate = new Update(query.getCurrentValue());
        classUpdate.add(CIConstruction.EntryPartList_Class.EfficiencyUoM,
                        _parameter.getParameterValue(CIFormConstruction.Construction_EntryPartListForm.efficiencyUoM.name));
        classUpdate.add(CIConstruction.EntryPartList_Class.EfficiencyOverAll, _parameter.getParameterValue(
                        CIFormConstruction.Construction_EntryPartListForm.efficiencyOverAll.name).isEmpty() ? null : effOA);
        classUpdate.add(CIConstruction.EntryPartList_Class.EfficiencyPersonal, _parameter.getParameterValue(
                        CIFormConstruction.Construction_EntryPartListForm.efficiencyPersonal.name).isEmpty() ? null : effPers);
        classUpdate.add(CIConstruction.EntryPartList_Class.EfficiencyTools, _parameter.getParameterValue(
                        CIFormConstruction.Construction_EntryPartListForm.efficiencyTools.name).isEmpty() ? null : effTools);
        classUpdate.add(CIConstruction.EntryPartList_Class.Total, rateNetTotal);
        classUpdate.add(CIConstruction.EntryPartList_Class.ManualPercent,
                        _parameter.getParameterValue(CIFormConstruction.Construction_EntryPartListForm.manualPercent.name));
        classUpdate.add(CIConstruction.EntryPartList_Class.ManualAmount, bom.getManualPercentAmount(_parameter, calcList, false));
        classUpdate.execute();

        final HashSet<Instance> entrySheetInsts = new HashSet<>();
        final DecimalFormat frmt = NumberFormatter.get().getFrmt4Total(CIConstruction.EntryPartList.getType());
        final int scale = frmt.getMaximumFractionDigits();

        // Update the related EntrySheet
        final QueryBuilder cSQueryBldr = new QueryBuilder(CIConstruction.EntrySheetPosition);
        cSQueryBldr.addWhereAttrEqValue(CIConstruction.EntrySheetPosition.Product, _parameter.getInstance());
        final MultiPrintQuery multi = cSQueryBldr.getPrint();
        final SelectBuilder selES = new SelectBuilder().linkto(CIConstruction.EntrySheetPosition.EntrySheetLink);
        final SelectBuilder selInst = new SelectBuilder(selES).instance();
        final SelectBuilder selRate = new SelectBuilder(selES).attribute(CIConstruction.EntrySheet.Rate);
        final SelectBuilder selRateCurr = new SelectBuilder(selES).attribute(CIConstruction.EntrySheet.RateCurrencyId);
        multi.addSelect(selInst, selRate, selRateCurr);
        multi.execute();
        while (multi.next()) {
            final Instance entrySheetInst = multi.<Instance>getSelect(selInst);
            final Object[] rateObj;
            final Long currId;
            if (entrySheetInst != null && entrySheetInst.isValid()) {
                entrySheetInsts.add(entrySheetInst);
                rateObj = multi.getSelect(selRate);
                currId = multi.getSelect(selRateCurr);
            } else {
                rateObj = new BigDecimal[]{ BigDecimal.ONE, BigDecimal.ONE };
                currId = Currency.getBaseCurrency().getId();
            }
            final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                            RoundingMode.HALF_UP);
            final BigDecimal crossTotal = rateCrosstotal.divide(rate, RoundingMode.HALF_UP)
                            .setScale(scale, RoundingMode.HALF_UP);
            final BigDecimal netTotal = rateNetTotal.divide(rate, RoundingMode.HALF_UP)
                            .setScale(scale, RoundingMode.HALF_UP);
            final Instance posInst = multi.getCurrentInstance();
            final Update posUpdate = new Update(posInst);
            posUpdate.add(CIConstruction.EntrySheetPosition.UoM, dim.getBaseUoM().getId());
            posUpdate.add(CIConstruction.EntrySheetPosition.ProductDesc,
                            _parameter.getParameterValue(CIFormConstruction.Construction_EntryPartListForm.description.name));
            posUpdate.add(CIConstruction.EntrySheetPosition.CrossUnitPrice, crossTotal);
            posUpdate.add(CIConstruction.EntrySheetPosition.NetUnitPrice, netTotal);
            posUpdate.add(CIConstruction.EntrySheetPosition.CrossPrice, crossTotal);
            posUpdate.add(CIConstruction.EntrySheetPosition.NetPrice, netTotal);
            posUpdate.add(CIConstruction.EntrySheetPosition.DiscountNetUnitPrice, netTotal);
            posUpdate.add(CIConstruction.EntrySheetPosition.RateNetUnitPrice, rateNetTotal);
            posUpdate.add(CIConstruction.EntrySheetPosition.RateCrossUnitPrice, rateCrosstotal);
            posUpdate.add(CIConstruction.EntrySheetPosition.RateDiscountNetUnitPrice, rateNetTotal);
            posUpdate.add(CIConstruction.EntrySheetPosition.RateNetPrice, rateNetTotal);
            posUpdate.add(CIConstruction.EntrySheetPosition.RateCrossPrice, rateCrosstotal);
            posUpdate.add(CIConstruction.EntrySheetPosition.RateCurrencyId, currId);
            posUpdate.execute();
        }

        if (!entrySheetInsts.isEmpty()) {
            new EntrySheet().updateTotals(_parameter, entrySheetInsts.toArray(new Instance[entrySheetInsts.size()]));
        }
        updatePriceList(_parameter, calcList);
        return new Return();
    }

    /**
     * Method to update the price of a entry bom.
     *
     * @param _parameter as passed from eFaps API.
     * @param _calcs A List with the current calculators.
     * @throws EFapsException on error.
     */
    protected void updatePriceList(final Parameter _parameter,
                                   final List<Calculator> _calcs)
        throws EFapsException
    {
        for (final Calculator calc : _calcs) {
            updatePriceList(_parameter, calc);
        }
    }
    /**
     * Method to update a prices.
     *
     * @param _parameter as passed from eFaps API
     * @param _calc The current calculator to update.
     * @throws EFapsException on error.
     */
    protected void updatePriceList(final Parameter _parameter,
                                   final Calculator _calc)
        throws EFapsException
    {
        if (!_calc.isEmpty()) {
            final PriceUtil priceutil = new PriceUtil();
            final ProductPrice price = priceutil.getPrice(_parameter, new DateTime(),
                            Instance.get(_calc.getOid()), _calc.getPriceListType().getUUID(), null, false);
            if (_calc.getNetUnitPrice().compareTo(BigDecimal.ZERO) != 0
                            && _calc.getNetUnitPrice().compareTo(price.getOrigPrice()) != 0) {
                final Insert insert = new Insert(_calc.getPriceListType());
                insert.add(CIProducts.ProductPricelistAbstract.ProductAbstractLink, Instance.get(_calc.getOid()));
                insert.add(CIProducts.ProductPricelistAbstract.ValidFrom, new DateTime().withTimeAtStartOfDay());
                insert.add(CIProducts.ProductPricelistAbstract.ValidUntil,
                                new DateTime().withTimeAtStartOfDay().plusYears(10));
                insert.executeWithoutAccessCheck();

                final Insert posInsert = new Insert(CIProducts.ProductPricelistPosition);
                posInsert.add(CIProducts.ProductPricelistPosition.ProductPricelist, insert.getInstance());
                posInsert.add(CIProducts.ProductPricelistPosition.Price, _calc.getNetUnitPrice());
                posInsert.add(CIProducts.ProductPricelistPosition.CurrencyId,
                                _calc.getProductPrice().getCurrentCurrencyInstance().getId());
                posInsert.executeWithoutAccessCheck();
            }
        }
    }


    /**
     * Update the price of a Products_EntryBOM, update the EntryPartList.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _bomInstance the bom instance
     * @param _price the price
     * @return the List of
     * @throws EFapsException on error
     */
    public Instance updateProductPrice(final Parameter _parameter,
                                       final Instance _bomInstance,
                                       final BigDecimal _price)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_bomInstance);
        final SelectBuilder sel = SelectBuilder.get().linkto(CIConstruction.EntryBOM.FromLink).instance();
        print.addSelect(sel);
        print.execute();
        final Instance ret = print.<Instance>getSelect(sel);

        final EntryBOM_Base bom = new EntryBOM();
        final Map<Instance, Calculator> calcMap = bom.getCalculators4EntryPartList(_parameter, ret);

        // update the the BOM relations
        for (final Entry<Instance, Calculator> entry : calcMap.entrySet()) {
            if (entry.getKey().equals(_bomInstance)) {
                entry.getValue().setNetUnitPrice(_price);
                final Update update = new Update(_bomInstance);
                update.add(CIConstruction.EntryBOM.UnitPrice, entry.getValue().getNetUnitPrice());
                update.add(CIConstruction.EntryBOM.Price, entry.getValue().getNetPrice());
                update.execute();
            }
        }
        final List<Calculator> calcList = new ArrayList<>(calcMap.values());

        final Parameter parameter = ParameterUtil.clone(_parameter);
        final PrintQuery print2 = new PrintQuery(ret);
        final SelectBuilder selManualPercent = SelectBuilder.get().clazz(CIConstruction.EntryPartList_Class)
                        .attribute(CIConstruction.EntryPartList_Class.ManualPercent);
        print2.addSelect(selManualPercent);
        print2.execute();
        final DecimalFormat formatter = NumberFormatter.get().getFormatter();

        final BigDecimal manualPercent = print2.<BigDecimal>getSelect(selManualPercent);

        ParameterUtil.setParameterValues(parameter, CIFormConstruction.Construction_EntryPartListForm.manualPercent.name,
                        manualPercent == null ? null : formatter.format(manualPercent));

        final BigDecimal total = bom.getNetTotal(parameter, calcList);
        final BigDecimal crosstotal = bom.getCrossTotal(parameter, calcList);
        final BigDecimal manualAmount = bom.getManualPercentAmount(parameter, calcList, false);

        final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.EntryPartList_Class);
        queryBldr.addWhereAttrEqValue(CIConstruction.EntryPartList_Class.ProductLink, ret);
        final InstanceQuery query = queryBldr.getQuery();
        query.execute();
        query.next();
        final Instance classInst = query.getCurrentValue();
        final Update partListUpdate = new Update(classInst);
        partListUpdate.add(CIConstruction.EntryPartList_Class.ManualAmount, manualAmount);
        partListUpdate.add(CIConstruction.EntryPartList_Class.Total, total);
        partListUpdate.execute();

        final QueryBuilder posQueryBldr = new QueryBuilder(CIConstruction.EntrySheetPosition);
        posQueryBldr.addWhereAttrEqValue(CIConstruction.EntrySheetPosition.Product, ret);
        final InstanceQuery posQuery = posQueryBldr.getQuery();
        posQuery.execute();
        while (posQuery.next()) {
            final Instance posInst = posQuery.getCurrentValue();
            final Update posUpdate = new Update(posInst);
            posUpdate.add(CIConstruction.EntrySheetPosition.CrossUnitPrice, crosstotal);
            posUpdate.add(CIConstruction.EntrySheetPosition.NetUnitPrice, total);
            posUpdate.add(CIConstruction.EntrySheetPosition.CrossPrice, crosstotal);
            posUpdate.add(CIConstruction.EntrySheetPosition.NetPrice, total);
            posUpdate.add(CIConstruction.EntrySheetPosition.DiscountNetUnitPrice, total);
            posUpdate.add(CIConstruction.EntrySheetPosition.RateNetUnitPrice, total);
            posUpdate.add(CIConstruction.EntrySheetPosition.RateCrossUnitPrice, crosstotal);
            posUpdate.add(CIConstruction.EntrySheetPosition.RateDiscountNetUnitPrice, total);
            posUpdate.add(CIConstruction.EntrySheetPosition.RateNetPrice, total);
            posUpdate.add(CIConstruction.EntrySheetPosition.RateCrossPrice, crosstotal);
            posUpdate.execute();
        }
        return ret;
    }

    /**
     * Copy values.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return copyValues(final Parameter _parameter)
        throws EFapsException
    {
        Instance origInst = Instance.get(_parameter.getParameterValue("selectedRow"));
        if (InstanceUtils.isKindOf(origInst, CIProducts.TreeViewAbstract)) {
            final PrintQuery print = new PrintQuery(origInst);
            final SelectBuilder selInst = SelectBuilder.get().linkto(CIProducts.TreeViewAbstract.ProductAbstractLink)
                            .instance();
            print.addSelect(selInst);
            print.execute();
            origInst = print.getSelect(selInst);
        }
        if (InstanceUtils.isValid(origInst) && InstanceUtils.isValid(_parameter.getInstance())) {
            final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.EntryBOM);
            queryBldr.addWhereAttrEqValue(CIConstruction.EntryBOM.FromLink, _parameter.getInstance());
            final InstanceQuery query = queryBldr.getQuery();
            query.execute();
            while (query.next()) {
                new Delete(query.getCurrentValue()).execute();
            }
            copy(_parameter, origInst, null, _parameter.getInstance(), true, false);
        }
        return new Return();
    }

    /**
     * Multi print for copy values.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return multiPrint4copyValues(final Parameter _parameter)
        throws EFapsException
    {
        final MultiPrint multi = new MultiPrint()
        {

            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                //get the current EntrySheet
                final QueryBuilder espQueryBldr = new QueryBuilder(CIConstruction.EntrySheetPosition);
                espQueryBldr.addWhereAttrEqValue(CIConstruction.EntrySheetPosition.Product, _parameter.getInstance());

                // go up to the costestimate
                final QueryBuilder ceQueryBldr = new QueryBuilder(CIConstruction.CostEstimate2EntrySheet);
                ceQueryBldr.addWhereAttrInQuery(CIConstruction.CostEstimate2EntrySheet.ToLink,
                                espQueryBldr.getAttributeQuery(CIConstruction.EntrySheetPosition.EntrySheetLink));
                //change to costestimae
                final QueryBuilder ceQueryBldr2 = new QueryBuilder(CIConstruction.CostEstimate2EntrySheet);
                ceQueryBldr2.addWhereAttrInQuery(CIConstruction.CostEstimate2EntrySheet.FromLink,
                                ceQueryBldr.getAttributeQuery(CIConstruction.CostEstimate2EntrySheet.FromLink));

                final QueryBuilder queryBldr2 = new QueryBuilder(CIConstruction.EntrySheetPosition);
                queryBldr2.addWhereAttrInQuery(CIConstruction.EntrySheetPosition.DocumentAbstractLink,
                                ceQueryBldr2.getAttributeQuery(CIConstruction.CostEstimate2EntrySheet.ToLink));

                _queryBldr.addWhereAttrInQuery(CIConstruction.EntryPartList.ID,
                                queryBldr2.getAttributeQuery(CIConstruction.EntrySheetPosition.Product));
                _queryBldr.addWhereAttrNotEqValue(CIConstruction.EntryPartList.ID, _parameter.getInstance());
            }
        };
        return multi.execute(_parameter);
    }

    /**
     * Gets the entry sheet field value.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the entry sheet field value
     * @throws EFapsException on error
     */
    @SuppressWarnings("unchecked")
    public Return getEntrySheetFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        Map<Instance, Set<String>> values;
        final String key = this.getClass().getName() + ".Request4EntrySheetFieldValue";
        if (!Context.getThreadContext().containsRequestAttribute(key)) {
            values = new HashMap<>();
            Context.getThreadContext().setRequestAttribute(key, values);
            final List<Instance> instances = (List<Instance>) _parameter.get(ParameterValues.REQUEST_INSTANCES);
            final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.EntrySheetPosition);
            queryBldr.addWhereAttrEqValue(CIConstruction.EntrySheetPosition.Product, instances.toArray());
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selProdInst = SelectBuilder.get().linkto(CIConstruction.EntrySheetPosition.Product).instance();
            final SelectBuilder selEntrySheetName = SelectBuilder.get().linkto(CIConstruction.EntrySheetPosition.EntrySheetLink)
                            .attribute(CIConstruction.EntrySheet.Name);
            final SelectBuilder selEntrySheetDescr = SelectBuilder.get().linkto(CIConstruction.EntrySheetPosition.EntrySheetLink)
                            .attribute(CIConstruction.EntrySheet.Description);
            multi.addSelect(selProdInst, selEntrySheetName, selEntrySheetDescr);
            multi.execute();
            while (multi.next()) {
                final Instance prodInst = multi.getSelect(selProdInst);
                Set<String> names;
                if (values.containsKey(prodInst)) {
                    names = values.get(prodInst);
                } else {
                    names = new TreeSet<>();
                    values.put(prodInst, names);
                }
                names.add(multi.<String>getSelect(selEntrySheetName) + " - " + multi.<String>getSelect(
                                selEntrySheetDescr));
            }
        } else {
            values = (Map<Instance, Set<String>>) Context.getThreadContext().getRequestAttribute(key);
        }
        ret.put(ReturnValues.VALUES, StringUtils.join(values.get(_parameter.getInstance()), ", "));
        return ret;
    }

    /**
     * Method to copy the data from a entry or product part to a new entry or
     * template part.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _origInst The entry or product part to copy.
     * @param _createType the create type
     * @param _targetInstance the target instance
     * @param _copyPrice copy the price also
     * @param _evalPrice evaluate the price
     * @return Object[] with the instance and the price.
     * @throws EFapsException on error.
     */
    @SuppressWarnings("unchecked")
    public Object[] copy(final Parameter _parameter,
                         final Instance _origInst,
                         final Type _createType,
                         final Instance _targetInstance,
                         final boolean _copyPrice,
                         final boolean _evalPrice)
        throws EFapsException
    {
        Object[] ret = null;

        final PrintQuery print = new PrintQuery(_origInst);
        final SelectBuilder selEffOA = new SelectBuilder().clazz(CIConstruction.EntryPartList_Class)
                        .attribute(CIConstruction.EntryPartList_Class.EfficiencyOverAll);
        final SelectBuilder selEffPers = new SelectBuilder().clazz(CIConstruction.EntryPartList_Class)
                        .attribute(CIConstruction.EntryPartList_Class.EfficiencyPersonal);
        final SelectBuilder selEffTools = new SelectBuilder().clazz(CIConstruction.EntryPartList_Class)
                        .attribute(CIConstruction.EntryPartList_Class.EfficiencyTools);
        final SelectBuilder selEffUoM = new SelectBuilder().clazz(CIConstruction.EntryPartList_Class)
                        .attribute(CIConstruction.EntryPartList_Class.EfficiencyUoM);
        final SelectBuilder selCur = new SelectBuilder().clazz(CIConstruction.EntryPartList_Class)
                        .linkto(CIConstruction.EntryPartList_Class.CurrencyLink).instance();
        final SelectBuilder selManAm = new SelectBuilder().clazz(CIConstruction.EntryPartList_Class)
                        .attribute(CIConstruction.EntryPartList_Class.ManualAmount);
        final SelectBuilder selManPer = new SelectBuilder().clazz(CIConstruction.EntryPartList_Class)
                        .attribute(CIConstruction.EntryPartList_Class.ManualPercent);
        final SelectBuilder selTotal = new SelectBuilder().clazz(CIConstruction.EntryPartList_Class)
                        .attribute(CIConstruction.EntryPartList_Class.Total);
        print.addSelect(selEffOA, selEffPers, selEffTools, selEffUoM, selCur, selManAm, selManPer, selTotal);
        print.addAttribute(CIProducts.ProductAbstract.Name,
                        CIProducts.ProductAbstract.Description,
                        CIProducts.ProductAbstract.Dimension,
                        CISales.ProductAbstract.TaxCategory);
        if (print.execute()) {
            final String name = print.<String> getAttribute(CIProducts.ProductAbstract.Name);
            final String desc = print.<String> getAttribute(CIProducts.ProductAbstract.Description);
            final Long dim = print.<Long> getAttribute(CIProducts.ProductAbstract.Dimension);
            final Long tax = print.<Long> getAttribute(CISales.ProductAbstract.TaxCategory);
            final BigDecimal effOA = print.<BigDecimal>getSelect(selEffOA);
            final BigDecimal effPers = print.<BigDecimal>getSelect(selEffPers);
            final BigDecimal effTools = print.<BigDecimal>getSelect(selEffTools);
            final BigDecimal manAm = print.<BigDecimal>getSelect(selManAm);
            final BigDecimal manPer = print.<BigDecimal>getSelect(selManPer);
            final BigDecimal total = print.<BigDecimal>getSelect(selTotal);

            final Object uom = print.getSelect(selEffUoM);
            final Instance currInst = print.<Instance> getSelect(selCur);

            final Instance newProdInst;
            if (_createType != null) {
                final Insert insert = new Insert(_createType);
                insert.add(CIProducts.ProductAbstract.Name, name);
                insert.add(CIProducts.ProductAbstract.Description, desc);
                insert.add(CIProducts.ProductAbstract.Dimension, dim);
                insert.add(CISales.ProductAbstract.TaxCategory, tax);
                insert.add(CIProducts.ProductAbstract.Active, true);
                insert.execute();
                newProdInst = insert.getInstance();
            } else {
                newProdInst = _targetInstance;
            }

            final Insert relInsert = new Insert(CIProducts.Product2Class);
            relInsert.add(CIProducts.Product2Class.Product, newProdInst.getId());
            relInsert.add(CIProducts.Product2Class.ClassTypeId, CIConstruction.EntryPartList_Class.getType().getId());
            relInsert.execute();

            // create the BOM relations
            final QueryBuilder bomQueryBldr = new QueryBuilder(CIConstruction.EntryBOM);
            bomQueryBldr.addWhereAttrEqValue(CIConstruction.EntryBOM.FromLink, _origInst);
            final MultiPrintQuery multi = bomQueryBldr.getPrint();
            final SelectBuilder selToProd = new SelectBuilder().linkto(CIConstruction.EntryBOM.ToLink).instance();
            multi.addSelect(selToProd);
            multi.addAttribute(CIConstruction.EntryBOM.Quantity, CIConstruction.EntryBOM.UoM,
                            CIConstruction.EntryBOM.Factor, CIConstruction.EntryBOM.UnitPrice,
                            CIConstruction.EntryBOM.Price, CIConstruction.EntryBOM.Remark);
            multi.execute();
            final List<Calculator> calculators = new ArrayList<>();
            while (multi.next()) {
                final Instance prodsInst = multi.<Instance> getSelect(selToProd);
                final Long uoM = multi.<Long> getAttribute(CIConstruction.EntryBOM.UoM);
                final BigDecimal quantity = multi.<BigDecimal> getAttribute(CIConstruction.EntryBOM.Quantity);
                final BigDecimal factor = multi.<BigDecimal> getAttribute(CIConstruction.EntryBOM.Factor);
                final String remark = multi.<String> getAttribute(CIConstruction.EntryBOM.Remark);
                BigDecimal unitPrice = BigDecimal.ZERO;
                BigDecimal price = BigDecimal.ZERO;
                if (_copyPrice) {
                    unitPrice = multi.<BigDecimal> getAttribute(CIConstruction.EntryBOM.UnitPrice);
                    price = multi.<BigDecimal> getAttribute(CIConstruction.EntryBOM.Price);
                } else if (_evalPrice) {
                    final DecimalFormat formatter = NumberFormatter.get().getFormatter();
                    final BomCalculator calc = new EntryBOM().getCalculator(_parameter,
                                    EntryBOM.getPositionType(_parameter, prodsInst),
                                    null,
                                    prodsInst.getOid(),
                                    formatter.format(quantity),
                                    null,
                                    factor == null ? null : formatter.format(factor),
                                    effOA == null ? null : formatter.format(effOA),
                                    effPers == null ? null : formatter.format(effPers),
                                    effTools == null ? null : formatter.format(effTools),
                                    null, uoM.toString(), null, true);
                    unitPrice = calc.getNetUnitPrice();
                    price = calc.getNetPrice();
                    calculators.add(calc);
                }
                final Insert bomInsert = new Insert(CIConstruction.EntryBOM);
                bomInsert.add(CIConstruction.EntryBOM.FromLink, newProdInst);
                bomInsert.add(CIConstruction.EntryBOM.ToLink, prodsInst);
                bomInsert.add(CIConstruction.EntryBOM.Quantity, quantity);
                bomInsert.add(CIConstruction.EntryBOM.UoM, uoM);
                bomInsert.add(CIConstruction.EntryBOM.UnitPrice, unitPrice);
                bomInsert.add(CIConstruction.EntryBOM.Price, price);
                bomInsert.add(CIConstruction.EntryBOM.Factor, factor);
                bomInsert.add(CIConstruction.EntryBOM.Remark, remark);
                bomInsert.execute();
            }

            final Update classInsert;
            final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.EntryPartList_Class);
            queryBldr.addWhereAttrEqValue(CIConstruction.EntryPartList_Class.ProductLink, newProdInst);
            final InstanceQuery query = queryBldr.getQuery();
            query.execute();
            if (query.next()) {
                classInsert = new Update(query.getCurrentValue());
            } else {
                classInsert = new Insert(CIConstruction.EntryPartList_Class);
            }

            // insert the classification
            classInsert.add(CIConstruction.EntryPartList_Class.ProductLink, newProdInst);
            classInsert.add(CIConstruction.EntryPartList_Class.EfficiencyOverAll, effOA);
            classInsert.add(CIConstruction.EntryPartList_Class.EfficiencyTools, effTools);
            classInsert.add(CIConstruction.EntryPartList_Class.EfficiencyPersonal, effPers);
            classInsert.add(CIConstruction.EntryPartList_Class.EfficiencyUoM, uom);
            classInsert.add(CIConstruction.EntryPartList_Class.CurrencyLink, currInst);
            classInsert.add(CIConstruction.EntryPartList_Class.ManualPercent, manPer);

            if (calculators.isEmpty()) {
                classInsert.add(CIConstruction.EntryPartList_Class.ManualAmount, manAm);
                classInsert.add(CIConstruction.EntryPartList_Class.Total, total);
            } else {
                final Parameter parameter = ParameterUtil.clone(_parameter);
                if (manPer != null) {
                    ParameterUtil.setParameterValues(parameter, CIFormConstruction.Construction_EntryPartListForm.manualPercent.name,
                                NumberFormatter.get().getFormatter().format(manPer));
                    classInsert.add(CIConstruction.EntryPartList_Class.ManualAmount,
                                new EntryBOM().getManualPercentAmount(parameter, calculators, false));
                }
                classInsert.add(CIConstruction.EntryPartList_Class.Total,
                                new EntryBOM().getNetTotal(parameter, calculators));
            }
            classInsert.execute();

            final Map<Instance, Instance> orig2copy;
            if (Context.getThreadContext().containsRequestAttribute(EntryPartList.REQKEY4COPIED)) {
                orig2copy = (Map<Instance, Instance>) Context.getThreadContext().getRequestAttribute(
                                EntryPartList.REQKEY4COPIED);
            } else {
                orig2copy = new HashMap<>();
                Context.getThreadContext().setRequestAttribute(REQKEY4COPIED, orig2copy);
            }
            orig2copy.put(_origInst, newProdInst);

            ret = new Object[] { newProdInst, total };
        }
        return ret;
    }

    /**
     * Method to create a entry part from a product part.
     *
     * @param _parameter as passed from eFaps API.
     * @return new Return
     * @throws EFapsException on error.
     */
    public Return createEntryFromTemplate(final Parameter _parameter)
        throws EFapsException
    {
        final Instance treeViewInst = Instance.get(_parameter.getParameterValue("selectedRow"));
        final PrintQuery tVprint = new PrintQuery(treeViewInst);
        final SelectBuilder selProd = new SelectBuilder().linkto(CIProducts.TreeViewProduct.ProductLink).oid();
        tVprint.addSelect(selProd);
        tVprint.execute();

        final Instance tmplInst = Instance.get((String) tVprint.getSelect(selProd));
        createEntryFromTemplate(_parameter, tmplInst, _parameter.getInstance());
        new EntrySheet().updateTotals(_parameter, _parameter.getInstance());
        return new Return();
    }


    /**
     * Creates the entry from template.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _tmplInst the tmpl inst
     * @param _entrySheetInst the entry sheet inst
     * @return the instance[]
     * @throws EFapsException on error
     */
    public Instance[] createEntryFromTemplate(final Parameter _parameter,
                                              final Instance _tmplInst,
                                              final Instance _entrySheetInst)
        throws EFapsException
    {
        final Instance[] ret  = new Instance[2];
        final PrintQuery printEntry = new PrintQuery(_tmplInst);
        printEntry.addAttribute(CIProducts.ProductAbstract.Dimension);
        printEntry.execute();
        final Dimension dim = Dimension.get(printEntry.<Long>getAttribute(CIProducts.ProductAbstract.Dimension));
        final Object[] obj = copy(_parameter, _tmplInst, CIConstruction.EntryPartList.getType(), null, false, true);
        if (obj != null && ((Instance) obj[0]).isValid()) {

            final Instance currBaseInst = Currency.getBaseCurrency();
            final Instance newProdInst = (Instance) obj[0];
            ret[0] = newProdInst;

            final PrintQuery print = new PrintQuery(newProdInst);
            print.addAttribute(CIProducts.ProductAbstract.Dimension, CIProducts.ProductAbstract.Description);
            print.execute();
            // create the position for the related Document
            final Insert posInsert = new Insert(CIConstruction.EntrySheetPosition);
            posInsert.add(CIConstruction.EntrySheetPosition.EntrySheetLink, _entrySheetInst);
            posInsert.add(CIConstruction.EntrySheetPosition.PositionNumber, 1);
            posInsert.add(CIConstruction.EntrySheetPosition.Product, ((Instance) obj[0]).getId());
            posInsert.add(CIConstruction.EntrySheetPosition.ProductDesc,
                            print.<String>getAttribute(CIProducts.ProductAbstract.Description));
            posInsert.add(CIConstruction.EntrySheetPosition.Quantity, BigDecimal.ONE);
            posInsert.add(CIConstruction.EntrySheetPosition.UoM, dim.getBaseUoM());
            posInsert.add(CIConstruction.EntrySheetPosition.CrossUnitPrice, obj[1]);
            posInsert.add(CIConstruction.EntrySheetPosition.NetUnitPrice, obj[1]);
            posInsert.add(CIConstruction.EntrySheetPosition.CrossPrice, obj[1]);
            posInsert.add(CIConstruction.EntrySheetPosition.NetPrice, obj[1]);
            posInsert.add(CIConstruction.EntrySheetPosition.Tax, 0);
            posInsert.add(CIConstruction.EntrySheetPosition.Discount, BigDecimal.ZERO);
            posInsert.add(CIConstruction.EntrySheetPosition.DiscountNetUnitPrice, obj[1]);
            posInsert.add(CIConstruction.EntrySheetPosition.CurrencyId, currBaseInst);
            posInsert.add(CIConstruction.EntrySheetPosition.Rate, new Object[] { BigDecimal.ONE, BigDecimal.ONE });
            posInsert.add(CIConstruction.EntrySheetPosition.RateCurrencyId, currBaseInst);
            posInsert.add(CIConstruction.EntrySheetPosition.RateNetUnitPrice, obj[1]);
            posInsert.add(CIConstruction.EntrySheetPosition.RateCrossUnitPrice, obj[1]);
            posInsert.add(CIConstruction.EntrySheetPosition.RateDiscountNetUnitPrice, obj[1]);
            posInsert.add(CIConstruction.EntrySheetPosition.RateNetPrice, obj[1]);
            posInsert.add(CIConstruction.EntrySheetPosition.RateCrossPrice, obj[1]);
            posInsert.execute();

            ret[1] = posInsert.getInstance();

            final Insert p2pInsert = new Insert(CIConstruction.TemplatePartList2EntryPartList);
            p2pInsert.add(CIConstruction.TemplatePartList2EntryPartList.FromLink, _tmplInst);
            p2pInsert.add(CIConstruction.TemplatePartList2EntryPartList.ToLink, newProdInst);
            p2pInsert.execute();
        }
        return ret;
    }


    /**
     * Method to create a product part from a entry part.
     *
     * @param _parameter as passed from eFaps API.
     * @return new Return.
     * @throws EFapsException on error.
     */
    public Return createTemplateFromEntry(final Parameter _parameter)
        throws EFapsException
    {
        final Instance entryInst = _parameter.getInstance();

        final Object[] obj = copy(_parameter, entryInst, CIConstruction.TemplatePartList.getType(), null, false, false);
        if (obj != null && ((Instance) obj[0]).isValid()) {
            final Instance newProdInst = (Instance) obj[0];

            final Insert p2pInsert = new Insert(CIConstruction.TemplatePartList2EntryPartList);
            p2pInsert.add(CIConstruction.TemplatePartList2EntryPartList.FromLink, newProdInst);
            p2pInsert.add(CIConstruction.TemplatePartList2EntryPartList.ToLink, entryInst);
            p2pInsert.execute();

            final PrintQuery print = new PrintQuery(entryInst);
            print.addAttribute(CIProducts.ProductAbstract.Name);
            print.execute();
            final Instance treeViewInst = Instance.get(_parameter.getParameterValue("selectedRow"));

            final Insert treeViewInsert = new Insert(CIProducts.TreeViewProduct);
            treeViewInsert.add(CIProducts.TreeViewProduct.ParentLink, treeViewInst.getId());
            treeViewInsert.add(CIProducts.TreeViewProduct.Label, print.<String>getAttribute(
                            CIProducts.ProductAbstract.Name));
            treeViewInsert.add(CIProducts.TreeViewProduct.ProductLink, newProdInst);
            treeViewInsert.execute();
        }
        return new Return();
    }

    /**
     * Method to validate before create a product in a part list.
     *
     * @param _parameter as passed from eFaps API.
     * @return on error.
     * @throws EFapsException on error
     */
    public Return validateCreateTemplate(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final StringBuilder html = new StringBuilder();
        if (_parameter.getInstance() != null && _parameter.getInstance().isValid()) {
            if (validateMultipleSelection(_parameter)) {
                html.append(DBProperties.getProperty(EntryPartList.class.getName() + ".Warning.multipleSelects"));
            } else {
                if (!validateElementSelected(_parameter)) {
                    html.append(DBProperties.getProperty(EntryPartList.class.getName() + ".Warning.elementSelected"));
                } else {
                    final Instance instEntry = _parameter.getInstance();
                    final QueryBuilder querBldr = new QueryBuilder(CIConstruction.TemplatePartList2EntryPartList);
                    querBldr.addWhereAttrEqValue(CIConstruction.TemplatePartList2EntryPartList.ToLink, instEntry);
                    final InstanceQuery instQuery = querBldr.getQuery();
                    instQuery.execute();
                    if (instQuery.next()) {
                        html.append(DBProperties.getProperty(EntryPartList.class.getName()
                                        + ".Warning.templateExisting"));
                    } else {
                        ret.put(ReturnValues.TRUE, true);
                    }
                }
            }
        }

        final String name = _parameter.getParameterValue(CIFormConstruction.Construction_TemplatePartListForm.name.name);
        if (name != null && !name.isEmpty()) {
            final QueryBuilder querBldr = new QueryBuilder(CIConstruction.TemplatePartList);
            querBldr.addWhereAttrEqValue(CIConstruction.TemplatePartList.Name, name);
            if (!querBldr.getQuery().execute().isEmpty()) {
                html.append(DBProperties.getProperty(EntryPartList.class.getName()
                                + ".Warning.templateNameExisting"));
            }
        }
        if (html.length() > 0) {
            ret.put(ReturnValues.SNIPLETT, html.toString());
        }
        return ret;
    }

    /**
     * Method to validate the creation of a entry part from a product entry.
     *
     * @param _parameter as passed from eFaps API.
     * @return Return with a String if there are errors.
     */
    public Return validateCreateFromTemplate(final Parameter _parameter)
    {
        final Return ret = new Return();
        final StringBuilder html = new StringBuilder();
        if (validateMultipleSelection(_parameter)) {
            html.append(DBProperties.getProperty(EntryPartList.class.getName() +  ".Warning.multipleSelects"));
            ret.put(ReturnValues.SNIPLETT, html.toString());
        } else {
            if (validateElementSelected(_parameter)) {
                html.append(DBProperties.getProperty(EntryPartList.class.getName() +  ".Warning.elementSelected2"));
                ret.put(ReturnValues.SNIPLETT, html.toString());
            } else {
                ret.put(ReturnValues.TRUE, true);
            }
        }
        return ret;
    }

    /**
     * Method to validate if it selected more than one row.
     *
     * @param _parameter as passed from eFaps API.
     * @return on error.
     */
    private boolean validateMultipleSelection(final Parameter _parameter)
    {
        boolean valid = false;
        final Map<?, ?> others = (HashMap<?, ?>) _parameter.get(ParameterValues.OTHERS);
        final String[] childOids = (String[]) others.get("selectedRow");
        if (childOids.length > 1) {
            valid = true;
        }
        return valid;
    }

    /**
     * Method to validate the type of selection.
     *
     * @param _parameter as passed from eFaps API.
     * @return on error.
     */
    private boolean validateElementSelected(final Parameter _parameter)
    {
        boolean valid = false;
        final Instance childOid = Instance.get(_parameter.getParameterValue("selectedRow"));
        if (childOid.isValid()) {
            if (CIProducts.TreeViewNode.uuid.equals(childOid.getType().getUUID())
                            || CIProducts.TreeViewRoot.uuid.equals(childOid.getType().getUUID())) {
                valid = true;
            }
        }
        return valid;
    }

    /**
     * @param _parameter as passed from eFaps API.
     * @return empty Return
     */
    public Return getEfficiencyFieldValue(final Parameter _parameter)
    {
        //TODO
        return new Return();
    }
}
