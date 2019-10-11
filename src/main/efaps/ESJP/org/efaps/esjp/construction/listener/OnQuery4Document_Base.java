/*
 * Copyright 2007 - 2019 moxter.net
 *
 * All Rights Reserved.
 * This program contains proprietary and trade secret information of
 * Jan Moxter Copyright notice is precautionary only and does not
 * evidence any actual or intended publication of such program.
 *
 */

package org.efaps.esjp.construction.listener;

import org.apache.commons.lang3.BooleanUtils;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIConstruction;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CIProjects;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.listener.ITypedClass;
import org.efaps.esjp.construction.util.Construction;
import org.efaps.esjp.sales.document.AbstractDocument;
import org.efaps.esjp.sales.listener.IOnQuery;
import org.efaps.ui.wicket.models.field.AbstractUIField;
import org.efaps.util.EFapsException;

@EFapsUUID("56739aca-5f9f-4fa3-af00-251a3d7d1da2")
@EFapsApplication("eFapsApp-Construction")
public abstract class OnQuery4Document_Base
    extends AbstractDocument
    implements IOnQuery
{

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean add2QueryBldr4AutoComplete4Product(final ITypedClass _typedClass,
                                                      final Parameter _parameter,
                                                      final QueryBuilder _queryBldr)
        throws EFapsException
    {
        if (_typedClass != null) {
            if (CISales.ProductRequest.equals(_typedClass.getCIType())) {
                add2QueryBldr4AutoComplete4Product(_parameter, _queryBldr);
            }
        }
        return true;
    }

    @Override
    protected boolean add2QueryBldr4AutoComplete4Product(final Parameter _parameter,
                                                         final QueryBuilder _queryBldr)
        throws EFapsException
    {
        Instance projectInst = Instance.get(_parameter.getParameterValue("project"));
        final boolean deactivate = BooleanUtils.toBoolean(_parameter.getParameterValue("filter4VerificationList"));
        if (!deactivate) {
            if (!projectInst.isValid()) {
                final Object obj = _parameter.get(ParameterValues.CLASS);
                // if the autocomplete was called in a field table
                if (obj instanceof AbstractUIField) {
                    final Instance docInst = ((AbstractUIField) obj).getParent().getInstance();
                    if (docInst != null && docInst.isValid()
                                    && docInst.getType().isKindOf(CIERP.DocumentAbstract.getType())) {
                        final PrintQuery print = new PrintQuery(docInst);
                        final SelectBuilder sel = new SelectBuilder()
                                        .linkfrom(CIProjects.Project2DocumentAbstract.ToAbstract)
                                        .linkto(CIProjects.Project2DocumentAbstract.FromAbstract).instance();
                        print.addSelect(sel);
                        print.executeWithoutAccessCheck();
                        projectInst = print.getSelect(sel);
                    }
                }
            }

            if (projectInst != null && projectInst.isValid()) {
                final QueryBuilder queryBldr = new QueryBuilder(CIConstruction.ProjectService2VerificationList);
                queryBldr.addWhereAttrEqValue(CIConstruction.ProjectService2VerificationList.FromLink, projectInst);
                // only if once was a VerficationList assigned add it to the QueryBuilder
                if (!queryBldr.getQuery().executeWithoutAccessCheck().isEmpty()) {
                    final QueryBuilder docAttrQueryBldr = new QueryBuilder(CIConstruction.VerificationList);
                    docAttrQueryBldr.addWhereAttrEqValue(CIConstruction.VerificationList.Status,
                                    Status.find(CIConstruction.VerificationListStatus.Closed));

                    final QueryBuilder relAttrQueryBldr = new QueryBuilder(CIConstruction.ProjectService2VerificationList);
                    relAttrQueryBldr.addWhereAttrEqValue(CIConstruction.ProjectService2VerificationList.FromLink, projectInst);

                    final QueryBuilder posAttrQueryBldr = new QueryBuilder(CIConstruction.VerificationListPosition);
                    posAttrQueryBldr.addWhereAttrInQuery(CIConstruction.VerificationListPosition.VerificationListLink,
                                    docAttrQueryBldr.getAttributeQuery(CIConstruction.VerificationList.ID));
                    posAttrQueryBldr.addWhereAttrInQuery(CIConstruction.VerificationListPosition.VerificationListLink,
                                    relAttrQueryBldr.getAttributeQuery(CIConstruction.ProjectService2VerificationList.ToLink));

                    if (Construction.VERIFICATIONLISTALLOWGENERIC.get()) {
                        // get all products allowed by a VerificationList because its generic product is connected
                        final QueryBuilder genericQueryBldr = new QueryBuilder(CIProducts.ProductGeneric2Product);
                        genericQueryBldr.addWhereAttrInQuery(CIProducts.ProductGeneric2Product.FromLink,
                                        posAttrQueryBldr.getAttributeQuery(CIConstruction.VerificationListPosition.Product));

                        // query that allows the products from the Verification list or the products definded
                        // by its generics
                        final QueryBuilder productQueryBldr = new QueryBuilder(CIProducts.ProductAbstract);
                        productQueryBldr.setOr(true);
                        productQueryBldr.addWhereAttrInQuery(CIProducts.ProductAbstract.ID,
                                        posAttrQueryBldr.getAttributeQuery(CIConstruction.VerificationListPosition.Product));
                        productQueryBldr.addWhereAttrInQuery(CIProducts.ProductAbstract.ID,
                                        genericQueryBldr.getAttributeQuery(CIProducts.ProductGeneric2Product.ToLink));

                        _queryBldr.addWhereAttrInQuery(CIProducts.ProductAbstract.ID,
                                        productQueryBldr.getAttributeQuery(CIProducts.ProductAbstract.ID));
                    } else {
                        _queryBldr.addWhereAttrInQuery(CIProducts.ProductAbstract.ID,
                                    posAttrQueryBldr.getAttributeQuery(CIConstruction.VerificationListPosition.Product));
                    }
                }
            }
        }
        return true;
    }


    @Override
    public int getWeight()
    {
        return 0;
    }
}
