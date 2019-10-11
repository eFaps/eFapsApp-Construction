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
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
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
@EFapsUUID("22e4c775-b165-489e-a608-fb49127d96d1")
@EFapsApplication("eFapsApp-Construction")
public abstract class EntrySheetGeneralCostReport_Base
{

    public enum CostType
    {
        FIX, VARIABLE
    }

    public Return getReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final AbstractDynamicReport dyRp = getGeneralCostReport(_parameter);
        dyRp.setFileName(DBProperties.getProperty(EntrySheetGeneralCostReport.class.getName() + ".FileName"));
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
        final AbstractDynamicReport dyRp = getGeneralCostReport(_parameter);
        dyRp.setFileName(DBProperties.getProperty(EntrySheetGeneralCostReport.class.getName() + ".FileName"));
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

    protected GeneralCostReport getGeneralCostReport(final Parameter _parameter)
        throws EFapsException
    {
        return new GeneralCostReport();
    }

    public static class GeneralCostReport
        extends AbstractDynamicReport
    {

        @SuppressWarnings("unchecked")
        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final DRDataSource ret = new DRDataSource("costType", "classification", "name", "description", "uom",
                            "quantity", "participation", "time", "netUnitPrice", "totalPrice");

            final Instance instance = _parameter.getInstance();

            final Map<Instance, GeneralCost> values = new HashMap<Instance, GeneralCost>();

            final QueryBuilder prodAttrQueryBldr = new QueryBuilder(CIConstruction.ProductGeneralCostPosition);
            final AttributeQuery prodAttrQuery = prodAttrQueryBldr
                            .getAttributeQuery(CIConstruction.ProductGeneralCostPosition.ID);

            final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.EntrySheetPosition);
            queryBldr.addWhereAttrInQuery(CIConstruction.EntrySheetPosition.Product, prodAttrQuery);
            queryBldr.addWhereAttrEqValue(CIConstruction.EntrySheetPosition.EntrySheetLink, instance);

            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.addAttribute(CIConstruction.EntrySheetPosition.RateNetUnitPrice, CIConstruction.EntrySheetPosition.ProductDesc,
                            CIConstruction.EntrySheetPosition.Participation, CIConstruction.EntrySheetPosition.UoM,
                            CIConstruction.EntrySheetPosition.Time, CIConstruction.EntrySheetPosition.Quantity,
                            CIConstruction.EntrySheetPosition.RateNetPrice);

            final SelectBuilder selProdInst = SelectBuilder.get().linkto(CIConstruction.EntrySheetPosition.Product).instance();
            final SelectBuilder selProdName = SelectBuilder.get().linkto(CIConstruction.EntrySheetPosition.Product)
                            .attribute(CIProducts.ProductAbstract.Name);
            final SelectBuilder selProdClass = new SelectBuilder().linkto(CIConstruction.EntrySheetPosition.Product).clazz()
                            .type();
            multi.addSelect(selProdInst, selProdName, selProdClass);
            multi.execute();
            while (multi.next()) {
                final Instance prodInst = multi.<Instance>getSelect(selProdInst);
                GeneralCost gc;
                if (values.containsKey(prodInst)) {
                    gc = values.get(prodInst);
                    gc.setMultiple(true);
                    gc.addDescription(multi.<String>getAttribute(CIConstruction.EntrySheetPosition.ProductDesc));
                    gc.addQuantity(multi.<BigDecimal>getAttribute(CIConstruction.EntrySheetPosition.Quantity));
                    gc.addTime(multi.<BigDecimal>getAttribute(CIConstruction.EntrySheetPosition.Time));
                    gc.addTotalPrice(multi.<BigDecimal>getAttribute(CIConstruction.EntrySheetPosition.RateNetPrice));
                } else {
                    gc = getGeneralCost(_parameter);
                    values.put(prodInst, gc);
                    gc.setName(multi.<String>getSelect(selProdName));
                    gc.setQuantity(multi.<BigDecimal>getAttribute(CIConstruction.EntrySheetPosition.Quantity));
                    gc.setTime(multi.<BigDecimal>getAttribute(CIConstruction.EntrySheetPosition.Time));
                    gc.setDescription(multi.<String>getAttribute(CIConstruction.EntrySheetPosition.ProductDesc));
                    gc.setNetUnitPrice(multi.<BigDecimal>getAttribute(CIConstruction.EntrySheetPosition.RateNetUnitPrice));
                    gc.setParticipation(multi.<BigDecimal>getAttribute(CIConstruction.EntrySheetPosition.Participation));
                    gc.setTotalPrice(multi.<BigDecimal>getAttribute(CIConstruction.EntrySheetPosition.RateNetPrice));
                    gc.setUom(Dimension.getUoM(multi.<Long>getAttribute(CIConstruction.EntrySheetPosition.UoM)));
                    final List<Classification> clazzes = multi.<List<Classification>>getSelect(selProdClass);
                    if (clazzes != null && !clazzes.isEmpty()) {
                        gc.setClassifcation(clazzes.get(0).getLabel());
                    }
                }
            }
            final List<GeneralCost> vals = new ArrayList<GeneralCost>();
            vals.addAll(values.values());

            final List<Comparator<GeneralCost>> comparators = new ArrayList<Comparator<GeneralCost>>();
            comparators.add((_prod0,
             _prod1) -> _prod0.getCostType().toString().compareTo(_prod1.getCostType().toString()));

            comparators.add((_prod0,
             _prod1) -> {
                final String prod0Str = _prod0.getClassifcation() == null ? "" : _prod0.getClassifcation();
                return prod0Str.compareTo(_prod1.getClassifcation());
            });

            comparators.add((_prod0,
             _prod1) -> _prod0.getName().compareTo(_prod1.getName()));

            Collections.sort(vals, ComparatorUtils.chainedComparator(comparators));

            for (final GeneralCost gc : vals) {
                ret.add(gc.getCostType().toString(), gc.getClassifcation(), gc.getName(), gc.getDescription(),
                                gc.getUoMLabel(), gc.getQuantity(), gc.getParticipation(), gc.getTime(),
                                gc.getNetUnitPrice(), gc.getTotalPrice());
            }

            return ret;
        }

        @Override
        protected void addColumnDefinition(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {
            final TextColumnBuilder<String> costTypeColumn = DynamicReports.col.column(DBProperties
                            .getProperty(EntrySheetGeneralCostReport.class.getName() + ".Column.costType"),
                            "costType", DynamicReports.type.stringType());
            final TextColumnBuilder<String> classColumn = DynamicReports.col.column(DBProperties
                            .getProperty(EntrySheetGeneralCostReport.class.getName() + ".Column.classification"),
                            "classification", DynamicReports.type.stringType());
            final TextColumnBuilder<String> nameColumn = DynamicReports.col.column(DBProperties
                            .getProperty(EntrySheetGeneralCostReport.class.getName() + ".Column.name"),
                            "name", DynamicReports.type.stringType());
            final TextColumnBuilder<String> descColumn = DynamicReports.col.column(DBProperties
                            .getProperty(EntrySheetGeneralCostReport.class.getName() + ".Column.description"),
                            "description", DynamicReports.type.stringType()).setWidth(500);
            final TextColumnBuilder<String> uomColumn = DynamicReports.col.column(DBProperties
                            .getProperty(EntrySheetGeneralCostReport.class.getName() + ".Column.uom"),
                            "uom", DynamicReports.type.stringType());
            final TextColumnBuilder<BigDecimal> quantityColumn = DynamicReports.col.column(DBProperties
                            .getProperty(EntrySheetGeneralCostReport.class.getName() + ".Column.quantity"),
                            "quantity", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> participationColumn = DynamicReports.col.column(DBProperties
                            .getProperty(EntrySheetGeneralCostReport.class.getName() + ".Column.participation"),
                            "participation", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> timeColumn = DynamicReports.col.column(DBProperties
                            .getProperty(EntrySheetGeneralCostReport.class.getName() + ".Column.time"),
                            "time", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> netUnitPriceColumn = DynamicReports.col.column(DBProperties
                            .getProperty(EntrySheetGeneralCostReport.class.getName() + ".Column.netUnitPrice"),
                            "netUnitPrice", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> totalPriceColumn = DynamicReports.col.column(DBProperties
                            .getProperty(EntrySheetGeneralCostReport.class.getName() + ".Column.totalPrice"),
                            "totalPrice", DynamicReports.type.bigDecimalType());

            _builder.addColumn(costTypeColumn, classColumn, nameColumn, descColumn, uomColumn, quantityColumn,
                            participationColumn, timeColumn, netUnitPriceColumn, totalPriceColumn);

            final ColumnGroupBuilder costTypeGroup = DynamicReports.grp.group("sc", costTypeColumn).groupByDataType();
            final ColumnGroupBuilder classGroup = DynamicReports.grp.group("2", classColumn).groupByDataType();
            _builder.groupBy(costTypeGroup, classGroup);

            _builder.addSubtotalOfPercentageAtGroupFooter(classGroup,
                            DynamicReports.sbt.percentage("totalPrice", BigDecimal.class, netUnitPriceColumn));

            _builder.addSubtotalOfPercentageAtGroupFooter(costTypeGroup,
                            DynamicReports.sbt.percentage("totalPrice", BigDecimal.class, netUnitPriceColumn));

            _builder.addSubtotalAtGroupFooter(costTypeGroup, DynamicReports.sbt.sum(totalPriceColumn));
            _builder.addSubtotalAtGroupFooter(classGroup, DynamicReports.sbt.sum(totalPriceColumn));

            _builder.addSubtotalAtSummary(DynamicReports.sbt.sum(totalPriceColumn));
        }

        public GeneralCost getGeneralCost(final Parameter _parameter)
        {
            return new GeneralCost();
        }

    }

    public static class GeneralCost
    {
        private boolean multiple;

        private CostType costType;

        private String classifcation;

        private String name;

        private String description;

        private UoM uom;

        private BigDecimal quantity;

        private BigDecimal participation;
        private BigDecimal time;
        private BigDecimal netUnitPrice;
        private BigDecimal totalPrice;

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
         * @param _attribute
         */
        public void addTotalPrice(final BigDecimal _totalPrice)
        {
            totalPrice = totalPrice.add(_totalPrice);
        }

        /**
         * @param _attribute
         */
        public void addTime(final BigDecimal _time)
        {
           time = time.add(_time);
        }

        /**
         * @param _attribute
         */
        public void addQuantity(final BigDecimal _quantity)
        {
            quantity = quantity.add(_quantity);
        }

        /**
         * @param _describtion description to add
         */
        public void addDescription(final String _describtion)
        {
            if (!getDescription().contains(_describtion)) {
                setDescription(getDescription() + ", " + _describtion);
            }
        }

        /**
         * @return
         */
        public String getUoMLabel()
        {
            String ret;
            if (uom == null) {
                ret = "";
            } else {
                ret = uom.getName();
            }
            return ret;
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
         * Getter method for the instance variable {@link #uom}.
         *
         * @return value of instance variable {@link #uom}
         */
        public UoM getUom()
        {
            return uom;
        }

        /**
         * Setter method for instance variable {@link #uom}.
         *
         * @param _uom value for instance variable {@link #uom}
         */
        public void setUom(final UoM _uom)
        {
            uom = _uom;
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
         */
        public void setQuantity(final BigDecimal _quantity)
        {
            quantity = _quantity;
        }

        /**
         * Getter method for the instance variable {@link #participation}.
         *
         * @return value of instance variable {@link #participation}
         */
        public BigDecimal getParticipation()
        {
            BigDecimal ret = null;
            if (!isMultiple()) {
                ret = participation;
            }
            return ret;
        }

        /**
         * Setter method for instance variable {@link #participation}.
         *
         * @param _participation value for instance variable
         *            {@link #participation}
         */
        public void setParticipation(final BigDecimal _participation)
        {
            participation = _participation;
        }

        /**
         * Getter method for the instance variable {@link #time}.
         *
         * @return value of instance variable {@link #time}
         */
        public BigDecimal getTime()
        {
            return time;
        }

        /**
         * Setter method for instance variable {@link #time}.
         *
         * @param _time value for instance variable {@link #time}
         */
        public void setTime(final BigDecimal _time)
        {
            time = _time;
        }

        /**
         * Getter method for the instance variable {@link #netUnitPrice}.
         *
         * @return value of instance variable {@link #netUnitPrice}
         */
        public BigDecimal getNetUnitPrice()
        {
            BigDecimal ret;
            if (isMultiple()) {
                ret = getTotalPrice().divide(getTime().multiply(getQuantity()), BigDecimal.ROUND_HALF_UP);
            } else {
                ret = netUnitPrice;
            }
            return ret;
        }

        /**
         * Setter method for instance variable {@link #netUnitPrice}.
         *
         * @param _netUnitPrice value for instance variable
         *            {@link #netUnitPrice}
         */
        public void setNetUnitPrice(final BigDecimal _netUnitPrice)
        {
            netUnitPrice = _netUnitPrice;
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
         */
        public void setTotalPrice(final BigDecimal _totalPrice)
        {
            totalPrice = _totalPrice;
        }

        /**
         * Getter method for the instance variable {@link #classifcation}.
         *
         * @return value of instance variable {@link #classifcation}
         */
        public String getClassifcation()
        {
            return classifcation;
        }

        /**
         * Setter method for instance variable {@link #classifcation}.
         *
         * @param _classifcation value for instance variable
         *            {@link #classifcation}
         */
        public void setClassifcation(final String _classifcation)
        {
            classifcation = _classifcation;
        }

        /**
         * Getter method for the instance variable {@link #costType}.
         *
         * @return value of instance variable {@link #costType}
         */
        public CostType getCostType()
        {
            if (costType == null) {
                if (BigDecimal.ONE.compareTo(getQuantity()) == 0) {
                    costType = CostType.FIX;
                } else {
                    costType = CostType.VARIABLE;
                }
            }
            return costType;
        }

        /**
         * Setter method for instance variable {@link #costType}.
         *
         * @param _costType value for instance variable {@link #costType}
         */
        public void setCostType(final CostType _costType)
        {
            costType = _costType;
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
         * Getter method for the instance variable {@link #multiple}.
         *
         * @return value of instance variable {@link #multiple}
         */
        public boolean isMultiple()
        {
            return multiple;
        }


        /**
         * Setter method for instance variable {@link #multiple}.
         *
         * @param _multiple value for instance variable {@link #multiple}
         */
        public void setMultiple(final boolean _multiple)
        {
            multiple = _multiple;
        }
    }
}
