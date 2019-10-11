/*
 * Copyright 2007 - 2016 Jan Moxter
 *
 * All Rights Reserved.
 * This program contains proprietary and trade secret information of
 * Jan Moxter. Copyright notice is precautionary only and does not
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

import org.apache.commons.collections4.ComparatorUtils;
import org.efaps.admin.datamodel.Dimension;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIConstruction;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.FilteredReport;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.util.EFapsException;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.subtotal.AggregationSubtotalBuilder;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

/**
 * TODO comment!
 *
 * @author Jan Moxter
 */
@EFapsUUID("3c2d4e50-42bd-4cf1-a1c2-3d5be36ba44e")
@EFapsApplication("eFapsApp-Construction")
public abstract class CostEstimateEntryPartListReport_Base
    extends FilteredReport
{

    /**
     * The Enum PosType.
     *
     * @author The eFaps Team
     */
    public enum PosType
    {

        /** The both. */
        BOTH,

        /** The generalcost. */
        GENERALCOST,

        /** The partlist. */
        PARTLIST
    }


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
        final AbstractDynamicReport dyRp = new EntryPartListReport(this);
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
        final AbstractDynamicReport dyRp = new EntryPartListReport(this);
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
     * The Class EntryPartListReport.
     */
    public static class EntryPartListReport
        extends AbstractDynamicReport
    {

        /** The filtered report. */
        private final CostEstimateEntryPartListReport_Base filteredReport;

        /**
         * Instantiates a new entry part list report.
         *
         * @param _filteredReport the filtered report
         */
        public EntryPartListReport(final CostEstimateEntryPartListReport_Base _filteredReport) {
            filteredReport = _filteredReport;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final Instance instance = _parameter.getInstance();

            final Map<Instance, ProductBean> values = new HashMap<>();

            final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.CostEstimatePositionAbstract);
            queryBldr.addWhereAttrEqValue(CIConstruction.CostEstimatePositionAbstract.CostEstimateAbstractLink, instance);
            queryBldr.addOrderByAttributeDesc(CIConstruction.CostEstimatePositionAbstract.RateNetPrice);

            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.addAttribute(CIConstruction.CostEstimatePositionAbstract.RateNetUnitPrice,
                            CIConstruction.CostEstimatePositionAbstract.RateCurrencyId,
                            CIConstruction.CostEstimatePositionAbstract.Quantity, CIConstruction.CostEstimatePositionAbstract.RateNetPrice);
            final SelectBuilder selProd = new SelectBuilder().linkto(CIConstruction.CostEstimatePositionAbstract.Product);
            final SelectBuilder selProdInst = new SelectBuilder(selProd).instance();
            final SelectBuilder selProdName = new SelectBuilder(selProd)
                            .attribute(CIProducts.ProductAbstract.Name);
            final SelectBuilder selProdDescr = new SelectBuilder(selProd)
                            .attribute(CIProducts.ProductAbstract.Description);
            final SelectBuilder selProdDim = new SelectBuilder(selProd)
                            .attribute(CIProducts.ProductAbstract.Dimension);
            multi.addSelect(selProdInst, selProdName, selProdDescr, selProdDim);
            multi.execute();
            BigDecimal eplSumCostDirect = BigDecimal.ZERO;

            final PosType posType = getPosType(_parameter);
            while (multi.next()) {
                final Instance prodInst = multi.<Instance>getSelect(selProdInst);
                boolean add;
                switch (posType) {
                    case GENERALCOST:
                        add = prodInst.getType().isCIType(CIConstruction.ProductGeneralCostPosition);
                        break;
                    case PARTLIST:
                        add = prodInst.getType().isCIType(CIConstruction.EntryPartList);
                        break;
                    default:
                        add = true;
                        break;
                }

                if (add) {
                    final Long curencyId = multi.getAttribute(CIConstruction.CostEstimatePositionAbstract.RateCurrencyId);
                    ProductBean prod;
                    if (!values.containsKey(prodInst)) {
                        prod = getProduct(_parameter, prodInst)
                                    .setName(multi.<String>getSelect(selProdName))
                                    .setDescription(multi.<String>getSelect(selProdDescr))
                                    .setUom(Dimension.get(multi.<Long>getSelect(selProdDim)).getBaseUoM().getName())
                                    .setQuantity(multi.<BigDecimal>getAttribute(
                                                    CIConstruction.CostEstimatePositionAbstract.Quantity))
                                    .setCurrency(CurrencyInst.get(curencyId).getSymbol());
                        values.put(prodInst, prod);
                    } else {
                        prod = values.get(prodInst);
                    }
                    final BigDecimal eplPrice = multi
                                    .<BigDecimal>getAttribute(CIConstruction.CostEstimatePositionAbstract.RateNetUnitPrice);
                    final BigDecimal eplQuantity = multi
                                    .<BigDecimal>getAttribute(CIConstruction.CostEstimatePositionAbstract.Quantity);
                    final BigDecimal totalPrice = eplPrice.multiply(eplQuantity);
                    prod.addPrice(totalPrice);
                    prod.addQuantity(eplQuantity);
                    eplSumCostDirect = eplSumCostDirect.add(totalPrice);
                }
            }
            // sorting
            final List<ProductBean> tmpValues = new ArrayList<ProductBean>();
            tmpValues.addAll(values.values());
            final List<Comparator<ProductBean>> comparators = new ArrayList<>();
            comparators.add((_prod0,
             _prod1) -> _prod1.getTotalPrice().compareTo(_prod0.getTotalPrice()));

            Collections.sort(tmpValues, ComparatorUtils.chainedComparator(comparators));
            NumberFormatter.get().getFormatter(2, 2);
            for (final ProductBean prod : tmpValues) {
                final BigDecimal aux = prod.getTotalPrice().multiply(BigDecimal.valueOf(100));
                if (aux.compareTo(BigDecimal.ZERO) != 0) {
                    prod.setPercentage(aux.divide(eplSumCostDirect, BigDecimal.ROUND_HALF_UP));
                }
            }
            return new JRBeanCollectionDataSource(tmpValues);
        }

        @Override
        protected void addColumnDefinition(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {

            final TextColumnBuilder<String> nameColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateEntryPartListReport.class.getName() + ".Column.name"),
                            "name", DynamicReports.type.stringType());
            final TextColumnBuilder<String> descColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateEntryPartListReport.class.getName() + ".Column.description"),
                            "description", DynamicReports.type.stringType()).setWidth(500);
            final TextColumnBuilder<BigDecimal> quantityColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateEntryPartListReport.class.getName() + ".Column.quantity"),
                            "quantity", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<String> uomColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateEntryPartListReport.class.getName() + ".Column.uom"),
                            "uom", DynamicReports.type.stringType());
            final TextColumnBuilder<BigDecimal> netUnitPriceColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateEntryPartListReport.class.getName() + ".Column.netUnitPrice"),
                            "netUnitPrice", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> totalPriceColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateEntryPartListReport.class.getName() + ".Column.totalPrice"),
                            "totalPrice", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> percentColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateEntryPartListReport.class.getName() + ".Column.percentage"),
                            "percentage", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<String> currencyColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateEntryPartListReport.class.getName() + ".Column.currency"),
                            "currency", DynamicReports.type.stringType()).setWidth(50);

            _builder.addColumn(nameColumn, descColumn, quantityColumn, uomColumn,
                            netUnitPriceColumn, totalPriceColumn, currencyColumn, percentColumn);

            final AggregationSubtotalBuilder<BigDecimal> priceSum = DynamicReports.sbt.sum(totalPriceColumn);
            final AggregationSubtotalBuilder<BigDecimal> percentSum = DynamicReports.sbt.sum(percentColumn);
            _builder.addSubtotalAtColumnFooter(priceSum);
            _builder.addSubtotalAtColumnFooter(percentSum);
            _builder.addSubtotalAtColumnFooter(DynamicReports.sbt.first(currencyColumn));
        }

        /**
         * Gets the pos type.
         *
         * @param _parameter Parameter as passed by the eFaps API
         * @return the pos type
         * @throws EFapsException on error
         */
        protected PosType getPosType(final Parameter _parameter) throws EFapsException
        {
            PosType ret = PosType.BOTH;
            final Map<String, Object> filtermap = getFilteredReport(_parameter).getFilterMap(_parameter);
            if (filtermap.containsKey("posType")) {
                final EnumFilterValue filter = (EnumFilterValue) filtermap.get("posType");
                ret = (PosType) filter.getObject();
            }
            return ret;
        }

        /**
         * Gets the product.
         *
         * @param _parameter Parameter as passed by the eFaps API
         * @param _eplInst the epl inst
         * @return the product
         */
        protected ProductBean getProduct(final Parameter _parameter,
                                         final Instance _eplInst)
        {
            return new ProductBean(_eplInst);
        }

        /**
         * Gets the filtered report.
         *
         * @return the filtered report
         */
        protected CostEstimateEntryPartListReport_Base getFilteredReport(final Parameter _parameter)
        {
            return filteredReport;
        }
    }

    /**
     * Inherit class to store the products for the report.
     */
    public static class ProductBean
    {

        /** The instance. */
        private final Instance instance;

        /** The name. */
        private String name;

        /** The description. */
        private String description;

        /** The uom. */
        private String uom;

        /** The quantity. */
        private BigDecimal quantity = BigDecimal.ZERO;

        /** The percentage. */
        private BigDecimal percentage;

        /** The total price. */
        private BigDecimal totalPrice = BigDecimal.ZERO;

        /** The currency. */
        private String currency;

        /**
         * Instantiates a new product bean.
         *
         * @param _prodInst the prod inst
         */
        public ProductBean(final Instance _prodInst)
        {
            instance = _prodInst;
        }

        /**
         * Adds the price.
         *
         * @param toAdd the to add
         * @return the big decimal
         */
        protected ProductBean addPrice(final BigDecimal toAdd)
        {
            totalPrice = totalPrice.add(toAdd);
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
         * Getter method for the instance variable {@link #name}.
         *
         * @return value of instance variable {@link #name}
         */
        public String getName()
        {
            return name;
        }

        /**
         * Setter method for instance variable {@link #name}.
         *
         * @param _name value for instance variable {@link #name}
         * @return the product bean
         */
        public ProductBean setName(final String _name)
        {
            name = _name;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #description}.
         *
         * @return value of instance variable {@link #description}
         */
        public String getDescription()
        {
            return description;
        }

        /**
         * Setter method for instance variable {@link #description}.
         *
         * @param _description value for instance variable {@link #description}
         * @return the product bean
         */
        public ProductBean setDescription(final String _description)
        {
            description = _description;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #uom}.
         *
         * @return value of instance variable {@link #uom}
         */
        public String getUom()
        {
            return uom;
        }

        /**
         * Setter method for instance variable {@link #uom}.
         *
         * @param _uom value for instance variable {@link #uom}
         * @return the product bean
         */
        public ProductBean setUom(final String _uom)
        {
            uom = _uom;
            return this;
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
         * Setter method for instance variable {@link #quantity}.
         *
         * @param _quantity value for instance variable {@link #quantity}
         * @return the product bean
         */
        public ProductBean setQuantity(final BigDecimal _quantity)
        {
            quantity = _quantity;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #percent}.
         *
         * @return value of instance variable {@link #percent}
         */
        public BigDecimal getPercentage()
        {
            return percentage;
        }

        /**
         * Setter method for instance variable {@link #percentage}.
         *
         * @param _percentage the percentage
         * @return the product bean
         */
        public ProductBean setPercentage(final BigDecimal _percentage)
        {
            percentage = _percentage;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #totalPrice}.
         *
         * @return value of instance variable {@link #totalPrice}
         */
        public BigDecimal getTotalPrice()
        {
            return totalPrice;
        }

        /**
         * Setter method for instance variable {@link #totalPrice}.
         *
         * @param _totalPrice value for instance variable {@link #totalPrice}
         * @return the product bean
         */
        public ProductBean setTotalPrice(final BigDecimal _totalPrice)
        {
            totalPrice = _totalPrice;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #netUnitPrice}.
         *
         * @return value of instance variable {@link #netUnitPrice}
         */
        public BigDecimal getNetUnitPrice()
        {
            BigDecimal ret = null;
            if (getQuantity().compareTo(BigDecimal.ZERO) != 0) {
                ret = getTotalPrice().divide(getQuantity(), 2, BigDecimal.ROUND_HALF_UP);
            }
            return ret;
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
         * @param _currency the new currency
         * @return the product bean
         */
        public ProductBean setCurrency(final String _currency)
        {
            currency = _currency;
            return this;
        }
    }
}
