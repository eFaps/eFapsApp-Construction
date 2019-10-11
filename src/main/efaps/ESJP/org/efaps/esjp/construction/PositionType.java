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

import java.util.ArrayList;
import java.util.List;

import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.ci.CIType;
import org.efaps.esjp.ci.CIConstruction;
import org.efaps.esjp.ci.CIProducts;




/**
 * TODO comment!
 *
 * @author Jan Moxter
 */
@EFapsUUID("fb192ebd-85f2-4e55-9ad6-cd2bd1c1a81d")
@EFapsApplication("eFapsApp-Construction")
public enum PositionType
    implements IPositionType
{
    /** Material. */
    MATERIAL("_M", new CIType[] { CIProducts.ProductMaterial, CIProducts.ProductGeneric }, new CIType[] {}),
    /** Personal. */
    PERSONAL("_P", new CIType[] { CIConstruction.ProductService }, new CIType[] { CIConstruction.ProductServiceClassPersonal }),
    /** Tools. */
    TOOLS("_T", new CIType[] { CIConstruction.ProductService }, new CIType[] { CIConstruction.ProductServiceClassTools }),
    /** Contracts. */
    CONTRACT("_C", new CIType[] { CIConstruction.ProductService }, new CIType[] { CIConstruction.ProductServiceClassContract });

    /**
     * suffix for use internal use.
     */
    private final String suffix;

    /**
     * CIType.
     */
    private final CIType[] ciType;

    /**
     * Classifications.
     */
    private final CIType[] ciClassification;

    /**
     * @param _suffix suffix
     */
    private PositionType(final String _suffix,
                         final CIType[] _ciType,
                         final CIType[] _ciClassification)
    {
        suffix = _suffix;
        ciType = _ciType;
        ciClassification = _ciClassification;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLabel()
    {
        return DBProperties.getProperty(EntryBOM.class.getName() + ".PositionType." + name());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSuffix()
    {
        return suffix;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isClassified()
    {
        return ciClassification != null && ciClassification.length > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Classification> getClassifications()
    {
        final List<Classification> ret = new ArrayList<Classification>();
        for (final CIType ciType :  ciClassification) {
            ret.add((Classification) ciType.getType());
        }
        return ret;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Type> getTypes()
    {
        final List<Type> ret = new ArrayList<Type>();
        for (final CIType ciType :  this.ciType) {
            ret.add(ciType.getType());
        }
        return ret;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getPosition()
    {
        return ordinal() * 10;
    }
}
