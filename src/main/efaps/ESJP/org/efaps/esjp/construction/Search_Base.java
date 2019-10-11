/*
 * Copyright 2007 - 2014 Jan Moxter
 *
 * All Rights Reserved.
 * This program contains proprietary and trade secret information of
 * Jan Moxter Copyright notice is precautionary only and does not
 * evidence any actual or intended publication of such program.
 *
 */

package org.efaps.esjp.construction;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.humanresource.Employee;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author Jan Moxter
 */
@EFapsUUID("7c836074-0153-4983-b416-59ec701d777c")
@EFapsApplication("eFapsApp-Construction")
public abstract class Search_Base
{

    public Return employee4Project(final Parameter _parameter)
        throws EFapsException
    {
        final org.efaps.esjp.common.uisearch.Search search = new org.efaps.esjp.common.uisearch.Search()
        {
            @Override
            protected void add2QueryBuilder(final Parameter _parameter,
                                            final QueryBuilder _queryBldr)
                throws EFapsException
            {
                super.add2QueryBuilder(_parameter, _queryBldr);
                new Employee().add2QueryBldr4EmployeeWithActivation(_parameter, _queryBldr);;
            }
        };
        return search.execute(_parameter);
    }
}
