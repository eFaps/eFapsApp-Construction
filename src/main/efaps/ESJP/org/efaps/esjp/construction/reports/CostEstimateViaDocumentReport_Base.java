/*
 * Copyright 2007 - 2015 moxter.net
 *
 * All Rights Reserved.
 * This program contains proprietary and trade secret information of
 * moxter.net. Copyright notice is precautionary only and does not
 * evidence any actual or intended publication of such program.
 *
 */

package org.efaps.esjp.construction.reports;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.comparators.ComparatorChain;
import org.apache.commons.lang3.BooleanUtils;
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
import org.efaps.esjp.construction.util.Construction;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.FilteredReport;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.esjp.products.Product;
import org.efaps.util.EFapsException;
import org.efaps.util.cache.CacheReloadException;
import org.joda.time.DateTime;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.base.expression.AbstractSimpleExpression;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.ComponentColumnBuilder;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.component.SubreportBuilder;
import net.sf.dynamicreports.report.builder.group.ColumnGroupBuilder;
import net.sf.dynamicreports.report.definition.ReportParameters;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

/**
 * TODO comment!
 *
 * @author Jan Moxter
 */
@EFapsUUID("f8089189-2dfd-497c-9227-5f48cf50f6cc")
@EFapsApplication("eFapsApp-Construction")
public abstract class CostEstimateViaDocumentReport_Base
    extends FilteredReport
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
        final AbstractDynamicReport dyRp = getDynReport(_parameter);
        dyRp.setFileName(DBProperties.getProperty(CostEstimateViaDocumentReport.class.getName() + ".FileName"));
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
        final AbstractDynamicReport dyRp = getDynReport(_parameter);
        dyRp.setFileName(DBProperties.getProperty(CostEstimateViaDocumentReport.class.getName() + ".FileName"));
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

    protected DynCostEstimateViaDocumentReport getDynReport(final Parameter _parameter)
        throws EFapsException
    {
        return new DynCostEstimateViaDocumentReport(this);
    }

    public static class DynCostEstimateViaDocumentReport
        extends AbstractDynamicReport
    {

        private final CostEstimateViaDocumentReport_Base filteredReport;

        private Instance currencyInstance;

        public DynCostEstimateViaDocumentReport(final CostEstimateViaDocumentReport_Base _salesProductReport_Base)
        {
            filteredReport = _salesProductReport_Base;
        }

        protected boolean isGroup(final Parameter _parameter)
            throws EFapsException
        {
            final Map<String, Object> filter = filteredReport.getFilterMap(_parameter);
            return BooleanUtils.isTrue((Boolean) filter.get("switch"));
        }

        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final boolean genericProducts = Construction.VERIFICATIONLISTALLOWGENERIC.get();
            final boolean group = isGroup(_parameter);
            final Map<Instance, DataBean> map = new HashMap<>();

            final Map<String, Object> filter = filteredReport.getFilterMap(_parameter);
            boolean offset = false;
            if (filter.containsKey("offset")) {
                offset = (boolean) filter.get("offset");
            }
            final QueryBuilder attrQueryBldr = getQueryBldrFromProperties(_parameter, offset ? 100 : 0);

            add2QueryBuilder(_parameter, attrQueryBldr);

            final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionSumAbstract);
            queryBldr.addWhereAttrInQuery(CISales.PositionSumAbstract.DocumentAbstractLink,
                            attrQueryBldr.getAttributeQuery(CIERP.DocumentAbstract.ID));
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selProd = SelectBuilder.get().linkto(CISales.PositionSumAbstract.Product);
            final SelectBuilder selProdInst = new SelectBuilder(selProd).instance();
            final SelectBuilder selProdName = new SelectBuilder(selProd).attribute(CIProducts.ProductAbstract.Name);
            final SelectBuilder selProdDesc = new SelectBuilder(selProd)
                            .attribute(CIProducts.ProductAbstract.Description);
            final SelectBuilder selProdClazz = new SelectBuilder(selProd).clazz().type();
            final SelectBuilder selCurrInst = SelectBuilder.get().linkto(CISales.PositionSumAbstract.RateCurrencyId)
                            .instance();
            final SelectBuilder selDate = SelectBuilder.get().linkto(CISales.PositionSumAbstract.DocumentAbstractLink)
                            .attribute(CIERP.DocumentAbstract.Date);
            multi.addSelect(selProdInst, selProdName, selProdDesc, selCurrInst, selDate);
            if (group) {
                multi.addSelect(selProdClazz);
            }
            multi.addAttribute(CISales.PositionSumAbstract.RateNetPrice, CISales.PositionSumAbstract.UoM,
                            CISales.PositionSumAbstract.Quantity);
            multi.execute();
            while (multi.next()) {
                final Instance prodInst = multi.getSelect(selProdInst);
                DataBean bean = null;
                final BigDecimal quantity = multi.<BigDecimal>getAttribute(CISales.PositionSumAbstract.Quantity);
                final boolean estimate = isEstimate(_parameter, multi.getCurrentInstance());

                final Instance currencyInst = multi.getSelect(selCurrInst);
                final DateTime date = multi.getSelect(selDate);
                RateInfo rateInfo;
                if (currencyInst.equals(getCurrencyInstance())) {
                    rateInfo = RateInfo.getDummyRateInfo();
                } else {
                    rateInfo = new Currency().evaluateRateInfos(_parameter, date, currencyInst,
                                    currencyInstance)[2];
                }

                if (estimate) {
                    final QueryBuilder bomQueryBldr = new QueryBuilder(CIConstruction.EntryBOM);
                    bomQueryBldr.addWhereAttrEqValue(CIConstruction.EntryBOM.FromLink, prodInst);

                    final MultiPrintQuery bomMulti = bomQueryBldr.getPrint();
                    final SelectBuilder selBomProd = new SelectBuilder().linkto(CIConstruction.EntryBOM.ToLink);
                    final SelectBuilder selBomProdInst = new SelectBuilder(selBomProd).instance();
                    final SelectBuilder selBomProdName = new SelectBuilder(selBomProd)
                                    .attribute(CIProducts.ProductAbstract.Name);
                    final SelectBuilder selBomProdDesc = new SelectBuilder(selBomProd)
                                    .attribute(CIProducts.ProductAbstract.Description);
                    final SelectBuilder selBomProdClazz = new SelectBuilder(selBomProd).clazz().type();
                    bomMulti.addSelect(selBomProdInst, selBomProdName, selBomProdDesc);
                    if (group) {
                        bomMulti.addSelect(selBomProdClazz);
                    }
                    bomMulti.addAttribute(CIConstruction.EntryBOM.Quantity, CIConstruction.EntryBOM.Price, CIConstruction.EntryBOM.UoM);
                    bomMulti.execute();

                    while (bomMulti.next()) {
                        final Instance bomProdInst = bomMulti.<Instance>getSelect(selBomProdInst);
                        final BigDecimal bomQuantity = bomMulti.<BigDecimal>getAttribute(CIConstruction.EntryBOM.Quantity);
                        final BigDecimal totalQuantity = quantity.multiply(bomQuantity);
                        final BigDecimal price = bomMulti.<BigDecimal>getAttribute(CIConstruction.EntryBOM.Price);
                        final BigDecimal totalPrice = price.multiply(quantity);

                        if (map.containsKey(bomProdInst)) {
                            bean = map.get(bomProdInst);
                        } else {
                            bean = new DataBean();
                            map.put(bomProdInst, bean);
                            bean.setCurrencyInst(currencyInstance);
                            bean.setProductName(bomMulti.<String>getSelect(selBomProdName));
                            bean.setProductDesc(bomMulti.<String>getSelect(selBomProdDesc));
                            if (group) {
                                bean.setClazz(bomMulti.getSelect(selBomProdClazz));
                            }
                        }
                        bean.addEstimate(multi.<Long>getAttribute(CISales.PositionSumAbstract.UoM), totalQuantity);
                        final BigDecimal amount = totalPrice.setScale(12, BigDecimal.ROUND_HALF_UP).divide(
                                        rateInfo.getRate(), BigDecimal.ROUND_HALF_UP);
                        bean.addEstimate(amount);
                    }
                } else {
                    final boolean negate = multi.getCurrentInstance().getType()
                                    .isKindOf(CISales.IncomingCreditNotePosition);

                    if (map.containsKey(prodInst)) {
                        bean = map.get(prodInst);
                    } else if (genericProducts) {
                        final Instance genericInst = new Product().getGeneric4Replacment(_parameter, prodInst);
                        if (genericInst.isValid() && map.containsKey(genericInst)) {
                            bean = map.get(genericInst);
                            bean.add2ReplProducts(prodInst, multi.<String>getSelect(selProdName),
                                            multi.<String>getSelect(selProdDesc));
                        }
                    }

                    if (bean == null) {
                        bean = new DataBean();
                        map.put(prodInst, bean);
                        bean.setCurrencyInst(currencyInstance);
                        bean.setProductName(multi.<String>getSelect(selProdName));
                        bean.setProductDesc(multi.<String>getSelect(selProdDesc));
                        if (group) {
                            bean.setClazz(multi.getSelect(selProdClazz));
                        }
                    }
                    BigDecimal price = multi.<BigDecimal>getAttribute(CISales.PositionSumAbstract.RateNetPrice);
                    BigDecimal quant = multi.<BigDecimal>getAttribute(CISales.PositionSumAbstract.Quantity);

                    if (negate) {
                        quant = quant.negate();
                        price = price.negate();
                    }

                    bean.addApplied(multi.<Long>getAttribute(CISales.PositionSumAbstract.UoM), quant);
                    final BigDecimal amount = price.setScale(12, BigDecimal.ROUND_HALF_UP).divide(rateInfo.getRate(),
                                    BigDecimal.ROUND_HALF_UP);
                    bean.addApplied(amount);
                }
            }

            final List<DataBean> datasource = new ArrayList<>();
            datasource.addAll(map.values());
            final ComparatorChain<DataBean> chain = new ComparatorChain<>();
            if (group) {
                chain.addComparator((_arg0,
                 _arg1) -> _arg0.getProductClazz().compareTo(_arg1.getProductClazz()));
            }

            chain.addComparator((_arg0,
             _arg1) -> _arg0.getProductName().compareTo(_arg1.getProductName()));
            Collections.sort(datasource, chain);
            return new JRBeanCollectionDataSource(datasource);
        }

        /**
         * @param _parameter Parameter as passed from the eFaps API
         * @param _queryBldr QueryBuilder the criteria will be added to
         * @throws EFapsException on error
         */
        protected void add2QueryBuilder(final Parameter _parameter,
                                        final QueryBuilder _queryBldr)
            throws EFapsException
        {
            final Map<String, Object> filter = filteredReport.getFilterMap(_parameter);
            final Instance inst = _parameter.getInstance();
            if (inst != null && inst.isValid() && inst.getType().isKindOf(CIProjects.ProjectAbstract)) {
                final PrintQuery print = new PrintQuery(inst);
                final SelectBuilder currinstSel = SelectBuilder.get().linkto(CIProjects.ProjectAbstract.CurrencyLink)
                                .instance();
                print.addSelect(currinstSel);
                print.execute();
                setCurrencyInstance(print.<Instance>getSelect(currinstSel));

                final QueryBuilder projAttrQueryBldr = new QueryBuilder(CIProjects.Project2DocumentAbstract);
                projAttrQueryBldr.addWhereAttrEqValue(CIProjects.Project2DocumentAbstract.FromAbstract, inst);
                _queryBldr.addWhereAttrInQuery(CIERP.DocumentAbstract.ID,
                                projAttrQueryBldr.getAttributeQuery(CIProjects.Project2DocumentAbstract.ToAbstract));
            }
            if (filter.containsKey("type")) {
                // _queryBldr.addWhereAttrEqValue(CISales.DocumentSumAbstract.Type,
                // ((TypeFilterValue) filter.get("type")).getObject());
            }
        }

        @Override
        protected void addColumnDefinition(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {
            final boolean genericProducts = Construction.VERIFICATIONLISTALLOWGENERIC.get();
            final TextColumnBuilder<String> prodNameColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateViaDocumentReport.class.getName() + ".Column.productName"),
                            "productName", DynamicReports.type.stringType());
            final TextColumnBuilder<String> prodDescColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateViaDocumentReport.class.getName() + ".Column.productDesc"),
                            "productDesc", DynamicReports.type.stringType()).setWidth(450);

            ComponentColumnBuilder subReportColumn = null;
            if (genericProducts) {
                final SubreportBuilder subreport = DynamicReports.cmp.subreport(new SubreportDesign())
                                             .setDataSource(new SubreportData());
                subReportColumn = DynamicReports.col.componentColumn("Products", subreport).setWidth(300);
                _builder.addField("replProducts", Collection.class);
            }

            final TextColumnBuilder<String> uoMColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateViaDocumentReport.class.getName() + ".Column.uoMName"),
                            "uoMName", DynamicReports.type.stringType());

            final TextColumnBuilder<BigDecimal> estQuantityColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateViaDocumentReport.class.getName() + ".Column.estQuantity"),
                            "estQuantity", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> applQuantityColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateViaDocumentReport.class.getName() + ".Column.applQuantity"),
                            "applQuantity", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> percentQuantityColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateViaDocumentReport.class.getName() + ".Column.percentQuantity"),
                            "percentQuantity", DynamicReports.type.bigDecimalType());

            final TextColumnBuilder<String> currencyColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateViaDocumentReport.class.getName() + ".Column.currency"),
                            "currency", DynamicReports.type.stringType());
            final TextColumnBuilder<BigDecimal> estAmountColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateViaDocumentReport.class.getName() + ".Column.estAmount"),
                            "estAmount", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> applAmountColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateViaDocumentReport.class.getName() + ".Column.applAmount"),
                            "applAmount", DynamicReports.type.bigDecimalType());

            final TextColumnBuilder<BigDecimal> percentAmountColumn = DynamicReports.col.column(DBProperties
                            .getProperty(CostEstimateViaDocumentReport.class.getName() + ".Column.percentAmount"),
                            "percentAmount", DynamicReports.type.bigDecimalType());

            if (isGroup(_parameter)) {
                final TextColumnBuilder<String> clazzColumn = DynamicReports.col.column(DBProperties
                                .getProperty(CostEstimateViaDocumentReport.class.getName() + ".Column.productClazz"),
                                "productClazz", DynamicReports.type.stringType());
                _builder.addColumn(clazzColumn);
                final ColumnGroupBuilder clazzGroup = DynamicReports.grp.group("sc", clazzColumn).groupByDataType();
                _builder.groupBy(clazzGroup);
                _builder.addSubtotalAtGroupFooter(clazzGroup, DynamicReports.sbt.sum(estAmountColumn));
                _builder.addSubtotalAtGroupFooter(clazzGroup, DynamicReports.sbt.sum(applAmountColumn));
            }

            _builder.addColumn(prodNameColumn, prodDescColumn);

            if (genericProducts) {
                _builder.addColumn(subReportColumn);
            }

            _builder.addColumn( uoMColumn, estQuantityColumn, applQuantityColumn,
                            percentQuantityColumn,
                            currencyColumn, estAmountColumn, applAmountColumn, percentAmountColumn);
        }

        protected boolean isEstimate(final Parameter _parameter,
                                     final Instance _instance)
            throws CacheReloadException
        {
            return _instance.getType().isKindOf(CIConstruction.CostEstimatePositionAbstract);
        }

        /**
         * Getter method for the instance variable {@link #currencyInstance}.
         *
         * @return value of instance variable {@link #currencyInstance}
         */
        protected Instance getCurrencyInstance()
            throws EFapsException
        {
            return currencyInstance;
        }

        /**
         * Setter method for instance variable {@link #currencyInstance}.
         *
         * @param _currencyInstance value for instance variable
         *            {@link #currencyInstance}
         */
        protected void setCurrencyInstance(final Instance _currencyInstance)
        {
            currencyInstance = _currencyInstance;
        }
    }

    public static class DataBean
    {

        private Instance prodInst;

        private Instance currencyInst;

        private String productName;

        private String productDesc;

        private UoM uom;

        private BigDecimal estQuantity = BigDecimal.ZERO;

        private BigDecimal applQuantity = BigDecimal.ZERO;

        private BigDecimal estAmount = BigDecimal.ZERO;

        private BigDecimal applAmount = BigDecimal.ZERO;

        private Object clazz;

        private final Map<Instance, Map<String, Object>> replProducts = new HashMap<>();

        public Collection<Map<String, Object>> getReplProducts()
        {
            return replProducts.values();
        }

        /**
         * Add2 repl products.
         *
         * @param _prodInst the _prod inst
         * @param _name the _name
         * @param _descr the _descr
         */
        public void add2ReplProducts(final Instance _prodInst,
                                     final String _name,
                                     final String _descr)
        {
            if (!replProducts.containsKey(_prodInst)) {
                final Map<String, Object> map = new HashMap<>();
                map.put("name", _name);
                map.put("descr", _descr);
                replProducts.put(_prodInst, map);
            }
        }

        public void setClazz(final Object _clazz)
        {
            clazz = _clazz;
        }

        public String getUoMName()
        {
            return uom == null ? "" : uom.getName();
        }

        /**
         * @param _attribute
         * @param _attribute2
         */
        public void addEstimate(final Long _uomId,
                                final BigDecimal _quantity)
        {
            if (uom == null) {
                uom = Dimension.getUoM(_uomId);
                estQuantity = estQuantity.add(_quantity);
            } else if (uom.getId() == _uomId) {
                estQuantity = estQuantity.add(_quantity);
            } else {
                estQuantity = convertToBase(uom, estQuantity);
                final UoM uomTmp = Dimension.getUoM(_uomId);
                estQuantity = estQuantity.add(convertToBase(uomTmp, _quantity));
            }
        }

        /**
         * @param _attribute
         * @param _attribute2
         */
        public void addEstimate(final BigDecimal _amount)
        {
            estAmount = estAmount.add(_amount);
        }

        /**
         * @param _attribute
         * @param _attribute2
         */
        public void addApplied(final BigDecimal _amount)
        {
            applAmount = applAmount.add(_amount);
        }

        /**
         * @param _attribute
         * @param _attribute2
         */
        public void addApplied(final Long _uomId,
                               final BigDecimal _quantity)
        {
            if (uom == null) {
                uom = Dimension.getUoM(_uomId);
                applQuantity = applQuantity.add(_quantity);
            } else if (uom.getId() == _uomId) {
                applQuantity = applQuantity.add(_quantity);
            } else {
                applQuantity = convertToBase(uom, applQuantity);
                final UoM uomTmp = Dimension.getUoM(_uomId);
                applQuantity = applQuantity.add(convertToBase(uomTmp, _quantity));
            }
        }

        protected BigDecimal convertToBase(final UoM _uoM,
                                           final BigDecimal _quantity)
        {
            BigDecimal ret = _quantity;
            if (!_uoM.equals(_uoM.getDimension().getBaseUoM())) {
                ret = new BigDecimal(_uoM.getNumerator()).setScale(12, BigDecimal.ROUND_HALF_UP)
                                .divide(new BigDecimal(_uoM.getDenominator()), BigDecimal.ROUND_HALF_UP)
                                .multiply(_quantity);
                if (!uom.equals(uom.getDimension().getBaseUoM())) {
                    uom = uom.getDimension().getBaseUoM();
                }
            }
            return ret;
        }

        public String getCurrency()
            throws EFapsException
        {
            return CurrencyInst.get(getCurrencyInst()).getSymbol();
        }

        public String getProductClazz()
        {
            String ret = "";
            if (clazz != null) {
                if (clazz instanceof Classification) {
                    ret = ((Classification) clazz).getLabel();
                } else if (clazz instanceof List) {
                    final List<Classification> clazzes = new ArrayList<Classification>();
                    for (final Object val : (List<?>) clazz) {
                        clazzes.add((Classification) val);
                    }
                    for (final Classification clazz : clazzes) {
                        ret = ret + clazz.getLabel();
                    }
                }
            }
            return ret;
        }

        /**
         * Getter method for the instance variable {@link #oid}.
         *
         * @return value of instance variable {@link #oid}
         */
        public String getOid()
        {
            return prodInst.getOid();
        }

        /**
         * Getter method for the instance variable {@link #productName}.
         *
         * @return value of instance variable {@link #productName}
         */
        public String getProductName()
        {
            return productName;
        }

        /**
         * Setter method for instance variable {@link #productName}.
         *
         * @param _productName value for instance variable {@link #productName}
         */
        public void setProductName(final String _productName)
        {
            productName = _productName;
        }

        /**
         * Getter method for the instance variable {@link #productDesc}.
         *
         * @return value of instance variable {@link #productDesc}
         */
        public String getProductDesc()
        {
            return productDesc;
        }

        /**
         * Setter method for instance variable {@link #productDesc}.
         *
         * @param _productDesc value for instance variable {@link #productDesc}
         */
        public void setProductDesc(final String _productDesc)
        {
            productDesc = _productDesc;
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
         * Getter method for the instance variable {@link #prodInst}.
         *
         * @return value of instance variable {@link #prodInst}
         */
        public Instance getProdInst()
        {
            return prodInst;
        }

        /**
         * Setter method for instance variable {@link #prodInst}.
         *
         * @param _prodInst value for instance variable {@link #prodInst}
         */
        public void setProdInst(final Instance _prodInst)
        {
            prodInst = _prodInst;
        }

        /**
         * Getter method for the instance variable {@link #currencyInst}.
         *
         * @return value of instance variable {@link #currencyInst}
         */
        public Instance getCurrencyInst()
            throws EFapsException
        {
            if (currencyInst != null && !currencyInst.isValid()) {
                currencyInst = Currency.getBaseCurrency();
            }

            return currencyInst;
        }

        /**
         * Setter method for instance variable {@link #currencyInst}.
         *
         * @param _currencyInst value for instance variable
         *            {@link #currencyInst}
         */
        public void setCurrencyInst(final Instance _currencyInst)
        {
            currencyInst = _currencyInst;
        }

        /**
         * Getter method for the instance variable {@link #estQuantity}.
         *
         * @return value of instance variable {@link #estQuantity}
         */
        public BigDecimal getEstQuantity()
        {
            return estQuantity;
        }

        /**
         * Setter method for instance variable {@link #estQuantity}.
         *
         * @param _estQuantity value for instance variable {@link #estQuantity}
         */
        public void setEstQuantity(final BigDecimal _estQuantity)
        {
            estQuantity = _estQuantity;
        }

        /**
         * Getter method for the instance variable {@link #aplQuantity}.
         *
         * @return value of instance variable {@link #aplQuantity}
         */
        public BigDecimal getApplQuantity()
        {
            return applQuantity;
        }

        public BigDecimal getPercentAmount()
        {
            BigDecimal ret = null;
            if (estAmount.compareTo(BigDecimal.ZERO) != 0 && applAmount.compareTo(BigDecimal.ZERO) != 0) {
                ret = new BigDecimal(100).setScale(12, BigDecimal.ROUND_HALF_UP)
                                .divide(estAmount, BigDecimal.ROUND_HALF_UP).multiply(applAmount);
            }
            return ret;
        }

        public BigDecimal getPercentQuantity()
        {
            BigDecimal ret = null;
            if (estQuantity.compareTo(BigDecimal.ZERO) != 0 && applQuantity.compareTo(BigDecimal.ZERO) != 0) {
                ret = new BigDecimal(100).setScale(12, BigDecimal.ROUND_HALF_UP)
                                .divide(estQuantity, BigDecimal.ROUND_HALF_UP).multiply(applQuantity);
            }
            return ret;
        }

        /**
         * Setter method for instance variable {@link #aplQuantity}.
         *
         * @param _aplQuantity value for instance variable {@link #aplQuantity}
         */
        public void setApplQuantity(final BigDecimal _aplQuantity)
        {
            applQuantity = _aplQuantity;
        }

        /**
         * Getter method for the instance variable {@link #estAmount}.
         *
         * @return value of instance variable {@link #estAmount}
         */
        public BigDecimal getEstAmount()
        {
            return estAmount;
        }

        /**
         * Setter method for instance variable {@link #estAmount}.
         *
         * @param _estAmount value for instance variable {@link #estAmount}
         */
        public void setEstAmount(final BigDecimal _estAmount)
        {
            estAmount = _estAmount;
        }

        /**
         * Getter method for the instance variable {@link #applAmount}.
         *
         * @return value of instance variable {@link #applAmount}
         */
        public BigDecimal getApplAmount()
        {
            return applAmount;
        }

        /**
         * Setter method for instance variable {@link #applAmount}.
         *
         * @param _applAmount value for instance variable {@link #applAmount}
         */
        public void setApplAmount(final BigDecimal _applAmount)
        {
            applAmount = _applAmount;
        }
    }

    public static class SubreportDesign
        extends AbstractSimpleExpression<JasperReportBuilder>
    {

        private static final long serialVersionUID = 1L;

        @Override
        public JasperReportBuilder evaluate(final ReportParameters reportParameters)
        {
            final JasperReportBuilder report = DynamicReports.report()
                            .columns(DynamicReports.col.column("name", DynamicReports.type.stringType()),
                                     DynamicReports.col.column("descr", DynamicReports.type.stringType()));
            return report;
        }
    }

    public static  class SubreportData
        extends AbstractSimpleExpression<JRDataSource>
    {

        private static final long serialVersionUID = 1L;

        @Override
        public JRDataSource evaluate(final ReportParameters reportParameters)
        {
            final Collection<Map<String, ?>> value = reportParameters.getValue("replProducts");
            return new JRMapCollectionDataSource(value);
        }
    }
}
