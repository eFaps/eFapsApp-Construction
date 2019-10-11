/*
 * Copyright 2007 - 2016 Jan Moxter
 *
 * All Rights Reserved.
 * This program contains proprietary and trade secret information of
 * Jan Moxter Copyright notice is precautionary only and does not
 * evidence any actual or intended publication of such program.
 *
 */

package org.efaps.esjp.construction.reports;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.collections4.ComparatorUtils;
import org.efaps.admin.datamodel.Dimension;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIConstruction;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CIProjects;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.AbstractCommon;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.construction.EntryBOM;
import org.efaps.esjp.construction.PositionType;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.util.EFapsException;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.PercentageColumnBuilder;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;


/**
 * TODO comment!
 *
 * @author Jan Moxter
 */
@EFapsUUID("24cd878c-6863-4462-b59c-0966379a64c5")
@EFapsApplication("eFapsApp-Construction")
public abstract class CostEstimateMaterialRankingReport_Base
    extends AbstractCommon
{

    /**
     * Gets the report.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the report
     * @throws EFapsException on error
     */
    public Return getReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final AbstractDynamicReport dyRp = new MaterialRankingReport();
        final String html = dyRp.getHtml(_parameter);
        ret.put(ReturnValues.SNIPLETT, html);
        return ret;
    }

    /**
     * Export report.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return exportReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String mime = (String) props.get("Mime");
        final AbstractDynamicReport dyRp = new MaterialRankingReport();
        dyRp.setFileName(getDBProperty("FileName"));
        File file = null;
        if ("xls".equalsIgnoreCase(mime)) {
            file = dyRp.getExcel(_parameter);
        } else if ("pdf".equalsIgnoreCase(mime)) {
            file = dyRp.getPDF(_parameter);
        }
        ret.put(ReturnValues.VALUES, file);
        ret.put(ReturnValues.TRUE, true);

        return ret;
    }

    /**
     * The Class MaterialRankingReport.
     *
     */
    public class MaterialRankingReport
        extends AbstractDynamicReport
    {

        @SuppressWarnings("unchecked")
        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final Instance instance = _parameter.getInstance();
            final Map<Integer, String> statusMap = analyseProperty(_parameter, "Status");
            final Map<Instance, ProductBean> values = new HashMap<Instance, ProductBean>();

            final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.CostEstimatePositionAbstract);
            final QueryBuilder pcQueryBldr = new QueryBuilder(CIConstruction.CostEstimatePercentageCost);

            if (instance.isValid() && instance.getType().isKindOf(CIProjects.ProjectService.getType())) {
                final QueryBuilder attrQueryBldr = new QueryBuilder(CIConstruction.ProjectService2CostEstimate);

                final List<Long> lstStatIds = new ArrayList<Long>();
                if (!statusMap.isEmpty()) {
                    for (final Entry<Integer, String> entry : statusMap.entrySet()) {
                        final Long statId = Status.find(CIConstruction.CostEstimateStatus, entry.getValue()).getId();
                        lstStatIds.add(statId);
                    }
                    final QueryBuilder statusAttrQueryBldr = new QueryBuilder(CIConstruction.CostEstimateAbstract);
                    statusAttrQueryBldr.addWhereAttrEqValue(CIConstruction.CostEstimateAbstract.StatusAbstract,
                                    lstStatIds.toArray());
                    final AttributeQuery statusAttrQuery = statusAttrQueryBldr
                                    .getAttributeQuery(CIConstruction.CostEstimateAbstract.ID);

                    attrQueryBldr.addWhereAttrInQuery(CIConstruction.ProjectService2CostEstimate.ToLink, statusAttrQuery);
                }

                attrQueryBldr.addWhereAttrEqValue(CIConstruction.ProjectService2CostEstimate.FromLink, instance);
                final AttributeQuery attrQuery = attrQueryBldr
                                .getAttributeQuery(CIConstruction.ProjectService2CostEstimate.ToLink);

                queryBldr.addWhereAttrInQuery(CIConstruction.CostEstimatePositionAbstract.CostEstimateAbstractLink, attrQuery);
                pcQueryBldr.addWhereAttrInQuery(CIConstruction.CostEstimatePercentageCost.CostEstimateLink, attrQuery);
            } else {
                queryBldr.addWhereAttrEqValue(CIConstruction.CostEstimatePositionAbstract.CostEstimateAbstractLink, instance);
                pcQueryBldr.addWhereAttrEqValue(CIConstruction.CostEstimatePercentageCost.CostEstimateLink, instance);
            }

            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selEntryIns = new SelectBuilder().linkto(CIConstruction.CostEstimatePositionAbstract.Product)
                            .instance();
            multi.addAttribute(CIConstruction.CostEstimatePositionAbstract.Quantity,
                            CIConstruction.CostEstimatePositionAbstract.RateCurrencyId);
            multi.addSelect(selEntryIns);
            multi.execute();

            while (multi.next()) {
                final BigDecimal entryQuantity = multi
                                .<BigDecimal>getAttribute(CIConstruction.CostEstimatePositionAbstract.Quantity);
                final Instance entryInstance = multi.<Instance>getSelect(selEntryIns);

                final QueryBuilder queryBldr2 = new QueryBuilder(CIConstruction.EntryBOM);
                queryBldr2.addWhereAttrEqValue(CIConstruction.EntryBOM.FromLink, entryInstance.getId());
                queryBldr2.addWhereAttrInQuery(CIConstruction.EntryBOM.ToLink,
                                new EntryBOM().getProductAttrQuery(_parameter, PositionType.MATERIAL));
                final MultiPrintQuery multi2 = queryBldr2.getPrint();
                final SelectBuilder selProd = new SelectBuilder().linkto(CIConstruction.EntryBOM.ToLink);
                final SelectBuilder selProdInst = new SelectBuilder(selProd).instance();
                final SelectBuilder selProdDesc = new SelectBuilder(selProd)
                                .attribute(CIProducts.ProductAbstract.Description);
                final SelectBuilder selProdName = new SelectBuilder(selProd).attribute(CIProducts.ProductAbstract.Name);

                multi2.addAttribute(CIConstruction.EntryBOM.Price, CIConstruction.EntryBOM.Quantity, CIConstruction.EntryBOM.UoM);
                multi2.addSelect(selProdInst, selProdDesc, selProdName);
                multi2.execute();

                while (multi2.next()) {
                    final Instance prodInst = multi2.<Instance>getSelect(selProdInst);
                    ProductBean prod;
                    if (!values.containsKey(prodInst)) {
                        prod = new ProductBean()
                                        .setName(multi2.<String>getSelect(selProdName))
                                        .setDescription(multi2.<String>getSelect(selProdDesc))
                                        .setCurrency(CurrencyInst.get(multi.<Long>getAttribute(
                                                        CISales.PositionSumAbstract.RateCurrencyId)).getSymbol())
                                        .setUom(Dimension.getUoM
                                                        (multi2.<Long>getAttribute(CISales.PositionSumAbstract.UoM))
                                                        .getName());
                        values.put(prodInst, prod);
                    } else {
                        prod = values.get(prodInst);
                    }
                    final BigDecimal price = multi2.<BigDecimal>getAttribute(CIConstruction.EntryBOM.Price);
                    final BigDecimal quantity = multi2.<BigDecimal>getAttribute(CIConstruction.EntryBOM.Quantity);

                    prod.addPrice(price.multiply(entryQuantity))
                        .addQuantity(quantity.multiply(entryQuantity));
                }
            }

            // sorting
            final List<ProductBean> tmpValues = new ArrayList<ProductBean>();
            tmpValues.addAll(values.values());
            final List<Comparator<ProductBean>> comparators = new ArrayList<Comparator<ProductBean>>();
            comparators.add((_prod0,
             _prod1) -> _prod1.getPrice().compareTo(_prod0.getPrice()));

            Collections.sort(tmpValues, ComparatorUtils.chainedComparator(comparators));
            final JRBeanCollectionDataSource ret = new JRBeanCollectionDataSource(tmpValues);
            return ret;
        }

        @Override
        protected void addColumnDefinition(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {

            final TextColumnBuilder<String> nameColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateMaterialRankingReport.class.getName() + ".Column.name"),
                            "name", DynamicReports.type.stringType());
            final TextColumnBuilder<String> descColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateMaterialRankingReport.class.getName() + ".Column.description"),
                            "description", DynamicReports.type.stringType()).setWidth(500);
            final TextColumnBuilder<String> uomColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateMaterialRankingReport.class.getName() + ".Column.uom"),
                            "uom", DynamicReports.type.stringType());
            final TextColumnBuilder<BigDecimal> unitPriceColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateMaterialRankingReport.class.getName() + ".Column.unitPrice"),
                            "unitPrice", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> quantityColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateMaterialRankingReport.class.getName() + ".Column.quantity"),
                            "quantity", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> priceColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateMaterialRankingReport.class.getName() + ".Column.price"),
                            "price", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<String> currencyColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateMaterialRankingReport.class.getName() + ".Column.currency"),
                            "currency", DynamicReports.type.stringType());
            final PercentageColumnBuilder percentColumn = DynamicReports.col.percentageColumn(DBProperties
                            .getProperty(CostEstimateMaterialRankingReport.class.getName() + ".Column.percent"),
                            priceColumn);
            _builder.addColumn(nameColumn, descColumn, uomColumn, unitPriceColumn, quantityColumn, priceColumn,
                            percentColumn, currencyColumn);

            _builder.addSubtotalAtColumnFooter(DynamicReports.sbt.sum(priceColumn));
            _builder.addSubtotalAtColumnFooter(DynamicReports.sbt.first(currencyColumn));
        }
    }

    /**
     * Inherit class to store the products for the report.
     */
    public static class ProductBean
    {

        /** The name. */
        private String name;

        /** The description. */
        private String description;

        /** The currency. */
        private String currency;

        /** The uom. */
        private String uom;

        /** The price. */
        private BigDecimal price = BigDecimal.ZERO;

        /** The quantity. */
        private BigDecimal quantity = BigDecimal.ZERO;

        /**
         * Adds the price.
         *
         * @param toAdd the to add
         * @return the big decimal
         */
        protected ProductBean addPrice(final BigDecimal toAdd)
        {
            price = price.add(toAdd);
            return this;
        }

        /**
         * Adds the quantity.
         *
         * @param toAdd the to add
         * @return the big decimal
         */
        protected ProductBean addQuantity(final BigDecimal toAdd)
        {
            quantity = quantity.add(toAdd);
            return this;
        }

        /**
         * Gets the name.
         *
         * @return the name
         */
        public String getName()
        {
            return name;
        }

        /**
         * Sets the name.
         *
         * @param _name the name
         * @return the product bean
         */
        public ProductBean setName(final String _name)
        {
            name = _name;
            return this;
        }

        /**
         * Gets the description.
         *
         * @return the description
         */
        public String getDescription()
        {
            return description;
        }

        /**
         * Sets the description.
         *
         * @param _description the description
         * @return the product bean
         */
        public ProductBean setDescription(final String _description)
        {
            description = _description;
            return this;
        }

        /**
         * Gets the uom.
         *
         * @return the uom
         */
        public String getUom()
        {
            return uom;
        }

        /**
         * Sets the uom.
         *
         * @param _uom the uom
         * @return the product bean
         */
        public ProductBean setUom(final String _uom)
        {
            uom = _uom;
            return this;
        }

        /**
         * Gets the price.
         *
         * @return the price
         */
        public BigDecimal getPrice()
        {
            return price;
        }

        /**
         * Sets the price.
         *
         * @param _price the price
         * @return the product bean
         */
        public ProductBean setPrice(final BigDecimal _price)
        {
            price = _price;
            return this;
        }

        /**
         * Gets the quantity.
         *
         * @return the quantity
         */
        public BigDecimal getQuantity()
        {
            return quantity;
        }

        /**
         * Sets the quantity.
         *
         * @param _quantity the quantity
         * @return the product bean
         */
        public ProductBean setQuantity(final BigDecimal _quantity)
        {
            quantity = _quantity;
            return this;
        }

        /**
         * Gets the unit price.
         *
         * @return the unit price
         */
        public BigDecimal getUnitPrice()
        {
            BigDecimal ret = null;
            if (getQuantity().compareTo(BigDecimal.ZERO) != 0) {
                ret = getPrice().divide(getQuantity(), 2, BigDecimal.ROUND_HALF_UP);
            }
            return ret;
        }

        /**
         * Gets the currency.
         *
         * @return the currency
         */
        public String getCurrency()
        {
            return currency;
        }

        /**
         * Sets the currency.
         *
         * @param _currency the currency
         * @return the product bean
         */
        public ProductBean setCurrency(final String _currency)
        {
            currency = _currency;
            return this;
        }
    }
}
