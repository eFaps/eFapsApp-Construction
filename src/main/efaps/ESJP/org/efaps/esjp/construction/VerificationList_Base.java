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
import java.util.List;
import java.util.Map;

import org.efaps.admin.datamodel.Dimension;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.ci.CIType;
import org.efaps.db.AttributeQuery;
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
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.esjp.erp.Revision;
import org.efaps.esjp.projects.document.Naming;
import org.efaps.esjp.sales.Calculator;
import org.efaps.esjp.sales.document.AbstractDocumentSum;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

@EFapsUUID("cf6bfe20-4559-40a3-8337-fc4265856437")
@EFapsApplication("eFapsApp-Construction")
public abstract class VerificationList_Base
    extends AbstractDocumentSum
{

    private static final String projectKey = VerificationList.class.getName() + ".ProjectKey";


    public Return create(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final CreatedDoc createdDoc = createDoc(_parameter);
        createPositions(_parameter, createdDoc);
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
        Instance projectInst = Instance.get(_parameter
                        .getParameterValue(CIFormConstruction.Construction_VerificationListForm.project.name));
        if (!projectInst.isValid() && _createdDoc != null) {
            projectInst = (Instance) _createdDoc.getValue(VerificationList_Base.projectKey);
        }
        if (projectInst.isValid() && _createdDoc != null) {
            final PrintQuery print = new PrintQuery(projectInst);
            print.addAttribute(CIProjects.ProjectService.Contact);
            print.execute();
            final Long contactid = print.<Long>getAttribute(CIProjects.ProjectService.Contact);
            _insert.add(CIConstruction.VerificationList.Contact, contactid);
        }
    }

    protected void insertRelation(final Parameter _parameter,
                                  final CreatedDoc _createdDoc)
        throws EFapsException
    {
        Instance projectInst = Instance.get(_parameter
                        .getParameterValue(CIFormConstruction.Construction_VerificationListForm.project.name));
        if (!projectInst.isValid() && _createdDoc != null) {
            projectInst = (Instance) _createdDoc.getValue(VerificationList_Base.projectKey);
        }
        if (projectInst.isValid()) {
            final Insert insert = new Insert(CIConstruction.ProjectService2VerificationList);
            insert.add(CIConstruction.ProjectService2VerificationList.FromLink, projectInst);
            insert.add(CIConstruction.ProjectService2VerificationList.ToLink, _createdDoc.getInstance());
            insert.execute();
        }
    }

    public Return edit(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final EditedDoc editedDoc = editDoc(_parameter);
        updatePositions(_parameter, editedDoc);
        return ret;
    }

    @Override
    public String getTypeName4SysConf(final Parameter _parameter)
        throws EFapsException
    {
        return CIConstruction.VerificationList.getType().getName();
    }

    @Override
    protected String getDocName4Create(final Parameter _parameter)
        throws EFapsException
    {
        String ret = null;
        final Instance projectInst = Instance.get(_parameter
                        .getParameterValue(CIFormConstruction.Construction_VerificationListForm.project.name));
        if (projectInst.isValid()) {
            ret = Naming.getName(projectInst, CIConstruction.VerificationList.getType(), true);
        }
        return ret == null ? super.getDocName4Create(_parameter) : ret;
    }


    public Return revise(final Parameter _parameter)
        throws EFapsException
    {
        final String[] oids = (String[]) _parameter.get(ParameterValues.OTHERS);
        final Instance qInst = Instance.get(oids[0]);
        _parameter.put(ParameterValues.INSTANCE, qInst);
        final Revision rev = new Revision()
        {

        };
        return rev.revise(_parameter);
    }

    public Return create4Project(final Parameter _parameter)
        throws EFapsException
    {
        create4Project(_parameter, _parameter.getInstance());
        return new Return();
    }

    public void create4Project(final Parameter _parameter,
                               final Instance _projectInstance)
        throws EFapsException
    {

        final List<Calculator> calcs = new ArrayList<>();
        final Map<Instance, ProductInfo> values = new HashMap<>();

        final QueryBuilder statusAttrQueryBldr = new QueryBuilder(CIConstruction.CostEstimateRealization);
        statusAttrQueryBldr.addWhereAttrEqValue(CIConstruction.CostEstimateRealization.Status,
                        Status.find(CIConstruction.CostEstimateStatus.Closed));
        final AttributeQuery statusAttrQuery = statusAttrQueryBldr.getAttributeQuery(CIConstruction.CostEstimateRealization.ID);

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIConstruction.ProjectService2CostEstimate);
        attrQueryBldr.addWhereAttrEqValue(CIConstruction.ProjectService2CostEstimate.FromLink, _projectInstance);
        attrQueryBldr.addWhereAttrInQuery(CIConstruction.ProjectService2CostEstimate.ToLink, statusAttrQuery);
        final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CIConstruction.ProjectService2CostEstimate.ToLink);

        final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.CostEstimatePositionRealization);
        queryBldr.addWhereAttrInQuery(CIConstruction.CostEstimatePositionRealization.CostEstimateLink, attrQuery);
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selEplInst = SelectBuilder.get().linkto(CIConstruction.CostEstimatePositionRealization.Product)
                        .instance();
        multi.addSelect(selEplInst);
        multi.addAttribute(CIConstruction.CostEstimatePositionRealization.Quantity);
        multi.execute();

        while (multi.next()) {
            final Instance eplInst = multi.<Instance>getSelect(selEplInst);
            final BigDecimal eplQuantity = multi
                            .<BigDecimal>getAttribute(CIConstruction.CostEstimatePositionRealization.Quantity);
            final QueryBuilder bomQueryBldr = new QueryBuilder(CIConstruction.EntryBOM);
            bomQueryBldr.addWhereAttrEqValue(CIConstruction.EntryBOM.FromLink, eplInst);

            final MultiPrintQuery bomMulti = bomQueryBldr.getPrint();
            final SelectBuilder selProd = new SelectBuilder().linkto(CIConstruction.EntryBOM.ToLink);
            final SelectBuilder selProdInst = new SelectBuilder(selProd).instance();
            bomMulti.addSelect(selProdInst);
            bomMulti.addAttribute(CIConstruction.EntryBOM.Quantity, CIConstruction.EntryBOM.Price, CIConstruction.EntryBOM.UoM);
            bomMulti.execute();

            while (bomMulti.next()) {
                final Instance prodInst = bomMulti.<Instance>getSelect(selProdInst);
                ProductInfo prod;
                if (!values.containsKey(prodInst)) {
                    prod = getProductInfo(_parameter, prodInst);
                    values.put(prodInst, prod);
                } else {
                    prod = values.get(prodInst);
                }

                final BigDecimal quantity = bomMulti.<BigDecimal>getAttribute(CIConstruction.EntryBOM.Quantity);
                final BigDecimal totalQuantity = eplQuantity.multiply(quantity);

                final BigDecimal price = bomMulti.<BigDecimal>getAttribute(CIConstruction.EntryBOM.Price);
                final BigDecimal totalPrice = price.multiply(eplQuantity);

                prod.add(totalQuantity, totalPrice);
            }
        }
        int idx = 0;
        for (final ProductInfo product : values.values()) {
            final Calculator calc = getCalculator(_parameter, null, product.getInstance(), product.getQuantity(),
                            product.getUnitPrice(), BigDecimal.ZERO, false, idx);
            calcs.add(calc);
            idx++;
        }

        final PrintQuery print = new PrintQuery(_projectInstance);
        final SelectBuilder selRateCurrInst = SelectBuilder.get().linkto(CIProjects.ProjectAbstract.CurrencyLink)
                        .instance();
        print.addSelect(selRateCurrInst);
        print.execute();

        final Instance baseCurrInst = Currency.getBaseCurrency();
        final Instance rateCurrInst = print.<Instance>getSelect(selRateCurrInst);

        final Currency curr = new Currency();
        final RateInfo rateInfo = curr.evaluateRateInfo(_parameter, new DateTime(), rateCurrInst);

        final Object[] rateObj = rateInfo.getRateObject();
        final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                        RoundingMode.HALF_UP);

        final CreatedDoc createdDoc = new CreatedDoc();
        createdDoc.getValues().put(VerificationList_Base.projectKey, _projectInstance);

        final Insert insert = new Insert(CIConstruction.VerificationList);
        insert.add(CIConstruction.VerificationList.Name, Naming.getName(_projectInstance, CIConstruction.VerificationList.getType(), true));
        insert.add(CIConstruction.VerificationList.Date, new DateTime());

        final DecimalFormat frmt = NumberFormatter.get().getFrmt4Total(getType4SysConf(_parameter));
        final int scale = frmt.getMaximumFractionDigits();
        final BigDecimal rateCrossTotal = getCrossTotal(_parameter, calcs)
                        .setScale(scale, RoundingMode.HALF_UP);
        insert.add(CISales.DocumentSumAbstract.RateCrossTotal, rateCrossTotal);
        createdDoc.getValues().put(CISales.DocumentSumAbstract.RateCrossTotal.name, rateCrossTotal);

        final BigDecimal rateNetTotal = getNetTotal(_parameter, calcs).setScale(scale, RoundingMode.HALF_UP);
        insert.add(CISales.DocumentSumAbstract.RateNetTotal, rateNetTotal);
        createdDoc.getValues().put(CISales.DocumentSumAbstract.RateNetTotal.name, rateNetTotal);

        insert.add(CISales.DocumentSumAbstract.RateDiscountTotal, BigDecimal.ZERO);
        insert.add(CISales.DocumentSumAbstract.RateTaxes, getRateTaxes(_parameter, calcs, rateCurrInst));
        insert.add(CISales.DocumentSumAbstract.Taxes, getTaxes(_parameter, calcs, rate, baseCurrInst));

        final BigDecimal crossTotal = getCrossTotal(_parameter, calcs).divide(rate, RoundingMode.HALF_UP)
                        .setScale(scale, RoundingMode.HALF_UP);
        insert.add(CISales.DocumentSumAbstract.CrossTotal, crossTotal);
        createdDoc.getValues().put(CISales.DocumentSumAbstract.CrossTotal.name, crossTotal);

        final BigDecimal netTotal = getNetTotal(_parameter, calcs).divide(rate, RoundingMode.HALF_UP)
                        .setScale(scale, RoundingMode.HALF_UP);
        insert.add(CISales.DocumentSumAbstract.NetTotal, netTotal);
        createdDoc.getValues().put(CISales.DocumentSumAbstract.CrossTotal.name, netTotal);

        insert.add(CISales.DocumentSumAbstract.DiscountTotal, BigDecimal.ZERO);

        insert.add(CISales.DocumentSumAbstract.CurrencyId, baseCurrInst);
        insert.add(CISales.DocumentSumAbstract.Rate, rateObj);
        insert.add(CISales.DocumentSumAbstract.RateCurrencyId, rateCurrInst);

        createdDoc.getValues().put(CISales.DocumentSumAbstract.CurrencyId.name, baseCurrInst);
        createdDoc.getValues().put(CISales.DocumentSumAbstract.RateCurrencyId.name, rateCurrInst);
        createdDoc.getValues().put(CISales.DocumentSumAbstract.Rate.name, rateObj);

        insert.add(CIConstruction.VerificationList.Status, Status.find(CIConstruction.VerificationListStatus.Closed));
        add2DocCreate(_parameter, insert, createdDoc);
        insert.execute();
        createdDoc.setInstance(insert.getInstance());
        insertRelation(_parameter, createdDoc);

        final DecimalFormat unitFrmt = NumberFormatter.get().getFrmt4UnitPrice(getType4SysConf(_parameter));
        final int uScale = unitFrmt.getMaximumFractionDigits();

        idx = 0;
        for (final Calculator calc : calcs) {
            if (!calc.isEmpty()) {
                final Insert posIns = new Insert(CIConstruction.VerificationListPosition);
                posIns.add(CISales.PositionAbstract.PositionNumber, idx + 1);
                posIns.add(CISales.PositionAbstract.DocumentAbstractLink, createdDoc.getInstance().getId());
                final Instance prodInst = Instance.get(calc.getOid());
                posIns.add(CISales.PositionAbstract.Product, prodInst);

                final PrintQuery prodPrint = new PrintQuery(prodInst);
                prodPrint.addAttribute(CIProducts.ProductAbstract.Dimension, CIProducts.ProductAbstract.Description,
                                CIProducts.ProductAbstract.DefaultUoM);
                prodPrint.execute();
                final Long dimId = prodPrint.<Long>getAttribute(CIProducts.ProductAbstract.Dimension);
                final Long uomId = prodPrint.<Long>getAttribute(CIProducts.ProductAbstract.DefaultUoM);
                posIns.add(CISales.PositionAbstract.UoM, uomId == null ? Dimension.get(dimId).getBaseUoM().getId()
                                : uomId);
                posIns.add(CISales.PositionAbstract.ProductDesc,
                                prodPrint.<String>getAttribute(CIProducts.ProductAbstract.Description));

                posIns.add(CISales.PositionSumAbstract.Quantity, calc.getQuantity());
                posIns.add(CISales.PositionSumAbstract.CrossUnitPrice, calc.getCrossUnitPrice()
                                .divide(rate, RoundingMode.HALF_UP).setScale(uScale, RoundingMode.HALF_UP));
                posIns.add(CISales.PositionSumAbstract.NetUnitPrice, calc.getNetUnitPrice()
                                .divide(rate, RoundingMode.HALF_UP).setScale(uScale, RoundingMode.HALF_UP));
                posIns.add(CISales.PositionSumAbstract.CrossPrice, calc.getCrossPrice()
                                .divide(rate, RoundingMode.HALF_UP).setScale(scale, RoundingMode.HALF_UP));
                posIns.add(CISales.PositionSumAbstract.NetPrice, calc.getNetPrice()
                                .divide(rate, RoundingMode.HALF_UP).setScale(scale, RoundingMode.HALF_UP));
                posIns.add(CISales.PositionSumAbstract.Tax, calc.getTaxCatId());
                posIns.add(CISales.PositionSumAbstract.Discount, calc.getDiscount());
                posIns.add(CISales.PositionSumAbstract.DiscountNetUnitPrice, calc.getDiscountNetUnitPrice()
                                .divide(rate, RoundingMode.HALF_UP).setScale(uScale, RoundingMode.HALF_UP));
                posIns.add(CISales.PositionSumAbstract.CurrencyId, baseCurrInst);
                posIns.add(CISales.PositionSumAbstract.Rate, rateObj);
                posIns.add(CISales.PositionSumAbstract.RateCurrencyId, rateCurrInst);
                posIns.add(CISales.PositionSumAbstract.RateNetUnitPrice, calc.getNetUnitPrice()
                                .setScale(uScale, RoundingMode.HALF_UP));
                posIns.add(CISales.PositionSumAbstract.RateCrossUnitPrice, calc.getCrossUnitPrice()
                                .setScale(uScale, RoundingMode.HALF_UP));
                posIns.add(CISales.PositionSumAbstract.RateDiscountNetUnitPrice, calc.getDiscountNetUnitPrice()
                                .setScale(uScale, RoundingMode.HALF_UP));
                posIns.add(CISales.PositionSumAbstract.RateNetPrice,
                                calc.getNetPrice().setScale(scale, RoundingMode.HALF_UP));
                posIns.add(CISales.PositionSumAbstract.RateCrossPrice,
                                calc.getCrossPrice().setScale(scale, RoundingMode.HALF_UP));
                add2PositionInsert(_parameter, calc, posIns, idx);
                posIns.execute();
                createdDoc.addPosition(posIns.getInstance());
            }
            idx++;
        }

        // invalidate all previous VerificationLists
        final QueryBuilder invAttrQueryBldr = new QueryBuilder(CIConstruction.ProjectService2VerificationList);
        invAttrQueryBldr.addWhereAttrEqValue(CIConstruction.ProjectService2VerificationList.FromLink, _projectInstance);
        invAttrQueryBldr.addWhereAttrNotEqValue(CIConstruction.ProjectService2VerificationList.ToLink, createdDoc.getInstance());
        final AttributeQuery invAttrQuery = invAttrQueryBldr
                        .getAttributeQuery(CIConstruction.ProjectService2VerificationList.ToLink);

        final QueryBuilder invQueryBldr = new QueryBuilder(CIConstruction.VerificationList);
        invQueryBldr.addWhereAttrEqValue(CIConstruction.VerificationList.Status, Status.find(CIConstruction.VerificationListStatus.Closed));
        invQueryBldr.addWhereAttrInQuery(CIConstruction.VerificationList.ID, invAttrQuery);
        invQueryBldr.addOrderByAttributeAsc(CIConstruction.VerificationList.Created);

        Instance origDoc = null;
        final InstanceQuery invQuery = invQueryBldr.getQuery();
        invQuery.execute();
        while (invQuery.next()) {
            origDoc = invQuery.getCurrentValue();
            final Update update = new Update(origDoc);
            update.add(CIConstruction.VerificationList.Status, Status.find(CIConstruction.VerificationListStatus.Canceled));
            update.executeWithoutTrigger();
        }
        if (origDoc != null) {
            final Revision rev = new Revision();
            final Object revision = rev.getNextRevision(_parameter, origDoc, createdDoc.getInstance());

            final Update update = new Update(createdDoc.getInstance());
            update.add(CIConstruction.VerificationList.Revision, revision);
            update.execute();

            final Insert d2rinsert = new Insert(CIERP.Document2Revision);
            d2rinsert.add(CIERP.Document2Revision.FromLink, origDoc);
            d2rinsert.add(CIERP.Document2Revision.ToLink, createdDoc.getInstance());
            d2rinsert.execute();
        }
    }

    @Override
    public CIType getCIType()
        throws EFapsException
    {
        return CIConstruction.VerificationList;
    }

    protected ProductInfo getProductInfo(final Parameter _parameter,
                                         final Instance _prodInst)
    {
        return new ProductInfo(_prodInst);
    }

    /**
     *
     */
    public static class ProductInfo
    {

        private final Instance instance;

        private BigDecimal quantity = BigDecimal.ZERO;

        private BigDecimal totalPrice = BigDecimal.ZERO;

        /**
         * @param _prodInst product instance
         */
        public ProductInfo(final Instance _prodInst)
        {
            instance = _prodInst;
        }


        public void add(final BigDecimal _quantity,
                        final BigDecimal _newTotalPrice)
        {
            totalPrice = totalPrice.add(_newTotalPrice);
            quantity = quantity.add(_quantity);
        }

        /**
         * Getter method for the instance variable {@link #instance}.
         *
         * @return value of instance variable {@link #instance}
         */
        public Instance getInstance()
        {
            return instance;
        }

        /**
         * Getter method for the instance variable {@link #quantity}.
         *
         * @return value of instance variable {@link #quantity}
         */
        public BigDecimal getQuantity()
        {
            return quantity;
        }

        /**
         * Getter method for the instance variable {@link #quantity}.
         *
         * @return value of instance variable {@link #quantity}
         */
        public BigDecimal getUnitPrice()
        {
            BigDecimal ret = BigDecimal.ZERO;
            if (totalPrice.compareTo(BigDecimal.ZERO) != 0) {
                ret = totalPrice.setScale(8, RoundingMode.HALF_UP)
                                .divide(getQuantity(), RoundingMode.HALF_UP);
            }
            return ret;
        }
    }
}
