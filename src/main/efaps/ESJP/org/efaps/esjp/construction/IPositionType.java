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
