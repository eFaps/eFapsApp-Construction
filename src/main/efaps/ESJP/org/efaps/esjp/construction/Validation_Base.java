/*
 * Copyright 2007 - 2015 moxter.net
 *
 * All Rights Reserved.
 * This program contains proprietary and trade secret information of
 * Jan Moxter Copyright notice is precautionary only and does not
 * evidence any actual or intended publication of such program.
 *
 */


package org.efaps.esjp.construction;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
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
import org.efaps.esjp.ci.CIFormConstruction;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CIProjects;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.AbstractCommon;
import org.efaps.esjp.construction.util.Construction;
import org.efaps.esjp.erp.AbstractPositionWarning;
import org.efaps.esjp.erp.IWarning;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.erp.WarningUtil;
import org.efaps.esjp.products.Product;
import org.efaps.util.EFapsException;


/**
 * TODO comment!
 *
 * @author Jan Moxter
 */
@EFapsUUID("280c3742-f13b-40b0-8ca7-8c2e1419847e")
@EFapsApplication("eFapsApp-Construction")
public abstract class Validation_Base
    extends AbstractCommon
{

    /**
     * Validate document4 verification list.
     *
     * @param _parameter the _parameter
     * @return the return
     * @throws EFapsException on error
     */
    public Return validateDocument4VerificationList(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final List<Instance> instances = getVerificationList4Project(_parameter);
        if (!instances.isEmpty()) {
            final List<IWarning> warnings = getWarnings(_parameter, instances);
            if (warnings.isEmpty()) {
                ret.put(ReturnValues.TRUE, true);
            } else {
                ret.put(ReturnValues.SNIPLETT, WarningUtil.getHtml4Warning(warnings).toString());
                if (!WarningUtil.hasError(warnings)) {
                    ret.put(ReturnValues.TRUE, true);
                }
            }
        } else {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }

    /**
     * Method to obtains StringBuilder with compares values.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @param _instances to compare.
     * @return new StringBuilder.
     * @throws EFapsException on error.
     */
    public Return getWarningFieldValue4Document4VerificationList(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final List<Instance> instances = getVerificationList4Project(_parameter);
        final StringBuilder html = new StringBuilder();
        if (!instances.isEmpty()) {
            final List<IWarning> warnings = getWarnings(_parameter, instances);
            if (!warnings.isEmpty()) {
                html.append(WarningUtil.getHtml4Warning(warnings).toString());
            }
        }
        ret.put(ReturnValues.SNIPLETT, html.toString());
        return ret;
    }

    /**
     * Gets the warnings.
     *
     * @param _parameter the _parameter
     * @param _instances the _instances
     * @return the warnings
     * @throws EFapsException on error
     */
    protected List<IWarning> getWarnings(final Parameter _parameter,
                                         final List<Instance> _instances)
        throws EFapsException
    {
        final List<IWarning> ret = new ArrayList<>();
        final boolean more = "true".equalsIgnoreCase(getProperty(_parameter, "AllowMore"));
        final boolean notIncluded = "true".equalsIgnoreCase(getProperty(_parameter, "AllowNotIncluded"));
        final Map<Instance, BigDecimal> current = getCurrentValues(_parameter);
        final Map<Instance, BigDecimal> verification = getVerificationValues(_parameter, _instances);
        final Map<Instance, Instance> product2generic = getProduct2Generic(_parameter, verification);
        final Map<Instance, BigDecimal> used = getUsedValues(_parameter, product2generic);

        if (!current.isEmpty()) {
            int i = 1;
            for (final Entry<Instance, BigDecimal> pos : current.entrySet()) {
                if (verification.containsKey(pos.getKey()) || product2generic.containsKey(pos.getKey())) {
                    final Instance prodInst = product2generic.containsKey(pos.getKey())
                                    ? product2generic.get(pos.getKey()) : pos.getKey();
                    BigDecimal usedQuandity = used.containsKey(prodInst) ? used.get(prodInst) : BigDecimal.ZERO;
                    usedQuandity = usedQuandity.add(pos.getValue());
                    used.put(prodInst, usedQuandity);
                    if (usedQuandity.compareTo(verification.get(prodInst)) > 0) {
                        ret.add(new ICMoreWarning(pos.getKey(), pos.getValue(), verification.get(prodInst))
                            .setPosition(i).setError(!more));
                    }
                } else {
                    ret.add(new ICNotIncludedWarning(pos.getKey()).setPosition(i).setError(!notIncluded));
                }
                i++;
            }
        }
        return ret;
    }

    /**
     * Gets the used values.
     *
     * @param _parameter the _parameter
     * @param product2generic
     * @return the used values
     */
    protected Map<Instance, BigDecimal> getUsedValues(final Parameter _parameter,
                                                      final Map<Instance, Instance> product2generic)
        throws EFapsException
    {
        final Map<Instance, BigDecimal> ret = new HashMap<>();
        Instance projectInst = Instance
                        .get(_parameter.getParameterValue(CIFormConstruction.Construction_VerificationListForm.project.name));

        if (!projectInst.isValid()) {
            final Instance docInst = _parameter.getInstance();
            final QueryBuilder queryBldr = new QueryBuilder(CIProjects.Project2DocumentAbstract);
            queryBldr.addWhereAttrEqValue(CIProjects.Project2DocumentAbstract.ToAbstract, docInst);
            final SelectBuilder projSel = SelectBuilder.get().linkto(CIProjects.Project2DocumentAbstract.FromAbstract)
                            .instance();
            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.addSelect(projSel);
            multi.execute();
            if (multi.next()) {
                projectInst = multi.<Instance>getSelect(projSel);
            }
        }

        if (projectInst.isValid()) {
            final QueryBuilder attrQueryBldr = new QueryBuilder(CIProjects.ProjectService2ProductRequest);
            attrQueryBldr.addWhereAttrEqValue(CIProjects.ProjectService2ProductRequest.FromLink, projectInst);
            final AttributeQuery attrQuery = attrQueryBldr
                            .getAttributeQuery(CIProjects.ProjectService2ProductRequest.ToLink);

            final QueryBuilder docQueryBldr = new QueryBuilder(CISales.ProductRequest);
            docQueryBldr.addWhereAttrNotEqValue(CISales.ProductRequest.Status,
                            Status.find(CISales.ProductRequestStatus.Canceled));

            final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionAbstract);
            queryBldr.addWhereAttrInQuery(CISales.PositionAbstract.DocumentAbstractLink, attrQuery);
            queryBldr.addWhereAttrInQuery(CISales.PositionAbstract.DocumentAbstractLink,
                            docQueryBldr.getAttributeQuery(CISales.ProductRequest.ID));
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selProdInst = new SelectBuilder().linkto(CISales.PositionAbstract.Product)
                            .instance();
            multi.addAttribute(CISales.PositionAbstract.Quantity);
            multi.addSelect(selProdInst);
            multi.execute();
            while (multi.next()) {
                ret.put(multi.<Instance>getSelect(selProdInst),
                                multi.<BigDecimal>getAttribute(CISales.PositionAbstract.Quantity));
            }
        }

        for (final Entry<Instance, Instance> entry : product2generic.entrySet()) {
            if (ret.containsKey(entry.getKey())) {
                final BigDecimal genQu = ret.containsKey(entry.getValue()) ? ret.get(entry.getValue())
                                : BigDecimal.ZERO;
                ret.put(entry.getValue(), genQu.add(ret.get(entry.getKey())));
            }
        }
        return ret;
    }

    /**
     * Gets the verification 4 generic products.
     *
     * @param _parameter the _parameter
     * @param _verification the _verification
     * @return the verification4 generic products
     * @throws EFapsException on error
     */
    protected Map<Instance, Instance> getProduct2Generic(final Parameter _parameter,
                                                         final Map<Instance, BigDecimal> _verification)
        throws EFapsException
    {
        final Map<Instance, Instance> ret = new HashMap<>();
        if (Construction.VERIFICATIONLISTALLOWGENERIC.get()) {
            for (final Entry<Instance, BigDecimal> pos : _verification.entrySet()) {
                if (pos.getKey().getType().isCIType(CIProducts.ProductGeneric)) {
                    final List<Instance> replacements = new Product().getReplacements4Generic(_parameter, pos.getKey());
                    for (final Instance inst : replacements) {
                        ret.put(inst, pos.getKey());
                    }
                }
            }
        }
        return ret;
    }

    /**
     * Method to obtains values of the position into interface.
     *
     * @param _parameter as Passed from the eFaps API.
     * @return new Map.
     * @throws EFapsException on error.
     */
    protected Map<Instance, BigDecimal> getCurrentValues(final Parameter _parameter)
        throws EFapsException
    {
        final Map<Instance, BigDecimal> ret = new LinkedHashMap<>();
        final boolean isDoc = "true".equalsIgnoreCase(getProperty(_parameter, "Document"));
        final Instance docInst = _parameter.getInstance();
        if (isDoc && docInst.isValid()) {
            final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionAbstract);
            queryBldr.addWhereAttrEqValue(CISales.PositionAbstract.DocumentAbstractLink, docInst);
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selProdInst = SelectBuilder.get().linkto(CISales.PositionAbstract.Product).instance();
            multi.addSelect(selProdInst);
            multi.addAttribute(CISales.PositionAbstract.Quantity);
            multi.execute();
            while (multi.next()) {
                ret.put(multi.<Instance>getSelect(selProdInst),
                                multi.<BigDecimal>getAttribute(CISales.PositionAbstract.Quantity));
            }
        } else {
            final DecimalFormat formatter = NumberFormatter.get().getFormatter();
            final String[] quantities = _parameter.getParameterValues("quantity");
            final String[] products = _parameter.getParameterValues("product");

            if (quantities != null && products != null && quantities.length == products.length) {
                for (int x = 0; x < quantities.length; x++) {
                    final Instance product = Instance.get(products[x]);
                    BigDecimal quantity = BigDecimal.ZERO;
                    try {
                        quantity = (BigDecimal) formatter.parse(quantities[x]);
                    } catch (final ParseException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    ret.put(product, quantity);
                }
            }
        }
        return ret;
    }

    /**
     * Method to obtains map with quantity products.
     *
     * @param _instances values to the relation VerificationList with project.
     * @param _parameter Parameter as passed from the eFaps API.
     * @return new Map.
     * @throws EFapsException on error.
     */
    protected Map<Instance, BigDecimal> getVerificationValues(final Parameter _parameter,
                                                              final List<Instance> _instances)
        throws EFapsException
    {
        final Map<Instance, BigDecimal> ret = new HashMap<Instance, BigDecimal>();

        final SelectBuilder selProdInst = new SelectBuilder().linkto(CIConstruction.VerificationListPosition.Product).instance();

        final MultiPrintQuery multi = new MultiPrintQuery(_instances);
        multi.addAttribute(CIConstruction.VerificationListPosition.Quantity);
        multi.addSelect(selProdInst);
        multi.execute();
        while (multi.next()) {
            ret.put(multi.<Instance>getSelect(selProdInst),
                            multi.<BigDecimal>getAttribute(CIConstruction.VerificationListPosition.Quantity));
        }
        return ret;
    }

    /**
     * Method to obtains instances of the position VerificationList.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @return instances to the contains positions.
     * @throws EFapsException on error.
     */
    protected List<Instance> getVerificationList4Project(final Parameter _parameter)
        throws EFapsException
    {
        final List<Instance> instances = new ArrayList<Instance>();

        Instance projectInst = Instance
                        .get(_parameter.getParameterValue(CIFormConstruction.Construction_VerificationListForm.project.name));

        if (!projectInst.isValid()) {
            final Instance docInst = _parameter.getInstance();
            final QueryBuilder queryBldr = new QueryBuilder(CIProjects.Project2DocumentAbstract);
            queryBldr.addWhereAttrEqValue(CIProjects.Project2DocumentAbstract.ToAbstract, docInst);
            final SelectBuilder projSel = SelectBuilder.get().linkto(CIProjects.Project2DocumentAbstract.FromAbstract)
                            .instance();
            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.addSelect(projSel);
            multi.execute();
            if (multi.next()) {
                projectInst = multi.<Instance>getSelect(projSel);
            }
        }

        if (projectInst.isValid()) {
            final QueryBuilder attrQueryBldr = new QueryBuilder(CIConstruction.ProjectService2VerificationList);
            attrQueryBldr.addWhereAttrEqValue(CIConstruction.ProjectService2VerificationList.FromLink, projectInst);
            final AttributeQuery attrQuery = attrQueryBldr
                            .getAttributeQuery(CIConstruction.ProjectService2VerificationList.ToLink);

            final QueryBuilder docQueryBldr = new QueryBuilder(CIConstruction.VerificationList);
            docQueryBldr.addWhereAttrEqValue(CIConstruction.VerificationList.Status,
                            Status.find(CIConstruction.VerificationListStatus.Closed));

            final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.VerificationListPosition);
            queryBldr.addWhereAttrInQuery(CIConstruction.VerificationListPosition.DocumentAbstractLink, attrQuery);
            queryBldr.addWhereAttrInQuery(CIConstruction.VerificationListPosition.DocumentAbstractLink,
                            docQueryBldr.getAttributeQuery(CIConstruction.VerificationList.ID));
            instances.addAll(queryBldr.getQuery().execute());
        }
        return instances;
    }

    public static abstract class AbstractWarning
        extends AbstractPositionWarning
    {

        private final Instance productInst;

        /**
         * @param _productInst
         */
        public AbstractWarning(final Instance _productInst)
            throws EFapsException
        {
            productInst = _productInst;
            addObject(getProductString());
        }

        public String getProductString()
            throws EFapsException
        {
            final PrintQuery print = new PrintQuery(productInst);
            print.addAttribute(CIProducts.ProductAbstract.Name, CIProducts.ProductAbstract.Description);
            print.execute();
            return print.getAttribute(CIProducts.ProductAbstract.Name) + " - "
                            + print.getAttribute(CIProducts.ProductAbstract.Description);
        }
    }

    public static class ICMoreWarning
        extends AbstractWarning
    {

        /**
         * @param _key
         */
        public ICMoreWarning(final Instance _productInst,
                           final BigDecimal _quantity,
                           final BigDecimal _allowed)
            throws EFapsException
        {
            super(_productInst);
            addObject(_quantity, _allowed);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKey()
        {
            return Validation.class.getName() + ".WARN.More";
        }
    }

    public static class ICNotIncludedWarning
        extends AbstractWarning
    {

        /**
         * @param _productInst
         */
        public ICNotIncludedWarning(final Instance _productInst)
            throws EFapsException
        {
            super(_productInst);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKey()
        {
            return Validation.class.getName() + ".WARN.NotIncluded";
        }
    }
}
