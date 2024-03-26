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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.efaps.admin.datamodel.Dimension;
import org.efaps.admin.datamodel.Dimension.UoM;
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
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.util.EFapsException;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.subtotal.AggregationSubtotalBuilder;
import net.sf.dynamicreports.report.datasource.DRDataSource;
import net.sf.jasperreports.engine.JRDataSource;

/**
 * TODO comment!
 *
 * @author Jan Moxter
 */
@EFapsUUID("37ced7bd-b1a7-45b4-bca3-a6763ae1cd06")
@EFapsApplication("eFapsApp-Construction")
public abstract class VerificationListReport_Base
{

    public Return getReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final AbstractDynamicReport dyRp = getPartListReport(_parameter);
        dyRp.setFileName(DBProperties.getProperty(VerificationListReport.class.getName() + ".FileName"));
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
        dyRp.setFileName(DBProperties.getProperty(VerificationListReport.class.getName() + ".FileName"));
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

    protected VerificationReport getPartListReport(final Parameter _parameter)
        throws EFapsException
    {
        return new VerificationReport();
    }

    public static class VerificationReport
        extends AbstractDynamicReport
    {

        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final DRDataSource ret = new DRDataSource("name", "description", "uom", "unitPrice", "quantity", "price");
            final Instance instance = _parameter.getInstance();

            final Map<Instance, Position> values = new HashMap<Instance, Position>();

            final QueryBuilder attrQueryBldr = new QueryBuilder(CIConstruction.ProjectService2VerificationList);
            attrQueryBldr.addWhereAttrEqValue(CIConstruction.ProjectService2VerificationList.FromLink, instance);
            final AttributeQuery attrQuery = attrQueryBldr
                            .getAttributeQuery(CIConstruction.ProjectService2VerificationList.ToLink);

            final QueryBuilder statusAttrQueryBldr = new QueryBuilder(CIConstruction.VerificationList);
            statusAttrQueryBldr.addWhereAttrEqValue(CIConstruction.VerificationList.Status,
                            Status.find(CIConstruction.VerificationListStatus.Closed));
            final AttributeQuery statusAttrQuery = statusAttrQueryBldr.getAttributeQuery(CIConstruction.VerificationList.ID);

            final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.VerificationListPosition);
            queryBldr.addWhereAttrInQuery(CIConstruction.VerificationListPosition.VerificationListLink, attrQuery);
            queryBldr.addWhereAttrInQuery(CIConstruction.VerificationListPosition.VerificationListLink, statusAttrQuery);

            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selProd = SelectBuilder.get().linkto(CIConstruction.VerificationListPosition.Product);
            final SelectBuilder selProdInst = new SelectBuilder(selProd).instance();
            final SelectBuilder selProdName = new SelectBuilder(selProd).attribute(CIProducts.ProductAbstract.Name);
            final SelectBuilder selProdDesc = new SelectBuilder(selProd)
                            .attribute(CIProducts.ProductAbstract.Description);
            multi.addSelect(selProdInst, selProdName, selProdDesc);
            multi.addAttribute(CIConstruction.VerificationListPosition.NetPrice, CIConstruction.VerificationListPosition.Quantity,
                            CIConstruction.VerificationListPosition.UoM);
            multi.execute();
            while (multi.next()) {
                final Instance prodInst = multi.<Instance>getSelect(selProdInst);
                Position pos;
                if (values.containsKey(prodInst)) {
                    pos = values.get(prodInst);
                } else {
                    pos = getPosition(_parameter);
                    values.put(prodInst, pos);
                    pos.setName(multi.<String>getSelect(selProdName));
                    pos.setDescription(multi.<String>getSelect(selProdDesc));
                    pos.setUom(Dimension.getUoM(multi.<Long>getAttribute(CIConstruction.VerificationListPosition.UoM))
                                    .getDimension().getBaseUoM().getName());
                }
                final UoM uom = Dimension.getUoM(multi.<Long>getAttribute(CIConstruction.VerificationListPosition.UoM));
                BigDecimal quantity = multi.<BigDecimal>getAttribute(CIConstruction.VerificationListPosition.Quantity);
                if (!uom.equals(uom.getDimension().getBaseUoM())) {
                    quantity = quantity.multiply(new BigDecimal(uom.getNumerator()))
                                    .setScale(12, BigDecimal.ROUND_HALF_UP)
                                    .divide(new BigDecimal(uom.getDenominator()), BigDecimal.ROUND_HALF_UP);
                }
                pos.addQuantity(quantity);
                pos.addPrice(multi.<BigDecimal>getAttribute(CIConstruction.VerificationListPosition.NetPrice));
            }
            final List<Position> positions = new ArrayList<Position>();
            positions.addAll(values.values());
            Collections.sort(positions, (_o1,
             _o2) -> _o1.getName().compareTo(_o2.getName()));
            for (final Position pos : positions) {
                ret.add(pos.getName(), pos.getDescription(), pos.getUom(), pos.getUnitPrice(), pos.getQuantity(),
                                pos.getPrice());
            }
            return ret;
        }

        protected Position getPosition(final Parameter _parameter)
            throws EFapsException
        {
            return new Position();
        }


        @Override
        protected void addColumnDefinition(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {
            final TextColumnBuilder<String> nameColumn = DynamicReports.col.column(DBProperties
                            .getProperty(VerificationListReport.class.getName() + ".Column.name"),
                            "name", DynamicReports.type.stringType());
            final TextColumnBuilder<String> descColumn = DynamicReports.col.column(DBProperties
                            .getProperty(VerificationListReport.class.getName() + ".Column.description"),
                            "description", DynamicReports.type.stringType()).setWidth(500);
            final TextColumnBuilder<String> uomColumn = DynamicReports.col.column(DBProperties
                            .getProperty(VerificationListReport.class.getName() + ".Column.uom"),
                            "uom", DynamicReports.type.stringType());
            final TextColumnBuilder<BigDecimal> unitPriceColumn = DynamicReports.col.column(DBProperties
                            .getProperty(VerificationListReport.class.getName() + ".Column.unitPrice"),
                            "unitPrice", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> quantityColumn = DynamicReports.col.column(DBProperties
                            .getProperty(VerificationListReport.class.getName() + ".Column.quantity"),
                            "quantity", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> priceColumn = DynamicReports.col.column(DBProperties
                            .getProperty(VerificationListReport.class.getName() + ".Column.price"),
                            "price", DynamicReports.type.bigDecimalType());

            _builder.addColumn(nameColumn, descColumn, uomColumn, unitPriceColumn, quantityColumn, priceColumn);

            final AggregationSubtotalBuilder<BigDecimal> priceSum = DynamicReports.sbt.sum(priceColumn);

            _builder.addSubtotalAtColumnFooter(priceSum);
        }

    }


    /**
     * Class to store the products for the report.
     */
    public static class Position
    {

        private String name;

        private String description;

        private String uom;

        private BigDecimal price = BigDecimal.ZERO;

        private BigDecimal quantity = BigDecimal.ZERO;

        public BigDecimal addPrice(final BigDecimal toAdd)
        {
            return price = price.add(toAdd);
        }

        public BigDecimal addQuantity(final BigDecimal toAdd)
        {
            return quantity = quantity.add(toAdd);
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

        public BigDecimal getUnitPrice()
        {
            BigDecimal ret = BigDecimal.ZERO;
            if (getQuantity().compareTo(BigDecimal.ZERO) != 0) {
                ret = getPrice().setScale(12, BigDecimal.ROUND_HALF_UP).divide(getQuantity(), BigDecimal.ROUND_HALF_UP);
            }
            return ret;
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
    }
}
