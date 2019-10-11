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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.comparators.ComparatorChain;
import org.efaps.admin.datamodel.Classification;
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
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.construction.EntryBOM;
import org.efaps.esjp.construction.IPositionType;
import org.efaps.esjp.construction.PositionType;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.FilteredReport;
import org.efaps.util.EFapsException;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

/**
 * The Class EntryPartListSummaryReport_Base.
 *
 * @author Jan Moxter
 */
@EFapsUUID("92ab6803-488e-4f3a-88ca-ebc386dac073")
@EFapsApplication("eFapsApp-Construction")
public abstract class EntryPartListSummaryReport_Base
    extends FilteredReport
{

    /**
     * Generate report.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
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
     * @param _parameter Parameter as passed by the eFaps API
     * @return the report
     * @throws EFapsException on error
     */
    protected AbstractDynamicReport getReport(final Parameter _parameter)
        throws EFapsException
    {
        return new DynEntryPartListSummaryReport(this);
    }

    /**
     * The Class DynEntryPartListSummaryReport.
     *
     * @author The eFaps Team
     */
    public static class DynEntryPartListSummaryReport
        extends AbstractDynamicReport
    {

        /** The filtered report. */
        private final FilteredReport filteredReport;

        /**
         * Instantiates a new dyn entry part list summary report.
         *
         * @param _filteredReport the filtered report
         */
        public DynEntryPartListSummaryReport(final FilteredReport _filteredReport)
        {
            filteredReport = _filteredReport;
        }

        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final List<DataBean> values = new ArrayList<>();
            String currency = null;
            final QueryBuilder bomQueryBldr = new QueryBuilder(CIConstruction.EntryBOM);
            if (_parameter.getInstance().getType().isKindOf(CIConstruction.EntryPartList.getType())) {
                bomQueryBldr.addWhereAttrEqValue(CIConstruction.EntryBOM.FromLink, _parameter.getInstance());
            } else {
                final QueryBuilder posAttrQueryBldr = new QueryBuilder(CISales.PositionAbstract);
                if (_parameter.getInstance().getType().isKindOf(CIConstruction.CostEstimateAbstract.getType())) {
                    final QueryBuilder ceAttrQueryBldr = new QueryBuilder(CIConstruction.CostEstimate2EntrySheet);
                    ceAttrQueryBldr.addWhereAttrEqValue(CIConstruction.CostEstimate2EntrySheet.FromLink,
                                    _parameter.getInstance());
                    final AttributeQuery quotAttrQuery = ceAttrQueryBldr
                                    .getAttributeQuery(CIConstruction.CostEstimate2EntrySheet.ToLink);
                    posAttrQueryBldr.addWhereAttrInQuery(CISales.PositionAbstract.DocumentAbstractLink, quotAttrQuery);
                    final PrintQuery print = new PrintQuery(_parameter.getInstance());
                    print.addAttribute(CIConstruction.CostEstimateAbstract.RateCurrencyId);
                    print.execute();
                    currency = CurrencyInst.get(print.<Long>getAttribute(CIConstruction.CostEstimateAbstract.RateCurrencyId))
                                    .getSymbol();
                } else {
                    posAttrQueryBldr.addWhereAttrEqValue(CISales.PositionAbstract.DocumentAbstractLink,
                                    _parameter.getInstance());
                }
                final AttributeQuery posAttrQuery = posAttrQueryBldr
                                .getAttributeQuery(CISales.PositionAbstract.Product);
                final QueryBuilder entryAttrQueryBldr = new QueryBuilder(CIConstruction.EntryPartList);
                entryAttrQueryBldr.addWhereAttrInQuery(CIConstruction.EntryPartList.ID, posAttrQuery);
                final AttributeQuery entryAttrQuery = entryAttrQueryBldr.getAttributeQuery(CIConstruction.EntryPartList.ID);
                bomQueryBldr.addWhereAttrInQuery(CIConstruction.EntryBOM.FromLink, entryAttrQuery);
            }
            final MultiPrintQuery bomMulti = bomQueryBldr.getPrint();
            final SelectBuilder selEpl = SelectBuilder.get().linkto(CIConstruction.EntryBOM.FromLink);
            final SelectBuilder selEplInst = new SelectBuilder(selEpl).instance();
            final SelectBuilder selEplTotal = new SelectBuilder(selEpl).clazz(CIConstruction.EntryPartList_Class).attribute(
                            CIConstruction.EntryPartList_Class.Total);
            final SelectBuilder selEplMAmount = new SelectBuilder(selEpl).clazz(CIConstruction.EntryPartList_Class).attribute(
                            CIConstruction.EntryPartList_Class.ManualAmount);
            final SelectBuilder selEplName = new SelectBuilder(selEpl).attribute(CIConstruction.EntryPartList.Name);
            final SelectBuilder selEplDesc = new SelectBuilder(selEpl).attribute(CIConstruction.EntryPartList.Description);

            final SelectBuilder selProd = SelectBuilder.get().linkto(CIConstruction.EntryBOM.ToLink);
            final SelectBuilder selProdInst = new SelectBuilder(selProd).instance();
            final SelectBuilder selProdClass = new SelectBuilder(selProd).clazz().type();
            bomMulti.addSelect(selEplInst, selEplTotal, selEplName, selEplInst, selEplDesc, selProdInst, selEplMAmount,
                            selProdClass);
            bomMulti.addAttribute(CIConstruction.EntryBOM.Price);
            bomMulti.execute();
            final Map<Instance, DataBean> map = new HashMap<Instance, DataBean>();
            while (bomMulti.next()) {
                final Instance partListInst = bomMulti.<Instance>getSelect(selEplInst);
                final DataBean dataBean;
                if (map.containsKey(partListInst)) {
                    dataBean = map.get(partListInst);
                } else {
                    dataBean = new DataBean()
                                    .setName(bomMulti.<String>getSelect(selEplName))
                                    .setDescription(bomMulti.<String>getSelect(selEplDesc))
                                    .setTotal(bomMulti.<BigDecimal>getSelect(selEplTotal))
                                    .setTools(bomMulti.<BigDecimal>getSelect(selEplMAmount))
                                    .setCurrency(currency);
                    map.put(partListInst, dataBean);
                }
                final Instance prodInst = bomMulti.getSelect(selProdInst);
                final List<Classification> clazzes = bomMulti.<List<Classification>>getSelect(selProdClass);
                final IPositionType posType = EntryBOM.getPositionType(_parameter, prodInst, clazzes);
                if (posType.equals(PositionType.MATERIAL)) {
                    dataBean.addMaterial(bomMulti.<BigDecimal>getAttribute(CIConstruction.EntryBOM.Price));
                } else if (posType.equals(PositionType.PERSONAL)) {
                    dataBean.addPersonal(bomMulti.<BigDecimal>getAttribute(CIConstruction.EntryBOM.Price));
                } else if (posType.equals(PositionType.TOOLS)) {
                    dataBean.addTools(bomMulti.<BigDecimal>getAttribute(CIConstruction.EntryBOM.Price));
                } else if (posType.equals(PositionType.CONTRACT)) {
                    dataBean.addContract(bomMulti.<BigDecimal>getAttribute(CIConstruction.EntryBOM.Price));
                }
            }
            values.addAll(map.values());

            final ComparatorChain<DataBean> compChain = new ComparatorChain<>();
            compChain.addComparator((_o1,
             _o2) -> _o1.getName().compareTo(_o2.getName()));
            compChain.addComparator((_o1,
             _o2) -> _o1.getDescription().compareTo(_o2.getDescription()));
            Collections.sort(values, compChain);
            return new JRBeanCollectionDataSource(values);
        }

        @Override
        protected void addColumnDefinition(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
                                              throws EFapsException
        {
            final TextColumnBuilder<String> nameColumn = DynamicReports.col.column(
                            getFilteredReport().getDBProperty("Column.name"),
                            "name", DynamicReports.type.stringType());
            final TextColumnBuilder<String> descriptionColumn = DynamicReports.col.column(
                            getFilteredReport().getDBProperty("Column.description"),
                            "description", DynamicReports.type.stringType()).setWidth(300);
            final TextColumnBuilder<BigDecimal> materialColumn = DynamicReports.col.column(
                            getFilteredReport().getDBProperty("Column.material"),
                            "material", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> personalColumn = DynamicReports.col.column(
                            getFilteredReport().getDBProperty("Column.personal"),
                            "personal", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> toolsColumn = DynamicReports.col.column(
                            getFilteredReport().getDBProperty("Column.tools"),
                            "tools", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> contractColumn = DynamicReports.col.column(
                            getFilteredReport().getDBProperty("Column.contract"),
                            "contract", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> totalColumn = DynamicReports.col.column(
                            getFilteredReport().getDBProperty("Column.total"),
                            "total", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<String> curencyColumn = DynamicReports.col.column(
                            getFilteredReport().getDBProperty("Column.currency"),
                            "currency", DynamicReports.type.stringType());
            _builder.addColumn(nameColumn, descriptionColumn, materialColumn, personalColumn, toolsColumn,
                            contractColumn, totalColumn, curencyColumn);

            _builder.addSubtotalAtSummary(DynamicReports.sbt.sum(materialColumn),
                            DynamicReports.sbt.sum(personalColumn),
                            DynamicReports.sbt.sum(toolsColumn),
                            DynamicReports.sbt.sum(contractColumn),
                            DynamicReports.sbt.sum(totalColumn),
                            DynamicReports.sbt.first(curencyColumn));
        }

        /**
         * Gets the filtered report.
         *
         * @return the filtered report
         */
        public FilteredReport getFilteredReport()
        {
            return filteredReport;
        }
    }

    /**
     * The Class DataBean.
     *
     */
    public static class DataBean
    {

        /** The name. */
        private String name;

        /** The descr. */
        private String description;

        /** The material. */
        private BigDecimal material = BigDecimal.ZERO;

        /** The personal. */
        private BigDecimal personal = BigDecimal.ZERO;

        /** The tools. */
        private BigDecimal tools = BigDecimal.ZERO;

        /** The contract. */
        private BigDecimal contract = BigDecimal.ZERO;

        /** The total. */
        private BigDecimal total = BigDecimal.ZERO;

        /** The currency. */
        private String currency;

        /**
         * Gets the personal.
         *
         * @return the personal
         */
        public BigDecimal getPersonal()
        {
            return personal;
        }

        /**
         * Sets the personal.
         *
         * @param _personal the personal
         * @return the data bean
         */
        public DataBean setPersonal(final BigDecimal _personal)
        {
            personal = _personal;
            return this;
        }

        /**
         * Sets the personal.
         *
         * @param _personal the personal
         * @return the data bean
         */
        public DataBean addPersonal(final BigDecimal _personal)
        {
            personal = personal.add(_personal);
            return this;
        }

        /**
         * Gets the tools.
         *
         * @return the tools
         */
        public BigDecimal getTools()
        {
            return tools;
        }

        /**
         * Sets the tools.
         *
         * @param _tools the tools
         * @return the data bean
         */
        public DataBean setTools(final BigDecimal _tools)
        {
            tools = _tools;
            return this;
        }

        /**
         * Sets the tools.
         *
         * @param _tools the tools
         * @return the data bean
         */
        public DataBean addTools(final BigDecimal _tools)
        {
            tools = tools.add(_tools);
            return this;
        }

        /**
         * Gets the contract.
         *
         * @return the contract
         */
        public BigDecimal getContract()
        {
            return contract;
        }

        /**
         * Sets the contract.
         *
         * @param _contract the contract
         * @return the data bean
         */
        public DataBean setContract(final BigDecimal _contract)
        {
            contract = _contract;
            return this;
        }

        /**
         * Sets the contract.
         *
         * @param _contract the contract
         * @return the data bean
         */
        public DataBean addContract(final BigDecimal _contract)
        {
            contract = contract.add(_contract);
            return this;
        }

        /**
         * Gets the total.
         *
         * @return the total
         */
        public BigDecimal getTotal()
        {
            return total;
        }

        /**
         * Sets the total.
         *
         * @param _total the total
         * @return the data bean
         */
        public DataBean setTotal(final BigDecimal _total)
        {
            total = _total;
            return this;
        }

        /**
         * Gets the material.
         *
         * @return the material
         */
        public BigDecimal getMaterial()
        {
            return material;
        }

        /**
         * Sets the material.
         *
         * @param _material the new material
         * @return the data bean
         */
        public DataBean setMaterial(final BigDecimal _material)
        {
            material = _material;
            return this;
        }

        /**
         * Sets the material.
         *
         * @param _material the new material
         * @return the data bean
         */
        public DataBean addMaterial(final BigDecimal _material)
        {
            material = material.add(_material);
            return this;
        }

        /**
         * Gets the descr.
         *
         * @return the descr
         */
        public String getDescription()
        {
            return description;
        }

        /**
         * Sets the description.
         *
         * @param _description the description
         * @return the data bean
         */
        public DataBean setDescription(final String _description)
        {
            description = _description;
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
         * @return the data bean
         */
        public DataBean setName(final String _name)
        {
            name = _name;
            return this;
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
         * @return the data bean
         */
        public DataBean setCurrency(final String _currency)
        {
            currency = _currency;
            return this;
        }
    }
}
