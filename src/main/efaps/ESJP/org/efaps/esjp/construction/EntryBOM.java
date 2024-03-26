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

import java.util.Collection;
import java.util.List;

import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;

/**
 * This class must be replaced for customization, therefore it is left empty.
 * Functional description can be found in the related "<code>_base</code>"
 * class.
 *
 * @author Jan Moxter
 */
@EFapsUUID("8d60e3f1-1054-4ea5-82f3-ae993070e8ff")
@EFapsApplication("eFapsApp-Construction")
public class EntryBOM
    extends EntryBOM_Base
{

    /**
     * Gets the position types.
     *
     * @return the position types
     */
    public static Collection<IPositionType> getPositionTypes()
    {
        return EntryBOM_Base.getPositionTypes();
    }

    /**
     * Gets the position type.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _prodInst the prod inst
     * @return the position type
     */
    public static IPositionType getPositionType(final Parameter _parameter,
                                                final Instance _prodInst)
    {
        return EntryBOM_Base.getPositionType(_parameter, _prodInst);
    }

    /**
     * Gets the position type.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _prodInst the prod inst
     * @param _clazzes the clazzes
     * @return the position type
     */
    public static IPositionType getPositionType(final Parameter _parameter,
                                                final Instance _prodInst,
                                                final List<Classification> _clazzes)
    {
        return EntryBOM_Base.getPositionType(_parameter, _prodInst, _clazzes);
    }
}
