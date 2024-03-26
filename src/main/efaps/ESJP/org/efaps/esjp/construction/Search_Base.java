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
