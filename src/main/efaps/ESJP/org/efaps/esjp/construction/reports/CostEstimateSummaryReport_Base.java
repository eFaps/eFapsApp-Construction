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
package org.efaps.esjp.construction.reports;

import java.io.File;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
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
import org.efaps.esjp.ci.CIProjects;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.construction.EntryBOM;
import org.efaps.esjp.construction.IPositionType;
import org.efaps.esjp.construction.PositionType;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.FilteredReport;
import org.efaps.util.EFapsException;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.PercentageColumnBuilder;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.group.ColumnGroupBuilder;
import net.sf.dynamicreports.report.constant.GroupHeaderLayout;
import net.sf.dynamicreports.report.datasource.DRDataSource;
import net.sf.jasperreports.engine.JRDataSource;

/**
 * TODO comment!.
 *
 * @author Jan Moxter
 */
@EFapsUUID("3b27cd4e-9e01-4f5f-959a-af6adbfbbc07")
@EFapsApplication("eFapsApp-Construction")
public abstract class CostEstimateSummaryReport_Base
    extends FilteredReport
{

    /**
     * The Enum CostGroup.
     *
     * @author The eFaps Team
     */
    public enum CostGroup
    {
        /** The direct. */
        DIRECT,
        /** The indirect. */
        INDIRECT
    }

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
        final String mime = getProperty(_parameter, "Mime", "pdf");
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
        return new SummaryReport(this);
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
     * The Class SummaryReport.
     */
    public static class SummaryReport
        extends AbstractDynamicReport
    {

        /**
         * Filtered Report.
         */
        private final CostEstimateSummaryReport_Base filterReport;

        /** The currency id. */
        private Long currencyId;

        /**
         * Instantiates a new summary report.
         *
         * @param _filterReport the filter report
         */
        public SummaryReport(final CostEstimateSummaryReport_Base _filterReport)
        {
            filterReport = _filterReport;
        }

        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final DRDataSource ret = new DRDataSource("summaryGroup", "costGroup", "description", "percent", "amount",
                            "currency");

            final Instance instance = _parameter.getInstance();

            final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.CostEstimatePositionAbstract);
            final QueryBuilder pcQueryBldr = new QueryBuilder(CIConstruction.CostEstimatePercentageCost);

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
                        // the documents have the same status keys but must be selected
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
                pcQueryBldr.addWhereAttrInQuery(CIConstruction.CostEstimatePercentageCost.CostEstimateLink, attrQuery);
            } else {
                queryBldr.addWhereAttrEqValue(CIConstruction.CostEstimatePositionAbstract.CostEstimateAbstractLink, instance);
                pcQueryBldr.addWhereAttrEqValue(CIConstruction.CostEstimatePercentageCost.CostEstimateLink, instance);
            }
            final MultiPrintQuery multi = queryBldr.getPrint();

            final SelectBuilder selCePInst = new SelectBuilder().linkto(CIConstruction.CostEstimatePositionAbstract.Product)
                            .instance();
            final SelectBuilder selCePInstClass = new SelectBuilder().linkto(CIConstruction.CostEstimatePositionAbstract.Product)
                            .clazz().type();

            final SelectBuilder eplManAmount = new SelectBuilder().linkto(CIConstruction.CostEstimatePositionAbstract.Product)
                            .clazz(CIConstruction.EntryPartList_Class).attribute(CIConstruction.EntryPartList_Class.ManualAmount);
            multi.addSelect(selCePInst, selCePInstClass, eplManAmount);
            multi.addAttribute(CIConstruction.CostEstimatePositionAbstract.Quantity,
                            CIConstruction.CostEstimatePositionAbstract.RateNetPrice,
                            CIConstruction.CostEstimatePositionAbstract.RateCurrencyId);
            multi.execute();
            final DirectCostPosition dcp = new DirectCostPosition(this);
            final IndirectCostPosition idcp = new IndirectCostPosition(this);
            while (multi.next()) {
                currencyId = multi.getAttribute(CIConstruction.CostEstimatePositionAbstract.RateCurrencyId);
                final BigDecimal eplNetPrice = multi
                                .<BigDecimal>getAttribute(CIConstruction.CostEstimatePositionAbstract.RateNetPrice);
                final BigDecimal eplQuantity = multi
                                .<BigDecimal>getAttribute(CIConstruction.CostEstimatePositionAbstract.Quantity);

                final Instance eplInst = multi.<Instance>getSelect(selCePInst);
                if (eplInst.getType().isKindOf(CIConstruction.EntryPartList.getType())) {
                    final BigDecimal manAmount = multi.<BigDecimal>getSelect(eplManAmount);
                    if (manAmount != null && manAmount.compareTo(BigDecimal.ZERO) != 0) {
                        dcp.add(PositionType.TOOLS, eplQuantity.multiply(manAmount));
                    }
                    final QueryBuilder bomQueryBldr = new QueryBuilder(CIConstruction.EntryBOM);
                    bomQueryBldr.addWhereAttrEqValue(CIConstruction.EntryBOM.FromLink, eplInst);
                    final MultiPrintQuery bomMulti = bomQueryBldr.getPrint();
                    final SelectBuilder selProd = new SelectBuilder().linkto(CIConstruction.EntryBOM.ToLink);
                    final SelectBuilder selProdInst = new SelectBuilder(selProd).instance();
                    final SelectBuilder selProdClass = new SelectBuilder(selProd).clazz().type();
                    bomMulti.addSelect(selProdInst, selProdClass);
                    bomMulti.addAttribute(CIConstruction.EntryBOM.Price);
                    bomMulti.execute();
                    while (bomMulti.next()) {
                        final Instance prodInst = bomMulti.<Instance>getSelect(selProdInst);
                        IPositionType posType = null;
                        // first check if unique type can be found
                        int i = 0;
                        for (final IPositionType postype : EntryBOM.getPositionTypes()) {
                            for (final Type type : postype.getTypes()) {
                                if (prodInst.getType().isKindOf(type)) {
                                    posType = postype;
                                    i++;
                                }
                            }
                        }
                        if (i != 1) {
                            final List<Classification> clazzes = bomMulti.<List<Classification>>getSelect(selProdClass);
                            if (clazzes != null && !clazzes.isEmpty()) {
                                for (final IPositionType postype : EntryBOM.getPositionTypes()) {
                                    if (postype.getClassifications().contains(clazzes.get(0))) {
                                        posType = postype;
                                        break;
                                    }
                                }
                            }
                        }
                        final BigDecimal price = bomMulti.<BigDecimal>getAttribute(CIConstruction.EntryBOM.Price);
                        dcp.add(posType, eplQuantity.multiply(price));
                    }
                } else {
                    final List<Classification> clazzes = multi.<List<Classification>>getSelect(selCePInstClass);
                    if (clazzes != null && !clazzes.isEmpty()) {
                        idcp.add(clazzes.get(0), eplNetPrice);
                    } else {
                        idcp.add(null, eplNetPrice);
                    }
                }
            }
            final PercentageCostPosition pcp = new PercentageCostPosition(this);
            final MultiPrintQuery pcMulti = pcQueryBldr.getPrint();
            final SelectBuilder pcSelCost = SelectBuilder.get()
                            .linkto(CIConstruction.CostEstimatePercentageCost.PercentageCostLink)
                            .attribute(CIConstruction.AttributeDefinitionPercentageCost.Value);
            pcMulti.addSelect(pcSelCost);
            pcMulti.addAttribute(CIConstruction.CostEstimatePercentageCost.RateAmount);
            pcMulti.execute();
            while (pcMulti.next()) {
                final BigDecimal amount = pcMulti.<BigDecimal>getAttribute(CIConstruction.CostEstimatePercentageCost.RateAmount);
                final String costVal = pcMulti.<String>getSelect(pcSelCost);
                pcp.add(costVal, amount);
            }
            dcp.add2DataSource(ret);
            idcp.add2DataSource(ret);
            pcp.add2DataSource(ret);
            return ret;
        }

        /**
         * Gets the currency.
         *
         * @return the currency
         * @throws EFapsException on error
         */
        protected String getCurrency()
            throws EFapsException
        {
            return CurrencyInst.get(currencyId).getSymbol();
        }


        @Override
        protected void addColumnDefinition(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {
            final TextColumnBuilder<String> summaryColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateSummaryReport.class.getName() + ".Column.summaryGroup"),
                            "summaryGroup", DynamicReports.type.stringType());

            final TextColumnBuilder<String> costGroupColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateSummaryReport.class.getName() + ".Column.costGroup"),
                            "costGroup", DynamicReports.type.stringType());

            final TextColumnBuilder<String> descColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateSummaryReport.class.getName() + ".Column.description"),
                            "description", DynamicReports.type.stringType()).setWidth(500);

            final TextColumnBuilder<BigDecimal> amountColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateSummaryReport.class.getName() + ".Column.amount"),
                            "amount", DynamicReports.type.bigDecimalType());

            final PercentageColumnBuilder percentColumn = DynamicReports.col.percentageColumn(DBProperties
                            .getProperty(CostEstimateSummaryReport.class.getName() + ".Column.percent"),
                            amountColumn);

            final TextColumnBuilder<String> currencyColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateSummaryReport.class.getName() + ".Column.currency"),
                            "currency", DynamicReports.type.stringType()).setWidth(50);

            final ColumnGroupBuilder summaryGroup = DynamicReports.grp.group("sc", summaryColumn).groupByDataType()
                            .setHeaderLayout(GroupHeaderLayout.EMPTY);
            final ColumnGroupBuilder costGroup = DynamicReports.grp.group(costGroupColumn).groupByDataType();
            _builder.groupBy(summaryGroup, costGroup);

            _builder.addColumn(summaryColumn, costGroupColumn, descColumn, percentColumn, amountColumn, currencyColumn);
            _builder.addSubtotalOfPercentageAtGroupFooter(costGroup,
                            DynamicReports.sbt.percentage("amount", BigDecimal.class, percentColumn));

            _builder.addSubtotalAtGroupFooter(costGroup, DynamicReports.sbt.sum(amountColumn));

            _builder.addSubtotalAtGroupFooter(costGroup, DynamicReports.sbt.first(currencyColumn));

            _builder.addSubtotalOfPercentageAtGroupFooter(summaryGroup,
                            DynamicReports.sbt.percentage("amount", BigDecimal.class, percentColumn));
            _builder.addSubtotalAtGroupFooter(summaryGroup, DynamicReports.sbt.sum(amountColumn));
            _builder.addSubtotalAtGroupFooter(summaryGroup, DynamicReports.sbt.first(currencyColumn));

            _builder.addSubtotalAtSummary(DynamicReports.sbt.sum(amountColumn));
            _builder.addSubtotalAtSummary(DynamicReports.sbt.first(currencyColumn));
        }

        /**
         * Getter method for the instance variable {@link #filterReport}.
         *
         * @return value of instance variable {@link #filterReport}
         */
        public CostEstimateSummaryReport_Base getFilterReport()
        {
            return filterReport;
        }
    }

    /**
     * The Class AbstractSummaryPosition.
     *
     * @author The eFaps Team
     */
    public static abstract class AbstractSummaryPosition
    {

        /** The report. */
        private final SummaryReport report;

        /**
         * Instantiates a new abstract summary position.
         *
         * @param _report the report
         */
        public AbstractSummaryPosition(final SummaryReport _report) {
            report = _report;
        }

        /**
         * Gets the label.
         *
         * @param _key the key
         * @return the label
         */
        public String getLabel(final String _key)
        {
            return report.getFilterReport().getDBProperty(_key);
        }

        /**
         * Gets the currency.
         *
         * @return the currency
         * @throws EFapsException on error
         */
        public String getCurrency()
            throws EFapsException
        {
            return report.getCurrency();
        }
    }

    /**
     * The Class PercentageCostPosition.
     *
     * @author The eFaps Team
     */
    public static class PercentageCostPosition
        extends AbstractSummaryPosition
    {

        /** The amounts. */
        private final Map<String, BigDecimal> amounts = new HashMap<String, BigDecimal>();

        /**
         * Instantiates a new percentage cost position.
         *
         * @param _report the report
         */
        public PercentageCostPosition(final SummaryReport _report)
        {
            super(_report);
        }

        /**
         * Adds the.
         *
         * @param _valname the valname
         * @param _cost the cost
         */
        public void add(final String _valname,
                        final BigDecimal _cost)
        {
            if (_cost != null) {
                BigDecimal current = BigDecimal.ZERO;
                if (amounts.containsKey(_valname)) {
                    current = amounts.get(_valname);
                }
                amounts.put(_valname, current.add(_cost));
            }
        }

        /**
         * Add2 data source.
         *
         * @param _ret the ret
         * @throws EFapsException on error
         */
        public void add2DataSource(final DRDataSource _ret)
            throws EFapsException
        {
            for (final Entry<String, BigDecimal> entry : amounts.entrySet()) {
                _ret.add("s2", getLabel("AdditionalCost.Label"), entry.getKey(), BigDecimal.ZERO, entry.getValue(),
                                getCurrency());
            }
        }
    }

    /**
     * The Class IndirectCostPosition.
     *
     * @author The eFaps Team
     */
    public static class IndirectCostPosition
        extends AbstractSummaryPosition
    {

        /** The amounts. */
        private final Map<Classification, BigDecimal> amounts = new HashMap<Classification, BigDecimal>();

        /**
         * Instantiates a new indirect cost position.
         *
         * @param _report the report
         */
        public IndirectCostPosition(final SummaryReport _report)
        {
            super(_report);
        }

        /**
         * Adds the.
         *
         * @param _class the class
         * @param _cost the cost
         */
        public void add(final Classification _class,
                        final BigDecimal _cost)
        {
            BigDecimal current = BigDecimal.ZERO;
            if (amounts.containsKey(_class)) {
                current = amounts.get(_class);
            }
            amounts.put(_class, current.add(_cost));
        }

        /**
         * Add2 data source.
         *
         * @param _ret the ret
         * @throws EFapsException on error
         */
        public void add2DataSource(final DRDataSource _ret)
            throws EFapsException
        {
            for (final Entry<Classification, BigDecimal> entry : amounts.entrySet()) {
                _ret.add("s1", getLabel("IndirectCost.Label"), entry.getKey() == null ? "-" : entry.getKey().getLabel(),
                                BigDecimal.ZERO, entry.getValue(), getCurrency());
            }
        }
    }

    /**
     * The Class DirectCostPosition.
     *
     * @author The eFaps Team
     */
    public static class DirectCostPosition
        extends AbstractSummaryPosition
    {

        /** The amounts. */
        private final Map<IPositionType, BigDecimal> amounts = new HashMap<IPositionType, BigDecimal>();

        /**
         * Instantiates a new direct cost position.
         *
         * @param _report the report
         */
        public DirectCostPosition(final SummaryReport _report)
        {
            super(_report);
        }

        /**
         * Adds the.
         *
         * @param _posType the pos type
         * @param _cost the cost
         */
        public void add(final IPositionType _posType,
                        final BigDecimal _cost)
        {
            BigDecimal current = BigDecimal.ZERO;
            if (amounts.containsKey(_posType)) {
                current = amounts.get(_posType);
            }
            amounts.put(_posType, current.add(_cost));
        }

        /**
         * Add2 data source.
         *
         * @param _ret the ret
         * @throws EFapsException on error
         */
        public void add2DataSource(final DRDataSource _ret)
            throws EFapsException
        {
            if (amounts.containsKey(null)) {
                _ret.add("s1", getLabel("DirectCost.Label"), "", BigDecimal.ZERO, amounts.get(null));
            }
            for (final IPositionType posType : EntryBOM.getPositionTypes()) {
                if (amounts.containsKey(posType)) {
                    _ret.add("s1", getLabel("DirectCost.Label"), posType.getLabel(), BigDecimal.ZERO, amounts.get(
                                    posType), getCurrency());
                }
            }
        }
    }
}
