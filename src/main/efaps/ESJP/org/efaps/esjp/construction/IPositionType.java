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

import java.util.List;

import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;


/**
 * TODO comment!
 *
 * @author Jan Moxter
 */
@EFapsUUID("ffd8e73b-e9a0-4e3c-aa52-2a6947ec55d8")
@EFapsApplication("eFapsApp-Construction")
public interface IPositionType
{
    /**
     * @return name of the type for reports etc.
     */
    String getLabel();

    /**
     * @return the suffix used in the UserInterface.
     */
    String getSuffix();

    /**
     * @return true if the PositionType uses classifcations.
     */
    boolean isClassified();

    /**
     * @return the list of Classification this PositionType belongs to.
     */
    List<Classification> getClassifications();

    /**
     * @return the list of ProductType this PositionType belongs to.
     */
    List<Type> getTypes();

    /**
     * @return int value presenting the position of the PositionType.
     * Mainly used for ordering them.
     */
    int getPosition();
}
