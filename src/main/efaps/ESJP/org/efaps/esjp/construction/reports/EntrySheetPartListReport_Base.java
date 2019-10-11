/*
 * Copyright 2007 - 2015 Jan Moxter
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

import org.apache.commons.collections4.ComparatorUtils;
import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Dimension;
import org.efaps.admin.datamodel.Dimension.UoM;
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
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.construction.EntryBOM;
import org.efaps.esjp.construction.IPositionType;
import org.efaps.esjp.construction.PositionType;
import org.efaps.util.EFapsException;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.base.expression.AbstractSimpleExpression;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.component.HorizontalListBuilder;
import net.sf.dynamicreports.report.builder.component.SubreportBuilder;
import net.sf.dynamicreports.report.builder.component.TextFieldBuilder;
import net.sf.dynamicreports.report.builder.group.ColumnGroupBuilder;
import net.sf.dynamicreports.report.builder.style.Styles;
import net.sf.dynamicreports.report.builder.subtotal.AggregationSubtotalBuilder;
import net.sf.dynamicreports.report.constant.HorizontalTextAlignment;
import net.sf.dynamicreports.report.constant.PageOrientation;
import net.sf.dynamicreports.report.constant.PageType;
import net.sf.dynamicreports.report.datasource.DRDataSource;
import net.sf.dynamicreports.report.definition.ReportParameters;
import net.sf.jasperreports.engine.JRDataSource;

/**
 * TODO comment!
 *
 * @author Jan Moxter
 * @version $Id: EntrySheetPartListReport_Base.java 165 2014-03-07 21:42:15Z
 *          jan.moxter $
 */
@EFapsUUID("43e4cfd7-e260-4e7b-9542-4e0125602954")
@EFapsApplication("eFapsApp-Construction")
public abstract class EntrySheetPartListReport_Base
{

    private static final Integer WIDTH = 900;

    public Return getReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final AbstractDynamicReport dyRp = getPartListReport(_parameter);
        dyRp.setFileName(DBProperties.getProperty(EntrySheetPartListReport.class.getName() + ".FileName"));
        final String html = dyRp.getHtml(_parameter);
        ret.put(ReturnValues.SNIPLETT, html);
        return ret;
    }

    public Return exportReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String mime = (String) props.get("Mime");
        final AbstractDynamicReport dyRp = getPartListReport(_parameter);
        dyRp.setFileName(DBProperties.getProperty(EntrySheetPartListReport.class.getName() + ".FileName"));
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

    protected PartListReport getPartListReport(final Parameter _parameter)
        throws EFapsException
    {
        return new PartListReport();
    }

    public static class PartListReport
        extends AbstractDynamicReport
    {

        protected List<PartList> partLists = new ArrayList<PartList>();

        @Override
        public String getHtml(final Parameter _parameter,
                              final boolean _strip)
            throws EFapsException
        {
            final SubreportBuilder subreport = DynamicReports.cmp.subreport(getSubreportExpression(_parameter, this))
                            .setDataSource(getDataSourceExpression(_parameter))
                            .setWidth(EntrySheetPartListReport_Base.WIDTH);
            getReport().detail(subreport, DynamicReports.cmp.verticalGap(20));
            return super.getHtml(_parameter, _strip);
        }

        @Override
        public File getExcel(final Parameter _parameter)
            throws EFapsException
        {
            final SubreportBuilder subreport = DynamicReports.cmp.subreport(getSubreportExpression(_parameter, this))
                            .setDataSource(getDataSourceExpression(_parameter))
                            .setWidth(EntrySheetPartListReport_Base.WIDTH);
            getReport().detail(subreport, DynamicReports.cmp.verticalGap(20));
            return super.getExcel(_parameter);
        }

        @Override
        public File getPDF(final Parameter _parameter)
            throws EFapsException
        {
            final SubreportBuilder subreport = DynamicReports.cmp.subreport(getSubreportExpression(_parameter, this))
                            .setDataSource(getDataSourceExpression(_parameter))
                            .setWidth(EntrySheetPartListReport_Base.WIDTH);
            getReport().detail(subreport, DynamicReports.cmp.verticalGap(20));
            return super.getPDF(_parameter);
        }

        @Override
        protected void configure4Pdf(final Parameter _parameter)
            throws EFapsException
        {
            super.configure4Pdf(_parameter);
            getStyleTemplate().setIgnorePageWidth(true);
            getReport().setPageFormat(EntrySheetPartListReport_Base.WIDTH,
                            PageType.A4.getHeight(), PageOrientation.PORTRAIT);
        }

        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final DRDataSource ret = new DRDataSource("partlist");

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
            final SelectBuilder selEplName = new SelectBuilder(selEpl).attribute(CIConstruction.EntryPartList.Name);
            final SelectBuilder selEplDesc = new SelectBuilder(selEpl).attribute(CIConstruction.EntryPartList.Description);
            final SelectBuilder selEplEffOA = new SelectBuilder(selEpl).clazz(CIConstruction.EntryPartList_Class).attribute(
                            CIConstruction.EntryPartList_Class.EfficiencyOverAll);
            final SelectBuilder selEplEffPers = new SelectBuilder(selEpl).clazz(CIConstruction.EntryPartList_Class).attribute(
                            CIConstruction.EntryPartList_Class.EfficiencyPersonal);
            final SelectBuilder selEplEffTools = new SelectBuilder(selEpl).clazz(CIConstruction.EntryPartList_Class).attribute(
                            CIConstruction.EntryPartList_Class.EfficiencyTools);
            final SelectBuilder selEplEffUoM = new SelectBuilder(selEpl).clazz(CIConstruction.EntryPartList_Class).attribute(
                            CIConstruction.EntryPartList_Class.EfficiencyUoM);
            final SelectBuilder selEplTotal = new SelectBuilder(selEpl).clazz(CIConstruction.EntryPartList_Class).attribute(
                            CIConstruction.EntryPartList_Class.Total);
            final SelectBuilder selEplMAmount = new SelectBuilder(selEpl).clazz(CIConstruction.EntryPartList_Class).attribute(
                            CIConstruction.EntryPartList_Class.ManualAmount);
            final SelectBuilder selEplMPer = new SelectBuilder(selEpl).clazz(CIConstruction.EntryPartList_Class).attribute(
                            CIConstruction.EntryPartList_Class.ManualPercent);

            final SelectBuilder selProd = SelectBuilder.get().linkto(CIConstruction.EntryBOM.ToLink);
            final SelectBuilder selProdInst = new SelectBuilder(selProd).instance();
            final SelectBuilder selProdName = new SelectBuilder(selProd).attribute(CIProducts.ProductAbstract.Name);
            final SelectBuilder selProdDesc = new SelectBuilder(selProd)
                            .attribute(CIProducts.ProductAbstract.Description);
            final SelectBuilder selProdClass = new SelectBuilder(selProd).clazz().type();
            bomMulti.addSelect(selEplInst, selEplName, selEplDesc, selEplEffOA, selEplEffPers, selEplEffTools,
                            selEplEffUoM, selEplTotal, selEplMAmount, selEplMPer, selProdInst, selProdName,
                            selProdDesc, selProdClass);
            bomMulti.addAttribute(CIConstruction.EntryBOM.Quantity, CIConstruction.EntryBOM.Factor,
                            CIConstruction.EntryBOM.Price, CIConstruction.EntryBOM.UnitPrice, CIConstruction.EntryBOM.UoM, CIConstruction.EntryBOM.Remark);
            bomMulti.execute();

            final Map<Instance, PartList> map = new HashMap<Instance, PartList>();
            while (bomMulti.next()) {
                final Instance partListInst = bomMulti.<Instance>getSelect(selEplInst);
                PartList partList;
                if (map.containsKey(partListInst)) {
                    partList = map.get(partListInst);
                } else {
                    partList = new PartList(partListInst);
                    map.put(partListInst, partList);
                    partLists.add(partList);
                    partList.setName(bomMulti.<String>getSelect(selEplName));
                    partList.setDescription(bomMulti.<String>getSelect(selEplDesc));
                    partList.setTotal(bomMulti.<BigDecimal>getSelect(selEplTotal));
                    partList.setManualAmount(bomMulti.<BigDecimal>getSelect(selEplMAmount));
                    partList.setManualPercent(bomMulti.<BigDecimal>getSelect(selEplMPer));
                    final Long uomId = bomMulti.<Long>getSelect(selEplEffUoM);
                    if (uomId != null) {
                        final UoM uoM = Dimension.getUoM(uomId);
                        partList.setEfficencyUoM(uoM == null ? "" : uoM.getName());
                        final BigDecimal effOA = bomMulti.<BigDecimal>getSelect(selEplEffOA);
                        if (effOA != null) {
                            partList.setEfficencyOverAll(effOA);
                        }
                        final BigDecimal effPers = bomMulti.<BigDecimal>getSelect(selEplEffPers);
                        if (effPers != null) {
                            partList.setEfficencyPersonal(effPers);
                        }
                        final BigDecimal effTools = bomMulti.<BigDecimal>getSelect(selEplEffTools);
                        if (effTools != null) {
                            partList.setEfficencyTools(effTools);
                        }
                    }
                }
                final Bom bom = new Bom();
                partList.getBoms().add(bom);
                bom.setQuantity(bomMulti.<BigDecimal>getAttribute(CIConstruction.EntryBOM.Quantity));
                bom.setFactor(bomMulti.<BigDecimal>getAttribute(CIConstruction.EntryBOM.Factor));
                bom.setUnitPrice(bomMulti.<BigDecimal>getAttribute(CIConstruction.EntryBOM.UnitPrice));
                bom.setPrice(bomMulti.<BigDecimal>getAttribute(CIConstruction.EntryBOM.Price));
                final String descr = bomMulti.<String>getSelect(selProdDesc);
                final String remark = bomMulti.<String>getAttribute(CIConstruction.EntryBOM.Remark);
                bom.setDescription(descr + (remark == null ? "" : " " + remark));
                bom.setName(bomMulti.<String>getSelect(selProdName));
                bom.setUom(Dimension.getUoM(bomMulti.<Long>getAttribute(CIConstruction.EntryBOM.UoM)).getName());

                final Instance prodInst = bomMulti.<Instance>getSelect(selProdInst);
                bom.setPositionType(EntryBOM.getPositionType(_parameter, prodInst,
                                bomMulti.<List<Classification>>getSelect(selProdClass)));
            }
            Collections.sort(partLists, (_pl0,
             _pl1) -> _pl0.getName().compareTo(_pl1.getName()));
            for (final PartList prtLst : partLists) {
                ret.add(prtLst);
            }
            return ret;
        }

        @Override
        protected void addColumnDefinition(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {
            // must be added as Object class, if not the jaspercompiler
            // will not find the inner class in case of deployment
            _builder.addField("partlist", Object.class);
        }

        protected SubreportExpression getSubreportExpression(final Parameter _parameter,
                                                             final PartListReport _plReport)
            throws EFapsException
        {

            return new SubreportExpression(_plReport);
        }

        protected SubreportDataSourceExpression getDataSourceExpression(final Parameter _parameter)
            throws EFapsException
        {

            return new SubreportDataSourceExpression();
        }

    }

    public static class SubreportExpression
        extends AbstractSimpleExpression<JasperReportBuilder>
    {

        private final PartListReport partListReport;

        public SubreportExpression(final PartListReport _plReport)
        {
            partListReport = _plReport;
        }

        private static final long serialVersionUID = 1L;

        @Override
        public JasperReportBuilder evaluate(final ReportParameters _reportParameters)
        {
            final PartList partlist = (PartList) _reportParameters.getFieldValue("partlist");

            final JasperReportBuilder report = DynamicReports.report();
            report.setTemplate(partListReport.getStyleTemplate());

            final TextFieldBuilder<String> partListLabel = DynamicReports.cmp.text(
                            DBProperties.getProperty(EntrySheetPartListReport.class.getName() + ".label.partlist"))
                            .setStyle(DynamicReports.stl.style().setFontSize(12))
                            .setWidth(20);
            final TextFieldBuilder<String> partListName = DynamicReports.cmp.text(partlist.getName())
                            .setWidth(20).setStretchWithOverflow(true)
                            .setStyle(DynamicReports.stl.style()
                                            .setHorizontalTextAlignment(HorizontalTextAlignment.RIGHT)
                                            .setFontSize(12).setBold(true).setPadding(Styles.padding().setRight(5)));
            final TextFieldBuilder<String> partListDescr = DynamicReports.cmp.text(partlist.getDescription())
                            .setStyle(DynamicReports.stl.style().setFontSize(12).setBold(true));

            final HorizontalListBuilder ls1 = DynamicReports.cmp.horizontalList(
                            partListLabel, partListName, partListDescr
                            ).setWidth(EntrySheetPartListReport_Base.WIDTH);

            final TextFieldBuilder<String> wDayLabel = DynamicReports.cmp.text(DBProperties
                            .getProperty(EntrySheetPartListReport.class.getName()
                                            + ".label.workingday")).setWidth(30);
            final TextFieldBuilder<BigDecimal> hours = DynamicReports.cmp.text(new BigDecimal(8)).setPattern(
                            "#.## h/dia").setWidth(30);

            final TextFieldBuilder<String> effOALabel = DynamicReports.cmp.text(DBProperties
                            .getProperty(EntrySheetPartListReport.class.getName() + ".label.efficiencyOverAll"));
            final TextFieldBuilder<BigDecimal> effOA = DynamicReports.cmp.text(partlist.getEfficencyOverAll())
                            .setPattern("#.#### ").setWidth(25);

            final TextFieldBuilder<String> effPersLabel = DynamicReports.cmp.text(DBProperties
                            .getProperty(EntrySheetPartListReport.class.getName() + ".label.efficiencyPersonal"));
            final TextFieldBuilder<BigDecimal> effPers = DynamicReports.cmp.text(partlist.getEfficencyPersonal())
                            .setPattern("#.#### " + partlist.getEfficencyUoM()).setWidth(40);

            final TextFieldBuilder<String> effToolLabel = DynamicReports.cmp.text(DBProperties
                            .getProperty(EntrySheetPartListReport.class.getName() + ".label.efficiencyTools"));
            final TextFieldBuilder<BigDecimal> effTool = DynamicReports.cmp.text(partlist.getEfficencyTools())
                            .setPattern("#.#### " + partlist.getEfficencyUoM()).setWidth(40);

            final TextFieldBuilder<String> totalLabel = DynamicReports.cmp.text(DBProperties
                            .getProperty(EntrySheetPartListReport.class.getName() + ".label.total"));
            final TextFieldBuilder<BigDecimal> total = DynamicReports.cmp.text(partlist.getTotal())
                            .setStyle(DynamicReports.stl.style().setBold(true));

            final HorizontalListBuilder ls2 = DynamicReports.cmp.horizontalList(wDayLabel, hours,
                            effOALabel, effOA, effPersLabel, effPers, effToolLabel, effTool, totalLabel, total
                            ).setWidth(EntrySheetPartListReport_Base.WIDTH);

            report.title(ls1, ls2);

            final TextColumnBuilder<String> typeColumn = DynamicReports.col.column(DBProperties
                            .getProperty(EntrySheetPartListReport.class.getName() + ".Column.type"),
                            "type", DynamicReports.type.stringType());
            final TextColumnBuilder<String> nameColumn = DynamicReports.col.column(DBProperties
                            .getProperty(EntrySheetPartListReport.class.getName() + ".Column.name"),
                            "name", DynamicReports.type.stringType()).setWidth(30);
            final TextColumnBuilder<String> descriptionColumn = DynamicReports.col.column(DBProperties
                            .getProperty(EntrySheetPartListReport.class.getName() + ".Column.description"),
                            "description", DynamicReports.type.stringType());
            final TextColumnBuilder<String> uomColumn = DynamicReports.col.column(DBProperties
                            .getProperty(EntrySheetPartListReport.class.getName() + ".Column.uom"),
                            "uom", DynamicReports.type.stringType()).setWidth(15);
            final TextColumnBuilder<BigDecimal> quantityColumn = DynamicReports.col.column(DBProperties
                            .getProperty(EntrySheetPartListReport.class.getName() + ".Column.quantity"),
                            "quantity", DynamicReports.type.bigDecimalType()).setWidth(20);
            final TextColumnBuilder<BigDecimal> factorColumn = DynamicReports.col.column(DBProperties
                            .getProperty(EntrySheetPartListReport.class.getName() + ".Column.factor"),
                            "factor", DynamicReports.type.bigDecimalType()).setWidth(20);
            final TextColumnBuilder<BigDecimal> unitpriceColumn = DynamicReports.col.column(DBProperties
                            .getProperty(EntrySheetPartListReport.class.getName() + ".Column.unitprice"),
                            "unitprice", DynamicReports.type.bigDecimalType()).setWidth(20);
            final TextColumnBuilder<BigDecimal> priceColumn = DynamicReports.col.column(DBProperties
                            .getProperty(EntrySheetPartListReport.class.getName() + ".Column.price"),
                            "price", DynamicReports.type.bigDecimalType()).setWidth(20);

            final ColumnGroupBuilder typeGroup = DynamicReports.grp.group(typeColumn).groupByDataType();
            report.groupBy(typeGroup);
            report.addColumn(typeColumn, nameColumn, descriptionColumn, uomColumn,
                            factorColumn, quantityColumn, unitpriceColumn, priceColumn);

            final AggregationSubtotalBuilder<BigDecimal> priceSum = DynamicReports.sbt.sum(priceColumn);
            report.addSubtotalAtGroupFooter(typeGroup, priceSum);
            return report;
        }

        /**
         * Getter method for the instance variable {@link #partListReport}.
         *
         * @return value of instance variable {@link #partListReport}
         */
        public PartListReport getPartListReport()
        {
            return partListReport;
        }
    }

    public static class SubreportDataSourceExpression
        extends AbstractSimpleExpression<JRDataSource>
    {

        private static final long serialVersionUID = 1L;

        @SuppressWarnings("unchecked")
        @Override
        public JRDataSource evaluate(final ReportParameters _reportParameters)
        {
            final PartList partlist = (PartList) _reportParameters.getFieldValue("partlist");

            final DRDataSource dataSource = new DRDataSource("type", "name", "description", "uom", "factor",
                            "quantity", "unitprice", "price");

            final List<Bom> boms = partlist.getBoms();

            if (partlist.getManualAmount() != null && partlist.getManualAmount().compareTo(BigDecimal.ZERO) != 0) {
                final Bom manBom = new Bom();
                manBom.setName("-");
                manBom.setDescription(DBProperties.getProperty(EntrySheetPartListReport.class.getName()
                                + ".Description.Manual"));
                manBom.setPositionType(PositionType.TOOLS);
                manBom.setQuantity(partlist.getManualPercent());
                manBom.setPrice(partlist.getManualAmount());
                boms.add(manBom);
            }

            final List<Comparator<Bom>> comparators = new ArrayList<Comparator<Bom>>();
            comparators.add((_bom0,
             _bom1) -> Integer.valueOf(_bom0.getPositionType().getPosition()).compareTo(
                            Integer.valueOf(_bom1.getPositionType().getPosition())));

            comparators.add((_bom0,
             _bom1) -> _bom0.getName().compareTo(_bom1.getName()));

            Collections.sort(boms, ComparatorUtils.chainedComparator(comparators));

            for (final Bom bom : boms) {
                dataSource.add(bom.getTypeLabel(), bom.getName(), bom.getDescription(), bom.getUom(),
                                bom.getFactor(), bom.getQuantity(), bom.getUnitPrice(), bom.getPrice());
            }
            return dataSource;
        }
    }

    public static class PartList
    {

        private String name;
        private String description;
        private BigDecimal total;
        private BigDecimal manualAmount;
        private BigDecimal manualPercent;
        private String efficencyUoM;
        private BigDecimal efficencyPersonal;
        private BigDecimal efficencyTools;
        private BigDecimal efficencyOverAll;
        private Instance instance;
        private final List<Bom> boms = new ArrayList<Bom>();

        /**
         * @param _currentInstance
         */
        public PartList(final Instance _currentInstance)
        {
            instance = _currentInstance;
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
         */
        public void setName(final String _name)
        {
            name = _name;
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
         * Setter method for instance variable {@link #instance}.
         *
         * @param _instance value for instance variable {@link #instance}
         */
        public void setInstance(final Instance _instance)
        {
            instance = _instance;
        }

        /**
         * Getter method for the instance variable {@link #boms}.
         *
         * @return value of instance variable {@link #boms}
         */
        public List<Bom> getBoms()
        {
            return boms;
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
         */
        public void setDescription(final String _description)
        {
            description = _description;
        }

        /**
         * Getter method for the instance variable {@link #total}.
         *
         * @return value of instance variable {@link #total}
         */
        public BigDecimal getTotal()
        {
            return total;
        }

        /**
         * Setter method for instance variable {@link #total}.
         *
         * @param _total value for instance variable {@link #total}
         */
        public void setTotal(final BigDecimal _total)
        {
            total = _total;
        }

        /**
         * Getter method for the instance variable {@link #efficencyUoM}.
         *
         * @return value of instance variable {@link #efficencyUoM}
         */
        public String getEfficencyUoM()
        {
            return efficencyUoM;
        }

        /**
         * Setter method for instance variable {@link #efficencyUoM}.
         *
         * @param _efficencyUoM value for instance variable
         *            {@link #efficencyUoM}
         */
        public void setEfficencyUoM(final String _efficencyUoM)
        {
            efficencyUoM = _efficencyUoM;
        }

        /**
         * Getter method for the instance variable {@link #manualAmount}.
         *
         * @return value of instance variable {@link #manualAmount}
         */
        public BigDecimal getManualAmount()
        {
            return manualAmount;
        }

        /**
         * Setter method for instance variable {@link #manualAmount}.
         *
         * @param _manualAmount value for instance variable
         *            {@link #manualAmount}
         */
        public void setManualAmount(final BigDecimal _manualAmount)
        {
            manualAmount = _manualAmount;
        }

        /**
         * Getter method for the instance variable {@link #manualPercent}.
         *
         * @return value of instance variable {@link #manualPercent}
         */
        public BigDecimal getManualPercent()
        {
            return manualPercent;
        }

        /**
         * Setter method for instance variable {@link #manualPercent}.
         *
         * @param _manualPercent value for instance variable
         *            {@link #manualPercent}
         */
        public void setManualPercent(final BigDecimal _manualPercent)
        {
            manualPercent = _manualPercent;
        }

        /**
         * Getter method for the instance variable {@link #efficencyTools}.
         *
         * @return value of instance variable {@link #efficencyTools}
         */
        public BigDecimal getEfficencyTools()
        {
            return efficencyTools;
        }

        /**
         * Setter method for instance variable {@link #efficencyTools}.
         *
         * @param _efficencyTools value for instance variable
         *            {@link #efficencyTools}
         */
        public void setEfficencyTools(final BigDecimal _efficencyTools)
        {
            efficencyTools = _efficencyTools;
        }

        /**
         * Getter method for the instance variable {@link #efficencyPersonal}.
         *
         * @return value of instance variable {@link #efficencyPersonal}
         */
        public BigDecimal getEfficencyPersonal()
        {
            return efficencyPersonal;
        }

        /**
         * Setter method for instance variable {@link #efficencyPersonal}.
         *
         * @param _efficencyPersonal value for instance variable
         *            {@link #efficencyPersonal}
         */
        public void setEfficencyPersonal(final BigDecimal _efficencyPersonal)
        {
            efficencyPersonal = _efficencyPersonal;
        }

        /**
         * Getter method for the instance variable {@link #efficencyOverAll}.
         *
         * @return value of instance variable {@link #efficencyOverAll}
         */
        public BigDecimal getEfficencyOverAll()
        {
            return efficencyOverAll;
        }

        /**
         * Setter method for instance variable {@link #efficencyOverAll}.
         *
         * @param _efficencyOverAll value for instance variable
         *            {@link #efficencyOverAll}
         */
        public void setEfficencyOverAll(final BigDecimal _efficencyOverAll)
        {
            efficencyOverAll = _efficencyOverAll;
        }

    }

    public static class Bom
    {

        private IPositionType positionType = PositionType.MATERIAL;
        private BigDecimal quantity;
        private BigDecimal factor;
        private BigDecimal price;
        private BigDecimal unitPrice;
        private String uom;
        private String name;
        private String description;

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
         */
        public void setQuantity(final BigDecimal _quantity)
        {
            quantity = _quantity;
        }

        /**
         * Getter method for the instance variable {@link #factor}.
         *
         * @return value of instance variable {@link #factor}
         */
        public BigDecimal getFactor()
        {
            return factor;
        }

        /**
         * Setter method for instance variable {@link #factor}.
         *
         * @param _factor value for instance variable {@link #factor}
         */
        public void setFactor(final BigDecimal _factor)
        {
            factor = _factor;
        }

        /**
         * Getter method for the instance variable {@link #price}.
         *
         * @return value of instance variable {@link #price}
         */
        public BigDecimal getPrice()
        {
            return price;
        }

        /**
         * Setter method for instance variable {@link #price}.
         *
         * @param _price value for instance variable {@link #price}
         */
        public void setPrice(final BigDecimal _price)
        {
            price = _price;
        }

        /**
         * Getter method for the instance variable {@link #unitPrice}.
         *
         * @return value of instance variable {@link #unitPrice}
         */
        public BigDecimal getUnitPrice()
        {
            return unitPrice;
        }

        /**
         * Setter method for instance variable {@link #unitPrice}.
         *
         * @param _unitPrice value for instance variable {@link #unitPrice}
         */
        public void setUnitPrice(final BigDecimal _unitPrice)
        {
            unitPrice = _unitPrice;
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
         */
        public void setUom(final String _uom)
        {
            uom = _uom;
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
         */
        public void setName(final String _name)
        {
            name = _name;
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
         */
        public void setDescription(final String _description)
        {
            description = _description;
        }

        /**
         * Getter method for the instance variable {@link #type}.
         *
         * @return value of instance variable {@link #type}
         */
        public String getTypeLabel()
        {
            return positionType.getLabel();
        }

        /**
         * Setter method for instance variable {@link #type}.
         *
         * @param _type value for instance variable {@link #type}
         */
        public void setPositionType(final IPositionType _type)
        {
            positionType = _type;
        }

        /**
         * Getter method for the instance variable {@link #positionType}.
         *
         * @return value of instance variable {@link #positionType}
         */
        public IPositionType getPositionType()
        {
            return positionType;
        }
    }
}
