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
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.collections4.ComparatorUtils;
import org.efaps.admin.datamodel.Classification;
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
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.util.EFapsException;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.group.ColumnGroupBuilder;
import net.sf.dynamicreports.report.datasource.DRDataSource;
import net.sf.jasperreports.engine.JRDataSource;

/**
 * TODO comment!
 *
 * @author Jan Moxter
 */
@EFapsUUID("bd2791b8-bbe1-4ef9-b9b2-588fa2d1b453")
@EFapsApplication("eFapsApp-Construction")
public abstract class CostEstimateCostPerProductReport_Base
{

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return return containing snipplet
     * @throws EFapsException on error
     */
    public Return getReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final AbstractDynamicReport dyRp = new CostPerProductReport();
        dyRp.setFileName(DBProperties.getProperty(CostEstimateCostPerProductReport.class.getName() + ".FileName"));
        final String html = dyRp.getHtml(_parameter);
        ret.put(ReturnValues.SNIPLETT, html);
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return return containing file
     * @throws EFapsException on error
     */
    public Return exportReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String mime = (String) props.get("Mime");
        final AbstractDynamicReport dyRp = new CostPerProductReport();
        dyRp.setFileName(DBProperties.getProperty(CostEstimateCostPerProductReport.class.getName() + ".FileName"));
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

    public class CostPerProductReport
        extends AbstractDynamicReport
    {

        @SuppressWarnings("unchecked")
        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final DRDataSource ret = new DRDataSource("type", "class", "entrySheet", "name", "description", "price",
                            "currency");
            final Instance instance = _parameter.getInstance();

            final Map<Instance, Product> values = new HashMap<Instance, Product>();

            final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.CostEstimatePositionAbstract);
            queryBldr.addWhereAttrEqValue(CIConstruction.CostEstimatePositionAbstract.CostEstimateAbstractLink, instance);

            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selDocInst = new SelectBuilder()
                            .linkto(CIConstruction.CostEstimatePositionAbstract.CostEstimateAbstractLink).instance();
            final SelectBuilder selEplInst = new SelectBuilder().linkto(CIConstruction.CostEstimatePositionAbstract.Product)
                            .instance();
            final SelectBuilder selCur = new SelectBuilder().linkto(CIConstruction.CostEstimatePositionAbstract.RateCurrencyId)
                            .attribute(CIERP.Currency.Symbol);
            multi.addSelect(selDocInst, selEplInst, selCur);
            multi.execute();
            while (multi.next()) {

                final String eplCurrency = multi.<String>getSelect(selCur);
                final Instance eplInst = multi.<Instance>getSelect(selEplInst);
                multi.<Instance>getSelect(selDocInst);

                final QueryBuilder bomQueryBldr = new QueryBuilder(CIConstruction.EntryBOM);
                bomQueryBldr.addWhereAttrEqValue(CIConstruction.EntryBOM.FromLink, eplInst);
                final MultiPrintQuery bomMulti = bomQueryBldr.getPrint();
                final SelectBuilder selCS = new SelectBuilder().linkto(CIConstruction.EntryBOM.FromLink)
                                .linkfrom(CIConstruction.EntrySheetPosition, CIConstruction.EntrySheetPosition.Product)
                                .linkto(CIConstruction.EntrySheetPosition.EntrySheetLink).attribute(CIConstruction.EntrySheet.Name);
                final SelectBuilder selProd = new SelectBuilder().linkto(CIConstruction.EntryBOM.ToLink);
                final SelectBuilder selProdInst = new SelectBuilder(selProd).instance();
                final SelectBuilder selProdName = new SelectBuilder(selProd).attribute(CIProducts.ProductAbstract.Name);
                final SelectBuilder selProdType = new SelectBuilder(selProd).type().label();
                final SelectBuilder selProdDesc = new SelectBuilder(selProd)
                                .attribute(CIProducts.ProductAbstract.Description);
                final SelectBuilder selProdClass = new SelectBuilder(selProd).clazz().type();
                bomMulti.addSelect(selCS, selProdInst, selProdName, selProdDesc, selProdType, selProdClass);
                bomMulti.addAttribute(CIConstruction.EntryBOM.UnitPrice);
                bomMulti.execute();

                while (bomMulti.next()) {
                    final Instance prodInst = bomMulti.<Instance>getSelect(selProdInst);
                    Product prod;
                    if (!values.containsKey(prodInst)) {
                        prod = new Product(prodInst);
                        prod.name = bomMulti.<String>getSelect(selProdName);
                        prod.description = bomMulti.<String>getSelect(selProdDesc);
                        prod.typeLabel = bomMulti.<String>getSelect(selProdType);
                        prod.currency = eplCurrency;
                        final List<Classification> clazzes = bomMulti.<List<Classification>>getSelect(selProdClass);
                        if (clazzes != null && !clazzes.isEmpty()) {
                            prod.classLabel = clazzes.get(0).getLabel();
                        } else {
                            prod.classLabel = "-";
                        }
                        values.put(prodInst, prod);
                    } else {
                        prod = values.get(prodInst);
                    }

                    final BigDecimal price = bomMulti.<BigDecimal>getAttribute(CIConstruction.EntryBOM.UnitPrice);
                    prod.addPrice(price, bomMulti.<String>getSelect(selCS));
                }
            }

            // sorting
            final List<Product> tmpValues = new ArrayList<Product>();
            tmpValues.addAll(values.values());
            final List<Comparator<Product>> comparators = new ArrayList<Comparator<Product>>();
            comparators.add((_prod0,
             _prod1) -> _prod0.typeLabel.compareTo(_prod1.typeLabel));

            comparators.add((_prod0,
             _prod1) -> _prod0.classLabel.compareTo(_prod1.classLabel));

            comparators.add((_prod0,
             _prod1) -> _prod0.name.compareTo(_prod1.name));

            Collections.sort(tmpValues, ComparatorUtils.chainedComparator(comparators));
            final DecimalFormat formatter = NumberFormatter.get().getFormatter(2, 2);
            try {
                for (final Product prod : tmpValues) {
                    if (prod.values.size() > 1) {

                        for (final Entry<String, Set<String>> entry : prod.values.entrySet()) {
                            final Set<String> csSet = entry.getValue();
                            final StringBuilder cs = new StringBuilder();
                            boolean first = true;
                            for (final String csStr : csSet) {
                                if (first) {
                                    first = false;
                                } else {
                                    cs.append(", ");
                                }
                                cs.append(csStr);
                            }
                            ret.add(prod.typeLabel, prod.classLabel, cs.toString(), prod.name, prod.description,
                                            formatter.parse(entry.getKey()),
                                            prod.currency);
                        }
                    } else {
                        ret.add(prod.typeLabel, prod.classLabel, "-", prod.name, prod.description,
                                        formatter.parse(prod.values.entrySet().iterator().next().getKey()),
                                        prod.currency);
                    }
                }
            } catch (final ParseException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            return ret;
        }

        @Override
        protected void addColumnDefinition(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {
            final TextColumnBuilder<String> typeColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateCostPerProductReport.class.getName() + ".Column.type"),
                            "type", DynamicReports.type.stringType());
            final TextColumnBuilder<String> classColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateCostPerProductReport.class.getName() + ".Column.class"),
                            "class", DynamicReports.type.stringType());
            final TextColumnBuilder<String> entrySheetColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateCostPerProductReport.class.getName() + ".Column.entrySheet"),
                            "entrySheet", DynamicReports.type.stringType());

            final TextColumnBuilder<String> nameColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateCostPerProductReport.class.getName() + ".Column.name"),
                            "name", DynamicReports.type.stringType());
            final TextColumnBuilder<String> descColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateCostPerProductReport.class.getName() + ".Column.description"),
                            "description", DynamicReports.type.stringType()).setWidth(500);
            final TextColumnBuilder<BigDecimal> priceColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateCostPerProductReport.class.getName() + ".Column.price"),
                            "price", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<String> currencyColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateCostPerProductReport.class.getName() + ".Column.currency"),
                            "currency", DynamicReports.type.stringType());

            final ColumnGroupBuilder typeGroup = DynamicReports.grp.group(typeColumn).groupByDataType();
            final ColumnGroupBuilder classGroup = DynamicReports.grp.group(classColumn).groupByDataType();
            _builder.groupBy(typeGroup, classGroup);
            _builder.addColumn(typeColumn, classColumn, entrySheetColumn, nameColumn, descColumn, priceColumn,
                            currencyColumn);
        }
    }

    /**
     * Inherit class to store the products for the report.
     *
     * @author Jan Moxter
     * @version $Id: CostEstimateCostPerProductReport_Base.java 119 2014-02-27
     *          15:36:48Z jan.moxter $
     */
    public class Product
    {

        public String currency;

        private final Instance instance;

        private String name;

        private String description;

        private String typeLabel;

        private String classLabel;

        private final Map<String, Set<String>> values = new HashMap<String, Set<String>>();

        /**
         * @param _prodInst
         */
        public Product(final Instance _prodInst)
        {
            instance = _prodInst;
        }

        /**
         * @param _price
         * @param _select
         */
        public void addPrice(final BigDecimal _price,
                             final String _entrySheet)
            throws EFapsException
        {
            final DecimalFormat formatter = NumberFormatter.get().getFormatter(2, 2);
            final String priceStr = formatter.format(_price);
            Set<String> cshtsSet;
            if (values.containsKey(priceStr)) {
                cshtsSet = values.get(priceStr);
            } else {
                cshtsSet = new HashSet<String>();
                values.put(priceStr, cshtsSet);
            }
            cshtsSet.add(_entrySheet);
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
    }
}
