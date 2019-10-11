/*
 * Copyright 2007 - 2015 Jan Moxter
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ComparatorUtils;
import org.efaps.admin.datamodel.Classification;
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
import org.efaps.db.AttributeQuery;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIConstruction;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CIProjects;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.erp.FilteredReport;
import org.efaps.util.EFapsException;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.group.ColumnGroupBuilder;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

// TODO: Auto-generated Javadoc
/**
 * TODO comment!.
 *
 * @author Jan Moxter
 */
@EFapsUUID("4b633a47-6054-45eb-93cc-9a9deebd3c1b")
@EFapsApplication("eFapsApp-Construction")
public abstract class CostEstimatePositionReport_Base
    extends FilteredReport
{

    /**
     * Generate report.
     *
     * @param _parameter the _parameter
     * @return the return
     * @throws EFapsException the e faps exception
     */
    public Return generateReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final AbstractDynamicReport dyRp = getReport(_parameter);
        final String html = dyRp.getHtml(_parameter);
        ret.put(ReturnValues.SNIPLETT, html);
        return ret;
    }

    /**
     * Export report.
     *
     * @param _parameter the _parameter
     * @return the return
     * @throws EFapsException the e faps exception
     */
    public Return exportReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String mime = (String) props.get("Mime");
        final AbstractDynamicReport dyRp = getReport(_parameter);
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
     * Gets the report.
     *
     * @param _parameter Parameter as passed by the eFasp API @return the report
     * class @throws EFapsException on error
     * @return the report
     * @throws EFapsException the e faps exception
     */
    protected AbstractDynamicReport getReport(final Parameter _parameter)
        throws EFapsException
    {
        return new DynCostEstimatePositionReport(this);
    }

    @Override
    protected Object getDefaultValue(final Parameter _parameter,
                                     final String _field,
                                     final String _type,
                                     final String _default)
        throws EFapsException
    {
        Object ret;
        if ("Status".equalsIgnoreCase(_type)) {
            final Set<Long> set = new HashSet<>();
            set.add(Status.find(CIConstruction.CostEstimateStatus.Closed).getId());
            ret = new StatusFilterValue().setObject(set);
        } else {
            ret = super.getDefaultValue(_parameter, _field, _type, _default);
        }
        return ret;
    }

    /**
     * The Class DynCostEstimatePositionReport.
     */
    public class DynCostEstimatePositionReport
        extends AbstractDynamicReport
    {

        /**
         * Filtered Report.
         */
        private final CostEstimatePositionReport_Base filterReport;

        /**
         * Instantiates a new dyn cost estimate position report.
         *
         * @param _filterReport the _filter report
         */
        public DynCostEstimatePositionReport(final CostEstimatePositionReport_Base _filterReport)
        {
            filterReport = _filterReport;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final Instance instance = _parameter.getInstance();

            final Map<Instance, DataBean> values = new HashMap<Instance, DataBean>();

            final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.CostEstimatePositionAbstract);
            if (instance.isValid() && instance.getType().isKindOf(CIProjects.ProjectService.getType())) {
                final Map<String, Object> filterMap = getFilterReport().getFilterMap(_parameter);
                final QueryBuilder attrQueryBldr = new QueryBuilder(CIConstruction.ProjectService2CostEstimate);
                attrQueryBldr.addWhereAttrEqValue(CIConstruction.ProjectService2CostEstimate.FromLink, instance);

                QueryBuilder tsAttrQueryBldr = null;
                if (filterMap.containsKey("type")) {
                    final TypeFilterValue filter = (TypeFilterValue) filterMap.get("type");
                    if (!filter.getObject().isEmpty()) {
                        for (final Long obj : filter.getObject()) {
                            final Type type = Type.get(obj);
                            if (tsAttrQueryBldr == null) {
                                tsAttrQueryBldr = new QueryBuilder(type);
                            } else {
                                tsAttrQueryBldr.addType(type);
                            }
                        }
                    }
                }
                if (tsAttrQueryBldr == null) {
                    tsAttrQueryBldr = new QueryBuilder(CIConstruction.CostEstimateAbstract);
                }
                if (filterMap.containsKey("status")) {
                    final StatusFilterValue filter = (StatusFilterValue) filterMap.get("status");
                    if (!filter.getObject().isEmpty()) {
                        // the documents have the same status keys but must be
                        // selected
                        final Set<Status> status = new HashSet<>();
                        for (final Long obj : filter.getObject()) {
                            Status.get(obj).getKey();
                            status.add(Status.get(obj));
                        }
                        tsAttrQueryBldr.addWhereAttrEqValue(CIConstruction.CostEstimateAbstract.StatusAbstract, status.toArray());
                    }
                }
                attrQueryBldr.addWhereAttrInQuery(CIConstruction.ProjectService2CostEstimate.ToLink,
                                tsAttrQueryBldr.getAttributeQuery(CIConstruction.CostEstimateAbstract.ID));

                final AttributeQuery attrQuery = attrQueryBldr
                                .getAttributeQuery(CIConstruction.ProjectService2CostEstimate.ToLink);

                queryBldr.addWhereAttrInQuery(CIConstruction.CostEstimatePositionAbstract.CostEstimateAbstractLink, attrQuery);
            } else {
                queryBldr.addWhereAttrEqValue(CIConstruction.CostEstimatePositionAbstract.CostEstimateAbstractLink, instance);
            }

            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selCEProd = new SelectBuilder().linkto(CIConstruction.CostEstimatePositionAbstract.Product);
            final SelectBuilder selCEProdInst = new SelectBuilder(selCEProd).instance();
            final SelectBuilder selCEProdName = new SelectBuilder(selCEProd).attribute(CIProducts.ProductAbstract.Name);
            final SelectBuilder selCur = new SelectBuilder().linkto(CIConstruction.CostEstimatePositionAbstract.RateCurrencyId)
                            .attribute(CIERP.Currency.Symbol);
            multi.addSelect(selCEProdInst, selCEProdName, selCur);
            multi.addAttribute(CIConstruction.CostEstimatePositionAbstract.Quantity, CIConstruction.CostEstimatePositionAbstract.UoM,
                            CIConstruction.CostEstimatePositionAbstract.RateNetPrice,
                            CIConstruction.CostEstimatePositionAbstract.ProductDesc);
            multi.execute();
            while (multi.next()) {
                final BigDecimal eplQuantity = multi
                                .<BigDecimal>getAttribute(CIConstruction.CostEstimatePositionAbstract.Quantity);
                final String eplCurrency = multi.<String>getSelect(selCur);

                final Instance ceProdInst = multi.<Instance>getSelect(selCEProdInst);
                // check if general cost or partlist
                if (ceProdInst.getType().isCIType(CIConstruction.ProductGeneralCostPosition)) {
                    DataBean prod;
                    if (!values.containsKey(ceProdInst)) {
                        prod = getDataBean(_parameter)
                                    .setProductInstance(ceProdInst)
                                    .setName(multi.<String>getSelect(selCEProdName))
                                    .setUom(Dimension.getUoM(multi.<Long>getAttribute(
                                                    CIConstruction.CostEstimatePositionAbstract.UoM)).getName())
                                    .setTypeLabel(CIConstruction.ProductGeneralCostPosition.getType().getLabel())
                                    .setCurrency(eplCurrency)
                                    .setClassLabel("-");
                        values.put(ceProdInst, prod);
                    } else {
                        prod = values.get(ceProdInst);
                    }
                    final BigDecimal price = multi.<BigDecimal>getAttribute(
                                    CIConstruction.CostEstimatePositionAbstract.RateNetPrice);
                    prod.add(eplQuantity, price)
                        .addDescription(multi.<String>getAttribute(
                                    CIConstruction.CostEstimatePositionAbstract.ProductDesc));
                } else {

                    // add the manual amount of a partlist
                    final PrintQuery print = new PrintQuery(ceProdInst);
                    final SelectBuilder sel = new SelectBuilder().clazz(CIConstruction.EntryPartList_Class)
                                    .attribute(CIConstruction.EntryPartList_Class.ManualAmount);
                    print.addSelect(sel);
                    print.execute();

                    final BigDecimal amount = print.<BigDecimal>getSelect(sel);
                    final DataBean manual;
                    if (!values.containsKey(getFakeInst4ManualAmount(_parameter))) {
                        manual = getDataBean(_parameter)
                                        .setProductInstance(getFakeInst4ManualAmount(_parameter))
                                        .setName("-")
                                        .setDescription(filterReport.getDBProperty("Description.Manual"))
                                        .setUom("")
                                        .setTypeLabel(CIConstruction.ProductService.getType().getLabel())
                                        .setClassLabel(CIConstruction.ProductServiceClassTools.getType().getLabel())
                                        .setCurrency(eplCurrency);
                        values.put(getFakeInst4ManualAmount(_parameter), manual);
                    } else {
                        manual = values.get(getFakeInst4ManualAmount(_parameter));
                    }
                    manual.add(BigDecimal.ONE, amount);


                    final QueryBuilder bomQueryBldr = new QueryBuilder(CIConstruction.EntryBOM);
                    bomQueryBldr.addWhereAttrEqValue(CIConstruction.EntryBOM.FromLink, ceProdInst);
                    final MultiPrintQuery bomMulti = bomQueryBldr.getPrint();
                    final SelectBuilder selProd = new SelectBuilder().linkto(CIConstruction.EntryBOM.ToLink);
                    final SelectBuilder selProdInst = new SelectBuilder(selProd).instance();
                    final SelectBuilder selProdName = new SelectBuilder(selProd).attribute(
                                    CIProducts.ProductAbstract.Name);
                    final SelectBuilder selProdType = new SelectBuilder(selProd).type().label();
                    final SelectBuilder selProdDesc = new SelectBuilder(selProd)
                                    .attribute(CIProducts.ProductAbstract.Description);
                    final SelectBuilder selProdClass = new SelectBuilder(selProd).clazz().type();
                    bomMulti.addSelect(selProdInst, selProdName, selProdDesc, selProdType, selProdClass);
                    bomMulti.addAttribute(CIConstruction.EntryBOM.Quantity, CIConstruction.EntryBOM.Price, CIConstruction.EntryBOM.UoM);
                    bomMulti.execute();

                    while (bomMulti.next()) {
                        final Instance prodInst = bomMulti.<Instance>getSelect(selProdInst);
                        DataBean prod;
                        if (!values.containsKey(prodInst)) {
                            prod = getDataBean(_parameter)
                                        .setProductInstance(prodInst)
                                        .setName(bomMulti.<String>getSelect(selProdName))
                                        .setDescription(bomMulti.<String>getSelect(selProdDesc))
                                        .setUom(Dimension.getUoM(bomMulti.<Long>getAttribute(
                                                        CISales.PositionSumAbstract.UoM)).getName())
                                        .setTypeLabel(bomMulti.<String>getSelect(selProdType))
                                        .setCurrency(eplCurrency);
                            final List<Classification> clazzes = bomMulti.<List<Classification>>getSelect(selProdClass);
                            if (clazzes != null && !clazzes.isEmpty()) {
                                prod.setClassLabel(clazzes.get(0).getLabel());
                            } else {
                                prod.setClassLabel("-");
                            }
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
            }

            // sorting
            final List<DataBean> tmpValues = new ArrayList<DataBean>();
            tmpValues.addAll(values.values());

            CollectionUtils.filter(tmpValues, _dataBean -> _dataBean.getTotalPrice().compareTo(BigDecimal.ZERO) != 0);

            final List<Comparator<DataBean>> comparators = new ArrayList<Comparator<DataBean>>();
            comparators.add((_prod0,
             _prod1) -> _prod0.typeLabel.compareTo(_prod1.typeLabel));

            comparators.add((_prod0,
             _prod1) -> _prod0.getClassLabel().compareTo(_prod1.getClassLabel()));

            comparators.add((_prod0,
             _prod1) -> _prod0.name.compareTo(_prod1.name));

            Collections.sort(tmpValues, ComparatorUtils.chainedComparator(comparators));

            return new JRBeanCollectionDataSource(tmpValues);
        }

        @Override
        protected void addColumnDefinition(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {
            final TextColumnBuilder<String> typeColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimatePositionReport.class.getName() + ".Column.type"),
                            "type", DynamicReports.type.stringType());
            final TextColumnBuilder<String> classColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimatePositionReport.class.getName() + ".Column.class"),
                            "classLabel", DynamicReports.type.stringType());
            final TextColumnBuilder<String> nameColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimatePositionReport.class.getName() + ".Column.name"),
                            "name", DynamicReports.type.stringType());
            final TextColumnBuilder<String> descColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimatePositionReport.class.getName() + ".Column.description"),
                            "description", DynamicReports.type.stringType()).setWidth(500);
            final TextColumnBuilder<String> uomColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimatePositionReport.class.getName() + ".Column.uom"),
                            "uom", DynamicReports.type.stringType());
            final TextColumnBuilder<BigDecimal> quantityColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimatePositionReport.class.getName() + ".Column.quantity"),
                            "quantity", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> unitPriceColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimatePositionReport.class.getName() + ".Column.unitPrice"),
                            "unitPrice", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> priceColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimatePositionReport.class.getName() + ".Column.totalPrice"),
                            "totalPrice", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<String> currencyColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimatePositionReport.class.getName() + ".Column.currency"),
                            "currency", DynamicReports.type.stringType());

            final ColumnGroupBuilder typeGroup = DynamicReports.grp.group(typeColumn).groupByDataType();
            final ColumnGroupBuilder classGroup = DynamicReports.grp.group(classColumn).groupByDataType();

            _builder.addSubtotalAtGroupFooter(typeGroup, DynamicReports.sbt.sum(priceColumn));
            _builder.addSubtotalAtGroupFooter(classGroup, DynamicReports.sbt.sum(priceColumn));
            _builder.addSubtotalAtLastPageFooter(DynamicReports.sbt.sum(priceColumn));

            _builder.groupBy(typeGroup, classGroup);
            _builder.addColumn(typeColumn, classColumn, nameColumn, descColumn, uomColumn, unitPriceColumn,
                            quantityColumn, priceColumn, currencyColumn);
        }

        /**
         * Getter method for the instance variable {@link #filterReport}.
         *
         * @return value of instance variable {@link #filterReport}
         */
        public CostEstimatePositionReport_Base getFilterReport()
        {
            return filterReport;
        }

        /**
         * Gets the data bean.
         *
         * @param _parameter the _parameter
         * @return the data bean
         */
        protected DataBean  getDataBean(final Parameter _parameter)
        {
            return new DataBean();
        }

        /**
         * Gets the fake inst4 manual amount.
         *
         * @param _parameter the _parameter
         * @return the fake inst4 manual amount
         */
        protected Instance getFakeInst4ManualAmount(final Parameter _parameter)
        {
            return Instance.get(CIConstruction.ProductService.getType(), Long.valueOf(0));
        }
    }

    /**
     * Inherit class to store the products for the report.
     *
     */
    public static class DataBean
    {
        /** The currency. */
        private String currency;

        /** The instance. */
        private Instance productInstance;

        /** The name. */
        private String name;

        /** The description. */
        private String description;

        /** The uom. */
        private String uom;

        /** The type label. */
        private String typeLabel;

        /** The class label. */
        private String classLabel;

        /** The quantity. */
        private BigDecimal quantity = BigDecimal.ZERO;

        /** The total price. */
        private BigDecimal totalPrice = BigDecimal.ZERO;

        /** The descriptions. */
        private final Set<String> descriptions = new TreeSet<>();

        /**
         * Adds the description.
         *
         * @param _description the _description
         * @return the data bean
         */
        public DataBean addDescription(final String _description)
        {
            descriptions.add(_description);
            return this;
        }

        /**
         * Gets the unit price.
         *
         * @return the unit price
         */
        public BigDecimal getUnitPrice()
        {
            return getQuantity().compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : totalPrice
                            .divide(getQuantity(), 2, BigDecimal.ROUND_HALF_UP);
        }

        /**
         * Gets the total price.
         *
         * @return the total price
         */
        public BigDecimal getTotalPrice()
        {
            return totalPrice;
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
         * Gets the currency.
         *
         * @return the currency
         */
        public String getCurrency()
        {
            return currency;
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
         * Gets the description.
         *
         * @return the description
         */
        public String getDescription()
        {
            String ret;
            if (!descriptions.isEmpty()) {
                final StringBuilder bldr = new StringBuilder();
                boolean first = true;
                for (final String descr : descriptions) {
                    if (first) {
                        first = false;
                    } else {
                        bldr.append(",\n");
                    }
                    bldr.append(descr);
                }
                ret = bldr.toString();
            } else {
                ret = description;
            }
            return ret;
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
         * Gets the type.
         *
         * @return the type
         */
        public String getType()
        {
            return typeLabel;
        }

        /**
         * Adds the.
         *
         * @param _quantity the _quantity @param _newTotalPrice the _new total
         * price
         * @param _newTotalPrice the _new total price
         * @return the data bean
         */
        public DataBean add(final BigDecimal _quantity,
                            final BigDecimal _newTotalPrice)
        {
            if (_quantity.compareTo(BigDecimal.ZERO) != 0 && _newTotalPrice.compareTo(BigDecimal.ZERO) != 0) {
                totalPrice = totalPrice.add(_newTotalPrice);
                quantity = quantity.add(_quantity);
            }
            return this;
        }

        /**
         * Getter method for the instance variable {@link #classLabel}.
         *
         * @return value of instance variable {@link #classLabel}
         */
        public String getClassLabel()
        {
            String ret;
            if (getProductInstance().getType().isKindOf(CIProducts.ProductMaterial.getType())) {
                ret = "-";
            } else {
                ret = classLabel;
            }
            return ret;
        }

        /**
         * Setter method for instance variable {@link #classLabel}.
         *
         * @param _classLabel value for instance variable {@link #classLabel}
         * @return the data bean
         */
        public DataBean setClassLabel(final String _classLabel)
        {
            classLabel = _classLabel;
            return this;
        }
        /**
         * Getter method for the instance variable {@link #productInstance}.
         *
         * @return value of instance variable {@link #productInstance}
         */
        public Instance getProductInstance()
        {
            return productInstance;
        }

        /**
         * Setter method for instance variable {@link #productInstance}.
         *
         * @param _productInstance value for instance variable {@link #productInstance}
         * @return the data bean
         */
        public DataBean setProductInstance(final Instance _productInstance)
        {
            productInstance = _productInstance;
            return this;
        }
       /**
         * Getter method for the instance variable {@link #typeLabel}.
         *
         * @return value of instance variable {@link #typeLabel}
         */
        public String getTypeLabel()
        {
            return typeLabel;
        }

        /**
         * Setter method for instance variable {@link #typeLabel}.
         *
         * @param _typeLabel value for instance variable {@link #typeLabel}
         * @return the data bean
         */
        public DataBean setTypeLabel(final String _typeLabel)
        {
            typeLabel = _typeLabel;
            return this;
        }

        /**
         * Setter method for instance variable {@link #currency}.
         *
         * @param _currency value for instance variable {@link #currency}
         * @return the data bean
         */
        public DataBean setCurrency(final String _currency)
        {
            currency = _currency;
            return this;
        }

        /**
         * Setter method for instance variable {@link #name}.
         *
         * @param _name value for instance variable {@link #name}
         * @return the data bean
         */
        public DataBean setName(final String _name)
        {
            name = _name;
            return this;
        }

        /**
         * Setter method for instance variable {@link #description}.
         *
         * @param _description value for instance variable {@link #description}
         * @return the data bean
         */
        public DataBean setDescription(final String _description)
        {
            description = _description;
            return this;
        }

        /**
         * Setter method for instance variable {@link #uom}.
         *
         * @param _uom value for instance variable {@link #uom}
         * @return the data bean
         */
        public DataBean setUom(final String _uom)
        {
            uom = _uom;
            return this;
        }

        /**
         * Setter method for instance variable {@link #quantity}.
         *
         * @param _quantity value for instance variable {@link #quantity}
         * @return the data bean
         */
        public DataBean setQuantity(final BigDecimal _quantity)
        {
            quantity = _quantity;
            return this;
        }

        /**
         * Setter method for instance variable {@link #totalPrice}.
         *
         * @param _totalPrice value for instance variable {@link #totalPrice}
         * @return the data bean
         */
        public DataBean setTotalPrice(final BigDecimal _totalPrice)
        {
            totalPrice = _totalPrice;
            return this;
        }
    }
}
