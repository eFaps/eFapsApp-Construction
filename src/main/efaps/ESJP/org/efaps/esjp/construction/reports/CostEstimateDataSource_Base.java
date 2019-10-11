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

import java.util.ArrayList;
import java.util.List;

import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.jasperreport.EFapsDataSource;
import org.efaps.util.EFapsException;

import net.sf.jasperreports.engine.JRField;

/**
 * TODO comment!
 *
 * @author Jan Moxter
 */
@EFapsUUID("af08826e-7eb7-4a07-82f4-212204a0fc00")
@EFapsApplication("eFapsApp-Construction")
public class CostEstimateDataSource_Base
    extends EFapsDataSource
{

    /**
     * {@inheritDoc}
     */
    @Override
    protected void analyze()
        throws EFapsException
    {
        final List<Instance> instances = new ArrayList<Instance>();

        if (isUseInstance()) {
            instances.add(getParameter().getInstance());
        } else {
            final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionGroupNode);
            queryBldr.addWhereAttrEqValue(CISales.PositionGroupNode.DocumentAbstractLink, getInstance().getId());
            queryBldr.addOrderByAttributeAsc(CISales.PositionGroupNode.Order);
            final InstanceQuery multi = queryBldr.getQuery();
            multi.execute();
            instances.addAll(multi.getValues());
        }

        if (instances.size() > 0) {
            setPrint(new MultiPrintQuery(instances));
            getPrint().setEnforceSorted(true);
            if (getJasperReport().getMainDataset().getFields() != null) {
                for (final JRField field : getJasperReport().getMainDataset().getFields()) {
                    final String select = field.getPropertiesMap().getProperty("Select");
                    if (select != null) {
                        getPrint().addSelect(select);
                        getSelects().add(select);
                    }
                }
            }
            getPrint().execute();
        }
    }

}
