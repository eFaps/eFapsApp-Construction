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
